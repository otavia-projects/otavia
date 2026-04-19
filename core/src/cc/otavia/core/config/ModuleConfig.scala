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

package cc.otavia.core.config

/** SPI trait for module-specific configuration.
 *
 *  Modules (handler, codec-*, sql-*, etc.) can define their own configuration by implementing this trait and
 *  registering the config instance with [[OtaviaConfigBuilder.withModuleConfig]]. The config system stores module
 *  configs by [[Class]] key for type-safe retrieval.
 *
 *  Example:
 *  {{{
 *  // In handler module:
 *  case class SslConfig(cacheBufferSize: Int = 20480) extends ModuleConfig:
 *    override def propertyPrefix: String = "cc.otavia.handler.ssl"
 *
 *  // Registration:
 *  val config = OtaviaConfigBuilder()
 *    .withModuleConfig(SslConfig(cacheBufferSize = 32768))
 *    .build()
 *
 *  // Retrieval:
 *  val sslCfg = system.config.getModuleConfig(classOf[SslConfig])
 *  }}}
 */
trait ModuleConfig {

    /** System property prefix for all properties in this module config.
 *
 *  Used for backward compatibility when reading system properties as fallback.
     */
    def propertyPrefix: String

}
