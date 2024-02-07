/*
 * Copyright 2022 Yan Kun <yan_kun_1992@foxmail.com>
 *
 * This file fork from jsoniter-scala.
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

package cc.otavia.json

import scala.quoted.*
import scala.collection.mutable
import scala.reflect.ClassTag
import java.math.MathContext
import scala.language.unsafeNulls
import java.util.concurrent.ConcurrentHashMap
import cc.otavia.serde.annotation.rename
import cc.otavia.serde.annotation.stringfield
import cc.otavia.serde.annotation.ignore
import cc.otavia.buffer.Buffer
import cc.otavia.json.types.StringJsonSerde
import java.math.BigInteger
import java.util.Currency
import scala.concurrent.duration.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale
import java.time.LocalTime
import cc.otavia.datatype.Money
import java.time.MonthDay
import java.time.OffsetDateTime
import java.time.OffsetTime
import java.time.Period
import java.util.UUID
import java.time.Year
import java.time.YearMonth
import java.time.ZonedDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import scala.collection.immutable

object JsonMacro {

    def derivedMacro[A: Type](using quotes: Quotes): Expr[JsonSerde[A]] = {
        import quotes.reflect.*

        def fail(msg: String): Nothing = report.errorAndAbort(msg, Position.ofMacroExpansion)

        def warn(msg: String): Unit = report.warning(msg, Position.ofMacroExpansion)

        def typeArgs(tpe: TypeRepr): List[TypeRepr] = tpe match
            case AppliedType(_, typeArgs) => typeArgs.map(_.dealias)
            case _                        => Nil

        def typeArg1(tpe: TypeRepr): TypeRepr = typeArgs(tpe).head

        def typeArg2(tpe: TypeRepr): TypeRepr = typeArgs(tpe).tail.head

        def isTuple(tpe: TypeRepr): Boolean = tpe <:< TypeRepr.of[Tuple]

        def valueClassValueSymbol(tpe: TypeRepr): Symbol = tpe.typeSymbol.fieldMembers(0)

        def valueClassValueType(tpe: TypeRepr): TypeRepr = tpe.memberType(tpe.typeSymbol.fieldMembers(0)).dealias

        def isNonAbstractScalaClass(tpe: TypeRepr): Boolean = tpe.classSymbol.fold(false) { sym =>
            val flags = sym.flags
            !flags.is(Flags.Abstract) && !flags.is(Flags.JavaDefined) && !flags.is(Flags.Trait)
        }

        def isSealedClass(tpe: TypeRepr): Boolean = tpe.typeSymbol.flags.is(Flags.Sealed)

        def isConstType(tpe: TypeRepr): Boolean = tpe match
            case ConstantType(_) => true
            case _               => false

        def isEnumOrModuleValue(tpe: TypeRepr): Boolean = tpe.isSingleton &&
            (tpe.typeSymbol.flags.is(Flags.Module) || tpe.termSymbol.flags.is(Flags.Enum))

        def adtChildren(tpe: TypeRepr): Seq[TypeRepr] = { // TODO: explore yet one variant with mirrors
            def resolveParentTypeArg(
                child: Symbol,
                fromNudeChildTarg: TypeRepr,
                parentTarg: TypeRepr,
                binding: Map[String, TypeRepr]
            ): Map[String, TypeRepr] = {
                if (fromNudeChildTarg.typeSymbol.isTypeParam) { // TODO: check for paramRef instead ?
                    val paramName = fromNudeChildTarg.typeSymbol.name
                    binding.get(paramName) match
                        case None => binding.updated(paramName, parentTarg)
                        case Some(oldBinding) =>
                            if (oldBinding =:= parentTarg) binding
                            else
                                fail(
                                  s"Type parameter $paramName in class ${child.name} appeared in the constructor of " +
                                      s"${tpe.show} two times differently, with ${oldBinding.show} and ${parentTarg.show}"
                                )
                } else if (fromNudeChildTarg <:< parentTarg)
                    binding // TODO: assure parentTag is covariant, get covariance from type parameters
                else {
                    (fromNudeChildTarg, parentTarg) match
                        case (AppliedType(ctycon, ctargs), AppliedType(ptycon, ptargs)) =>
                            ctargs.zip(ptargs).foldLeft(resolveParentTypeArg(child, ctycon, ptycon, binding)) {
                                (b, e) =>
                                    resolveParentTypeArg(child, e._1, e._2, b)
                            }
                        case _ =>
                            fail(
                              s"Failed unification of type parameters of ${tpe.show} from child $child - " +
                                  s"${fromNudeChildTarg.show} and ${parentTarg.show}"
                            )
                }
            }

            def resolveParentTypeArgs(
                child: Symbol,
                nudeChildParentTags: List[TypeRepr],
                parentTags: List[TypeRepr],
                binding: Map[String, TypeRepr]
            ): Map[String, TypeRepr] = {
                nudeChildParentTags
                    .zip(parentTags)
                    .foldLeft(binding)((s, e) => resolveParentTypeArg(child, e._1, e._2, s))
            }

            tpe.typeSymbol.children.map { sym =>
                if (sym.isType) {
                    if (
                      sym.name == "<local child>"
                    ) // problem - we have no other way to find this other return the name
                        fail(
                          s"Local child symbols are not supported, please consider change '${tpe.show}' or implement a " +
                              "custom implicitly accessible codec"
                        )
                    val nudeSubtype      = TypeIdent(sym).tpe
                    val tpeArgsFromChild = typeArgs(nudeSubtype.baseType(tpe.typeSymbol))
                    nudeSubtype.memberType(sym.primaryConstructor) match
                        case MethodType(_, _, resTp) => resTp
                        case PolyType(names, bounds, resPolyTp) =>
                            val targs      = typeArgs(tpe)
                            val tpBindings = resolveParentTypeArgs(sym, tpeArgsFromChild, targs, Map.empty)
                            val ctArgs = names.map { name =>
                                tpBindings.getOrElse(
                                  name,
                                  fail(
                                    s"Type parameter $name of $sym can't be deduced from " +
                                        s"type arguments of ${tpe.show}. Please provide a custom implicitly accessible codec for it."
                                  )
                                )
                            }
                            val polyRes = resPolyTp match
                                case MethodType(_, _, resTp) => resTp
                                case other                   => other
                            if (ctArgs.isEmpty) polyRes
                            else
                                polyRes match
                                    case AppliedType(base, _) => base.appliedTo(ctArgs)
                                    case AnnotatedType(AppliedType(base, _), annot) =>
                                        AnnotatedType(base.appliedTo(ctArgs), annot)
                        case other =>
                            fail(s"Primary constructior for ${tpe.show} is not MethodType or PolyType but $other")
                } else if (sym.isTerm) Ref(sym).tpe
                else
                    fail(
                      "Only Scala classes & objects are supported for ADT leaf classes. Please consider using of " +
                          s"them for ADT with base '${tpe.show}' or provide a custom implicitly accessible codec for the ADT base. " +
                          s"Failed symbol: $sym (fullName=${sym.fullName})\n"
                    )
            }
        }

        def adtLeafClasses(adtBaseTpe: TypeRepr): Seq[TypeRepr] = {
            def collectRecursively(tpe: TypeRepr): Seq[TypeRepr] = {
                val leafTpes = adtChildren(tpe).flatMap { subTpe =>
                    if (isEnumOrModuleValue(subTpe) || subTpe =:= TypeRepr.of[None.type]) subTpe :: Nil
                    else if (isSealedClass(subTpe)) collectRecursively(subTpe)
                    else if (isNonAbstractScalaClass(subTpe)) subTpe :: Nil
                    else {
                        if (subTpe.typeSymbol.flags.is(Flags.Abstract) || subTpe.typeSymbol.flags.is(Flags.Trait))
                            fail(
                              "Only sealed intermediate traits or abstract classes are supported. Please consider using of them " +
                                  s"for ADT with base '${adtBaseTpe.show}' or provide a custom implicitly accessible codec for the ADT base."
                            )
                        else
                            fail(
                              "Only Scala classes & objects are supported for ADT leaf classes. Please consider using of them " +
                                  s"for ADT with base '${adtBaseTpe.show}' or provide a custom implicitly accessible codec for the ADT base."
                            )
                    }
                }
                if (isNonAbstractScalaClass(tpe)) leafTpes :+ tpe else leafTpes
            }
            val classes = collectRecursively(adtBaseTpe).distinct
            if (classes.isEmpty)
                fail(
                  s"Cannot find leaf classes for ADT base '${adtBaseTpe.show}'. " +
                      "Please add them or provide a custom implicitly accessible codec for the ADT base."
                )
            classes
        }

        def isOption(tpe: TypeRepr, types: List[TypeRepr]): Boolean = tpe <:< TypeRepr.of[Option[?]] &&
            !types.headOption.exists(_ <:< TypeRepr.of[Option[?]])

        def isCollection(tpe: TypeRepr): Boolean =
            tpe <:< TypeRepr.of[Iterable[?]] || tpe <:< TypeRepr.of[Iterator[?]] ||
                tpe <:< TypeRepr.of[Array[?]] || tpe.typeSymbol.fullName == "scala.IArray$package$.IArray"

        def isJavaEnum(tpe: TypeRepr): Boolean = tpe <:< TypeRepr.of[java.lang.Enum[?]]

        def scalaCollectionCompanion(tpe: TypeRepr): Term = if (tpe.typeSymbol.fullName.startsWith("scala.collection."))
            Ref(tpe.typeSymbol.companionModule)
        else fail(s"Unsupported type '${tpe.show}'. Please consider using a custom implicitly accessible codec for it.")

        def scalaCollectionEmptyNoArgs(cTpe: TypeRepr, eTpe: TypeRepr): Term =
            TypeApply(Select.unique(scalaCollectionCompanion(cTpe), "empty"), List(Inferred(eTpe)))

        def scalaMapEmptyNoArgs(cTpe: TypeRepr, kTpe: TypeRepr, vTpe: TypeRepr): Term =
            TypeApply(Select.unique(scalaCollectionCompanion(cTpe), "empty"), List(Inferred(kTpe), Inferred(vTpe)))

        def scala2EnumerationObject(tpe: TypeRepr): Expr[Enumeration] = tpe match
            case TypeRef(ct, _) => Ref(ct.termSymbol).asExprOf[Enumeration]

        def findScala2EnumerationById[C <: AnyRef: Type](tpe: TypeRepr, i: Expr[Int])(using Quotes): Expr[Option[C]] =
            '{ ${ scala2EnumerationObject(tpe) }.values.iterator.find(_.id == $i) }.asExprOf[Option[C]]

        def findScala2EnumerationByName[C <: AnyRef: Type](tpe: TypeRepr, name: Expr[String])(using
            Quotes
        ): Expr[Option[C]] =
            '{ ${ scala2EnumerationObject(tpe) }.values.iterator.find(_.toString() == $name) }.asExprOf[Option[C]]

        def genNewArray[T: Type](size: Expr[Int])(using Quotes): Expr[Array[T]] =
            Apply(
              TypeApply(
                Select(New(TypeIdent(defn.ArrayClass)), defn.ArrayClass.primaryConstructor),
                List(TypeTree.of[T])
              ),
              List(size.asTerm)
            ).asExprOf[Array[T]]

        val rootType          = TypeRepr.of[A].dealias
        val inferredOrderings = mutable.Map.empty[TypeRepr, Term]
        val inferredSerde     = mutable.Map.empty[TypeRepr, Option[Expr[JsonSerde[?]]]]
        val classTags         = mutable.Map.empty[TypeRepr, ValDef]

        def summonOrdering(tpe: TypeRepr): Term = inferredOrderings.getOrElseUpdate(
          tpe, {
              tpe.asType match
                  case '[t] => Expr.summon[Ordering[t]].fold(fail(s"Can't summon Ordering[${tpe.show}]"))(_.asTerm)
          }
        )

        def symbol(name: String, tpe: TypeRepr, flags: Flags = Flags.EmptyFlags): Symbol =
            Symbol.newVal(Symbol.spliceOwner, name, tpe, flags, Symbol.noSymbol)

        def summonClassTag(tpe: TypeRepr): Term = Ref(
          classTags
              .getOrElseUpdate(
                tpe, {
                    tpe.asType match
                        case '[t] =>
                            val sym = symbol("ct" + classTags.size, TypeRepr.of[ClassTag[t]])
                            val ct =
                                Expr.summon[ClassTag[t]].fold(fail(s"Can't summon ClassTag[${tpe.show}]"))(_.asTerm)
                            ValDef(sym, Some(ct.changeOwner(sym)))
                }
              )
              .symbol
        )

        def inferImplicitValue[T: Type](typeToSearch: TypeRepr): Option[Expr[T]] = Implicits.search(typeToSearch) match
            case v: ImplicitSearchSuccess => Some(v.tree.asExprOf[T])
            case _                        => None

        def checkRecursionInTypes(types: List[TypeRepr]): Unit = {
            val tpe          = types.head
            val nestedTypes  = types.tail
            val recursiveIdx = nestedTypes.indexOf(tpe)
            if (recursiveIdx >= 0) {
                val recTypes = nestedTypes.take(recursiveIdx + 1).map(_.show).reverse.mkString("'", "', '", "'")
                fail(s"Recursive type(s) detected: $recTypes. Please consider using a custom implicitly ")
            }
        }

        def findImplicitSerde(tpe: TypeRepr): Option[Expr[JsonSerde[?]]] = {
            if (tpe =:= rootType) None
            else
                inferredSerde.getOrElseUpdate(
                  tpe, {
                      inferImplicitValue[JsonSerde[?]](TypeRepr.of[JsonSerde].appliedTo(tpe))
                  }
                )
        }

        val mathContexts = new mutable.LinkedHashMap[Int, ValDef]

        def withMathContextFor(precision: Int): Expr[MathContext] = {
            if (precision == MathContext.DECIMAL128.getPrecision) '{ (MathContext.DECIMAL128: java.math.MathContext) }
            else if (precision == MathContext.DECIMAL64.getPrecision) '{
                (MathContext.DECIMAL64: java.math.MathContext)
            }
            else if (precision == MathContext.DECIMAL32.getPrecision) '{
                (MathContext.DECIMAL32: java.math.MathContext)
            }
            else if (precision == MathContext.UNLIMITED.getPrecision) '{
                (MathContext.UNLIMITED: java.math.MathContext)
            }
            else
                Ref(
                  mathContexts
                      .getOrElseUpdate(
                        precision, {
                            val sym = symbol("mc" + mathContexts.size, TypeRepr.of[MathContext])
                            val rhs = '{ new MathContext(${ Expr(34) }, java.math.RoundingMode.HALF_EVEN) }.asTerm
                            ValDef(sym, Some(rhs.changeOwner(sym)))
                        }
                      )
                      .symbol
                ).asExprOf[MathContext]
        }

        val scalaEnumCaches = new mutable.LinkedHashMap[TypeRepr, ValDef]

        def withScalaEnumCacheFor[K: Type, T: Type](tpe: TypeRepr)(using Quotes): Expr[ConcurrentHashMap[K, T]] = {
            Ref(
              scalaEnumCaches
                  .getOrElseUpdate(
                    tpe, {
                        val sym = symbol("ec" + scalaEnumCaches.size, TypeRepr.of[ConcurrentHashMap[K, T]])
                        ValDef(sym, Some('{ new ConcurrentHashMap[K, T] }.asTerm.changeOwner(sym)))
                    }
                  )
                  .symbol
            ).asExprOf[ConcurrentHashMap[K, T]]
        }

        case class JavaEnumValueInfo(value: Symbol, name: String, transformed: Boolean)

        val javaEnumValueInfos = new mutable.LinkedHashMap[TypeRepr, List[JavaEnumValueInfo]]

        def javaEnumValues(tpe: TypeRepr): List[JavaEnumValueInfo] = javaEnumValueInfos.getOrElseUpdate(
          tpe, {
              val classSym = tpe.classSymbol.getOrElse(fail(s"$tpe is not a class"))
              val values = classSym.children.map { sym =>
                  val name        = sym.name
                  val transformed = name
                  JavaEnumValueInfo(sym, transformed, name != transformed)
              }
              val nameCollisions = values.map(_.name).distinct // TODO:
              // TODO:
              values
          }
        )

        // genReadJavaEnumValue

        case class FieldInfo(
            symbol: Symbol,
            mappedName: String,
            getterOrField: Symbol,
            defaultValue: Option[Term],
            resolvedTpe: TypeRepr,
            isTransient: Boolean,
            isStringified: Boolean,
            nonTransientFieldIndex: Int
        )

        case class ClassInfo(tpe: TypeRepr, primaryConstructor: Symbol, paramLists: List[List[FieldInfo]]) {
            val fields: List[FieldInfo] = paramLists.flatten.filter(!_.isTransient)

            def genNew(arg: Term): Term = genNew(List(List(arg)))

            def genNew(argss: List[List[Term]]): Term = {
                val constructorNoTypes = Select(New(Inferred(tpe)), primaryConstructor)
                val constructor = typeArgs(tpe) match
                    case Nil      => constructorNoTypes
                    case typeArgs => TypeApply(constructorNoTypes, typeArgs.map(t => Inferred(t)))
                argss.tail.foldLeft(Apply(constructor, argss.head))((acc, args) => Apply(acc, args))
            }
        }

        case class FieldAnnotations(partiallyMappedName: String, transient: Boolean, stringfield: Boolean)

        val classInfos = new mutable.LinkedHashMap[TypeRepr, ClassInfo]

        def getPrimaryConstructor(tpe: TypeRepr): Symbol = tpe.classSymbol match
            case Some(sym) if sym.primaryConstructor.exists => sym.primaryConstructor
            case _                                          => fail(s"Cannot find a primary constructor for '$tpe'")

        def hasSupportedAnnotation(m: Symbol): Boolean =
            m.annotations.exists(a =>
                a.tpe =:= TypeRepr.of[rename] || a.tpe =:= TypeRepr.of[ignore] || a.tpe =:= TypeRepr.of[stringfield]
            )

        def supportedTransientTypeNames: String = s"'${Type.show[ignore]}')"

        def namedValueOpt(namedAnnotation: Option[Term], tpe: TypeRepr): Option[String] = namedAnnotation.map {
            case Apply(_, List(param)) =>
                param match
                    case Literal(StringConstant(s)) => s
                    case _ =>
                        fail(s"Cannot evaluate a parameter of the '@rename' annotation in type '${tpe.show}': $param.")
            case a => fail(s"Invalid rename annotation ${a.show}")
        }

        def getClassInfo(tpe: TypeRepr): ClassInfo = classInfos.getOrElseUpdate(
          tpe, {
              val tpeClassSym = tpe.classSymbol.getOrElse(fail(s"Expected that ${tpe.show} has classSymbol"))
              val annotations = tpeClassSym.fieldMembers.collect {
                  case m: Symbol if hasSupportedAnnotation(m) =>
                      val name  = m.name
                      val named = m.annotations.filter(_.tpe =:= TypeRepr.of[rename])
                      if (named.size > 1)
                          fail(s"Duplicated '${TypeRepr.of[rename].show}' defined for '$name' of '${tpe.show}'.")
                      val trans = m.annotations.filter(_.tpe =:= TypeRepr.of[ignore])
                      if (trans.size > 1)
                          warn(s"Duplicated $supportedTransientTypeNames defined for '$name' of '${tpe.show}'.")
                      val strings = m.annotations.filter(_.tpe =:= TypeRepr.of[stringfield])
                      if (strings.size > 1)
                          warn(s"Duplicated '${TypeRepr.of[stringfield].show}' defined for '$name' of '${tpe.show}'.")
                      if ((named.nonEmpty || strings.nonEmpty) && trans.nonEmpty)
                          warn(
                            s"Both $supportedTransientTypeNames and '${Type.show[rename]}' or " +
                                s"$supportedTransientTypeNames and '${Type.show[stringfield]}' defined for '$name' of '${tpe.show}'."
                          )
                      val partiallyMappedName = namedValueOpt(named.headOption, tpe).getOrElse(name)
                      (name, FieldAnnotations(partiallyMappedName, trans.nonEmpty, strings.nonEmpty))
              }.toMap
              val primaryConstructor = getPrimaryConstructor(tpe)

              def createFieldInfos(
                  params: List[Symbol],
                  typeParams: List[Symbol],
                  fieldIndex: Boolean => Int
              ): List[FieldInfo] = {
                  var i = 0
                  params.map { symbol =>
                      i += 1
                      val name             = symbol.name
                      val annotationOption = annotations.get(name)
                      val mappedName       = annotationOption.fold(name)(_.partiallyMappedName)
                      val field            = tpeClassSym.fieldMember(name)
                      val getterOrField = if (field.exists) {
                          if (field.flags.is(Flags.PrivateLocal))
                              fail(
                                s"Field '$name' in class '${tpe.show}' is private. " +
                                    "It should be defined as 'val' or 'var' in the primary constructor."
                              )
                          field
                      } else {
                          val getters = tpeClassSym
                              .methodMember(name)
                              .filter(_.flags.is(Flags.CaseAccessor | Flags.FieldAccessor | Flags.ParamAccessor))
                          if (getters.isEmpty) { // Scala3 doesn't set FieldAccess flag for val parameters of constructor
                              val namedMembers = tpeClassSym.methodMember(name).filter(_.paramSymss == Nil)
                              if (namedMembers.isEmpty)
                                  fail(
                                    s"Field and getter not found: '$name' parameter of '${tpe.show}' " +
                                        s"should be defined as 'val' or 'var' in the primary constructor."
                                  )
                              namedMembers.head.privateWithin match
                                  case None => namedMembers.head
                                  case _ =>
                                      fail(
                                        s"Getter is private: '$name' paramter of '${tpe.show}' should be defined " +
                                            "as 'val' or 'var' in the primary constructor."
                                      )
                          } else getters.head
                      }
                      val defaultValue: Option[Term] = None
                      val isStringified              = annotationOption.exists(_.stringfield)
                      val isTransient                = annotationOption.exists(_.transient)
                      val originFieldType            = tpe.memberType(symbol).dealias
                      val fieldType = typeArgs(tpe) match
                          case Nil      => originFieldType
                          case typeArgs => originFieldType.substituteTypes(typeParams, typeArgs)
                      fieldType match
                          case TypeLambda(_, _, _) =>
                              fail(
                                s"Hight-kinded types are not supported for type ${tpe.show} " +
                                    s"with field type for '$name' (symbol=$symbol) : ${fieldType.show}, originFieldType=" +
                                    s"${originFieldType.show}, constructor typeParams=$typeParams, "
                              )
                          case TypeBounds(_, _) =>
                              fail(
                                s"Type bounds are not supported for type '${tpe.show}' with field " +
                                    s"type for $name '${fieldType.show}'"
                              )
                          case _ =>
                              if (defaultValue.isDefined && !(defaultValue.get.tpe <:< fieldType)) {
                                  fail(
                                    "Polymorphic expression cannot be instantiated to expected type: default value for " +
                                        s"field $symbol of class ${tpe.show} have type ${defaultValue.get.tpe.show} but field type " +
                                        s"is ${fieldType.show}"
                                  )
                              }
                      FieldInfo(
                        symbol,
                        mappedName,
                        getterOrField,
                        defaultValue,
                        fieldType,
                        isTransient,
                        isStringified,
                        fieldIndex(isTransient)
                      )
                  }
              }

              def isTypeParamsList(symbols: List[Symbol]): Boolean = symbols.exists(_.isTypeParam)

              val fieldIndex: Boolean => Int = {
                  var i = -1
                  (isTransient: Boolean) => {
                      if (!isTransient) i += 1
                      i
                  }
              }

              val paramLists = primaryConstructor.paramSymss match
                  case tps :: ps :: Nil if isTypeParamsList(tps) => createFieldInfos(ps, tps, fieldIndex) :: Nil
                  case tps :: pss if isTypeParamsList(tps)       => pss.map(ps => createFieldInfos(ps, tps, fieldIndex))
                  case pss                                       => pss.map(ps => createFieldInfos(ps, Nil, fieldIndex))

              ClassInfo(tpe, primaryConstructor, paramLists)
          }
        )

        def isValueClass(tpe: TypeRepr): Boolean = !isConstType(tpe) &&
            (isNonAbstractScalaClass(tpe) && !isCollection(tpe) && getClassInfo(tpe).fields.size == 1) ||
            tpe <:< TypeRepr.of[AnyVal]

        case class DecoderMethodKey(tpe: TypeRepr, isStringified: Boolean, useDiscriminator: Boolean)
        val decodeMethodSyms = new mutable.HashMap[DecoderMethodKey, Symbol]
        val decodeMethodDefs = new mutable.ArrayBuffer[DefDef]

        case class EncoderMethodKey(
            tpe: TypeRepr,
            isStringified: Boolean,
            discriminatorKeyValue: Option[(String, String)]
        )
        val encodeMethodSyms = new mutable.HashMap[EncoderMethodKey, Symbol]
        val encodeMethodDefs = new mutable.ArrayBuffer[DefDef]

        def withDecoderFor[T: Type](methodKey: DecoderMethodKey, in: Expr[Buffer])(
            f: (Expr[Buffer]) => Expr[T]
        )(using Quotes): Expr[T] = {
            val sym =
                if (decodeMethodSyms.contains(methodKey)) decodeMethodSyms(methodKey)
                else {
                    val sym = Symbol.newMethod(
                      Symbol.spliceOwner,
                      "d" + decodeMethodSyms.size,
                      MethodType(List("in"))(_ => List(TypeRepr.of[Buffer]), _ => TypeRepr.of[T])
                    )
                    decodeMethodSyms.update(methodKey, sym)
                    decodeMethodDefs += DefDef(
                      sym,
                      params => {
                          val List(List(in)) = params
                          Some(f(in.asExprOf[Buffer]).asTerm.changeOwner(sym))
                      }
                    )
                    sym
                }
            Apply(Ref(sym), List(in.asTerm)).asExprOf[T]
        }

        def withEncoderFor[T: Type](methodKey: EncoderMethodKey, arg: Expr[T], out: Expr[Buffer])(
            f: (Expr[T], Expr[Buffer]) => Expr[Unit]
        ): Expr[Unit] = {
            val sym =
                if (encodeMethodSyms.contains(methodKey)) encodeMethodSyms(methodKey)
                else {
                    val sym = Symbol.newMethod(
                      Symbol.spliceOwner,
                      "e" + encodeMethodSyms.size,
                      MethodType(List("value", "out"))(
                        _ => List(TypeRepr.of[T], TypeRepr.of[Buffer]),
                        _ => TypeRepr.of[Unit]
                      )
                    )
                    encodeMethodSyms.update(methodKey, sym)
                    encodeMethodDefs += DefDef(
                      sym,
                      params => {
                          val List(List(value, out)) = params
                          Some(f(value.asExprOf[T], out.asExprOf[Buffer]).asTerm.changeOwner(sym))
                      }
                    )
                    sym
                }
            Apply(Ref(sym), List(arg.asTerm, out.asTerm)).asExprOf[Unit]
        }

        val stringable = Set(
          TypeRepr.of[Boolean],
          TypeRepr.of[BigDecimal],
          TypeRepr.of[BigInteger],
          TypeRepr.of[BigInt],
          TypeRepr.of[Byte],
          TypeRepr.of[Double],
          TypeRepr.of[Float],
          TypeRepr.of[Int],
          TypeRepr.of[java.math.BigDecimal],
          TypeRepr.of[Long],
          TypeRepr.of[Short]
        )

        def isStringableType(tpe: TypeRepr): Boolean = if (tpe <:< TypeRepr.of[Option[?]])
            isStringableType(typeArg1(tpe))
        else stringable.exists(p => tpe =:= p)

        def genWriteNonAbstractScalaClass[T: Type](x: Expr[T], tpe: TypeRepr, out: Expr[Buffer])(using
            Quotes
        ): Expr[Unit] = {
            val classInfo   = getClassInfo(tpe)
            val writeFields = new mutable.ArrayBuffer[Term](classInfo.fields.length)
            for (fieldInfo <- classInfo.fields if !fieldInfo.isTransient) {
                val fTpe = fieldInfo.resolvedTpe
                fTpe.asType match
                    case '[ft] =>
                        val vterm = Select(x.asTerm, fieldInfo.getterOrField).asExprOf[ft]
                        val term = if (fieldInfo.isStringified && isStringableType(fTpe)) '{
                            JsonHelper.serializeKey(${ Expr(fieldInfo.mappedName) }, $out)
                            $out.writeByte(JsonConstants.TOKEN_DOUBLE_QUOTE)
                            ${ genWriteCode(fTpe, vterm, out) }
                            $out.writeByte(JsonConstants.TOKEN_DOUBLE_QUOTE)
                        }.asTerm
                        else
                            '{
                                JsonHelper.serializeKey(${ Expr(fieldInfo.mappedName) }, $out)
                                ${ genWriteCode(fTpe, vterm, out) }
                            }.asTerm
                        writeFields.addOne(term)
                        writeFields.append('{ $out.writeByte(JsonConstants.TOKEN_COMMA) }.asTerm)
            }
            Block(
              '{ JsonHelper.serializeObjectStart($out) }.asTerm :: writeFields.init.toList,
              '{ JsonHelper.serializeObjectEnd($out) }.asTerm
            ).asExprOf[Unit]
        }

        def genReadCode[T: Type](tpe: TypeRepr, in: Expr[Buffer], isString: Boolean = false)(using Quotes): Expr[T] = {
            val impli = findImplicitSerde(tpe)
            impli match
                case Some(serde) =>
                    if (isString && isStringableType(tpe)) '{
                        JsonHelper.skipBlanks($in)
                        $in.skipIfNextIs(JsonConstants.TOKEN_DOUBLE_QUOTE)
                        val ret = ${ serde.asExprOf[JsonSerde[T]] }.deserialize($in)
                        $in.skipIfNextIs(JsonConstants.TOKEN_DOUBLE_QUOTE)
                        ret
                    }
                    else '{ JsonHelper.skipBlanks($in); ${ serde.asExprOf[JsonSerde[T]] }.deserialize($in) }
                case None =>
                    val methodKey       = DecoderMethodKey(tpe, isString, false)
                    val decodeMethodSym = decodeMethodSyms.get(methodKey)
                    if (decodeMethodSym.isDefined) Apply(Ref(decodeMethodSym.get), List(in.asTerm)).asExprOf[T]
                    else if (tpe =:= TypeRepr.of[Byte])
                        if (isString) '{
                            JsonHelper.skipBlanks($in)
                            $in.skipIfNextIs(JsonConstants.TOKEN_DOUBLE_QUOTE)
                            val ret = JsonHelper.deserializeByte($in)
                            $in.skipIfNextIs(JsonConstants.TOKEN_DOUBLE_QUOTE)
                            ret
                        }.asExprOf[T]
                        else '{ JsonHelper.deserializeByte($in) }.asExprOf[T]
                    else if (tpe =:= TypeRepr.of[Boolean])
                        if (isString) '{
                            JsonHelper.skipBlanks($in)
                            $in.skipIfNextIs(JsonConstants.TOKEN_DOUBLE_QUOTE)
                            val ret = JsonHelper.deserializeBoolean($in)
                            $in.skipIfNextIs(JsonConstants.TOKEN_DOUBLE_QUOTE)
                            ret
                        }.asExprOf[T]
                        else '{ JsonHelper.deserializeBoolean($in) }.asExprOf[T]
                    else if (tpe =:= TypeRepr.of[Char])
                        '{ JsonHelper.skipBlanks($in); JsonHelper.deserializeChar($in) }.asExprOf[T]
                    else if (tpe =:= TypeRepr.of[Short])
                        if (isString) '{
                            JsonHelper.skipBlanks($in)
                            $in.skipIfNextIs(JsonConstants.TOKEN_DOUBLE_QUOTE)
                            val ret = JsonHelper.deserializeShort($in)
                            $in.skipIfNextIs(JsonConstants.TOKEN_DOUBLE_QUOTE)
                            ret
                        }.asExprOf[T]
                        else '{ JsonHelper.deserializeShort($in) }.asExprOf[T]
                    else if (tpe =:= TypeRepr.of[Int])
                        if (isString) '{
                            JsonHelper.skipBlanks($in)
                            $in.skipIfNextIs(JsonConstants.TOKEN_DOUBLE_QUOTE)
                            val ret = JsonHelper.deserializeInt($in)
                            $in.skipIfNextIs(JsonConstants.TOKEN_DOUBLE_QUOTE)
                            ret
                        }.asExprOf[T]
                        else '{ JsonHelper.deserializeInt($in) }.asExprOf[T]
                    else if (tpe =:= TypeRepr.of[Long])
                        if (isString) '{
                            JsonHelper.skipBlanks($in)
                            $in.skipIfNextIs(JsonConstants.TOKEN_DOUBLE_QUOTE)
                            val ret = JsonHelper.deserializeLong($in)
                            $in.skipIfNextIs(JsonConstants.TOKEN_DOUBLE_QUOTE)
                            ret
                        }.asExprOf[T]
                        else '{ JsonHelper.deserializeLong($in) }.asExprOf[T]
                    else if (tpe =:= TypeRepr.of[Float])
                        if (isString) '{
                            JsonHelper.skipBlanks($in)
                            $in.skipIfNextIs(JsonConstants.TOKEN_DOUBLE_QUOTE)
                            val ret = JsonHelper.deserializeFloat($in)
                            $in.skipIfNextIs(JsonConstants.TOKEN_DOUBLE_QUOTE)
                            ret
                        }.asExprOf[T]
                        else '{ JsonHelper.deserializeFloat($in) }.asExprOf[T]
                    else if (tpe =:= TypeRepr.of[Double])
                        if (isString) '{
                            JsonHelper.skipBlanks($in)
                            $in.skipIfNextIs(JsonConstants.TOKEN_DOUBLE_QUOTE)
                            val ret = JsonHelper.deserializeDouble($in)
                            $in.skipIfNextIs(JsonConstants.TOKEN_DOUBLE_QUOTE)
                            ret
                        }.asExprOf[T]
                        else '{ JsonHelper.deserializeDouble($in) }.asExprOf[T]
                    else if (tpe <:< TypeRepr.of[Option[?]]) {
                        val tpe1 = typeArg1(tpe)
                        tpe1.asType match
                            case '[t1] =>
                                '{
                                    JsonHelper.skipBlanks($in)
                                    if ($in.skipIfNextAre(JsonConstants.TOKEN_NULL)) None
                                    else Some(${ genReadCode[t1](tpe1, in, isString) })
                                }.asExprOf[T]
                    } else if (isTuple(tpe)) withDecoderFor(methodKey, in) { in =>
                        val indexedTypes = typeArgs(tpe)
                        var i            = 0
                        val valDefs = indexedTypes.map { te =>
                            i += 1
                            te.asType match
                                case '[t] =>
                                    val sym = symbol("_r" + i, te)
                                    val rhs =
                                        if (i == 1) genReadCode[t](te, in)
                                        else
                                            '{
                                                assert(
                                                  JsonHelper.isNextToken($in, JsonConstants.TOKEN_COMMA),
                                                  "decode error, except ','"
                                                )
                                                ${ genReadCode[t](te, in) }
                                            }
                                    ValDef(sym, Some(rhs.asTerm.changeOwner(sym)))
                        }
                        val readCreateBlock = Block(
                          valDefs,
                          '{
                              assert(
                                JsonHelper.isNextToken($in, JsonConstants.TOKEN_ARRAY_END),
                                "decode error, except ']'"
                              )
                              ${
                                  Apply(
                                    TypeApply(
                                      Select.unique(New(Inferred(tpe)), "<init>"),
                                      indexedTypes.map(x => Inferred(x))
                                    ),
                                    valDefs.map(x => Ref(x.symbol))
                                  ).asExpr
                              }
                          }.asTerm
                        )
                        '{
                            assert(
                              JsonHelper.isNextToken($in, JsonConstants.TOKEN_ARRAY_START),
                              "decode error, except '['"
                            )
                            ${ readCreateBlock.asExprOf[T] }
                        }
                    }
                    else if (tpe <:< TypeRepr.of[AnyVal]) {
                        val te = valueClassValueType(tpe)
                        te.asType match
                            case '[t] => 
                                val readVal = genReadCode[t](te, in, isString)
                                getClassInfo(tpe).genNew(readVal.asTerm).asExprOf[T]
                    }
                    else '{ ??? }
        }

        def genWriteCode[T: Type](tpe: TypeRepr, value: Expr[T], out: Expr[Buffer], isString: Boolean = false)(using
            Quotes
        ): Expr[Unit] = {
            val impli = findImplicitSerde(tpe)
            impli match
                case Some(serde) =>
                    if (isString && isStringableType(tpe)) '{
                        $out.writeByte(JsonConstants.TOKEN_DOUBLE_QUOTE)
                        ${ serde.asExprOf[JsonSerde[T]] }.serialize($value, $out)
                        $out.writeByte(JsonConstants.TOKEN_DOUBLE_QUOTE)
                    }
                    else '{ ${ serde.asExprOf[JsonSerde[T]] }.serialize($value, $out) }
                case None =>
                    val methodKey       = EncoderMethodKey(tpe, isString, None)
                    val encodeMethodSym = encodeMethodSyms.get(methodKey)
                    if (encodeMethodSym.isDefined)
                        Apply(Ref(encodeMethodSym.get), List(value.asTerm, out.asTerm)).asExprOf[Unit]
                    else if (tpe =:= TypeRepr.of[Byte])
                        if (isString) '{
                            $out.writeByte(JsonConstants.TOKEN_DOUBLE_QUOTE)
                            JsonHelper.serializeByte(${ value.asExprOf[Byte] }, $out)
                            $out.writeByte(JsonConstants.TOKEN_DOUBLE_QUOTE)
                        }
                        else '{ JsonHelper.serializeByte(${ value.asExprOf[Byte] }, $out) }
                    else if (tpe =:= TypeRepr.of[Boolean])
                        if (isString) '{
                            $out.writeByte(JsonConstants.TOKEN_DOUBLE_QUOTE)
                            JsonHelper.serializeBoolean(${ value.asExprOf[Boolean] }, $out)
                            $out.writeByte(JsonConstants.TOKEN_DOUBLE_QUOTE)
                        }
                        else '{ JsonHelper.serializeBoolean(${ value.asExprOf[Boolean] }, $out) }
                    else if (tpe =:= TypeRepr.of[Char]) '{ JsonHelper.serializeChar(${ value.asExprOf[Char] }, $out) }
                    else if (tpe =:= TypeRepr.of[Short])
                        if (isString) '{
                            $out.writeByte(JsonConstants.TOKEN_DOUBLE_QUOTE)
                            JsonHelper.serializeShort(${ value.asExprOf[Short] }, $out)
                            $out.writeByte(JsonConstants.TOKEN_DOUBLE_QUOTE)
                        }
                        else '{ JsonHelper.serializeShort(${ value.asExprOf[Short] }, $out) }
                    else if (tpe =:= TypeRepr.of[Int])
                        if (isString) '{
                            $out.writeByte(JsonConstants.TOKEN_DOUBLE_QUOTE)
                            JsonHelper.serializeInt(${ value.asExprOf[Int] }, $out)
                            $out.writeByte(JsonConstants.TOKEN_DOUBLE_QUOTE)
                        }
                        else '{ JsonHelper.serializeInt(${ value.asExprOf[Int] }, $out) }
                    else if (tpe =:= TypeRepr.of[Long])
                        if (isString) '{
                            $out.writeByte(JsonConstants.TOKEN_DOUBLE_QUOTE)
                            JsonHelper.serializeLong(${ value.asExprOf[Long] }, $out)
                            $out.writeByte(JsonConstants.TOKEN_DOUBLE_QUOTE)
                        }
                        else '{ JsonHelper.serializeLong(${ value.asExprOf[Long] }, $out) }
                    else if (tpe =:= TypeRepr.of[Float])
                        if (isString) '{
                            $out.writeByte(JsonConstants.TOKEN_DOUBLE_QUOTE)
                            JsonHelper.serializeFloat(${ value.asExprOf[Float] }, $out)
                            $out.writeByte(JsonConstants.TOKEN_DOUBLE_QUOTE)
                        }
                        else '{ JsonHelper.serializeFloat(${ value.asExprOf[Float] }, $out) }
                    else if (tpe =:= TypeRepr.of[Double])
                        if (isString) '{
                            $out.writeByte(JsonConstants.TOKEN_DOUBLE_QUOTE)
                            JsonHelper.serializeDouble(${ value.asExprOf[Double] }, $out)
                            $out.writeByte(JsonConstants.TOKEN_DOUBLE_QUOTE)
                        }
                        else '{ JsonHelper.serializeDouble(${ value.asExprOf[Double] }, $out) }
                    else if (tpe =:= TypeRepr.of[String])
                        '{ JsonHelper.serializeString(${ value.asExprOf[String] }, $out) }
                    else if (tpe <:< TypeRepr.of[AnyVal]) {
                        val te = valueClassValueType(tpe)
                        genWriteCode(te, Select(value.asTerm, valueClassValueSymbol(tpe)).asExpr, out, isString)
                    } else if (tpe <:< TypeRepr.of[Option[?]]) {
                        val tpe1 = typeArg1(tpe)
                        tpe1.asType match
                            case '[t1] =>
                                '{
                                    ${ value.asExprOf[Option[t1]] } match
                                        case None    => JsonHelper.serializeNull($out)
                                        case Some(v) => ${ genWriteCode(tpe1, 'v, out, isString) }
                                }
                    } else if (tpe <:< TypeRepr.of[Array[?]]) withEncoderFor(methodKey, value, out) { (x, out) =>
                        val tpe1 = typeArg1(tpe)
                        tpe1.asType match
                            case '[t1] =>
                                val tx = x.asExprOf[Array[t1]]
                                '{
                                    JsonHelper.serializeArrayStart($out)
                                    var i = 0
                                    while (i < $tx.length) {
                                        ${ genWriteCode(tpe1, '{ $tx.apply(i) }, out) }
                                        i += 1
                                        if (i != $tx.length) $out.writeByte(JsonConstants.TOKEN_COMMA)
                                    }
                                    JsonHelper.serializeArrayEnd($out)
                                }
                    }
                    else if (tpe <:< TypeRepr.of[mutable.ArraySeq[?]]) withEncoderFor(methodKey, value, out) {
                        (x, out) =>
                            val tpe1 = typeArg1(tpe)
                            tpe1.asType match
                                case '[t] =>
                                    val tx = x.asExprOf[mutable.ArraySeq[t]]
                                    '{
                                        JsonHelper.serializeArrayStart($out)
                                        val xs = $tx.array.asInstanceOf[Array[t]]
                                        var i  = 0
                                        while (i < xs.length) {
                                            ${ genWriteCode[t](tpe1, '{ xs(i) }, out) }
                                            i += 1
                                            if (i != xs.length) $out.writeByte(JsonConstants.TOKEN_COMMA)
                                        }
                                        JsonHelper.serializeArrayEnd($out)
                                    }
                    }
                    else if (tpe <:< TypeRepr.of[immutable.ArraySeq[?]]) withEncoderFor(methodKey, value, out) {
                        (x, out) =>
                            val tpe1 = typeArg1(tpe)
                            tpe1.asType match
                                case '[t] =>
                                    val tx = x.asExprOf[immutable.ArraySeq[t]]
                                    '{
                                        JsonHelper.serializeArrayStart($out)
                                        val xs = $tx.unsafeArray.asInstanceOf[Array[t]]
                                        var i  = 0
                                        while (i < xs.length) {
                                            ${ genWriteCode[t](tpe1, '{ xs(i) }, out) }
                                            i += 1
                                            if (i != xs.length) $out.writeByte(JsonConstants.TOKEN_COMMA)
                                        }
                                        JsonHelper.serializeArrayEnd($out)
                                    }
                    }
                    else if (tpe.typeSymbol.fullName == "scala.IArray$package$.IArray")
                        withEncoderFor(methodKey, value, out) { (x, out) =>
                            val tpe1 = typeArg1(tpe)
                            tpe1.asType match
                                case '[t] =>
                                    val tx = x.asExprOf[IArray[t]]
                                    '{
                                        JsonHelper.serializeArrayStart($out)
                                        var i = 0
                                        while (i < $tx.length) {
                                            ${ genWriteCode[t](tpe1, '{ $tx(i) }, out) }
                                            i += 1
                                            if (i != $tx.length) $out.writeByte(JsonConstants.TOKEN_COMMA)
                                        }
                                        JsonHelper.serializeArrayEnd($out)
                                    }
                        }
                    else if (tpe <:< TypeRepr.of[scala.collection.Seq[?]]) withEncoderFor(methodKey, value, out) {
                        (x, out) =>
                            val tpe1 = typeArg1(tpe)
                            tpe1.asType match
                                case '[t1] =>
                                    val tx = x.asExprOf[scala.collection.Seq[t1]]
                                    '{
                                        JsonHelper.serializeArrayStart($out)
                                        var i = 0
                                        while (i < $tx.length) {
                                            ${ genWriteCode(tpe1, '{ $tx.apply(i) }, out) }
                                            i += 1
                                            if (i != $tx.length) $out.writeByte(JsonConstants.TOKEN_COMMA)
                                        }
                                        JsonHelper.serializeArrayEnd($out)
                                    }
                    }
                    else if (tpe <:< TypeRepr.of[scala.collection.Map[?, ?]]) withEncoderFor(methodKey, value, out) {
                        (x, out) =>
                            val tpe1 = typeArg1(tpe)
                            val tpe2 = typeArg2(tpe)
                            (tpe1.asType, tpe2.asType) match
                                case ('[tk], '[tv]) =>
                                    val tx = x.asExprOf[collection.Map[tk, tv]]
                                    '{
                                        JsonHelper.serializeObjectStart($out)
                                        var i      = 0
                                        val length = $tx.size
                                        $tx.foreach { (k, v) =>
                                            JsonHelper.serializeKey(k.toString(), $out)
                                            ${ genWriteCode(tpe2, 'v, out) }
                                            i += 1
                                            if (i != length) $out.writeByte(JsonConstants.TOKEN_COMMA)
                                        }
                                        JsonHelper.serializeObjectEnd($out)
                                    }
                    }
                    else if (isTuple(tpe)) withEncoderFor(methodKey, value, out) { (x, out) =>
                        val tpes   = typeArgs(tpe)
                        val length = tpes.length
                        val writeFields = tpes.zipWithIndex.map { (te, i) =>
                            val idx = i + 1
                            te.asType match
                                case '[t] =>
                                    if (idx != length)
                                        '{
                                            ${ genWriteCode(te, Select.unique(x.asTerm, "_" + idx).asExprOf[t], out) }
                                            $out.writeByte(JsonConstants.TOKEN_COMMA)
                                        }.asTerm
                                    else genWriteCode(te, Select.unique(x.asTerm, "_" + idx).asExprOf[t], out).asTerm
                        }
                        Block(
                          '{ JsonHelper.serializeArrayStart($out) }.asTerm :: writeFields,
                          '{ JsonHelper.serializeArrayEnd($out) }.asTerm
                        ).asExprOf[Unit]
                    }
                    else if (isNonAbstractScalaClass(tpe)) withEncoderFor(methodKey, value, out) { (x, out) =>
                        genWriteNonAbstractScalaClass(x, tpe, out)
                    }
                    else '{ ??? }
        }

        val clz = '{
            new JsonSerde[A] {
                override def serialize(value: A, out: Buffer): Unit = ${ genWriteCode(rootType, 'value, 'out) }
                override def deserialize(in: Buffer): A             = ${ genReadCode(rootType, 'in) }
            }
        }.asTerm

        val needDefs =
            classTags.values ++
                mathContexts.values ++
                scalaEnumCaches.values ++
                decodeMethodDefs ++
                encodeMethodDefs
        val serde = Block(needDefs.toList, clz).asExprOf[JsonSerde[A]]
        report.info(
          s"Generated JSON serde for type '${rootType.show}':\n${serde.show}",
          Position.ofMacroExpansion
        )
        serde

    }

}
