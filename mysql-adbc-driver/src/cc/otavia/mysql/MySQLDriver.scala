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

package cc.otavia.mysql

import cc.otavia.adbc.Statement.ExecuteUpdate
import cc.otavia.adbc.{ConnectOptions, Connection, Driver}
import cc.otavia.buffer.Buffer
import cc.otavia.buffer.pool.AdaptiveBuffer
import cc.otavia.core.channel.{ChannelHandlerContext, ChannelInflight}
import cc.otavia.core.slf4a.{Logger, LoggerFactory}
import cc.otavia.core.stack.ChannelFuture
import cc.otavia.mysql.protocol.CapabilitiesFlag.*
import cc.otavia.mysql.protocol.Packets.*
import cc.otavia.mysql.protocol.{CapabilitiesFlag, CommandType}
import cc.otavia.mysql.utils.*

import java.net.SocketAddress
import java.nio.channels.ClosedChannelException
import java.nio.charset.{Charset, StandardCharsets}
import java.util
import scala.collection.mutable
import scala.language.unsafeNulls

class MySQLDriver(override val options: MySQLConnectOptions) extends Driver(options) {

    import MySQLDriver.*

    private var logger: Logger = _

    private val collation: Collation =
        if (options.collation != null) Collation.valueOf(options.collation)
        else {
            if (options.charset == null) Collation.utf8mb4_general_ci
            else Collation.valueOf(Collation.getDefaultCollationFromCharsetName(options.charset))
        }

    private val encodingCharset: Charset =
        if (options.collation != null) Charset.forName(collation.mappedJavaCharsetName)
        else if (options.characterEncoding == null) Charset.defaultCharset()
        else Charset.forName(options.characterEncoding)

    private var clientCapabilitiesFlag: Int = {
        var flags = CLIENT_SUPPORTED_CAPABILITIES_FLAGS
        if (options.database != null && options.database.nonEmpty) flags |= CLIENT_CONNECT_WITH_DB
        if (options.properties.nonEmpty) flags |= CLIENT_CONNECT_ATTRS
        if (options.useAffectedRows) flags |= CLIENT_FOUND_ROWS
        flags
    }

    private var ctx: ChannelHandlerContext = _
    private var status                     = ST_CONNECTING

    private var authenticationFailed: Throwable = _
    private var authMsgId                       = ChannelInflight.INVALID_CHANNEL_MESSAGE_ID

    private var currentMessageId = ChannelInflight.INVALID_CHANNEL_MESSAGE_ID

    private var sequenceId: Byte                  = 0
    private var metadata: MySQLDatabaseMetadata   = _
    private var connectionId: Long                = Long.MinValue
    private var authPluginData: Array[Byte]       = _
    private var isWaitingForRsaPublicKey: Boolean = false
    private var isSsl: Boolean                    = false

    final override protected def checkDecodePacket(buffer: Buffer): Boolean =
        if (buffer.readableBytes > 4) {
            val start     = buffer.readerOffset
            val packetLen = buffer.getUnsignedMediumLE(start) + 4
            if (buffer.readableBytes >= packetLen) true else false
        } else false

    override protected def decode(ctx: ChannelHandlerContext, input: AdaptiveBuffer): Unit =
        while (checkDecodePacket(input)) {
            val packetStart     = input.readerOffset
            val length          = input.readUnsignedMediumLE
            val sequenceId: Int = input.readUnsignedByte
            status match
                case ST_CONNECTING =>
                    handleInitialHandshake(input)
                    status = ST_AUTHENTICATING
                case ST_AUTHENTICATING => handleAuthentication(input)

            // skip remaining data
            if (input.readerOffset - packetStart < length + 4) input.readerOffset(packetStart + length + 4)
        }

    override protected def encode(ctx: ChannelHandlerContext, output: AdaptiveBuffer, msg: AnyRef, mid: Long): Unit = {
        msg match
            case _: Connection.Auth =>
                if (status == ST_AUTHENTICATED) ctx.fireChannelRead(None, mid)
                else if (status == ST_AUTHENTICATE_FAILED) ctx.fireChannelRead(authenticationFailed, mid)
                else if (status == ST_CLOSING) ctx.fireChannelRead(ClosedChannelException(), mid)
                else authMsgId = mid
            case executeUpdate: ExecuteUpdate =>
                if (status == ST_AUTHENTICATED) {
                    sendQueryCommand(executeUpdate.sql)
                } else ???
            case _ => ???
    }

