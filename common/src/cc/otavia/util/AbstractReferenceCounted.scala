/*
 * Copyright 2022 Yan Kun <yan_kun_1992@foxmail.com>
 *
 * This file fork from netty.
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

package cc.otavia.util

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater

abstract class AbstractReferenceCounted extends ReferenceCounted {

    import AbstractReferenceCounted.*

    // Value might not equal "real" reference count, all access should be via the updater
    @volatile private val cnt: Int = updater.initialValue

    override def refCnt: Int = updater.refCnt(this)

    /** An unsafe operation intended for use by a subclass that sets the reference count of the buffer directly */
    protected final def setRefCnt(refCnt: Int): Unit = updater.setRefCnt(this, refCnt)

    override def retain: this.type = {
        updater.retain(this)
        this
    }

    override def retain(increment: Int): this.type = {
        updater.retain(this, increment)
        this
    }

    override def release: Boolean = handleRelease(updater.release(this))

    override def release(decrement: Int): Boolean = handleRelease(updater.release(this, decrement))

    private def handleRelease(result: Boolean): Boolean = {
        if (result) deallocate()
        result
    }

    /** Called once [[refCnt]] is equals 0. */
    protected def deallocate(): Unit

}

object AbstractReferenceCounted {

    private val REFCNT_FIELD_OFFSET = ReferenceCountUpdater.getUnsafeOffset(classOf[AbstractReferenceCounted], "cnt")

    private val AIF_UPDATER: AtomicIntegerFieldUpdater[AbstractReferenceCounted] =
        AtomicIntegerFieldUpdater.newUpdater(classOf[AbstractReferenceCounted], "cnt")

    private val updater = new ReferenceCountUpdater[AbstractReferenceCounted]() {

        override protected def updater: AtomicIntegerFieldUpdater[AbstractReferenceCounted] = AIF_UPDATER

        override protected def unsafeOffset: Long = ???

    }

}
