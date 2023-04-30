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

import io.otavia.core.actor.*
import io.otavia.core.address.Address
import io.otavia.core.ioc.Injectable
import io.otavia.core.slf4a.Appender
import io.otavia.core.message.{Ask, IdAllocator, Notice, Reply}
import io.otavia.core.stack.*
import io.otavia.examples.HandleStateActor.{MSG, QueryDB, QueryRedis}

class HandleStateActor extends StateActor[MSG] with Injectable {

    import HandleStateActor.*

    private var redis: Address[QueryRedis]  = _
    private var db: Address[QueryDB]        = _

    override def afterMount(): Unit = {
        redis = autowire("redis-client")
        db = autowire("database-client")
    }

    private def handleRequest(stack: AskStack[Request]): Option[StackState] = {
        stack.stackState match
            case StackState.initialState =>
                logDebug("Initial state")
                val request = stack.ask
                val state   = new WaitRedisState(request.req)
                redis.ask(QueryRedis(request.req), state.redisResponseFuture)
                state.suspend()
            case waitRedisState: WaitRedisState =>
                val redisResponse = waitRedisState.redisResponseFuture.getNow
                if (redisResponse.res == "null") {
                    val dbState = new WaitDBState()
                    db.ask(QueryDB(waitRedisState.query), dbState.dbFuture)
                    dbState.suspend()
                } else {
                    val response = Response(s"hit in redis with result: ${redisResponse.res}")
                    stack.`return`(response)
                }
            case waitDBState: WaitDBState =>
                val res = waitDBState.dbFuture.getNow.res
                stack.`return`(Response(s"hit in database with result: $res"))
    }

    override def continueAsk(stack: AskStack[MSG]): Option[StackState] = stack match
        case stack: AskStack[Request] => handleRequest(stack)

}

object HandleStateActor {

    type MSG = Request

    private class WaitRedisState(ask: String) extends StackState {

        var query: String = ask

        val redisResponseFuture: ReplyFuture[RedisResponse] = ReplyFuture[RedisResponse]()

        override def resumable(): Boolean = redisResponseFuture.isDone

    }

    private class WaitDBState extends StackState {

        val dbFuture: ReplyFuture[DBResponse] = ReplyFuture()

        override def resumable(): Boolean = dbFuture.isDone

    }

    final class TestState extends StackState {

        var a: Int    = _
        var b: String = _
        // def a_=(ax: Int):Unit = {}
        // def b_=(bx: String): Unit = {}
        override def resumable(): Boolean = true

    }

    final case class Request(req: String) extends Ask[Response]

    final case class Response(res: String) extends Reply

    final case class QueryRedis(cmd: String) extends Ask[RedisResponse]

    final case class RedisResponse(res: String) extends Reply

    final case class QueryDB(sql: String) extends Ask[DBResponse]

    final case class DBResponse(res: String) extends Reply

}
