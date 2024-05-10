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

package cc.otavia.handler.ssl.protocol

/** Interface to support Application Protocol Negotiation. <p> Default implementations are provided for: <ul> <li><a
 *  href="https://technotes.googlecode.com/git/nextprotoneg.html">Next Protocol Negotiation</a></li> <li><a
 *  href="https://tools.ietf.org/html/rfc7301">Application-Layer Protocol Negotiation</a></li> </ul>
 */
trait ApplicationProtocolNegotiator {

    /** Get the collection of application protocols supported by this application (in preference order). */
    def protocols: List[String]

}
