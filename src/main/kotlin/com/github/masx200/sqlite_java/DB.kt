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

import com.google.common.eventbus.AsyncEventBus
import java.util.function.Consumer

//import lombok.NonNull;

interface DB : AutoCloseable {
    fun getAsyncEventBus(identifier: String): AsyncEventBus

    override fun close()

    fun findDifferenceTypeColumns(classes: Class<*>): List<String>


    fun createColumns(classes: Class<*>, vararg columnNames: String): List<String>

    fun dropColumns(classes: Class<*>, vararg columnNames: String): List<String>

    fun checkTableDifferenceInPrimaryKeyAndAutoIncrement(classes: Class<*>): Boolean

    fun dropUnusedColumns(vararg classes: Class<*>): List<String>

    fun tables(vararg classes: Class<*>): List<String>

    fun drop(vararg classes: Class<*>): List<String>

    fun create(vararg classes: Class<*>): List<String>

    fun version(): String

    fun <T : DataSupport<T>> insert(t: T): List<String>

    fun <T : DataSupport<T>> updateByPredicate(t: T, predicate: String?, vararg args: Any?): List<String>

    fun <T : DataSupport<T>> updateById(t: T): List<String>

    fun <T : DataSupport<T>> deleteByPredicate(tClass: Class<T>, predicate: String?, vararg args: Any?): List<String>

    fun <T : DataSupport<T>> deleteByListId(tClass: Class<T>, ids: List<Long>): List<String>

    fun <T : DataSupport<T>> deleteByVarargId(tClass: Class<T>, vararg ids: Long): List<String>

    fun <T : DataSupport<T>> deleteAll(tClass: Class<T>): List<String>

    fun <T : DataSupport<T>> findByConsumer(tClass: Class<T>, consumer: Consumer<Options>?): List<T>

    fun <T : DataSupport<T>> findByListId(tClass: Class<T>, ids: List<Long>): List<T>

    fun <T : DataSupport<T>> findByVarargId(tClass: Class<T>, vararg ids: Long): List<T>

    fun <T : DataSupport<T>> findAll(tClass: Class<T>): List<T>

    fun <T : DataSupport<T>> findOneByPredicate(tClass: Class<T>, predicate: String?, vararg args: Any?): T?

    fun <T : DataSupport<T>> findOneById(tClass: Class<T>, id: Long): T?

    fun <T : DataSupport<T>> firstByPredicate(tClass: Class<T>, predicate: String?, vararg args: Any?): T?

    fun <T : DataSupport<T>> first(tClass: Class<T>): T?

    fun <T : DataSupport<T>> lastByPredicate(tClass: Class<T>, predicate: String?, vararg args: Any?): T?

    fun <T : DataSupport<T>> last(tClass: Class<T>): T?

    fun <T : DataSupport<T>> countByPredicate(tClass: Class<T>, predicate: String?, vararg args: Any?): Long

    fun <T : DataSupport<T>> count(tClass: Class<T>): Long

    fun <T : DataSupport<T>> averageByPredicate(
        tClass: Class<T>,
        column: String,
        predicate: String?,
        vararg args: Any?
    ): Double

    fun <T : DataSupport<T>> average(tClass: Class<T>, column: String): Double

    fun <T : DataSupport<T>> sumByPredicate(
        tClass: Class<T>,
        column: String,
        predicate: String?,
        vararg args: Any?
    ): Number

    fun <T : DataSupport<T>> sum(tClass: Class<T>, column: String): Number

    fun <T : DataSupport<T>> maxByPredicate(
        tClass: Class<T>,
        column: String,
        predicate: String?,
        vararg args: Any?
    ): Number?

    fun <T : DataSupport<T>> max(tClass: Class<T>, column: String): Number?

    fun <T : DataSupport<T>> minByPredicate(
        tClass: Class<T>,
        column: String,
        predicate: String?,
        vararg args: Any?
    ): Number?

    fun <T : DataSupport<T>> min(tClass: Class<T>, column: String): Number?

    companion object {
        @JvmStatic
        fun connect(path: String): DB {
            return Core(path)
        }
    }
}