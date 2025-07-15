package com.yanjiazheng.dslock.ratelimit;

import com.yanjiazheng.dslock.strategy.AbstractRateLimiter;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author hp
 */
@Component
public class HybridBucketRateLimiter extends AbstractRateLimiter {

    /***
     *  1. 对令牌桶计数器自增，表示一个新请求到来；
     *  2. 如果是第一个请求，设置其自动过期时间；
     *  3. 如果令牌数未超出限制，直接放行；
     *  4. 若超限，则回滚 token 计数；
     *  5. 检查排队列表是否已满，满则拒绝；
     *  6. 没满则将当前请求入队；
     *  7. 设置排队队列过期；
     *  8. 返回允许通过。
     */
    private final RedisScript<Long> script = RedisScript.of(
            "local token_key = KEYS[1] " +
                    "local queue_key = KEYS[2] " +
                    "local token_limit = tonumber(ARGV[1]) " +
                    "local queue_limit = tonumber(ARGV[2]) " +
                    "local window = tonumber(ARGV[3]) * 1000 " +
                    "local now = tonumber(ARGV[4]) " +
                    // 尝试增加 token 计数器，表示有一个请求到达
                    "local tokens = redis.call('INCR', token_key) " +
                    // 如果是第一个 token，则设置 token_key 的过期时间（用于滑动窗口统计）
                    "if tokens == 1 then redis.call('PEXPIRE', token_key, window) end " +
                    // 如果当前请求数未超过令牌桶限制，则允许请求
                    "if tokens <= token_limit then return 1 end " +
                    // 超过令牌桶限制，撤回 INCR 操作
                    "redis.call('DECR', token_key) " +
                    // 获取排队队列长度
                    "local queue_size = redis.call('LLEN', queue_key) " +
                    // 如果排队已满，拒绝请求
                    "if queue_size >= queue_limit then return 0 end " +
                    // 将当前时间戳加入排队队列（尾部插入），表示入队
                    "redis.call('RPUSH', queue_key, now) " +
                    // 设置排队队列的过期时间，防止内存泄露
                    "redis.call('PEXPIRE', queue_key, window) " +
                    "return 1",
            Long.class
    );

    @Override
    public boolean allow(String key, String... args) {
        List<String> keys = java.util.Arrays.asList(key, key + ":queue");
        Long result = executeScript(script, keys, args);
        return result != null && result == 1;
    }
}