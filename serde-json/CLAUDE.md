# CLAUDE.md -- serde-json

## Purpose

JSON serialization via `JsonSerde[A]` type class with macro-derived instances for case classes.

## Dependencies

serde

## Key Types

- `JsonSerde[A] extends Serde[A]` -- JSON format serde trait
- `JsonSerde.derived[T]` -- macro-derived serde instances via inline macros
- `JsonMacro` -- macro implementation for deriving
- `JsonFormatException` -- JSON-specific parse exception
- `JsonHelper` -- internal JSON parsing utilities
- `JsonConstants` -- byte constants for JSON tokens

### Built-in type serdes (`types/`)
`StringJsonSerde`, `ShortJsonSerde`, `BigDecimalJsonSerde`, `BigIntJsonSerde`, `BigIntegerJsonSerde`, `JBigDecimalJsonSerde`, `JDurationJsonSerde`, `UUIDJsonSerde`, `InstantJsonSerde`, `LocalDateJsonSerde`, `LocalDateTimeJsonSerde`, `LocalTimeJsonSerde`, `MonthDayJsonSerde`, `OffsetDateTimeJsonSerde`, `OffsetTimeJsonSerde`, `PeriodJsonSerde`, `YearJsonSerde`, `YearMonthJsonSerde`, `ZoneIdJsonSerde`, `ZoneOffsetJsonSerde`, `ZonedDateTimeJsonSerde`

## Package Layout

```
cc.otavia.json        -- JsonSerde, JsonMacro, JsonFormatException
cc.otavia.json.types  -- built-in type serdes (21 types)
```

## Patterns

- Use `JsonSerde.derived[MyCaseClass]` to auto-derive serde for case classes
- Field annotations `@rename`, `@ignore` (from serde module) control JSON field mapping

## Testing

6 test files under `test/src/`.
