/*
 * Copyright 2022 Yan Kun
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

trait ChannelInflight {
    this: AbstractChannel[?, ?] =>

    private var headOfLine: Boolean = true

    private var _inboundMessageBarrier: AnyRef => Boolean = _ => false

    /** The Channel inbound is head-of-line */
    def isHeadOfLine: Boolean = headOfLine

    /** Set the Channel inbound head-of-line
     *  @param hol
     *    head-of-line
     */
    def setHeadOfLine(hol: Boolean): Unit = headOfLine = hol

    /** Inbound message barrier */
    def inboundMessageBarrier: AnyRef => Boolean = _inboundMessageBarrier

    /** Set inbound message barrier
     *  @param barrier
     */
    def setInboundMessageBarrier(barrier: AnyRef => Boolean): Unit = _inboundMessageBarrier = barrier
    
    

}
