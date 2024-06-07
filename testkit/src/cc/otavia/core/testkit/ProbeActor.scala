package cc.otavia.core.testkit

import cc.otavia.core.actor.{AbstractActor, Actor, StateActor}
import cc.otavia.core.address.Address
import cc.otavia.core.message.{Ask, Notice, Reply, ReplyOf}
import cc.otavia.core.stack.helper.{FutureState, StartState}
import cc.otavia.core.stack.{MessageFuture, NoticeStack, StackState, StackYield}
import cc.otavia.core.testkit.ProbeActor.ProbeStart

import scala.concurrent.{Future, Promise}
import scala.reflect.ClassTag

class ProbeActor[M <: Ask[? <: Reply], R <: ReplyOf[M]: ClassTag](
    address: Address[M],
    msg: M,
    expect: MessageFuture[R] => Boolean,
    result: Promise[Boolean]
) extends StateActor[ProbeStart] {

    override protected def afterMount(): Unit = self.notice(ProbeStart())

    override def resumeNotice(stack: NoticeStack[ProbeStart]): StackYield = {
        stack.state match
            case _: StartState =>
                val state = FutureState[R]()
                address.ask(msg, state.future)
                stack.suspend(state)
            case state: FutureState[?] if state.id == 0 =>
                result.success(expect(state.future.asInstanceOf[MessageFuture[R]]))
                stack.`return`()
    }

}

object ProbeActor {
    case class ProbeStart() extends Notice
}
