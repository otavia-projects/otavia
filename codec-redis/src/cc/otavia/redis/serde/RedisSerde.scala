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

package cc.otavia.redis.serde

import cc.otavia.buffer.{Buffer, BufferUtils}
import cc.otavia.redis.RedisProtocolException
import cc.otavia.redis.cmd.*
import cc.otavia.redis.serde.RedisSerde.*
import cc.otavia.serde.Serde

import java.nio.charset.StandardCharsets
import scala.language.unsafeNulls

trait RedisSerde[T <: Command[?] | CommandResponse] extends Serde[T] {

    final protected def serializeBulkString(bulk: String, out: Buffer): this.type = {
        out.writeByte('$')
        val bytes = bulk.getBytes(StandardCharsets.UTF_8)
        out.writeCharSequence(bytes.length.toString)
        serializeCRLF(out)
        out.writeBytes(bytes)
        serializeCRLF(out)
        this
    }

    final protected def deserializeBulkString(in: Buffer): String = if (in.skipIfNextIs('$')) {
        val strLen = BufferUtils.readStringAsInt(in)
        in.skipReadableBytes(2) // skip "\r\n"
        val string = if (strLen != 0) in.readCharSequence(strLen).toString else ""
        in.skipReadableBytes(2)
        string
    } else throw new RedisProtocolException(s"except byte '$$' but get '${in.getByte(in.readerOffset)}'")

    final protected def serializeInteger(value: Long, out: Buffer): this.type = {
        out.writeByte(':')
        out.writeCharSequence(value.toString)
        serializeCRLF(out)
        this
    }

    final protected def deserializeInteger(in: Buffer): Long = if (in.skipIfNextIs(':')) {
        val int = BufferUtils.readStringAsLong(in)
        in.skipReadableBytes(2) // skip "\r\n"
        int
    } else throw new RedisProtocolException(s"except byte ':' but get '${in.getByte(in.readerOffset)}'")

    protected def serializeArrayHeader(len: Int, out: Buffer): this.type = {
        out.writeByte('*')
        out.writeCharSequence(len.toString)
        serializeCRLF(out)
        this
    }

    protected def deserializeArrayHeader(in: Buffer): Int = if (in.skipIfNextIs('*')) {

        ???
    } else throw new RedisProtocolException(s"except byte '*' but get '${in.getByte(in.readerOffset)}'")

    final protected def serializeCRLF(out: Buffer): this.type = {
        out.writeByte('\r')
        out.writeByte('\n')
        this
    }

    /** serialize Null in RESP3
     *  @param out
     *    output [[Buffer]]
     *  @return
     *    this serde
     */
    final protected def serializeNull(out: Buffer): this.type = {
        out.writeByte('_')
        serializeCRLF(out)
    }

    /** deserialize Null in RESP3
     *  @param in
     *    input [[Buffer]]
     *  @return
     *    [[None]]
     */
    final protected def deserializeNull(in: Buffer): None.type =
        if (in.skipIfNextIs('_') && in.skipIfNextAre(CRLF)) None
        else
            throw new RedisProtocolException(s"RESP3 Null except byte '_' but get '${in.getByte(in.readerOffset)}'")

    /** deserialize Double in RESP3
     *  @param double
     *    double value
     *  @param out
     *    output [[Buffer]]
     *  @return
     *    this
     */
    final protected def serializeDouble(double: Double, out: Buffer): this.type = {
        if (double == Double.PositiveInfinity) out.writeBytes(INF)
        else if (double == Double.NegativeInfinity) out.writeBytes(NE_INF)
        else {
            out.writeByte(',')
            out.writeCharSequence(double.toString)
            out.writeBytes(CRLF)
        }
        this
    }

    /** deserialize Double in RESP3
     *  @param in
     *    input [[Buffer]]
     *  @return
     *    double value
     */
    final protected def deserializeDouble(in: Buffer): Double =
        if (in.skipIfNextAre(INF)) Double.PositiveInfinity
        else if (in.skipIfNextAre(NE_INF)) Double.NegativeInfinity
        else if (in.skipIfNextIs(',')) {
            val double = in.readCharSequence(in.bytesBefore(CRLF)).toString.toDouble
            in.skipReadableBytes(2)
            double
        } else
            throw new RedisProtocolException(s"RESP3 Double except byte ',' but get '${in.getByte(in.readerOffset)}'")

    /** serialize Boolean in RESP3
     *  @param boolean
     *    boolean value
     *  @param out
     *    output [[Buffer]]
     *  @return
     *    this
     */
    final protected def serializeBoolean(boolean: Boolean, out: Buffer): this.type = {
        if (boolean) out.writeBytes(TRUE) else out.writeBytes(FALSE)
        this
    }

