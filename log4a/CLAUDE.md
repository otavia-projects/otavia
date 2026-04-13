# CLAUDE.md -- log4a

## Purpose

Simple Logging Facade for Otavia Actors (SLF4A) -- logging framework integrating with the actor system.

## Dependencies

core. External: `scala-xml:2.4.0` for XML configuration parsing.

## Key Types

- `Log4aLogger` / `Log4aLoggerFactory` -- logger implementation and factory
- `Log4aModule` -- module registration for IoC
- `Appender` -- trait for log message output destinations (as actors)
- `ConsoleAppender` / `SingleFileAppender` / `RollingFileAppender` -- concrete output destinations
- `Log4aConfig` / `RootLoggerConfig` / `NormalLoggerConfig` -- XML configuration model
- `Log4aServiceProvider` -- SLF4A SPI binding

## Package Layout

```
cc.otavia.log4a           -- logger, factory, module
cc.otavia.log4a.appender  -- Console, File, RollingFile appenders
cc.otavia.log4a.config    -- XML configuration model classes
cc.otavia.log4a.spi       -- SLF4A service provider
```

## Testing

2 test files under `test/src/`. XML configuration examples in `resources/`.
