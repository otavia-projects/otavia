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

/** Provides a mechanism to iterate over a collection of bytes. */
trait ByteProcessor {

    /** Process Buffer
     *  @return
     *    true if the processor wants to continue the loop and handle the next byte in the buffer. false if the
     *    processor wants to stop handling bytes and abort the loop.
     */
    def process(value: Byte): Boolean

}

object ByteProcessor {

    private val SPACE           = ' '.toByte
    private val HTAB            = '\t'.toByte
    private val CARRIAGE_RETURN = '\r'.toByte
    private val LINE_FEED       = '\n'.toByte

    /** A [[ByteProcessor]] which finds the first appearance of a specific byte. */
    class IndexOfProcessor(byteToFind: Byte) extends ByteProcessor {
        override def process(value: Byte): Boolean = value != byteToFind

    }

    /** A [[ByteProcessor]] which finds the first appearance which is not of a specific byte. */
    class IndexNotOfProcessor(byteToFind: Byte) extends ByteProcessor {
        override def process(value: Byte): Boolean = value == byteToFind

    }

    /** Aborts on a `NULL(0x00)`. */
    val FIND_NUL = new ByteProcessor.IndexOfProcessor(0.toByte)

    /** Aborts on a `non-NULL(0x00)`. */
    val FIND_NON_NUL = new ByteProcessor.IndexNotOfProcessor(0.toByte)

    /** Aborts on a `CR ('\r')`. */
    val FIND_CR = new ByteProcessor.IndexOfProcessor(CARRIAGE_RETURN)

    /** Aborts on a `non-CR ('\r')`. */
    val FIND_NON_CR = new ByteProcessor.IndexNotOfProcessor(CARRIAGE_RETURN)

    /** Aborts on a `LF ('\n')`. */
    val FIND_LF = new ByteProcessor.IndexOfProcessor(LINE_FEED)

    /** Aborts on a `non-LF ('\n')`. */
    val FIND_NON_LF = new ByteProcessor.IndexNotOfProcessor(LINE_FEED)

    /** Aborts on a semicolon `(';')`. */
    val FIND_SEMI_COLON = new ByteProcessor.IndexOfProcessor(';'.toByte)

    /** Aborts on a comma `(',')`. */
    val FIND_COMMA = new ByteProcessor.IndexOfProcessor(','.toByte)

    /** Aborts on a ascii space character (`' '`). */
    val FIND_ASCII_SPACE = new ByteProcessor.IndexOfProcessor(SPACE)

    /** Aborts on a `CR ('\r')` or a `LF ('\n')`. */
    val FIND_CRLF: ByteProcessor = (value: Byte) => value != CARRIAGE_RETURN && value != LINE_FEED

    /** Aborts on a byte which is neither a `CR ('\r')` nor a `LF ('\n')`. */
    val FIND_NON_CRLF: ByteProcessor = (value: Byte) => value == CARRIAGE_RETURN || value == LINE_FEED

    /** Aborts on a linear whitespace (a (`' '` or a `'\t'`). */
    val FIND_LINEAR_WHITESPACE: ByteProcessor = (value: Byte) => value != SPACE && value != HTAB

    /** Aborts on a byte which is not a linear whitespace (neither `' '` nor `'\t'`). */
    val FIND_NON_LINEAR_WHITESPACE: ByteProcessor = (value: Byte) => value == SPACE || value == HTAB

}
