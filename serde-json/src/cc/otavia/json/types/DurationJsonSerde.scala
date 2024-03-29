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

package cc.otavia.json.types

import cc.otavia.buffer.Buffer
import cc.otavia.json.JsonSerde

import scala.concurrent.duration.Duration

/** [[JsonSerde]] for [[Duration]]. */
object DurationJsonSerde extends JsonSerde[Duration] {

    override final def serialize(value: Duration, out: Buffer): Unit = this.serializeDuration(value, out)

    override final def deserialize(in: Buffer): Duration = this.deserializeDuration(in)

}
