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

package io.otavia.core.util

import java.util.Locale
import scala.language.unsafeNulls

object Platform {

    val NORMALIZED_ARCH: String = normalizeArch(SystemPropertyUtil.get("os.arch", ""))
    private val NORMALIZED_OS: String   = normalizeOs(SystemPropertyUtil.get("os.name", ""))

    def isWindows: Boolean = NORMALIZED_OS == "windows"

    private final def normalize(value: String): String =
        value.toLowerCase(Locale.US).replaceAll("[^a-z0-9]+", "")

    private final def normalizeArch(value: String): String = {
        val normal = normalize(value)
        if (normal.matches("^(x8664|amd64|ia32e|em64t|x64)$")) "x86_64"
        else if (normal.matches("^(x8632|x86|i[3-6]86|ia32|x32)$")) "x86_32"
        else if (normal.matches("^(ia64|itanium64)$")) "itanium_64"
        else if (normal.matches("^(sparc|sparc32)$")) "sparc_32"
        else if (normal.matches("^(sparcv9|sparc64)$")) "sparc_64"
        else if (normal.matches("^(arm|arm32)$")) "arm_32"
        else if (normal == "aarch64") "aarch_64"
        else if (normal.matches("^(ppc|ppc32)$")) "ppc_32"
        else if (normal == "ppc64") "ppc_64"
        else if (normal == "ppc64le") "ppcle_64"
        else if (normal == "s390") "s390_32"
        else if (normal == "s390x") "s390_64"
        else if (normal == "loongarch64") "loongarch_64"
        else "unknown"
    }

    private final def normalizeOs(value: String): String = {
        val normal = normalize(value)
        if (normal.startsWith("aix")) "aix"
        else if (normal.startsWith("hpux")) "hpux"
        else if (normal.startsWith("os400") && (normal.length <= 5 || !normal.charAt(5).isDigit)) "os400"
        else if (normal.startsWith("linux")) "linux"
        else if (normal.startsWith("macosx") || normal.startsWith("osx") || normal.startsWith("darwin")) "osx"
        else if (normal.startsWith("freebsd")) "freebsd"
        else if (normal.startsWith("openbsd")) "openbsd"
        else if (normal.startsWith("netbsd")) "netbsd"
        else if (normal.startsWith("solaris") || normal.startsWith("sunos")) "solaris"
        else if (normal.startsWith("windows")) "windows"
        else "unknown"
    }

}
