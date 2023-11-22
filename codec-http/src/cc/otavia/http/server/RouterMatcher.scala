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
import cc.otavia.common.SystemPropertyUtil
import cc.otavia.core.cache.ActorThreadLocal
import cc.otavia.core.system.ActorThread
import cc.otavia.http.HttpMethod.*
import cc.otavia.http.server.Router.*
import cc.otavia.http.{HttpConstants, HttpMethod, HttpUtils, HttpVersion}

import java.net.{URLDecoder, URLEncoder}
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

    private val ctx = new ActorThreadLocal[RouterContext] {
        override protected def initialValue(): RouterContext = new RouterContext()
    }

    private val ctxJdk = new ThreadLocal[RouterContext] { // only used in unittest
        override def initialValue(): RouterContext = new RouterContext()
    }

    this.synchronized {
        for (router <- routers) {
            router match
                case ControllerRouter(method, path, controller, requestSerde, responseSerde) =>
                    updateTree(method, path, router)
                case StaticFilesRouter(path, dir)          => updateTree(HttpMethod.GET, path.trim, router)
                case router: NotFoundRouter                => this.notFoundRouter = router
                case ConstantRouter(method, path, _, _, _) => updateTree(method, path.trim, router)
        }
        if (this.notFoundRouter == null) this.notFoundRouter = NotFoundRouter()
    }

    def `404`: NotFoundRouter = notFoundRouter

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
                if ((part.startsWith("{") && part.endsWith("}")) || part.startsWith(":")) {
                    // {var} style path variable or :var style path variable
                    val name = if (part.startsWith("{")) part.tail.init.trim else part.tail.trim
                    if (matched.varChild == null) matched.varChild = new Node(null, name, null, true)
                    else if (matched.varChild.name != name)
                        throw new IllegalArgumentException(
                          s"path[ $path ] at '${matched.name}' has already exists variable '${matched.varChild.name}', but the second '$name' configured!"
                        )
                    matched = matched.varChild
                } else {
                    val encoded = URLEncoder.encode(part, URL_CHARSET).getBytes(StandardCharsets.US_ASCII)
                    if (matched.children == null) matched.children = mutable.ArrayBuffer.empty
                    matched.children.find(n => n.name == part && !n.isVar) match
                        case Some(node) => matched = node
                        case None =>
                            val node = new Node(encoded, part)
                            matched.children.addOne(node)
                            matched = node
                }
                if (isLast) matched.router = router
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
        while (buffer.skipIfNextIs(HttpConstants.SP)) {}

        val routerContext = context
        routerContext.clear()

        routerContext.setMethod(method)

        // skip schema
        buffer.skipIfNextAre(HttpConstants.HTTP_SCHEMA)
        buffer.skipIfNextAre(HttpConstants.HTTPS_SCHEMA)

        if (!buffer.skipIfNextIs('/')) { // skip host
            val len = buffer.bytesBefore('/'.toByte) + 1
            buffer.skipReadableBytes(len)
        }

        var matched = choiceTree(method)

        if (buffer.skipIfNextIs(HttpConstants.SP)) {
            if (matched.router != null) matched.router else notFoundRouter
        } else {
            while (
              matched != null && (matched.children != null || matched.varChild != null) &&
              !buffer.nextIn(HttpConstants.PATH_END)
            ) {
                var find: Node = null
                if (matched.children != null) {
                    var i = 0
                    while (find == null && i < matched.children.length) {
                        val node = matched.children(i)
                        if (
                          buffer.nextAre(node.text) &&
                          buffer.indexIn(HttpConstants.NODE_END, node.text.length + buffer.readerOffset)
                        ) {
                            find = node
                            routerContext.appendPath("/")
                            routerContext.appendPath(node.name)
                            buffer.skipReadableBytes(node.text.length)
                            buffer.skipIfNextIs(HttpConstants.PATH_SPLITTER)
                        }
                        i += 1
                    }
                }

                if (find == null && matched.varChild != null) {
                    find = matched.varChild
                    val len     = buffer.bytesBeforeIn(HttpConstants.NODE_END)
                    val encoded = buffer.readCharSequence(len, StandardCharsets.US_ASCII).toString
                    val decoded = URLDecoder.decode(encoded, URL_CHARSET)
                    routerContext.putPathVariable(find.name, decoded)
                    routerContext.appendPath("/")
                    routerContext.appendPath(decoded)
                    buffer.skipIfNextIs(HttpConstants.PATH_SPLITTER)
                }

                matched = find
            }

            val router = if (matched != null && matched.router != null) {
                while (!buffer.nextIn(HttpConstants.PATH_END)) {
                    val len     = buffer.bytesBeforeIn(HttpConstants.NODE_SPLITTER)
                    val encoded = buffer.readCharSequence(len, StandardCharsets.US_ASCII).toString
                    val decoded = URLDecoder.decode(encoded, URL_CHARSET)
                    routerContext.appendPath("/")
                    routerContext.appendPath(decoded)
                    routerContext.appendRemaining("/")
                    routerContext.appendRemaining(decoded)
                    buffer.skipIfNextIs(HttpConstants.PATH_SPLITTER)
                }
                matched.router
            } else notFoundRouter

            router match // parse params
                case ControllerRouter(_, _, _, requestSerde, _)
                    if requestSerde.hasParams && buffer.skipIfNextIs(HttpConstants.PARAM_START) =>
                    while (!buffer.nextIs(HttpConstants.SP)) {
                        val key = URLDecoder.decode(
                          buffer
                              .readCharSequence(buffer.bytesBefore(HttpConstants.EQ), StandardCharsets.US_ASCII)
                              .toString,
                          URL_CHARSET
                        )
                        buffer.skipReadableBytes(1) // skip '='
                        val value = URLDecoder.decode(
                          buffer
                              .readCharSequence(
                                buffer.bytesBeforeIn(HttpConstants.PARAMS_END),
                                StandardCharsets.US_ASCII
                              )
                              .toString,
                          URL_CHARSET
                        )
                        buffer.skipIfNextIs(HttpConstants.PARAM_SPLITTER)
                        routerContext.putPathVariable(key, value)
                    }
                case _ =>

            router
        }
    }

    def context: RouterContext = if (ActorThread.currentThreadIsActorThread) ctx.get() else ctxJdk.get()

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
        var name: String = null,
        var children: mutable.ArrayBuffer[Node] = null,
        val isVar: Boolean = false,
        var router: Router = null,
        var varChild: Node = null
    )

}
