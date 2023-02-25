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

package io.otavia.core.util

import io.otavia.core.actor.StateActor
import io.otavia.core.message.{IdAllocator, Notice}
import io.otavia.core.stack.{NoticeFrame, StackState}
import io.otavia.core.util.InitialService.*

abstract class InitialService extends StateActor[InitialService.MSG] {

    override def continueNotice(state: InitialService.MSG & Notice | NoticeFrame): Option[StackState] = state match
        case initialFromConfig: InitialFromConfig => None
        case initialFromClass: InitialFromClass   => None
        case _                                    => None

}

object InitialService {

    type MSG = InitialFromConfig | InitialFromClass

    final case class InitialFromConfig()(using IdAllocator) extends Notice

    final case class InitialFromClass(clz: Class[?], num: Int)(using IdAllocator) extends Notice

}
