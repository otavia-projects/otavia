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

package cc.otavia.sql.datatype

import java.net.InetAddress
import scala.language.unsafeNulls

/** A PosgreSQL <a
 *  href="https://www.postgresql.org/docs/9.1/datatype-net-types.html#:~:text=1.-,inet,(the%20%22netmask%22).">inet
 *  network address</a>.
 */
class Inet() {

    private var address: InetAddress = _
    private var netmask: Int         = -1

    /** @return the inet address */
    def getAddress: InetAddress = address

    /** Set the inet address
     *
     *  @param address
     *  @return
     *    a reference to this, so the API can be used fluently
     */
    def setAddress(address: InetAddress): Inet = {
        this.address = address
        this
    }

    /** @return the optional netmask */
    def getNetmask: Int = netmask

    /** Set a netmask.
     *
     *  @param netmask
     *    the netmask
     *  @return
     *    a reference to this, so the API can be used fluently
     */
    def setNetmask(netmask: Int): Inet = {
        if (netmask < 0 || netmask > 255)
            throw new IllegalArgumentException(s"Invalid netmask: $netmask")
        this.netmask = netmask
        this
    }

}
