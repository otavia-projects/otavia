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
import cc.otavia.sql.{Row, RowCodec, RowSet}

sealed trait PrepareQuery[T <: Reply] extends Ask[T] {

    def sql: String

    def parameterLength: Short

}

object PrepareQuery {

    def updateWithProduct(sql: String, param: Product): PrepareQuery[ModifyRows] = PrepareUpdate(sql, param)

    def update[P <: Product](sql: String, param: P)(using codec: RowCodec[P]): PrepareQuery[ModifyRows] =
        PrepareUpdate(sql, param).withParameterCodec(codec)

    def insertWithProduct(sql: String, param: Product): PrepareQuery[ModifyRows] = PrepareUpdate(sql, param)

    def insert[P <: Product](sql: String, param: P)(using codec: RowCodec[P]): PrepareQuery[ModifyRows] =
        PrepareUpdate(sql, param).withParameterCodec(codec)

    def deleteWithProduct(sql: String, param: Product): PrepareQuery[ModifyRows] = PrepareUpdate(sql, param)
    def delete[P <: Product](sql: String, param: P)(using codec: RowCodec[P]): PrepareQuery[ModifyRows] =
        PrepareUpdate(sql, param).withParameterCodec(codec)

    def updateBatchWithProduct(sql: String, params: Seq[Product]): PrepareQuery[ModifyRows] =
        PrepareUpdateBatch(sql, params)

    def updateBatch[P <: Product](sql: String, params: Seq[P])(using codec: RowCodec[P]): PrepareQuery[ModifyRows] =
        PrepareUpdateBatch(sql, params).withParameterCodec(codec)

    def insertBatchWithProduct(sql: String, params: Seq[Product]): PrepareQuery[ModifyRows] =
        PrepareUpdateBatch(sql, params)

    def insertBatch[P <: Product](sql: String, params: Seq[P])(using codec: RowCodec[P]): PrepareQuery[ModifyRows] =
        PrepareUpdateBatch(sql, params).withParameterCodec(codec)

    def deleteBatchWithProduct(sql: String, params: Seq[Product]): PrepareQuery[ModifyRows] =
        PrepareUpdateBatch(sql, params)

    def deleteBatch[P <: Product](sql: String, params: Seq[P])(using codec: RowCodec[P]): PrepareQuery[ModifyRows] =
        PrepareUpdateBatch(sql, params).withParameterCodec(codec)

    def fetchOne[R <: Row](sql: String)(using codec: RowCodec[R]): PrepareQuery[R] =
        new FetchOneBindNothing[R](sql, codec)

    def fetchOne[R <: Row](sql: String, parm: Byte)(using codec: RowCodec[R]): PrepareQuery[R] =
        new FetchOneBindByte[R](sql, codec, parm)

    def fetchOne[R <: Row](sql: String, parm: Char)(using codec: RowCodec[R]): PrepareQuery[R] =
        new FetchOneBindChar[R](sql, codec, parm)

    def fetchOne[R <: Row](sql: String, parm: Boolean)(using codec: RowCodec[R]): PrepareQuery[R] =
        new FetchOneBindBoolean[R](sql, codec, parm)

    def fetchOne[R <: Row](sql: String, parm: Short)(using codec: RowCodec[R]): PrepareQuery[R] =
        new FetchOneBindShort[R](sql, codec, parm)

    def fetchOne[R <: Row](sql: String, parm: Int)(using codec: RowCodec[R]): PrepareQuery[R] =
        new FetchOneBindInt[R](sql, codec, parm)

    def fetchOne[R <: Row](sql: String, parm: Long)(using codec: RowCodec[R]): PrepareQuery[R] =
        new FetchOneBindLong[R](sql, codec, parm)

    def fetchOne[R <: Row](sql: String, parm: String)(using codec: RowCodec[R]): PrepareQuery[R] =
        new FetchOneBindString[R](sql, codec, parm)

    def fetchOne[R <: Row, P <: Product](sql: String, parm: P)(using
        codec: RowCodec[R],
        pcodec: RowCodec[P]
    ): PrepareQuery[R] = new FetchOneBindProduct[R](sql, parm, codec, pcodec)

    def fetchAll[R <: Product](sql: String)(using codec: RowCodec[R]): PrepareQuery[RowSet[R]] =
        new FetchAllBindNothing[R](sql, codec)

    def fetchAll[R <: Product](sql: String, parm: Byte)(using codec: RowCodec[R]): PrepareQuery[RowSet[R]] =
        new FetchAllBindByte[R](sql, codec, parm)

    def fetchAll[R <: Product](sql: String, parm: Char)(using codec: RowCodec[R]): PrepareQuery[RowSet[R]] =
        new FetchAllBindChar[R](sql, codec, parm)

    def fetchAll[R <: Product](sql: String, parm: Boolean)(using codec: RowCodec[R]): PrepareQuery[RowSet[R]] =
        new FetchAllBindBoolean[R](sql, codec, parm)

    def fetchAll[R <: Product](sql: String, parm: Short)(using codec: RowCodec[R]): PrepareQuery[RowSet[R]] =
        new FetchAllBindShort[R](sql, codec, parm)

    def fetchAll[R <: Product](sql: String, parm: Int)(using codec: RowCodec[R]): PrepareQuery[RowSet[R]] =
        new FetchAllBindInt[R](sql, codec, parm)

    def fetchAll[R <: Product](sql: String, parm: Long)(using codec: RowCodec[R]): PrepareQuery[RowSet[R]] =
        new FetchAllBindLong[R](sql, codec, parm)

    def fetchAll[R <: Product](sql: String, parm: String)(using codec: RowCodec[R]): PrepareQuery[RowSet[R]] =
        new FetchAllBindString[R](sql, codec, parm)

