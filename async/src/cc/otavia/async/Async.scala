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

package cc.otavia.async

import cc.otavia.core.address.Address
import cc.otavia.core.message.*
import cc.otavia.core.stack.{Future, StackState}

import scala.annotation.compileTimeOnly

object Async {

    @compileTimeOnly("[async] `await` must be enclosed in an `async` block")
    def await[A <: Ask[? <: Reply]](address: Address[A], ask: A): Future[ReplyOf[A]] = ???

    @compileTimeOnly("[unwarp] `await` must be enclosed in an `async` block")
    def unwarp[A <: Ask[? <: Reply]](address: Address[A], ask: A): ReplyOf[A] = ???

    inline def async(inline body: Option[StackState]): Option[StackState] = ???

}
