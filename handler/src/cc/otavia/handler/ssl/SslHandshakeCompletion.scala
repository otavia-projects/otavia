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

/** Event that is fired once the SSL handshake is complete, which may be because it was successful or there was an
 *  error.
 */
class SslHandshakeCompletion(cause: Option[Throwable] = None) extends SslCompletionEvent(cause)

object SslHandshakeCompletion {
    val SUCCESS = new SslHandshakeCompletion()
}
