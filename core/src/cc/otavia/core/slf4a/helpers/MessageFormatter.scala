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

package cc.otavia.core.slf4a.helpers

import cc.otavia.common.{Report, ThrowableUtil}

import java.lang

object MessageFormatter {

    private[helpers] val DELIM_START = '{'
    private[helpers] val DELIM_STOP  = '}'
    private[helpers] val DELIM_STR   = "{}"
    private val ESCAPE_CHAR          = '\\'

    private def objectAppend(sbuf: lang.StringBuilder, obj: Any): Unit = {
        try {
            if (!obj.isInstanceOf[Throwable]) sbuf.append(obj.toString)
            else sbuf.append(ThrowableUtil.stackTraceToString(obj.asInstanceOf[Throwable]))
        } catch {
            case t: Throwable =>
                Report.report(s"SLF4A: Failed toString() invocation on an object of type [${obj.getClass.getName}]", t)
                sbuf.append("[FAILED toString()]")
        }
    }

    def format(formatter: String, arg: Any): String = {
        val sbuf = new lang.StringBuilder(formatter.length + 20)
        val j    = formatter.indexOf(DELIM_STR)
        if (j != -1) {
            sbuf.append(formatter, 0, j)
            objectAppend(sbuf, arg)
            sbuf.append(formatter, j + 2, formatter.length)
            sbuf.toString
        } else {
            sbuf.append(formatter)
            sbuf.append(' ')
            sbuf.append(arg)
            sbuf.toString
        }
    }

    def format(formatter: String, arg1: Any, arg2: Any): String = {
        val sbuf = new lang.StringBuilder(formatter.length + 40)
        val j    = formatter.indexOf(DELIM_STR)
        if (j == -1) {
            sbuf.append(formatter)
            sbuf.append(' ')
            objectAppend(sbuf, arg1)
            sbuf.append(' ')
            objectAppend(sbuf, arg2)
            sbuf.toString
        } else {
            sbuf.append(formatter, 0, j)
            objectAppend(sbuf, arg1)
            val k = formatter.indexOf(DELIM_STR, j + 2)
            if (k == -1) {
                sbuf.append(formatter, j + 2, formatter.length)
                sbuf.append(' ')
                objectAppend(sbuf, arg2)
            } else {
                sbuf.append(formatter, j + 2, k)
                objectAppend(sbuf, arg2)
                sbuf.append(formatter, k + 2, formatter.length)
            }
            sbuf.toString
        }
    }

    def format(formatter: String, seq: Seq[Any]): String = {
        val sbuf       = new lang.StringBuilder(formatter.length + seq.length * 20)
        var searchFrom = 0
        var i          = 0
        while (i < seq.length) {
            val j = formatter.indexOf(DELIM_STR, searchFrom)
            if (j == -1) {
                sbuf.append(formatter, searchFrom, formatter.length)
                while (i < seq.length) {
                    sbuf.append(' ')
                    objectAppend(sbuf, seq(i))
                    i += 1
                }
                return sbuf.toString
            }
            sbuf.append(formatter, searchFrom, j)
            objectAppend(sbuf, seq(i))
            searchFrom = j + 2
            i += 1
        }
        sbuf.append(formatter, searchFrom, formatter.length)
        sbuf.toString
    }

    private def primaryArrayAppend[@specialized T](sbuf: lang.StringBuilder, a: Array[T]): Unit = {
        sbuf.append('[')
        var i = 0
        while (i < a.length - 1) {
            sbuf.append(a(i))
            sbuf.append(", ")
            i += 1
        }
        sbuf.append(a(i))
        sbuf.append(']')
    }

}
