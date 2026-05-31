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

package cc.otavia.core.actor

import cc.otavia.core.message.*
import cc.otavia.core.stack.*
import cc.otavia.core.system.ActorSystem
import org.scalatest.funsuite.AnyFunSuite

import scala.language.unsafeNulls

class MessageDispatcherSuite extends AnyFunSuite {

    // =========================================================================
    // Message types used by test actors
    // =========================================================================

    case class Hello(msg: String) extends Ask[HelloReply.type]
    case object HelloReply extends Reply

    case class Ping(id: Int) extends Ask[PingReply.type]
    case object PingReply extends Reply

    case class Hi(msg: String) extends Notice

    // =========================================================================
    // Test actors using deriveDispatch
    // =========================================================================

    type UnionAsk = Hello | Ping

    class UnionAskActor extends StateActor[UnionAsk] {
        deriveDispatch
        def handleHello(stack: AskStack[Hello]): StackYield = stack.`return`(HelloReply)
        def handlePing(stack: AskStack[Ping]): StackYield   = stack.`return`(PingReply)
    }

    class SingleAskActor extends StateActor[Hello] {
        deriveDispatch
        def handleHello(stack: AskStack[Hello]): StackYield = stack.`return`(HelloReply)
    }

    type Mixed = Hello | Hi

    class MixedActor extends StateActor[Mixed] {
        deriveDispatch
        def handleHello(stack: AskStack[Hello]): StackYield = stack.`return`(HelloReply)
        def handleHi(stack: NoticeStack[Hi]): StackYield   = stack.`return`()
    }

    // =========================================================================
    // Tests
    // =========================================================================

    test("deriveDispatch: actor with union type can be built via ActorSystem") {
        val system = ActorSystem.global
        try {
            val address = system.buildActor(() => new UnionAskActor)
            assert(address != null)
        }
    }

    test("deriveDispatch: actor with single message type can be built via ActorSystem") {
        val system = ActorSystem.global
        try {
            val address = system.buildActor(() => new SingleAskActor)
            assert(address != null)
        }
    }

    test("deriveDispatch: actor with mixed Ask+Notice can be built via ActorSystem") {
        val system = ActorSystem.global
        try {
            val address = system.buildActor(() => new MixedActor)
            assert(address != null)
        }
    }

}
