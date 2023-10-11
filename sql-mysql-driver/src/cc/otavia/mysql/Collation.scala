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

package cc.otavia.mysql

/** MySQL collation which is a set of rules for comparing characters in a character set.
 *
 *  @param mysqlCharsetName
 *    the binding MySQL charset name for this collation.
 *  @param mappedJavaCharsetName
 *    the mapped Java charset name which is mapped from the collation.
 *  @param collationId
 *    the collation Id of this collation
 */
enum Collation(val mysqlCharsetName: String, val mappedJavaCharsetName: String, val collationId: Int) {

    case big5_chinese_ci          extends Collation("big5", "Big5", 1)
    case latin2_czech_cs          extends Collation("latin2", "ISO8859_2", 2)
    case dec8_swedish_ci          extends Collation("dec8", "Cp1252", 3)
    case cp850_general_ci         extends Collation("cp850", "Cp850", 4)
    case latin1_german1_ci        extends Collation("latin1", "Cp1252", 5)
    case hp8_english_ci           extends Collation("hp8", "Cp1252", 6)
    case koi8r_general_ci         extends Collation("koi8r", "KOI8_R", 7)
    case latin1_swedish_ci        extends Collation("latin1", "Cp1252", 8)
    case latin2_general_ci        extends Collation("latin2", "ISO8859_2", 9)
    case swe7_swedish_ci          extends Collation("swe7", "Cp1252", 10)
    case ascii_general_ci         extends Collation("ascii", "US-ASCII", 11)
    case ujis_japanese_ci         extends Collation("ujis", "EUC_JP", 12)
    case sjis_japanese_ci         extends Collation("sjis", "SJIS", 13)
    case cp1251_bulgarian_ci      extends Collation("cp1251", "Cp1251", 14)
    case latin1_danish_ci         extends Collation("latin1", "Cp1252", 15)
    case hebrew_general_ci        extends Collation("hebrew", "ISO8859_8", 16)
    case tis620_thai_ci           extends Collation("tis620", "TIS620", 18)
    case euckr_korean_ci          extends Collation("euckr", "EUC_KR", 19)
    case latin7_estonian_cs       extends Collation("latin7", "ISO-8859-13", 20)
    case latin2_hungarian_ci      extends Collation("latin2", "ISO8859_2", 21)
    case koi8u_general_ci         extends Collation("koi8u", "KOI8_R", 22)
    case cp1251_ukrainian_ci      extends Collation("cp1251", "Cp1251", 23)
    case gb2312_chinese_ci        extends Collation("gb2312", "EUC_CN", 24)
    case greek_general_ci         extends Collation("greek", "ISO8859_7", 25)
    case cp1250_general_ci        extends Collation("cp1250", "Cp1250", 26)
    case latin2_croatian_ci       extends Collation("latin2", "ISO8859_2", 27)
    case gbk_chinese_ci           extends Collation("gbk", "GBK", 28)
    case cp1257_lithuanian_ci     extends Collation("cp1257", "Cp1257", 29)
    case latin5_turkish_ci        extends Collation("latin5", "ISO8859_9", 30)
    case latin1_german2_ci        extends Collation("latin1", "Cp1252", 31)
    case armscii8_general_ci      extends Collation("armscii8", "Cp1252", 32)
    case utf8_general_ci          extends Collation("utf8", "UTF-8", 33)
    case cp1250_czech_cs          extends Collation("cp1250", "Cp1250", 34)
    case ucs2_general_ci          extends Collation("ucs2", "UnicodeBig", 35)
    case cp866_general_ci         extends Collation("cp866", "Cp866", 36)
    case keybcs2_general_ci       extends Collation("keybcs2", "Cp852", 37)
    case macce_general_ci         extends Collation("macce", "MacCentralEurope", 38)
    case macroman_general_ci      extends Collation("macroman", "MacRoman", 39)
    case cp852_general_ci         extends Collation("cp852", "Cp852", 40)
    case latin7_general_ci        extends Collation("latin7", "ISO-8859-13", 41)
    case latin7_general_cs        extends Collation("latin7", "ISO-8859-13", 42)
    case macce_bin                extends Collation("macce", "MacCentralEurope", 43)
    case cp1250_croatian_ci       extends Collation("cp1250", "Cp1250", 44)
    case utf8mb4_general_ci       extends Collation("utf8mb4", "UTF-8", 45)
    case utf8mb4_bin              extends Collation("utf8mb4", "UTF-8", 46)
    case latin1_bin               extends Collation("latin1", "Cp1252", 47)
    case latin1_general_ci        extends Collation("latin1", "Cp1252", 48)
    case latin1_general_cs        extends Collation("latin1", "Cp1252", 49)
    case cp1251_bin               extends Collation("cp1251", "Cp1251", 50)
    case cp1251_general_ci        extends Collation("cp1251", "Cp1251", 51)
    case cp1251_general_cs        extends Collation("cp1251", "Cp1251", 52)
    case macroman_bin             extends Collation("macroman", "MacRoman", 53)
    case utf16_general_ci         extends Collation("utf16", "UTF-16", 54)
    case utf16_bin                extends Collation("utf16", "UTF-16", 55)
    case utf16le_general_ci       extends Collation("utf16le", "UTF-16LE", 56)
    case cp1256_general_ci        extends Collation("cp1256", "Cp1256", 57)
    case cp1257_bin               extends Collation("cp1257", "Cp1257", 58)
    case cp1257_general_ci        extends Collation("cp1257", "Cp1257", 59)
    case utf32_general_ci         extends Collation("utf32", "UTF-32", 60)
    case utf32_bin                extends Collation("utf32", "UTF-32", 61)
    case utf16le_bin              extends Collation("utf16le", "UTF-16LE", 62)
    case binary                   extends Collation("binary", "ISO8859_1", 63)
    case armscii8_bin             extends Collation("armscii8", "Cp1252", 64)
    case ascii_bin                extends Collation("ascii", "US-ASCII", 65)
    case cp1250_bin               extends Collation("cp1250", "Cp1250", 66)
    case cp1256_bin               extends Collation("cp1256", "Cp1256", 67)
    case cp866_bin                extends Collation("cp866", "Cp866", 68)
    case dec8_bin                 extends Collation("dec8", "Cp1252", 69)
    case greek_bin                extends Collation("greek", "ISO8859_7", 70)
    case hebrew_bin               extends Collation("hebrew", "ISO8859_8", 71)
    case hp8_bin                  extends Collation("hp8", "Cp1252", 72)
    case keybcs2_bin              extends Collation("keybcs2", "Cp852", 73)
    case koi8r_bin                extends Collation("koi8r", "KOI8_R", 74)
    case koi8u_bin                extends Collation("koi8u", "KOI8_R", 75)
    case latin2_bin               extends Collation("latin2", "ISO8859_2", 77)
    case latin5_bin               extends Collation("latin5", "ISO8859_9", 78)
    case latin7_bin               extends Collation("latin7", "ISO-8859-13", 79)
    case cp850_bin                extends Collation("cp850", "Cp850", 80)
    case cp852_bin                extends Collation("cp852", "Cp852", 81)
    case swe7_bin                 extends Collation("swe7", "Cp1252", 82)
    case utf8_bin                 extends Collation("utf8", "UTF-8", 83)
    case big5_bin                 extends Collation("big5", "Big5", 84)
    case euckr_bin                extends Collation("euckr", "EUC_KR", 85)
    case gb2312_bin               extends Collation("gb2312", "EUC_CN", 86)
    case gbk_bin                  extends Collation("gbk", "GBK", 87)
    case sjis_bin                 extends Collation("sjis", "SJIS", 88)
    case tis620_bin               extends Collation("tis620", "TIS620", 89)
    case ucs2_bin                 extends Collation("ucs2", "UnicodeBig", 90)
    case ujis_bin                 extends Collation("ujis", "EUC_JP", 91)
    case geostd8_general_ci       extends Collation("geostd8", "Cp1252", 92)
    case geostd8_bin              extends Collation("geostd8", "Cp1252", 93)
    case latin1_spanish_ci        extends Collation("latin1", "Cp1252", 94)
    case cp932_japanese_ci        extends Collation("cp932", "Cp932", 95)
    case cp932_bin                extends Collation("cp932", "Cp932", 96)
    case eucjpms_japanese_ci      extends Collation("eucjpms", "EUC_JP_Solaris", 97)
    case eucjpms_bin              extends Collation("eucjpms", "EUC_JP_Solaris", 98)
    case cp1250_polish_ci         extends Collation("cp1250", "Cp1250", 99)
    case utf16_unicode_ci         extends Collation("utf16", "UTF-16", 101)
    case utf16_icelandic_ci       extends Collation("utf16", "UTF-16", 102)
    case utf16_latvian_ci         extends Collation("utf16", "UTF-16", 103)
    case utf16_romanian_ci        extends Collation("utf16", "UTF-16", 104)
    case utf16_slovenian_ci       extends Collation("utf16", "UTF-16", 105)
    case utf16_polish_ci          extends Collation("utf16", "UTF-16", 106)
    case utf16_estonian_ci        extends Collation("utf16", "UTF-16", 107)
    case utf16_spanish_ci         extends Collation("utf16", "UTF-16", 108)
    case utf16_swedish_ci         extends Collation("utf16", "UTF-16", 109)
    case utf16_turkish_ci         extends Collation("utf16", "UTF-16", 110)
    case utf16_czech_ci           extends Collation("utf16", "UTF-16", 111)
    case utf16_danish_ci          extends Collation("utf16", "UTF-16", 112)
    case utf16_lithuanian_ci      extends Collation("utf16", "UTF-16", 113)
    case utf16_slovak_ci          extends Collation("utf16", "UTF-16", 114)
    case utf16_spanish2_ci        extends Collation("utf16", "UTF-16", 115)
    case utf16_roman_ci           extends Collation("utf16", "UTF-16", 116)
    case utf16_persian_ci         extends Collation("utf16", "UTF-16", 117)
    case utf16_esperanto_ci       extends Collation("utf16", "UTF-16", 118)
    case utf16_hungarian_ci       extends Collation("utf16", "UTF-16", 119)
    case utf16_sinhala_ci         extends Collation("utf16", "UTF-16", 120)
    case utf16_german2_ci         extends Collation("utf16", "UTF-16", 121)
    case utf16_croatian_ci        extends Collation("utf16", "UTF-16", 122)
    case utf16_unicode_520_ci     extends Collation("utf16", "UTF-16", 123)
    case utf16_vietnamese_ci      extends Collation("utf16", "UTF-16", 124)
    case ucs2_unicode_ci          extends Collation("ucs2", "UnicodeBig", 128)
    case ucs2_icelandic_ci        extends Collation("ucs2", "UnicodeBig", 129)
    case ucs2_latvian_ci          extends Collation("ucs2", "UnicodeBig", 130)
    case ucs2_romanian_ci         extends Collation("ucs2", "UnicodeBig", 131)
    case ucs2_slovenian_ci        extends Collation("ucs2", "UnicodeBig", 132)
    case ucs2_polish_ci           extends Collation("ucs2", "UnicodeBig", 133)
    case ucs2_estonian_ci         extends Collation("ucs2", "UnicodeBig", 134)
    case ucs2_spanish_ci          extends Collation("ucs2", "UnicodeBig", 135)
    case ucs2_swedish_ci          extends Collation("ucs2", "UnicodeBig", 136)
    case ucs2_turkish_ci          extends Collation("ucs2", "UnicodeBig", 137)
    case ucs2_czech_ci            extends Collation("ucs2", "UnicodeBig", 138)
    case ucs2_danish_ci           extends Collation("ucs2", "UnicodeBig", 139)
    case ucs2_lithuanian_ci       extends Collation("ucs2", "UnicodeBig", 140)
    case ucs2_slovak_ci           extends Collation("ucs2", "UnicodeBig", 141)
    case ucs2_spanish2_ci         extends Collation("ucs2", "UnicodeBig", 142)
    case ucs2_roman_ci            extends Collation("ucs2", "UnicodeBig", 143)
    case ucs2_persian_ci          extends Collation("ucs2", "UnicodeBig", 144)
    case ucs2_esperanto_ci        extends Collation("ucs2", "UnicodeBig", 145)
    case ucs2_hungarian_ci        extends Collation("ucs2", "UnicodeBig", 146)
    case ucs2_sinhala_ci          extends Collation("ucs2", "UnicodeBig", 147)
    case ucs2_german2_ci          extends Collation("ucs2", "UnicodeBig", 148)
    case ucs2_croatian_ci         extends Collation("ucs2", "UnicodeBig", 149)
    case ucs2_unicode_520_ci      extends Collation("ucs2", "UnicodeBig", 150)
    case ucs2_vietnamese_ci       extends Collation("ucs2", "UnicodeBig", 151)
    case ucs2_general_mysql500_ci extends Collation("ucs2", "UnicodeBig", 159)
    case utf32_unicode_ci         extends Collation("utf32", "UTF-32", 160)
    case utf32_icelandic_ci       extends Collation("utf32", "UTF-32", 161)
    case utf32_latvian_ci         extends Collation("utf32", "UTF-32", 162)
    case utf32_romanian_ci        extends Collation("utf32", "UTF-32", 163)
    case utf32_slovenian_ci       extends Collation("utf32", "UTF-32", 164)
    case utf32_polish_ci          extends Collation("utf32", "UTF-32", 165)
    case utf32_estonian_ci        extends Collation("utf32", "UTF-32", 166)
    case utf32_spanish_ci         extends Collation("utf32", "UTF-32", 167)
    case utf32_swedish_ci         extends Collation("utf32", "UTF-32", 168)
    case utf32_turkish_ci         extends Collation("utf32", "UTF-32", 169)
    case utf32_czech_ci           extends Collation("utf32", "UTF-32", 170)
    case utf32_danish_ci          extends Collation("utf32", "UTF-32", 171)
    case utf32_lithuanian_ci      extends Collation("utf32", "UTF-32", 172)
    case utf32_slovak_ci          extends Collation("utf32", "UTF-32", 173)
    case utf32_spanish2_ci        extends Collation("utf32", "UTF-32", 174)
    case utf32_roman_ci           extends Collation("utf32", "UTF-32", 175)
    case utf32_persian_ci         extends Collation("utf32", "UTF-32", 176)
    case utf32_esperanto_ci       extends Collation("utf32", "UTF-32", 177)
    case utf32_hungarian_ci       extends Collation("utf32", "UTF-32", 178)
    case utf32_sinhala_ci         extends Collation("utf32", "UTF-32", 179)
    case utf32_german2_ci         extends Collation("utf32", "UTF-32", 180)
    case utf32_croatian_ci        extends Collation("utf32", "UTF-32", 181)
    case utf32_unicode_520_ci     extends Collation("utf32", "UTF-32", 182)
    case utf32_vietnamese_ci      extends Collation("utf32", "UTF-32", 183)
    case utf8_unicode_ci          extends Collation("utf8", "UTF-8", 192)
    case utf8_icelandic_ci        extends Collation("utf8", "UTF-8", 193)
    case utf8_latvian_ci          extends Collation("utf8", "UTF-8", 194)
    case utf8_romanian_ci         extends Collation("utf8", "UTF-8", 195)
    case utf8_slovenian_ci        extends Collation("utf8", "UTF-8", 196)
    case utf8_polish_ci           extends Collation("utf8", "UTF-8", 197)
    case utf8_estonian_ci         extends Collation("utf8", "UTF-8", 198)
    case utf8_spanish_ci          extends Collation("utf8", "UTF-8", 199)
    case utf8_swedish_ci          extends Collation("utf8", "UTF-8", 200)
    case utf8_turkish_ci          extends Collation("utf8", "UTF-8", 201)
    case utf8_czech_ci            extends Collation("utf8", "UTF-8", 202)
    case utf8_danish_ci           extends Collation("utf8", "UTF-8", 203)
    case utf8_lithuanian_ci       extends Collation("utf8", "UTF-8", 204)
    case utf8_slovak_ci           extends Collation("utf8", "UTF-8", 205)
    case utf8_spanish2_ci         extends Collation("utf8", "UTF-8", 206)
    case utf8_roman_ci            extends Collation("utf8", "UTF-8", 207)
    case utf8_persian_ci          extends Collation("utf8", "UTF-8", 208)
    case utf8_esperanto_ci        extends Collation("utf8", "UTF-8", 209)
    case utf8_hungarian_ci        extends Collation("utf8", "UTF-8", 210)
    case utf8_sinhala_ci          extends Collation("utf8", "UTF-8", 211)
    case utf8_german2_ci          extends Collation("utf8", "UTF-8", 212)
    case utf8_croatian_ci         extends Collation("utf8", "UTF-8", 213)
    case utf8_unicode_520_ci      extends Collation("utf8", "UTF-8", 214)
    case utf8_vietnamese_ci       extends Collation("utf8", "UTF-8", 215)
    case utf8_general_mysql500_ci extends Collation("utf8", "UTF-8", 223)
    case utf8mb4_unicode_ci       extends Collation("utf8mb4", "UTF-8", 224)
    case utf8mb4_icelandic_ci     extends Collation("utf8mb4", "UTF-8", 225)
    case utf8mb4_latvian_ci       extends Collation("utf8mb4", "UTF-8", 226)
    case utf8mb4_romanian_ci      extends Collation("utf8mb4", "UTF-8", 227)
    case utf8mb4_slovenian_ci     extends Collation("utf8mb4", "UTF-8", 228)
    case utf8mb4_polish_ci        extends Collation("utf8mb4", "UTF-8", 229)
    case utf8mb4_estonian_ci      extends Collation("utf8mb4", "UTF-8", 230)
    case utf8mb4_spanish_ci       extends Collation("utf8mb4", "UTF-8", 231)
    case utf8mb4_swedish_ci       extends Collation("utf8mb4", "UTF-8", 232)
    case utf8mb4_turkish_ci       extends Collation("utf8mb4", "UTF-8", 233)
    case utf8mb4_czech_ci         extends Collation("utf8mb4", "UTF-8", 234)
    case utf8mb4_danish_ci        extends Collation("utf8mb4", "UTF-8", 235)
    case utf8mb4_lithuanian_ci    extends Collation("utf8mb4", "UTF-8", 236)
    case utf8mb4_slovak_ci        extends Collation("utf8mb4", "UTF-8", 237)
    case utf8mb4_spanish2_ci      extends Collation("utf8mb4", "UTF-8", 238)
    case utf8mb4_roman_ci         extends Collation("utf8mb4", "UTF-8", 239)
    case utf8mb4_persian_ci       extends Collation("utf8mb4", "UTF-8", 240)
    case utf8mb4_esperanto_ci     extends Collation("utf8mb4", "UTF-8", 241)
    case utf8mb4_hungarian_ci     extends Collation("utf8mb4", "UTF-8", 242)
    case utf8mb4_sinhala_ci       extends Collation("utf8mb4", "UTF-8", 243)
    case utf8mb4_german2_ci       extends Collation("utf8mb4", "UTF-8", 244)
    case utf8mb4_croatian_ci      extends Collation("utf8mb4", "UTF-8", 245)
    case utf8mb4_unicode_520_ci   extends Collation("utf8mb4", "UTF-8", 246)
    case utf8mb4_vietnamese_ci    extends Collation("utf8mb4", "UTF-8", 247)
    case gb18030_chinese_ci       extends Collation("gb18030", "GB18030", 248)
    case gb18030_bin              extends Collation("gb18030", "GB18030", 249)
    case gb18030_unicode_520_ci   extends Collation("gb18030", "GB18030", 250)
    case utf8mb4_0900_ai_ci       extends Collation("utf8mb4", "UTF-8", 255)

}

