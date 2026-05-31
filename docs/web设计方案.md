# Otavia Web 模块设计方案

## 1. 设计理念

**"Actor 引擎驱动，Web 体验至上"**

Otavia Web 是构建在 Otavia Actor 框架之上的 Web 便利层模块。设计目标：

- **对开发者**：通过 Scala 3 宏 + 注解，减少路由注册、Serde 解析等样板代码
- **对运行时**：保持 Actor 模型的全部优势——无锁并发、零拷贝 I/O、对象池化、栈协程异步
- **对编译器**：利用 Scala 3 元编程在编译期完成路由注册、Serde 解析，零运行时反射

### 核心原则

Web 模块不改变 Actor 模型的任何基本约束。一切设计必须保持 Actor 的消息驱动、无锁并发、栈协程异步的本质。不引入任何诱导用户编写阻塞、并发竞争代码的设计。

### 与现有框架的定位差异

| 维度 | Spring Boot | http4s | Ktor | Actix-web | **Otavia Web** |
|------|------------|--------|------|-----------|----------------|
| 并发模型 | 线程池 | Cats Effect IO | 协程 | tokio task | **Actor + 栈协程** |
| 内存策略 | GC | GC | GC | 所有权 | **对象池 + 零拷贝** |
| 路由注册 | 运行时反射 | 编译期 DSL | 运行时 DSL | 编译时属性 | **编译期宏** |
| 异步模型 | DeferredResult | monad transformer | suspend fun | async/await | **栈协程状态机** |
| DI | IoC 容器 | 手动构造 | 外部库 | Arc 共享 | **autowire + 构造器注入** |
| 中间件 | Interceptor 链 | middleware 组合 | pipeline | guard/fn | **Actor AOP** |
| 上下文传递 | ThreadLocal | 参数传递 | 续体上下文 | 提取器 | **HttpRequest 携带** |

## 2. Quick Start

### 最简应用

```scala
import cc.otavia.web.*
import cc.otavia.web.annotation.*

class JsonController extends WebActor {
    @Get("/json")
    def json(stack: AskStack[JsonRequest]): StackYield =
        stack.`return`(Message("Hello, World!"))
}

object JsonController {
    class JsonRequest extends HttpRequest[Nothing, Message]

    case class Message(message: String) derives JsonSerde
}

@main def main(): Unit =
    val system = ActorSystem()
    WebApp(system)
        .mount[JsonController]()
        .listen(8080)
        .start()
```

### 完整应用（参考 techempower）

```scala
@main def main(): Unit =
    val system = ActorSystem()

    // 基础设施
    system.buildActor(() => Connection(config.db.url, config.db.user, config.db.password),
        global = true, num = config.db.poolSize)

    WebApp(system)
        // 常量路由（不经过 Actor，ServerCodec 直接响应）
        .constant("/plaintext", "Hello, World!", TEXT_PLAIN_UTF8)
        // 控制器
        .mount[JsonController](global = true, num = system.actorWorkerSize)
        .mount[DBController](global = true, num = system.actorWorkerSize)
        .mount[FortuneController](global = true, num = system.actorWorkerSize)
        // 启动
        .listen(config.server.port)
        .start()
```

## 3. 控制器——WebActor

Web 模块提供唯一的控制器模式：**WebActor**（extends StateActor）。用户直接使用栈协程编写异步逻辑，不隐藏任何 Actor 机制。

```scala
class DBController extends WebActor {
    private var connection: Address[MessageOf[Connection]] = _
    override protected def afterMount(): Unit = connection = autowire[Connection]()

    @Get("/db")
    def singleQuery(stack: AskStack[SingleQueryRequest]): StackYield =
        stack.state match
            case _: StartState =>
                val state = FutureState[World]()
                connection.ask(PrepareQuery.fetchOne[World](SELECT_WORLD, randomWorld()), state.future)
                stack.suspend(state)
            case state: FutureState[World] =>
                stack.`return`(state.future.getNow)

    @Get("/queries")
    def multipleQuery(stack: AskStack[MultipleQueryRequest]): StackYield =
        stack.state match
            case _: StartState =>
                val state = FutureState[Seq[World]]()
                val queries = (1 to queryCount).map(_ => PrepareQuery.fetchOne[World](SELECT_WORLD, randomWorld()))
                connection.batchAsk(queries, state.future)
                stack.suspend(state)
            case state: FutureState[Seq[World]] =>
                stack.`return`(HttpResponse.builder.setContent(state.future.getNow.toArray).build())

    @Get("/updates")
    def update(stack: AskStack[UpdateRequest]): StackYield = ???
}

object DBController {
    class SingleQueryRequest extends HttpRequest[Nothing, World]
    class MultipleQueryRequest extends HttpRequest[Nothing, HttpResponse[Array[World]]]
    class UpdateRequest extends HttpRequest[Nothing, HttpResponse[Seq[World]]]
}
```

