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

package cc.otavia.postgres

import cc.otavia.adbc.Connection.Auth
import cc.otavia.adbc.Driver
import cc.otavia.adbc.Statement.ExecuteUpdate
import cc.otavia.buffer.Buffer
import cc.otavia.buffer.pool.AdaptiveBuffer
import cc.otavia.core.channel.{ChannelHandlerContext, ChannelInflight}
import cc.otavia.core.slf4a.{Logger, LoggerFactory}
import cc.otavia.postgres.protocol.Constants
import cc.otavia.postgres.utils.ScramAuthentication.ScramClientInitialMessage
import cc.otavia.postgres.utils.{BufferUtils, MD5Authentication, ScramAuthentication}

import java.nio.charset.StandardCharsets
import scala.language.unsafeNulls

class PostgresDriver(override val options: PostgresConnectOptions) extends Driver(options) {

    import PostgresDriver.*

    private var logger: Logger             = _
    private var ctx: ChannelHandlerContext = _

    private var status: Int = ST_CONNECTING

    private var scramAuthentication: ScramAuthentication = _
    private var encoding: String                         = _

    private var authMsgId = ChannelInflight.INVALID_CHANNEL_MESSAGE_ID

    final override protected def checkDecodePacket(buffer: Buffer): Boolean =
        if (buffer.readableBytes >= 5) {
            val start        = buffer.readerOffset
            val packetLength = buffer.getInt(start + 1) + 1
            if (buffer.readableBytes >= packetLength) true else false
        } else false

    override protected def encode(ctx: ChannelHandlerContext, output: AdaptiveBuffer, msg: AnyRef, msgId: Long): Unit =
        msg match
            case _: Auth =>
                if (status == ST_CONNECTING) {
                    authMsgId = msgId
                    sendStartupMessage()
                    status = ST_AUTHENTICATING
                }
            case executeUpdate: ExecuteUpdate =>
                if (status == ST_AUTHENTICATED) sendQuery(executeUpdate.sql)
            case _ => ???

    override protected def decode(ctx: ChannelHandlerContext, input: AdaptiveBuffer): Unit =
        while (checkDecodePacket(input)) {
            val packetStart = input.readerOffset

            val id     = input.readByte
            val length = input.readInt

            id match
                case Constants.MSG_TYPE_READY_FOR_QUERY  => decodeReadyForQuery(input)
                case Constants.MSG_TYPE_DATA_ROW         => decodeDataRow(input)
                case Constants.MSG_TYPE_COMMAND_COMPLETE => decodeCommandComplete(input)
                case Constants.MSG_TYPE_BIND_COMPLETE    => decodeBindComplete()
                case _                                   => decodeMessage(id, input)

            // skip remaining data
            if (input.readerOffset - packetStart < length + 1) input.readerOffset(packetStart + length + 1)
        }

    private def decodeMessage(id: Byte, payload: Buffer): Unit = {
        id match
            case Constants.MSG_TYPE_ROW_DESCRIPTION       => ???
            case Constants.MSG_TYPE_ERROR_RESPONSE        => ???
            case Constants.MSG_TYPE_NOTICE_RESPONSE       => ???
            case Constants.MSG_TYPE_AUTHENTICATION        => decodeAuthentication(payload)
            case Constants.MSG_TYPE_EMPTY_QUERY_RESPONSE  => ???
            case Constants.MSG_TYPE_PARSE_COMPLETE        => ???
            case Constants.MSG_TYPE_CLOSE_COMPLETE        => ???
            case Constants.MSG_TYPE_NO_DATA               => ???
            case Constants.MSG_TYPE_PORTAL_SUSPENDED      => ???
            case Constants.MSG_TYPE_PARAMETER_DESCRIPTION => ???
            case Constants.MSG_TYPE_PARAMETER_STATUS      => ???
            case Constants.MSG_TYPE_BACKEND_KEY_DATA      => ???
            case Constants.MSG_TYPE_NOTIFICATION_RESPONSE => ???
            case _                                        =>
    }

    private def sendStartupMessage(): Unit = {
        val packet = ctx.outboundAdaptiveBuffer

        val startIdx = packet.writerOffset
        packet.writeInt(0) // set payload length by calculation

        // protocol version
        packet.writeShort(3)
        packet.writeShort(0)

        BufferUtils.writeCString(packet, USER)
        BufferUtils.writeCString(packet, options.user, StandardCharsets.UTF_8)

        BufferUtils.writeCString(packet, DATABASE)
        BufferUtils.writeCString(packet, options.database, StandardCharsets.UTF_8)

        for ((key, value) <- options.properties) {
            BufferUtils.writeCString(packet, key, StandardCharsets.UTF_8)
            BufferUtils.writeCString(packet, value, StandardCharsets.UTF_8)
        }

        packet.writeByte(0)

        packet.setInt(startIdx, packet.writerOffset - startIdx)

        ctx.writeAndFlush(packet)
    }

