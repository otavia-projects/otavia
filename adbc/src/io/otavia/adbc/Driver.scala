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

package io.otavia.adbc

import io.otavia.core.actor.ChannelsActor
import io.otavia.core.message.{Ask, IdAllocator, Reply}

abstract class Driver extends ChannelsActor[Driver.MSG] {}

object Driver {
  type MSG = Connect

  final case class Connect(url: String, username: String, password: String) extends Ask[Connected]
  final case class Connected() extends Reply

}
