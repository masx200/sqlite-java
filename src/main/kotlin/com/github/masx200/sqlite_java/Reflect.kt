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

import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.sql.ResultSet
import java.util.*
import java.util.function.BiConsumer
import java.util.function.Consumer
import kotlin.jvm.functions.Function1

internal class Reflect<T> {
    val fieldMap: MutableMap<String, Field> = LinkedHashMap<String, Field>()
    private var tClass: Class<*>? = null
    private var t: T? = null

    constructor(tClass: Class<*>) {
        this.tClass = tClass
        newInstance(tClass)
    }

    constructor(t: T?) {
        this.t = t
        newInstance(t!!.javaClass)
    }

    private fun newInstance(tClass: Class<*>?) {
        var clazz = tClass
        while (clazz != null) {
            for (field in clazz.getDeclaredFields()) {
                field.setAccessible(true)
                // 忽略静态字段
                if (!isIgnore(field) && (!Modifier.isStatic(field.modifiers))) {
                    fieldMap.putIfAbsent(field.name.lowercase(Locale.getDefault()), field)
                }
            }
            clazz = clazz.getSuperclass()
        }
    }

    fun setValue(fieldName: String, value: Any?) {
        try {
            val field = fieldMap.getOrDefault(fieldName.lowercase(Locale.getDefault()), null)
            if (field != null) {
                field.set(t, value)
            }
        } catch (e: IllegalAccessException) {
            throw RuntimeException(e)
        }
    }

    fun getValue(fieldName: String): Any? {
        try {
            val field = fieldMap.getOrDefault(fieldName.lowercase(Locale.getDefault()), null)
            return if (field != null) field.get(t) else null
        } catch (e: IllegalAccessException) {
            throw RuntimeException(e)
        }
    }

    fun getType(fieldName: String): Class<*>? {
        val field = fieldMap.getOrDefault(fieldName.lowercase(Locale.getDefault()), null)
        return field?.type
    }

    fun getDatabaseType(fieldName: String): String {
        when (getType(fieldName.lowercase(Locale.getDefault()))?.getSimpleName()?.lowercase(Locale.getDefault())) {
            "int", "integer", "byte", "short", "long" -> return "integer"
            "float", "double" -> return "real"
            "char", "character", "string" -> return "text"
            "boolean" -> return "blob"
            else -> throw NullPointerException()
        }
    }

    fun getDBValue(field: Field): Any? {
        try {
            val fieldName = field.name.lowercase(Locale.getDefault())
            val dbField = fieldMap.getOrDefault(fieldName, null)
            val dbValue = if (dbField != null) dbField.get(t) else null
            if (dbField != null && dbValue != null) {
                if (isJson(field)) {
                    return String.format("'%s'", Core.Companion.gson.toJson(dbValue))
                }
                when (getDatabaseType(fieldName)) {
                    "text" -> return String.format("'%s'", dbValue)
                    "blob" -> return if (dbValue == true) 1 else 0
                    else -> return dbValue
                }
            }
            return null
        } catch (e: IllegalAccessException) {
            throw RuntimeException(e)
        }
    }

    fun getDBColumnsWithValue(consumer: BiConsumer<String, Any?>) {
        for (field in fieldMap.values) {
            consumer.accept(field.name, getDBValue(field))
        }
    }

    fun getDBColumnsWithType(consumer: BiConsumer<String, String>) {
        for (field in fieldMap.values) {
            if (isJson(field)) {
                consumer.accept(field.name.lowercase(Locale.getDefault()), "text")
                continue
            }
            consumer.accept(field.name.lowercase(Locale.getDefault()), getDatabaseType(field.name))
        }
    }

    fun getIndexList(consumer: Consumer<IndexesData2>) {
        val table = getTableNameFromClass(tClass!!)
        fieldMap.values.forEach(Consumer { field: Field? ->
            if (isIndex(field!!)) {
                val column = field.name.lowercase(Locale.getDefault())
                val index = String.format("idx_%s_%s", table, column)
                consumer.accept(IndexesData2(index, column, isUnique(field)))
            }
        })
    }

