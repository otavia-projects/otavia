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

import java.math.{BigInteger, BigDecimal as JBigDecimal}

trait SerdeMathTypeOps {
    this: Serde[?] =>

    // serialize ops

    protected def serializeBigInt(bigInt: BigInt, out: Buffer): this.type

    protected def serializeBigDecimal(bigDecimal: BigDecimal, out: Buffer): this.type

    protected def serializeBigInteger(bigInteger: BigInteger, out: Buffer): this.type

    protected def serializeJBigDecimal(bigDecimal: JBigDecimal, out: Buffer): this.type

    // deserialize ops

    protected def deserializeBigInt(in: Buffer): BigInt

    protected def deserializeBigDecimal(in: Buffer): BigInt

    protected def deserializeBigInteger(in: Buffer): BigInt

    protected def deserializeJBigDecimal(in: Buffer): BigInt

}
