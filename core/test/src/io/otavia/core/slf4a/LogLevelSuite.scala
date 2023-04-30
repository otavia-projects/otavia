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

package io.otavia.core.slf4a

import org.scalatest.funsuite.AnyFunSuite

class LogLevelSuite extends AnyFunSuite {

    test("Create LogLevel from integer") {
        assert(LogLevel.fromOrdinal(LogLevel.ALL.ordinal) == LogLevel.ALL)
        assert(LogLevel.fromOrdinal(LogLevel.TRACE.ordinal) == LogLevel.TRACE)
        assert(LogLevel.fromOrdinal(LogLevel.DEBUG.ordinal) == LogLevel.DEBUG)
        assert(LogLevel.fromOrdinal(LogLevel.INFO.ordinal) == LogLevel.INFO)
        assert(LogLevel.fromOrdinal(LogLevel.WARN.ordinal) == LogLevel.WARN)
        assert(LogLevel.fromOrdinal(LogLevel.ERROR.ordinal) == LogLevel.ERROR)
        assert(LogLevel.fromOrdinal(LogLevel.FATAL.ordinal) == LogLevel.FATAL)
    }

    test("LogLevel compare") {
        assert(LogLevel.ALL > LogLevel.TRACE)
        assert(LogLevel.TRACE > LogLevel.DEBUG)
        assert(LogLevel.DEBUG > LogLevel.INFO)
        assert(LogLevel.INFO > LogLevel.WARN)
        assert(LogLevel.WARN > LogLevel.ERROR)
        assert(LogLevel.ERROR > LogLevel.FATAL)
        assert(LogLevel.FATAL > LogLevel.OFF)
    }

}
