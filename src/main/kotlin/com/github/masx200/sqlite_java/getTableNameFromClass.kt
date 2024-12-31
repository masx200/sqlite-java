package com.github.masx200.sqlite_java

import java.util.*

/**
 * 从类中获取对应的数据库表名
 *
 * 此函数通过检查类上是否标注了@Table注解来确定表名如果标注了且注解中的name属性不为空，
 * 则使用该name属性作为表名；否则，使用类的简单名称作为表名
 *
 * @param tClass 类的Class对象，用于获取类的注解信息或简单名称
 * @return 返回数据库表名，全部小写
 */
fun getTableNameFromClass(tClass: Class<*>): String {
    if (tClass.isAnnotationPresent(Table::class.java)) {

        val annotation = tClass.getAnnotation(Table::class.java)
        if (annotation.name.isNotEmpty()) {
//            println(annotation.name)
            return annotation.name.lowercase(Locale.getDefault())
        }

    }
    val simplename = tClass.getSimpleName()
    val tableName = simplename.lowercase(Locale.getDefault())
    return tableName
}