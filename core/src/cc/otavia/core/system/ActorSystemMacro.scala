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

package cc.otavia.core.system

import cc.otavia.core.actor.{Actor, ChannelsActor, MessageOf}
import cc.otavia.core.address.Address
import cc.otavia.core.message.Call

import scala.quoted.*

object ActorSystemMacro {

    def buildActorImpl[A <: Actor[? <: Call]](argsExpr: Expr[Seq[Any]], systemExpr: Expr[ActorSystem])(using
        q: Quotes,
        t1: Type[MessageOf[A]],
        t2: Type[A]
    ): Expr[Address[MessageOf[A]]] = {
        import q.reflect.*
        '{
            val system: ActorSystem = ${ systemExpr }
            val actor               = ${ newActor(argsExpr) }
            val isIoActor           = if (actor.isInstanceOf[ChannelsActor[?]]) true else false

            val thread = system.pool.next(isIoActor)

            val address = system.setActorContext(actor, thread)

            address
        }
    }

    private def newActor[A <: Actor[? <: Call]](params: Expr[Seq[Any]])(using q: Quotes, t: Type[A]): Expr[A] = {
        import q.reflect.*

        params match
            case Varargs(args) =>
                Select.overloaded(New(TypeTree.of[A]), "<init>", Nil, args.toList.map(_.asTerm)).asExprOf[A]
    }

}
