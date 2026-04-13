# CLAUDE.md -- buffer

## Purpose

Byte buffer infrastructure. Provides the `Buffer` abstraction, pooled/unpooled memory allocators, and `AdaptiveBuffer` -- the core data structure through which bytes flow in the channel pipeline.

## Dependencies

None (Layer 0 foundation).

## Key Types

- `Buffer` -- core buffer abstraction wrapping java.nio.ByteBuffer with separate read/write offsets
- `BufferAllocator` -- factory trait for creating buffers
- `AdaptiveBuffer` -- growable buffer built from a deque of pooled memory pages; serves as the channel's read and write buffer (see below)
- `DirectPooledPageAllocator` / `HeapPooledPageAllocator` -- thread-local pooled allocators
- `UnpoolDirectAllocator` / `UnpoolHeapAllocator` -- unpooled alternatives
- `RecyclablePageBuffer` -- fixed-size pooled memory page, linkable via `.next` into page chains
- `ByteCursor` / `ByteProcessor` -- buffer iteration abstractions

## AdaptiveBuffer Design

`AdaptiveBufferImpl` extends `ArrayDeque[RecyclablePageBuffer]` -- a logically contiguous buffer composed of a chain of pooled fixed-size memory pages:

- **Virtually infinite capacity** (`Int.MaxValue`). Grows on demand by allocating new pages from `PooledPageAllocator` and appending to the deque tail
- **Automatic page recycling** on read: consumed pages are returned to the object pool immediately, no compaction or data copying needed
- **Cross-page read/write**: every primitive operation has three paths -- fast-path (within one page), exact-boundary (page boundary), cross-page (assemble value spanning two pages)
- **Zero-copy transfer**: `splitBefore(offset)` physically detaches a prefix chain of pages from one AdaptiveBuffer. `writeBytes(AdaptiveBuffer)` transfers page ownership between AdaptiveBuffers sharing the same allocator -- no byte copying
- **Pipeline integration**: each `ChannelPipelineImpl` holds one inbound and one outbound AdaptiveBuffer. Buffer-aware handlers also hold their own AdaptiveBuffer instances. Handlers communicate data ranges via `AdaptiveBufferMessage`, `AdaptiveBufferRange`, and `AdaptiveBufferChangeNotice` -- references into the shared buffer, not data copies

AdaptiveBuffer is the channel's circulatory system for bytes. It is infrastructure tightly coupled to the pipeline, not a general-purpose user-facing composite buffer.

## Package Layout

```
cc.otavia.buffer            -- Buffer, BufferAllocator, AdaptiveBuffer
cc.otavia.buffer.pool       -- pooled allocators, RecyclablePageBuffer, AdaptiveBufferImpl
cc.otavia.buffer.unpool     -- unpooled allocators
cc.otavia.buffer.utils      -- ByteCursor, ByteProcessor
cc.otavia.buffer.constant   -- ByteOrder and buffer constants
```

## Testing

9 test files under `test/src/`. Benchmarks under `bench/` via JMH.
