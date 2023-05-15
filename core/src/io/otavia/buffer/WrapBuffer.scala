package io.otavia.buffer

import java.nio.ByteBuffer

class WrapBuffer(underlying: ByteBuffer) extends AbstractBuffer(underlying) {

    override def close(): Unit = ???

}
