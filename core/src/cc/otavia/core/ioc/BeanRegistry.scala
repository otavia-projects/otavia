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

import cc.otavia.core.actor.{AbstractActor, Actor}
import cc.otavia.core.address.Address
import cc.otavia.core.system.ActorSystem

import java.util.concurrent.ConcurrentHashMap
import scala.collection.mutable
import scala.language.unsafeNulls

private[core] class BeanRegistry(val system: ActorSystem) {

    import BeanRegistry.*

    private val pending = mutable.ArrayBuffer[Registration]()

    @volatile private var snapshot: FrozenSnapshot = FrozenSnapshot.Empty

    @volatile private var frozen: Boolean = false

    def register(
        clz: Class[?],
        address: Address[?],
        qualifier: Option[String] = None,
        primary: Boolean = false
    ): Unit = {
        pending.addOne(Registration(clz, address, qualifier, primary))
    }

    def freeze(): Unit = {
        if pending.isEmpty && snapshot != FrozenSnapshot.Empty then { frozen = true; return }

        val regs = pending.toSeq
        pending.clear()

        snapshot = FrozenSnapshot.build(regs)
        frozen = true
    }

    def unfreeze(): Unit = {
        frozen = false
        val existing = snapshot.allEntries
        pending.clear()
        existing.foreach { e => pending.addOne(Registration(e.clz, e.address, e.qualifier, e.primary)) }
    }

    def isFrozen: Boolean = frozen

    def getBean(qualifier: String, clz: Class[?]): Address[?] = snapshot.getByQualifier(qualifier, clz)

    def getBean(clz: Class[?]): Address[?] = snapshot.getByClass(clz)

    def count: Int = snapshot.count

}

object BeanRegistry {

    private case class Registration(
        clz: Class[?],
        address: Address[?],
        qualifier: Option[String],
        primary: Boolean
    )

    private case class BeanEntry(
        clz: Class[?],
        address: Address[?],
        qualifier: Option[String],
        primary: Boolean
    ) {
        def name: String = clz.getName
    }

    private object SupertypeIndex {

        private val actorClass         = classOf[Actor[?]]
        private val abstractActorClass = classOf[AbstractActor[?]]

        private val cache = new ConcurrentHashMap[Class[?], Set[String]]()

        def supersOf(clz: Class[?]): Set[String] = cache.computeIfAbsent(clz, compute)

        private def compute(clz: Class[?]): Set[String] = {
            val set = Set.newBuilder[String]
            walk(clz, set)
            set.result()
        }

        private def walk(clz: Class[?], set: mutable.Builder[String, Set[String]]): Unit = {
            if clz.isInterface && actorClass.isAssignableFrom(clz) && clz.getTypeParameters.isEmpty then
                set += clz.getName
                clz.getInterfaces.foreach(walk(_, set))
            else if abstractActorClass.isAssignableFrom(clz) && clz.getTypeParameters.isEmpty then
                set += clz.getName
                val sp = clz.getSuperclass
                if sp != null then walk(sp, set)
                clz.getInterfaces.foreach(walk(_, set))
        }

    }

    private class FrozenSnapshot(
        val byClass: Map[String, BeanEntry],
        val byQualifier: Map[String, BeanEntry],
        val bySupertype: Map[String, Array[BeanEntry]]
    ) {

        def allEntries: Seq[BeanEntry] = byClass.values.toSeq

        def count: Int = byClass.size

        def getByClass(clz: Class[?]): Address[?] = {
            val name = clz.getName
            byClass.get(name) match
                case Some(entry) => entry.address
                case None =>
                    bySupertype.get(name) match
                        case Some(entries) if entries.length == 1 => entries(0).address
                        case Some(entries) =>
                            val primaries = entries.filter(_.primary)
                            if primaries.length == 1 then primaries(0).address
                            else if primaries.isEmpty then
                                throw AmbiguousResolutionException(name, entries.map(_.name).toSeq)
                            else
                                throw AmbiguousResolutionException(name, primaries.map(_.name).toSeq)
                        case None =>
                            throw BeanNotFoundException(name, None, byClass.keys.toSeq)
        }

        def getByQualifier(qualifier: String, clz: Class[?]): Address[?] = {
            byQualifier.get(qualifier) match
                case Some(entry) =>
                    if clz.isAssignableFrom(entry.clz) then entry.address
                    else
                        throw BeanNotFoundException(
                            clz.getName,
                            Some(qualifier),
                            byClass.keys.toSeq
                        )
                case None =>
                    throw BeanNotFoundException(clz.getName, Some(qualifier), byClass.keys.toSeq)
        }

    }

    private object FrozenSnapshot {

        val Empty: FrozenSnapshot = new FrozenSnapshot(Map.empty, Map.empty, Map.empty)

        def build(registrations: Seq[Registration]): FrozenSnapshot = {
            val byClass     = Map.newBuilder[String, BeanEntry]
            val byQual      = Map.newBuilder[String, BeanEntry]
            val bySuper     = mutable.HashMap[String, mutable.ArrayBuffer[BeanEntry]]()

            for reg <- registrations do
                val entry = BeanEntry(reg.clz, reg.address, reg.qualifier, reg.primary)

                byClass += (entry.name -> entry)

                reg.qualifier.foreach { q => byQual += (q -> entry) }

                for sup <- SupertypeIndex.supersOf(reg.clz) if sup != entry.name do
                    val buf = bySuper.getOrElseUpdate(sup, mutable.ArrayBuffer.empty[BeanEntry])
                    buf += entry

            val bySupertype = bySuper.view.mapValues(_.toArray).toMap

            new FrozenSnapshot(byClass.result(), byQual.result(), bySupertype)
        }

    }

}
