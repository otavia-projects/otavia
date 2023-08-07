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

package cc.otavia.core.slf4a

import scala.annotation.targetName

/** Log level for actor. */
enum LogLevel extends Ordered[LogLevel] {

    case OFF   extends LogLevel
    case FATAL extends LogLevel
    case ERROR extends LogLevel
    case WARN  extends LogLevel
    case INFO  extends LogLevel
    case DEBUG extends LogLevel
    case TRACE extends LogLevel
    case ALL   extends LogLevel

    final override def compare(that: LogLevel): Int = ordinal - that.ordinal

}
