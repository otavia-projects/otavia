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

import cc.otavia.core.actor.*
import cc.otavia.core.address.Address
import cc.otavia.core.message.*
import cc.otavia.core.stack.{MessageFuture, NoticeStack, StackYield}
import cc.otavia.core.system.ActorSystem
import org.scalatest.funsuite.AnyFunSuite

import scala.language.unsafeNulls

sealed trait TestNotice extends Notice

trait ServiceA extends Actor[TestNotice]

class ServiceAImpl extends StateActor[TestNotice] with ServiceA {
    override def resumeNotice(stack: NoticeStack[TestNotice]): StackYield = stack.`return`()
}

class ServiceAAlt extends StateActor[TestNotice] with ServiceA {
    override def resumeNotice(stack: NoticeStack[TestNotice]): StackYield = stack.`return`()
}

class ServiceB extends StateActor[TestNotice] {
    override def resumeNotice(stack: NoticeStack[TestNotice]): StackYield = stack.`return`()
}

/** Minimal Address implementation for testing - BeanRegistry only stores and compares references. */
private class TestAddress extends Address[Call] {
    def notice(notice: Call & Notice): Unit                                                = ???
    def ask[A <: Call & Ask[? <: Reply]](ask: A, future: MessageFuture[ReplyOf[A]])(using sender: AbstractActor[?]): MessageFuture[ReplyOf[A]] = ???
    def askUnsafe(ask: Ask[?], f: MessageFuture[?])(using sender: AbstractActor[?]): MessageFuture[?] = ???
    def ask[A <: Call & Ask[? <: Reply]](ask: A, f: MessageFuture[ReplyOf[A]], timeout: Long)(using sender: AbstractActor[?]): MessageFuture[ReplyOf[A]] = ???
    private[core] def reply(reply: Reply, replyId: Long, sender: AbstractActor[?]): Unit  = ???
    private[core] def reply(reply: Reply, replyIds: Array[Long], sender: AbstractActor[?]): Unit = ???
    private[core] def `throw`(cause: ExceptionMessage, replyId: Long, sender: AbstractActor[?]): Unit = ???
    private[core] def `throw`(cause: ExceptionMessage, replyIds: Array[Long], sender: AbstractActor[?]): Unit = ???
}

class ModuleSuite extends AnyFunSuite {

    test("Module should have name and empty dependencies by default") {
        class TestModule extends AbstractModule {
            override def name: String = "test"
            override def definitions: Seq[BeanDefinition] = Seq.empty
        }
        val m = new TestModule
        assert(m.name == "test")
        assert(m.dependencies.isEmpty)
    }

    test("Module should declare dependencies") {
        class DependentModule extends AbstractModule {
            override def name: String = "dependent"
            override def dependencies: Seq[String] = Seq("base")
            override def definitions: Seq[BeanDefinition] = Seq.empty
        }
        val m = new DependentModule
        assert(m.dependencies == Seq("base"))
    }

    test("ModuleDependencyException should contain diagnostic info") {
        val ex = ModuleDependencyException("cache", "redis", Seq("log4a"))
        assert(ex.getMessage.contains("cache"))
        assert(ex.getMessage.contains("redis"))
        assert(ex.getMessage.contains("log4a"))
    }

    test("DuplicateModuleException should contain diagnostic info") {
        val ex = DuplicateModuleException("redis", "cc.otavia.RedisModule")
        assert(ex.getMessage.contains("redis"))
    }

    test("ModuleListener should be queued before load") {
        class TestModule extends AbstractModule {
            override def name: String = "listener-test"
            override def definitions: Seq[BeanDefinition] = Seq.empty
        }

        var called = false
        val m = new TestModule
        m.addListener(new ModuleListener {
            override def onLoaded(system: ActorSystem): Unit = called = true
        })

        assert(!m.loaded)
        assert(!called)
    }

