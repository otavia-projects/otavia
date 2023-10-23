package cc.otavia.core.testkit

import cc.otavia.core.actor.{AbstractActor, Actor, StateActor}
import cc.otavia.core.address.Address
import cc.otavia.core.message.{Ask, Notice, Reply, ReplyOf}
import cc.otavia.core.stack.helper.FutureState
import cc.otavia.core.stack.{NoticeStack, ReplyFuture, StackState}
import cc.otavia.core.testkit.ProbeActor.ProbeStart

import scala.concurrent.{Future, Promise}
import scala.reflect.ClassTag

class ProbeActor[M <: Ask[? <: Reply], R <: ReplyOf[M]: ClassTag](
    address: Address[M],
    msg: M,
    expect: ReplyFuture[R] => Boolean,
    result: Promise[Boolean]
) extends StateActor[ProbeStart] {

    override protected def afterMount(): Unit = self.notice(ProbeStart())

    override def continueNotice(stack: NoticeStack[ProbeStart]): Option[StackState] = {
        stack.state match
            case StackState.start =>
                val state = FutureState[R]()
                address.ask(msg, state.future)
                state.suspend()
            case state: FutureState[?] if state.id == 0 =>
                result.success(expect(state.future.asInstanceOf[ReplyFuture[R]]))
                stack.`return`()
    }

}

object ProbeActor {
    case class ProbeStart() extends Notice
}
