/**
 * Copyright 2023 Zhang Guanhu
 *
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.masx200.sqlite_java

import com.github.masx200.sqlite_java.Reflect.Companion.isAutoIncrement
import com.github.masx200.sqlite_java.Reflect.Companion.isPrimaryKey
import java.util.*
import java.util.function.BiConsumer

internal object SQLTemplate {
    fun create(tClass: Class<*>): String {


        val reflect = Reflect<Any?>(tClass)
//        println(reflect.fieldMap)
//        reflect.fieldMap.forEach { (key, value) ->
//            println("$key -> $value,isPrimaryKey:${isPrimaryKey(value)}")
//
//            println("$key -> $value,isAutoIncrement:${isAutoIncrement(value)}")
//
//        }
        val field = reflect.fieldMap["id"]
        val columnsString = StringBuffer(
            "id integer " + when {

                field != null && isPrimaryKey(field) -> "primary key"
                else -> ""
            } + " " + when {

                field != null && isAutoIncrement(field) -> "autoincrement"
                else -> ""
            } + ","
        )
        reflect.getDBColumnsWithType(BiConsumer { column: String?, type: String? ->
            if (column != "id") {
                val field = reflect.fieldMap[column]
                columnsString.append(column).append(" ").append(
                    type + " " + when {

                        field != null && isAutoIncrement(field) -> "autoincrement"
                        else -> ""
                    }
                ).append(",")
            }
        })
        columnsString.deleteCharAt(columnsString.length - 1)
        val tableName = getTableNameFromClass(tClass)
        return `$`("create table %s (%s);", tableName, columnsString)
    }

    fun addTableColumn(tableName: String?, column: String, type: String?): String {
//        if (column != null) {
        return `$`("alter table %s add column %s %s;", tableName, column.lowercase(Locale.getDefault()), type)
//        }
//        throw IllegalArgumentException("column can not be null")
    }

    fun drop(tClass: Class<*>): String {
        return `$`("drop table %s;", getTableNameFromClass(tClass))
    }

    fun <T> insert(t: T?): String {
        val columnsString = StringBuffer()
        val valueString = StringBuffer()
        Reflect<T?>(t).getDBColumnsWithValue(BiConsumer { column: String?, value: Any? ->
            if (column != "id") {
                columnsString.append(column).append(",")
                valueString.append(value).append(",")
            }
        })
        columnsString.deleteCharAt(columnsString.length - 1)
        valueString.deleteCharAt(valueString.length - 1)
        val tableName = getTableNameFromClass(t!!.javaClass)
        return `$`("insert into %s (%s) values (%s);", tableName, columnsString, valueString)
    }

    fun <T> update(t: T?, options: Options): String {
        val tableName = getTableNameFromClass(t!!.javaClass)
        val whereString = if (options.wherePredicate != null) `$`("where %s ", options.wherePredicate) else ""
        val setString = StringBuffer()
        Reflect<T?>(t).getDBColumnsWithValue(BiConsumer { column: String?, value: Any? ->
            if (value != null && column != "id") {
                setString.append(column).append(" = ").append(value).append(",")
            }
        })
        setString.deleteCharAt(setString.length - 1)
        val SQLBuilder = StringBuilder()
        return SQLBuilder
            .append(`$`("update %s set %s ", tableName, setString))
            .append(whereString)
            .append(";")
            .deleteCharAt(SQLBuilder.length - 2)
            .toString()
    }

    fun <T> delete(tClass: Class<T>, options: Options): String {
        val deleteString = `$`("delete from %s ", getTableNameFromClass(tClass))
        val whereString = if (options.wherePredicate != null) `$`("where %s ", options.wherePredicate) else ""
        val SQLBuilder = StringBuilder()
        return SQLBuilder
            .append(deleteString)
            .append(whereString)
            .append(";")
            .deleteCharAt(SQLBuilder.length - 2)
            .toString()
    }

    fun <T> query(table: String?, options: Options?): String {
        if (options == null) {
            return `$`("select * from %s;", table)
        }
        val fromString = `$`("from %s ", table)
        val selectString = `$`("select %s ", Optional.ofNullable<String?>(options.selectColumns).orElse("*"))
        val whereString = if (options.wherePredicate != null) `$`("where %s ", options.wherePredicate) else ""
        val groupString = if (options.groupColumns != null) `$`("group by %s ", options.groupColumns) else ""
        val orderString = if (options.orderColumns != null) `$`("order by %s ", options.orderColumns) else ""
        val limitString = if (options.limitSize != null) `$`("limit %d ", options.limitSize) else ""
        val offsetString = if (options.offsetSize != null) `$`("offset %d ", options.offsetSize) else ""
        val SQLBuilder = StringBuilder()
        return SQLBuilder
            .append(selectString)
            .append(fromString)
            .append(whereString)
            .append(groupString)
            .append(orderString)
            .append(limitString)
            .append(offsetString)
            .append(";")
            .deleteCharAt(SQLBuilder.length - 2)
            .toString()
    }

    fun <T> query(tClass: Class<T>, options: Options?): String {
        return query<Any?>(getTableNameFromClass(tClass), options)
    }

    fun createIndex(tClass: Class<*>, column: String?, unique: Boolean?): String {
        val table = getTableNameFromClass(tClass)
        val index = `$`("idx_%s_%s", table, column)
        return `$`(
            "create " +
                    if (unique == true) "unique" else ""

                            + " index %s on %s(%s)", index, table, column
        )
    }

    fun <T> dropIndex(index: String?): String {
        return `$`("drop index %s", index)
    }

    private fun `$`(format: String, vararg objects: Any?): String {
        return String.format(format, *objects)
    }
//不能修改列的类型语法错误
//    fun alterTableColumn(table_name: String, column_name: String, column_type: String?): String {
//        return "alter table $table_name alter column ${column_name.lowercase(Locale.getDefault())} $column_type"
////        println(
////            """
////                alterTableColumn()
////                string: $string
////                string1: $string1
////                string2: $string2
////                """.trimIndent()
////        )
////
//    }

    fun dropTableColumn(table_name: String, column_name: String): String {
//        println(
//            """
//                dropTableColumn()
//                p0: $table_name
//                p1: $column_name
//                """.trimIndent()
//        )
        return "ALTER TABLE $table_name DROP COLUMN ${column_name.lowercase(Locale.getDefault())} ;"
    }
}
