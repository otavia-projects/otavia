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

package cc.otavia.serde

import cc.otavia.buffer.Buffer

trait SerdePrimaryTypeOps {
    this: Serde[?] =>

    protected def serializeByte(byte: Byte, out: Buffer): this.type

    protected def serializeBoolean(boolean: Boolean, out: Buffer): this.type

    protected def serializeChar(char: Char, out: Buffer): this.type

    protected def serializeShort(short: Short, out: Buffer): this.type

    protected def serializeInt(int: Int, out: Buffer): this.type

    protected def serializeLong(long: Long, out: Buffer): this.type

    protected def serializeFloat(float: Float, out: Buffer): this.type

    protected def serializeDouble(double: Double, out: Buffer): this.type

    protected def serializeString(string: String, out: Buffer): this.type

    // deserialize ops

    protected def deserializeByte(in: Buffer): Byte

    protected def deserializeBoolean(in: Buffer): Boolean

    protected def deserializeChar(in: Buffer): Char

    protected def deserializeShort(in: Buffer): Short

    protected def deserializeInt(in: Buffer): Short

    protected def deserializeLong(in: Buffer): Long

    protected def deserializeFloat(in: Buffer): Float

    protected def deserializeDouble(in: Buffer): Double

    protected def deserializeString(in: Buffer): String

}
