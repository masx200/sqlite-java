package com.github.masx200.sqlite_java

data class TableReflectInfo(
    val tablesMapTypes: HashMap<String, HashMap<String, String>>,
    val tablesMapPrimaryKeys: HashMap<String, String>,
    val tablesMapIsAutoIncrement: HashMap<String, HashMap<String, Boolean>>,

    val tablesMapIndexesData1: HashMap<String, List<IndexesData1>>,
)