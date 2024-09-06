/*
 * Copyright 2022 Yan Kun <yan_kun_1992@foxmail.com>
 *
 * This class is fork from netty
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

package cc.otavia.core.transport.reactor.nio

import cc.otavia.core.transport.reactor.nio.SelectedSelectionKeySet

import java.io.IOException
import java.nio.channels.spi.SelectorProvider
import java.nio.channels.{SelectionKey, Selector}
import java.util
import scala.language.unsafeNulls

final class SelectedSelectionKeySetSelector(
    private val delegate: Selector,
    private val selectionKeys: SelectedSelectionKeySet
) extends Selector {

    override def isOpen: Boolean = delegate.isOpen

    override def provider(): SelectorProvider = delegate.provider()

    override def keys(): util.Set[SelectionKey] = delegate.keys()

    override def selectedKeys(): util.Set[SelectionKey] = delegate.selectedKeys()

    @throws[IOException]
    override def selectNow(): Int = {
        selectionKeys.reset()
        delegate.selectNow()
    }

    @throws[IOException]
    override def select(timeout: Long): Int = {
        selectionKeys.reset()
        delegate.select(timeout)
    }

    @throws[IOException]
    override def select(): Int = {
        selectionKeys.reset()
        delegate.select()
    }

    override def wakeup(): Selector = delegate.wakeup()

    override def close(): Unit = delegate.close()

}
