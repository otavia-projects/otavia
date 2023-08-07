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

package cc.otavia.core.ioc

import cc.otavia.core.actor.{Actor, MessageOf}
import cc.otavia.core.address.Address
import cc.otavia.core.message.{Ask, Call, Message, Notice}

import scala.reflect.{ClassTag, classTag}

trait Injectable {
    this: Actor[?] =>

    final def autowire[T <: Call](name: String): Address[T] = ???

    final def autowire[A <: Actor[?]: ClassTag](
        qualifier: Option[String] = None,
        remote: Option[String] = None
    ): Address[MessageOf[A]] =
        system.getAddress(classTag[A].runtimeClass.asInstanceOf[Class[? <: Actor[?]]], qualifier, remote)

}
