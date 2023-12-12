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

package cc.otavia.postgres.protocol

import java.sql.JDBCType
import scala.language.unsafeNulls

enum DataType(
    val id: Int,
    val supportsBinary: Boolean = true,
    val jdbcType: JDBCType = JDBCType.OTHER,
    val array: Boolean = false
) {

    case BOOL        extends DataType(16, true, JDBCType.BOOLEAN)
    case INT2        extends DataType(21, true, JDBCType.SMALLINT)
    case INT4        extends DataType(23, true, JDBCType.INTEGER)
    case INT8        extends DataType(20, true, JDBCType.BIGINT)
    case FLOAT4      extends DataType(700, true, JDBCType.REAL)
    case FLOAT8      extends DataType(701, true, JDBCType.DOUBLE)
    case NUMERIC     extends DataType(1700, true, JDBCType.NUMERIC)
    case MONEY       extends DataType(790, true, null)
    case BIT         extends DataType(1560, true, JDBCType.BIT)
    case VARBIT      extends DataType(1562)
    case CHAR        extends DataType(18, true, JDBCType.BIT)
    case VARCHAR     extends DataType(1043, true, JDBCType.VARCHAR)
    case BPCHAR      extends DataType(1042, true, JDBCType.VARCHAR)
    case TEXT        extends DataType(25, true, JDBCType.LONGVARCHAR)
    case NAME        extends DataType(19, true, JDBCType.VARCHAR)
    case DATE        extends DataType(1082, true, JDBCType.DATE)
    case TIME        extends DataType(1083, true, JDBCType.TIME)
    case TIMETZ      extends DataType(1266, true, JDBCType.TIME_WITH_TIMEZONE)
    case TIMESTAMP   extends DataType(1114, true, JDBCType.TIMESTAMP)
    case TIMESTAMPTZ extends DataType(1184, true, JDBCType.TIMESTAMP_WITH_TIMEZONE)
    case INTERVAL    extends DataType(1186, true, JDBCType.DATE)
    case BYTEA       extends DataType(17, true, JDBCType.BINARY)
    case MACADDR     extends DataType(829)
    case INET        extends DataType(869)
    case CIDR        extends DataType(650)
    case MACADDR8    extends DataType(774)
    case UUID        extends DataType(2950)
    case JSON        extends DataType(114)
    case JSONB       extends DataType(3802)
    case XML         extends DataType(142)
    case POINT       extends DataType(600)
    case LINE        extends DataType(628)
    case LSEG        extends DataType(601)
    case BOX         extends DataType(603)
    case PATH        extends DataType(602)
    case POLYGON     extends DataType(604)
    case CIRCLE      extends DataType(718)
    case HSTORE      extends DataType(33670)
    case OID         extends DataType(26)
    case VOID        extends DataType(2278)
    case UNKNOWN     extends DataType(705, false)
    case TS_VECTOR   extends DataType(3614, false)
    case TS_QUERY    extends DataType(3615, false)

    case BOOL_ARRAY extends DataType(1000, true, JDBCType.BOOLEAN, true)
    case INT2_ARRAY extends DataType(1005, true, JDBCType.SMALLINT, true)
    case INT4_ARRAY extends DataType(1007, true, JDBCType.INTEGER, true)
    case INT8_ARRAY extends DataType(1016, true, JDBCType.BIGINT, true)

}

object DataType {
    def fromOid(oid: Int): DataType = oid match
        case 16    => DataType.BOOL
        case 21    => DataType.INT2
        case 23    => DataType.INT4
        case 20    => DataType.INT8
        case 700   => DataType.FLOAT4
        case 701   => DataType.FLOAT8
        case 1700  => DataType.NUMERIC
        case 790   => DataType.MONEY
        case 1560  => DataType.BIT
        case 1561  => DataType.VARBIT
        case 18    => DataType.CHAR
        case 1043  => DataType.VARCHAR
        case 1042  => DataType.BPCHAR
        case 25    => DataType.TEXT
        case 19    => DataType.NAME
        case 1082  => DataType.DATE
        case 1083  => DataType.TIME
        case 1266  => DataType.TIMETZ
        case 1114  => DataType.TIMESTAMP
        case 1184  => DataType.TIMESTAMPTZ
        case 1186  => DataType.INTERVAL
        case 17    => DataType.BYTEA
        case 829   => DataType.MACADDR
        case 869   => DataType.INET
        case 650   => DataType.CIDR
        case 774   => DataType.MACADDR8
        case 2950  => DataType.UUID
        case 114   => DataType.JSON
        case 3820  => DataType.JSONB
        case 142   => DataType.XML
        case 600   => DataType.POINT
        case 628   => DataType.LINE
        case 601   => DataType.LSEG
        case 603   => DataType.BOX
        case 602   => DataType.PATH
        case 604   => DataType.POLYGON
        case 718   => DataType.CIRCLE
        case 33670 => DataType.HSTORE
        case 26    => DataType.OID
        case 2278  => DataType.VOID
        case 705   => DataType.UNKNOWN
        case 3614  => DataType.TS_VECTOR
        case 3615  => DataType.TS_QUERY

}