    private def handleInitialHandshake(payload: Buffer): Unit = {
        logger.debug("handle initial handshake")
        val protocolVersion: Int = payload.readUnsignedByte
        val serverVersion        = BufferUtils.readNullTerminatedString(payload, StandardCharsets.US_ASCII)
        metadata = MySQLDatabaseMetadata.parse(serverVersion)
        if (
          metadata.majorVersion == 5 &&
          (metadata.minorVersion < 7 || (metadata.minorVersion == 7 && metadata.microVersion < 5))
        ) {
            // EOF_HEADER has to be enabled for older MySQL version which does not support the CLIENT_DEPRECATE_EOF flag
        } else clientCapabilitiesFlag |= CLIENT_DEPRECATE_EOF

        connectionId = payload.readUnsignedIntLE

        // read first of scramble
        authPluginData = new Array[Byte](NONCE_LENGTH)
        payload.readBytes(authPluginData, 0, AUTH_PLUGIN_DATA_PART1_LENGTH)

        // filter
        payload.readByte

        // read lower 2 bytes of Capabilities flags
        val lowerServerCapabilitiesFlags = payload.readUnsignedShortLE
        val charset                      = payload.readUnsignedByte.toShort
        val statusFlags                  = payload.readUnsignedShortLE

        // read upper 2 bytes of Capabilities flags
        val upperFlags  = payload.readUnsignedShortLE
        val serverFlags = lowerServerCapabilitiesFlags | (upperFlags << 16)

        // length of the combined auth_plugin_data (scramble)
        val isClientPluginAuthSupported = (serverFlags & CapabilitiesFlag.CLIENT_PLUGIN_AUTH) != 0
        val authPluginDataLength =
            if (isClientPluginAuthSupported) payload.readUnsignedByte
            else {
                payload.readByte
                0
            }

        // 10 bytes reserved
        payload.skipReadableBytes(10)

        // reset of the plugin provided data
        payload.readBytes(
          authPluginData,
          AUTH_PLUGIN_DATA_PART1_LENGTH,
          Math.max(NONCE_LENGTH - AUTH_PLUGIN_DATA_PART1_LENGTH, authPluginDataLength - 9)
        )
        payload.readByte // reserved byte

        // assume the server supports auth plugin
        val serverPluginName = BufferUtils.readNullTerminatedString(payload, StandardCharsets.UTF_8)

        val upgradeSsl = options.sslMode match
            case SslMode.DISABLED        => false
            case SslMode.PREFERRED       => isTlsSupportedByServer(serverFlags)
            case SslMode.REQUIRED        => true
            case SslMode.VERIFY_CA       => true
            case SslMode.VERIFY_IDENTITY => true

        if (upgradeSsl) {
            logger.warn("SSL connect is not support")
            sendHandshakeResponseMessage(serverPluginName, options.authenticationPlugin, authPluginData, serverFlags)
        } else sendHandshakeResponseMessage(serverPluginName, options.authenticationPlugin, authPluginData, serverFlags)
    }

    private def sendHandshakeResponseMessage(
        serverPluginName: String,
        plugin: AuthenticationPlugin,
        nonce: Array[Byte],
        serverFlags: Int
    ): Unit = {
        logger.debug("sending handshake response message")
        clientCapabilitiesFlag &= serverFlags
        val clientPluginName = if (plugin == AuthenticationPlugin.DEFAULT) serverPluginName else plugin.value

        val packet         = ctx.outboundAdaptiveBuffer
        val packetStartIdx = packet.writerOffset
        packet.writeMediumLE(0) // set after
        packet.writeByte(sequenceId.toByte)
        packet.writeIntLE(clientCapabilitiesFlag)
        packet.writeIntLE(PACKET_PAYLOAD_LENGTH_LIMIT)
        packet.writeByte(collation.collationId.toByte)
        packet.writeBytes(23, 0x00.toByte)
        BufferUtils.writeNullTerminatedString(packet, options.user, StandardCharsets.UTF_8)

        var authMethod = clientPluginName
        if (options.password == null || options.password.isEmpty) packet.writeByte(0)
        else {
            val authResponse = authMethod match
                case "mysql_native_password" =>
                    Native41Authenticator.encode(options.password.getBytes(StandardCharsets.UTF_8), nonce)
                case "caching_sha2_password" =>
                    CachingSha2Authenticator.encode(options.password.getBytes(StandardCharsets.UTF_8), nonce)
                case "mysql_clear_password" =>
                    val bytes = options.password.getBytes(StandardCharsets.UTF_8)
                    val resp  = new Array[Byte](bytes.length + 1)
                    System.arraycopy(bytes, 0, resp, 0, bytes.length)
                    resp(bytes.length) = 0
                    resp
                case _ =>
                    logger.warn(
                      s"Unknown authentication method: $authMethod,  the client will try to use mysql_native_password instead."
                    )
                    authMethod = "mysql_native_password"
                    Native41Authenticator.encode(options.password.getBytes(StandardCharsets.UTF_8), nonce)

            if ((clientCapabilitiesFlag & CLIENT_PLUGIN_AUTH_LENENC_CLIENT_DATA) != 0) {
                BufferUtils.writeLengthEncodedInteger(packet, authResponse.length)
                packet.writeBytes(authResponse)
            } else if ((clientCapabilitiesFlag & CLIENT_SECURE_CONNECTION) != 0) {
                packet.writeByte(authResponse.length.toByte)
                packet.writeBytes(authResponse)
            } else packet.writeByte(0)
        }

        if ((clientCapabilitiesFlag & CLIENT_CONNECT_WITH_DB) != 0)
            BufferUtils.writeNullTerminatedString(packet, options.database, StandardCharsets.UTF_8)

        if ((clientCapabilitiesFlag & CLIENT_PLUGIN_AUTH) != 0)
            BufferUtils.writeNullTerminatedString(packet, authMethod, StandardCharsets.UTF_8)

        if ((clientCapabilitiesFlag & CLIENT_CONNECT_ATTRS) != 0) encodeConnectionAttributes(options.properties, packet)

        // set payload length
        val payloadLength = packet.writerOffset - packetStartIdx - 4
        packet.setMediumLE(packetStartIdx, payloadLength)

        // send packet
        ctx.writeAndFlush(packet)
    }

