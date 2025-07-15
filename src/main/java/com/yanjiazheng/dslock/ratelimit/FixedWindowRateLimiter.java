package com.yanjiazheng.dslock.ratelimit;

import com.yanjiazheng.dslock.strategy.AbstractRateLimiter;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * @author hp
 */
@Component
public class FixedWindowRateLimiter extends AbstractRateLimiter {

    private final RedisScript<Long> script = RedisScript.of(
            "local key = KEYS[1] " +
                    "local limit = tonumber(ARGV[1]) " +  // 限流阈值
                    "local window = tonumber(ARGV[2]) " +  // 时间窗口（秒）
                    "local current = redis.call('INCR', key) " +  // 使用INCR原子操作增加计数
                    "if current == 1 then " +  // 如果是第一个请求
                    "redis.call('PEXPIRE', key, window * 1000) end " +  // 设置窗口过期时间（毫秒）
                    "if current > limit then return 0 end " +  // 超出阈值返回0
                    "return 1",  // 正常请求返回1
            Long.class
    );

    @Override
    public boolean allow(String key, String... args) {
        Long result = executeScript(script, key, args);
        return result != null && result == 1;
    }
}