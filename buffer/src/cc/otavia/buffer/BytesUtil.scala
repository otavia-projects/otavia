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

package cc.otavia.buffer

object BytesUtil {

    final def bytes2Int(b0: Byte, b1: Byte): Int = b0.toInt << 8 | b1

    final def bytes3Int(b0: Byte, b1: Byte, b2: Byte): Int = b0.toInt << 16 | b1.toInt << 8 | b2

    final def bytes4Int(b0: Byte, b1: Byte, b2: Byte, b3: Byte): Int =
        b0.toInt << 24 | b1.toInt << 16 | b2.toInt << 8 | b3

    final def bytes5Long(b0: Byte, b1: Byte, b2: Byte, b3: Byte, b4: Byte): Long =
        b0.toLong << 32 | b1.toLong << 24 | b2.toLong << 16 | b3.toLong << 8 | b4

    final def bytes6Long(b0: Byte, b1: Byte, b2: Byte, b3: Byte, b4: Byte, b5: Byte): Long =
        b0.toLong << 40 | b1.toLong << 32 | b2.toLong << 24 | b3.toLong << 16 | b4.toLong << 8 | b5

    final def bytes7Long(b0: Byte, b1: Byte, b2: Byte, b3: Byte, b4: Byte, b5: Byte, b6: Byte): Long =
        b0.toLong << 48 | b1.toLong << 40 | b2.toLong << 32 | b3.toLong << 24 | b4.toLong << 16 | b5.toLong << 8 | b6

    final def bytes8Long(b0: Byte, b1: Byte, b2: Byte, b3: Byte, b4: Byte, b5: Byte, b6: Byte, b7: Byte): Long =
        b0.toLong << 56 | b1.toLong << 48 | b2.toLong << 40 | b3.toLong << 32 | b4.toLong << 24 | b5.toLong << 16 | b6.toLong << 8 | b7

}
