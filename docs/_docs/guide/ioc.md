---
layout: main
title: Actor IOC
---

# Actor Dependency Injection (IoC)

Otavia provides compile-time type-safe dependency injection for Actor addresses using Scala 3's match types. The `ActorSystem` acts as the IoC container, and `BeanManager` handles registration and lookup.

## BeanManager

`BeanManager` stores beans in three `ConcurrentHashMap`s:

| Map | Key | Purpose |
|-----|-----|---------|
| `beans` | Class name | Direct class name lookup |
| `qualifiers` | Qualifier string | Qualified bean lookup |
| `superTypes` | Superclass/interface name | Type hierarchy lookup (returns list) |

### Registration

When an actor is created, it can be registered with the BeanManager:

```scala
system.buildActor[MyService](factory, qualifier = "primary", primary = true)
```

Parameters:
- `qualifier`: An optional string to disambiguate multiple beans of the same type
- `primary`: Marks this bean as the default choice when multiple candidates exist

### Lookup

`getBean` supports multiple lookup strategies:

1. **By class name**: Direct lookup in `beans` map
2. **By qualifier + class**: Lookup in `qualifiers` map
3. **By superclass/interface**: Lookup in `superTypes` map, returns matching beans
4. **Primary bean**: When multiple candidates exist, the one marked `primary` is preferred

## Bean

`Bean` holds:
- `clz: Class[?]` — The actor's class
- `address: Address[Call]` — The actor's Address
- `primary: Boolean` — Whether this is the primary bean
- Superclass/interface hierarchy computed via reflection for type-based lookup

## BeanDefinition and Module

`BeanDefinition` is a case class specifying how to create and register an actor:

```scala
case class BeanDefinition(
  factory: ActorFactory[?],
  num: Int,
  qualifier: String,
  primary: Boolean
)
```

`Module` is a trait with a `definitions: Seq[BeanDefinition]` method. When loaded via `system.loadModule(module)`, all definitions are instantiated as actors and registered with the `BeanManager`.

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

The `Actor.autowire[A]` method provides type-safe service lookup:

```scala
class MyActor extends StateActor[MyCall] {
  override protected def afterMount(): Unit = {
    val userService: Address[MessageOf[UserService]] = autowire[UserService]()
    val orderService: Address[MessageOf[OrderService]] = autowire[OrderService](qualifier = "primary")
  }
}
```

Under the hood, `autowire` calls `system.getAddress(classTag[A].runtimeClass, qualifier, remote)`, which delegates to `BeanManager.getBean`.

The `MessageOf[A]` type alias maps an Actor type to the message type it accepts, ensuring type safety at the Address level.
