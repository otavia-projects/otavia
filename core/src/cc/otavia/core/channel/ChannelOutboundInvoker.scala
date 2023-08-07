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

package cc.otavia.core.channel

import cc.otavia.core.actor.ChannelsActor
import cc.otavia.core.channel.message.ReadPlan
import cc.otavia.core.reactor.Reactor
import cc.otavia.core.stack.{ChannelFuture, Future}
import cc.otavia.core.system.ActorSystem

import java.io.File
import java.net.SocketAddress
import java.nio.channels.FileChannel
import java.nio.file.attribute.FileAttribute
import java.nio.file.{FileAlreadyExistsException, OpenOption, Path}
import scala.language.unsafeNulls

trait ChannelOutboundInvoker {

    /** Request to bind to the given [[SocketAddress]] and notify the [[ChannelFuture]] once the operation completes,
     *  either because the operation was successful or because of an error. This will result in having the
     *  [[ChannelHandler.bind(ChannelHandlerContext, SocketAddress)]] method called of the next [[ChannelHandler]]
     *  contained in the [[ChannelPipeline]] of the [[Channel]].
     *
     *  @param local
     *    Address to bind
     *  @param future
     *    bind async result
     *  @return
     *    same [[ChannelFuture]] in params [[future]]
     */
    def bind(local: SocketAddress, future: ChannelFuture): ChannelFuture

    /** Request to connect to the given [[SocketAddress]] while bind to the [[local]] and notify the [[ChannelFuture]]
     *  once the operation completes, either because the operation was successful or because of an error. This will
     *  result in having the [[ChannelHandler.connect(ChannelHandlerContext, SocketAddress, SocketAddress)]] method
     *  called of the next [[ChannelHandler]] contained in the [[ChannelPipeline]] of the [[Channel]].
     *
     *  @param remote
     *    remote address to connect.
     *  @param local
     *    local address to bind.
     *  @param future
     *    operation async result.
     *  @return
     *    same [[ChannelFuture]] in params [[future]]
     */
    def connect(remote: SocketAddress, local: Option[SocketAddress], future: ChannelFuture): ChannelFuture

    /** Request to connect to the given [[SocketAddress]] and notify the [[ChannelFuture]] once the operation completes,
     *  either because the operation was successful or because of an error. If the connection fails because of a
     *  connection timeout, the [[ChannelFuture]] will get failed with a [[ConnectTimeoutException]]. If it fails
     *  because of connection refused a [[ConnectException]] will be used. This will result in having the
     *  [[ChannelHandler.connect(ChannelHandlerContext, SocketAddress, SocketAddress)]] method called of the next
     *  [[ChannelHandler]] contained in the [[ChannelPipeline]] of the [[Channel]].
     *
     *  @param remote
     *    remote address to connect.
     *  @param future
     *  @return
     */
    final def connect(remote: SocketAddress, future: ChannelFuture): ChannelFuture = connect(remote, None, future)

    /** Opens or creates a file, returning a file channel to access the file. An invocation of this method behaves in
     *  exactly the same way as the invocation
     *
     *  [[FileChannel.open(path, options)]]
     *
     *  where [[options]] is a set of the options specified in the options array
     *
     *  This will result in having the [[ChannelHandler.open]] method called of the next [[ChannelHandler]] contained in
     *  the [[ChannelPipeline]] of the [[Channel]].
     *
     *  @param path
     *    The path of the file to open or create
     *  @param options
     *    Options specifying how the file is opened
     *  @param attrs
     *    An optional list of file attributes to set atomically when creating the file
     *  @param future
     *    operation async result. It maybe fails with
     *    1. [[IllegalArgumentException]] - If the set contains an invalid combination of options
     *    1. [[UnsupportedOperationException]] – If the path is associated with a provider that does not support
     *       creating file channels, or an unsupported open option is specified.
     *    1. [[FileAlreadyExistsException]] – If a file of that name already exists and the CREATE_NEW option is
     *       specified and the file is being opened for writing (optional specific exception) IOException – If an I/O
     *       error occurs
     *    1. [[SecurityException]] – If a security manager is installed and it denies an unspecified permission required
     *       by the implementation. In the case of the default provider, the SecurityManager.checkRead(String) method is
     *       invoked to check read access if the file is opened for reading. The SecurityManager.checkWrite(String)
     *       method is invoked to check write access if the file is opened for writing
     *  @return
     *    same [[ChannelFuture]] in params [[future]]
     *  @see
     *    [[FileChannel]]
     */
    def open(path: Path, options: Seq[OpenOption], attrs: Seq[FileAttribute[?]], future: ChannelFuture): ChannelFuture

