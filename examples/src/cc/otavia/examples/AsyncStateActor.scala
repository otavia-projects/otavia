package cc.otavia.examples
//
//import cc.otavia.core.actor.{AcceptorActor, ChannelsActor, ClientChannelsActor, StateActor}
//import cc.otavia.core.address.Address
//import cc.otavia.core.async.Async
//import cc.otavia.core.ioc.Injectable
//import cc.otavia.core.log4a.Logger
//import cc.otavia.core.message.*
//import cc.otavia.core.stack
//import cc.otavia.core.stack.*
//import cc.otavia.examples.HandleStateActor.*
//
//import scala.annotation.experimental
//import scala.concurrent.ExecutionContext.Implicits.global
//import scala.concurrent.Future
//
//@experimental
//class AsyncStateActor extends StateActor[MSG] with Injectable {
//
//    var redis: Address[QueryRedis]  = _
//    var db: Address[QueryDB]        = _
//    var log: Address[Logger.LogMsg] = _
//
//    override def afterMount(): Unit = {
//        redis = autowire("redis-client")
//        db = autowire("database-client")
//        log = autowire[Logger]
//    }
//
//    def showExpr(stack: AskStack[Request]): Option[StackState] = Async.async {
//        val request             = stack.ask
//        val variable            = 1 + 2
//        val variable2: Int      = 1 + 2
//        val queryRedis          = QueryRedis(request.req)
//        val redisResponseFuture = Async.await(redis, queryRedis)
//        val p = {
//            val variable3 = variable + variable2
//            val redisRes  = Async.unwarp(redis, queryRedis)
//            variable3
//        }
//
//        Future {
//            println("hello")
//        }
//
//        if (
//          redisResponseFuture.isFailed ||
//          (redisResponseFuture.isSuccess && redisResponseFuture.getNow.res == "null")
//        ) {
//            val dbResponse = Async.unwarp(db, QueryDB(request.req))
//            val res        = Response(s"hit in database with result: ${dbResponse.res}")
//            stack.`return`(res)
//        } else {
//            val redisResponse = redisResponseFuture.getNow
//            stack.`return`(
//              Response(s"hit in redis with result: ${redisResponse.res}")
//            )
//        }
//    }
//
//    override def continueAsk(msg: MSG & Ask[?] | AskFrame): Option[StackState] = Async.async {
//        val aaa: Int = 1
//        // this will auto generate by Macros.async
//        final class State1(var query: String) extends StackState {
//            val redisWaiter: ReplyWaiter[RedisResponse] = new ReplyWaiter()
//            override def resumable(): Boolean           = redisWaiter.received
//        }
//        // this will auto generate by Macros.async
//        final class State2 extends StackState {
//            val dbWaiter: ReplyWaiter[DBResponse] = new ReplyWaiter()
//            override def resumable(): Boolean     = dbWaiter.received
//        }
//
//        msg match {
//            case request: Request =>
//                val state = new State1(request.req)
//                redis.ask(QueryRedis(request.req), state.redisWaiter)
//                Some(state)
//            case stackFrame: StackFrame =>
//                stackFrame.state match
//                    case state: State1 =>
//                        val redisResponse = state.redisWaiter.reply
//                        if (redisResponse.res == "null") {
//                            val dbState = new State2()
//                            db.ask[QueryDB](QueryDB(state.query), dbState.dbWaiter)
//                            Some(dbState)
//                        } else {
//                            stackFrame.`return`(Response(s"hit in redis with result: ${redisResponse.res}"))
//                        }
//                    case dbState: State2 =>
//                        stackFrame.`return`(Response(s"hit in database with result: ${dbState.dbWaiter.reply.res}"))
//        }
//    }
//
//}

object AsyncStateActor // delete this