    /** deserialize Boolean in RESP3
     *  @param in
     *    input [[Buffer]]
     *  @return
     *    boolean value
     */
    final protected def deserializeBoolean(in: Buffer): Boolean =
        if (in.skipIfNextAre(TRUE)) true
        else if (in.skipIfNextAre(FALSE)) false
        else
            throw new RedisProtocolException(s"RESP3 Boolean except byte '#' but get '${in.getByte(in.readerOffset)}'")

    /** serialize Blob Error in RESP3
     *
     *  @param error
     *    error message
     *  @param out
     *    output [[Buffer]]
     *  @return
     *    this
     */
    final protected def serializeBlobError(error: String, out: Buffer): this.type = {
        out.writeByte('!')
        val bytes = error.getBytes(StandardCharsets.UTF_8)
        out.writeCharSequence(bytes.length.toString)
        out.writeBytes(CRLF)
        out.writeBytes(bytes)
        out.writeBytes(CRLF)
        this
    }

    /** deserialize Blob Error in RESP3
     *
     *  @param in
     *    input [[Buffer]]
     *  @return
     *    error message
     */
    final protected def deserializeBlobError(in: Buffer): String = if (in.skipIfNextIs('!')) {
        val strLen = in.readCharSequence(in.bytesBefore(CRLF)).toString.toInt
        in.skipReadableBytes(2)
        val error = in.readCharSequence(strLen).toString
        if (in.skipIfNextAre(CRLF)) {} else
            throw new RedisProtocolException(
              s"RESP3 Blob Error except bytes '<CR><LF>' but get '${in.getBytes(in.readerOffset, 2).mkString("'", ", ", "'")}'"
            )
        error
    } else
        throw new RedisProtocolException(s"RESP3 Blob Error except byte '!' but get '${in.getByte(in.readerOffset)}'")

    /** serialize Verbatim String in RESP3
     *
     *  @param verbatim
     *    Verbatim String
     *  @param out
     *    output [[Buffer]]
     *  @return
     *    this
     */
    final protected def serializeVerbatimString(verbatim: String, out: Buffer): this.type = {
        out.writeByte('=')
        val bytes = verbatim.getBytes(StandardCharsets.UTF_8)
        out.writeCharSequence(bytes.length.toString)
        out.writeBytes(CRLF)

        out.writeBytes(bytes)
        out.writeBytes(CRLF)
        this
    }

    /** deserialize Verbatim String in RESP3
     *
     *  @param in
     *    input [[Buffer]]
     *  @return
     *    verbatim string
     */
    final protected def deserializeVerbatimString(in: Buffer): String = if (in.skipIfNextIs('=')) {
        val strLen = in.readCharSequence(in.bytesBefore(CRLF)).toString.toInt
        in.skipReadableBytes(2)
        val verbatim = in.readCharSequence(strLen).toString
        if (in.skipIfNextAre(CRLF)) {} else
            throw new RedisProtocolException(
              s"RESP3 Verbatim String except bytes '<CR><LF>' but get '${in.getBytes(in.readerOffset, 2).mkString("'", ", ", "'")}'"
            )
        verbatim
    } else
        throw new RedisProtocolException(
          s"RESP3 Verbatim String except byte '=' but get '${in.getByte(in.readerOffset)}'"
        )

    /** serialize Big number in RESP3
     *
     *  @param bigInt
     *    Big number
     *  @param out
     *    output [[Buffer]]
     *  @return
     *    this
     */
    final protected def serializeBigInt(bigInt: BigInt, out: Buffer): this.type = {
        out.writeByte('(')
        out.writeCharSequence(bigInt.toString())
        out.writeBytes(CRLF)
        this
    }

    /** deserialize Big number in RESP3
     *
     *  @param in
     *    input [[Buffer]]
     *  @return
     *    Big number
     */
    final protected def deserializeBigInt(in: Buffer): BigInt = if (in.skipIfNextIs('(')) {
        val str = in.readCharSequence(in.bytesBefore(CRLF)).toString
        in.skipReadableBytes(2)
        BigInt(str)
    } else
        throw new RedisProtocolException(
          s"RESP3 Big number except byte '(' but get '${in.getByte(in.readerOffset)}'"
        )

    final protected def checkInteger(index: Int, in: Buffer): Boolean = if (in.getByte(index) == ':') {

        ???
    } else false

    final protected def checkBulkString(index: Int, in: Buffer): Boolean = if (in.getByte(index) == '$') {
        ???
    } else false

    final protected def checkArray(index: Int, in: Buffer): Boolean = if (in.getByte(index) == '*') {
        ???
    } else false

}

object RedisSerde {

    private val CRLF: Array[Byte]   = Array('\r', '\n')
    private val INF: Array[Byte]    = ",inf\r\n".getBytes()
    private val NE_INF: Array[Byte] = ",-inf\r\n".getBytes()

    private val TRUE: Array[Byte]  = "#t\r\n".getBytes()
    private val FALSE: Array[Byte] = "#f\r\n".getBytes()

}
