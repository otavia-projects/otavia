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

import io.otavia.core.slf4a.helpers.Util
import org.scalatest.funsuite.AnyFunSuite

class UtilSuite extends AnyFunSuite {

    test("report error") {
        Util.report("A SLF4A service provider failed to instantiate:\n")
        Console.err.printf("format string {} is ok", "value")
        val format = "format string {} is ok"



        assert(true)
    }

}