### 关键设计点

1. **路由注解在 handler 方法上**：`@Get("/db")` 标注在 `singleQuery` 方法上，路由、Request 类型、handler 逻辑三者就近可见
2. **Request 类型由用户在伴生对象中定义**：因为 handler 方法签名（`AskStack[SingleQueryRequest]`）依赖 Request 类型，宏无法先生成类型再让用户引用
3. **handler 方法内是完整栈协程逻辑**：不隐藏 Actor 机制，用户拥有完整控制力
4. **`mount[T]()` 显式注册**：所有控制器注册集中在一处，系统拓扑一目了然，也便于传递每个控制器的创建参数（`global`、`num`）

## 4. 路由系统

### 4.1 注解声明

```scala
@Get("/path")        // GET 请求
@Post("/path")       // POST 请求
@Put("/path")        // PUT 请求
@Patch("/path")      // PATCH 请求
@Delete("/path")     // DELETE 请求
@Head("/path")       // HEAD 请求
@Options("/path")    // OPTIONS 请求
```

路径支持变量：`/users/{id}`。

### 4.2 控制器级路径前缀

```scala
@Controller("/api/v1")
class ApiController extends WebActor {
    @Get("/users")       // 匹配 /api/v1/users
    def listUsers(stack: AskStack[ListUsersRequest]): StackYield = ???

    @Get("/users/{id}")  // 匹配 /api/v1/users/{id}
    def getUser(stack: AskStack[GetUserRequest]): StackYield = ???
}
```

### 4.3 编译期路由表生成

`mount[T]()` 宏在编译期完成以下工作：

1. 扫描 `T` 中带路由注解的 `def` 方法
2. 从方法签名的 `AskStack[R]` 类型参数提取 Request 类型 `R`
3. 在 `T` 的伴生对象中找到 `R` 的定义，提取 Response 类型
4. 通过 `summon` 验证 Response 类型有对应的 Serde，不满足则编译报错
5. 为每个路由生成 `Router`（path, method, factory, serde）
6. 生成 Actor 创建代码

**编译错误示例**：

```scala
class BadController extends WebActor {
    @Get("/users/{id}")
    def getUser(stack: AskStack[GetUserRequest]): StackYield = ???
}
object BadController {
    // 编译错误：找不到 JsonSerde[CustomType]
    class GetUserRequest extends HttpRequest[Nothing, CustomType]
}
```

### 4.4 常量路由与静态路由

不经过 Actor，由 ServerCodec 直接响应：

```scala
WebApp(system)
    .constant("/plaintext", "Hello, World!", MediaType.TEXT_PLAIN_UTF8)
    .static("/public", Path.of("./static"))
    .notFound(Path.of("./404.html"))
```

### 4.5 消息自动分发

WebActor 处理多种 Request 消息类型时，`resumeAsk` 中的 match 分发代码可通过 `deriveDispatch` 自动生成。`deriveDispatch` 是 `AbstractActor` 的 protected inline 方法，宏在编译时自动发现 handler 方法并生成 if-else 分发链。详见 [docs/actor消息自动分发设计.md](actor消息自动分发设计.md)。

`mount[T]()` 宏只负责路由相关的事情（Router 生成、Serde 解析、Request-Response 类型关联），不负责 match 分发。

## 5. 请求上下文

上下文信息保存在 `HttpRequest` 中，通过 `stack.ask` 访问。不使用 ActorThreadLocal 或任何隐式全局状态。