object Collation {

    private val charsetToDefaultCollationMapping = Map(
      ("big5", "big5_chinese_ci"),
      ("dec8", "dec8_swedish_ci"),
      ("cp850", "cp850_general_ci"),
      ("hp8", "hp8_english_ci"),
      ("koi8r", "koi8r_general_ci"),
      ("latin1", "latin1_swedish_ci"),
      ("latin2", "latin2_general_ci"),
      ("swe7", "swe7_swedish_ci"),
      ("ascii", "ascii_general_ci"),
      ("ujis", "ujis_japanese_ci"),
      ("sjis", "sjis_japanese_ci"),
      ("hebrew", "hebrew_general_ci"),
      ("tis620", "tis620_thai_ci"),
      ("euckr", "euckr_korean_ci"),
      ("koi8u", "koi8u_general_ci"),
      ("gb2312", "gb2312_chinese_ci"),
      ("greek", "greek_general_ci"),
      ("cp1250", "cp1250_general_ci"),
      ("gbk", "gbk_chinese_ci"),
      ("latin5", "latin5_turkish_ci"),
      ("armscii8", "armscii8_general_ci"),
      ("utf8", "utf8_general_ci"),
      ("ucs2", "ucs2_general_ci"),
      ("cp866", "cp866_general_ci"),
      ("keybcs2", "keybcs2_general_ci"),
      ("macce", "macce_general_ci"),
      ("macroman", "macroman_general_ci"),
      ("cp852", "cp852_general_ci"),
      ("latin7", "latin7_general_ci"),
      ("utf8mb4", "utf8mb4_general_ci"),
      ("cp1251", "cp1251_general_ci"),
      ("utf16", "utf16_general_ci"),
      ("utf16le", "utf16le_general_ci"),
      ("cp1256", "cp1256_general_ci"),
      ("cp1257", "cp1257_general_ci"),
      ("utf32", "utf32_general_ci"),
      ("binary", "binary"),
      ("geostd8", "geostd8_general_ci"),
      ("cp932", "cp932_japanese_ci"),
      ("eucjpms", "eucjpms_japanese_ci"),
      ("gb18030", "gb18030_chinese_ci")
    )

    def getDefaultCollationFromCharsetName(charset: String): String =
        charsetToDefaultCollationMapping.get(charset) match
            case Some(value) => value
            case None        => throw new IllegalArgumentException(s"Unknown charset name: [$charset]")

}
