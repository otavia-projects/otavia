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

package cc.otavia.handler.ssl.config

import cc.otavia.core.config.ModuleConfig

/** SSL/TLS configuration for [[cc.otavia.handler.ssl.SslHandler]].
 *
 *  @param cacheBufferSize
 *    Size in bytes of the ByteBuffer cache used for SSL wrap/unwrap operations. This buffer is allocated per
 *    ActorThread and reused across SSL operations. Larger values reduce the chance of BUFFER_OVERFLOW errors
 *    but consume more memory. Default is 20480 (20KB).
 */
case class SslConfig(
    cacheBufferSize: Int = 20480
) extends ModuleConfig {

    override def propertyPrefix: String = "cc.otavia.handler.ssl"

}
