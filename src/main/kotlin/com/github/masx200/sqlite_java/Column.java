package com.github.masx200.sqlite_java;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Column {
    boolean index() default false;

    boolean unique() default false;

    boolean ignore() default false;

    boolean primaryKey() default false;

    boolean json() default false;

    boolean autoIncrement() default false;
}
