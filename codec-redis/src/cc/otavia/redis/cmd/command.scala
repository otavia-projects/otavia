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

package cc.otavia.redis.cmd

import cc.otavia.core.message.{Ask, Notice, Reply}
import cc.otavia.redis.*

import java.nio.charset.StandardCharsets
import scala.language.unsafeNulls

sealed trait Command[R <: CommandResponse] extends Ask[R]

sealed trait CommandResponse extends Reply

object Command {

    def set(key: Array[Byte], value: Array[Byte]): Set = Set(key, value)
    def set(key: String, value: String): Set =
        Set(key.getBytes(StandardCharsets.UTF_8), value.getBytes(StandardCharsets.UTF_8))
    def set(key: String, value: Array[Byte]): Set = Set(key.getBytes(StandardCharsets.UTF_8), value)
    def set(key: Array[Byte], value: String): Set = Set(key, value.getBytes(StandardCharsets.UTF_8))

    def del(key: String): Del      = Del(key.getBytes(StandardCharsets.UTF_8))
    def del(key: Array[Byte]): Del = Del(key)
    // def del(keys: Seq[String]): Del = Del(keys.map(_.getBytes(StandardCharsets.UTF_8)))
    def del(keys: Seq[Array[Byte]]) = Del(keys)

    def info(): Info = Info()

}

case class OK() extends CommandResponse

case class Success(value: Long) extends CommandResponse

case class BulkReply(value: Array[Byte]) extends CommandResponse

case class Select(db: Int) extends Command[OK]

case class Info() extends Command[BulkReply]

case class Auth(password: String) extends Command[OK]

case class Set(key: Array[Byte], value: Array[Byte]) extends Command[OK]

case class Get(key: String | Array[Byte]) extends Command[BulkReply]

case class Del(key: Array[Byte] | Seq[Array[Byte]]) extends Command[Success]

class HGetAll(key: Array[Byte]) extends Command[RedisMap[?, ?]]

trait RedisMap[K, V] extends CommandResponse

case class HSet(key: Array[Byte], values: (String, String) | RedisMap[?, ?]) extends Command[Success]