    def fetchAll[R <: Product, P <: Product](sql: String, parm: P)(using
        codec: RowCodec[R],
        pcodec: RowCodec[P]
    ): PrepareQuery[RowSet[R]] = new FetchAllBindProduct[R](sql, parm, codec, pcodec)

    private[otavia] trait FetchQuery[R <: Reply] extends PrepareQuery[R] {

        def all: Boolean

        def codec: RowCodec[?]

    }

    private[otavia] case class FetchOneBindNothing[R <: Row](sql: String, codec: RowCodec[R]) extends FetchQuery[R] {

        override def all: Boolean = false

        override def parameterLength: Short = 0

    }

    private[otavia] case class FetchAllBindNothing[R <: Product](sql: String, codec: RowCodec[R])
        extends FetchQuery[RowSet[R]] {

        override def all: Boolean = true

        override def parameterLength: Short = 0

    }

    private[otavia] case class FetchOneBindByte[R <: Row](sql: String, codec: RowCodec[?], parm: Byte)
        extends FetchQuery[R] {

        override def all: Boolean = false

        override def parameterLength: Short = 1

    }

    private[otavia] case class FetchAllBindByte[R <: Product](sql: String, codec: RowCodec[?], parm: Byte)
        extends FetchQuery[RowSet[R]] {

        override def all: Boolean = true

        override def parameterLength: Short = 1

    }

    private[otavia] case class FetchOneBindBoolean[R <: Row](sql: String, codec: RowCodec[?], parm: Boolean)
        extends FetchQuery[R] {

        override def all: Boolean = false

        override def parameterLength: Short = 1

    }

    private[otavia] case class FetchAllBindBoolean[R <: Product](sql: String, codec: RowCodec[?], parm: Boolean)
        extends FetchQuery[RowSet[R]] {

        override def all: Boolean = true

        override def parameterLength: Short = 1

    }

    private[otavia] case class FetchOneBindChar[R <: Row](sql: String, codec: RowCodec[R], parm: Char)
        extends FetchQuery[R] {

        override def all: Boolean = false

        override def parameterLength: Short = 1

    }

    private[otavia] case class FetchAllBindChar[R <: Product](sql: String, codec: RowCodec[R], parm: Char)
        extends FetchQuery[RowSet[R]] {

        override def all: Boolean = false

        override def parameterLength: Short = 1

    }

    private[otavia] case class FetchOneBindShort[R <: Row](sql: String, codec: RowCodec[R], parm: Short)
        extends FetchQuery[R] {

        override def all: Boolean = false

        override def parameterLength: Short = 1

    }

    private[otavia] case class FetchAllBindShort[R <: Product](sql: String, codec: RowCodec[R], parm: Short)
        extends FetchQuery[RowSet[R]] {

        override def all: Boolean = true

        override def parameterLength: Short = 1

    }

    private[otavia] case class FetchOneBindInt[R <: Row](sql: String, codec: RowCodec[R], parm: Int)
        extends FetchQuery[R] {

        override def all: Boolean = false

        override def parameterLength: Short = 1

    }

    private[otavia] case class FetchAllBindInt[R <: Product](sql: String, codec: RowCodec[R], parm: Int)
        extends FetchQuery[RowSet[R]] {

        override def all: Boolean = true

        override def parameterLength: Short = 1

    }

    private[otavia] case class FetchOneBindLong[R <: Row](sql: String, codec: RowCodec[R], parm: Long)
        extends FetchQuery[R] {

        override def all: Boolean = false

        override def parameterLength: Short = 1

    }

    private[otavia] case class FetchAllBindLong[R <: Product](sql: String, codec: RowCodec[R], parm: Long)
        extends FetchQuery[RowSet[R]] {

        override def all: Boolean = true

        override def parameterLength: Short = 1

    }

    private[otavia] case class FetchOneBindString[R <: Row](sql: String, codec: RowCodec[R], parm: String)
        extends FetchQuery[R] {

        override def all: Boolean = false

        override def parameterLength: Short = 1

    }

    private[otavia] case class FetchAllBindString[R <: Product](sql: String, codec: RowCodec[R], parm: String)
        extends FetchQuery[RowSet[R]] {

        override def all: Boolean = true

        override def parameterLength: Short = 1

    }

    private[otavia] case class FetchOneBindProduct[R <: Row](
        sql: String,
        parm: Product,
        codec: RowCodec[R],
        pcodec: RowCodec[?]
    ) extends FetchQuery[R] {

        override def all: Boolean = false

        override def parameterLength: Short = parm.productArity.toShort

    }

    private[otavia] case class FetchAllBindProduct[R <: Product](
        sql: String,
        parm: Product,
        codec: RowCodec[R],
        pcodec: RowCodec[?]
    ) extends FetchQuery[RowSet[R]] {

        override def all: Boolean = true

        override def parameterLength: Short = parm.productArity.toShort

    }

    private[otavia] case class PrepareUpdate(sql: String, param: Product) extends PrepareQuery[ModifyRows] {

        private var codec: RowCodec[?] = _

        override def parameterLength: Short = param.productArity.toShort

        def withParameterCodec(codec: RowCodec[?]): this.type = {
            this.codec = codec
            this
        }

        def parameterCodec: RowCodec[?] = codec

    }

    private[otavia] case class PrepareUpdateBatch(sql: String, params: Seq[Product]) extends PrepareQuery[ModifyRows] {

        private var codec: RowCodec[?] = _

        override def parameterLength: Short = params.head.productArity.toShort

        def withParameterCodec(codec: RowCodec[?]): this.type = {
            this.codec = codec
            this
        }

        def parameterCodec: RowCodec[?] = codec

    }

}
