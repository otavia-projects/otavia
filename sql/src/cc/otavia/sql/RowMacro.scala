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

package cc.otavia.sql

import java.math.{BigInteger, BigDecimal as JBigDecimal}
import java.time.{Duration as JDuration, *}
import java.util.{Currency, Locale, UUID}
import scala.concurrent.duration.Duration
import scala.quoted.*

object RowMacro {

    def derivedMacro[A <: Row: Type](using quotes: Quotes): Expr[RowDecoder[A]] = {
        import quotes.reflect.*

        val tpe = TypeRepr.of[A].dealias

        def typeArgs(tpe: TypeRepr): List[TypeRepr] = tpe match
            case AppliedType(_, typeArgs) => typeArgs.map(_.dealias)
            case _                        => Nil

        def typeArg1(tpe: TypeRepr): TypeRepr = typeArgs(tpe).head

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
            else report.errorAndAbort(s"Not support type ${te.show} for RowDecoder, see RowParser")
        }

        def genDecode[T: Type](tpe: TypeRepr, parser: Expr[RowParser]): Expr[T] = {
            val fieldSymbols = tpe.typeSymbol.caseFields
            val fieldTpes    = fieldSymbols.map(s => s.tree.asInstanceOf[ValDef].tpt.tpe.dealias)

            val cols = (0 until fieldTpes.length) map { (idx) =>
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

        if (tpe.typeSymbol.flags.is(Flags.Case)) {
            val decoder = '{
                new RowDecoder[A] {
                    override def decode(parser: RowParser): A = ${ genDecode[A](tpe, 'parser) }
                }
            }
            decoder
        } else report.errorAndAbort(s"type ${tpe.show} is not a case class, only case class can be derived by macro!")
    }

}
