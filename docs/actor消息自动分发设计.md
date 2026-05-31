# Actor 消息自动分发设计

## 1. 为什么需要这个特性

### 问题

许多 Actor 处理多种消息类型。当前用户需要在 `resumeAsk` 中手写 match 分发代码：

```scala
class DBController extends StateActor[DBController.Req] {
    override protected def resumeAsk(stack: AskStack[DBController.Req & Ask[? <: Reply]]): StackYield =
        stack.ask match
            case _: SingleQueryRequest   => handleSingleQuery(stack.asInstanceOf[AskStack[SingleQueryRequest]])
            case _: MultipleQueryRequest => handleMultipleQuery(stack.asInstanceOf[AskStack[MultipleQueryRequest]])
            case _: UpdateRequest        => handleUpdateRequest(stack.asInstanceOf[AskStack[UpdateRequest]])

    private def handleSingleQuery(stack: AskStack[SingleQueryRequest]): StackYield = ???
    private def handleMultipleQuery(stack: AskStack[MultipleQueryRequest]): StackYield = ???
    private def handleUpdateRequest(stack: AskStack[UpdateRequest]): StackYield = ???
}
```

这段 match 代码是纯粹的样板——每个消息类型对应一个 handler 方法，match 只做类型判断 + 向下转型 + 方法调用。当消息类型增减时，必须同步修改 match 分支，容易遗漏。

### 不止 Web 场景

这个问题不只存在于 WebActor。任何处理多种消息类型的 Actor 都有同样的样板：

- **数据库连接 Actor**：处理 Query、Update、Batch 等多种 SQL 请求
- **缓存 Actor**：处理 Get、Set、Delete、Invalidate 等多种操作
- **消息队列消费者 Actor**：处理不同类型的业务消息
- **RPC 服务 Actor**：处理不同类型的 RPC 请求

代码库中的实际案例：

| Actor | 消息类型数 | 分发方式 |
|---|---|---|
| `DBController` (techempower) | 3 | `stack.ask match` + `asInstanceOf` |
| `HttpClient` (codec-http) | 2 | `stack match` + `isInstanceOf` type guard |
| `redis.Client` (codec-redis) | 2 | `stack match` + `isInstanceOf` type guard |
| `sql.Connection` | 3+ | `stack.ask.isInstanceOf[...]` |

### 自动分发的本质

自动分发的本质很简单：**每个 Ask 消息类型对应一个独立的 handler 方法，match 分发代码由框架自动生成**。用户只需定义 handler 方法并声明它们处理的类型，不需要手写 match。

## 2. 设计方向

### 目标

1. 用户为每种消息类型定义独立的 handler 方法
2. 框架自动生成 match 分发（或等效机制）
3. 分发逻辑可 debug——异常的 stack trace 指向用户的 handler 方法
4. 性能不能明显劣于手写 match
5. **编译时类型安全不能丢失**——Actor 可以接受的消息受其类型参数 `M <: Call` 约束，这个编译时检查必须保留

### 最终实现路径：C3（宏发现 handler 方法 + 自动生成分发）

经过探索和比较，最终选择 C3 方案。参见第 3 节。

### Debuggability 要求

分发层必须是**薄分发**——只做类型判断和调用，不内联 handler 逻辑。这样：
- 异常 stack trace 指向用户定义的 handler 方法名
- IDE 可以直接导航到 handler 方法
- 分发逻辑本身几乎不可能出错

## 3. 最终实现

### 3.1 API 形态

```scala
class DBController extends StateActor[REQ] {
    deriveDispatch  // 一行触发宏

    private def queryOne(stack: AskStack[SingleQueryRequest]): StackYield = ???
    private def queryAll(stack: AskStack[MultipleQueryRequest]): StackYield = ???
    private def update(stack: AskStack[UpdateRequest]): StackYield = ???
}
```

- `deriveDispatch` 是 `AbstractActor` 上的 `protected inline def`，无需混入额外 trait
- handler 方法发现规则：**参数 `AskStack[T]`/`NoticeStack[N]` + 返回 `StackYield`**
- handler 方法的访问修饰符不限（`private`/`protected`/`public` 均可）
- 所有 handler 的参数类型 `T` 必须是 actor 类型参数 `M` 的 union 成员，否则编译错误
- 遗漏任何成员 → 编译错误（穷尽性验证）

### 3.2 宏实现机制

#### 编译时

1. **分解 union 类型**：通过 `OrType(lhs, rhs)` 递归拆解 `M = A | B | C`
2. **分类成员**：`<:< TypeRepr.of[Ask[? <: Reply]]` → ask 成员；`<:< TypeRepr.of[Notice]` → notice 成员
3. **扫描类方法**：`classSym.methodMembers`，匹配单参数 `AskStack[T]`/`NoticeStack[N]` + 返回 `StackYield`
4. **验证穷尽性**：每个 union 成员有且仅有一个对应 handler
5. **生成代码**：为每个 handler 生成局部 helper def，生成 `setAskDispatch`/`setNoticeDispatch` 调用

