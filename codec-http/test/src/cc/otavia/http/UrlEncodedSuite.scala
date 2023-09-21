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

package cc.otavia.http

import org.scalatest.funsuite.AnyFunSuite

import java.net.{URLDecoder, URLEncoder}

class UrlEncodedSuite extends AnyFunSuite {

    test("url encode") {
        println(URLEncoder.encode("中文", "utf-8"))
        println(URLEncoder.encode("中文", "gbk"))

        println(URLDecoder.decode("name=%E4%B8%A5%E9%94%9F&age=30", "utf-8"))
    }

}
