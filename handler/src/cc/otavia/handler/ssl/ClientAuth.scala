/*
 * Copyright 2022 Yan Kun <yan_kun_1992@foxmail.com>
 *
 * This file fork from netty.
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

import javax.net.ssl.SSLEngine

/** Indicates the state of the [[SSLEngine]] with respect to client authentication. This configuration item
 *  really only applies when building the server-side [[SslContext]].
 */
enum ClientAuth {

    /** Indicates that the [[SSLEngine]] will not request client authentication. */
    case NONE

    /** Indicates that the [[SSLEngine]] will request client authentication. */
    case OPTIONAL

    /** Indicates that the [[SSLEngine]] will *require* client authentication. */
    case REQUIRE

}