#### handler 类型提取

从方法参数对应的 `ValDef.tpt.tpe` 提取完整参数类型（如 `AskStack[Hello]`），不需要 `@experimental` API。

#### 生成的代码等价于

```scala
{
    def queryOne$dispatch(s: Any): StackYield = this.queryOne(s.asInstanceOf[AskStack[SingleQueryRequest]])
    def queryAll$dispatch(s: Any): StackYield = this.queryAll(s.asInstanceOf[AskStack[MultipleQueryRequest]])
    def update$dispatch(s: Any): StackYield = this.update(s.asInstanceOf[AskStack[UpdateRequest]])

    setAskDispatch { (stack: AskStack[REQ & Ask[? <: Reply]]) =>
        if stack.ask.isInstanceOf[SingleQueryRequest] then queryOne$dispatch(stack)
        else if stack.ask.isInstanceOf[MultipleQueryRequest] then queryAll$dispatch(stack)
        else if stack.ask.isInstanceOf[UpdateRequest] then update$dispatch(stack)
        else throw NotImplementedError(...)
    }
}
```

#### 为什么不需要 `@experimental`

核心难题：宏展开期间 `methodSym.tree.paramss` 为空（方法参数树尚未填充），无法获取参数类型。`methodSym.info` 能提供精确类型但是 `@experimental` API。

解决方案：不直接调用 handler 方法。生成**局部 helper def**：

```scala
def handler$dispatch(s: Any): StackYield = this.handlerName(s.asInstanceOf[AskStack[T]])
```

- 参数类型 `AskStack[T]` 从 `paramSym.tree.tpt.tpe` 提取（ValDef 的源级类型）
- `asInstanceOf` 无条件通过类型检查
- helper def 通过 `Ref(helperSym)` 在 if-else 链中引用

### 3.3 Notice 分发

`deriveDispatch` 同时扫描 Ask 和 Notice 两种 handler。定义 `NoticeStack[N]` 参数的方法即可成为 notice handler：

```scala
class MixedActor extends StateActor[MSG] {
    deriveDispatch

    private def handleQuery(stack: AskStack[QueryReq]): StackYield = ???
    private def handleEvent(stack: NoticeStack[EventMsg]): StackYield = ???
}
```

### 3.4 Debuggability

```
at DBController.queryOne(DBController.scala:42)        ← 用户方法，行号精确
at AbstractActor.dispatchAskStack(AbstractActor.scala:280)
```

stack trace 直接指向用户的 handler 方法，dispatch 层是薄 if-else 链，本身不会出错。

### 3.5 与 Web 路由宏的兼容性

Web 模块的 `mount[T]()` 宏负责路由注册，`deriveDispatch` 负责消息分发。两者职责正交：

| | `mount[T]()` 宏（web 模块） | `deriveDispatch` 宏（框架级） |
|---|---|---|
| **输入** | 带 `@Get`/`@Post` 注解的方法 | 参数为 `AskStack[T]`/`NoticeStack[N]` 的方法 |
| **关心什么** | URL 路径 → Request 类型映射 | 消息类型 → handler 方法分发 |
| **生成什么** | `Router` 实例 + Actor 创建代码 | if-else 分发的 lambda |
| **运行时机** | `WebApp.mount[T]()` 调用处 | Actor 类编译时 |

```scala
class DBController extends WebActor {
    deriveDispatch

    @Get("/db")
    private def singleQuery(stack: AskStack[SingleQueryRequest]): StackYield = ???

    @Get("/queries")
    private def multipleQuery(stack: AskStack[MultipleQueryRequest]): StackYield = ???
}
```

两个宏扫描同一组方法但做不同的事情，互不干扰。

## 4. 实现现状

- ✅ **Ask 分发**：StateActor 和 ChannelsActor 均支持
- ✅ **Notice 分发**：与 Ask 分发同时支持
- ✅ **穷尽性验证**：遗漏 handler → 编译错误
- ✅ **类型安全**：handler 的消息类型必须是 actor 类型参数的成员
- ✅ **Debug 友好**：stack trace 直接指向 handler 方法
- ✅ **无 `@experimental`**：不传染给用户代码
- ✅ **向后兼容**：不调 `deriveDispatch` 的 actor 行为完全不变
- ❌ **BatchAsk/BatchNotice 分发**：暂不支持

## 5. 后续

- BatchAsk/BatchNotice 的自动分发
- 在更多模块中使用 `deriveDispatch` 替换手写 match
- 性能基准测试
