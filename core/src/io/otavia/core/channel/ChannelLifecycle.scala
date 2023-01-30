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

package io.otavia.core.channel

import io.otavia.core.channel.ChannelLifecycle.*

/** A trait for manage lifecycle for [[Channel]] */
trait ChannelLifecycle {
    this: Channel =>

    private var state: Long = ST_CREATED

    private inline def get(mask: Long): Boolean = (state & mask) != 0

    private inline def set(mask: Long, value: Boolean): Unit =
        if (value) state = state | mask else state = state & (~mask)

    protected def isCreated: Boolean = get(ST_CREATED)

    protected def neverRegistered_=(value: Boolean): Unit = set(ST_NEVER_REGISTERED, value)
    protected def neverRegistered: Boolean                = get(ST_NEVER_REGISTERED)

    protected def registering_=(value: Boolean): Unit = set(ST_REGISTERING, value)
    protected def registering: Boolean                = get(ST_REGISTERING)

    protected def registered_=(value: Boolean): Unit = set(ST_REGISTERED, value)
    protected def registered: Boolean                = get(ST_REGISTERED)

    protected def binding_=(value: Boolean): Unit = set(ST_BINDING, value)
    protected def binding: Boolean                = get(ST_BINDING)

    protected def bound_=(value: Boolean): Unit = set(ST_BOUND, value)
    protected def bound: Boolean                = get(ST_BOUND)

    protected def connecting_=(value: Boolean): Unit = set(ST_CONNECTING, value)
    protected def connecting: Boolean                = get(ST_CONNECTING)

    protected def connected_=(value: Boolean): Unit = set(ST_CONNECTED, value)
    protected def connected: Boolean                = get(ST_CONNECTED)

    protected def disconnecting_=(value: Boolean): Unit = set(ST_DISCONNECTING, value)
    protected def disconnecting: Boolean                = get(ST_DISCONNECTING)

    protected def disconnected_=(value: Boolean): Unit = set(ST_DISCONNECTED, value)
    protected def disconnected: Boolean                = get(ST_DISCONNECTED)

    protected def closing_=(value: Boolean): Unit = set(ST_CLOSING, value)
    protected def closing: Boolean                = get(ST_CLOSING)

    protected def closed_=(value: Boolean): Unit = set(ST_CLOSED, value)
    protected def closed: Boolean                = get(ST_CLOSED)

    protected def unregistering_=(value: Boolean): Unit = set(ST_UNREGISTERING, value)
    protected def unregistering: Boolean                = get(ST_UNREGISTERING)

    protected def unregistered_=(value: Boolean): Unit = set(ST_UNREGISTERED, value)
    protected def unregistered: Boolean                = get(ST_UNREGISTERED)

    protected def shutdowningInbound_=(value: Boolean): Unit = set(ST_SHUTDOWNING_INBOUND, value)
    protected def shutdowningInbound: Boolean                = get(ST_SHUTDOWNING_INBOUND)

    protected def shutdownedInbound_=(value: Boolean): Unit = set(ST_SHUTDOWNED_INBOUND, value)
    protected def shutdownedInbound: Boolean                = get(ST_SHUTDOWNED_INBOUND)

    protected def shutdowningOutbound_=(value: Boolean): Unit = set(ST_SHUTDOWNING_OUTBOUND, value)
    protected def shutdowningOutbound: Boolean                = get(ST_SHUTDOWNING_OUTBOUND)

    protected def shutdownedOutbound_=(value: Boolean): Unit = set(ST_SHUTDOWNED_OUTBOUND, value)
    protected def shutdownedOutbound: Boolean                = get(ST_SHUTDOWNED_OUTBOUND)

    protected def autoRead_=(value: Boolean): Unit = set(ST_AUTO_READ, value)
    protected def autoRead: Boolean                = get(ST_AUTO_READ)

    protected def autoClose_=(value: Boolean): Unit = set(ST_AUTO_CLOSE, value)
    protected def autoClose: Boolean                = get(ST_AUTO_CLOSE)

    protected def allowHalfClosure_=(value: Boolean): Unit = set(ST_ALLOW_HALF_CLOSURE)
    protected def allowHalfClosure: Boolean                = get(ST_ALLOW_HALF_CLOSURE)

}

object ChannelLifecycle {

    private val ST_CREATED_OFFSET: Long = 0
    private val ST_CREATED: Long        = 1 << ST_CREATED_OFFSET

