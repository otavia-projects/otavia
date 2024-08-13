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

package cc.otavia.sql.statement

import cc.otavia.core.message.{Ask, Reply}
import cc.otavia.sql.{Row, RowSet, RowDecoder as Decoder}

sealed trait PrepareQuery[T <: Reply] extends Ask[T] {

    def sql: String

    def parameterLength: Short

}

object PrepareQuery {

    def update(sql: String, param: Product): PrepareQuery[ModifyRows] = PrepareUpdate(sql, param)

    def insert(sql: String, param: Product): PrepareQuery[ModifyRows] = PrepareUpdate(sql, param)

    def delete(sql: String, param: Product): PrepareQuery[ModifyRows] = PrepareUpdate(sql, param)

    def updateBatch(sql: String, params: Seq[Product]): PrepareQuery[ModifyRows] = PrepareUpdateBatch(sql, params)

    def insertBatch(sql: String, params: Seq[Product]): PrepareQuery[ModifyRows] = PrepareUpdateBatch(sql, params)

    def deleteBatch(sql: String, params: Seq[Product]): PrepareQuery[ModifyRows] = PrepareUpdateBatch(sql, params)

    def fetchOne[R <: Row](sql: String)(using decoder: Decoder[R]): PrepareQuery[R] =
        new FetchOneBindNothing[R](sql, decoder)

    def fetchOne[R <: Row](sql: String, parm: Byte)(using decoder: Decoder[R]): PrepareQuery[R] =
        new FetchOneBindByte[R](sql, decoder, parm)

    def fetchOne[R <: Row](sql: String, parm: Char)(using decoder: Decoder[R]): PrepareQuery[R] =
        new FetchOneBindChar[R](sql, decoder, parm)

    def fetchOne[R <: Row](sql: String, parm: Boolean)(using decoder: Decoder[R]): PrepareQuery[R] =
        new FetchOneBindBoolean[R](sql, decoder, parm)

    def fetchOne[R <: Row](sql: String, parm: Short)(using decoder: Decoder[R]): PrepareQuery[R] =
        new FetchOneBindShort[R](sql, decoder, parm)

    def fetchOne[R <: Row](sql: String, parm: Int)(using decoder: Decoder[R]): PrepareQuery[R] =
        new FetchOneBindInt[R](sql, decoder, parm)

    def fetchOne[R <: Row](sql: String, parm: Long)(using decoder: Decoder[R]): PrepareQuery[R] =
        new FetchOneBindLong[R](sql, decoder, parm)

    def fetchOne[R <: Row](sql: String, parm: String)(using decoder: Decoder[R]): PrepareQuery[R] =
        new FetchOneBindString[R](sql, decoder, parm)

    def fetchOne[R <: Row](sql: String, parm: Product)(using decoder: Decoder[R]): PrepareQuery[R] =
        new FetchOneBindProduct[R](sql, decoder, parm)

    def fetchAll[R <: Row](sql: String)(using decoder: Decoder[R]): PrepareQuery[RowSet[R]] =
        new FetchAllBindNothing[R](sql, decoder)

    def fetchAll[R <: Row](sql: String, parm: Byte)(using decoder: Decoder[R]): PrepareQuery[RowSet[R]] =
        new FetchAllBindByte[R](sql, decoder, parm)

    def fetchAll[R <: Row](sql: String, parm: Int)(using decoder: Decoder[R]): PrepareQuery[RowSet[R]] =
        new FetchAllBindInt[R](sql, decoder, parm)

    private[otavia] trait FetchQuery[R <: Reply] extends PrepareQuery[R] {

        def all: Boolean

        def decoder: Decoder[?]

    }

    private[otavia] case class FetchOneBindNothing[R <: Row](sql: String, decoder: Decoder[R]) extends FetchQuery[R] {

        override def all: Boolean = false

        override def parameterLength: Short = 0

    }

    private[otavia] case class FetchAllBindNothing[R <: Row](sql: String, decoder: Decoder[R])
        extends FetchQuery[RowSet[R]] {

        override def all: Boolean = true

        override def parameterLength: Short = 0

    }

