package com.yanjiazheng.dslock.strategy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

public abstract class AbstractRateLimiter implements RateLimitStrategy {

    @Autowired
    protected RedisTemplate<String, String> redisTemplate;

    protected Long executeScript(RedisScript<Long> script, String key, String... args) {
        return redisTemplate.execute(script, java.util.Collections.singletonList(key), args);
    }

    protected Long executeScript(RedisScript<Long> script, java.util.List<String> keys, String... args) {
        return redisTemplate.execute(script, keys, args);
    }
}
