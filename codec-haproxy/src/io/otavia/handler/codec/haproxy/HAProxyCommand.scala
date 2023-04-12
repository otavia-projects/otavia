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

package io.otavia.handler.codec.haproxy

/** The command of an HAProxy proxy protocol header */
enum HAProxyCommand(val byteValue: Byte) {

    /** The LOCAL command represents a connection that was established on purpose by the proxy without being relayed. */
    case LOCAL extends HAProxyCommand(HAProxyConstants.COMMAND_LOCAL_BYTE)

    /** The PROXY command represents a connection that was established on behalf of another node, and reflects the
     *  original connection endpoints.
     */
    case PROXY extends HAProxyCommand(HAProxyConstants.COMMAND_PROXY_BYTE)

}

object HAProxyCommand {

    /** The command is specified in the lowest 4 bits of the protocol version and command byte */

    private val COMMAND_MASK: Byte = 0x0f

    /** Returns the [[HAProxyCommand]] represented by the lowest 4 bits of the specified byte.
     *  @param verCmdByte
     *    protocol version and command byte
     *  @return
     */
    def valueOf(verCmdByte: Byte): HAProxyCommand = {
        val cmd = verCmdByte & COMMAND_MASK
        cmd.toByte match {
            case HAProxyConstants.COMMAND_PROXY_BYTE => PROXY
            case HAProxyConstants.COMMAND_LOCAL_BYTE => LOCAL
            case _ =>
                throw new IllegalArgumentException("unknown command: " + cmd)
        }
    }

}
