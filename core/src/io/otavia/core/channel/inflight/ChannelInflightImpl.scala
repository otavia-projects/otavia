package io.otavia.core.channel.inflight

import io.otavia.core.channel.{AbstractChannel, ChannelInflight}

trait ChannelInflightImpl extends ChannelInflight {
    this: AbstractChannel[?, ?] =>

    private var headOfLine: Boolean = true

    private var inboundMsgBarrier: AnyRef => Boolean = _ => false

    override def isHeadOfLine: Boolean = headOfLine

    override def setHeadOfLine(hol: Boolean): Unit = headOfLine = hol

    override def inboundMessageBarrier: AnyRef => Boolean = inboundMsgBarrier

    override def setInboundMessageBarrier(barrier: AnyRef => Boolean): Unit = inboundMsgBarrier = barrier

    override def ask(value: AnyRef): Unit = ???

    override def notice(value: AnyRef): Unit = ???

    override def reply(value: AnyRef): Unit = ???

    override def generateMessageId: Long = ???

    override private[core] def onInboundMessage(msg: AnyRef): Unit = ???

    override private[core] def onInboundMessage(msg: AnyRef, id: Long): Unit = ???

}