```scala
class DebugController extends WebActor {
    @Get("/debug")
    def debug(stack: AskStack[DebugRequest]): StackYield = {
        val req = stack.ask
        val info = DebugInfo(
            method = req.method,
            path = req.path,
            headers = req.headers,
            remoteAddress = req.remoteAddress
        )
        stack.`return`(info)
    }
}
```

`HttpRequest` 提供的上下文 API：

```scala
abstract class HttpRequest[C, R <: Reply] extends Ask[R] {
    def method: HttpMethod
    def path: String
    def header(key: HttpHeaderKey): HttpHeaderValue
    def headers: mutable.Map[HttpHeaderKey, HttpHeaderValue]
    def params: Map[String, String]          // 查询参数
    def pathVariables: Map[String, String]   // 路径变量
    def content: Option[C]                   // 请求体（反序列化后）
    def remoteAddress: String
}
```

## 6. 响应构建

### 6.1 直接返回值

handler 方法通过 `stack.return()` 返回结果。如果返回类型不是 `HttpResponse`，宏自动包装：

```scala
@Get("/hello")
def hello(stack: AskStack[HelloRequest]): StackYield =
    stack.`return`(Message("Hello, World!"))
// 宏自动包装为 HttpResponse.builder.setContent(Message(...)).build()
```

### 6.2 手动构建 HttpResponse

需要控制响应头、状态码等时，直接构建 `HttpResponse`：

```scala
@Post("/users")
def create(stack: AskStack[CreateUserRequest]): StackYield =
    stack.state match
        case _: StartState =>
            val state = FutureState[User]()
            userService.ask(CreateUser(stack.ask.content.get), state.future)
            stack.suspend(state)
        case state: FutureState[User] =>
            val user = state.future.getNow
            val response = HttpResponse.builder
                .setContent(user)
                .status(201)
                .header(HttpHeaderKey.LOCATION, s"/users/${user.id}")
                .build()
            stack.`return`(response)
```

### 6.3 Serde 自动解析

`mount[T]()` 宏在编译期通过 `summon` 为每个路由的 Response 类型解析 Serde：

| 返回类型 | 解析规则 |
|---------|---------|
| `T` 且存在 `given JsonSerde[T]` | `HttpResponseSerde.json(summon[JsonSerde[T]])` |
| `String` | `HttpResponseSerde.stringHtml` |
| `Array[Byte]` | `HttpResponseSerde(BytesSerde, TEXT_PLAIN_UTF8)` |

## 7. 错误处理

### 7.1 复用 Actor 异常消息体系

Web 模块不引入独立的异常体系，直接复用 Actor 框架的 `ExceptionMessage`。

Web 模块定义 `HttpException`（携带 HTTP 状态码），作为 `ExceptionMessage` 的 cause：

```scala
// Web 模块定义
class HttpException(val status: Int, message: String) extends RuntimeException(message)
class BadRequestException(message: String)      extends HttpException(400, message)
class UnauthorizedException(message: String)    extends HttpException(401, message)
class ForbiddenException(message: String)       extends HttpException(403, message)
class NotFoundException(message: String = "")   extends HttpException(404, message)
class ConflictException(message: String)        extends HttpException(409, message)
class InternalServerException(message: String)  extends HttpException(500, message)
```

### 7.2 在 WebActor 中使用

```scala
@Get("/users/{id}")
def getUser(stack: AskStack[GetUserRequest]): StackYield =
    stack.state match
        case _: StartState =>
            val id = stack.ask.pathVariables("id").toInt
            if id <= 0 then stack.throw(ExceptionMessage(BadRequestException("id must be positive")))
            else
                val state = FutureState[Option[User]]()
                userService.ask(FindUser(id), state.future)
                stack.suspend(state)
        case state: FutureState[Option[User]] =>
            state.future.getNow match
                case Some(user) => stack.`return`(user)
                case None       => stack.throw(ExceptionMessage(NotFoundException(s"User not found")))
```

### 7.3 HttpServerWorker 异常处理

`HttpServerWorker` 收到异常回复（`ExceptionMessage`）时：
- 检查 cause 是否为 `HttpException`，如果是则用其 status 和 message 构造 HTTP 错误响应
- 否则返回 500 Internal Server Error

## 8. 中间件

Web 模块不提供独立的 Filter/中间件机制。中间件需求（日志、认证、限流、CORS 等）由框架级别的 **Actor AOP** 能力实现。

