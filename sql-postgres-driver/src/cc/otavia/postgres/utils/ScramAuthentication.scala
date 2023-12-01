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

package cc.otavia.postgres.utils

import cc.otavia.buffer.Buffer
import com.ongres.scram.client.{ScramClient, ScramSession}
import com.ongres.scram.common.exception.ScramException
import com.ongres.scram.common.stringprep.StringPreparations

import java.nio.charset.StandardCharsets
import scala.collection.mutable
import scala.language.unsafeNulls

case class ScramAuthentication(username: String, password: String) {

    import ScramAuthentication.*

    private var session: ScramSession                                   = _
    private var clientFinalProcessor: ScramSession#ClientFinalProcessor = _

    /** The client selects one of the supported mechanisms from the list, and sends a SASLInitialResponse message to the
     *  server. The message includes the name of the selected mechanism, and an optional Initial Client Response, if the
     *  selected mechanism uses that.
     *
     *  @param buffer
     *  @return
     */
    def initialSaslMsg(buffer: Buffer): ScramClientInitialMessage = {
        val mechanisms = mutable.ArrayBuffer.empty[String]
        while (0 != buffer.getByte(buffer.readerOffset)) mechanisms.append(PgBufferUtils.readCString(buffer))
        buffer.readByte
        if (mechanisms.isEmpty)
            throw new UnsupportedOperationException("SASL Authentication : the server returned no mechanism")
        if (!mechanisms.contains(SCRAM_SHA_256))
            throw new UnsupportedOperationException(
              s"SASL Authentication : only SCRAM-SHA-256 is currently supported, server wants ${mechanisms.mkString("[", ", ", "]")}"
            )
        val scramClient = ScramClient
            .channelBinding(ScramClient.ChannelBinding.NO)
            .stringPreparation(StringPreparations.NO_PREPARATION)
            .selectMechanismBasedOnServerAdvertised(mechanisms.toSeq: _*)
            .setup()

        session = scramClient.scramSession(username)

        ScramClientInitialMessage(scramClient.getScramMechanism.getName, session.clientFirstMessage())
    }

    /** One or more server-challenge and client-response message will follow. Each server-challenge is sent in an
     *  AuthenticationSASLContinue message, followed by a response from client in an SASLResponse message. The
     *  particulars of the messages are mechanism specific.
     */
    def recvServerFirstMsg(buffer: Buffer): String = {
        val msg = buffer.readCharSequence(buffer.readableBytes, StandardCharsets.UTF_8).toString
        val processor =
            try {
                session.receiveServerFirstMessage(msg)
            } catch {
                case e: ScramException =>
                    throw new UnsupportedOperationException(e)
            }
        clientFinalProcessor = processor.clientFinalProcessor(password)

        clientFinalProcessor.clientFinalMessage()
    }

    /** Finally, when the authentication exchange is completed successfully, the server sends an AuthenticationSASLFinal
     *  message, followed immediately by an AuthenticationOk message.
     *
     *  The AuthenticationSASLFinal contains additional server-to-client data, whose content is particular to the
     *  selected authentication mechanism.
     *
     *  If the authentication mechanism doesn't use additional data that's sent at completion, the
     *  AuthenticationSASLFinal message is not sent
     */
    def checkServerFinalMsg(buffer: Buffer, length: Int): Unit = {
        val serverFinalMsg = buffer.readCharSequence(length, StandardCharsets.UTF_8).toString
        try {
            clientFinalProcessor.receiveServerFinalMessage(serverFinalMsg)
        } catch {
            case e: Exception =>
                throw new UnsupportedOperationException(e)
        }
    }

}

object ScramAuthentication {

    case class ScramClientInitialMessage(mechanism: String, message: String)

    private val SCRAM_SHA_256 = "SCRAM-SHA-256"

}
