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

package io.otavia.core.stack

import io.otavia.core.actor.Actor

import scala.concurrent.TimeoutException

/** The result of an asynchronous operation.
 *
 *  An asynchronous operation is one that might be completed outside a given [[Actor]] of execution. The operation can
 *  either be performing computation, or I/O, or both.
 *
 *  All I/O operations in Otavia are asynchronous. It means any I/O calls will return immediately with no guarantee that
 *  the requested I/O operation has been completed at the end of the call. Instead, you will be returned with a
 *  [[Future]] instance which gives you the information about the result or status of the I/O operation.
 *
 *  A [[Future]] is either uncompleted or completed. When an I/O operation begins, a new future object is created. The
 *  new future is uncompleted initially - it is neither succeeded, failed, nor cancelled because the I/O operation is
 *  not finished yet. If the I/O operation is finished either successfully, with failure, or by cancellation, the future
 *  is marked as completed with more specific information, such as the cause of the failure. Please note that even
 *  failure and cancellation belong to the completed state.
 *  @tparam V
 *    type of result
 */
trait Future[+V] {

    /** Returns true if and only if the operation was completed successfully. */
    def isSuccess: Boolean

    /** Returns true if and only if the operation was completed and failed. */
    def isFailed: Boolean

    /** Return true if this operation has been completed either [[Promise.setSuccess]] successfully,
     *  [[Promise.setFailure]] unsuccessfully.
     *
     *  @return
     *    true if this operation has completed, otherwise false.
     */
    def isDone: Boolean

    /** Returns true if and only if the operation was completed and failed with [[TimeoutException]]. */
    final def isTimeout: Boolean = if (isFailed) causeUnsafe.isInstanceOf[TimeoutException] else false

    /** Return the successful result of this asynchronous operation, if any. If the operation has not yet been
     *  completed, then this will throw [[IllegalStateException]] . If the operation has been failed with an exception,
     *  then this throw [[cause]] .
     *
     *  @return
     *    the result of this operation, if completed successfully.
     *  @throws IllegalStateException
     *    if this [[Future]] or [[Promise]] has not completed yet.
     *  @throws Throwable
     *    if the operation has been failed with an exception.
     */
    @throws[Throwable]
    def getNow: V

    /** Returns the cause of the failed operation if the operation has failed.
     *
     *  @return
     *    The cause of the failure, if any. Otherwise [[None]] if succeeded.
     *  @throws IllegalStateException
     *    if this [[Promise]] has not completed yet.
     */
    @throws[IllegalStateException]
    def cause: Option[Throwable]

    /** Returns the cause of the failed operation if the operation has failed.
     *
     *  @return
     *    The cause of the failure, if any.
     *  @throws IllegalStateException
     *    if this [[Promise]] has not completed yet, or if it has been succeeded.
     */
    @throws[IllegalStateException]
    def causeUnsafe: Throwable

    private[core] def promise: Promise[_] = this.asInstanceOf[Promise[?]]

}

object Future {
    def apply[V](): DefaultFuture[V] = DefaultPromise()

}
