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

package io.otavia.core.async

import io.otavia.core.message.Reply
import io.otavia.core.stack.{ReplyWaiter, StackState}
import io.otavia.core.util.TimerService.*

import scala.annotation.experimental
import scala.quoted.*

private[async] class Transformer(val asyncExpr: Expr[Option[StackState]])(using q: Quotes) {

    import q.reflect.*

    private val template = '{
        final class TemplateState() extends StackState {

            private var b: String       = _
            def get_b(): String         = this.b
            def set_b(bx: String): Unit = b = bx

            override def receive(reply: Reply): Unit = {}
            override def resumable(): Boolean        = true
        }
    }.asTerm match
        case Inlined(_, _, Block(stats, ret)) => stats.head.asInstanceOf[ClassDef]

    @experimental
    private def generateClassDef(name: String): q.reflect.ClassDef = {
        import q.reflect.*
        val parents = List(TypeTree.of[Object], TypeTree.of[StackState])
        // ("a", TypeTree.of[Int]),
        val vals = List(("a", TypeTree.of[Int | Null]), ("b", TypeTree.of[String | Null]))

        val decls = (cls: Symbol) =>
            // vals.map(va => Symbol.newMethod(cls, va._1 + "_=", MethodType(List(va._1 + "x"))(_ => List(va._2.tpe), _ => TypeRepr.of[Unit]))) ++
            vals.map(va => Symbol.newMethod(cls, "get_" + va._1, MethodType(Nil)(_ => Nil, _ => va._2.tpe))) ++
                vals.map(va =>
                    Symbol.newMethod(
                      cls,
                      "set_" + va._1,
                      MethodType(List(va._1 + "x"))(_ => List(va._2.tpe), _ => TypeRepr.of[Unit])
                    )
                ) ++
                vals.map(va =>
                    Symbol.newVal(
                      cls,
                      va._1,
                      va._2.tpe,
                      Flags.Mutable | Flags.Private | Flags.Local | Flags.PrivateLocal,
                      Symbol.noSymbol
                    )
                ) :+
                Symbol.newMethod(
                  cls,
                  "receive",
                  MethodType(List("reply"))(_ => List(TypeRepr.of[Reply]), _ => TypeRepr.of[Unit])
                ) :+
                Symbol.newMethod(cls, "resumable", MethodType(Nil)(_ => Nil, _ => TypeRepr.of[Boolean]))

        val clzSym        = Symbol.newClass(Symbol.spliceOwner, name, parents.map(_.tpe), decls, selfType = None)
        val receiveSymbol = clzSym.declaredMethod("receive").head
        val resumeSymbol  = clzSym.declaredMethod("resumable").head

        val resumeDef  = DefDef(resumeSymbol, argss => Some('{ true }.asTerm))
        val receiveDef = DefDef(receiveSymbol, argss => Some('{}.asTerm))

        val valDef = vals.map(va => ValDef(clzSym.declaredField(va._1), Some('{ null }.asTerm)))

        // val varDef = vals.map { va => DefDef(clzSym.declaredMethod(va._1 + "_=").head, argss => Some('{ (): Unit }.asTerm)) }
        val getters = vals.map(va =>
            DefDef(
              clzSym.declaredMethod("get_" + va._1).head,
              argss => Some(Select(This(clzSym), clzSym.declaredField(va._1)))
            )
        )
        val setters = vals.map { va =>
            DefDef(
              clzSym.declaredMethod("set_" + va._1).head,
              argss => {
                  val arg    = argss
                  val select = Select(This(clzSym), clzSym.declaredField(va._1))
                  val rhs    = argss.head.head.asInstanceOf[Term]
                  Some(Assign(select, rhs))
              }
            )
        }

        val clsDef = ClassDef(clzSym, parents, body = valDef :+ receiveDef :+ resumeDef)

        clsDef
    }

    @experimental
    private def generateMyClass(): List[q.reflect.Statement] = {
        val name: String = "myClass"
        val parents      = List(TypeTree.of[AnyRef])
        def decls(cls: Symbol): List[Symbol] =
            List(Symbol.newMethod(cls, "foo", MethodType(Nil)(_ => Nil, _ => TypeRepr.of[Unit])))

        val cls    = Symbol.newClass(Symbol.spliceOwner, name, parents = parents.map(_.tpe), decls, selfType = None)
        val fooSym = cls.declaredMethod("foo").head

        val fooDef = DefDef(fooSym, argss => Some('{ println(s"Calling foo") }.asTerm))
        val clsDef = ClassDef(cls, parents, body = List(fooDef))

        val newCls = Typed(Apply(Select(New(TypeIdent(cls)), cls.primaryConstructor), Nil), TypeTree.of[AnyRef])

        val stclz = template match
            case ClassDef(name, defdef, lists, valdefs, stats) =>
                for (stat <- stats) {
                    stat match
                        case valdef @ ValDef(varname, typetree, vbody) =>
                            val sb     = valdef.symbol
                            val sbflag = sb.flags.show
                            vbody match
                                case Some(underscore: Ident) =>
                                    val under = underscore.underlying
                                    println("")
                                case Some(value) =>
                                    println("")
                                case None =>
                            println("")
                        case defdef @ DefDef(name, params, etp, optRhs) =>
                            val sb     = defdef.symbol
                            val sbflag = sb.flags.show
                            optRhs match
                                case Some(Assign(lhs, mbody)) =>
                                    println("")
                                case Some(rhs) =>
                                    println("")
                                case None =>
                }

                val b = ValDef(
                  Symbol.newVal(Symbol.spliceOwner, "b", TypeRepr.of[String], Flags.Mutable, Symbol.spliceOwner),
                  None
                )
                ClassDef.copy(template)("TestState", defdef, lists, valdefs, b +: stats)

        val stObj = ValDef(
          Symbol.newVal(Symbol.spliceOwner, "kkk", TypeTree.ref(stclz.symbol).tpe, Flags.Mutable, Symbol.spliceOwner),
          Some(
            Typed(
              Apply(Select(New(TypeIdent(stclz.symbol)), stclz.symbol.primaryConstructor), Nil),
              TypeTree.ref(stclz.symbol)
            )
          )
        )

        // report.info(stclz.show(using Printer.TreeStructure))
        // report.info(stclz.asExpr.show)

        List(stclz, stObj, clsDef, newCls)
    }

    @experimental
    def transform: Expr[Option[StackState]] = {
        val asyncAst = asyncExpr.asTerm
        val gen      = this.generateMyClass()
        asyncAst match
            case term @ Inlined(call, binding, Block(stats, ret)) =>
                val injectClz = this.generateClassDef("InjectedState")
                val injectObj = ValDef(
                  Symbol.newVal(
                    Symbol.spliceOwner,
                    "injectState",
                    TypeTree.ref(injectClz.symbol).tpe,
                    Flags.Final,
                    Symbol.noSymbol
                  ),
                  Some(
                    Typed(
                      Apply(Select(New(TypeIdent(injectClz.symbol)), injectClz.symbol.primaryConstructor), Nil),
                      TypeTree.ref(injectClz.symbol)
                    )
                  )
                )
                val ass = Apply(Select.unique(Ident(injectObj.symbol.termRef), "set_a"), List('{ 1 }.asTerm))

                val askTp    = TypeTree.of[FixTime]
                val replyTp  = TypeTree.of[TimeArrival]
                val waitertp = TypeTree.of[ReplyWaiter]

                val applied = waitertp.tpe.appliedTo(List(askTp.tpe, replyTp.tpe))

                TypeTree.ref(askTp.tpe.typeSymbol)

                // Select.overloaded(New(waitertp))
                // TypeApply()
                val waiter = ValDef(
                  Symbol.newVal(
                    Symbol.spliceOwner,
                    "waiter",
                    TypeRepr.of[ReplyWaiter[TimeArrival]],
                    Flags.Final,
                    Symbol.noSymbol
                  ),
                  Some(Select.overloaded(New(TypeTree.of[ReplyWaiter]), "<init>", List(replyTp.tpe), Nil))
                )

                val prt = '{ println(${ Ident(waiter.symbol.termRef).asExprOf[Any] }) }.asTerm

                val injectStats = List(injectClz, injectObj, ass, waiter, prt)
                val blk         = Block(injectStats ++ stats, ret)
                val inle        = Inlined(call, binding, blk)
                report.info(inle.show(using Printer.TreeStructure))
                val expr = inle.asExprOf[Option[StackState]]
                expr

            case _ => asyncExpr

    }

}
