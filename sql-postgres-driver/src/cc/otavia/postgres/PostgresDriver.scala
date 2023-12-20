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

import cc.otavia.buffer.Buffer
import cc.otavia.buffer.pool.AdaptiveBuffer
import cc.otavia.core.channel.{Channel, ChannelHandlerContext, ChannelInflight}
import cc.otavia.core.slf4a.{Logger, LoggerFactory}
import cc.otavia.postgres.impl.*
import cc.otavia.postgres.protocol.{Constants, DataFormat, DataType}
import cc.otavia.postgres.utils.ScramAuthentication.ScramClientInitialMessage
import cc.otavia.postgres.utils.{MD5Authentication, PgBufferUtils, ScramAuthentication}
import cc.otavia.sql.Statement.{ExecuteQueries, ExecuteQuery, ExecuteUpdate, ModifyRows}
import cc.otavia.sql.{Authentication, Driver, Row, RowDecoder}

import java.nio.charset.StandardCharsets
import scala.collection.mutable
import scala.language.unsafeNulls

class PostgresDriver(override val options: PostgresConnectOptions) extends Driver(options) {

    import PostgresDriver.*

    private var logger: Logger             = _
    private var ctx: ChannelHandlerContext = _

    private var status: Int = ST_CONNECTING

    private var scramAuthentication: ScramAuthentication = _
    private var encoding: String                         = _

    private var currentOutboundMessageId = ChannelInflight.INVALID_CHANNEL_MESSAGE_ID

    private var metadata: PostgresDatabaseMetadata = _
    private var processId: Int                     = 0
    private var secretKey: Int                     = 0

    private val response: Response = new Response

    private val rowDesc: RowDesc                           = new RowDesc()
    private var continueParseRow: Boolean                  = false
    private var currentRowDecoder: RowDecoder[Row]         = _
    private var isSingleRow: Boolean                       = true
    private val rowBuffer: mutable.ArrayBuffer[Row]        = mutable.ArrayBuffer.empty
    private val rowOffsets: mutable.ArrayBuffer[RowOffset] = mutable.ArrayBuffer.empty

    private val rowParser: PostgresRowParser = new PostgresRowParser()

    override def setChannelOptions(channel: Channel): Unit = {
        // TODO:
    }

    final override protected def checkDecodePacket(buffer: Buffer): Boolean =
        if (buffer.readableBytes >= 5) {
            val start        = buffer.readerOffset
            val packetLength = buffer.getInt(start + 1) + 1
            if (buffer.readableBytes >= packetLength) true else false
        } else false

    override protected def encode(ctx: ChannelHandlerContext, output: AdaptiveBuffer, msg: AnyRef, mid: Long): Unit = {
        currentOutboundMessageId = mid
        msg match
            case _: Authentication =>
                if (status == ST_CONNECTING) {
                    sendStartupMessage()
                    status = ST_AUTHENTICATING
                }
            case executeUpdate: ExecuteUpdate =>
                encodeQuery(executeUpdate.sql, output)
            case executeQuery: ExecuteQuery[?] =>
                currentRowDecoder = executeQuery.decoder
                isSingleRow = true
                encodeQuery(executeQuery.sql, output)
            case executeQueries: ExecuteQueries[?] =>
                currentRowDecoder = executeQueries.decoder
                isSingleRow = false
                encodeQuery(executeQueries.sql, output)
            case _ => ???
    }

    override protected def decode(ctx: ChannelHandlerContext, input: AdaptiveBuffer): Unit = {
        while (checkDecodePacket(input)) {
            val packetStart = input.readerOffset

            val id     = input.readByte
            val length = input.readInt

            id match
                case Constants.MSG_TYPE_READY_FOR_QUERY       => decodeReadyForQuery(input)
                case Constants.MSG_TYPE_DATA_ROW              => decodeDataRow(input, length)
                case Constants.MSG_TYPE_COMMAND_COMPLETE      => decodeCommandComplete(input, length)
                case Constants.MSG_TYPE_BIND_COMPLETE         => decodeBindComplete()
                case Constants.MSG_TYPE_ROW_DESCRIPTION       => decodeRowDescription(input)
                case Constants.MSG_TYPE_ERROR_RESPONSE        => decodeErrorResponse(input, length)
                case Constants.MSG_TYPE_NOTICE_RESPONSE       => ???
                case Constants.MSG_TYPE_AUTHENTICATION        => decodeAuthentication(input, length)
                case Constants.MSG_TYPE_EMPTY_QUERY_RESPONSE  => ???
                case Constants.MSG_TYPE_PARSE_COMPLETE        => ???
                case Constants.MSG_TYPE_CLOSE_COMPLETE        => ???
                case Constants.MSG_TYPE_NO_DATA               => ???
                case Constants.MSG_TYPE_PORTAL_SUSPENDED      => ???
                case Constants.MSG_TYPE_PARAMETER_DESCRIPTION => ???
                case Constants.MSG_TYPE_PARAMETER_STATUS      => decodeParameterStatus(input)
                case Constants.MSG_TYPE_BACKEND_KEY_DATA      => decodeBackendKeyData(input)
                case Constants.MSG_TYPE_NOTIFICATION_RESPONSE => ???
                case _                                        =>

            // skip remaining data
            if (input.readerOffset - packetStart < length + 1) input.readerOffset(packetStart + length + 1)
        }
        if (input.readableBytes == 0) input.compact()
    }

