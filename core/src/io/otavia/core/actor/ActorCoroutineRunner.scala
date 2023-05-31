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

package io.otavia.core.actor

import io.otavia.core.message.{Ask, Call, Notice, Reply}
import io.otavia.core.stack.*

private[core] trait ActorCoroutineRunner[M <: Call] {

    def batchContinueNotice(stack: BatchNoticeStack[M & Notice]): Option[StackState] =
        throw new NotImplementedError(getClass.getName.nn + ": an implementation is missing")

    def batchContinueAsk(stack: BatchAskStack[M & Ask[? <: Reply]]): Option[StackState] =
        throw new NotImplementedError(getClass.getName.nn + ": an implementation is missing")

    def batchNoticeFilter: M & Notice => Boolean = _ => true

    def batchAskFilter: M & Ask[?] => Boolean = _ => true

    def noticeBarrier: M & Notice => Boolean = _ => false

    def askBarrier: M & Ask[?] => Boolean = _ => false

    /** implement this method to handle ask message and resume when received reply message for this notice message
     *
     *  @param state
     *    ask message received by this actor instance, or resume frame .
     *  @return
     *    an option value containing the resumable [[StackState]] waited for some reply message, or `None` if the stack
     *    frame has finished.
     */
    def continueAsk(stack: AskStack[M & Ask[? <: Reply]]): Option[StackState] =
        throw new NotImplementedError(getClass.getName.nn + ": an implementation is missing")

    /** implement this method to handle notice message and resume when received reply message for this notice message
     *
     *  @param state
     *    notice message receive by this actor instance, or resume frame .
     *  @return
     *    an option value containing the resumable [[StackState]] waited for some reply message, or `None` if the stack
     *    frame has finished.
     */
    def continueNotice(stack: NoticeStack[M & Notice]): Option[StackState] =
        throw new NotImplementedError(getClass.getName.nn + ": an implementation is missing")

    def continueChannel(stack: ChannelStack[AnyRef]): Option[StackState] =
        throw new NotImplementedError(getClass.getName.nn + ": an implementation is missing")

}
