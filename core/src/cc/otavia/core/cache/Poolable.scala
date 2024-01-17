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

package cc.otavia.core.cache

import cc.otavia.core.system.{ActorSystem, ActorThreadPool}
import cc.otavia.core.util.Nextable

import scala.language.unsafeNulls

/** An object which can be pooled */
trait Poolable extends Nextable {

    private var thread: Thread = _

    /** The [[Thread]] which created this instance. */
    private[core] def creator: Thread = thread

    /** Set the creator [[Thread]]. */
    private[core] def creator(t: Thread): Unit = thread = t

    /** Recycle this instance. */
    def recycle(): Unit

    /** Clean the field of this instance. */
    protected def cleanInstance(): Unit

    /** Clean all fields of this instance. */
    final def clean(): Unit = {
        this.deChain()
        this.cleanInstance()
    }

}
