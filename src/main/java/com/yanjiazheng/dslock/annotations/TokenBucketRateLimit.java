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
public @interface TokenBucketRateLimit {
    int capacity() default 100;      // 桶容量

    int rate() default 10;           // 令牌生成速率，单位：令牌/秒
}
