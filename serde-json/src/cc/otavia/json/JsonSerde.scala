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
import cc.otavia.json.primaries.*
import cc.otavia.serde.{Serde, SerdeMathTypeOps, SerdeOps, SerdePrimaryTypeOps}

import java.math.BigInteger
import java.nio.charset.{Charset, StandardCharsets}
import scala.collection.mutable
import scala.compiletime.*
import scala.deriving.Mirror
import scala.language.unsafeNulls

trait JsonSerde[A] extends Serde[A] with SerdePrimaryTypeOps with SerdeMathTypeOps {

    def charsets: Charset = StandardCharsets.UTF_8

    protected def skipBlanks(in: Buffer): Unit =
        while (in.skipIfNextIn(JsonConstants.TOKEN_BLANKS)) {}

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
        out.writeByte(JsonConstants.TOKEN_DOUBLE_QUOTE)
        out.writeByte(char.toByte)
        out.writeByte(JsonConstants.TOKEN_DOUBLE_QUOTE)
        this
    }

    override final protected def serializeShort(short: Short, out: Buffer): JsonSerde.this.type = {
        out.writeCharSequence(short.toString)
        this
    }

    override final protected def serializeInt(int: Int, out: Buffer): JsonSerde.this.type = {
        out.writeCharSequence(int.toString)
        this
    }

    override final protected def serializeLong(long: Long, out: Buffer): JsonSerde.this.type = {
        out.writeCharSequence(long.toString)
        this
    }

    override final protected def serializeFloat(float: Float, out: Buffer): JsonSerde.this.type = {
        out.writeCharSequence(float.toString)
        this
    }

    override final protected def serializeDouble(double: Double, out: Buffer): JsonSerde.this.type = {
        out.writeCharSequence(double.toString)
        this
    }

    override final protected def serializeString(string: String, out: Buffer): JsonSerde.this.type = {
        out.writeByte(JsonConstants.TOKEN_DOUBLE_QUOTE)
        out.writeCharSequence(string, charsets) // TODO: escape char
        out.writeByte(JsonConstants.TOKEN_DOUBLE_QUOTE)
        this
    }

    override final protected def deserializeByte(in: Buffer): Byte = deserializeInt(in).toByte

    override final protected def deserializeBoolean(in: Buffer): Boolean = {
        skipBlanks(in)
        if (in.skipIfNexts(JsonConstants.TOKEN_TURE)) true
        else if (in.skipIfNexts(JsonConstants.TOKEN_FALSE)) false
        else throw new JsonFormatException()
    }

    override final protected def deserializeChar(in: Buffer): Char = {
        skipBlanks(in)
        assert(in.skipIfNext(JsonConstants.TOKEN_DOUBLE_QUOTE), s"except \" but get ${in.readByte}")
        val b = in.readByte
        assert(in.skipIfNext(JsonConstants.TOKEN_DOUBLE_QUOTE), s"except \" but get ${in.readByte}")
        b.toChar
    }

    override final protected def deserializeShort(in: Buffer): Short = deserializeInt(in).toShort

    override final protected def deserializeInt(in: Buffer): Int = {
        skipBlanks(in)
        in.skipIfNext(JsonConstants.TOKEN_PLUS)
        val minus    = in.skipIfNext(JsonConstants.TOKEN_MINUS)
        var ret: Int = 0
        while (in.readableBytes > 0 && in.nextIn(JsonConstants.TOKEN_NUMBERS)) {
            val b = in.readByte
            ret = ret * 10 + (b - JsonConstants.TOKEN_ZERO)
        }
        if (minus) -ret else ret
    }

    override final protected def deserializeLong(in: Buffer): Long = {
        skipBlanks(in)
        in.skipIfNext(JsonConstants.TOKEN_PLUS)
        val minus     = in.skipIfNext(JsonConstants.TOKEN_MINUS)
        var ret: Long = 0
        while (in.readableBytes > 0 && in.nextIn(JsonConstants.TOKEN_NUMBERS)) {
            val b = in.readByte
            ret = ret * 10L + (b - JsonConstants.TOKEN_ZERO)
        }
        if (minus) -ret else ret
    }

    override final protected def deserializeFloat(in: Buffer): Float = {
        skipBlanks(in)
        in.skipIfNext(JsonConstants.TOKEN_PLUS)
        val minus               = in.skipIfNext(JsonConstants.TOKEN_MINUS)
        var intPart: Float      = 0
        var floatPart: Float    = 0f
        var startFloat: Boolean = false
        var floatIdx: Float     = 0
        while (in.readableBytes > 0 && in.nextIn(JsonConstants.TOKEN_FLOATS)) {
            val b = in.readByte
            if (b == JsonConstants.TOKEN_POINT) startFloat = true
            else {
                if (!startFloat) intPart = intPart * 10f + (b - JsonConstants.TOKEN_ZERO)
                else {
                    floatIdx += 1
                    floatPart = floatPart + ((b - JsonConstants.TOKEN_ZERO).toFloat / Math.pow(10f, floatIdx).toFloat)
                }
            }
        }
        if (minus) -(intPart + floatPart) else intPart + floatPart
    }

    override final protected def deserializeDouble(in: Buffer): Double = {
        skipBlanks(in)
        in.skipIfNext(JsonConstants.TOKEN_PLUS)
        val minus               = in.skipIfNext(JsonConstants.TOKEN_MINUS)
        var intPart: Double     = 0d
        var floatPart: Double   = 0d
        var startFloat: Boolean = false
        var floatIdx: Double    = 0d
        while (in.readableBytes > 0 && in.nextIn(JsonConstants.TOKEN_FLOATS)) {
            val b = in.readByte
            if (b == JsonConstants.TOKEN_POINT) startFloat = true
            else {
                if (!startFloat) intPart = intPart * 10d + (b - JsonConstants.TOKEN_ZERO)
                else {
                    floatIdx += 1
                    floatPart = floatPart + ((b - JsonConstants.TOKEN_ZERO).toFloat / Math.pow(10d, floatIdx))
                }
            }
        }
        if (minus) -(intPart + floatPart) else intPart + floatPart
    }

    override final protected def deserializeString(in: Buffer): String = {
        skipBlanks(in)
        assert(in.skipIfNext(JsonConstants.TOKEN_DOUBLE_QUOTE), s"except \" but get ${in.readByte}")
        val len = in.bytesBefore(JsonConstants.TOKEN_DOUBLE_QUOTE) // TODO: escape
        val str = in.readCharSequence(len, charsets).toString
        in.readByte
        str
    }

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

    private given Charset = StandardCharsets.UTF_8

    given JsonSerde[Boolean] = BooleanJsonSerde
    given JsonSerde[Byte]    = ByteJsonSerde
    given JsonSerde[Char]    = CharJsonSerde
    given JsonSerde[Double]  = DoubleJsonSerde
    given JsonSerde[Float]   = FloatJsonSerde
    given JsonSerde[Int]     = IntJsonSerde
    given JsonSerde[Long]    = LongJsonSerde
    given JsonSerde[Short]   = ShortJsonSerde

    given stringSerde(using charset: Charset): JsonSerde[String] with {

        private val serde =
            if (charset == StandardCharsets.UTF_8) StringJsonSerde.UTF8StringJsonSerde
            else new StringJsonSerde(charset)

        override def deserialize(in: Buffer): String = serde.deserialize(in)

        override def serialize(value: String, out: Buffer): Unit = serde.serialize(value, out)

    }

    given seqSerde[T](using se: JsonSerde[T]): JsonSerde[Seq[T]] with {

        override def deserialize(in: Buffer): Seq[T] = {
            val seq = mutable.Seq.empty[T]
            skipBlanks(in)
            assert(in.skipIfNext(JsonConstants.TOKEN_ARRAY_START), "")
            while (!in.skipIfNext(JsonConstants.TOKEN_ARRAY_END)) {
                skipBlanks(in)
                seq.appended(se.deserialize(in))
                skipBlanks(in)
                in.skipIfNext(JsonConstants.TOKEN_COMMA)
            }
            seq.toSeq
        }

        override def serialize(value: Seq[T], out: Buffer): Unit = {
            if (value.isEmpty) {
                serializeArrayStart(out)
                serializeArrayEnd(out)
            } else {
                serializeArrayStart(out)
                for (elem <- value) {
                    se.serialize(elem, out)
                    out.writeByte(JsonConstants.TOKEN_COMMA)
                }
                out.writerOffset(out.writerOffset - 1)
                serializeArrayEnd(out)
            }
        }

    }

    given mutableSeqSerde[T](using se: JsonSerde[T]): JsonSerde[mutable.Seq[T]] with {

        override def deserialize(in: Buffer): mutable.Seq[T] = {
            val seq = mutable.Seq.empty[T]
            skipBlanks(in)
            assert(in.skipIfNext(JsonConstants.TOKEN_ARRAY_START), "")
            while (!in.skipIfNext(JsonConstants.TOKEN_ARRAY_END)) {
                skipBlanks(in)
                seq.appended(se.deserialize(in))
                skipBlanks(in)
                in.skipIfNext(JsonConstants.TOKEN_COMMA)
            }
            seq
        }

        override def serialize(value: mutable.Seq[T], out: Buffer): Unit = if (value.isEmpty) {
            serializeArrayStart(out)
            serializeArrayEnd(out)
        } else {
            serializeArrayStart(out)
            for (elem <- value) {
                se.serialize(elem, out)
                out.writeByte(JsonConstants.TOKEN_COMMA)
            }
            out.writerOffset(out.writerOffset - 1)
            serializeArrayEnd(out)
        }

    }

    given optionSerde[T](using se: JsonSerde[T]): JsonSerde[Option[T]] with {

        override def deserialize(in: Buffer): Option[T] = {
            skipBlanks(in)
            if (in.skipIfNexts(JsonConstants.TOKEN_NULL)) None else Some(se.deserialize(in))
        }

        override def serialize(value: Option[T], out: Buffer): Unit = value match
            case None        => out.writeBytes(JsonConstants.TOKEN_NULL)
            case Some(value) => se.serialize(value, out)

    }

}
