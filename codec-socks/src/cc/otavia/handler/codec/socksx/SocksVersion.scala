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

package cc.otavia.handler.codec.socksx

/** The version of SOCKS protocol. */
enum SocksVersion(val byteValue: Byte) {

    /** SOCKS protocol version 4a (or 4) */
    case SOCKS4a extends SocksVersion(0x04)

    /** SOCKS protocol version 5 */
    case SOCKS5 extends SocksVersion(0x05)

    /** Unknown protocol version */
    case UNKNOWN extends SocksVersion(0xff.toByte)

}

object SocksVersion {

    /** Returns the SocksVersion that corresponds to the specified version field value, as defined in the protocol
     *  specification.
     *
     *  @param b
     *  @return
     *    [[UNKNOWN]] if the specified value does not represent a known SOCKS protocol version
     */
    def valueOf(b: Byte): SocksVersion = b match
        case SOCKS4a.byteValue => SOCKS4a
        case SOCKS5.byteValue  => SOCKS5
        case _                 => UNKNOWN
}
