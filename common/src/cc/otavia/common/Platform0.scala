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

package cc.otavia.common

import scala.language.unsafeNulls

object Platform0 {

    private val JAVA_VERSION: Int         = javaVersion0
    private final val IS_ANDROID: Boolean = isAndroid0

    def isAndroid: Boolean = IS_ANDROID
    private def isAndroid0 = {
        // Idea: Sometimes java binaries include Android classes on the classpath, even if it isn't actually Android.
        // Rather than check if certain classes are present, just check the VM, which is tied to the JDK.
        // Optional improvement: check if `android.os.Build.VERSION` is >= 24. On later versions of Android, the
        // OpenJDK is used, which means `Unsafe` will actually work as expected.
        // Android sets this property to Dalvik, regardless of whether it actually is.
        val vmName    = SystemPropertyUtil.get("java.vm.name")
        val isAndroid = vmName.contains("Dalvik")
        isAndroid
    }

    private def javaVersion0 =
        if (isAndroid0) 6 else majorVersionFromJavaSpecificationVersion
    private def majorVersionFromJavaSpecificationVersion =
        majorVersion(SystemPropertyUtil.get("java.specification.version", "1.6"))
    private def majorVersion(javaSpecVersion: String) = {
        val version = javaSpecVersion.split("\\.").map(_.toInt)
        if (version(0) == 1) {
            assert(version(1) >= 6)
            version(1)
        } else version(0)
    }

}