    /** Opens or creates a file, returning a file channel to access the file. An invocation of this method behaves in
     *  exactly the same way as the invocation
     *
     *  [[FileChannel.open(path, options)]]
     *
     *  where [[options]] is a set of the options specified in the options array
     *
     *  This will result in having the [[ChannelHandler.open]] method called of the next [[ChannelHandler]] contained in
     *  the [[ChannelPipeline]] of the [[Channel]].
     *
     *  @param file
     *    The file to open or create
     *  @param options
     *    Options specifying how the file is opened
     *  @param attrs
     *    An optional list of file attributes to set atomically when creating the file
     *  @param future
     *    operation async result. It maybe fails with
     *    1. [[IllegalArgumentException]] - If the set contains an invalid combination of options
     *    1. [[UnsupportedOperationException]] – If the path is associated with a provider that does not support
     *       creating file channels, or an unsupported open option is specified.
     *    1. [[FileAlreadyExistsException]] – If a file of that name already exists and the CREATE_NEW option is
     *       specified and the file is being opened for writing (optional specific exception) IOException – If an I/O
     *       error occurs
     *    1. [[SecurityException]] – If a security manager is installed and it denies an unspecified permission required
     *       by the implementation. In the case of the default provider, the SecurityManager.checkRead(String) method is
     *       invoked to check read access if the file is opened for reading. The SecurityManager.checkWrite(String)
     *       method is invoked to check write access if the file is opened for writing
     *  @return
     *    same [[ChannelFuture]] in params [[future]]
     *  @see
     *    [[FileChannel]]
     */
    final def open(
        file: File,
        options: Seq[OpenOption],
        attrs: Seq[FileAttribute[?]],
        future: ChannelFuture
    ): ChannelFuture = open(file.toPath, options, attrs, future)

    /** Request to disconnect from the remote peer and notify the [[ChannelFuture]] once the operation completes, either
     *  because the operation was successful or because of an error. This will result in having the
     *  [[ChannelHandler.disconnect(ChannelHandlerContext)]] method called of the next [[ChannelHandler]] contained in
     *  the [[ChannelPipeline]] of the [[Channel]].
     *
     *  @param future
     *    operation async result.
     *  @return
     *    same [[ChannelFuture]] in params future
     */
    def disconnect(future: ChannelFuture): ChannelFuture

    /** Request to close the [[Channel]] and notify the [[ChannelFuture]] once the operation completes, either because
     *  the operation was successful or because of an error. After it is closed it is not possible to reuse it again.
     *  This will result in having the [[ChannelHandler.close(ChannelHandlerContext)]] method called of the next
     *  [[ChannelHandler]] contained in the [[ChannelPipeline]] of the [[Channel]].
     *
     *  @param future
     *    operation async result.
     *  @return
     *    same [[ChannelFuture]] in params future
     */
    def close(future: ChannelFuture): ChannelFuture

