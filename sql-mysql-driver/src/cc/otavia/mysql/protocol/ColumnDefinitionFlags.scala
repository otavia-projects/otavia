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

package cc.otavia.mysql.protocol

/* https://dev.mysql.com/doc/dev/mysql-server/latest/group__group__cs__column__definition__flags.html */
object ColumnDefinitionFlags {

    val NOT_NULL_FLAG: Int                  = 0x00000001
    val PRI_KEY_FLAG: Int                   = 0x00000002
    val UNIQUE_KEY_FLAG: Int                = 0x00000004
    val MULTIPLE_KEY_FLAG: Int              = 0x00000008
    val BLOB_FLAG: Int                      = 0x00000010
    val UNSIGNED_FLAG: Int                  = 0x00000020
    val ZEROFILL_FLAG: Int                  = 0x00000040
    val BINARY_FLAG: Int                    = 0x00000080
    val ENUM_FLAG: Int                      = 0x00000100
    val AUTO_INCREMENT_FLAG: Int            = 0x00000200
    val TIMESTAMP_FLAG: Int                 = 0x00000400
    val SET_FLAG: Int                       = 0x00000800
    val NO_DEFAULT_VALUE_FLAG: Int          = 0x00001000
    val ON_UPDATE_NOW_FLAG: Int             = 0x00002000
    val NUM_FLAG: Int                       = 0x00008000
    val PART_KEY_FLAG: Int                  = 0x00004000
    val GROUP_FLAG: Int                     = 0x00008000
    val UNIQUE_FLAG: Int                    = 0x00010000
    val BINCMP_FLAG: Int                    = 0x00020000
    val GET_FIXED_FIELDS_FLAG: Int          = 0x00040000
    val FIELD_IN_PART_FUNC_FLAG: Int        = 0x00080000
    val FIELD_IN_ADD_INDEX: Int             = 0x00100000
    val FIELD_IS_RENAMED: Int               = 0x00200000
    val FIELD_FLAGS_STORAGE_MEDIA: Int      = 22
    val FIELD_FLAGS_STORAGE_MEDIA_MASK: Int = 3 << FIELD_FLAGS_STORAGE_MEDIA
    val FIELD_FLAGS_COLUMN_FORMAT: Int      = 24
    val FIELD_FLAGS_COLUMN_FORMAT_MASK: Int = 3 << FIELD_FLAGS_COLUMN_FORMAT
    val FIELD_IS_DROPPED: Int               = 0x04000000
    val EXPLICIT_NULL_FLAG: Int             = 0x08000000
    val FIELD_IS_MARKED: Int                = 0x10000000

}