    private val ST_NEVER_REGISTERED_OFFSET: Long = 1
    private val ST_NEVER_REGISTERED: Long        = 1 << ST_NEVER_REGISTERED_OFFSET

    private val ST_REGISTERING_OFFSET: Long = 2
    private val ST_REGISTERING: Long        = 1 << ST_REGISTERING_OFFSET

    private val ST_REGISTERED_OFFSET: Long = 3
    private val ST_REGISTERED: Long        = 1 << ST_REGISTERED_OFFSET

    private val ST_BINDING_OFFSET: Long = 4
    private val ST_BINDING: Long        = 1 << ST_BINDING_OFFSET

    private val ST_BOUND_OFFSET: Long = 5
    private val ST_BOUND: Long        = 1 << ST_BOUND_OFFSET

    private val ST_CONNECTING_OFFSET: Long = 6
    private val ST_CONNECTING: Long        = 1 << ST_CONNECTING_OFFSET

    private val ST_CONNECTED_OFFSET: Long = 7
    private val ST_CONNECTED: Long        = 1 << ST_CONNECTED_OFFSET

    private val ST_DISCONNECTING_OFFSET: Long = 8
    private val ST_DISCONNECTING: Long        = 1 << ST_DISCONNECTING_OFFSET

    private val ST_DISCONNECTED_OFFSET: Long = 9
    private val ST_DISCONNECTED: Long        = 1 << ST_DISCONNECTED_OFFSET

    private val ST_CLOSING_OFFSET: Long = 10
    private val ST_CLOSING: Long        = 1 << ST_CLOSING_OFFSET

    private val ST_CLOSED_OFFSET: Long = 11
    private val ST_CLOSED: Long        = 1 << ST_CLOSED_OFFSET

    private val ST_UNREGISTERING_OFFSET: Long = 12
    private val ST_UNREGISTERING: Long        = 1 << ST_UNREGISTERING_OFFSET

    private val ST_UNREGISTERED_OFFSET: Long = 13
    private val ST_UNREGISTERED: Long        = 1 << ST_UNREGISTERED_OFFSET

    private val ST_SHUTDOWNING_INBOUND_OFFSET: Long = 14
    private val ST_SHUTDOWNING_INBOUND: Long        = 1 << ST_SHUTDOWNING_INBOUND_OFFSET

    private val ST_SHUTDOWNED_INBOUND_OFFSET: Long = 15
    private val ST_SHUTDOWNED_INBOUND: Long        = 1 << ST_SHUTDOWNED_INBOUND_OFFSET

    private val ST_SHUTDOWNING_OUTBOUND_OFFSET: Long = 16
    private val ST_SHUTDOWNING_OUTBOUND: Long        = 1 << ST_SHUTDOWNING_OUTBOUND_OFFSET

    private val ST_SHUTDOWNED_OUTBOUND_OFFSET: Long = 17
    private val ST_SHUTDOWNED_OUTBOUND: Long        = 1 << ST_SHUTDOWNED_OUTBOUND_OFFSET

    private val ST_AUTO_READ_OFFSET: Long = 32
    private val ST_AUTO_READ: Long        = 1 << ST_AUTO_READ_OFFSET

    private val ST_AUTO_CLOSE_OFFSET: Long = 33
    private val ST_AUTO_CLOSE: Long        = 1 << ST_AUTO_CLOSE_OFFSET

    private val ST_ALLOW_HALF_CLOSURE_OFFSET: Long = 34
    private val ST_ALLOW_HALF_CLOSURE: Long        = 1 << ST_ALLOW_HALF_CLOSURE_OFFSET

    private val ST_CLOSE_INITIATED_OFFSET: Long = 35
    private val ST_CLOSE_INITIATED: Long        = 1 << ST_CLOSE_INITIATED_OFFSET

    private val ST_IN_WRITE_FLUSHED_OFFSET: Long = 36
    private val ST_IN_WRITE_FLUSHED: Long        = 1 << ST_IN_WRITE_FLUSHED_OFFSET

    private val ST_INPUT_CLOSED_SEEN_ERROR_ON_READ_OFFSET: Long = 37
    private val ST_INPUT_CLOSED_SEEN_ERROR_ON_READ: Long        = 1 << ST_INPUT_CLOSED_SEEN_ERROR_ON_READ_OFFSET

}
