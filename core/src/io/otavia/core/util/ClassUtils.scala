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

package io.otavia.core.util

import scala.language.{higherKinds, unsafeNulls}

object ClassUtils {

    private val PACKAGE_SEPARATOR_CHAR = '.'

    /** The shortcut to [[simpleClassName]] . */
    def simpleClassName(nullable: AnyRef | Null): String = {
        if (nullable == null) "null_object"
        else {
            val o: AnyRef = nullable
            val clz       = o.getClass
            simpleClassName(clz)
        }
    }

    /** Generates a simplified name from a [[Class]]. Similar to Class.getSimpleName(), but it works fine with anonymous
     *  classes.
     */
    def simpleClassName(clz: Class[?]): String = {
        val className: String = clz.getName
        val lastDotIdx: Int   = className.lastIndexOf(PACKAGE_SEPARATOR_CHAR)
        if (lastDotIdx > -1) className.substring(lastDotIdx + 1) else className
    }

    def printInheritTree(clz: Class[?], hierarchy: Int = 0): Unit = {
        val tokenClz =
            "\t" * hierarchy + (if (clz.isInterface)
                                    s"I(${clz.getName}${handleTypeParameters(clz)})"
                                else
                                    s"C(${clz.getName}${handleTypeParameters(clz)})")
        println(tokenClz)

        val sp = clz.getSuperclass
        if (sp != null)
            printInheritTree(sp, hierarchy + 1)

        clz.getInterfaces.foreach { intClz =>
            printInheritTree(intClz, hierarchy + 1)
        }
    }

    def handleTypeParameters(clz: Class[?]): String = {
        val typeParameters = clz.getTypeParameters
        val tps = typeParameters.map { tp =>
            (tp.getName, tp.getBounds.mkString(","), tp.getGenericDeclaration.getSimpleName)
        }
        val typeName = tps.mkString("[", ",", "]")
        typeName
    }

}
