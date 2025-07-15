package com.yanjiazheng.dslock.ratelimit;

import com.yanjiazheng.dslock.strategy.AbstractRateLimiter;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

@Component
public class LeakyBucketRateLimiter extends AbstractRateLimiter {

    /***
     *  1. 记录状态：Redis 使用 Hash 存储桶的状态（当前水位 water 与上次时间戳 last）；
     *  2. 计算流出水量：根据“上次时间”和当前时间计算“漏出量”，以模拟平滑的请求出流；
     *  3. 判断是否超过容量：如果加上本次请求后超过容量，拒绝；
     *  4. 通过则入桶：请求通过，水量 +1，并更新状态；
     *  5. 设置自动过期：无访问 60 秒后自动清理 key，释放内存。
     */
    private final RedisScript<Long> script = RedisScript.of(
            "local key = KEYS[1] " +
                    "local capacity = tonumber(ARGV[1]) " +
                    "local rate = tonumber(ARGV[2]) " +
                    "local now = tonumber(ARGV[3]) " +
                    // 从 Redis 哈希中取出当前水位，如果没有则默认为 0（首次请求）
                    "local water = tonumber(redis.call('HGET', key, 'water')) or 0 " +
                    // 上次请求时间戳，如果没有则设为 now（首次请求）
                    "local last = tonumber(redis.call('HGET', key, 'last')) or now " +
                    // 根据上次时间与当前时间的间隔计算已流出的水量 = (时间差 × 漏速) / 1000，向下取整
                    "local leaked = math.floor((now - last) * rate / 1000) " +
                    // 当前水量减去已漏出水量，确保不为负
                    "water = math.max(0, water - leaked) " +
                    // 判断当前水量加1后是否超出桶容量，若超出则拒绝（返回0）
                    "if water + 1 > capacity then return 0 end " +
                    // 未超限，请求通过，水量加1
                    "water = water + 1 " +
                    // 更新 Redis 哈希中存储的当前水量和上次请求时间
                    "redis.call('HMSET', key, 'water', water, 'last', now) " +
                    // 设置该 key 的过期时间为60秒，避免内存泄露（无请求后自动清理）
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