    private def sendStartupMessage(): Unit = {
        val packet = ctx.outboundAdaptiveBuffer

        val startIdx = packet.writerOffset
        packet.writeInt(0) // set payload length by calculation

        // protocol version
        packet.writeShort(3)
        packet.writeShort(0)

        PgBufferUtils.writeCString(packet, USER)
        PgBufferUtils.writeCString(packet, options.user, StandardCharsets.UTF_8)

        PgBufferUtils.writeCString(packet, DATABASE)
        PgBufferUtils.writeCString(packet, options.database, StandardCharsets.UTF_8)

        for ((key, value) <- options.properties) {
            PgBufferUtils.writeCString(packet, key, StandardCharsets.UTF_8)
            PgBufferUtils.writeCString(packet, value, StandardCharsets.UTF_8)
        }

        packet.writeByte(0)

        packet.setInt(startIdx, packet.writerOffset - startIdx)

    }

    private def encodeQuery(sql: String, output: Buffer): Unit = {
        val packet = output
        packet.writeByte(QUERY)
        val pos = packet.writerOffset
        packet.writeInt(0)
        PgBufferUtils.writeCString(packet, sql, StandardCharsets.UTF_8)
        packet.setInt(pos, packet.writerOffset - pos)
    }

    private def decodeReadyForQuery(payload: Buffer): Unit = {
        val id = payload.readByte
        if (id == I) {
            // IDLE
        } else if (id == T) {
            // ACTIVE
        } else {
            // FAILED
            ctx.fireChannelExceptionCaught(new TransactionFailed, currentOutboundMessageId)
        }
        // fire ?
    }

    private def decodeDataRow(payload: Buffer, payloadLength: Int): Unit =
        if (continueParseRow) {
            rowOffsets.clear()
            val len    = payload.readUnsignedShort
            var i      = 0
            var offset = 0
            while (i < len) {
                val length = payload.getInt(payload.readerOffset + offset)
                rowOffsets.addOne(RowOffset(offset, length))
                if (length > 0) offset += 4 + length else offset += 4
                i += 1
            }
            rowParser.setPayload(payload)
            rowParser.setRowOffsets(rowOffsets)
            val row = currentRowDecoder.decode(rowParser)
            println(row)
        }

    private def decodeRowDescription(payload: Buffer): Unit = {
        val columnLength = payload.readUnsignedShort
        this.rowDesc.setLength(columnLength)
        var i = 0
        while (i < columnLength) {
            val columnDesc = this.rowDesc(i)
            columnDesc.name = PgBufferUtils.readCString(payload)
            columnDesc.relationId = payload.readInt
            columnDesc.relationAttributeNo = payload.readShort
            columnDesc.dataType = DataType.fromOid(payload.readInt)
            columnDesc.length = payload.readShort
            columnDesc.typeModifier = payload.readInt
            columnDesc.dataFormat = DataFormat.fromOrdinal(payload.readUnsignedShort)
            i += 1
        }
        rowParser.setRowDesc(rowDesc)
        continueParseRow = true
    }

    private def decodeCommandComplete(payload: Buffer, length: Int): Unit = {
        if (payload.skipIfNextAre(CMD_COMPLETED_UPDATE)) {
            val rows = payload.readStringAsLong(length - 5 - CMD_COMPLETED_UPDATE.length).toInt
            ctx.fireChannelRead(ModifyRows(rows))
        } else if (payload.skipIfNextAre(CMD_COMPLETED_DELETE))
            val rows = payload.readStringAsLong(length - 5 - CMD_COMPLETED_DELETE.length).toInt
            ctx.fireChannelRead(ModifyRows(rows))
        else if (payload.skipIfNextAre(CMD_COMPLETED_INSERT))
            val rows = payload.readStringAsLong(length - 5 - CMD_COMPLETED_INSERT.length).toInt
            ctx.fireChannelRead(ModifyRows(rows))
    }

    private def decodeBindComplete(): Unit = {
        ???
    }

    private def decodeAuthentication(payload: Buffer, length: Int): Unit = {
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
                    scramAuthentication.checkServerFinalMsg(payload, length - 8)
                    logger.debug("sasl final")
                } catch {
                    case e: UnsupportedOperationException => ctx.fireChannelExceptionCaught(e, currentOutboundMessageId)
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
        PgBufferUtils.writeCString(packet, hash, StandardCharsets.UTF_8)
        packet.setInt(pos, packet.writerOffset - pos)

        ctx.writeAndFlush(packet)
    }

