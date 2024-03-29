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

package cc.otavia.sql

trait RowDecoder[+R <: Row] {
    def decode(parser: RowParser): R
}

object RowDecoder {

    /** Derives a [[RowDecoder]] for database values for the specified type [[T]].
     *
     *  @tparam T
     *    a type that should be encoded and decoded by the derived serde
     *  @return
     *    an instance of the derived serde
     */
    inline def derived[T <: Row]: RowDecoder[T] = ${ RowMacro.derivedMacro[T] }

}
