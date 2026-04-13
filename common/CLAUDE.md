# CLAUDE.md -- common

## Purpose

Utility classes and datatypes shared across all modules. No internal dependencies.

## Dependencies

None (Layer 0 foundation).

## Key Types

- `ReferenceCounted` / `AbstractReferenceCounted` -- ref-counted resource lifecycle management
- `Resource` -- resource management trait
- `ClassUtils` -- reflection and class loading utilities
- `SystemPropertyUtil` -- safe system property access
- `ThrowableUtil` -- exception utilities
- `Platform` / `Platform0` -- JVM platform detection (internal)
- `Box`, `Money`, `Inet` -- domain datatypes
- `Circle`, `Line`, `Point`, `Path`, `Polygon` -- geometry types under `datatype`

## Package Layout

```
cc.otavia.common      -- core utilities
cc.otavia.datatype    -- domain and geometry types
cc.otavia.util        -- ReferenceCounted, Resource
cc.otavia.internal    -- Platform, ReflectionUtil (internal)
```

## Testing

1 test file under `test/src/`.