    /** Request shutdown one direction of the [[Channel]] and notify the [[ChannelFuture]] once the operation completes,
     *  either because the operation was successful or because of an error. When completed, the channel will either not
     *  produce any inbound data anymore, or it will not be possible to write data anymore, depending on the given
     *  [[ChannelShutdownDirection]]. Depending on the transport implementation shutting down the
     *  [[ChannelShutdownDirection.Outbound]] or [[ChannelShutdownDirection.Inbound]] might also result in data
     *  transferred over the network. Like for example in case of TCP shutting down the
     *  [[ChannelShutdownDirection.Outbound]] will result in a FIN that is transmitted to the remote peer that will as a
     *  result shutdown [[ChannelShutdownDirection.Inbound]]. This will result in having the
     *  [[ChannelHandler.shutdown(ChannelHandlerContext, ChannelShutdownDirection)]]. method called of the next
     *  [[ChannelHandler]] contained in the [[ChannelPipeline]] of the [[Channel]].
     *
     *  @param direction
     *    direction to shutdown
     *  @param future
     *    operation async result
     *  @return
     *    same [[ChannelFuture]] in params future
     */
    def shutdown(direction: ChannelShutdownDirection, future: ChannelFuture): ChannelFuture

    /** Request to register on the [[Reactor]] for I/O processing. [[ChannelFuture]] once the operation completes,
     *  either because the operation was successful or because of an error. This will result in having the
     *  [[ChannelHandler.register(ChannelHandlerContext)]] method called of the next [[ChannelHandler]] contained in the
     *  [[ChannelPipeline]] of the [[Channel]].
     *
     *  @param future
     *    operation async result
     *  @return
     *    same [[ChannelFuture]] in params future
     */
    def register(future: ChannelFuture): ChannelFuture

    /** Request to deregister from the previous [[Reactor]] and notify the Future once the operation completes, either
     *  because the operation was successful or because of an error. This will result in having the
     *  [[ChannelHandler.deregister(ChannelHandlerContext)]] method called of the next [[ChannelHandler]] contained in
     *  the [[ChannelPipeline]] of the [[Channel]].
     *
     *  @param future
     *    operation async result
     *  @return
     *    same [[ChannelFuture]] in params future
     */
    def deregister(future: ChannelFuture): ChannelFuture

    /** Request to read data from the [[Channel]], triggers an
     *  [[ChannelHandler.channelRead(ChannelHandlerContext, Object)]] event if data was read, and triggers a
     *  channelReadComplete event so the handler can decide to continue reading. If there's a pending read operation
     *  already, this method does nothing. This will result in having the
     *  [[ChannelHandler.read(ChannelHandlerContext, ReadBufferAllocator)]] method called of the next [[ChannelHandler]]
     *  contained in the [[ChannelPipeline]] of the [[Channel]].
     *
     *  @param readPlan
     *  @return
     */
    def read(readPlan: ReadPlan): this.type

    def read(): this.type

    /** Request to write a message via this [[ChannelHandlerContext]] through the [[ChannelPipeline]]. This method will
     *  not request to actual flush, so be sure to call flush() once you want to request to flush all pending data to
     *  the actual transport.
     *  @param msg
     *    object to write
     */
    def write(msg: AnyRef): Unit

    def write(msg: AnyRef, msgId: Long): Unit

//    def writeComplete(): Unit // TODO for clean AdaptiveBuffer in batch write for example http pipeline request

    /** Request to flush all pending messages via this ChannelOutboundInvoker.
     *  @return
     */
    def flush(): this.type

    /** Shortcut for call write(Object) and flush().
     *  @param msg
     */
    def writeAndFlush(msg: AnyRef): Unit

    def writeAndFlush(msg: AnyRef, msgId: Long): Unit

    /** Send a custom outbound event via this [[ChannelOutboundInvoker]] through the [[ChannelPipeline]]. This will
     *  result in having the {{{ChannelHandler.sendOutboundEvent(ChannelHandlerContext, Object)}}} method called of the
     *  next [[ChannelHandler]] contained in the [[ChannelPipeline]] of the [[Channel]].
     *
     *  @param event
     */
    def sendOutboundEvent(event: AnyRef): Unit

    /** Executor of this channel instance, the channel inbound and outbound event must execute in the binding executor
     */
    def executor: ChannelsActor[?]

    final def system: ActorSystem = executor.system

}
