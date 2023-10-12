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

import java.time.MonthDay

/** [[JsonSerde]] for [[MonthDay]]. */
object MonthDayJsonSerde extends JsonSerde[MonthDay] {

    override final def serialize(value: MonthDay, out: Buffer): Unit = this.serializeMonthDay(value, out)

    override final def deserialize(in: Buffer): MonthDay = this.deserializeMonthDay(in)

}