    private def handleAuthentication(payload: Buffer): Unit = {
        logger.debug("handle authentication")
        val header = payload.getUnsignedByte(payload.readerOffset)
        header match {
            case OK_PACKET_HEADER    => successAuthAndResponse()
            case ERROR_PACKET_HEADER => handleErrorPacketPayload(payload)
            case AUTH_SWITCH_REQUEST_STATUS_FLAG =>
                handleAuthSwitchRequest(options.password.getBytes(StandardCharsets.UTF_8), payload)
            case AUTH_MORE_DATA_STATUS_FLAG =>
                handleAuthMoreData(options.password.getBytes(StandardCharsets.UTF_8), payload)
            case _ => failAuthAndResponse(new IllegalStateException(s"Unhandled state with header: $header"))
        }
    }

    private def handleErrorPacketPayload(payload: Buffer): Unit = {
        payload.readByte // skip ERR packet header
        val errorCode = payload.readUnsignedShortLE
        // CLIENT_PROTOCOL_41 capability flag will always be set
        payload.readByte // // SQL state marker will always be #
        val sqlState = BufferUtils.readFixedLengthString(payload, 5, StandardCharsets.UTF_8)
        val errorMsg = BufferUtils.readFixedLengthString(payload, payload.readableBytes, StandardCharsets.UTF_8)

        failAuthAndResponse(new MySQLException(errorMsg, errorCode, sqlState))
    }

    private def handleAuthSwitchRequest(password: Array[Byte], payload: Buffer): Unit = {
        // Protocol::AuthSwitchRequest
        payload.readByte
        val pluginName = BufferUtils.readNullTerminatedString(payload, StandardCharsets.UTF_8)
        val nonce      = new Array[Byte](NONCE_LENGTH)
        payload.readBytes(nonce)
        pluginName match
            case "mysql_native_password" => sendBytesAsPacket(ctx, Native41Authenticator.encode(password, nonce))
            case "caching_sha2_password" => sendBytesAsPacket(ctx, CachingSha2Authenticator.encode(password, nonce))
            case "mysql_clear_password"  => sendBytesAsPacket(ctx, util.Arrays.copyOf(password, password.length + 1))
            case _ =>
                val msg = s"Unsupported authentication method: $pluginName"
                failAuthAndResponse(new UnsupportedOperationException(msg))
    }

    private def handleAuthMoreData(password: Array[Byte], payload: Buffer): Unit = {
        logger.debug("handle more auth data")
        payload.readByte

        if (isWaitingForRsaPublicKey) {
            val serverRsaPublicKey =
                BufferUtils.readFixedLengthString(payload, payload.readableBytes, StandardCharsets.UTF_8)
            sendEncryptedPasswordWithServerRsaPublicKey(password, serverRsaPublicKey)
        } else {
            val flag = payload.readByte
            flag match
                case FULL_AUTHENTICATION_STATUS_FLAG =>
                    if (isSsl) {
                        // TODO: support SSL
                    } else {
                        if (options.serverRsaPublicKeyValue == null) {
                            isWaitingForRsaPublicKey = true
                            val packet = ctx.outboundAdaptiveBuffer
                            packet.writeMediumLE(1)
                            packet.writeByte(sequenceId.toByte)
                            packet.writeByte(AUTH_PUBLIC_KEY_REQUEST_FLAG)
                            ctx.writeAndFlush(packet)
                        } else {
                            val content = options.serverRsaPublicKeyValue
                                .getCharSequence(
                                  options.serverRsaPublicKeyValue.readerOffset,
                                  options.serverRsaPublicKeyValue.readableBytes,
                                  StandardCharsets.UTF_8
                                )
                                .toString
                            sendEncryptedPasswordWithServerRsaPublicKey(password, content)
                        }
                    }
                case FAST_AUTH_STATUS_FLAG => // fast auth success
                    logger.debug("fast auth success")
                    successAuthAndResponse()
                case _ =>
                    val msg = s"Unsupported flag for AuthMoreData : $flag"
                    failAuthAndResponse(new UnsupportedOperationException(msg))
        }
    }

