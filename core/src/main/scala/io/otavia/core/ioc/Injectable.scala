/*
 * Copyright 2022 Yan Kun
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

package io.otavia.core.ioc

import io.otavia.core.actor.{Actor, MessageOf}
import io.otavia.core.address.Address
import io.otavia.core.message.{Ask, Message, Notice}

import scala.reflect.{ClassTag, classTag}

trait Injectable {
    this: Actor[?] =>

    final def autowire[T <: Ask[?] | Notice](name: String): Address[T] = ???

    final def autowire[A <: Actor[_]: ClassTag](qualifier: Option[String] = None): Address[MessageOf[A]] =
        system.getAddress(classTag[A].runtimeClass.asInstanceOf[Class[_ <: Actor[_]]], qualifier)

}
