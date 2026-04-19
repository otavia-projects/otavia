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

package cc.otavia.core.config

/** Actor scheduling priority configuration.
 *
 *  The actor scheduler uses a three-signal model to determine whether an actor should be scheduled with high
 *  priority. High-priority actors are dispatched before normal-priority actors, reducing latency for actors
 *  that need prompt attention.
 *
 *  @param highPriorityReplySize
 *    Reply mailbox size threshold to boost scheduling priority. When an actor's reply mailbox contains more than
 *    this many messages, the actor is scheduled with high priority. Each reply corresponds to a completable
 *    [[cc.otavia.core.channel.MessagePromise]] — processing it completes a future and may unblock suspended stacks.
 *    Default is 2.
 *  @param highPriorityEventSize
 *    Event mailbox size threshold to boost scheduling priority. When an actor's event mailbox contains more than
 *    this many messages, the actor is scheduled with high priority. System events (timer expirations, channel
 *    lifecycle) need timely processing to maintain system responsiveness. Default is 4.
 */
case class PriorityConfig(
    highPriorityReplySize: Int = 2,
    highPriorityEventSize: Int = 4
)
