package com.github.masx200.sqlite_java

fun recreateColumnsOnSchemaChangeInColumnTypes(db: DB, vararg list: Class<*>): List<String> {
    var resultList = mutableListOf<String>()
    for (klass in list) {
        var findDifferenceTypeColumns = db.findDifferenceTypeColumns(klass)
        findDifferenceTypeColumns.forEach {

            db.dropColumns(klass, it).forEach {
                resultList.add(it)
            }

            db.createColumns(klass, it).forEach {
                resultList.add(it)
            }
        }


    }
    return resultList
}