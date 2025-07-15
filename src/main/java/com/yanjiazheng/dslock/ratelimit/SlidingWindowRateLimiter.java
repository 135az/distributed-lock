package com.yanjiazheng.dslock.ratelimit;

import com.yanjiazheng.dslock.strategy.AbstractRateLimiter;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * @author hp
 */
@Component
public class SlidingWindowRateLimiter extends AbstractRateLimiter {

    /***
     *  这个脚本的作用是实现一个基于时间滑动窗口的限流算法逻辑，关键点是：
     *      用 ZSET 来记录每次请求的时间戳；
     *      每次请求到来时，加入当前时间戳；
     *      删除窗口之外的旧时间戳；
     *      统计窗口内请求数量是否超限；
     *      设置 Redis 键过期时间，确保资源自动释放。
     */
    private final RedisScript<Long> script = RedisScript.of(
            // 获取 key，即限流使用的 Redis 键
            "local key = KEYS[1] " +
                    // 获取当前时间（客户端传入），并转换为数字类型
                    "local now = tonumber(ARGV[1]) " +
                    // 获取限流阈值（允许的最大请求数）
                    "local limit = tonumber(ARGV[2]) " +
                    // 获取滑动窗口时间长度（单位：秒），转换为毫秒
                    "local window = tonumber(ARGV[3]) * 1000 " +

                    // 将当前请求的时间戳作为 score 和 member 添加到 ZSET（有序集合）中
                    "redis.call('ZADD', key, now, now) " +
                    // 移除滑动窗口外的时间戳（score <= now - window）
                    "redis.call('ZREMRANGEBYSCORE', key, 0, now - window) " +
                    // 设置 key 的过期时间为窗口期，避免 key 永久占用内存
                    "redis.call('PEXPIRE', key, window) " +
                    // 获取当前窗口内的请求数量
                    "local count = redis.call('ZCARD', key) " +
                    // 判断是否超过限流阈值，如果超过，返回 0；否则返回 1
                    "if count > limit then return 0 end return 1",
            Long.class
    );

    @Override
    public boolean allow(String key, String... args) {
        Long result = executeScript(script, key, args);
        return result != null && result == 1;
    }
}