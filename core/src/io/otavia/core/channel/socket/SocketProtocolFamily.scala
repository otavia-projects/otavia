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

package io.otavia.core.channel.socket

import java.net.{ProtocolFamily, StandardProtocolFamily}
import scala.language.unsafeNulls

/** [[ProtocolFamily]] implementation that is used by the different transport implementations. */
sealed trait SocketProtocolFamily extends ProtocolFamily {

    /** Return a [[ProtocolFamily]] that is "known" by the JDK itself and represent the same as
     *  [[SocketProtocolFamily]].
     *
     *  @return
     *    the JDK [[ProtocolFamily]].
     */
    def toJdkFamily: ProtocolFamily
}

object SocketProtocolFamily {

    /** IPv4 */
    object INET extends SocketProtocolFamily {

        override def name(): String = "INET"

        override def toJdkFamily: ProtocolFamily = StandardProtocolFamily.INET

    }

    /** IPv6 */
    object INET6 extends SocketProtocolFamily {

        override def name(): String = "INET6"

        override def toJdkFamily: ProtocolFamily = StandardProtocolFamily.INET6

    }

    /** Unix Domain Socket */
    object UNIX extends SocketProtocolFamily {

        override def name(): String = "UNIX"

        /** Just use valueOf as we compile with Java11. If the JDK does not support unix domain sockets, this will
         *  throw.
         *
         *  @return
         *    the JDK [[ProtocolFamily]].
         *  @throws UnsupportedOperationException
         *    if it can't be converted.
         */
        override def toJdkFamily: ProtocolFamily = StandardProtocolFamily.valueOf(name())

    }

    /** Return the [[SocketProtocolFamily]] for the given [[ProtocolFamily]] if possible.
     *  @param family
     *    the JDK [[ProtocolFamily]] to convert.
     *  @return
     *    the [[SocketProtocolFamily]].
     *  @throws UnsupportedOperationException
     *    if it can't be converted.
     */
    def of(family: ProtocolFamily): SocketProtocolFamily = family match
        case family: SocketProtocolFamily           => family
        case StandardProtocolFamily.INET            => INET
        case StandardProtocolFamily.INET6           => INET6
        case _ if UNIX.name().equals(family.name()) => UNIX
        case _ => throw new UnsupportedOperationException("ProtocolFamily is not supported: " + family)

}
