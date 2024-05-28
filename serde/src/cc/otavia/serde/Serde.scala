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

package cc.otavia.serde

import cc.otavia.buffer.Buffer

import scala.runtime.Nothing$

/** A type class to serialize/deserialize instance of type [[A]].
 *  @tparam A
 *    The type to serialize/deserialize.
 */
trait Serde[A] {

    /** Check that there is enough data in the buffer to deserialize.
     *  @param in
     *    The input [[Buffer]].
     *  @return
     *    `true` if has enough data, or else `false`.
     */
    def checkDeserializable(in: Buffer): Boolean = true

    /** Deserialize the bytes data in [[Buffer]] to instance of type [[A]]
     *  @param in
     *    The input [[Buffer]].
     *  @return
     *    Instance of type [[A]].
     */
    def deserialize(in: Buffer): A

    /** Deserialize the bytes data in [[Buffer]] to instance of type [[A]], but return erased instance of type [[Any]].
     *  @param in
     *    The input [[Buffer]].
     *  @return
     *    Instance of type [[Any]].
     */
    final def deserializeToAny(in: Buffer): Any = deserialize(in)

    /** Serialize instance of type [[A]] into [[Buffer]].
     *  @param value
     *    Instance of type [[A]].
     *  @param out
     *    Output [[Buffer]].
     */
    def serialize(value: A, out: Buffer): Unit

    /** Serialize instance of type [[A]] which is erased type to [[Any]] into Buffer.
     *  @param value
     *    Instance of type [[A]] which is erased type to [[Any]].
     *  @param out
     *    Output [[Buffer]].
     */
    final def serializeAny(value: Any, out: Buffer): Unit = serialize(value.asInstanceOf[A], out)

}