    private def sendQuery(sql: String): Unit = {
        val packet = ctx.outboundAdaptiveBuffer
        packet.writeByte(QUERY)
        val pos = packet.writerOffset
        packet.writeInt(0)
        BufferUtils.writeCString(packet, sql, StandardCharsets.UTF_8)
        packet.setInt(pos, packet.writerOffset - pos)
    }

    private def decodeReadyForQuery(payload: Buffer): Unit = {
        ???
    }

    private def decodeDataRow(payload: Buffer): Unit = {
        ???
    }

    private def decodeCommandComplete(payload: Buffer): Unit = {
        ???
    }

    private def decodeBindComplete(): Unit = {
        ???
    }

    private def decodeAuthentication(payload: Buffer): Unit = {
        val typ = payload.readInt
        typ match
            case Constants.AUTH_TYPE_OK => successAuthAndResponse()
            case Constants.AUTH_TYPE_MD5_PASSWORD =>
                val salt = new Array[Byte](4)
                payload.readBytes(salt)
                sendPasswordMessage(salt)
            case Constants.AUTH_TYPE_CLEARTEXT_PASSWORD => sendPasswordMessage(null)
            case Constants.AUTH_TYPE_SASL =>
                scramAuthentication = new ScramAuthentication(options.user, options.password)
                val msg = scramAuthentication.initialSaslMsg(payload)
                sendScramClientInitialMessage(msg)
            case Constants.AUTH_TYPE_SASL_CONTINUE =>
                sendScramClientFinalMessage(scramAuthentication.recvServerFirstMsg(payload))
                logger.debug("sasl continue send")
            case Constants.AUTH_TYPE_SASL_FINAL =>
                try {
                    scramAuthentication.checkServerFinalMsg(payload, payload.readableBytes)
                    logger.debug("sasl final")
                } catch {
                    case e: UnsupportedOperationException => ???
                }
            case _ =>
                val error = new UnsupportedOperationException(
                  s"Authentication type $typ is not supported in the client"
                )

    }

    private def sendPasswordMessage(salt: Array[Byte] | Null): Unit = {
        val packet = ctx.outboundAdaptiveBuffer
        packet.writeByte(PASSWORD_MESSAGE)
        val pos = packet.writerOffset
        packet.writeInt(0)
        val hash =
            if (salt != null) MD5Authentication.encode(options.user, options.password, salt) else options.password
        BufferUtils.writeCString(packet, hash, StandardCharsets.UTF_8)
        packet.setInt(pos, packet.writerOffset - pos)

        ctx.writeAndFlush(packet)
    }

    private def sendScramClientInitialMessage(msg: ScramClientInitialMessage): Unit = {
        val packet = ctx.outboundAdaptiveBuffer
        packet.writeByte(PASSWORD_MESSAGE)
        val pos = packet.writerOffset
        packet.writeInt(0)
        BufferUtils.writeCString(packet, msg.mechanism, StandardCharsets.UTF_8)

        val msgPos = packet.writerOffset
        packet.writeInt(0)
        packet.writeCharSequence(msg.message, StandardCharsets.UTF_8)

        // rewind to set the message and total length
        packet.setInt(msgPos, packet.writerOffset - msgPos - Integer.BYTES)
        packet.setInt(pos, packet.writerOffset - pos)

        ctx.writeAndFlush(packet)
    }

    private def sendScramClientFinalMessage(msg: String): Unit = {
        val packet = ctx.outboundAdaptiveBuffer
        packet.writeByte(PASSWORD_MESSAGE)
        val pos = packet.writerOffset
        packet.writeInt(0)
        packet.writeCharSequence(msg, StandardCharsets.UTF_8)

        packet.setInt(pos, packet.writerOffset - pos)

        ctx.writeAndFlush(packet)
    }

    override def handlerAdded(ctx: ChannelHandlerContext): Unit = {
        super.handlerAdded(ctx)
        this.ctx = ctx
        logger = LoggerFactory.getLogger(getClass, ctx.system)
    }

    private def successAuthAndResponse(): Unit = {
        status = ST_AUTHENTICATED
        if (authMsgId != ChannelInflight.INVALID_CHANNEL_MESSAGE_ID) ctx.fireChannelRead(None, authMsgId)
    }

}

object PostgresDriver {

    private val ST_CONNECTING          = 0
    private val ST_AUTHENTICATING      = 1
    private val ST_CONNECTED           = 2
    private val ST_AUTHENTICATED       = 4
    private val ST_AUTHENTICATE_FAILED = 5
    private val ST_CLOSING             = 6

    private val USER: Array[Byte]     = "user".getBytes(StandardCharsets.UTF_8)
    private val DATABASE: Array[Byte] = "database".getBytes(StandardCharsets.UTF_8)

    private val PASSWORD_MESSAGE: Byte = 'p'
    private val QUERY: Byte            = 'Q'
    private val TERMINATE: Byte        = 'X'
    private val PARSE: Byte            = 'P'
    private val BIND: Byte             = 'B'
    private val DESCRIBE: Byte         = 'D'
    private val EXECUTE: Byte          = 'E'
    private val CLOSE: Byte            = 'C'
    private val SYNC: Byte             = 'S'

}
