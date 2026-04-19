/*
 * Copyright 2022 Yan Kun <yan_kun_1992@foxmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cc.otavia.core.config

/** Buffer allocation configuration.
 *
 *  @param pageSize
 *    Page size in bytes for buffer allocation. Must be a power of 2 and one of {1024, 2048, 4096, 8192, 16384}.
 *    Default is 4096 (4KB). Larger pages reduce allocation overhead but increase memory consumption for small buffers.
 *  @param minCache
 *    Minimum number of pages cached per-thread allocator. Ensures a baseline of cached pages to avoid allocation
 *    thrashing. Must be at least 1. Default is 8.
 *  @param maxCache
 *    Maximum number of pages cached per-thread allocator. Limits memory consumption from buffer caching. Must be
 *    greater than or equal to minCache. Default is 10240.
 */
case class BufferConfig(
    pageSize: Int  = 4096,
    minCache: Int  = 8,
    maxCache: Int  = 10240
)
