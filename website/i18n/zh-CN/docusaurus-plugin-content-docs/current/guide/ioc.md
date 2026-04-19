---
title: Actor IoC
---

# Actor 依赖注入（IoC）

otavia 使用 Scala 3 的匹配类型为 Actor 地址提供编译时类型安全的依赖注入。`ActorSystem` 作为 IoC 容器，`BeanManager` 负责注册和查找。

## BeanManager

`BeanManager` 使用三个 `ConcurrentHashMap` 存储 bean：

| Map | 键 | 用途 |
|-----|-----|------|
| `beans` | 类名 | 按类名直接查找 |
| `qualifiers` | Qualifier 字符串 | 限定符 bean 查找 |
| `superTypes` | 父类/接口名 | 类型层次查找（返回列表） |

### 注册

创建 actor 时，可以注册到 BeanManager：

```scala
system.buildActor[MyService](factory, qualifier = "primary", primary = true)
```

参数：
- `qualifier`：可选字符串，用于区分同类型的多个 bean
- `primary`：标记此 bean 为多候选时的默认选择

### 查找

`getBean` 支持多种查找策略：

1. **按类名**：在 `beans` map 中直接查找
2. **按 qualifier + 类**：在 `qualifiers` map 中查找
3. **按父类/接口**：在 `superTypes` map 中查找，返回匹配的 bean
4. **Primary bean**：当存在多个候选时，优先选择标记为 `primary` 的

## Bean

`Bean` 持有：
- `clz: Class[?]` — Actor 的类
- `address: Address[Call]` — Actor 的 Address
- `primary: Boolean` — 是否是 primary bean
- 通过反射计算的父类/接口层次，用于类型查找

## BeanDefinition 和 Module

`BeanDefinition` 是指定如何创建和注册 actor 的 case class：

```scala
case class BeanDefinition(
  factory: ActorFactory[?],
  num: Int,
  qualifier: String,
  primary: Boolean
)
```

`Module` 是带有 `definitions: Seq[BeanDefinition]` 方法的 trait。通过 `system.loadModule(module)` 加载时，所有 definition 被实例化为 actor 并注册到 `BeanManager`。

```scala
class MyModule extends AbstractModule {
  override def definitions: Seq[BeanDefinition] = Seq(
    BeanDefinition(ActorFactory[UserService], 1, "", true),
    BeanDefinition(ActorFactory[OrderService], 1, "", true)
  )
}

system.loadModule(new MyModule)
```

## autowire

`Actor.autowire[A]` 方法提供类型安全的服务查找：

```scala
class MyActor extends StateActor[MyCall] {
  override protected def afterMount(): Unit = {
    val userService: Address[MessageOf[UserService]] = autowire[UserService]()
    val orderService: Address[MessageOf[OrderService]] = autowire[OrderService](qualifier = "primary")
  }
}
```

底层实现中，`autowire` 调用 `system.getAddress(classTag[A].runtimeClass, qualifier, remote)`，委托给 `BeanManager.getBean`。

`MessageOf[A]` 类型别名将 Actor 类型映射到其接受的消息类型，确保 Address 级别的类型安全。
