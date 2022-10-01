/*
 * Copyright 2022 yankun
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


package io.otavia.core

import io.otavia.core.message.MessageIdDistributor

/**
 * base class of
 *
 * @tparam M the type of message of this actor can handle
 */
abstract class ActorBase[M <: Message] {

  /** message header setter */
  given distributor: MessageIdDistributor = new MessageIdDistributor()

  private var ctx: ActorContext = _

  def context: ActorContext = ctx

  /**
   * this method will call by [[ActorSystem]] when actor create, when a actor is creating, the [[ActorSystem]] will
   * create a [[ActorContext]]
   *
   * @param context the context of this actor
   */
  private[core] def setCtx(context: ActorContext): Unit = {
    ctx = context
    distributor.setActorId(context.actorId)
    distributor.setActorAddress(context.address)
  }

  def system: ActorSystem = context.system

  def actorId: Long = context.actorId

  def self: Address[M] = context.address.asInstanceOf[Address[M]]

  def receive(message: M): Unit
}
