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

package io.otavia.core.util

trait CompressionBooleanLong {

    private var compression: Long = 0

    protected final inline def setAt(position: Int, value: Boolean): Unit = {
        val mask = 1 << position
        if (value) compression = compression | mask else compression = compression & (~mask)
    }

    protected final inline def getAt(position: Int): Boolean = {
        val mask = 1 << position
        (compression & mask) != 0
    }

    protected final inline def set(mask: Long, value: Boolean): Unit =
        if (value) compression = (compression | mask) else compression = (compression & (~mask))

    protected final inline def get(mask: Long): Boolean = (compression & mask) != 0

}