    test("BeanResolutionException hierarchy should have descriptive messages") {
        val notFound = BeanNotFoundException("Foo", None, Seq("Bar", "Baz"))
        assert(notFound.getMessage.contains("Foo"))
        assert(notFound.getMessage.contains("Bar"))

        val notFoundQ = BeanNotFoundException("Foo", Some("primary"), Seq("Bar"))
        assert(notFoundQ.getMessage.contains("primary"))

        val ambiguous = AmbiguousResolutionException("Service", Seq("Impl1", "Impl2"))
        assert(ambiguous.getMessage.contains("Impl1"))
        assert(ambiguous.getMessage.contains("primary"))

        val dupReg = DuplicateRegistrationException("Foo", "addr1", "addr2")
        assert(dupReg.getMessage.contains("Foo"))

        val dupQual = DuplicateQualifierException("myBean", "A", "B")
        assert(dupQual.getMessage.contains("myBean"))
    }

    test("BeanRegistry register, freeze, lookup by exact class") {
        val registry = new BeanRegistry(null)
        val address = new TestAddress()

        registry.register(classOf[ServiceB], address)
        assert(!registry.isFrozen)

        registry.freeze()
        assert(registry.isFrozen)
        assert(registry.count == 1)

        val result = registry.getBean(classOf[ServiceB])
        assert(result eq address)
    }

    test("BeanRegistry should resolve by supertype trait") {
        val registry = new BeanRegistry(null)
        val address = new TestAddress()

        registry.register(classOf[ServiceAImpl], address)
        registry.freeze()

        val result = registry.getBean(classOf[ServiceA])
        assert(result eq address)
    }

    test("BeanRegistry should resolve by qualifier") {
        val registry = new BeanRegistry(null)
        val addr1 = new TestAddress()
        val addr2 = new TestAddress()

        registry.register(classOf[ServiceAImpl], addr1, Some("impl1"))
        registry.register(classOf[ServiceAAlt], addr2, Some("impl2"))
        registry.freeze()

        assert(registry.getBean("impl1", classOf[ServiceA]) eq addr1)
        assert(registry.getBean("impl2", classOf[ServiceA]) eq addr2)
    }

    test("BeanRegistry should throw BeanNotFoundException for unregistered type") {
        val registry = new BeanRegistry(null)
        registry.freeze()

        val ex = intercept[BeanNotFoundException] {
            registry.getBean(classOf[ServiceB])
        }
        assert(ex.requestedType == classOf[ServiceB].getName)
    }

    test("BeanRegistry should disambiguate by primary flag") {
        val registry = new BeanRegistry(null)
        val addr1 = new TestAddress()
        val addr2 = new TestAddress()

        registry.register(classOf[ServiceAImpl], addr1, None, primary = false)
        registry.register(classOf[ServiceAAlt], addr2, None, primary = true)
        registry.freeze()

        assert(registry.getBean(classOf[ServiceA]) eq addr2)
    }

    test("BeanRegistry should throw AmbiguousResolutionException for multiple non-primary") {
        val registry = new BeanRegistry(null)
        val addr1 = new TestAddress()
        val addr2 = new TestAddress()

        registry.register(classOf[ServiceAImpl], addr1)
        registry.register(classOf[ServiceAAlt], addr2)
        registry.freeze()

        val ex = intercept[AmbiguousResolutionException] {
            registry.getBean(classOf[ServiceA])
        }
        assert(ex.requestedType == classOf[ServiceA].getName)
    }

    test("BeanRegistry should support unfreeze and dynamic loading") {
        val registry = new BeanRegistry(null)
        val addr1 = new TestAddress()
        val addr2 = new TestAddress()

        registry.register(classOf[ServiceAImpl], addr1)
        registry.freeze()
        assert(registry.isFrozen)
        assert(registry.count == 1)

        registry.unfreeze()
        assert(!registry.isFrozen)
        registry.register(classOf[ServiceB], addr2)
        registry.freeze()
        assert(registry.isFrozen)
        assert(registry.count == 2)

        assert(registry.getBean(classOf[ServiceA]) eq addr1)
        assert(registry.getBean(classOf[ServiceB]) eq addr2)
    }

    test("BeanRegistry getBean should work during unfreeze using old snapshot") {
        val registry = new BeanRegistry(null)
        val addr1 = new TestAddress()

        registry.register(classOf[ServiceAImpl], addr1)
        registry.freeze()

        registry.unfreeze()
        val result = registry.getBean(classOf[ServiceA])
        assert(result eq addr1)
    }

}
