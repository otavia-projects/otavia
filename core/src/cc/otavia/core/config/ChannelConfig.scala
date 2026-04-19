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

/** Channel configuration defaults.
 *
 *  @param connectTimeoutMs
 *    Default TCP connect timeout in milliseconds. Applied when no explicit timeout is set on the connect operation.
 *    Default is 30000ms (30 seconds).
 *  @param writeWaterMarkLow
 *    Low watermark for write buffer in bytes. When the write buffer size drops below this threshold after being
 *    above the high watermark, the channel becomes writable again. Default is 32768 (32KB).
 *  @param writeWaterMarkHigh
 *    High watermark for write buffer in bytes. When the write buffer size exceeds this threshold, the channel
 *    becomes non-writable, triggering backpressure. Default is 65536 (64KB).
 *  @param maxFutureInflight
 *    Maximum number of pipelined requests (ChannelFutures) that can be in-flight without waiting for responses.
 *    Enables high-performance pipelined protocols like Redis pipeline and HTTP/2 multiplexing. Default is 1.
 *  @param maxStackInflight
 *    Maximum number of inbound server requests (ChannelStacks) that can be processed concurrently per channel.
 *    Default is 1.
 *  @param maxMessagesPerRead
 *    Maximum number of messages to read per read loop iteration for server channels. Controls read batching.
 *    Default is 16.
 */
case class ChannelConfig(
    connectTimeoutMs: Int   = 30000,
    writeWaterMarkLow: Int  = 32768,
    writeWaterMarkHigh: Int = 65536,
    maxFutureInflight: Int  = 1,
    maxStackInflight: Int   = 1,
    maxMessagesPerRead: Int = 16
)
