package com.yanjiazheng.dslock.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author hp
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface HybridBucketRateLimit {
    int tokenLimit() default 150;

    int queueLimit() default 100;

    int window() default 1; // 单位：秒
}
