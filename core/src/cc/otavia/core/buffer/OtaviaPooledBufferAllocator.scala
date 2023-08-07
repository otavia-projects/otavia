///*
// * Copyright 2022 Yan Kun <yan_kun_1992@foxmail.com>
// *
// * This file fork from netty.
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *     http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package cc.otavia.core.buffer
//
//import cc.otavia.buffer.pool.{BufferAllocatorMetric, BufferAllocatorMetricProvider}
//import cc.otavia.buffer.{AllocationType, Buffer, BufferAllocator}
//
//import java.util.function.Supplier
//
///** A optimal [[BufferAllocator]] for otavia thread model. */
//class OtaviaPooledBufferAllocator extends BufferAllocator with BufferAllocatorMetricProvider {
//
//    override def isPooling: Boolean = ???
//
//    override def getAllocationType: AllocationType = ???
//
//    override def allocate(size: Int): Buffer = ???
//
//    override def constBufferSupplier(bytes: Array[Byte]): Supplier[Buffer] = ???
//
//    override def close(): Unit = ???
//
//    override def metric(): BufferAllocatorMetric = ???
//
//}
//
//object OtaviaPooledBufferAllocator {}
