/*
 * Copyright 2022 Yan Kun
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

import io.otavia.core.channel.socket.SocketProtocolFamily.*
import io.otavia.core.channel.{Channel, ChannelOption}

import java.net.{InetAddress, NetworkInterface}

/** A UDP/IP [[Channel]].
 *
 *  <h3>Available options</h3>
 *
 *  In addition to the options provided by [[Channel]], [[DatagramChannel]] allows the following options in the option
 *  map:
 *
 *  <table border="1" cellspacing="0" cellpadding="6"> <tr> <th>[[ChannelOption]]</th> <th>[[INET]]</th>
 *  <th>[[INET6]]</th> <th>[[UNIX]]</th> </tr><tr>
 *  <td>[[ChannelOption.DATAGRAM_CHANNEL_ACTIVE_ON_REGISTRATION]]</td><td>X</td><td>X</td><td>-</td> </tr><tr>
 *  <td>[[ChannelOption.SO_BROADCAST]]</td><td>X</td><td>X</td><td>-</td> </tr><tr>
 *  <td>[[ChannelOption.SO_REUSEADDR]]</td><td>X</td><td>X</td><td>-</td> </tr><tr>
 *  <td>[[ChannelOption.SO_RCVBUF]]</td><td>X</td><td>X</td><td>X</td> </tr><tr>
 *  <td>[[ChannelOption.SO_SNDBUF]]</td><td>X</td><td>X</td><td>X</td> </tr><tr>
 *  <td>[[ChannelOption.IP_MULTICAST_LOOP_DISABLED]]</td><td>X</td><td>X</td><td>-</td> </tr><tr>
 *  <td>[[ChannelOption.IP_MULTICAST_IF]]</td><td>X</td><td>X</td><td>-</td> </tr><tr>
 *  <td>[[ChannelOption.IP_MULTICAST_TTL]]</td><td>X</td><td>X</td><td>-</td> </tr><tr>
 *  <td>[[ChannelOption.IP_TOS]]</td><td>X</td><td>X</td><td>-</td> </tr> </table>
 */
trait DatagramChannel extends Channel {

    /** Return true if the [[DatagramChannel]] is connected to the remote peer. */
    def isConnected: Boolean

    /** Joins a multicast group. If the underlying implementation does not support this operation it will throws an
     *  [[UnsupportedOperationException]].
     *
     *  @param multicast
     *    the multicast group address.
     *  @throws UnsupportedOperationException
     *    If the underlying implementation does not support this operation.
     */
    @throws[UnsupportedOperationException]
    def joinGroup(multicast: InetAddress): Unit

    /** Joins the specified multicast group at the specified interface .
     *
     *  If the underlying implementation does not support this operation it will throws an
     *  [[UnsupportedOperationException]].
     *
     *  @param multicast
     *    the multicast group address.
     *  @param interface
     *    the interface to use.
     *  @param source
     *    the source address.
     *  @throws UnsupportedOperationException
     *    If the underlying implementation does not support this operation.
     */
    @throws[UnsupportedOperationException]
    def joinGroup(multicast: InetAddress, interface: NetworkInterface, source: Option[InetAddress]): Unit

    /** Leaves a multicast group.
     *
     *  If the underlying implementation does not support this operation it will throws an
     *  [[UnsupportedOperationException]].
     *
     *  @param multicast
     *    the multicast group address.
     *  @throws UnsupportedOperationException
     *    If the underlying implementation does not support this operation.
     */
    @throws[UnsupportedOperationException]
    def leaveGroup(multicast: InetAddress): Unit

    /** Leave the specified multicast group at the specified interface using the specified source.
     *
     *  If the underlying implementation does not support this operation it will throws an
     *  [[UnsupportedOperationException]].
     *
     *  @param multicast
     *    the multicast group address.
     *  @param interface
     *    the interface to use.
     *  @param source
     *    the source address (might be [[None]]).
     *  @throws UnsupportedOperationException
     *    If the underlying implementation does not support this operation.
     */
    @throws[UnsupportedOperationException]
    def leaveGroup(multicast: InetAddress, interface: NetworkInterface, source: Option[InetAddress]): Unit

    /** Block the given source address for the given multicast address on the given network interface .
     *
     *  If the underlying implementation does not support this operation it will throws an
     *  [[UnsupportedOperationException]].
     *
     *  @param multicast
     *    the multicast group address.
     *  @param interface
     *    the interface to use.
     *  @param source
     *    the source address.
     *  @throws UnsupportedOperationException
     *    If the underlying implementation does not support this operation.
     */
    @throws[UnsupportedOperationException]
    def block(multicast: InetAddress, interface: NetworkInterface, source: InetAddress): Unit

    /** Block the given source address for the given multicast address.
     *
     *  If the underlying implementation does not support this operation it will throws an
     *  [[UnsupportedOperationException]].
     *
     *  @param multicast
     *    the multicast group address.
     *  @param source
     *    the source address.
     *  @throws UnsupportedOperationException
     *    If the underlying implementation does not support this operation.
     */
    @throws[UnsupportedOperationException]
    def block(multicast: InetAddress, source: InetAddress): Unit

}
