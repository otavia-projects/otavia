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

package cc.otavia.core.message

/** The base type of all data exchanged between actors. Every value sent to or received from an actor must be a
 *  [[Message]]. The message hierarchy defines the communication patterns:
 *    1. [[Call]] — triggers stack coroutine execution in the receiving actor
 *       a. [[Notice]] — fire-and-forget, no reply expected
 *       b. [[Ask]][R] — request-response, expects a [[Reply]] of type [[R]]
 *    1. [[Reply]] — the response to an [[Ask]]
 */
sealed trait Message extends Serializable

/** A [[Message]] that triggers stack coroutine execution when received by an [[cc.otavia.core.actor.Actor]].
 *
 *  On delivery, the runtime creates a [[cc.otavia.core.stack.Stack]] (AskStack, NoticeStack, or batch variant) and
 *  invokes the actor's corresponding resume method.
 */
sealed trait Call extends Message

/** A [[Call]] that does not expect a reply. The sender has no way to know when (or whether) the notice was processed.
 *
 *  Example:
 *  {{{
 *  case class LogEntry(text: String) extends Notice
 *  }}}
 */
trait Notice extends Call

/** A [[Call]] that expects a reply of type [[R]]. When an actor receives an [[Ask]], it must eventually call
 *  `stack.return(reply)` to deliver a [[R]] back to the sender.
 *
 *  The match type [[ReplyOf]] extracts [[R]] from an [[Ask]] at compile time, enabling type-safe request-response
 *  patterns without manual casting:
 *  {{{
 *  case class GetUser(id: Long) extends Ask[User]
 *  // ReplyOf[GetUser] resolves to User at compile time
 *  }}}
 *
 *  @tparam R
 *    the expected reply type
 */
trait Ask[R <: Reply] extends Call

/** Match type that extracts the [[Reply]] type parameter from an [[Ask]]. Given `GetUser extends Ask[User]`,
 *  `ReplyOf[GetUser]` resolves to `User`.
 */
type ReplyOf[A <: Ask[? <: Reply]] <: Reply = A match
    case Ask[r] => r

/** The response to an [[Ask]]. Each [[Reply]] completes exactly one pending ask, waking the suspended
 *  [[cc.otavia.core.stack.Stack]] in the sending actor.
 */
trait Reply extends Message

/** A [[Reply]] indicating that an [[Ask]] timed out before the target actor responded. */
sealed trait TimeoutReply extends Reply

object TimeoutReply {

    private val INSTANCE      = new TimeoutReply {}
    def apply(): TimeoutReply = INSTANCE

}
