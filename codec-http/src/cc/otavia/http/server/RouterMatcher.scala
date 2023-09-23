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

package cc.otavia.http.server

import cc.otavia.buffer.Buffer
import cc.otavia.core.util.SystemPropertyUtil
import cc.otavia.http.HttpMethod.*
import cc.otavia.http.server.Router.*
import cc.otavia.http.{HttpConstants, HttpMethod, HttpUtils, HttpVersion}

import java.net.URLEncoder
import java.nio.charset.{Charset, StandardCharsets}
import java.util
import scala.collection.mutable
import scala.language.unsafeNulls

class RouterMatcher(routers: Seq[Router]) {

    import RouterMatcher.*

    private var notFoundRouter: NotFoundRouter = _

    private val getTree: Node     = new Node(ROOT_TEXT)
    private val postTree: Node    = new Node(ROOT_TEXT)
    private val optionsTree: Node = new Node(ROOT_TEXT)
    private val headTree: Node    = new Node(ROOT_TEXT)
    private val putTree: Node     = new Node(ROOT_TEXT)
    private val patchTree: Node   = new Node(ROOT_TEXT)
    private val deleteTree: Node  = new Node(ROOT_TEXT)
    private val tractTree: Node   = new Node(ROOT_TEXT)
    private val connectTree: Node = new Node(ROOT_TEXT)

    this.synchronized {
        for (router <- routers) {
            router match
                case ControllerRouter(method, path, controller, headers, paramsSerde, contentSerde, responseSerde) =>
                    updateTree(method, path, router)
                case StaticFilesRouter(path, dir)                 => updateTree(HttpMethod.GET, path.trim, router)
                case router: NotFoundRouter                       => this.notFoundRouter = router
                case PlainTextRouter(method, path, text, charset) => updateTree(method, path.trim, router)
        }
        if (this.notFoundRouter == null) this.notFoundRouter = NotFoundRouter()
    }

    private def updateTree(method: HttpMethod, path: String, router: Router): Unit = {
        val tree    = choiceTree(method)
        val notRoot = path != "/"

        if (notRoot) {
            val paths   = path.split("/")
            var matched = tree
            var i       = 1
            while (i < paths.length) {
                val isLast = i == paths.length - 1
                val part   = paths(i)
                if (part.startsWith("{") && part.endsWith("}")) {
                    // {var} style path variable
                } else if (part.startsWith(":")) {
                    // :var style path variable
                } else {
                    val encoded =
                        if (isLast) URLEncoder.encode(part, URL_CHARSET).getBytes(StandardCharsets.US_ASCII)
                        else {
                            val prefix = URLEncoder.encode(part, URL_CHARSET).getBytes(StandardCharsets.US_ASCII)
                            val arr    = new Array[Byte](prefix.length + 1)
                            System.arraycopy(prefix, 0, arr, 0, prefix.length)
                            arr(prefix.length) = '/'
                            arr
                        }
                    if (matched.children == null) {
                        matched.children = mutable.ArrayBuffer.empty
                        val node = new Node(encoded)
                        matched.children.addOne(node)
                        if (isLast) node.router = router else matched = node
                    } else {
                        matched.children.find(_.text sameElements encoded) match
                            case Some(node) =>
                                matched = node
                            case None =>
                                val node = new Node(encoded)
                                matched.children.addOne(node)
                                if (isLast) node.router = router else matched = node
                    }
                }

                i += 1
            }
        } else tree.router = router
    }

    private def choiceTree(method: HttpMethod): Node = method match
        case GET     => getTree
        case POST    => postTree
        case OPTIONS => optionsTree
        case HEAD    => headTree
        case PUT     => putTree
        case PATCH   => patchTree
        case DELETE  => deleteTree
        case TRACE   => tractTree
        case CONNECT => connectTree

    def choice(buffer: Buffer): Router = this.choice(buffer, HttpUtils.parseMethod(buffer))

    def choice(buffer: Buffer, method: HttpMethod): Router = {
        while (buffer.skipIfNext(HttpConstants.SP)) {}

        // skip schema
        buffer.skipIfNexts(HttpConstants.HTTP_SCHEMA)
        buffer.skipIfNexts(HttpConstants.HTTPS_SCHEMA)

        if (!buffer.skipIfNext('/')) { // skip host
            val len = buffer.bytesBefore('/'.toByte) + 1
            buffer.skipWritableBytes(len)
        }

        var current = choiceTree(method)

        if (buffer.skipIfNext(HttpConstants.SP)) {
            if (current.router != null) current.router else notFoundRouter
        } else {
            while (current != null && current.children != null) {
                current.children.find { node =>
                    if (node.isVar) true
                    else {
                        if (buffer.skipIfNexts(node.text)) true else false
                    }
                } match
                    case Some(node) => current = node
                    case None       => current = null
            }

            if (current != null) current.router else notFoundRouter
        }
    }

    def sync(): this.type = this.synchronized(this)

}

object RouterMatcher {

    private val ROOT_TEXT: Array[Byte]  = Array.empty[Byte]
    private val EMPTY_TEXT: Array[Byte] = Array.empty[Byte]

    private val URL_CHARSET_DEFAULT = "utf8"
    val URL_CHARSET: Charset =
        Charset.forName(SystemPropertyUtil.get("cc.otavia.http.url.charset", URL_CHARSET_DEFAULT))

    private class Node(
        val text: Array[Byte],
        var children: mutable.ArrayBuffer[Node] = null,
        val isVar: Boolean = false,
        var router: Router = null
    )

}
