/*
 * Copyright 2022 Yan Kun <yan_kun_1992@foxmail.com>
 *
 * This file fork from netty.
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

package cc.otavia.handler.codec.base64

/** Enumeration of supported Base64 dialects.
 *
 *  The internal lookup tables in this class has been derived from <a
 *  href="http://iharder.sourceforge.net/current/java/base64/"> Robert Harder's Public Domain Base64
 *  Encoder/Decoder.</a>
 */
enum Base64Dialect(val alphabet: Array[Byte], val decodabet: Array[Byte], val breakLinesByDefault: Boolean) {

    /** Standard Base64 encoding as described in the Section 3 of <a
     *  href="http://www.faqs.org/rfcs/rfc3548.html">RFC3548</a>.
     */
    case STANDARD
        extends Base64Dialect(
          Array[Byte](
            'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U',
            'V', 'W', 'X', 'Y', 'Z', 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p',
            'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '+', '/'
          ),
          Array[Byte](-9, -9, -9, -9, -9, -9,                   //
            -9, -9, -9,                                         // Decimal  0 -  8
            -5, -5,                                             // Whitespace: Tab and Linefeed
            -9, -9,                                             // Decimal 11 - 12
            -5,                                                 // Whitespace: Carriage Return
            -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, // Decimal 14 - 26
            -9, -9, -9, -9, -9,                                 // Decimal 27 - 31
            -5,                                                 // Whitespace: Space
            -9, -9, -9, -9, -9, -9, -9, -9, -9, -9,             // Decimal 33 - 42
            62,                                                 // Plus sign at decimal 43
            -9, -9, -9,                                         // Decimal 44 - 46
            63,                                                 // Slash at decimal 47
            52, 53, 54, 55, 56, 57, 58, 59, 60, 61,             // Numbers zero through nine
            -9, -9, -9,                                         // Decimal 58 - 60
            -1,                                                 // Equals sign at decimal 61
            -9, -9, -9,                                         // Decimal 62 - 64
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13,       // Letters 'A' through 'N'
            14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25,     // Letters 'O' through 'Z'
            -9, -9, -9, -9, -9, -9,                             // Decimal 91 - 96
            26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, // Letters 'a' through 'm'
            39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, // Letters 'n' through 'z'
            -9, -9, -9, -9, -9                                  // Decimal 123 - 127)
            /* -9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,     // Decimal 128 - 140
              -9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,     // Decimal 141 - 153
              -9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,     // Decimal 154 - 166
              -9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,     // Decimal 167 - 179
              -9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,     // Decimal 180 - 192
              -9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,     // Decimal 193 - 205
              -9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,     // Decimal 206 - 218
              -9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,     // Decimal 219 - 231
              -9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,     // Decimal 232 - 244
              -9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9            // Decimal 245 - 255 */
          ),
          true
        )

    /** Base64-like encoding that is URL-safe as described in the Section 4 of <a
     *  href="http://www.faqs.org/rfcs/rfc3548.html">RFC3548</a>. It is important to note that data encoded this way is
     *  <em>not</em> officially valid Base64, or at the very least should not be called Base64 without also specifying
     *  that is was encoded using the URL-safe dialect.
     */
    case URL_SAFE
        extends Base64Dialect(
          Array[Byte](
            'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U',
            'V', 'W', 'X', 'Y', 'Z', 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p',
            'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '-', '_'
          ),
          Array[Byte](-9, -9, -9, -9, -9, -9,                   //
            -9, -9, -9,                                         // Decimal  0 -  8
            -5, -5,                                             // Whitespace: Tab and Linefeed
            -9, -9,                                             // Decimal 11 - 12
            -5,                                                 // Whitespace: Carriage Return
            -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, // Decimal 14 - 26
            -9, -9, -9, -9, -9,                                 // Decimal 27 - 31
            -5,                                                 // Whitespace: Space
            -9, -9, -9, -9, -9, -9, -9, -9, -9, -9,             // Decimal 33 - 42
            -9,                                                 // Plus sign at decimal 43
            -9,                                                 // Decimal 44
            62,                                                 // Minus sign at decimal 45
            -9,                                                 // Decimal 46
            -9,                                                 // Slash at decimal 47
            52, 53, 54, 55, 56, 57, 58, 59, 60, 61,             // Numbers zero through nine
            -9, -9, -9,                                         // Decimal 58 - 60
            -1,                                                 // Equals sign at decimal 61
            -9, -9, -9,                                         // Decimal 62 - 64
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13,       // Letters 'A' through 'N'
            14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25,     // Letters 'O' through 'Z'
            -9, -9, -9, -9,                                     // Decimal 91 - 94
            63,                                                 // Underscore at decimal 95
            -9,                                                 // Decimal 96
            26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, // Letters 'a' through 'm'
            39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, // Letters 'n' through 'z'
            -9, -9, -9, -9, -9                                  // Decimal 123 - 127
            /* -9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,     // Decimal 128 - 140
              -9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,     // Decimal 141 - 153
              -9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,     // Decimal 154 - 166
              -9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,     // Decimal 167 - 179
              -9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,     // Decimal 180 - 192
              -9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,     // Decimal 193 - 205
              -9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,     // Decimal 206 - 218
              -9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,     // Decimal 219 - 231
              -9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,     // Decimal 232 - 244
              -9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9            // Decimal 245 - 255 */
          ),
          false
        )

    /** Special "ordered" dialect of Base64 described in <a href="http://www.faqs.org/qa/rfcc-1940.html">RFC1940</a>. */
    case ORDERED
        extends Base64Dialect(
          Array[Byte](
            '-', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J',
            'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z', '_', 'a', 'b', 'c', 'd',
            'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z'
          ),
          Array[Byte](-9, -9, -9, -9, -9, -9,                   //
            -9, -9, -9,                                         // Decimal  0 -  8
            -5, -5,                                             // Whitespace: Tab and Linefeed
            -9, -9,                                             // Decimal 11 - 12
            -5,                                                 // Whitespace: Carriage Return
            -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, // Decimal 14 - 26
            -9, -9, -9, -9, -9,                                 // Decimal 27 - 31
            -5,                                                 // Whitespace: Space
            -9, -9, -9, -9, -9, -9, -9, -9, -9, -9,             // Decimal 33 - 42
            -9,                                                 // Plus sign at decimal 43
            -9,                                                 // Decimal 44
            0,                                                  // Minus sign at decimal 45
            -9,                                                 // Decimal 46
            -9,                                                 // Slash at decimal 47
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10,                      // Numbers zero through nine
            -9, -9, -9,                                         // Decimal 58 - 60
            -1,                                                 // Equals sign at decimal 61
            -9, -9, -9,                                         // Decimal 62 - 64
            11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, // Letters 'A' through 'M'
            24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, // Letters 'N' through 'Z'
            -9, -9, -9, -9,                                     // Decimal 91 - 94
            37,                                                 // Underscore at decimal 95
            -9,                                                 // Decimal 96
            38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, // Letters 'a' through 'm'
            51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63, // Letters 'n' through 'z'
            -9, -9, -9, -9, -9                                  // Decimal 123 - 127)
            /* -9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,     // Decimal 128 - 140
              -9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,     // Decimal 141 - 153
              -9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,     // Decimal 154 - 166
              -9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,     // Decimal 167 - 179
              -9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,     // Decimal 180 - 192
              -9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,     // Decimal 193 - 205
              -9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,     // Decimal 206 - 218
              -9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,     // Decimal 219 - 231
              -9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,     // Decimal 232 - 244
              -9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9            // Decimal 245 - 255 */
          ),
          true
        )

}
