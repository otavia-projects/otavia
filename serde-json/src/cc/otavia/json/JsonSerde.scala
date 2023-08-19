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

package cc.otavia.json

import cc.otavia.buffer.Buffer
import cc.otavia.serde.{Serde, SerdeMathTypeOps, SerdeOps, SerdePrimaryTypeOps}

import java.math.BigInteger
import java.nio.charset.{Charset, StandardCharsets}
import scala.compiletime.*
import scala.deriving.Mirror
import scala.language.unsafeNulls

trait JsonSerde[A] extends Serde[A] with SerdePrimaryTypeOps with SerdeMathTypeOps {

    def charsets: Charset = StandardCharsets.UTF_8

    private def skipBlanks(in: Buffer): Unit =
        while (in.skipIfNext(' ') || in.skipIfNext('\n') || in.skipIfNext('\r') || in.skipIfNext('\t')) {}

    protected def serializeObjectStart(out: Buffer): this.type = {
        out.writeByte(JsonConstants.TOKEN_OBJECT_START)
        this
    }

    // TODO: replace to exceptXXX, if false throws error
    protected def skipObjectStart(in: Buffer): Boolean = {
        skipBlanks(in)
        in.skipIfNext(JsonConstants.TOKEN_OBJECT_START)
    }

    protected def serializeArrayStart(out: Buffer): this.type = {
        out.writeByte(JsonConstants.TOKEN_ARRAY_START)
        this
    }

    protected def skipArrayStart(in: Buffer): Boolean = {
        skipBlanks(in)
        in.skipIfNext(JsonConstants.TOKEN_ARRAY_START)
    }

    protected def serializeObjectEnd(out: Buffer): this.type = {
        out.writeByte(JsonConstants.TOKEN_OBJECT_END)
        this
    }

    protected def skipObjectEnd(in: Buffer): Boolean = {
        skipBlanks(in)
        in.skipIfNext(JsonConstants.TOKEN_OBJECT_END)
    }

    protected def serializeArrayEnd(out: Buffer): this.type = {
        out.writeByte(JsonConstants.TOKEN_ARRAY_END)
        this
    }

    protected def skipArrayEnd(in: Buffer): Boolean = {
        skipBlanks(in)
        in.skipIfNext(JsonConstants.TOKEN_ARRAY_END)
    }

    protected def serializeKey(key: String, out: Buffer): this.type = {
        out.writeByte(JsonConstants.TOKEN_DOUBLE_QUOTE)
        out.writeCharSequence(key, charsets)
        out.writeByte(JsonConstants.TOKEN_DOUBLE_QUOTE)
        out.writeByte(JsonConstants.TOKEN_COLON)
        this
    }

    override final protected def serializeByte(byte: Byte, out: Buffer): JsonSerde.this.type = serializeInt(byte, out)

    override final protected def serializeBoolean(boolean: Boolean, out: Buffer): JsonSerde.this.type = if (boolean) {
        out.writeBytes(JsonConstants.TOKEN_TURE)
        this
    } else {
        out.writeBytes(JsonConstants.TOKEN_FALSE)
        this
    }

    override final protected def serializeChar(char: Char, out: Buffer): JsonSerde.this.type = {

        this
    }

    override final protected def serializeShort(short: Short, out: Buffer): JsonSerde.this.type = {

        this
    }

    override final protected def serializeInt(int: Int, out: Buffer): JsonSerde.this.type = ???

    override final protected def serializeLong(long: Long, out: Buffer): JsonSerde.this.type = ???

    override final protected def serializeFloat(float: Float, out: Buffer): JsonSerde.this.type = ???

    override final protected def serializeDouble(double: Double, out: Buffer): JsonSerde.this.type = ???

    override final protected def serializeString(string: String, out: Buffer): JsonSerde.this.type = ???

    override final protected def deserializeByte(in: Buffer): Byte = ???

    override final protected def deserializeBoolean(in: Buffer): Boolean = ???

    override final protected def deserializeChar(in: Buffer): Char = ???

    override final protected def deserializeShort(in: Buffer): Short = ???

    override final protected def deserializeInt(in: Buffer): Short = ???

    override final protected def deserializeLong(in: Buffer): Long = ???

    override final protected def deserializeFloat(in: Buffer): Float = ???

    override final protected def deserializeDouble(in: Buffer): Double = ???

    override final protected def deserializeString(in: Buffer): String = ???

    // math type

    override final protected def serializeBigInt(bigInt: BigInt, out: Buffer): JsonSerde.this.type = ???

    override final protected def serializeBigDecimal(bigDecimal: BigDecimal, out: Buffer): JsonSerde.this.type = ???

    override final protected def serializeBigInteger(bigInteger: BigInteger, out: Buffer): JsonSerde.this.type = ???

    override final protected def serializeJBigDecimal(
        bigDecimal: java.math.BigDecimal,
        out: Buffer
    ): JsonSerde.this.type =
        ???

    override final protected def deserializeBigInt(in: Buffer): BigInt = ???

    override final protected def deserializeBigDecimal(in: Buffer): BigInt = ???

    override final protected def deserializeBigInteger(in: Buffer): BigInt = ???

    override final protected def deserializeJBigDecimal(in: Buffer): BigInt = ???

}

object JsonSerde {

    inline def derived[A](using m: Mirror.Of[A]): JsonSerde[A] = {
        ???
    }

}