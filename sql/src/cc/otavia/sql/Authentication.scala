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

package cc.otavia.sql

import cc.otavia.core.actor.ChannelsActor.ChannelEstablished
import cc.otavia.core.message.{Ask, Notice}

case class Authentication(url: String, info: Map[String, String], driver: Option[String] = None)
    extends Ask[ChannelEstablished]
    with Notice

object Authentication {
    def apply(url: String, user: String, password: String): Authentication = {
        val info = Map("user" -> user, "password" -> password)
        new Authentication(url, info, None)
    }
}
