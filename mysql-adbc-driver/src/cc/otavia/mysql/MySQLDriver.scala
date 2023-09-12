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

import cc.otavia.adbc.{ConnectOptions, Driver}
import cc.otavia.buffer.Buffer
import cc.otavia.buffer.pool.AdaptiveBuffer
import cc.otavia.core.channel.ChannelHandlerContext
import cc.otavia.core.stack.ChannelFuture
import cc.otavia.mysql.protocol.CapabilitiesFlag.*
import cc.otavia.mysql.protocol.Packets.*
import cc.otavia.mysql.utils.*

import java.net.SocketAddress
import java.nio.charset.{Charset, StandardCharsets}
import scala.language.unsafeNulls

class MySQLDriver(override val options: MySQLConnectOptions) extends Driver(options) {

    import MySQLDriver.*

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

    private var status = ST_CONNECTING

    private var sequenceId: Int = 0

    final override protected def checkDecodePacket(buffer: Buffer): Boolean =
        if (buffer.readableBytes > 4) {
            val start     = buffer.readerOffset
            val packetLen = buffer.getUnsignedMediumLE(start) + 4
            if (buffer.readableBytes >= packetLen) true else false
        } else false

    override protected def decode(ctx: ChannelHandlerContext, input: AdaptiveBuffer): Unit =
        if (checkDecodePacket(input)) {
            val packetStart     = input.readerOffset
            val length          = input.readUnsignedMediumLE
            val sequenceId: Int = input.readUnsignedByte
            status match
                case ST_CONNECTING =>
                    handleInitialHandshake(input)
                    status = ST_AUTHENTICATING
                case ST_AUTHENTICATING => handleAuthentication(ctx, input)
        }

    override protected def encode(ctx: ChannelHandlerContext, output: AdaptiveBuffer, msg: AnyRef, msgId: Long): Unit =
        ???

    private def handleInitialHandshake(payload: Buffer): Unit = {
        val protocolVersion: Int = payload.readUnsignedByte
        val serverVersion        = BufferUtils.readNullTerminatedString(payload, StandardCharsets.US_ASCII)

        ???
    }

    private def handleAuthentication(ctx: ChannelHandlerContext, payload: Buffer): Unit = {
        val header = payload.getUnsignedByte(payload.readerOffset)
        header match {
            case OK_PACKET_HEADER =>
                status = ST_CONNECTED
            case ERROR_PACKET_HEADER =>
            case AUTH_SWITCH_REQUEST_STATUS_FLAG =>
                handleAuthSwitchRequest(ctx, options.password.getBytes(StandardCharsets.UTF_8), payload)
            case AUTH_MORE_DATA_STATUS_FLAG => // handleAuthMoreData(cmd.password.getBytes(StandardCharsets.UTF_8), payload)
            case _ =>
        }
    }

    private def handleAuthSwitchRequest(ctx: ChannelHandlerContext, password: Array[Byte], payload: Buffer): Unit = {
        // Protocol::AuthSwitchRequest
        payload.readByte
        val pluginName = BufferUtils.readNullTerminatedString(payload, StandardCharsets.UTF_8)
        val nonce      = new Array[Byte](NONCE_LENGTH)
        payload.readBytes(nonce)
        val authRes = pluginName match {
            case "mysql_native_password" => Native41Authenticator.encode(password, nonce)
            case "caching_sha2_password" => CachingSha2Authenticator.encode(password, nonce)
            case "mysql_clear_password"  => password
            case _                       => ???
        }
        sendBytesAsPacket(ctx, authRes)
    }

    def isTlsSupportedByServer(serverCapabilitiesFlags: Int): Boolean = (serverCapabilitiesFlags & CLIENT_SSL) != 0

    private def sendBytesAsPacket(ctx: ChannelHandlerContext, payload: Array[Byte]): Unit = {
        val length = payload.length
        val packet = ctx.outboundAdaptiveBuffer
        packet.writeMediumLE(length)
        packet.writeByte(sequenceId.toByte)
        packet.writeBytes(payload)
        ctx.writeAndFlush(packet)
    }

}

object MySQLDriver {

    private val AUTH_PLUGIN_DATA_PART1_LENGTH = 8

    private val ST_CONNECTING     = 0
    private val ST_AUTHENTICATING = 1
    private val ST_CONNECTED      = 2
    private val ST_CLOSING        = 5

    val ST_CLIENT_CREATE        = 0
    val ST_CLIENT_CONNECTED     = 1
    val ST_CLIENT_AUTHENTICATED = 2
    val ST_CLIENT_CLOSED        = 3

    // auth
    val NONCE_LENGTH                    = 20
    val AUTH_SWITCH_REQUEST_STATUS_FLAG = 0xfe

    val AUTH_MORE_DATA_STATUS_FLAG                = 0x01
    protected val AUTH_PUBLIC_KEY_REQUEST_FLAG    = 0x02
    protected val FAST_AUTH_STATUS_FLAG           = 0x03
    protected val FULL_AUTHENTICATION_STATUS_FLAG = 0x04

}
