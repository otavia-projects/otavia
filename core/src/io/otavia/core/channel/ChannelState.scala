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

import io.otavia.core.channel.ChannelState.*
import io.otavia.core.util.CompressionBooleanLong

/** A trait for manage lifecycle for [[Channel]] */
trait ChannelState extends CompressionBooleanLong {
    this: Channel =>

    protected final def created_=(value: Boolean): Unit = set(ST_CREATED, value)
    protected final def created: Boolean                = get(ST_CREATED)

    protected final def neverRegistered_=(value: Boolean): Unit = set(ST_NEVER_REGISTERED, value)
    protected final def neverRegistered: Boolean                = get(ST_NEVER_REGISTERED)

    protected final def registering_=(value: Boolean): Unit = set(ST_REGISTERING, value)
    protected final def registering: Boolean                = get(ST_REGISTERING)

    protected final def registered_=(value: Boolean): Unit = set(ST_REGISTERED, value)
    protected final def registered: Boolean                = get(ST_REGISTERED)

    protected final def binding_=(value: Boolean): Unit = set(ST_BINDING, value)
    protected final def binding: Boolean                = get(ST_BINDING)

    protected final def bound_=(value: Boolean): Unit = set(ST_BOUND, value)
    protected final def bound: Boolean                = get(ST_BOUND)

    protected final def connecting_=(value: Boolean): Unit = set(ST_CONNECTING, value)
    protected final def connecting: Boolean                = get(ST_CONNECTING)

    protected final def connected_=(value: Boolean): Unit = set(ST_CONNECTED, value)
    protected final def connected: Boolean                = get(ST_CONNECTED)

    protected final def disconnecting_=(value: Boolean): Unit = set(ST_DISCONNECTING, value)
    protected final def disconnecting: Boolean                = get(ST_DISCONNECTING)

    protected final def disconnected_=(value: Boolean): Unit = set(ST_DISCONNECTED, value)
    protected final def disconnected: Boolean                = get(ST_DISCONNECTED)

    protected final def closing_=(value: Boolean): Unit = set(ST_CLOSING, value)
    protected final def closing: Boolean                = get(ST_CLOSING)

    protected final def closed_=(value: Boolean): Unit = set(ST_CLOSED, value)
    protected final def closed: Boolean                = get(ST_CLOSED)

    protected final def unregistering_=(value: Boolean): Unit = set(ST_UNREGISTERING, value)
    protected final def unregistering: Boolean                = get(ST_UNREGISTERING)

    protected final def unregistered_=(value: Boolean): Unit = set(ST_UNREGISTERED, value)
    protected final def unregistered: Boolean                = get(ST_UNREGISTERED)

    protected final def shutdowningInbound_=(value: Boolean): Unit = set(ST_SHUTDOWNING_INBOUND, value)
    protected final def shutdowningInbound: Boolean                = get(ST_SHUTDOWNING_INBOUND)

    protected final def shutdownedInbound_=(value: Boolean): Unit = set(ST_SHUTDOWNED_INBOUND, value)
    protected final def shutdownedInbound: Boolean                = get(ST_SHUTDOWNED_INBOUND)

    protected final def shutdowningOutbound_=(value: Boolean): Unit = set(ST_SHUTDOWNING_OUTBOUND, value)
    protected final def shutdowningOutbound: Boolean                = get(ST_SHUTDOWNING_OUTBOUND)

    protected final def shutdownedOutbound_=(value: Boolean): Unit = set(ST_SHUTDOWNED_OUTBOUND, value)
    protected final def shutdownedOutbound: Boolean                = get(ST_SHUTDOWNED_OUTBOUND)

    protected final def autoRead_=(value: Boolean): Unit = set(ST_AUTO_READ, value)
    protected final def autoRead: Boolean                = get(ST_AUTO_READ)

    protected final def autoClose_=(value: Boolean): Unit = set(ST_AUTO_CLOSE, value)
    protected final def autoClose: Boolean                = get(ST_AUTO_CLOSE)

