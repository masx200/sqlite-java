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


import com.github.masx200.sqlite_java.MyEvent
import com.github.masx200.sqlite_java.Reflect.Companion.isAutoIncrement
import com.github.masx200.sqlite_java.Reflect.Companion.isPrimaryKey
import com.github.masx200.sqlite_java.SQLiteIndexFetcher.fetchIndexes
import com.google.common.eventbus.AsyncEventBus
import com.google.common.util.concurrent.MoreExecutors
import com.google.gson.Gson
import java.nio.file.Files
import java.nio.file.Paths
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantLock
import java.util.function.Consumer

internal class Core(var path: String) : DB {
    private val lock = ReentrantLock()

    private var connection: Connection? = null

    init {
        try {
            val databasePath = Paths.get(path)
            val parentPath = databasePath.parent
            if (parentPath != null) {
                Files.createDirectories(parentPath)
            }
            Class.forName("org.sqlite.JDBC")
            connection = DriverManager.getConnection("jdbc:sqlite:" + path)
            Runtime.getRuntime().addShutdownHook(Thread(Runnable { this.close() }))
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    fun getReflectInfo(vararg classes: Class<*>): TableReflectInfo {
        val tablesMapIndexesData1: HashMap<String, List<IndexesData1>> = HashMap<String, List<IndexesData1>>()
        // 存储表名和其列类型映射的Map
        val tablesMapTypes = HashMap<String, HashMap<String, String>>()

        // 存储表名和其主键列名映射的Map
        val tablesMapPrimaryKeys = HashMap<String, String>()
        // 存储表名和其列是否自动增长映射的Map
        val tablesMapIsAutoIncrement = HashMap<String, HashMap<String, Boolean>>()
        // 存储索引名和列名映射的Map

        var tableNameSet = HashSet<String>()
        for (tClass in classes) {
            val tableName = getTableNameFromClass(tClass)
            tableNameSet.add(tableName)
        }
        for (tClass in classes) {

            val tableName = getTableNameFromClass(tClass)
            val reflect: Reflect<*> = Reflect<Any?>(tClass)

            val classColumns = hashMapOf<String, String>()
            reflect.getDBColumnsWithType { column: String, type: String ->
                classColumns.put(column, type)
            }
            tablesMapTypes.put(tableName, classColumns)
            val MapIsAutoIncrement = HashMap<String, Boolean>()

            for (field in reflect.fieldMap) {
                if (isPrimaryKey(field.value)) {
                    tablesMapPrimaryKeys.put(tableName, field.key.lowercase())
                }
                MapIsAutoIncrement.put(field.key.lowercase(), isAutoIncrement(field.value))
            }

            tablesMapIsAutoIncrement.put(tableName, MapIsAutoIncrement)
            val indexes = mutableListOf<IndexesData1>()
            reflect.getIndexList {
                val indexName = it.name
                val columnName = it.column
                if (true) {
                    indexes.add(IndexesData1(it.unique, indexName, listOf(columnName)))
                }
            }
            tablesMapIndexesData1[tableName] = indexes
        }

        return TableReflectInfo(
            tablesMapTypes,
            tablesMapPrimaryKeys, tablesMapIsAutoIncrement, tablesMapIndexesData1
        )
    }

    override fun getAsyncEventBus(identifier: String): AsyncEventBus {
        return asyncEventBusMap.computeIfAbsent(identifier) {
            AsyncEventBus(MoreExecutors.listeningDecorator(executorService))
        }
    }

    private val asyncEventBusMap: MutableMap<String, AsyncEventBus> = ConcurrentHashMap()
    private val executorService: ExecutorService = Executors.newCachedThreadPool()
    override fun close() {
        try {

            executorService.shutdown()
        } catch (e: SQLException) {
            throw RuntimeException(e)
        }
        try {
            connection!!.close()
        } catch (e: SQLException) {
            throw RuntimeException(e)
        }
    }

    override fun findDifferenceTypeColumns(classes: Class<*>): List<String> {
        var differenceTypeColumns: MutableList<String> = mutableListOf()
        val tablesMapTypes = HashMap<String, HashMap<String, String>>()

        // 存储表名和其主键列名映射的Map
        val tablesMapPrimaryKeys = HashMap<String, String>()
        // 存储表名和其列是否自动增长映射的Map
        val tablesMapIsAutoIncrement = HashMap<String, HashMap<String, Boolean>>()
        // 存储索引名和列名映射的Map
        val indexMapColumns = HashMap<String, String>()
        val indexMapTables = HashMap<String, String>()

        val s = SQLTemplate.query<Any?>("sqlite_master", Options().where("type = ?", "table"))
        var tableNameSet = HashSet<String>()
        for (tClass in listOf<Class<*>>(classes)) {
            val tableName = getTableNameFromClass(tClass)
            tableNameSet.add(tableName)
        }
        try {
            connection!!.createStatement().use { statement ->
                statement.executeQuery(s).use { result ->
                    val metaData = connection!!.metaData
                    while (result.next()) {
                        val tableColumnTypeMap = HashMap<String, String>()
                        val tableColumnTypeMapisAutoIncrement = HashMap<String, Boolean>()
                        val tableName = result.getString("name")
                        if (tableNameSet.contains(tableName)) {
                            metaData.getPrimaryKeys(null, null, tableName).use { primaryKeySet ->
                                while (primaryKeySet.next()) {
                                    val primaryKeyColumn =
                                        primaryKeySet.getString("COLUMN_NAME").lowercase(Locale.getDefault())
//                                println("Column $primaryKeyColumn in table $tableName is a primary key")
                                    tablesMapPrimaryKeys.put(tableName, primaryKeyColumn)
                                }

                            }
                            metaData.getColumns(null, null, tableName, null).use { set ->
//                            println(set.metaData.isAutoIncrement())


                                while (set.next()) {
                                    val isAutoIncrement =
                                        set.getString("IS_AUTOINCREMENT").lowercase(Locale.getDefault())

//
                                    val column = set.getString("COLUMN_NAME").lowercase(Locale.getDefault())
                                    val type = set.getString("TYPE_NAME").lowercase(Locale.getDefault())
                                    tableColumnTypeMap.put(column, type)
                                    tableColumnTypeMapisAutoIncrement.put(column, isAutoIncrement == "yes")
                                }
                            }
                            tablesMapTypes.put(tableName, tableColumnTypeMap)
                            tablesMapIsAutoIncrement.put(tableName, tableColumnTypeMapisAutoIncrement)
                            metaData.getIndexInfo(null, null, tableName, false, false).use { set ->
                                while (set.next()) {
//                                println(set.getString("TABLE_NAME"))
                                    val index = set.getString("INDEX_NAME")
                                    val column = set.getString("COLUMN_NAME")
                                    if (index != null) {
                                        indexMapTables.put(
                                            index.lowercase(Locale.getDefault()),
                                            tableName.lowercase(Locale.getDefault())
                                        )
                                    }
                                    Optional.ofNullable<String>(index)
                                        .ifPresent { i: String ->
                                            indexMapColumns.put(
                                                index.lowercase(Locale.getDefault()),
                                                column.lowercase(Locale.getDefault())
                                            )
                                        }
                                }
                            }
                        }
                    }
                    for (tClass in listOf<Class<*>>(classes)) {
                        val tableName = getTableNameFromClass(tClass)
                        val tableColumnTypeMap = tablesMapTypes.getOrDefault(tableName, null)
//                        val dbColumns = tableColumnTypeMap?.keys?.toSet()
                        val reflect: Reflect<*> = Reflect<Any?>(tClass)
                        val classColumns = mutableMapOf<String, String>()
                        reflect.getDBColumnsWithType { column: String, type: String ->
                            classColumns.put(column, type)
                        }
//                        println(
//                            dbColumns
//                        )
//                        println(
//                            classColumns
//                        )
                        if (tableColumnTypeMap == null) {
//                            var sql = SQLTemplate.create(tClass)
//                            resultList.add(sql)
//                            statement.executeUpdate(sql)
                            throw RuntimeException("table $tableName not found")
                        } else {

//                            println(tableColumnTypeMap)
                            reflect.getDBColumnsWithType { column: String, type: String ->

//                                if (tableColumnTypeMap.getOrDefault(column, null) == null) {
//                                    try {
//                                        column?.let {
//                                            var sql = SQLTemplate.addTableColumn(
//                                                tableName,
//                                                it,
//                                                type
//                                            )
////                                            resultList.add(sql)
//                                            statement.executeUpdate(
//                                                sql
//                                            )
//                                        }
//                                    } catch (e: SQLException) {
//                                        e.printStackTrace()
//                                        throw RuntimeException(e)
//                                    }
//                                }
//检查列类型并修改：在遍历类的列时，如果数据库中的列类型与类中的列类型不同，则执行 alterTableColumn 操作来修改列类型。
                                if (tableColumnTypeMap[column] != type) {
                                    column.let { differenceTypeColumns.add(it) }
                                }
//                                    try {
//                                        column?.let {
//                                            var sql1 = SQLTemplate.dropTableColumn(
//                                                tableName,
//                                                it,
//
//                                                )
//                                            resultList.add(sql1)
//                                            statement.executeUpdate(
//                                                sql1
//                                            )
//
//                                            var sql = SQLTemplate.addTableColumn(
//                                                tableName,
//                                                it,
//                                                type
//                                            )
//                                            resultList.add(sql)
//                                            statement.executeUpdate(
//                                                sql
//                                            )
//                                        }
//                                    } catch (e: SQLException) {
//                                        throw RuntimeException(e)
//                                    }
//                                }
                            }
//                            删除多余字段：在遍历数据库中的列时，如果数据库中的列在类中不存在，则执行 dropTableColumn 操作来删除该列。
//                            dbColumns?.filter { !classColumns.contains(it) }?.forEach { column ->
//                                try {
//                                    column?.let { statement.executeUpdate(SQLTemplate.dropTableColumn(tableName, it)) }
//                                } catch (e: SQLException) {
//                                    throw RuntimeException(e)
//                                }
//                            }
                        }


                    }

                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw RuntimeException(e)
        }
        return differenceTypeColumns
    }

    override fun createColumns(classes: Class<*>, vararg columnNames: String): MutableList<String> {
        var toSet = columnNames.toSet()
        var resultList = mutableListOf<String>()
        val tablesMapTypes = HashMap<String, HashMap<String, String>>()

        // 存储表名和其主键列名映射的Map
        val tablesMapPrimaryKeys = HashMap<String, String>()
        // 存储表名和其列是否自动增长映射的Map
        val tablesMapIsAutoIncrement = HashMap<String, HashMap<String, Boolean>>()
        // 存储索引名和列名映射的Map


        val s = SQLTemplate.query<Any?>("sqlite_master", Options().where("type = ?", "table"))
        var tableNameSet = HashSet<String>()
        for (tClass in listOf<Class<*>>(classes)) {
            val tableName = getTableNameFromClass(tClass)
            tableNameSet.add(tableName)
        }
        try {
            connection!!.createStatement().use { statement ->
                statement.executeQuery(s).use { result ->
                    val metaData = connection!!.metaData
                    while (result.next()) {
                        val tableColumnTypeMap = HashMap<String, String>()
                        val tableColumnTypeMapisAutoIncrement = HashMap<String, Boolean>()
                        val tableName = result.getString("name")
                        if (tableNameSet.contains(tableName)) {
                            metaData.getPrimaryKeys(null, null, tableName).use { primaryKeySet ->
                                while (primaryKeySet.next()) {
                                    val primaryKeyColumn =
                                        primaryKeySet.getString("COLUMN_NAME").lowercase(Locale.getDefault())
//                                println("Column $primaryKeyColumn in table $tableName is a primary key")
                                    tablesMapPrimaryKeys.put(tableName, primaryKeyColumn)
                                }

                            }
                            metaData.getColumns(null, null, tableName, null).use { set ->
//                            println(set.metaData.isAutoIncrement())


                                while (set.next()) {
                                    val isAutoIncrement =
                                        set.getString("IS_AUTOINCREMENT").lowercase(Locale.getDefault())

//
                                    val column = set.getString("COLUMN_NAME").lowercase(Locale.getDefault())
                                    val type = set.getString("TYPE_NAME").lowercase(Locale.getDefault())
                                    tableColumnTypeMap.put(column, type)
                                    tableColumnTypeMapisAutoIncrement.put(column, isAutoIncrement == "yes")
                                }
                            }
                            tablesMapTypes.put(tableName, tableColumnTypeMap)
                            tablesMapIsAutoIncrement.put(tableName, tableColumnTypeMapisAutoIncrement)

                        }
                    }

                    for (tClass in listOf<Class<*>>(classes)) {
                        val tableName = getTableNameFromClass(tClass)
                        val tableColumnTypeMap = tablesMapTypes.getOrDefault(tableName, null)
//                        val dbColumns = tableColumnTypeMap?.keys?.toSet()
                        val reflect: Reflect<*> = Reflect<Any?>(tClass)
                        val classColumns = mutableMapOf<String, String>()
                        reflect.getDBColumnsWithType { column: String, type: String ->
                            classColumns.put(column, type)
                        }
//                        println(
//                            dbColumns
//                        )
//                        println(
//                            classColumns
//                        )
                        if (tableColumnTypeMap == null) {
                            throw RuntimeException("table $tableName not found")
                        } else {
//                            println(tableColumnTypeMap)
                            reflect.getDBColumnsWithType { column: String, type: String ->

                                if (toSet.contains(column) && tableColumnTypeMap.getOrDefault(column, null) == null) {
                                    try {
                                        column.let {
                                            var sql = SQLTemplate.addTableColumn(
                                                tableName,
                                                it,
                                                type
                                            )
                                            var asyncEventBus = getAsyncEventBus("alter")
                                            asyncEventBus.post(MyEvent(sql))
                                            resultList.add(sql)
                                            statement.executeUpdate(
                                                sql
                                            )
                                        }
                                    } catch (e: SQLException) {
                                        e.printStackTrace()
                                        throw RuntimeException(e)
                                    }
                                }
//检查列类型并修改：在遍历类的列时，如果数据库中的列类型与类中的列类型不同，则执行 alterTableColumn 操作来修改列类型。
//                                else if (tableColumnTypeMap[column] != type) {
//                                    try {
//                                        column?.let {
//                                            var sql1 = SQLTemplate.dropTableColumn(
//                                                tableName,
//                                                it,
//
//                                                )
//                                            resultList.add(sql1)
//                                            statement.executeUpdate(
//                                                sql1
//                                            )
//
//                                            var sql = SQLTemplate.addTableColumn(
//                                                tableName,
//                                                it,
//                                                type
//                                            )
//                                            resultList.add(sql)
//                                            statement.executeUpdate(
//                                                sql
//                                            )
//                                        }
//                                    } catch (e: SQLException) {
//                                        throw RuntimeException(e)
//                                    }
//                                }
                            }
//                            删除多余字段：在遍历数据库中的列时，如果数据库中的列在类中不存在，则执行 dropTableColumn 操作来删除该列。
//                            dbColumns?.filter { !classColumns.contains(it) }?.forEach { column ->
//                                try {
//                                    column?.let { statement.executeUpdate(SQLTemplate.dropTableColumn(tableName, it)) }
//                                } catch (e: SQLException) {
//                                    throw RuntimeException(e)
//                                }
//                            }
                        }


                    }

                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw RuntimeException(e)
        }
        return resultList
    }

    override fun dropColumns(
        classes: Class<*>,
        vararg columnNames: String
    ): List<String> {
        var toSet = columnNames.toSet()
        var resultList = mutableListOf<String>()
        val tablesMapTypes = HashMap<String, HashMap<String, String>>()

        // 存储表名和其主键列名映射的Map
        val tablesMapPrimaryKeys = HashMap<String, String>()
        // 存储表名和其列是否自动增长映射的Map


        val s = SQLTemplate.query<Any?>("sqlite_master", Options().where("type = ?", "table"))
        var tableNameSet = HashSet<String>()
        for (tClass in listOf<Class<*>>(classes)) {
            val tableName = getTableNameFromClass(tClass)
            tableNameSet.add(tableName)
        }
        try {
            connection!!.createStatement().use { statement ->
                statement.executeQuery(s).use { result ->
                    val metaData = connection!!.metaData
                    while (result.next()) {
                        val tableColumnTypeMap = HashMap<String, String>()

                        val tableName = result.getString("name")
                        if (tableNameSet.contains(tableName)) {
                            metaData.getPrimaryKeys(null, null, tableName).use { primaryKeySet ->
                                while (primaryKeySet.next()) {
                                    val primaryKeyColumn =
                                        primaryKeySet.getString("COLUMN_NAME").lowercase(Locale.getDefault())
//                                println("Column $primaryKeyColumn in table $tableName is a primary key")
                                    tablesMapPrimaryKeys.put(tableName, primaryKeyColumn)
                                }

                            }
                            metaData.getColumns(null, null, tableName, null).use { set ->
//                            println(set.metaData.isAutoIncrement())


                                while (set.next()) {


//
                                    val column = set.getString("COLUMN_NAME").lowercase(Locale.getDefault())
                                    val type = set.getString("TYPE_NAME").lowercase(Locale.getDefault())
                                    tableColumnTypeMap.put(column, type)

                                }
                            }
                            tablesMapTypes.put(tableName, tableColumnTypeMap)

                        }
                    }

                    for (tClass in listOf<Class<*>>(classes)) {
                        val tableName = getTableNameFromClass(tClass)
                        val tableColumnTypeMap = tablesMapTypes.getOrDefault(tableName, null)
                        val dbColumns = tableColumnTypeMap?.keys?.filter {

                            tablesMapPrimaryKeys.get(tableName) != it
                        }?.toSet()
//                        val reflect: Reflect<*> = Reflect<Any?>(tClass)
//                        val classColumns = mutableMapOf<String, String>()
//                        reflect.getDBColumnsWithType { column: String, type: String ->
//                            classColumns.put(column!!, type!!)
//                        }
//                        println(
//                            dbColumns
//                        )
//                        println(
//                            classColumns
//                        )
                        if (tableColumnTypeMap == null) {
                            throw RuntimeException("table $tableName not found")
                        } else {
//                            println(tableColumnTypeMap)

//                            删除多余字段：在遍历数据库中的列时，如果数据库中的列在类中不存在，则执行 dropTableColumn 操作来删除该列。
                            dbColumns?.filter { toSet.contains(it) }?.forEach { column ->
                                try {
                                    column.let {


                                        var sql = SQLTemplate.dropTableColumn(tableName, it)
                                        var asyncEventBus = getAsyncEventBus("alter")
                                        asyncEventBus.post(MyEvent(sql))
                                        resultList.add(sql)
                                        statement.executeUpdate(sql)
                                    }
                                } catch (e: SQLException) {
                                    throw RuntimeException(e)
                                }
                            }
                        }


                    }

                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw RuntimeException(e)
        }
        return resultList
    }

    override fun checkTableDifferenceInPrimaryKeyAndAutoIncrement(classes: Class<*>): Boolean {

        val classInfo = getClassInfoFromClass(classes)
        val dbInfo = getTableInfoFromDatabase(classes)
//        println(
//            classInfo
//
//
//        )
//        println(
//            dbInfo
//
//
//        )
        if (dbInfo == null || classInfo == null) return true
        return classInfo.primaryKey != dbInfo.primaryKey || classInfo.isAutoIncrement != dbInfo.isAutoIncrement
    }

    /**
     * 无法删除主键!!!!!!!
     * */
    override fun dropUnusedColumns(vararg classes: Class<*>): List<String> {
        var resultList = mutableListOf<String>()
        // 存储表名和其列类型映射的Map
        val tablesMapTypes = HashMap<String, HashMap<String, String>>()

        // 存储表名和其主键列名映射的Map
        val tablesMapPrimaryKeys = HashMap<String, String>()
        // 存储表名和其列是否自动增长映射的Map


        val s = SQLTemplate.query<Any?>("sqlite_master", Options().where("type = ?", "table"))
        var tableNameSet = HashSet<String>()
        for (tClass in classes) {
            val tableName = getTableNameFromClass(tClass)
            tableNameSet.add(tableName)
        }
        try {
            connection!!.createStatement().use { statement ->
                statement.executeQuery(s).use { result ->
                    val metaData = connection!!.metaData
                    while (result.next()) {
                        val tableColumnTypeMap = HashMap<String, String>()

                        val tableName = result.getString("name")
                        if (tableNameSet.contains(tableName)) {
                            metaData.getPrimaryKeys(null, null, tableName).use { primaryKeySet ->
                                while (primaryKeySet.next()) {
                                    val primaryKeyColumn =
                                        primaryKeySet.getString("COLUMN_NAME").lowercase(Locale.getDefault())
//                                println("Column $primaryKeyColumn in table $tableName is a primary key")
                                    tablesMapPrimaryKeys.put(tableName, primaryKeyColumn)
                                }

                            }
                            metaData.getColumns(null, null, tableName, null).use { set ->
//                            println(set.metaData.isAutoIncrement())


                                while (set.next()) {


//
                                    val column = set.getString("COLUMN_NAME").lowercase(Locale.getDefault())
                                    val type = set.getString("TYPE_NAME").lowercase(Locale.getDefault())
                                    tableColumnTypeMap.put(column, type)

                                }
                            }
                            tablesMapTypes.put(tableName, tableColumnTypeMap)

                        }
                    }

                    for (tClass in classes) {
                        val tableName = getTableNameFromClass(tClass)
                        val tableColumnTypeMap = tablesMapTypes.getOrDefault(tableName, null)
                        val dbColumns = tableColumnTypeMap?.keys?.filter {

                            tablesMapPrimaryKeys.get(tableName) != it
                        }?.toSet()
                        val reflect: Reflect<*> = Reflect<Any?>(tClass)
                        val classColumns = mutableMapOf<String, String>()
                        reflect.getDBColumnsWithType { column: String, type: String ->
                            classColumns.put(column, type)
                        }
//                        println(
//                            dbColumns
//                        )
//                        println(
//                            classColumns
//                        )
                        if (tableColumnTypeMap == null) {
                            throw RuntimeException("table $tableName not found")
                        } else {
//                            println(tableColumnTypeMap)

//                            删除多余字段：在遍历数据库中的列时，如果数据库中的列在类中不存在，则执行 dropTableColumn 操作来删除该列。
                            dbColumns?.filter { !classColumns.contains(it) }?.forEach { column ->
                                try {
                                    column.let {


                                        var sql = SQLTemplate.dropTableColumn(tableName, it)
                                        var asyncEventBus = getAsyncEventBus("alter")
                                        asyncEventBus.post(MyEvent(sql))
                                        resultList.add(sql)
                                        statement.executeUpdate(sql)
                                    }
                                } catch (e: SQLException) {
                                    throw RuntimeException(e)
                                }
                            }
                        }


                    }

                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw RuntimeException(e)
        }
        return resultList
    }

    fun getTableInfoFromDatabase(value: Class<*>): PrimaryKeyAndAutoIncrementInfo? {
        val tableName = value.let { getTableNameFromClass(it) }
        var tablesInfo = getTablesInfo(value)
        return tablesInfo.tablesMapPrimaryKeys.get(tableName)?.let {


            var isAutoIncrement = tablesInfo.tablesMapIsAutoIncrement.get(tableName)?.get(it)

            if (isAutoIncrement == null) {
                isAutoIncrement = false
            }
            PrimaryKeyAndAutoIncrementInfo(
                it,
                isAutoIncrement = isAutoIncrement,
            )
        }
    }

    fun getClassInfoFromClass(value: Class<*>): PrimaryKeyAndAutoIncrementInfo? {


        val tableName = value.let { getTableNameFromClass(it) }
        var tablesInfo = getReflectInfo(value)
        return tablesInfo.tablesMapPrimaryKeys.get(tableName)?.let {


            var isAutoIncrement = tablesInfo.tablesMapIsAutoIncrement.get(tableName)?.get(it)

            if (isAutoIncrement == null) {
                isAutoIncrement = false
            }
            PrimaryKeyAndAutoIncrementInfo(
                it,
                isAutoIncrement = isAutoIncrement,
            )
        }
    }

    /**
     * 根据给定的类更新或创建数据库表结构
     * 此函数检查数据库中的现有表结构，并根据提供的类更新或创建相应的表
     * 它会比较数据库中的表结构和类的结构，进行必要的列添加、修改或删除，以及索引的更新
     *
     * @param classes 可变参数，代表需要更新或创建表结构的类
     */
    override fun tables(vararg classes: Class<*>): List<String> {
        var resultList = mutableListOf<String>()
        // 存储表名和其列类型映射的Map
        val tablesMapTypes = HashMap<String, HashMap<String, String>>()

        // 存储表名和其主键列名映射的Map
        val tablesMapPrimaryKeys = HashMap<String, String>()
        // 存储表名和其列是否自动增长映射的Map
        val tablesMapIsAutoIncrement = HashMap<String, HashMap<String, Boolean>>()
        // 存储索引名和列名映射的Map
        val indexMapColumns = HashMap<String, String>()
        val indexMapTables = HashMap<String, String>()

        val s = SQLTemplate.query<Any?>("sqlite_master", Options().where("type = ?", "table"))
        var tableNameSet = HashSet<String>()
        for (tClass in classes) {
            val tableName = getTableNameFromClass(tClass)
            tableNameSet.add(tableName)
        }
        try {
            connection!!.createStatement().use { statement ->
                statement.executeQuery(s).use { result ->
                    val metaData = connection!!.metaData
                    while (result.next()) {
                        val tableColumnTypeMap = HashMap<String, String>()
                        val tableColumnTypeMapisAutoIncrement = HashMap<String, Boolean>()
                        val tableName = result.getString("name")
                        if (tableNameSet.contains(tableName)) {
                            metaData.getPrimaryKeys(null, null, tableName).use { primaryKeySet ->
                                while (primaryKeySet.next()) {
                                    val primaryKeyColumn =
                                        primaryKeySet.getString("COLUMN_NAME").lowercase(Locale.getDefault())
//                                println("Column $primaryKeyColumn in table $tableName is a primary key")
                                    tablesMapPrimaryKeys.put(tableName, primaryKeyColumn)
                                }

                            }
                            metaData.getColumns(null, null, tableName, null).use { set ->
//                            println(set.metaData.isAutoIncrement())


                                while (set.next()) {
                                    val isAutoIncrement =
                                        set.getString("IS_AUTOINCREMENT").lowercase(Locale.getDefault())

//
                                    val column = set.getString("COLUMN_NAME").lowercase(Locale.getDefault())
                                    val type = set.getString("TYPE_NAME").lowercase(Locale.getDefault())
                                    tableColumnTypeMap.put(column, type)
                                    tableColumnTypeMapisAutoIncrement.put(column, isAutoIncrement == "yes")
                                }
                            }
                            tablesMapTypes.put(tableName, tableColumnTypeMap)
                            tablesMapIsAutoIncrement.put(tableName, tableColumnTypeMapisAutoIncrement)
                            metaData.getIndexInfo(null, null, tableName, false, false).use { set ->
                                while (set.next()) {
//                                println(set.getString("TABLE_NAME"))
                                    val index = set.getString("INDEX_NAME")
                                    val column = set.getString("COLUMN_NAME")
                                    if (index != null) {
                                        indexMapTables.put(
                                            index.lowercase(Locale.getDefault()),
                                            tableName.lowercase(Locale.getDefault())
                                        )
                                    }
                                    Optional.ofNullable<String>(index)
                                        .ifPresent { i: String ->
                                            indexMapColumns.put(
                                                index.lowercase(Locale.getDefault()),
                                                column.lowercase(Locale.getDefault())
                                            )
                                        }
                                }
                            }
                        }
                    }
                    val indexMapColumnsTemp = indexMapColumns.toMutableMap()

                    for (tClass in classes) {
                        val tableName = getTableNameFromClass(tClass)
                        val tableColumnTypeMap = tablesMapTypes.getOrDefault(tableName, null)
//                        val dbColumns = tableColumnTypeMap?.keys?.toSet()
                        val reflect: Reflect<*> = Reflect<Any?>(tClass)
                        val classColumns = mutableMapOf<String, String>()
                        reflect.getDBColumnsWithType { column: String, type: String ->
                            classColumns.put(column, type)
                        }
//                        println(
//                            dbColumns
//                        )
//                        println(
//                            classColumns
//                        )
                        if (tableColumnTypeMap == null) {
                            var sql = SQLTemplate.create(tClass)
                            var asyncEventBus = getAsyncEventBus("create")
                            asyncEventBus.post(MyEvent(sql))
                            resultList.add(sql)
                            statement.executeUpdate(sql)
                        } else {
//                            println(tableColumnTypeMap)
                            reflect.getDBColumnsWithType { column: String, type: String ->

                                if (tableColumnTypeMap.getOrDefault(column, null) == null) {
                                    try {
                                        column.let {
                                            var sql = SQLTemplate.addTableColumn(
                                                tableName,
                                                it,
                                                type
                                            )
                                            var asyncEventBus = getAsyncEventBus("alter")
                                            asyncEventBus.post(MyEvent(sql))
                                            resultList.add(sql)
                                            statement.executeUpdate(
                                                sql
                                            )
                                        }
                                    } catch (e: SQLException) {
                                        e.printStackTrace()
                                        throw RuntimeException(e)
                                    }
                                }
//检查列类型并修改：在遍历类的列时，如果数据库中的列类型与类中的列类型不同，则执行 alterTableColumn 操作来修改列类型。
//                                else if (tableColumnTypeMap[column] != type) {
//                                    try {
//                                        column?.let {
//                                            var sql1 = SQLTemplate.dropTableColumn(
//                                                tableName,
//                                                it,
//
//                                                )
//                                            resultList.add(sql1)
//                                            statement.executeUpdate(
//                                                sql1
//                                            )
//
//                                            var sql = SQLTemplate.addTableColumn(
//                                                tableName,
//                                                it,
//                                                type
//                                            )
//                                            resultList.add(sql)
//                                            statement.executeUpdate(
//                                                sql
//                                            )
//                                        }
//                                    } catch (e: SQLException) {
//                                        throw RuntimeException(e)
//                                    }
//                                }
                            }
//                            删除多余字段：在遍历数据库中的列时，如果数据库中的列在类中不存在，则执行 dropTableColumn 操作来删除该列。
//                            dbColumns?.filter { !classColumns.contains(it) }?.forEach { column ->
//                                try {
//                                    column?.let { statement.executeUpdate(SQLTemplate.dropTableColumn(tableName, it)) }
//                                } catch (e: SQLException) {
//                                    throw RuntimeException(e)
//                                }
//                            }
                        }


                        reflect.getIndexList {
                            var index = it.name
                            val column = it.column
                            try {
                                if (indexMapColumnsTemp.get(index) != null) {
                                    indexMapColumnsTemp.remove(index, column)
                                } else {
                                    var sql = SQLTemplate.createIndex(tClass, column, it.unique)
                                    var asyncEventBus = getAsyncEventBus("create")
                                    asyncEventBus.post(MyEvent(sql))
                                    resultList.add(sql)
                                    statement.executeUpdate(sql)
                                }
                            } catch (e: SQLException) {
                                throw RuntimeException(e)
                            }
                        }
                    }
                    indexMapColumnsTemp.forEach { (index: String, _: String) ->
                        try {
                            var sql = SQLTemplate.dropIndex<Any?>(index)
                            var asyncEventBus = getAsyncEventBus("drop")
                            asyncEventBus.post(MyEvent(sql))
                            resultList.add(sql)
                            statement.executeUpdate(sql)
                        } catch (e: SQLException) {
                            throw RuntimeException(e)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw RuntimeException(e)
        }
        return resultList
    }

    override fun create(vararg classes: Class<*>): List<String> {
        var resultList = mutableListOf<String>()
        try {
            connection!!.createStatement().use { statement ->
                for (tClass in classes) {
                    var sql = SQLTemplate.create(tClass)
                    var asyncEventBus = getAsyncEventBus("create")
                    asyncEventBus.post(MyEvent(sql))
                    resultList.add(sql)
                    statement.executeUpdate(sql)
                }
            }
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
        return resultList
    }

    override fun drop(vararg classes: Class<*>): List<String> {
        var resultList = mutableListOf<String>()
        try {
            connection!!.createStatement().use { statement ->
                for (tClass in classes) {
                    var sql = SQLTemplate.drop(tClass)
                    var asyncEventBus = getAsyncEventBus("drop")
                    asyncEventBus.post(MyEvent(sql))
                    resultList.add(sql)
                    statement.executeUpdate(sql)
                }
            }
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
        return resultList
    }

    override fun version(): String {
        val s = "select sqlite_version();"
        try {
            connection!!.createStatement().use { statement ->
                statement.executeQuery(s).use { resultSet ->
                    return if (resultSet.next()) resultSet.getString(1) else "unknown"
                }
            }
        } catch (e: SQLException) {
            throw RuntimeException(e)
        }
    }

    override fun <T : DataSupport<T>> insert(t: T): List<String> {
        var resultList = mutableListOf<String>()
        try {
            connection!!.createStatement().use { statement ->
                lock.lock()
                t.createdAt = System.currentTimeMillis()
                t.updatedAt = t.createdAt
                var sql = SQLTemplate.insert<T>(t)
                var asyncEventBus = getAsyncEventBus("insert")
                asyncEventBus.post(MyEvent(sql))
                resultList.add(
                    sql
                )
                statement.executeUpdate(sql)
                statement.executeQuery("select last_insert_rowid()").use { result ->
                    if (result.next()) {
                        t.id = result.getLong(1)
                    }
                }
            }
        } catch (e: Exception) {
            throw RuntimeException(e)
        } finally {
            lock.unlock()
        }
        return resultList
    }

    override fun <T : DataSupport<T>> updateByPredicate(t: T, predicate: String?, vararg args: Any?): List<String> {
        var resultList = mutableListOf<String>()
        try {
            connection!!.createStatement().use { statement ->
                lock.lock()
                t.updatedAt = System.currentTimeMillis()
                var sql = SQLTemplate.update<T>(t, Options().where(predicate, *args))
                var asyncEventBus = getAsyncEventBus("update")
                asyncEventBus.post(MyEvent(sql))
                resultList.add(sql)
                statement.executeUpdate(sql)
            }
        } catch (e: Exception) {
            throw RuntimeException(e)
        } finally {
            lock.unlock()
        }
        return resultList
    }

    override fun <T : DataSupport<T>> updateById(t: T): List<String> {
        if (t.id == null) {
            throw IllegalArgumentException("The entity must have an id to be updated.")
        }
        return updateByPredicate<T>(t, "id = ?", t.id())
    }

    override fun <T : DataSupport<T>> deleteByPredicate(
        tClass: Class<T>,
        predicate: String?,
        vararg args: Any?
    ): List<String> {
        val sql = SQLTemplate.delete<T>(tClass, Options().where(predicate, *args))
//        println(sql)
        var asyncEventBus = getAsyncEventBus("delete")
        asyncEventBus.post(MyEvent(sql))
        try {
            connection!!.createStatement().use { statement ->
                lock.lock()
                statement.executeUpdate(sql)
            }
        } catch (e: Exception) {
            throw RuntimeException(e)
        } finally {
            lock.unlock()
        }
        return mutableListOf(sql)
    }

    override fun <T : DataSupport<T>> deleteByListId(tClass: Class<T>, ids: List<Long>): List<String> {
        val builder = StringBuilder(ids.toList().toString())
        builder.deleteCharAt(0).deleteCharAt(builder.length - 1)
        return deleteByPredicate<T>(tClass, "id in(?)", builder)
    }

    override fun <T : DataSupport<T>> deleteByVarargId(tClass: Class<T>, vararg ids: Long): List<String> {
        val builder = StringBuilder(ids.toList().toString())
//        println(ids.toList().toString())
        builder.deleteCharAt(0).deleteCharAt(builder.length - 1)
        return deleteByPredicate<T>(tClass, "id in(?)", builder.toString())
    }

    override fun <T : DataSupport<T>> deleteAll(tClass: Class<T>): List<String> {
        return deleteByPredicate<T>(tClass, null, null)
    }

    override fun <T : DataSupport<T>> findByConsumer(tClass: Class<T>, consumer: Consumer<Options>?): MutableList<T> {
        val options = Options()
        Optional.ofNullable<Consumer<Options>?>(consumer)
            .ifPresent { c: Consumer<Options> -> c.accept(options) }
        val sql = SQLTemplate.query<T>(tClass, options)
        var asyncEventBus = getAsyncEventBus("select")
        asyncEventBus.post(MyEvent(sql))
        try {
            connection!!.createStatement().use { statement ->
                statement.executeQuery(sql).use { resultSet ->
                    val list: MutableList<T> = ArrayList<T>()
                    while (resultSet.next()) {
                        val t = Reflect.toEntity<T>(tClass, options, resultSet)
                        Optional.ofNullable<T>(t).ifPresent { e: T -> list.add(e) }
                    }
                    return list
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw RuntimeException(e)
        }
    }

    override fun <T : DataSupport<T>> findByListId(tClass: Class<T>, ids: List<Long>): List<T> {
        val builder = StringBuilder(ids.toList().toString())
        builder.deleteCharAt(0).deleteCharAt(builder.length - 1)
        return findByConsumer<T>(tClass) { options: Options? -> options!!.where("id in(?)", builder) }
    }

    override fun <T : DataSupport<T>> findByVarargId(tClass: Class<T>, vararg ids: Long): MutableList<T> {
        val builder = StringBuilder(ids.toList().toString())
        builder.deleteCharAt(0).deleteCharAt(builder.length - 1)
        return findByConsumer<T>(tClass) { options: Options? -> options!!.where("id in(?)", builder) }
    }

    override fun <T : DataSupport<T>> findAll(tClass: Class<T>): MutableList<T> {
        return findByConsumer<T>(tClass, null as Consumer<Options>?)
    }

    override fun <T : DataSupport<T>> findOneByPredicate(tClass: Class<T>, predicate: String?, vararg args: Any?): T? {
        val list = findByConsumer<T>(tClass) { options: Options? -> options!!.where(predicate, *args) }
        return if (!list.isEmpty()) list.get(0) else null
    }

    override fun <T : DataSupport<T>> findOneById(tClass: Class<T>, id: Long): T? {
        return findOneByPredicate<T>(tClass, "id = ?", id)
    }

    override fun <T : DataSupport<T>> firstByPredicate(tClass: Class<T>, predicate: String?, vararg args: Any?): T? {
        val list = findByConsumer<T>(
            tClass
        ) { options: Options? -> options!!.where(predicate, *args).order("id", Options.ASC) }
        return if (!list.isEmpty()) list.get(0) else null
    }

    override fun <T : DataSupport<T>> first(tClass: Class<T>): T? {
        return firstByPredicate<T>(tClass, null, null as Any?)
    }

    override fun <T : DataSupport<T>> lastByPredicate(tClass: Class<T>, predicate: String?, vararg args: Any?): T? {
        val list = findByConsumer<T>(
            tClass
        ) { options: Options? -> options!!.where(predicate, *args).order("id", Options.DESC) }
        return if (!list.isEmpty()) list.get(0) else null
    }

    override fun <T : DataSupport<T>> last(tClass: Class<T>): T? {
        return lastByPredicate<T>(tClass, null, null as Any?)
    }

    override fun <T : DataSupport<T>> countByPredicate(tClass: Class<T>, predicate: String?, vararg args: Any?): Long {
        val sql = SQLTemplate.query<T>(tClass, Options().select("count(*)").where(predicate, *args))
        var asyncEventBus = getAsyncEventBus("select")
        asyncEventBus.post(MyEvent(sql))
        try {
            connection!!.createStatement().use { statement ->
                statement.executeQuery(sql).use { resultSet ->
                    return if (resultSet.next()) resultSet.getLong(1) else 0
                }
            }
        } catch (e: SQLException) {
            throw RuntimeException(e)
        }
    }

    override fun <T : DataSupport<T>> count(tClass: Class<T>): Long {
        return countByPredicate<T>(tClass, null, null as Any?)
    }

    override fun <T : DataSupport<T>> averageByPredicate(
        tClass: Class<T>,
        column: String,
        predicate: String?,
        vararg args: Any?
    ): Double {
        val sql = SQLTemplate.query<T>(
            tClass,
            Options().select(String.format("avg(%s)", column)).where(predicate, *args)
        )
        var asyncEventBus = getAsyncEventBus("select")
        asyncEventBus.post(MyEvent(sql))
        try {
            connection!!.createStatement().use { statement ->
                statement.executeQuery(sql).use { resultSet ->
                    return if (resultSet.next()) resultSet.getDouble(1) else 0.0
                }
            }
        } catch (e: SQLException) {
            throw RuntimeException(e)
        }
    }

    override fun <T : DataSupport<T>> average(tClass: Class<T>, column: String): Double {
        return averageByPredicate<T>(tClass, column, null, null as Any?)
    }

    override fun <T : DataSupport<T>> sumByPredicate(
        tClass: Class<T>,
        column: String,
        predicate: String?,
        vararg args: Any?
    ): Number {
        val sql = SQLTemplate.query<T>(
            tClass,
            Options().select(String.format("sum(%s)", column)).where(predicate, *args)
        )
        var asyncEventBus = getAsyncEventBus("select")
        asyncEventBus.post(MyEvent(sql))
        try {
            connection!!.createStatement().use { statement ->
                statement.executeQuery(sql).use { resultSet ->
                    return if (resultSet.next()) resultSet.getObject(1) as Number else 0
                }
            }
        } catch (e: SQLException) {
            throw RuntimeException(e)
        }
    }

    override fun <T : DataSupport<T>> sum(tClass: Class<T>, column: String): Number {
        return sumByPredicate<T>(tClass, column, null, null as Any?)
    }

    override fun <T : DataSupport<T>> maxByPredicate(
        tClass: Class<T>,
        column: String,
        predicate: String?,
        vararg args: Any?
    ): Number? {
        val sql = SQLTemplate.query<T>(
            tClass,
            Options().select(String.format("max(%s)", column)).where(predicate, *args)
        )
        var asyncEventBus = getAsyncEventBus("select")
        asyncEventBus.post(MyEvent(sql))
        try {
            connection!!.createStatement().use { statement ->
                statement.executeQuery(sql).use { resultSet ->
                    return if (resultSet.next()) {
                        var any = resultSet.getObject(1)
                        if (any == null) null else (any as Number)
                    } else 0
                }
            }
        } catch (e: SQLException) {
            throw RuntimeException(e)
        }
    }

    override fun <T : DataSupport<T>> max(tClass: Class<T>, column: String): Number? {
        return maxByPredicate<T>(tClass, column, null, null as Any?)
    }

    override fun <T : DataSupport<T>> minByPredicate(
        tClass: Class<T>,
        column: String,
        predicate: String?,
        vararg args: Any?
    ): Number? {
        val sql = SQLTemplate.query<T>(
            tClass,
            Options().select(String.format("min(%s)", column)).where(predicate, *args)
        )
        var asyncEventBus = getAsyncEventBus("select")
        asyncEventBus.post(MyEvent(sql))
        try {
            connection!!.createStatement().use { statement ->
                statement.executeQuery(sql).use { resultSet ->
                    return if (resultSet.next()) {
                        var any = resultSet.getObject(1)
                        if (any == null) null else (any as Number)
                    } else 0
                }
            }
        } catch (e: SQLException) {
            throw RuntimeException(e)
        }
    }

    override fun <T : DataSupport<T>> min(tClass: Class<T>, column: String): Number? {
        return minByPredicate<T>(tClass, column, null, null as Any?)
    }

    companion object {
        @JvmField
        val gson: Gson = Gson()
    }


    fun getTablesInfo(vararg classes: Class<*>): TableReflectInfo {
        val tablesMapIndexesData1: HashMap<String, List<IndexesData1>> = HashMap<String, List<IndexesData1>>()
        // 存储表名和其列类型映射的Map
        val tablesMapTypes = HashMap<String, HashMap<String, String>>()

        // 存储表名和其主键列名映射的Map
        val tablesMapPrimaryKeys = HashMap<String, String>()
        // 存储表名和其列是否自动增长映射的Map
        val tablesMapIsAutoIncrement = HashMap<String, HashMap<String, Boolean>>()
        // 存储索引名和列名映射的Map
//        val indexMapColumns = HashMap<String, String>()
//        val indexMapTables = HashMap<String, String>()
        var tableNameSet = HashSet<String>()
        for (tClass in classes) {
            val tableName = getTableNameFromClass(tClass)
            tableNameSet.add(tableName)
        }
        val dbPath = this.path
        for (tablename in tableNameSet) {
            val indexes = fetchIndexes(dbPath, tablename)
            tablesMapIndexesData1.put(tablename, indexes)
        }
        val s = SQLTemplate.query<Any?>("sqlite_master", Options().where("type = ?", "table"))
        try {


            connection!!.createStatement().use { statement ->
                statement.executeQuery(s).use { result ->
                    val metaData = connection!!.metaData
                    while (result.next()) {
                        val tableColumnTypeMap = HashMap<String, String>()
                        val tableColumnTypeMapisAutoIncrement = HashMap<String, Boolean>()
                        val tableName = result.getString("name")
                        if (tableNameSet.contains(tableName)) {
                            metaData.getPrimaryKeys(null, null, tableName).use { primaryKeySet ->
                                while (primaryKeySet.next()) {
                                    val primaryKeyColumn =
                                        primaryKeySet.getString("COLUMN_NAME").lowercase(Locale.getDefault())
//                                println("Column $primaryKeyColumn in table $tableName is a primary key")
                                    tablesMapPrimaryKeys.put(tableName, primaryKeyColumn)
                                }

                            }
                            metaData.getColumns(null, null, tableName, null).use { set ->
//                            println(set.metaData.isAutoIncrement())


                                while (set.next()) {
                                    val isAutoIncrement =
                                        set.getString("IS_AUTOINCREMENT").lowercase(Locale.getDefault())

//                             
                                    val column = set.getString("COLUMN_NAME").lowercase(Locale.getDefault())
                                    val type = set.getString("TYPE_NAME").lowercase(Locale.getDefault())
                                    tableColumnTypeMap.put(column, type)
                                    tableColumnTypeMapisAutoIncrement.put(column, isAutoIncrement == "yes")
                                }
                            }
                            tablesMapTypes.put(tableName, tableColumnTypeMap)
                            tablesMapIsAutoIncrement.put(tableName, tableColumnTypeMapisAutoIncrement)

                        }
                    }
                    // val indexMapColumnsTemp = indexMapColumns.toMutableMap()

                    return TableReflectInfo(
                        tablesMapTypes,
                        tablesMapPrimaryKeys, tablesMapIsAutoIncrement, tablesMapIndexesData1
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw RuntimeException(e)
        }
    }

}

