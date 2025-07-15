package com.yanjiazheng.dslock.ratelimit;

import com.yanjiazheng.dslock.strategy.AbstractRateLimiter;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

@Component
public class TokenBucketRateLimiter extends AbstractRateLimiter {

    /***
     *   1. 从 Redis 获取当前令牌数 tokens 和上次令牌生成时间 last；
     *   2. 根据当前时间 now 计算已生成的新令牌数（时间差 * rate）；
     *   3. 加入新令牌后不能超过桶最大容量；
     *   4. 如果当前可用令牌数 ≥ 1，允许通过并扣减一个令牌；
     *   5. 更新 Redis 中的 tokens 和 last；
     *   6. 设置 key 的自动过期，减少空桶的长期存在；
     *   7. 返回 1 表示允许，返回 0 表示限流。
     */
    private final RedisScript<Long> script = RedisScript.of(
            "local key = KEYS[1] " +
                    "local capacity = tonumber(ARGV[1]) " +
                    "local rate = tonumber(ARGV[2]) " +
                    "local now = tonumber(ARGV[3]) " +
                    // 当前令牌数：从 Redis 的哈希结构中获取 tokens 字段，首次请求默认为满桶（capacity）
                    "local tokens = tonumber(redis.call('HGET', key, 'tokens')) or capacity " +
                    // 上一次填充令牌的时间戳，首次请求默认为当前时间
                    "local last = tonumber(redis.call('HGET', key, 'last')) or now " +
                    // 计算从上次到现在新生成了多少令牌：时间差 * 速率（注意转换成秒），向下取整
                    "local delta = math.floor((now - last) * rate / 1000) " +
                    // 新令牌加入后总令牌数不能超过桶容量
                    "tokens = math.min(capacity, tokens + delta) " +
                    // 如果令牌数 < 1，说明限流触发，返回0
                    "if tokens < 1 then return 0 end " +
                    // 请求通过：消耗一个令牌
                    "tokens = tokens - 1 " +
                    // 更新哈希表：写入新的令牌数量和时间戳
                    "redis.call('HMSET', key, 'tokens', tokens, 'last', now) " +
                    // 设置过期时间，防止长期不请求造成内存泄漏，单位毫秒
                    "redis.call('PEXPIRE', key, 60000) " +
                    "return 1",
            Long.class
    );

    @Override
    public boolean allow(String key, String... args) {
        Long result = executeScript(script, key, args);
        return result != null && result == 1;
    }
}