    protected final def allowHalfClosure_=(value: Boolean): Unit = set(ST_ALLOW_HALF_CLOSURE, value)
    protected final def allowHalfClosure: Boolean                = get(ST_ALLOW_HALF_CLOSURE)

    protected final def closeInitiated_=(value: Boolean): Unit = set(ST_CLOSE_INITIATED, value)
    protected final def closeInitiated: Boolean                = get(ST_CLOSE_INITIATED)

    protected final def inWriteFlushed_=(value: Boolean): Unit = set(ST_IN_WRITE_FLUSHED, value)
    protected final def inWriteFlushed: Boolean                = get(ST_IN_WRITE_FLUSHED)

    protected final def inputClosedSeenErrorOnRead_=(value: Boolean): Unit =
        set(ST_INPUT_CLOSED_SEEN_ERROR_ON_READ, value)
    protected final def inputClosedSeenErrorOnRead: Boolean = get(ST_INPUT_CLOSED_SEEN_ERROR_ON_READ)

    protected final def writable_=(value: Boolean): Unit = set(ST_WRITABLE, value)
    protected final def writable: Boolean                = get(ST_WRITABLE)

    protected final def neverActive_=(value: Boolean): Unit = set(ST_NEVER_ACTIVE, value)
    protected final def neverActive: Boolean                = get(ST_NEVER_ACTIVE)

    protected final def mounted_=(value: Boolean): Unit = set(ST_MOUNTED, value)
    protected final def mounted: Boolean                = get(ST_MOUNTED)

    override final def isMounted: Boolean = mounted

//    protected final def readSomething_=(value: Boolean): Unit = set(ST_READ_SOMETHING, value)
//    protected final def readSomething: Boolean                = get(ST_READ_SOMETHING)

//    protected final def continueReading_=(value: Boolean): Unit = set(ST_CONTINUE_READING, value)
//    protected final def continueReading: Boolean                = get(ST_CONTINUE_READING)

    /** Set the [[Channel]] inbound head-of-line
     *  @param value
     *    head-of-line
     */
    protected final def inboundHeadOfLine_=(value: Boolean): Unit = set(ST_INBOUND_HOL, value)

    /** The [[Channel]] inbound is head-of-line */
    protected final def inboundHeadOfLine: Boolean = get(ST_INBOUND_HOL)

    protected final def outboundHeadOfLine_=(value: Boolean): Unit = set(ST_OUTBOUND_HOL, value)
    protected final def outboundHeadOfLine: Boolean                = get(ST_OUTBOUND_HOL)

}

object ChannelState {

    // Channel lifecycle state
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

    private val ST_NEVER_ACTIVE_OFFSET: Long = 18
    private val ST_NEVER_ACTIVE: Long        = 1 << ST_NEVER_ACTIVE_OFFSET

    private val ST_MOUNTED_OFFSET: Long = 19
    private val ST_MOUNTED: Long        = 1 << ST_MOUNTED_OFFSET
    // End channel lifecycle state

    // state in ReadSink
    private val ST_READ_SOMETHING_OFFSET: Long = 20
    private val ST_READ_SOMETHING: Long        = 1 << ST_READ_SOMETHING_OFFSET

    private val ST_CONTINUE_READING_OFFSET: Long = 21
    private val ST_CONTINUE_READING: Long        = 1 << ST_CONTINUE_READING_OFFSET

    // end ReadSink

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

    private val ST_WRITABLE_OFFSET: Long = 38
    private val ST_WRITABLE: Long        = 1 << ST_WRITABLE_OFFSET

    // Channel inflight state
    private val ST_OUTBOUND_HOL_OFFSET: Long = 39
    private val ST_OUTBOUND_HOL: Long        = 1 << ST_OUTBOUND_HOL_OFFSET

    private val ST_INBOUND_HOL_OFFSET: Long = 40
    private val ST_INBOUND_HOL: Long        = 1 << ST_INBOUND_HOL_OFFSET

    // End channel inflight state

}
