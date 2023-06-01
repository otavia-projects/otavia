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

import io.otavia.core.timer.Timer

/** A time-out [[Promise]] */
abstract class TimeoutablePromise[V] extends AbstractPromise[V] {

    private var tid: Long = Timer.INVALID_TIMEOUT_REGISTER_ID

    def setTimeoutId(id: Long): Unit = tid = id

    def timeoutId: Long = tid

    final override def canTimeout: Boolean = tid != Timer.INVALID_TIMEOUT_REGISTER_ID

    override protected def cleanInstance(): Unit = {
        tid = Timer.INVALID_TIMEOUT_REGISTER_ID
        super.cleanInstance()
    }

}
