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

import java.nio.ByteBuffer
import javax.net.ssl.{SSLEngine, SSLEngineResult, SSLParameters, SSLSession}
import scala.language.unsafeNulls

class JdkSslEngine(val engine: SSLEngine) extends SSLEngine {

    @volatile private var applicationProtocol: String = _

    def getNegotiatedApplicationProtocol: String = applicationProtocol

    def setNegotiatedApplicationProtocol(applicationProtocol: String): Unit =
        this.applicationProtocol = applicationProtocol

    override def getSession: SSLSession = engine.getSession

    def getWrappedEngine: SSLEngine = engine

    override def closeInbound(): Unit = engine.closeInbound()

    override def closeOutbound(): Unit = engine.closeOutbound()

    override def getPeerHost: String = engine.getPeerHost

    override def getPeerPort: Int = engine.getPeerPort

    override def wrap(src: ByteBuffer, dst: ByteBuffer): SSLEngineResult = engine.wrap(src, dst)

    override def wrap(srcs: Array[ByteBuffer], dst: ByteBuffer): SSLEngineResult = engine.wrap(srcs, dst)

    override def wrap(srcs: Array[ByteBuffer], offset: Int, length: Int, dst: ByteBuffer): SSLEngineResult =
        engine.wrap(srcs, offset, length, dst)

    override def unwrap(src: ByteBuffer, dst: ByteBuffer): SSLEngineResult = engine.unwrap(src, dst)

    override def unwrap(src: ByteBuffer, dsts: Array[ByteBuffer]): SSLEngineResult = engine.unwrap(src, dsts)

    override def unwrap(src: ByteBuffer, dsts: Array[ByteBuffer], offset: Int, length: Int): SSLEngineResult =
        engine.unwrap(src, dsts, offset, length)

    override def getDelegatedTask: Runnable = engine.getDelegatedTask

    override def isInboundDone: Boolean = engine.isInboundDone

    override def isOutboundDone: Boolean = engine.isOutboundDone

    override def getSupportedCipherSuites: Array[String] = engine.getSupportedCipherSuites

    override def getEnabledCipherSuites: Array[String] = engine.getEnabledCipherSuites

    override def setEnabledCipherSuites(suites: Array[String]): Unit = engine.setEnabledCipherSuites(suites)

    override def getSupportedProtocols: Array[String] = engine.getSupportedProtocols

    override def getEnabledProtocols: Array[String] = engine.getEnabledProtocols

    override def setEnabledProtocols(protocols: Array[String]): Unit = engine.setEnabledProtocols(protocols)

    override def getHandshakeSession: SSLSession = engine.getHandshakeSession

    override def beginHandshake(): Unit = engine.beginHandshake()

    override def getHandshakeStatus: SSLEngineResult.HandshakeStatus = engine.getHandshakeStatus

    override def setUseClientMode(mode: Boolean): Unit = engine.setUseClientMode(mode)

    override def getUseClientMode: Boolean = engine.getUseClientMode

    override def setNeedClientAuth(need: Boolean): Unit = engine.setNeedClientAuth(need)

    override def getNeedClientAuth: Boolean = engine.getNeedClientAuth

    override def setWantClientAuth(want: Boolean): Unit = engine.setWantClientAuth(want)

    override def getWantClientAuth: Boolean = engine.getWantClientAuth

    override def setEnableSessionCreation(flag: Boolean): Unit = engine.setEnableSessionCreation(flag)

    override def getEnableSessionCreation: Boolean = engine.getEnableSessionCreation

    override def getSSLParameters: SSLParameters = engine.getSSLParameters

    override def setSSLParameters(params: SSLParameters): Unit = engine.setSSLParameters(params)

}
