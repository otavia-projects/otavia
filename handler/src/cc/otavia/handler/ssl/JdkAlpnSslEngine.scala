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

package cc.otavia.handler.ssl

import cc.otavia.handler.ssl.JdkAlpnSslEngine.AlpnSelector
import cc.otavia.handler.ssl.protocol.*

import java.nio.ByteBuffer
import java.util
import java.util.function.BiFunction
import javax.net.ssl.{SSLEngine, SSLEngineResult, SSLHandshakeException}
import scala.jdk.CollectionConverters.*
import scala.language.unsafeNulls

class JdkAlpnSslEngine private (engine: SSLEngine) extends JdkSslEngine(engine) {

    private var alpnSelector: AlpnSelector                   = _
    private var selectionListener: ProtocolSelectionListener = _

    def this(engine: SSLEngine, apn: JdkApplicationProtocolNegotiator, isServer: Boolean) = {
        this(engine)
        if (isServer) {
            alpnSelector = new AlpnSelector(apn.protocolSelectorFactory.newSelector(this, apn.protocols.toSet), this)
            engine.setHandshakeApplicationProtocolSelector(alpnSelector)
        } else {
            selectionListener = apn.protocolListenerFactory.newListener(this, apn.protocols)
            val params             = engine.getSSLParameters
            val supportedProtocols = apn.protocols.toArray
            params.setApplicationProtocols(supportedProtocols)
        }
    }

    private def verifyProtocolSelection(result: SSLEngineResult): SSLEngineResult = {
        if (result.getHandshakeStatus == SSLEngineResult.HandshakeStatus.FINISHED) {
            if (alpnSelector == null) { // This means we are using client-side and
                try {
                    val protocol = getApplicationProtocol
                    assert(protocol != null)
                    if (protocol.isEmpty) {
                        // If empty the server did not announce ALPN:
                        // See:
                        // https://hg.openjdk.java.net/jdk9/dev/jdk/file/65464a307408/src/java.base/
                        // share/classes/sun/security/ssl/ClientHandshaker.java#l741
                        selectionListener.unsupported()
                    } else selectionListener.select(protocol)
                } catch {
                    case e: Throwable =>
                        e match
                            case e: SSLHandshakeException => throw e
                            case _                        => throw new SSLHandshakeException(e.getMessage).initCause(e)
                }
            } else {
                assert(selectionListener == null)
                alpnSelector.checkUnsupported()
            }
        }
        result
    }

    override def wrap(src: ByteBuffer, dst: ByteBuffer): SSLEngineResult = verifyProtocolSelection(super.wrap(src, dst))

    override def wrap(srcs: Array[ByteBuffer], dst: ByteBuffer): SSLEngineResult =
        verifyProtocolSelection(super.wrap(srcs, dst))

    override def wrap(srcs: Array[ByteBuffer], offset: Int, length: Int, dst: ByteBuffer): SSLEngineResult =
        verifyProtocolSelection(super.wrap(srcs, offset, length, dst))

    override def unwrap(src: ByteBuffer, dst: ByteBuffer): SSLEngineResult =
        verifyProtocolSelection(super.unwrap(src, dst))

    override def unwrap(src: ByteBuffer, dsts: Array[ByteBuffer]): SSLEngineResult =
        verifyProtocolSelection(super.unwrap(src, dsts))

    override def unwrap(src: ByteBuffer, dsts: Array[ByteBuffer], offset: Int, length: Int): SSLEngineResult =
        verifyProtocolSelection(super.unwrap(src, dsts, offset, length))

    override def setNegotiatedApplicationProtocol(applicationProtocol: String): Unit = {
        // Do nothing as this is handled internally by the Java8u251+ implementation of SSLEngine.
    }

    override def getNegotiatedApplicationProtocol: String = {
        val protocol = getApplicationProtocol
        if (protocol != null) { if (protocol.isEmpty) null else protocol }
        else null
    }

    override def getApplicationProtocol: String = getWrappedEngine.getApplicationProtocol

    override def getHandshakeApplicationProtocol: String = getWrappedEngine.getHandshakeApplicationProtocol

    override def setHandshakeApplicationProtocolSelector(
        selector: BiFunction[SSLEngine, util.List[String], String]
    ): Unit = getWrappedEngine.setHandshakeApplicationProtocolSelector(selector)

    override def getHandshakeApplicationProtocolSelector: BiFunction[SSLEngine, util.List[String], String] =
        getWrappedEngine.getHandshakeApplicationProtocolSelector

}

object JdkAlpnSslEngine {

    class AlpnSelector(val selector: ProtocolSelector, engine: JdkAlpnSslEngine)
        extends BiFunction[SSLEngine, util.List[String], String] {

        private var called: Boolean = false

        override def apply(t: SSLEngine, strings: util.List[String]): String = {
            assert(!called)
            called = true
            try {
                val selected = selector.select(strings.asScala.toList)
                if (selected == null) "" else selected
            } catch {
                case cause: Exception =>
                    // Returning null means we want to fail the handshake.
                    //
                    // See https://download.java.net/java/jdk9/docs/api/javax/net/ssl/
                    // SSLEngine.html#setHandshakeApplicationProtocolSelector-java.util.function.BiFunction-
                    null
            }
        }

        def checkUnsupported(): Unit = if (called) {
            // ALPN message was received by peer and so apply(...) was called.
            // See:
            // https://hg.openjdk.java.net/jdk9/dev/jdk/file/65464a307408/src/
            // java.base/share/classes/sun/security/ssl/ServerHandshaker.java#l933
        } else {
            val protocol = engine.getApplicationProtocol
            assert(protocol != null)
            if (protocol.isEmpty) selector.unsupported() // ALPN is not supported
        }

    }

}