Actor AOP 基于 Actor 之间通过 Address 通信的特性，通过消息拦截实现横切关注点。消息发送端无需感知拦截器的存在。详见 [docs/actor-aop设计.md](actor-aop设计.md)。

## 9. 依赖注入

WebActor 本质是 StateActor，直接使用 Actor 框架的 `autowire` 机制：

```scala
class UserController extends WebActor {
    private var userService: Address[MessageOf[UserService]] = _
    private var orderService: Address[MessageOf[OrderService]] = _

    override protected def afterMount(): Unit =
        userService = autowire[UserService]
        orderService = autowire[OrderService]
}
```

## 10. 配置系统

利用 Serde 基础设施直接反序列化配置文件：

```scala
case class ServerConfig(port: Int = 8080, workers: Int = 8, serverName: String = "otavia-web") derives Serde
case class DatabaseConfig(url: String, user: String, password: String, poolSize: Int = 4) derives Serde
case class AppConfig(server: ServerConfig, database: DatabaseConfig) derives Serde
```

```scala
val config = WebConfig.load[AppConfig]("config.json")
WebApp(system, config.server)
    .mount[DBController]()
    .listen(config.server.port)
    .start()
```

## 11. 框架级依赖

Web 模块依赖两个框架级别的特性，这些特性不只在 web 场景有用，对所有 Actor 都有价值：

### 11.1 Actor AOP

**为什么需要**：Web 服务需要横切关注点（日志、认证、限流、CORS 等）。传统 Web 框架通过 Filter/Interceptor 链实现，但这引入了同步阻塞的中间件模型。Actor 天然适合 AOP——Actor 之间通过 Address 发送消息，只需在 Address 层面拦截消息即可实现横切关注点，消息发送端完全无感知。详见 [docs/actor-aop设计.md](actor-aop设计.md)。

### 11.2 Actor 消息自动分发

**为什么需要**：WebActor 通常处理多种 Request 消息类型，需要在 `resumeAsk` 中写 match 分发代码。这个样板代码对所有处理多种消息类型的 Actor 都存在。将自动分发提升为框架级别的能力，可以减少重复代码。详见 [docs/actor消息自动分发设计.md](actor消息自动分发设计.md)。

## 12. 请求生命周期

完整请求从 TCP 到响应的过程：

```
┌─────────────────────────────────────────────────────────────┐
│                        ActorSystem                           │
│                                                              │
│  HttpServer (AcceptorActor)                                  │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │  Accept TCP connection                                   │ │
│  │  Assign to Worker (round-robin across ActorThread pool)  │ │
│  └──────────────────────────┬──────────────────────────────┘ │
│                             │                                │
│  HttpServerWorker (per-ActorThread)                          │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │  1. ServerCodec.decode:                                  │ │
│  │     - 解析 HTTP 请求行 + 头 + 体                          │ │
│  │     - RouterMatcher.choice(): Trie 匹配路由               │ │
│  │     - HttpRequestFactory.createHttpRequest(): 创建类型请求 │ │
│  │     - 对 ConstantRouter/StaticFilesRouter: 直接响应       │ │
│  │     - 对 ControllerRouter: fireChannelRead(request)       │ │
│  │                                                          │ │
│  │  2. resumeChannelStack:                                   │ │
│  │     - request → controller.ask(request, future)           │ │
│  │     - 等待 controller 返回 Reply                           │ │
│  │     - 收到 ExceptionMessage 时：根据 cause 构造 HTTP 错误  │ │
│  │                                                          │ │
│  │  3. ServerCodec.encode:                                   │ │
│  │     - 写 HTTP 响应行 + Server + Date + Content-Type       │ │
│  │     - HttpResponseSerde.contentSerde 序列化 body          │ │
│  │     - 回填 Content-Length                                 │ │
│  └─────────────────────────────────────────────────────────┘ │
│                             │                                │
│  Controller Actor (WebActor, 同一 ActorThread)               │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │  4. resumeAsk:                                           │ │
│  │     - 消息自动分发到对应 handler 方法                      │ │
│  │     - handler 方法内使用栈协程处理异步逻辑                  │ │
│  │     - 通过 stack.return / stack.throw 返回结果            │ │
│  └─────────────────────────────────────────────────────────┘ │
│                                                              │
│  * ControllerActor 与 HttpServerWorker 在同一 ActorThread 上 │
│  * ask 调用是同线程调度，无跨线程开销                          │
│  * 整个请求链路在同一个 ActorThread 内完成                    │
│  * 上下文通过 HttpRequest 携带，无 ThreadLocal                │
└─────────────────────────────────────────────────────────────┘
```

