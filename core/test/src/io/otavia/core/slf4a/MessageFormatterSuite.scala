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

import io.otavia.core.slf4a.helpers.MessageFormatter
import org.scalatest.funsuite.AnyFunSuite

class MessageFormatterSuite extends AnyFunSuite {

    test("One argument") {
        assert(MessageFormatter.format("print {} logger", 1) == "print 1 logger")
        assert(MessageFormatter.format("print {} logger", "hello") == "print hello logger")
        assert(MessageFormatter.format("print {} logger", true) == "print true logger")
        assert(MessageFormatter.format("print {} logger", false) == "print false logger")
        assert(MessageFormatter.format("print logger", false) == "print logger false")
    }

}
