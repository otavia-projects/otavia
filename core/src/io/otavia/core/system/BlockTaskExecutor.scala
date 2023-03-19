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

package io.otavia.core.system

import io.otavia.core.actor.Actor
import io.otavia.core.channel.Channel
import io.otavia.core.stack.BlockFuture

import java.util.concurrent.Executors
import scala.language.unsafeNulls

class BlockTaskExecutor(nThreads: Int) {

    private val executor = Executors.newFixedThreadPool(nThreads)

    def executeBlocking[V](future: BlockFuture[V], owner: Actor[?]): BlockFuture[V] = {
        executor.submit(future.promise)

        future
    }

    def executeBlocking[V](future: BlockFuture[V], owner: Channel): BlockFuture[V] = ???

}