### 关键性能特征

1. **同线程调度**：HttpServerWorker 的 `ask` 将请求投递到同 ActorThread 的 ControllerActor 邮箱，无跨线程同步
2. **零拷贝路由**：RouterMatcher 直接在 Byte Buffer 上做 Trie 匹配
3. **对象池化**：HttpRequest、HttpResponse、StackState 全部通过对象池复用
4. **无反射**：所有路由注册、参数提取、Serde 解析在编译期完成

## 13. 模块结构

```
otavia/
  web/                                    # 核心模块
    src/cc/otavia/web/
      annotation.scala                    # @Get @Post @Put @Delete @Controller 等注解
      WebApp.scala                        # 应用入口 mount/constant/listen/start
      WebActor.scala                      # 控制器基类（extends StateActor）
      HttpException.scala                 # HTTP 异常（400/401/403/404/500 等）
      WebConfig.scala                     # 配置加载
      WebMacros.scala                     # mount[T]() 宏实现（Quoted Reflection）
    CLAUDE.md

  web-session/                            # 会话管理（可选，后续扩展）
  web-security/                           # CORS/CSRF（可选，后续扩展）
  web-template/                           # 模板渲染（可选，后续扩展）
```

### 依赖关系

```
web → codec-http, serde-json
web-session → web
web-security → web
web-template → web
```

核心 `web` 模块依赖 `codec-http`（HTTP 服务）和 `serde-json`（JSON 序列化）。可选模块只依赖核心 `web`。

## 14. mount[T]() 宏原理

### 入口

```scala
object WebApp {
    inline def mount[T](global: Boolean = true, num: Int = 0): WebApp =
        ${ WebMacros.mount[T]('system, 'global, 'num) }
}
```

### 宏实现步骤

1. **扫描带路由注解的方法**：用 Quoted Reflection 扫描 `T` 的所有 `def` 成员，筛选带 `@Get`/`@Post`/... 的方法
2. **提取 Request 类型**：从方法签名的 `AskStack[R]` 参数类型获取 `R`
3. **在伴生对象中查找 Request 定义**：找到 `R`，提取 `HttpRequest` 的两个类型参数（body 类型、response 类型）
4. **验证 Serde 可用性**：对 response 类型 `summon` 验证 Serde 存在，不存在则编译报错
5. **生成 Router 定义**：为每个路由生成 `Router(method, path, controller, factory, serde)`
6. **生成 Actor 创建代码**：`system.buildActor(() => new T(), global, num)`

### 宏不负责

- `resumeAsk` 中的 match 分发（由框架级消息自动分发特性处理）
- Request 类型的生成（由用户在伴生对象中定义）

## 15. 实施路线图

### Phase 1：核心路由（MVP）

目标：最简可用的 WebActor + 路由注解 + Serde 自动解析。

- 注解定义：`@Get`, `@Post`, `@Controller`
- `mount[T]()` 宏：扫描注解方法 → 关联 Request 类型 → summon Serde → 生成 Router
- `WebApp` 入口：封装 `HttpServer` 创建和启动
- `HttpException` + ExceptionMessage 集成

可验证：用 techempower 的 `/json` 和 `/plaintext` 端点测试。

### Phase 2：框架级依赖

目标：实现 Web 模块依赖的两个框架级特性。

- ✅ Actor 消息自动分发：通过 `deriveDispatch` 宏消除 `resumeAsk` 中的手写 match（已实现）
- Actor AOP：基于消息拦截的横切关注点

### Phase 3：Web 生态

目标：开箱即用的 Web 开发体验。

- 配置系统（`WebConfig.load`）
- 静态文件服务增强
- WebSocket 支持
- 会话管理（`web-session` 模块）
- CORS/CSRF（`web-security` 模块）