    private def sendEncryptedPasswordWithServerRsaPublicKey(
        password: Array[Byte],
        serverRsaPublicKeyContent: String
    ): Unit = {
        logger.debug("send encrypted password with server rsa public key")
        try {
            val encryptedPassword = RsaPublicKeyEncryptor.encrypt(
              util.Arrays.copyOf(password, password.length + 1),
              authPluginData,
              serverRsaPublicKeyContent
            )
            sendBytesAsPacket(ctx, encryptedPassword)
        } catch {
            case e: Exception => failAuthAndResponse(e)
        }
    }

    private def encodeConnectionAttributes(attributes: mutable.Map[String, String], packet: Buffer): Unit = {
        val buffer = AdaptiveBuffer(ctx.outboundAdaptiveBuffer.allocator)
        for ((key, value) <- attributes) {
            BufferUtils.writeLengthEncodedString(buffer, key, StandardCharsets.UTF_8)
            BufferUtils.writeLengthEncodedString(buffer, value, StandardCharsets.UTF_8)
        }
        BufferUtils.writeLengthEncodedInteger(packet, buffer.readableBytes)
        packet.writeBytes(buffer)
        buffer.close()
    }

    private def isTlsSupportedByServer(serverCapabilitiesFlags: Int): Boolean =
        (serverCapabilitiesFlags & CLIENT_SSL) != 0

    private def sendQueryCommand(sql: String): Unit = {
        val packet      = ctx.outboundAdaptiveBuffer
        val packetStart = packet.writerOffset
        packet.writeMediumLE(0) // will set payload length by calculation
        packet.writeByte(sequenceId)

        // encode packet payload
        packet.writeByte(CommandType.COM_QUERY)
        packet.writeCharSequence(sql, encodingCharset)

        // set payload length
        val payloadLength = packet.writerOffset - packetStart - 4
        packet.setMediumLE(packetStart, payloadLength)

        ctx.writeAndFlush(packet) // TODO: check maxpacket limit
    }

    private def sendBytesAsPacket(ctx: ChannelHandlerContext, payload: Array[Byte]): Unit = {
        val length = payload.length
        val packet = ctx.outboundAdaptiveBuffer
        packet.writeMediumLE(length)
        packet.writeByte(sequenceId.toByte)
        packet.writeBytes(payload)
        ctx.writeAndFlush(packet)
    }

    override def handlerAdded(ctx: ChannelHandlerContext): Unit = {
        super.handlerAdded(ctx)
        this.ctx = ctx
        logger = LoggerFactory.getLogger(getClass, ctx.system)
    }

    private def failAuthAndResponse(cause: Throwable): Unit = {
        status = ST_AUTHENTICATE_FAILED
        if (authMsgId != ChannelInflight.INVALID_CHANNEL_MESSAGE_ID) ctx.fireChannelRead(cause, authMsgId)
        else authenticationFailed = cause
    }

    private def successAuthAndResponse(): Unit = {
        status = ST_AUTHENTICATED
        if (authMsgId != ChannelInflight.INVALID_CHANNEL_MESSAGE_ID) ctx.fireChannelRead(None, authMsgId)
    }

}

object MySQLDriver {

    private val AUTH_PLUGIN_DATA_PART1_LENGTH = 8

    private val ST_CONNECTING          = 0
    private val ST_AUTHENTICATING      = 1
    private val ST_CONNECTED           = 2
    private val ST_AUTHENTICATED       = 4
    private val ST_AUTHENTICATE_FAILED = 5
    private val ST_CLOSING             = 6

    val ST_CLIENT_CREATE        = 0
    val ST_CLIENT_CONNECTED     = 1
    val ST_CLIENT_AUTHENTICATED = 2
    val ST_CLIENT_CLOSED        = 3

    // auth
    val NONCE_LENGTH                    = 20
    val AUTH_SWITCH_REQUEST_STATUS_FLAG = 0xfe

    val AUTH_MORE_DATA_STATUS_FLAG                   = 0x01
    protected val AUTH_PUBLIC_KEY_REQUEST_FLAG: Byte = 0x02
    protected val FAST_AUTH_STATUS_FLAG              = 0x03
    protected val FULL_AUTHENTICATION_STATUS_FLAG    = 0x04

}