    private def sendScramClientInitialMessage(msg: ScramClientInitialMessage): Unit = {
        val packet = ctx.outboundAdaptiveBuffer
        packet.writeByte(PASSWORD_MESSAGE)
        val pos = packet.writerOffset
        packet.writeInt(0)
        PgBufferUtils.writeCString(packet, msg.mechanism, StandardCharsets.UTF_8)

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

    private def successAuthAndResponse(): Unit = {
        status = ST_AUTHENTICATED
        if (ChannelInflight.isValidChannelMessageId(currentOutboundMessageId))
            ctx.fireChannelRead(None, currentOutboundMessageId)
    }

    private def decodeParameterStatus(payload: Buffer): Unit = {
        val key   = PgBufferUtils.readCString(payload)
        val value = PgBufferUtils.readCString(payload)
        if (key == "client_encoding") encoding = value
        if (key == "server_version") metadata = PostgresDatabaseMetadata.parse(value)
    }

    private def decodeBackendKeyData(payload: Buffer): Unit = {
        processId = payload.readInt
        secretKey = payload.readInt
    }

    private def decodeErrorResponse(payload: Buffer, length: Int): Unit = {
        decodeResponse(payload, length)
        val exception = response.toExecption()
        ctx.fireChannelExceptionCaught(exception, currentOutboundMessageId)
    }

    private def decodeResponse(payload: Buffer, length: Int): Unit = {
        var tpe: Byte = payload.readByte
        while (tpe != 0) {
            tpe match
                case Constants.ERR_OR_NOTICE_SEVERITY => response.setSeverity(PgBufferUtils.readCString(payload))
                case Constants.ERR_OR_NOTICE_CODE     => response.setCode(PgBufferUtils.readCString(payload))
                case Constants.ERR_OR_NOTICE_MESSAGE  => response.setMessage(PgBufferUtils.readCString(payload))
                case Constants.ERR_OR_NOTICE_DETAIL   => response.setDetail(PgBufferUtils.readCString(payload))
                case Constants.ERR_OR_NOTICE_HINT     => response.setHint(PgBufferUtils.readCString(payload))
                case Constants.ERR_OR_NOTICE_INTERNAL_POSITION =>
                    response.setInternalPosition(PgBufferUtils.readCString(payload))
                case Constants.ERR_OR_NOTICE_INTERNAL_QUERY =>
                    response.setInternalQuery(PgBufferUtils.readCString(payload))
                case Constants.ERR_OR_NOTICE_POSITION   => response.setPosition(PgBufferUtils.readCString(payload))
                case Constants.ERR_OR_NOTICE_WHERE      => response.setWhere(PgBufferUtils.readCString(payload))
                case Constants.ERR_OR_NOTICE_FILE       => response.setFile(PgBufferUtils.readCString(payload))
                case Constants.ERR_OR_NOTICE_LINE       => response.setLine(PgBufferUtils.readCString(payload))
                case Constants.ERR_OR_NOTICE_ROUTINE    => response.setRoutine(PgBufferUtils.readCString(payload))
                case Constants.ERR_OR_NOTICE_SCHEMA     => response.setSchema(PgBufferUtils.readCString(payload))
                case Constants.ERR_OR_NOTICE_TABLE      => response.setTable(PgBufferUtils.readCString(payload))
                case Constants.ERR_OR_NOTICE_COLUMN     => response.setColumn(PgBufferUtils.readCString(payload))
                case Constants.ERR_OR_NOTICE_DATA_TYPE  => response.setDataType(PgBufferUtils.readCString(payload))
                case Constants.ERR_OR_NOTICE_CONSTRAINT => response.setConstraint(PgBufferUtils.readCString(payload))
                case _                                  => payload.skipReadableBytes(payload.bytesBefore(0.toByte) + 1)

            tpe = payload.readByte
        }
    }

    override def handlerAdded(ctx: ChannelHandlerContext): Unit = {
        super.handlerAdded(ctx)
        this.ctx = ctx
        logger = LoggerFactory.getLogger(getClass, ctx.system)

        rowParser.setRowDesc(rowDesc)
        rowParser.setRowOffsets(rowOffsets)
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

    private val I: Byte = 'I'
    private val T: Byte = 'T'

    private val CMD_COMPLETED_INSERT: Array[Byte] = "INSERT 0 ".getBytes(StandardCharsets.US_ASCII)
    private val CMD_COMPLETED_DELETE: Array[Byte] = "DELETE ".getBytes(StandardCharsets.US_ASCII)
    private val CMD_COMPLETED_UPDATE: Array[Byte] = "UPDATE ".getBytes(StandardCharsets.US_ASCII)
    private val CMD_COMPLETED_SELECT: Array[Byte] = "SELECT ".getBytes(StandardCharsets.US_ASCII)
    private val CMD_COMPLETED_MOVE: Array[Byte]   = "MOVE ".getBytes(StandardCharsets.US_ASCII)
    private val CMD_COMPLETED_FETCH: Array[Byte]  = "FETCH ".getBytes(StandardCharsets.US_ASCII)
    private val CMD_COMPLETED_COPY: Array[Byte]   = "COPY ".getBytes(StandardCharsets.US_ASCII)

}
