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

package cc.otavia.util

object ASCII {

    val NUL: Byte = 0  // null
    val SOH: Byte = 1  // start of headline
    val STX: Byte = 2  // start of text
    val ETX: Byte = 3  // end of text
    val EOT: Byte = 4  // end of transmission
    val ENQ: Byte = 5  // enquiry
    val ACK: Byte = 6  // acknowledge
    val BEL: Byte = 7  // bell
    val BS: Byte  = 8  // backspace
    val HT: Byte  = 9  // horizontal tab
    val LF: Byte  = 10 // LF (NL line feed, new line)
    val VT: Byte  = 11 // VT (vertical tab)
    val FF: Byte  = 12 // FF (NP form feed, new page)
    val CR: Byte  = 13 // CR (carriage return)
    val SO: Byte  = 14 // shift out
    val SI: Byte  = 15 // shift in
    val DLE: Byte = 16 // data link escape
    val DC1: Byte = 17 // device control 1
    val DC2: Byte = 18 // device control 2
    val DC3: Byte = 19 // device control 3
    val DC4: Byte = 20 // device control 4
    val NAK: Byte = 21 // negative acknowledge
    val SYN: Byte = 22 // synchronous idle
    val ETB: Byte = 23 // end of trans. block
    val CAN: Byte = 24 // cancel
    val EM: Byte  = 25 // end of medium
    val SUB: Byte = 26 // substitute
    val ESC: Byte = 27 // escape
    val FS: Byte  = 28 // file separator
    val GS: Byte  = 29 // group separator
    val RS: Byte  = 30 // record separator
    val US: Byte  = 31 // unit separator

    val SPACE: Byte        = 32 // ' '
    val BANG: Byte         = 33 // '!'
    val DOUBLE_QUOTE: Byte = 34 // '"'
    val NUMBER_SIGN: Byte  = 35 // '#'
    val DOLLAR: Byte       = 36 // '$'
    val PERCENT: Byte      = 37 // '%'
    val AMPERSAND: Byte    = 38 // '&'
    val SINGLE_QUOTE: Byte = 39 // '''
    val PAREN_LEFT: Byte   = 40 // '('
    val PAREN_RIGHT: Byte  = 41 // ')'
    val ASTERISK: Byte     = 42 // '*'
    val PLUS: Byte         = 43 // '+'
    val COMMA: Byte        = 44 // ','
    val MINUS_SIGN: Byte   = 45 // '-'
    val DOT: Byte          = 46 // '.'
    val SLASH: Byte        = 47 // '/'

    val ZERO: Byte  = 48 // '0'
    val ONE: Byte   = 49 // '1'
    val TWO: Byte   = 50 // '2'
    val THREE: Byte = 51 // '3'
    val FOUR: Byte  = 52 // '4'
    val FIVE: Byte  = 53 // '5'
    val SIX: Byte   = 54 // '6'
    val SEVEN: Byte = 55 // '7'
    val EIGHT: Byte = 56 // '8'
    val NINE: Byte  = 57 // '9'

    val COLON: Byte     = 58 // ':'
    val SEMICOLON: Byte = 59 // ';'
    val LT: Byte        = 60 // '<'
    val EQUAL: Byte     = 61 // '='
    val GT: Byte        = 62 // '>'
    val QUESTION: Byte  = 63 // '?'
    val AT: Byte        = 64 // '@'

    val A: Byte = 65 // 'A'
    val B: Byte = 66 // 'B'
    val C: Byte = 67 // 'C'
    val D: Byte = 68 // 'D'
    val E: Byte = 69 // 'E'
    val F: Byte = 70 // 'F'
    val G: Byte = 71 // 'G'
    val H: Byte = 72 // 'H'
    val I: Byte = 73 // 'I'
    val J: Byte = 74 // 'J'
    val K: Byte = 75 // 'K'
    val L: Byte = 76 // 'L'
    val M: Byte = 77 // 'M'
    val N: Byte = 78 // 'N'
    val O: Byte = 79 // 'O'
    val P: Byte = 80 // 'P'
    val Q: Byte = 81 // 'Q'
    val R: Byte = 82 // 'R'
    val S: Byte = 83 // 'S'
    val T: Byte = 84 // 'T'
    val U: Byte = 85 // 'U'
    val V: Byte = 86 // 'V'
    val W: Byte = 87 // 'W'
    val X: Byte = 88 // 'X'
    val Y: Byte = 89 // 'Y'
    val Z: Byte = 90 // 'Z'

    val BRACKET_LEFT: Byte  = 91 // '['
    val BACKSLASH: Byte     = 92 // '\'
    val BRACKET_RIGHT: Byte = 93 // ']'
    val CARET: Byte         = 94 // '^'
    val UNDERSCORE: Byte    = 95 // '_'
    val BACK_QUOTE: Byte    = 96 // '`'

    val a: Byte = 97  // 'a'
    val b: Byte = 98  // 'b'
    val c: Byte = 99  // 'c'
    val d: Byte = 100 // 'd'
    val e: Byte = 101 // 'e'
    val f: Byte = 102 // 'f'
    val g: Byte = 103 // 'g'
    val h: Byte = 104 // 'h'
    val i: Byte = 105 // 'i'
    val j: Byte = 106 // 'j'
    val k: Byte = 107 // 'k'
    val l: Byte = 108 // 'l'
    val m: Byte = 109 // 'm'
    val n: Byte = 110 // 'n'
    val o: Byte = 111 // 'o'
    val p: Byte = 112 // 'p'
    val q: Byte = 113 // 'q'
    val r: Byte = 114 // 'r'
    val s: Byte = 115 // 's'
    val t: Byte = 116 // 't'
    val u: Byte = 117 // 'u'
    val v: Byte = 118 // 'v'
    val w: Byte = 119 // 'w'
    val x: Byte = 120 // 'x'
    val y: Byte = 121 // 'y'
    val z: Byte = 122 // 'z'

    val BRACE_LEFT: Byte  = 123 // '{'
    val BAR: Byte         = 124 // '|'
    val BRACE_RIGHT: Byte = 125 // '}'
    val TILDE: Byte       = 126 // '~'

}
