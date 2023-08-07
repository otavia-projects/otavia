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

package cc.otavia.handler.codec.haproxy

object HAProxyConstants {

    /** Command byte constants */
    private[haproxy] val COMMAND_LOCAL_BYTE: Byte = 0x00
    private[haproxy] val COMMAND_PROXY_BYTE: Byte = 0x01

    /** Version byte constants */
    private[haproxy] val VERSION_ONE_BYTE: Byte = 0x10
    private[haproxy] val VERSION_TWO_BYTE: Byte = 0x20

    /** Transport protocol byte constants */
    private[haproxy] val TRANSPORT_UNSPEC_BYTE: Byte = 0x00
    private[haproxy] val TRANSPORT_STREAM_BYTE: Byte = 0x01
    private[haproxy] val TRANSPORT_DGRAM_BYTE: Byte  = 0x02

    /** Address family byte constants */
    private[haproxy] val AF_UNSPEC_BYTE: Byte = 0x00
    private[haproxy] val AF_IPV4_BYTE: Byte   = 0x10
    private[haproxy] val AF_IPV6_BYTE: Byte   = 0x20
    private[haproxy] val AF_UNIX_BYTE: Byte   = 0x30

    /** Transport protocol and address family byte constants */
    private[haproxy] val TPAF_UNKNOWN_BYTE: Byte     = 0x00
    private[haproxy] val TPAF_TCP4_BYTE: Byte        = 0x11
    private[haproxy] val TPAF_TCP6_BYTE: Byte        = 0x21
    private[haproxy] val TPAF_UDP4_BYTE: Byte        = 0x12
    private[haproxy] val TPAF_UDP6_BYTE: Byte        = 0x22
    private[haproxy] val TPAF_UNIX_STREAM_BYTE: Byte = 0x31
    private[haproxy] val TPAF_UNIX_DGRAM_BYTE: Byte  = 0x32

    /** V2 protocol binary header prefix */
    private[haproxy] val BINARY_PREFIX = Array(
      0x0d.toByte,
      0x0a.toByte,
      0x0d.toByte,
      0x0a.toByte,
      0x00.toByte,
      0x0d.toByte,
      0x0a.toByte,
      0x51.toByte,
      0x55.toByte,
      0x49.toByte,
      0x54.toByte,
      0x0a.toByte
    )

    private[haproxy] val TEXT_PREFIX = Array('P'.toByte, 'R'.toByte, 'O'.toByte, 'X'.toByte, 'Y'.toByte)

}