    private[otavia] case class FetchOneBindByte[R <: Row](sql: String, decoder: Decoder[?], parm: Byte)
        extends FetchQuery[R] {

        override def all: Boolean = false

        override def parameterLength: Short = 1

    }

    private[otavia] case class FetchAllBindByte[R <: Row](sql: String, decoder: Decoder[?], parm: Byte)
        extends FetchQuery[RowSet[R]] {

        override def all: Boolean = true

        override def parameterLength: Short = 1

    }

    private[otavia] case class FetchOneBindBoolean[R <: Row](sql: String, decoder: Decoder[?], parm: Boolean)
        extends FetchQuery[R] {

        override def all: Boolean = false

        override def parameterLength: Short = 1

    }

    private[otavia] case class FetchAllBindBoolean[R <: Row](sql: String, decoder: Decoder[?], parm: Boolean)
        extends FetchQuery[RowSet[R]] {

        override def all: Boolean = true

        override def parameterLength: Short = 1

    }

    private[otavia] case class FetchOneBindChar[R <: Row](sql: String, decoder: Decoder[R], parm: Char)
        extends FetchQuery[R] {

        override def all: Boolean = false

        override def parameterLength: Short = 1

    }

    private[otavia] case class FetchAllBindChar[R <: Row](sql: String, decoder: Decoder[R], parm: Char)
        extends FetchQuery[RowSet[R]] {

        override def all: Boolean = false

        override def parameterLength: Short = 1

    }

    private[otavia] case class FetchOneBindShort[R <: Row](sql: String, decoder: Decoder[R], parm: Short)
        extends FetchQuery[R] {

        override def all: Boolean = false

        override def parameterLength: Short = 1

    }

    private[otavia] case class FetchAllBindShort[R <: Row](sql: String, decoder: Decoder[R], parm: Short)
        extends FetchQuery[RowSet[R]] {

        override def all: Boolean = true

        override def parameterLength: Short = 1

    }

    private[otavia] case class FetchOneBindInt[R <: Row](sql: String, decoder: Decoder[R], parm: Int)
        extends FetchQuery[R] {

        override def all: Boolean = false

        override def parameterLength: Short = 1

    }

    private[otavia] case class FetchAllBindInt[R <: Row](sql: String, decoder: Decoder[R], parm: Int)
        extends FetchQuery[RowSet[R]] {

        override def all: Boolean = true

        override def parameterLength: Short = 1

    }

    private[otavia] case class FetchOneBindLong[R <: Row](sql: String, decoder: Decoder[R], parm: Long)
        extends FetchQuery[R] {

        override def all: Boolean = false

        override def parameterLength: Short = 1

    }

    private[otavia] case class FetchAllBindLong[R <: Row](sql: String, decoder: Decoder[R], parm: Long)
        extends FetchQuery[R] {

        override def all: Boolean = true

        override def parameterLength: Short = 1

    }

    private[otavia] case class FetchOneBindString[R <: Row](sql: String, decoder: Decoder[R], parm: String)
        extends FetchQuery[R] {

        override def all: Boolean = false

        override def parameterLength: Short = 1

    }

    private[otavia] case class FetchAllBindString[R <: Row](sql: String, decoder: Decoder[R], parm: String)
        extends FetchQuery[R] {

        override def all: Boolean = true

        override def parameterLength: Short = 1

    }

    private[otavia] case class FetchOneBindProduct[R <: Row](sql: String, decoder: Decoder[R], parm: Product)
        extends FetchQuery[R] {

        override def all: Boolean = false

        override def parameterLength: Short = parm.productArity.toShort

    }

    private[otavia] case class FetchAllBindProduct[R <: Row](sql: String, decoder: Decoder[R], parm: Product)
        extends FetchQuery[R] {

        override def all: Boolean = true

        override def parameterLength: Short = parm.productArity.toShort

    }

    private[otavia] case class PrepareUpdate(sql: String, param: Product) extends PrepareQuery[ModifyRows] {
        override def parameterLength: Short = param.productArity.toShort
    }

    private[otavia] case class PrepareUpdateBatch(sql: String, params: Seq[Product]) extends PrepareQuery[ModifyRows] {
        override def parameterLength: Short = params.head.productArity.toShort
    }

}
