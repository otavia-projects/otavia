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

package cc.otavia.core.actor

import cc.otavia.core.actor.MainActor.Args
import cc.otavia.core.message.Notice
import cc.otavia.core.stack.{AskStack, NoticeStack, StackState, StackYield}

/** Application entry point actor. Automatically sends command-line arguments as an [[Args]] notice to self on mount.
 *
 *  Subclasses implement [[main0]] to define the application logic. Note that [[afterMount]] is finalized to inject the
 *  args-sending behavior — override [[afterMount0]] instead for custom mount-time initialization.
 *
 *  @param args
 *    command-line arguments passed to the application
 */
abstract class MainActor(val args: Array[String] = Array.empty) extends StateActor[Args] {

    final override def afterMount(): Unit = {
        afterMount0()
        self.notice(Args(args))
        logger.info(s"Send main args ${args.mkString("[", ", ", "]")} to main actor [${getClass.getName}]")
    }

    /** Replacement for [[afterMount]] since it is finalized in [[MainActor]]. Override this for custom mount-time
     *  initialization.
     */
    protected def afterMount0(): Unit = {}

    final override def resumeNotice(stack: NoticeStack[Args]): StackYield = main0(stack)

    /** Application entry point. Called when the [[Args]] notice is received. */
    def main0(stack: NoticeStack[Args]): StackYield

}

object MainActor {
    case class Args(args: Array[String]) extends Notice

}
