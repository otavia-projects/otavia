/*
 * Copyright 2022 Yan Kun <yan_kun_1992@foxmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License")

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

package cc.otavia.sql

import java.math.{BigInteger, BigDecimal as JBigDecimal}
import java.time.{Duration as JDuration, *}
import java.util.{Currency, Locale, UUID}
import scala.concurrent.duration.Duration
import scala.quoted.*

object RowCodecMacro {
    def derivedImpl[A <: Product: Type](using quotes: Quotes): Expr[RowCodec[A]] = {
        import quotes.reflect.*

        val tpe = TypeRepr.of[A].dealias

        def typeArgs(tpe: TypeRepr): List[TypeRepr] = tpe match
            case AppliedType(_, typeArgs) => typeArgs.map(_.dealias)
            case _                        => Nil

        def typeArg1(tpe: TypeRepr): TypeRepr = typeArgs(tpe).head

        def isTuple(tpe: TypeRepr): Boolean = tpe <:< TypeRepr.of[Tuple]

        def readColumn(te: TypeRepr, parser: Expr[RowParser], idx: Int)(using
            Quotes
        ): Term = {
            if (te =:= TypeRepr.of[Char]) '{ $parser.parseChar(${ Expr(idx) }) }.asTerm
            else if (te =:= TypeRepr.of[String]) '{ $parser.parseString(${ Expr(idx) }) }.asTerm
            else if (te =:= TypeRepr.of[Boolean]) '{ $parser.parseBoolean(${ Expr(idx) }) }.asTerm
            else if (te =:= TypeRepr.of[Byte]) '{ $parser.parseByte(${ Expr(idx) }) }.asTerm
            else if (te =:= TypeRepr.of[Short]) '{ $parser.parseShort(${ Expr(idx) }) }.asTerm
            else if (te =:= TypeRepr.of[Int]) '{ $parser.parseInt(${ Expr(idx) }) }.asTerm
            else if (te =:= TypeRepr.of[Long]) '{ $parser.parseLong(${ Expr(idx) }) }.asTerm
            else if (te =:= TypeRepr.of[Float]) '{ $parser.parseFloat(${ Expr(idx) }) }.asTerm
            else if (te =:= TypeRepr.of[Double]) '{ $parser.parseDouble(${ Expr(idx) }) }.asTerm
            else if (te =:= TypeRepr.of[BigInt]) '{ $parser.parseBigInt(${ Expr(idx) }) }.asTerm
            else if (te =:= TypeRepr.of[BigDecimal]) '{ $parser.parseBigDecimal(${ Expr(idx) }) }.asTerm
            else if (te =:= TypeRepr.of[BigInteger]) '{ $parser.parseBigInteger(${ Expr(idx) }) }.asTerm
            else if (te =:= TypeRepr.of[JBigDecimal]) '{ $parser.parseJBigDecimal(${ Expr(idx) }) }.asTerm
            else if (te =:= TypeRepr.of[Instant]) '{ $parser.parseInstant(${ Expr(idx) }) }.asTerm
            else if (te =:= TypeRepr.of[LocalDate]) '{ $parser.parseLocalDate(${ Expr(idx) }) }.asTerm
            else if (te =:= TypeRepr.of[LocalDateTime]) '{ $parser.parseLocalDateTime(${ Expr(idx) }) }.asTerm
            else if (te =:= TypeRepr.of[LocalTime]) '{ $parser.parseLocalTime(${ Expr(idx) }) }.asTerm
            else if (te =:= TypeRepr.of[MonthDay]) '{ $parser.parseMonthDay(${ Expr(idx) }) }.asTerm
            else if (te =:= TypeRepr.of[OffsetDateTime]) '{ $parser.parseOffsetDateTime(${ Expr(idx) }) }.asTerm
            else if (te =:= TypeRepr.of[OffsetTime]) '{ $parser.parseOffsetTime(${ Expr(idx) }) }.asTerm
            else if (te =:= TypeRepr.of[Period]) '{ $parser.parsePeriod(${ Expr(idx) }) }.asTerm
            else if (te =:= TypeRepr.of[Year]) '{ $parser.parseYear(${ Expr(idx) }) }.asTerm
            else if (te =:= TypeRepr.of[YearMonth]) '{ $parser.parseYearMonth(${ Expr(idx) }) }.asTerm
            else if (te =:= TypeRepr.of[ZoneId]) '{ $parser.parseZoneId(${ Expr(idx) }) }.asTerm
            else if (te =:= TypeRepr.of[ZoneOffset]) '{ $parser.parseZoneOffset(${ Expr(idx) }) }.asTerm
            else if (te =:= TypeRepr.of[ZonedDateTime]) '{ $parser.parseZonedDateTime(${ Expr(idx) }) }.asTerm
            else if (te =:= TypeRepr.of[JDuration]) '{ $parser.parseJDuration(${ Expr(idx) }) }.asTerm
            else if (te =:= TypeRepr.of[Duration]) '{ $parser.parseDuration(${ Expr(idx) }) }.asTerm
            else if (te =:= TypeRepr.of[Currency]) '{ $parser.parseCurrency(${ Expr(idx) }) }.asTerm
            else if (te =:= TypeRepr.of[Locale]) '{ $parser.parseLocale(${ Expr(idx) }) }.asTerm
            else if (te =:= TypeRepr.of[UUID]) '{ $parser.parseUUID(${ Expr(idx) }) }.asTerm
            else report.errorAndAbort(s"Not support type ${te.show} for RowCodec, see RowParser")
        }

        def writeColumn[T](v: Expr[T], te: TypeRepr, writer: Expr[RowWriter], idx: Int)(using Quotes): Expr[Unit] = {
            if (te =:= TypeRepr.of[Char]) '{ $writer.writeChar(${ v.asExprOf[Char] }, ${ Expr(idx) }) }
            else if (te =:= TypeRepr.of[String]) '{ $writer.writeString(${ v.asExprOf[String] }, ${ Expr(idx) }) }
            else if (te =:= TypeRepr.of[Boolean]) '{ $writer.writeBoolean(${ v.asExprOf[Boolean] }, ${ Expr(idx) }) }
            else if (te =:= TypeRepr.of[Byte]) '{ $writer.writeByte(${ v.asExprOf[Byte] }, ${ Expr(idx) }) }
            else if (te =:= TypeRepr.of[Short]) '{ $writer.writeByte(${ v.asExprOf[Byte] }, ${ Expr(idx) }) }
            else if (te =:= TypeRepr.of[Int]) '{ $writer.writeInt(${ v.asExprOf[Int] }, ${ Expr(idx) }) }
            else if (te =:= TypeRepr.of[Long]) '{ $writer.writeLong(${ v.asExprOf[Long] }, ${ Expr(idx) }) }
            else if (te =:= TypeRepr.of[Float]) '{ $writer.writeFloat(${ v.asExprOf[Float] }, ${ Expr(idx) }) }
            else if (te =:= TypeRepr.of[Double]) '{ $writer.writeDouble(${ v.asExprOf[Double] }, ${ Expr(idx) }) }
            else if (te =:= TypeRepr.of[BigInt]) '{ $writer.writeBigInt(${ v.asExprOf[BigInt] }, ${ Expr(idx) }) }
            else if (te =:= TypeRepr.of[BigDecimal]) '{
                $writer.writeBigDecimal(${ v.asExprOf[BigDecimal] }, ${ Expr(idx) })
            }
            else if (te =:= TypeRepr.of[BigInteger]) '{
                $writer.writeBigInteger(${ v.asExprOf[BigInteger] }, ${ Expr(idx) })
            }
            else if (te =:= TypeRepr.of[JBigDecimal]) '{
                $writer.writeJBigDecimal(${ v.asExprOf[JBigDecimal] }, ${ Expr(idx) })
            }
            else if (te =:= TypeRepr.of[Instant]) '{ $writer.writeInstant(${ v.asExprOf[Instant] }, ${ Expr(idx) }) }
            else if (te =:= TypeRepr.of[LocalDate]) '{
                $writer.writeLocalDate(${ v.asExprOf[LocalDate] }, ${ Expr(idx) })
            }
            else if (te =:= TypeRepr.of[LocalDateTime]) '{
                $writer.writeLocalDateTime(${ v.asExprOf[LocalDateTime] }, ${ Expr(idx) })
            }
            else if (te =:= TypeRepr.of[LocalTime]) '{
                $writer.writeLocalTime(${ v.asExprOf[LocalTime] }, ${ Expr(idx) })
            }
            else if (te =:= TypeRepr.of[MonthDay]) '{ $writer.writeMonthDay(${ v.asExprOf[MonthDay] }, ${ Expr(idx) }) }
            else if (te =:= TypeRepr.of[OffsetDateTime]) '{
                $writer.writeOffsetDateTime(${ v.asExprOf[OffsetDateTime] }, ${ Expr(idx) })
            }
            else if (te =:= TypeRepr.of[OffsetTime]) '{
                $writer.writeOffsetTime(${ v.asExprOf[OffsetTime] }, ${ Expr(idx) })
            }
            else if (te =:= TypeRepr.of[Period]) '{ $writer.writePeriod(${ v.asExprOf[Period] }, ${ Expr(idx) }) }
            else if (te =:= TypeRepr.of[Year]) '{ $writer.writeYear(${ v.asExprOf[Year] }, ${ Expr(idx) }) }
            else if (te =:= TypeRepr.of[YearMonth]) '{
                $writer.writeYearMonth(${ v.asExprOf[YearMonth] }, ${ Expr(idx) })
            }
            else if (te =:= TypeRepr.of[ZoneId]) '{ $writer.writeZoneId(${ v.asExprOf[ZoneId] }, ${ Expr(idx) }) }
            else if (te =:= TypeRepr.of[ZoneOffset]) '{
                $writer.writeZoneOffset(${ v.asExprOf[ZoneOffset] }, ${ Expr(idx) })
            }
            else if (te =:= TypeRepr.of[ZonedDateTime]) '{ $writer.writeByte(${ v.asExprOf[Byte] }, ${ Expr(idx) }) }
            else if (te =:= TypeRepr.of[JDuration]) '{
                $writer.writeJDuration(${ v.asExprOf[JDuration] }, ${ Expr(idx) })
            }
            else if (te =:= TypeRepr.of[Duration]) '{ $writer.writeDuration(${ v.asExprOf[Duration] }, ${ Expr(idx) }) }
            else if (te =:= TypeRepr.of[Currency]) '{ $writer.writeCurrency(${ v.asExprOf[Currency] }, ${ Expr(idx) }) }
            else if (te =:= TypeRepr.of[Locale]) '{ $writer.writeLocale(${ v.asExprOf[Locale] }, ${ Expr(idx) }) }
            else if (te =:= TypeRepr.of[UUID]) '{ $writer.writeUUID(${ v.asExprOf[UUID] }, ${ Expr(idx) }) }
            else report.errorAndAbort(s"Not support type ${te.show} for RowCodec, see RowWriter")
        }

        def genDecode[T: Type](tpe: TypeRepr, parser: Expr[RowParser]): Expr[T] = {
            val fieldSymbols = tpe.typeSymbol.caseFields
            val fieldTpes    = fieldSymbols.map(s => s.tree.asInstanceOf[ValDef].tpt.tpe.dealias)

            val cols = (0 until fieldTpes.length).map { idx =>
                val te = fieldTpes(idx)
                if (te <:< TypeRepr.of[Option[?]]) {
                    val te1 = typeArg1(te)
                    te1.asType match
                        case '[t] =>
                            '{
                                if ($parser.isNull(${ Expr(idx) })) None
                                else Some(${ readColumn(te1, parser, idx).asExprOf[t] })
                            }.asTerm
                } else readColumn(te, parser, idx)
            }

            Apply(Select.unique(New(Inferred(tpe)), "<init>"), cols.toList).asExprOf[T]
        }

        def symbol(name: String, tpe: TypeRepr, flags: Flags = Flags.EmptyFlags): Symbol =
            Symbol.newVal(Symbol.spliceOwner, name, tpe, flags, Symbol.noSymbol)

        def genTupeDecode[T: Type](tpe: TypeRepr, parser: Expr[RowParser]): Expr[T] = {
            val indexedTypes = typeArgs(tpe)
            var i            = 0
            val valDefs = indexedTypes.map { te =>
                i += 1
                if (te <:< TypeRepr.of[Option[?]]) {
                    val te1 = typeArg1(te)
                    te1.asType match
                        case '[t] =>
                            val sym = symbol("_r" + i, te)
                            val rhs = '{
                                if ($parser.isNull(${ Expr(i) })) None
                                else Some(${ readColumn(te1, parser, i).asExprOf[t] })
                            }
                            ValDef(sym, Some(rhs.asTerm.changeOwner(sym)))
                } else {
                    te.asType match
                        case '[t] =>
                            val sym = symbol("_r" + i, te)
                            val rhs = readColumn(te, parser, i).asExprOf[t]
                            ValDef(sym, Some(rhs.asTerm.changeOwner(sym)))
                }
            }
            val readCreateBlock = Block(
              valDefs,
              Apply(
                TypeApply(
                  Select.unique(New(Inferred(tpe)), "<init>"),
                  indexedTypes.map(x => Inferred(x))
                ),
                valDefs.map(x => Ref(x.symbol))
              )
            )
            readCreateBlock.asExprOf[T]
        }

        def genTupleEncode[T: Type](row: Expr[T], tpe: TypeRepr, writer: Expr[RowWriter]): Expr[Unit] = {
            val fieldTpes = typeArgs(tpe)

            val cols = (0 until fieldTpes.length).map { idx =>
                val te    = fieldTpes(idx)
                val vterm = Select.unique(row.asTerm, "_" + (idx + 1))
                if (te <:< TypeRepr.of[Option[?]]) {
                    val tpe1 = typeArg1(tpe)
                    tpe1.asType match
                        case '[t1] =>
                            '{
                                ${ vterm.asExprOf[Option[t1]] } match
                                    case None    => $writer.writeNull(${ Expr(idx) })
                                    case Some(v) => ${ writeColumn[t1]('v, tpe1, writer, idx) }
                            }.asTerm
                } else {
                    te.asType match
                        case '[t1] =>
                            writeColumn[t1](vterm.asExprOf[t1], te, writer, idx).asTerm
                }
            }.toList

            Block(
              '{ $writer.writeRowStart() }.asTerm :: cols,
              '{ $writer.writeRowEnd() }.asTerm
            ).asExprOf[Unit]
        }

        def genCaseClassEncode[T: Type](row: Expr[T], tpe: TypeRepr, writer: Expr[RowWriter]): Expr[Unit] = {
            val fieldSymbols = tpe.typeSymbol.caseFields
            val fieldTpes    = fieldSymbols.map(s => s.tree.asInstanceOf[ValDef].tpt.tpe.dealias)
            val cols = (0 until fieldTpes.length).map { idx =>
                val te    = fieldTpes(idx)
                val sb    = fieldSymbols(idx)
                val vterm = Select.unique(row.asTerm, sb.name)
                if (te <:< TypeRepr.of[Option[?]]) {
                    val tpe1 = typeArg1(te)
                    tpe1.asType match
                        case '[t1] =>
                            '{
                                ${ vterm.asExprOf[Option[t1]] } match
                                    case None    => $writer.writeNull(${ Expr(idx) })
                                    case Some(v) => ${ writeColumn[t1]('v, tpe1, writer, idx) }
                            }.asTerm
                } else {
                    te.asType match
                        case '[t1] =>
                            writeColumn[t1](vterm.asExprOf[t1], te, writer, idx).asTerm
                }
            }.toList

            Block(
              '{ $writer.writeRowStart() }.asTerm :: cols,
              '{ $writer.writeRowEnd() }.asTerm
            ).asExprOf[Unit]
        }

        if (isTuple(tpe)) {
            val codec = '{
                new RowCodec[A] {
                    override def decode(parser: RowParser): A = ${ genTupeDecode[A](tpe, 'parser) }

                    override def encode(row: A, writer: RowWriter): Unit = ${ genTupleEncode[A]('row, tpe, 'writer) }
                }
            }
            report.info(
              s"Generated RowCodec for type '${tpe.show}':\n${codec.show}",
              Position.ofMacroExpansion
            )
            codec
        } else if (tpe.typeSymbol.flags.is(Flags.Case)) {
            val codec = '{
                new RowCodec[A] {
                    override def decode(parser: RowParser): A = ${ genDecode[A](tpe, 'parser) }

                    override def encode(row: A, writer: RowWriter): Unit = ${ genCaseClassEncode[A]('row, tpe, 'writer) }
                }
            }
            report.info(
              s"Generated RowCodec for type '${tpe.show}':\n${codec.show}",
              Position.ofMacroExpansion
            )
            codec
        } else
            report.errorAndAbort(
              s"type ${tpe.show} is not a case class or Tuple, only case class / Tuple can be derived by macro!"
            )
    }
}
