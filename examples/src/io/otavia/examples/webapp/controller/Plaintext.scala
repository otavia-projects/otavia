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

package io.otavia.examples.webapp.controller

import io.otavia.core.actor.StateActor
import io.otavia.core.stack.{AskFrame, StackFrame, StackState}
//import io.otavia.http.annotation.Controller
import io.otavia.http.{HttpRequest, HttpResponse}

//@Controller("/plaintext")
class Plaintext extends StateActor[HttpRequest[String]] {

  import Plaintext.*

  //  given codec: JsonValueCodec[User] = JsonCodecMaker.make

  override def continueAsk(state: HttpRequest[String] | AskFrame): Option[StackState] = state match
    case ask: HttpRequest[String] => ask.reply(HttpResponse(ask.headers, "hello"))
    case _                        => None

  //  @mapping("/")
  def handle(ask: HttpRequest[String]): Option[StackState] = {
    None
  }

}

object Plaintext {
  case class Device(id: Int, model: String)

  case class User(name: String, devices: Seq[Device])
}
