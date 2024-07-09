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

import cc.otavia.buffer.pool.AdaptiveBuffer
import cc.otavia.buffer.{Buffer, BufferUtils}
import cc.otavia.core.channel.{Channel, ChannelHandlerContext, ChannelInflight, ChannelOption}
import cc.otavia.core.slf4a.{Logger, LoggerFactory}
import cc.otavia.postgres.impl.*
import cc.otavia.postgres.protocol.{Constants, DataFormat, DataType}
import cc.otavia.postgres.utils.ScramAuthentication.ScramClientInitialMessage
import cc.otavia.postgres.utils.{MD5Authentication, PgBufferUtils, ScramAuthentication}
import cc.otavia.sql.*
import cc.otavia.sql.Statement.*
import cc.otavia.sql.impl.HexSequence

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

    private val rowDesc: RowDesc          = new RowDesc()
    private var continueParseRow: Boolean = false

    private val rowBuffer: mutable.ArrayBuffer[Row]        = mutable.ArrayBuffer.empty
    private val rowOffsets: mutable.ArrayBuffer[RowOffset] = mutable.ArrayBuffer.empty

    private val rowParser: PostgresRowParser = new PostgresRowParser()

    private val prepareStatements: mutable.HashMap[String, PreparedStatement] = mutable.HashMap.empty
    private val psSeq             = new HexSequence() // used for generating named prepared statement name
    private var compiled: Boolean = false             // whether the current prepared query compiled

    private var modifyRows: Int = 0

    private var error: Throwable = _

    override def setChannelOptions(channel: Channel): Unit = {
        channel.setOption(ChannelOption.CHANNEL_MAX_FUTURE_INFLIGHT, options.pipeliningLimit)
        channel.setOption(ChannelOption.CHANNEL_FUTURE_BARRIER, this.futureBarrier)
        // TODO:
    }

    private def futureBarrier(msg: AnyRef): Boolean = msg match
        case prepareQuery: PrepareQuery[?] => !prepareStatements.contains(prepareQuery.sql)
        case _                             => false

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
            case simpleQuery: SimpleQuery[?]   => encodeSimpleQuery(simpleQuery, output)
            case prepareQuery: PrepareQuery[?] => encodePrepareQuery(prepareQuery, output)
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
                case Constants.MSG_TYPE_PARSE_COMPLETE        =>
                case Constants.MSG_TYPE_CLOSE_COMPLETE        => ???
                case Constants.MSG_TYPE_NO_DATA               =>
                case Constants.MSG_TYPE_PORTAL_SUSPENDED      => ???
                case Constants.MSG_TYPE_PARAMETER_DESCRIPTION => decodeParameterDescription(input, length)
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

    private def encodeSimpleQuery(query: SimpleQuery[?], output: Buffer): Unit = {
        val sql    = query.sql
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
            error = new TransactionFailed
        }

        val inflight = ctx.inflightFutures
        val promise  = inflight.first
        val query    = promise.getAsk()
        if (error != null) {
            val cause = error
            error = null
            ctx.fireChannelExceptionCaught(cause, promise.messageId)
        } else {
            query match
                case query: PrepareQuery[?] if !compiled =>
                    encodePrepareQuery(query, ctx.outboundAdaptiveBuffer) // stage 2: execute prepared statement
                    compiled = true
                    ctx.inflightFutures.setBarrierMode(false)
                case _ =>
                    query match
                        case authentication: Authentication =>
                            if (status == ST_AUTHENTICATED) ctx.fireChannelRead(None, promise.messageId)
                            else ctx.fireChannelExceptionCaught(new UnknownError(), promise.messageId)
                        case update: (ExecuteUpdate | PrepareUpdate) =>
                            ctx.fireChannelRead(ModifyRows(modifyRows), promise.messageId)
                            modifyRows = 0
                        case query: (ExecuteQuery[?] | PrepareFetchOneQuery[?]) =>
                            if (rowBuffer.nonEmpty) ctx.fireChannelRead(rowBuffer.head, promise.messageId)
                            else ctx.fireChannelExceptionCaught(new Error("No data fetch"), promise.messageId)
                            rowBuffer.clear()
                        case queries: (ExecuteQueries[?] | PrepareFetchAllQuery[?]) =>
                            if (rowBuffer.nonEmpty) {
                                val rowSet = RowSet(rowBuffer.toSeq)
                                ctx.fireChannelRead(rowSet, promise.messageId)
                            } else ctx.fireChannelExceptionCaught(new Error("No data fetch"), promise.messageId)
                            rowBuffer.clear()
        }

    }

    private def recycleRowOffset(): Unit = {
        for (off <- rowOffsets) off.recycle()
        rowOffsets.clear()
    }

    private def decodeDataRow(payload: Buffer, payloadLength: Int): Unit =
        if (continueParseRow) {
            recycleRowOffset()
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

            val future = ctx.inflightFutures.first
            val cmd    = future.getAsk()

            cmd match
                case executeQuery: ExecuteQuery[?] =>
                    val row = executeQuery.decoder.decode(rowParser)
                    continueParseRow = false
                    rowBuffer.addOne(row)
                case executeQueries: ExecuteQueries[?] =>
                    val decoder = executeQueries.decoder
                    val row     = decoder.decode(rowParser)
                    rowBuffer.addOne(row)
                case prepareQuery: PrepareFetchOneQuery[?] =>
                    decodeDataRow0(prepareQuery.decoder, prepareStatements(prepareQuery.sql))
                    continueParseRow = false
                case prepareQuery: PrepareFetchAllQuery[?] =>
                    decodeDataRow0(prepareQuery.decoder, prepareStatements(prepareQuery.sql))
        }

    private def decodeDataRow0(decoder: RowDecoder[?], ps: PreparedStatement): Unit = {
        rowParser.setRowDesc(ps.rowDesc)
        val row = decoder.decode(rowParser)
        rowBuffer.addOne(row)
    }

    private def decodeRowDescription(payload: Buffer): Unit = {
        ctx.inflightFutures.first.getAsk() match
            case value: PrepareQuery[?] =>
                val ps           = prepareStatements(value.sql)
                val columnLength = payload.readUnsignedShort
                ps.rowDesc.setLength(columnLength)
                var i = 0
                while (i < columnLength) {
                    val columnDesc = ps.rowDesc(i)
                    decodeColumnDescription(payload, columnDesc)
                    if (columnDesc.dataFormat == DataFormat.TEXT && columnDesc.dataType.supportsBinary)
                        columnDesc.dataFormat = DataFormat.BINARY
                    i += 1
                }
            case _ =>
                val columnLength = payload.readUnsignedShort
                this.rowDesc.setLength(columnLength)
                var i = 0
                while (i < columnLength) {
                    val columnDesc = this.rowDesc(i)
                    decodeColumnDescription(payload, columnDesc)
                    i += 1
                }
                rowParser.setRowDesc(rowDesc)
                continueParseRow = true
    }

    private def decodeColumnDescription(payload: Buffer, columnDesc: ColumnDesc): Unit = {
        columnDesc.name = PgBufferUtils.readCString(payload)
        columnDesc.relationId = payload.readInt
        columnDesc.relationAttributeNo = payload.readShort
        columnDesc.dataType = DataType.fromOid(payload.readInt)
        columnDesc.length = payload.readShort
        columnDesc.typeModifier = payload.readInt
        val df = payload.readUnsignedShort
        columnDesc.dataFormat = DataFormat.fromOrdinal(df)
    }

    private def decodeCommandComplete(payload: Buffer, length: Int): Unit = {
        if (payload.skipIfNextAre(CMD_COMPLETED_UPDATE)) {
            val rows = BufferUtils.readStringAsInt(payload)
            modifyRows += rows
        } else if (payload.skipIfNextAre(CMD_COMPLETED_DELETE)) {
            val rows = BufferUtils.readStringAsInt(payload)
            modifyRows += rows
        } else if (payload.skipIfNextAre(CMD_COMPLETED_INSERT)) {
            val rows = BufferUtils.readStringAsInt(payload)
            modifyRows += rows
        } else if (payload.skipIfNextAre(CMD_COMPLETED_SELECT)) {
            //
        } else if (payload.skipIfNextAre(CMD_COMPLETED_FETCH)) {
            //
        } else if (payload.skipIfNextAre(CMD_COMPLETED_COPY)) {
            //
        } else if (payload.skipIfNextAre(CMD_COMPLETED_MOVE)) {
            //
        }
    }

    private def decodeBindComplete(): Unit = continueParseRow = true

    private def decodeAuthentication(payload: Buffer, length: Int): Unit = {
        val typ = payload.readInt
        typ match
            case Constants.AUTH_TYPE_OK => successAuth()
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
                    case e: UnsupportedOperationException => error = e
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

    private def successAuth(): Unit = {
        status = ST_AUTHENTICATED
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
        error = exception
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

    private def encodePrepareQuery(query: PrepareQuery[?], output: Buffer): Unit = {
        prepareStatements.get(query.sql) match
            case Some(ps) if ps.parsed =>
                val bind = query.bind
                bind match
                    case products: Seq[?] =>
                        for (product <- products.asInstanceOf[Seq[Product]]) {
                            encodeBind(ps, product, output)
                            encodeExecute(0, output)
                        }
                    case product: Product =>
                        encodeBind(ps, product, output)
                        encodeExecute(0, output)
                encodeSync(output)
                ctx.writeAndFlush(output)
                output.compact()
            case None =>
                ctx.inflightFutures.setBarrierMode(true)
                compiled = false
                sendPrepareStatement(query.sql, output)
            case _ =>
    }

    private def sendPrepareStatement(sql: String, output: Buffer): Unit = {
        val statement = nextStatementName()
        val ps        = new PreparedStatement()
        ps.sql = sql
        ps.statement = statement
        prepareStatements.put(ps.sql, ps)
        encodeParse(sql, statement, output)
        encodeDescribe(statement, output)
        encodeSync(output)
        ctx.writeAndFlush(output)
    }

    private def nextStatementName(): Array[Byte] = psSeq.next()

    private def encodeParse(sql: String, statement: Array[Byte], output: Buffer): Unit = {
        output.writeByte(PARSE)
        val pos = output.writerOffset
        output.writeInt(0)
        output.writeBytes(statement)
        PgBufferUtils.writeCString(output, sql)
        // Let pg figure out the parameter types
        output.writeShort(0)
        output.setInt(pos, output.writerOffset - pos) // set packet payload length
    }

    private def encodeDescribe(statement: Array[Byte], output: Buffer): Unit = {
        output.writeByte(DESCRIBE)
        val pos = output.writerOffset
        output.writeInt(0)
        if (statement.length > 1) {
            output.writeByte('S')
            output.writeBytes(statement)
        } else {
            output.writeByte('S')
            output.writeByte(0)
        }
        output.setInt(pos, output.writerOffset - pos)
    }

    private def encodeBind(ps: PreparedStatement, params: Product, output: Buffer): Unit = {
        output.writeByte(BIND)
        val pos = output.writerOffset
        output.writeInt(0)
        output.writeByte(0)
        output.writeBytes(ps.statement)
        output.writeShort(params.productArity.toShort)
        for (paramDesc <- ps.paramDesc) output.writeShort(if (paramDesc.supportsBinary) 1 else 0)
        output.writeShort(ps.paramDesc.length.toShort)
        var i = 0
        while (i < params.productArity) {
            val param = params.productElement(i)
            if (param == null || param == None) { // NULL value
                output.writeInt(-1)
            } else {
                val datatype = ps.paramDesc(i)
                if (datatype.supportsBinary) {
                    val pos = output.writerOffset
                    output.writeInt(0)
                    RowValueCodec.encodeBinary(datatype, param, output)
                    output.setInt(pos, output.writerOffset - pos - 4)
                } else ???
            }
            i += 1
        }

        // MAKE resultColumsn non null to avoid null check
        // Result columns are all in Binary format
        if (ps.rowDesc.length > 0) {
            output.writeShort(ps.rowDesc.length.toShort)
            var i = 0
            while (i < ps.rowDesc.length) {
                val colDesc = ps.rowDesc(i)
                output.writeShort(if (colDesc.dataType.supportsBinary) 1 else 0)
                i += 1
            }
            // for (rowDesc <- ps.rowDesc.columns) output.writeShort(if (rowDesc.dataType.supportsBinary) 1 else 0)
        } else {
            output.writeShort(1)
            output.writeShort(1)
        }
        output.setInt(pos, output.writerOffset - pos)
    }

    private def encodeExecute(fetch: Int, output: Buffer): Unit = {
        output.writeByte(EXECUTE)
        val pos = output.writerOffset
        output.writeInt(0)
        output.writeByte(0)
        output.writeInt(fetch)                        // Zero denotes "no limit" maybe for ReadStream<Row>
        output.setInt(pos, output.writerOffset - pos) // set packet length
    }

    private def encodeSync(output: Buffer): Unit = {
        output.writeByte(SYNC)
        output.writeInt(4)
    }

    private def decodeParameterDescription(payload: Buffer, length: Int): Unit = {
        val paramDataTypes = new Array[DataType](payload.readUnsignedShort)
        for (idx <- paramDataTypes.indices) {
            paramDataTypes(idx) = DataType.fromOid(payload.readInt)
        }
        val prepare = ctx.inflightFutures.first.getAsk().asInstanceOf[PrepareQuery[?]]
        val ps      = prepareStatements(prepare.sql)
        ps.paramDesc = paramDataTypes
        ps.parsed = true
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
