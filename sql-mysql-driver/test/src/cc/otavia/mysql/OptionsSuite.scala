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

package cc.otavia.mysql

import cc.otavia.mysql.spi.MySQLDriverFactory
import org.scalatest.funsuite.AnyFunSuite

class OptionsSuite extends AnyFunSuite {

    test("mysql url parse: sample") {
        val url     = "jdbc:mysql://localhost:3306/test_demo?useUnicode=true&useSSL=false"
        val options = MySQLDriverFactory.parseOptions(url, Map.empty)

        assert(options.host == "localhost")
        assert(options.port == 3306)
        assert(options.database == "test_demo")
        assert(options.properties.get("useUnicode").contains("true"))
        assert(options.properties.get("useSSL").contains("false"))
    }

    test("mysql url parse: no port") {
        val url     = "jdbc:mysql://localhost/test_demo?useUnicode=true&useSSL=false"
        val options = MySQLDriverFactory.parseOptions(url, Map.empty)

        assert(options.host == "localhost")
        assert(options.port == 3306)
        assert(options.database == "test_demo")
        assert(options.properties.get("useUnicode").contains("true"))
        assert(options.properties.get("useSSL").contains("false"))
    }

}
