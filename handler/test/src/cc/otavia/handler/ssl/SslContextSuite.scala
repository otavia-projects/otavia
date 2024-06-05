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

package cc.otavia.handler.ssl

import org.scalatest.funsuite.AnyFunSuiteLike

import scala.language.unsafeNulls

class SslContextSuite extends AnyFunSuiteLike {

    test("parse github certificate") {
        val certInputStream = this.getClass.getResourceAsStream("/github-com.pem")
        val cert            = SslContext.toX509Certificates(certInputStream).head

        println(cert)
        println("-----------------------------")
    }

    test("parse github certificates chain") {
        val certInputStream = this.getClass.getResourceAsStream("/github-com-chain.pem")
        val certs           = SslContext.toX509Certificates(certInputStream)

        for (elem <- certs) {
            println(elem)
            println("-----------------------------")
        }
    }

}
