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

import cc.otavia.internal.Platform

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater
import scala.util.Try

/** Common logic for [[ReferenceCounted]] implementations */
abstract class ReferenceCountUpdater[T <: ReferenceCounted] {

    import ReferenceCountUpdater.*

    protected def updater: AtomicIntegerFieldUpdater[T]

    protected def unsafeOffset: Long

    def initialValue = 2

    private def nonVolatileRawCnt(instance: T) = {
        val offset = unsafeOffset
        if (offset != -1) Platform.getInt(instance, offset) else updater.get(instance)
    }

    final def refCnt(instance: T): Int = realRefCnt(updater.get(instance))

    final def isLiveNonVolatile(instance: T): Boolean = {
        val offset = unsafeOffset
        val rawCnt = if (offset != -1) Platform.getInt(instance, offset) else updater.get(instance)

        // the real ref count is > 0 if the rawCnt is even.
        rawCnt == 2 || rawCnt == 4 || rawCnt == 6 || rawCnt == 8 || (rawCnt & 1) == 0
    }

    /** An unsafe operation that sets the reference count directly */
    final def setRefCnt(instance: T, refCnt: Int): Unit =
        updater.set(instance, if (refCnt > 0) refCnt << 1 else 1) // overflow OK here

    /** Resets the reference count to 1 */
    final def resetRefCnt(instance: T): Unit = updater.lazySet(instance, initialValue)

    final def retain(instance: T): T = retain0(instance, 1, 2)

    final def retain(instance: T, increment: Int): T = {
        assert(increment > 0, s"increment : $increment (expected: > 0)")
        // all changes to the raw count are 2x the "real" change - overflow is OK
        val rawIncrement = increment << 1
        retain0(instance, increment, rawIncrement)
    }

    // rawIncrement == increment << 1
    private def retain0(instance: T, increment: Int, rawIncrement: Int): T = {
        val oldRef = updater.getAndAdd(instance, rawIncrement)
        if (oldRef != 2 && oldRef != 4 && (oldRef & 1) != 0) throw new IllegalReferenceCountException(0, increment)
        // don't pass 0!
        if ((oldRef <= 0 && oldRef + rawIncrement >= 0) || (oldRef >= 0 && oldRef + rawIncrement < oldRef)) {
            // overflow case
            updater.getAndAdd(instance, -rawIncrement)
            throw new IllegalReferenceCountException(realRefCnt(oldRef), increment)
        }

        instance
    }

    final def release(instance: T): Boolean = {
        val rawCnt = nonVolatileRawCnt(instance)
        if (rawCnt == 2) tryFinalRelease0(instance, 2) || retryRelease0(instance, 1)
        else nonFinalRelease0(instance, 1, rawCnt, toLiveRealRefCnt(rawCnt, 1))
    }

    final def release(instance: T, decrement: Int): Boolean = {
        val rawCnt = nonVolatileRawCnt(instance)
        assert(decrement > 0, s"decrement : $decrement (expected: > 0)")
        val realCnt = toLiveRealRefCnt(rawCnt, decrement)
        if (decrement == realCnt) tryFinalRelease0(instance, rawCnt) || retryRelease0(instance, decrement)
        else nonFinalRelease0(instance, decrement, rawCnt, realCnt)
    }

    private def tryFinalRelease0(instance: T, expectRawCnt: Int): Boolean =
        updater.compareAndSet(instance, expectRawCnt, 1) // any odd number will work

    private def nonFinalRelease0(instance: T, decrement: Int, rawCnt: Int, realCnt: Int): Boolean =
        if (
          decrement < realCnt &&
          // all changes to the raw count are 2x the "real" change - overflow is OK
          updater.compareAndSet(instance, rawCnt, rawCnt - (decrement << 1))
        ) false
        else retryRelease0(instance, decrement)

    private def retryRelease0(instance: T, decrement: Int): Boolean = {
        var continue     = true
        var res: Boolean = false
        while (continue) {
            val rawCnt  = updater.get(instance)
            val realCnt = toLiveRealRefCnt(rawCnt, decrement)
            if (decrement == realCnt) {
                if (tryFinalRelease0(instance, rawCnt)) {
                    continue = false
                    res = true
                }
            } else if (decrement > realCnt) {
                // all changes to the raw count are 2x the "real" change
                if (updater.compareAndSet(instance, rawCnt, rawCnt - (decrement << 1))) {
                    continue = false
                    res = false
                }
            } else throw new IllegalReferenceCountException(realCnt, -decrement)

            Thread.`yield`() // this benefits throughput under high contention
        }

        res
    }

}

object ReferenceCountUpdater {

    def getUnsafeOffset(clz: Class[? <: ReferenceCounted], fieldName: String): Long =
        Try {
            if (Platform.hasUnsafe) Platform.objectFieldOffset(clz.getDeclaredField(fieldName)) else -1
        }.getOrElse(-1)

    private def realRefCnt(rawCnt: Int) = if (rawCnt != 2 && rawCnt != 4 && (rawCnt & 1) != 0) 0 else rawCnt >>> 1

    /** Like [[realRefCnt]]( int ) but throws if refCnt == 0 */
    private def toLiveRealRefCnt(rawCnt: Int, decrement: Int): Int = {
        if (rawCnt == 2 || rawCnt == 4 || (rawCnt & 1) == 0) return rawCnt >>> 1
        // odd rawCnt => already deallocated
        throw new IllegalReferenceCountException(0, -decrement)
    }

}
