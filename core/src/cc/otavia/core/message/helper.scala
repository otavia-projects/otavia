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

package cc.otavia.core.message

/** Some notices classes that may be used frequently */
object helper {

    sealed trait UnitReply private () extends Reply

    object UnitReply {

        private val default: UnitReply = new UnitReply {}
        def apply(): UnitReply         = default

    }

    final case class IntNotice(value: Int) extends Notice

    final case class StringNotice(value: String) extends Notice

    final case class ArrayNotice[@specialized T](value: Array[T]) extends Notice

    final case class SeqNotice[T](value: Seq[T]) extends Notice

}
