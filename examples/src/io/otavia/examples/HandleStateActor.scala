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

package io.otavia.examples

import io.otavia.core.actor.{ExceptionStrategy, StateActor}
import io.otavia.core.address.Address
import io.otavia.core.ioc.Injectable
import io.otavia.core.message.{Ask, IdAllocator, Notice, Reply}
import io.otavia.core.stack.*
import io.otavia.examples.HandleStateActor.{MSG, QueryDB, QueryRedis}

class HandleStateActor extends StateActor[MSG] with Injectable {

    import HandleStateActor.*

    var redis: Address[QueryRedis] = _
    var db: Address[QueryDB]       = _

    override def afterMount(): Unit = {
        redis = autowire("redis-client")
        db = autowire("database-client")
    }

    override def continueAsk(state: MSG & Ask[?] | AskFrame): Option[StackState] = state match
        case request: Request =>
            val state = new WaitRedisState(request.req)
            redis.ask(QueryRedis(request.req), state.redisWaiter)
            Some(state)
        case frame: AskFrame =>
            frame.state match
                case state: WaitRedisState =>
                    val redisResponse = state.redisWaiter.reply
                    if (redisResponse.res == "null") {
                        val dbState = new WaitDBState()
                        db.ask(QueryDB(state.query), dbState.dbWaiter)
                        dbState.suspend()
                    } else {
                        val response = Response(s"hit in redis with result: ${redisResponse.res}")
                        frame.`return`(response)
                    }
                case dbState: WaitDBState =>
                    frame.`return`(Response(s"hit in database with result: ${dbState.dbWaiter.reply.res}"))

}

object HandleStateActor {

    type MSG = Request

    class WaitRedisState(ask: String) extends StackState {

        var query: String = ask

        val redisWaiter: ReplyWaiter[RedisResponse] = new ReplyWaiter()

        override def resumable(): Boolean = redisWaiter.received

    }

    class WaitDBState extends StackState {

        val dbWaiter: ReplyWaiter[DBResponse] = new ReplyWaiter()

        override def resumable(): Boolean = dbWaiter.received

    }

    final class TestState extends StackState {

        var a: Int    = _
        var b: String = _
        // def a_=(ax: Int):Unit = {}
        // def b_=(bx: String): Unit = {}
        override def resumable(): Boolean = true

    }

    final case class Request(req: String)(using IdAllocator) extends Ask[Response]

    final case class Response(res: String)(using IdAllocator) extends Reply

    final case class QueryRedis(cmd: String)(using IdAllocator) extends Ask[RedisResponse]

    final case class RedisResponse(res: String)(using IdAllocator) extends Reply

    final case class QueryDB(sql: String)(using IdAllocator) extends Ask[DBResponse]

    final case class DBResponse(res: String)(using IdAllocator) extends Reply

}
