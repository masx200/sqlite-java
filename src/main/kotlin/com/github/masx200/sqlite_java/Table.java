package com.github.masx200.sqlite_java;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// 定义注解
@Retention(RetentionPolicy.RUNTIME) // 注解会在运行时保留，可以反射获取
@Target(ElementType.TYPE) // 注解可以应用于类、接口、枚举
public @interface Table {
    String name() default ""; // 定义注解的参数
}
