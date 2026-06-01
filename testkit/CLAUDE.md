# CLAUDE.md -- testkit

## Purpose

Testing utilities including TestProbe for actor-level assertions with `askAndExpect`.

## Dependencies

core

## Key Types

- `TestProbe` -- synchronous test probe for inspecting actor messages and assertions
- `TestActorAddress` -- wrapper providing test address context for actors
- `ProbeActor` -- internal probe actor backing TestProbe functionality

## Package Layout

```
cc.otavia.testkit          -- TestProbe, TestActorAddress
cc.otavia.core.testkit     -- ProbeActor (internal)
```

## Testing

1 test file under `test/src/`:
- `InterceptorE2ESuite` -- end-to-end tests for the interceptor/AOP feature (uses `TestProbe.askAndExpect`)
