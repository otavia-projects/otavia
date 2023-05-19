/*
 * Copyright 2022 Yan Kun <yan_kun_1992@foxmail.com>
 *
 * This file fork from netty.
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

package io.otavia.buffer

import sun.misc.Unsafe
import sun.nio.ch.DirectBuffer as JDirectBuffer

import java.nio.ByteBuffer
import java.nio.channels.{FileChannel, ReadableByteChannel, WritableByteChannel}
import java.nio.charset.Charset
import scala.language.unsafeNulls

class DirectBuffer(underlying: ByteBuffer) extends AbstractBuffer(underlying) {

    private val address: Long = underlying.asInstanceOf[JDirectBuffer].address()

    override def fill(value: Byte): Buffer = {
        Util.Unsafe.setMemory(address, capacity, value)
        this
    }

    override def isDirect: Boolean = true

    override def writeCharSequence(source: CharSequence, charset: Charset): Buffer = ???

    override def readCharSequence(length: Int, charset: Charset): CharSequence = ???

    override def writeBytes(source: Buffer): Buffer = ???

    override def writeBytes(source: Array[Byte], srcPos: Int, length: Int): Buffer = ???

    override def writeBytes(source: ByteBuffer): Buffer = ???

    override def readBytes(destination: ByteBuffer): Buffer = ???

    override def readBytes(destination: Array[Byte], destPos: Int, length: Int): Buffer = ???

    override def bytesBefore(needle: Byte): Int = ???

    override def bytesBefore(needle1: Byte, needle2: Byte): Int = ???

    override def bytesBefore(needle1: Byte, needle2: Byte, needle3: Byte): Int = ???

    override def bytesBefore(needle: Buffer): Int = ???

    override def openCursor(fromOffset: Int, length: Int): ByteCursor = ???

    override def openReverseCursor(fromOffset: Int, length: Int): ByteCursor = ???

    override def ensureWritable(size: Int, minimumGrowth: Int, allowCompaction: Boolean): Buffer = ???

    override def readByte: Byte = ???

    override def getByte(ridx: Int): Byte = underlying.get(ridx)

    override def readUnsignedByte: Int = ???

    override def getUnsignedByte(roff: Int): Int = ???

    override def writeByte(value: Byte): Buffer = ???

    override def setByte(woff: Int, value: Byte): Buffer = ???

    override def writeUnsignedByte(value: Int): Buffer = ???

    override def setUnsignedByte(woff: Int, value: Int): Buffer = ???

    override def readChar: Char = ???

    override def getChar(roff: Int): Char = ???

    override def writeChar(value: Char): Buffer = ???

    override def setChar(woff: Int, value: Char): Buffer = ???

    override def readShort: Short = ???

    override def getShort(roff: Int): Short = ???

    override def readUnsignedShort: Int = ???

    override def getUnsignedShort(roff: Int): Int = ???

    override def writeShort(value: Short): Buffer = ???

    override def setShort(woff: Int, value: Short): Buffer = ???

    override def writeUnsignedShort(value: Int): Buffer = ???

    override def setUnsignedShort(woff: Int, value: Int): Buffer = ???

    override def readMedium: Int = ???

    override def getMedium(roff: Int): Int = ???

    override def readUnsignedMedium: Int = ???

    override def getUnsignedMedium(roff: Int): Int = ???

    override def writeMedium(value: Int): Buffer = ???

    override def setMedium(woff: Int, value: Int): Buffer = ???

    override def writeUnsignedMedium(value: Int): Buffer = ???

    override def setUnsignedMedium(woff: Int, value: Int): Buffer = ???

    override def readInt: Int = ???

    override def getInt(roff: Int): Int = ???

    override def readUnsignedInt: Long = ???

    override def getUnsignedInt(roff: Int): Long = ???

    override def writeInt(value: Int): Buffer = ???

    override def setInt(woff: Int, value: Int): Buffer = ???

    override def writeUnsignedInt(value: Long): Buffer = ???

    override def setUnsignedInt(woff: Int, value: Long): Buffer = ???

    override def readFloat: Float = ???

    override def getFloat(roff: Int): Float = ???

    override def writeFloat(value: Float): Buffer = ???

    override def setFloat(woff: Int, value: Float): Buffer = ???

    override def readLong: Long = ???

    override def getLong(roff: Int): Long = ???

    override def writeLong(value: Long): Buffer = ???

    override def setLong(woff: Int, value: Long): Buffer = ???

    override def readDouble: Double = ???

    override def getDouble(roff: Int): Double = ???

    override def writeDouble(value: Double): Buffer = ???

    override def setDouble(woff: Int, value: Double): Buffer = ???

}
