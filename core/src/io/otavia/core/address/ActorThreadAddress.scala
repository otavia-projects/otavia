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

package io.otavia.core.address

import io.otavia.core.reactor.Event
import io.otavia.core.system.ActorThread

class ActorThreadAddress(private val thread: ActorThread) extends EventableAddress {

    override private[core] def inform(event: Event): Unit = thread.putEvent(event)

    override private[core] def inform(events: Seq[Event]): Unit = thread.putEvents(events)

}
