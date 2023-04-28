package io.otavia.core.util

import io.otavia.core.actor.Actor
import io.otavia.core.log4a.*
import org.scalatest.funsuite.AnyFunSuite

class ClassUtilsSuite extends AnyFunSuite {

    test("show class hierarchy") {
        ClassUtils.printInheritTree(classOf[ConsoleAppender])
        println("")
        ClassUtils.printInheritTree(classOf[Appender])
        println("")
        ClassUtils.printInheritTree(classOf[Actor[Appender.Info | Appender.Warn]])
        assert(true)
    }

}
