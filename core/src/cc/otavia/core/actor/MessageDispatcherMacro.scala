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

import cc.otavia.core.message.*
import cc.otavia.core.stack.*
import scala.quoted.*

object MessageDispatcherMacro {

    def deriveDispatchImpl[M <: Call: Type](using quotes: Quotes): Expr[Unit] = {
        import quotes.reflect.*

        // ==== Step 1: Find enclosing class ====
        def findEnclosingClass(sym: Symbol): Option[Symbol] =
            if sym.isClassDef then Some(sym)
            else if sym == Symbol.noSymbol then None
            else findEnclosingClass(sym.owner)

        val classSym = findEnclosingClass(Symbol.spliceOwner).getOrElse(
            report.errorAndAbort("deriveDispatch must be called inside an actor class body")
        )

        // ==== Step 2: Decompose M into union members ====
        def decomposeUnion(tpe: TypeRepr): List[TypeRepr] = tpe match
            case OrType(lhs, rhs) => decomposeUnion(lhs) ++ decomposeUnion(rhs)
            case _                => List(tpe)

        val mTypeRepr     = TypeRepr.of[M].dealias
        val members       = decomposeUnion(mTypeRepr)
        val askMembers    = members.filter(_ <:< TypeRepr.of[Ask[? <: Reply]])
        val noticeMembers = members.filter(_ <:< TypeRepr.of[Notice])

        // ==== Step 3: Discover handler methods ====
        // Get the full parameter type from the ValDef's tpt.tpe, which gives
        // the exact source-level type without needing @experimental methodSym.info.
        case class HandlerInfo(symbol: Symbol, msgType: TypeRepr, fullArgType: TypeRepr, isAsk: Boolean)

        val askStackSym   = TypeRepr.of[AskStack[?]].typeSymbol
        val noticeStackSym = TypeRepr.of[NoticeStack[?]].typeSymbol

        // Extraction: parameter must be AskStack[T] / NoticeStack[N] AND
        // return type must be StackYield.
        def paramTypeOf(methodSym: Symbol): Option[(TypeRepr, TypeRepr, Boolean)] =
            val paramSymOpt = methodSym.paramSymss match
                case List(List(p)) => Some(p)
                case _             => None
            // also extract return type from DefDef
            val returnType: TypeRepr = methodSym.tree match
                case DefDef(_, _, ret, _) => ret.tpe
                case _                    => TypeRepr.of[Nothing]
            for
                paramSym <- paramSymOpt
                if returnType <:< TypeRepr.of[StackYield]
                vd <- paramSym.tree match
                    case v: ValDef => Some(v)
                    case _         => None
            yield
                val fullType = vd.tpt.tpe  // e.g. AskStack[Hello]
                val (msgType, isAsk) = fullType match
                    case AppliedType(base, List(t)) if base.typeSymbol == askStackSym =>
                        (t, true)
                    case AppliedType(base, List(n)) if base.typeSymbol == noticeStackSym =>
                        (n, false)
                    case _ => (TypeRepr.of[Nothing], false)
                (msgType, fullType, isAsk)

        val handlerInfos = classSym.methodMembers.flatMap { methodSym =>
            if !methodSym.isDefDef then Nil
            else paramTypeOf(methodSym) match
                case Some((msgType, fullType, isAsk)) if fullType.typeSymbol != TypeRepr.of[Nothing].typeSymbol =>
                    List(HandlerInfo(methodSym, msgType, fullType, isAsk))
                case _ => Nil
        }
        val askHandlers    = handlerInfos.filter(_.isAsk)
        val noticeHandlers = handlerInfos.filter(!_.isAsk)

        // Filter out inherited methods: only keep handlers whose msgType is a union member
        val ownAskHandlers    = askHandlers.filter(h => members.exists(_ =:= h.msgType))
        val ownNoticeHandlers = noticeHandlers.filter(h => members.exists(_ =:= h.msgType))

        // ==== Step 4: Exhaustiveness verification ====
        val unhandledAsks = askMembers.filterNot(m => ownAskHandlers.exists(_.msgType =:= m))
        if unhandledAsks.nonEmpty then
            report.errorAndAbort(
                s"Missing ask handler(s) for: ${unhandledAsks.map(_.show).mkString(", ")}. " +
                    s"Define a method with parameter type AskStack[T] for each."
            )
        val unhandledNotices = noticeMembers.filterNot(m => ownNoticeHandlers.exists(_.msgType =:= m))
        if unhandledNotices.nonEmpty then
            report.errorAndAbort(
                s"Missing notice handler(s) for: ${unhandledNotices.map(_.show).mkString(", ")}. " +
                    s"Define a method with parameter type NoticeStack[N] for each."
            )

        // ==== Step 5: Generate dispatch code ====
        def argToTerm(args: List[Tree]): Term = args.head.asInstanceOf[Term]

        // For each handler, create a local helper def that bridges the type gap:
        //   def helper$name(s: Any): StackYield = this.handlerName(s.asInstanceOf[FullArgType])
        // The helper is referenced by Ref(helperSym) inside the if-else chain.
        val allHandlers = ownAskHandlers ++ ownNoticeHandlers
        val helperSyms  = allHandlers.map { h =>
            val sym = Symbol.newMethod(
                Symbol.spliceOwner,
                h.symbol.name + "$dispatch",
                MethodType(List("s"))(_ => List(TypeRepr.of[Any]), _ => TypeRepr.of[StackYield])
            )
            h -> sym
        }.toMap

        val allDefs  = List.newBuilder[Definition]
        val allTerms = List.newBuilder[Term]

        for (h, hsym) <- helperSyms do
            allDefs += DefDef(hsym, { case List(List(s)) =>
                val st   = s.asInstanceOf[Term]
                val cast = TypeApply(Select.unique(st, "asInstanceOf"), List(Inferred(h.fullArgType)))
                Some(Apply(Select.unique(This(classSym), h.symbol.name), List(cast)))
            })

        if ownAskHandlers.nonEmpty then
            val askStackType = TypeRepr.of[AskStack[M & Ask[? <: Reply]]]
            val lambda = Lambda(
                Symbol.spliceOwner,
                MethodType("stack" :: Nil)(_ => List(askStackType), _ => TypeRepr.of[StackYield]),
                { (_, args) =>
                    val stackTerm = argToTerm(args)
                    ownAskHandlers.foldRight[Term](
                        '{ throw NotImplementedError("unhandled ask") }.asTerm
                    ) { (h, elseBr) =>
                        val cond = TypeApply(
                            Select.unique(Select.unique(stackTerm, "ask"), "isInstanceOf"),
                            List(Inferred(h.msgType))
                        )
                        val call = Apply(
                            Ref(helperSyms(h)),
                            List(TypeApply(Select.unique(stackTerm, "asInstanceOf"),
                                List(Inferred(TypeRepr.of[Any]))))
                        )
                        If(cond, call, elseBr)
                    }
                }
            )
            allTerms += Apply(Select.unique(This(classSym), "setAskDispatch"), List(lambda))

        if ownNoticeHandlers.nonEmpty then
            val noticeStackType = TypeRepr.of[NoticeStack[M & Notice]]
            val lambda = Lambda(
                Symbol.spliceOwner,
                MethodType("stack" :: Nil)(_ => List(noticeStackType), _ => TypeRepr.of[StackYield]),
                { (_, args) =>
                    val stackTerm = argToTerm(args)
                    ownNoticeHandlers.foldRight[Term](
                        '{ throw NotImplementedError("unhandled notice") }.asTerm
                    ) { (h, elseBr) =>
                        val cond = TypeApply(
                            Select.unique(Select.unique(stackTerm, "notice"), "isInstanceOf"),
                            List(Inferred(h.msgType))
                        )
                        val call = Apply(
                            Ref(helperSyms(h)),
                            List(TypeApply(Select.unique(stackTerm, "asInstanceOf"),
                                List(Inferred(TypeRepr.of[Any]))))
                        )
                        If(cond, call, elseBr)
                    }
                }
            )
            allTerms += Apply(Select.unique(This(classSym), "setNoticeDispatch"), List(lambda))

        val defs  = allDefs.result()
        val terms = allTerms.result()
        if defs.isEmpty && terms.isEmpty then
            report.errorAndAbort(
                "deriveDispatch found no handler methods. " +
                    "Define methods with AskStack[T] or NoticeStack[N] parameters."
            )

        report.info(
            s"deriveDispatch: ${ownAskHandlers.size} ask handler(s), ${ownNoticeHandlers.size} notice handler(s) for ${classSym.name}",
            Position.ofMacroExpansion
        )

        Block(defs ++ terms, '{ () }.asTerm).asExprOf[Unit]
    }

}
