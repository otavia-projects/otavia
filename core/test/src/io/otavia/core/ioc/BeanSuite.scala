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

package io.otavia.core.ioc

import io.otavia.core.slf4a.appender.ConsoleAppender
import org.scalatest.funsuite.AnyFunSuite

import scala.collection.mutable
import scala.language.unsafeNulls

class BeanSuite extends AnyFunSuite {

    test("Get all super class for ConsoleAppender") {
        val bean = new Bean(classOf[ConsoleAppender], null)
        val sps  = bean.superClasses()

        assert(sps == mutable.Set("io.otavia.core.slf4a.appender.ConsoleAppender", "io.otavia.core.slf4a.Appender"))
    }

}