    fun get(): T? {
        return t
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun <T> toEntity(tClass: Class<T>, options: Options?, resultSet: ResultSet): T? {
            try {
                val columnsMap: MutableMap<String, Boolean?> = HashMap<String, Boolean?>()
                if (options != null && options.selectColumns != null && (options.selectColumns != "*")) {
                    val columns: Array<String> =
                        options.selectColumns.split(", ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    for (column in columns) {
                        columnsMap.put(column, true)
                    }
                }
                var t: T?
                try {
                    // 尝试使用无参构造函数创建对象
                    t = tClass.getConstructor().newInstance()
                } catch (e: NoSuchMethodException) {
                    // 如果没有无参构造函数，则使用带 Consumer 参数的构造函数
                    try {
                        t = tClass.getConstructor(Function1::class.java)
                            .newInstance(({ c: T? -> }) as Function1<T?, Unit>)
                    } catch (e2: NoSuchMethodException) {
                        t = tClass.getConstructor(Consumer::class.java).newInstance((Consumer { c: T? -> }))
                    }
                }

                val reflect = Reflect<T?>(t)
                for (field in reflect.fieldMap.values) {
                    val name = field.name
                    if (!columnsMap.isEmpty() && !columnsMap.getOrDefault(name, false)!!) {
                        continue
                    }
                    if (isJson(field)) {
                        reflect.setValue(name, Core.Companion.gson.fromJson(resultSet.getString(name), field.type))
                        continue
                    }
                    val type = field.type.getSimpleName().lowercase(Locale.getDefault())
                    when (type) {
                        "int", "integer" -> reflect.setValue(name, resultSet.getInt(name))
                        "byte" -> reflect.setValue(name, resultSet.getByte(name))
                        "short" -> reflect.setValue(name, resultSet.getShort(name))
                        "long" -> reflect.setValue(name, resultSet.getLong(name))
                        "float" -> reflect.setValue(name, resultSet.getFloat(name))
                        "double" -> reflect.setValue(name, resultSet.getDouble(name))
                        "char", "character", "string" -> reflect.setValue(name, resultSet.getString(name))
                        "boolean" -> reflect.setValue(name, resultSet.getBoolean(name))
                    }
                }
                return reflect.get()
            } catch (e: Exception) {
                e.printStackTrace()
                throw RuntimeException(e)
            }
        }

        fun isPrimaryKey(field: Field): Boolean {
            if (field.isAnnotationPresent(Column::class.java)) {
                val column = field.getAnnotation<Column>(Column::class.java)
                return column.primaryKey
            }
            return false
        }

        fun isAutoIncrement(field: Field): Boolean {
            if (field.isAnnotationPresent(Column::class.java)) {
                val column = field.getAnnotation<Column>(Column::class.java)
                return column.autoIncrement
            }
            return false
        }

        fun isIgnore(field: Field): Boolean {
            if (field.isAnnotationPresent(Column::class.java)) {
                val column = field.getAnnotation<Column>(Column::class.java)
                return column.ignore
            }
            return false
        }

        fun isUnique(field: Field): Boolean {
            if (field.isAnnotationPresent(Column::class.java)) {
                val column = field.getAnnotation<Column>(Column::class.java)
                return column.unique
            }
            return false
        }

        fun isIndex(field: Field): Boolean {
            if (field.isAnnotationPresent(Column::class.java)) {
                val column = field.getAnnotation<Column>(Column::class.java)
                return column.index
            }
            return false
        }

        fun isJson(field: Field): Boolean {
            if (field.isAnnotationPresent(Column::class.java)) {
                val column = field.getAnnotation<Column>(Column::class.java)
                return column.json
            }
            return false
        }

        fun getColumn(field: Field): Column? {
            if (field.isAnnotationPresent(Column::class.java)) {
                return field.getAnnotation<Column?>(Column::class.java)
            } else {
                return null
            }
        }
    }
}

