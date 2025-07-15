package com.yanjiazheng.dslock.util;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;


/**
 * @author hp
 */
public class DistributedRedisLock implements Lock {

    private StringRedisTemplate redisTemplate;

    private String lockName;

    private String uuid;

    private long expire = 30;

    public DistributedRedisLock(StringRedisTemplate redisTemplate, String lockName, String uuid) {
        this.redisTemplate = redisTemplate;
        this.lockName = lockName;
        this.uuid = uuid + ":" + Thread.currentThread().getId();
    }

    @Override
    public void lock() {
        this.tryLock();
    }

    @Override
    public void lockInterruptibly() throws InterruptedException {

    }

    @Override
    public boolean tryLock() {
        try {
            return this.tryLock(-1L, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * 尝试获取锁，如果无法立即获取锁，则等待指定的时间
     *
     * @param time 等待锁的时间，如果为-1，则不设置超时
     * @param unit 时间单位
     * @return 如果获取到锁，则返回true；否则返回false
     * @throws InterruptedException 如果线程被中断
     */
    @Override
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        //  如果指定了超时时间，则计算锁的过期时间
        if (time != -1) {
            this.expire = unit.toSeconds(time);
        }
        //  Lua脚本，用于在Redis中执行原子操作来尝试获取锁
        String script =
                // 判断锁是否未存在，或者该线程已经持有锁（可重入）
                "if redis.call('exists', KEYS[1]) == 0 or redis.call('hexists', KEYS[1], ARGV[1]) == 1 " +
                        "then " +
                        // 将当前线程的重入次数 +1：
                        "   redis.call('hincrby', KEYS[1], ARGV[1], 1) " +
                        // 设置锁的过期时间（防止死锁）
                        "   redis.call('expire', KEYS[1], ARGV[2]) " +
                        "   return 1 " +
                        "else " +
                        "   return 0 " +
                        "end";
        //  循环尝试执行Lua脚本以获取锁，如果未成功则线程休眠50毫秒后再次尝试
        while (!this.redisTemplate.execute(new DefaultRedisScript<>(script, Boolean.class), Collections.singletonList(lockName), uuid, String.valueOf(expire))) {
            Thread.sleep(50);
        }
        //  定时续期
        this.renewExpire();
        return true;
    }

    /**
     * 解锁方法
     * <p>
     * 本方法通过执行Lua脚本在Redis中进行解锁操作确保操作的原子性
     * 解锁逻辑如下：
     * 1. 如果锁不存在，则返回nil，表示解锁失败
     * 2. 如果锁存在，尝试减少锁的计数器如果减少后的计数器为0，则删除锁
     * 3. 如果减少后的计数器不为0，则返回0，表示解锁成功
     * <p>
     * 注意：此方法假设锁是由当前线程持有，且锁的标识通过uuid传递
     *
     * @throws IllegalMonitorStateException 如果锁不属于当前线程时抛出此异常
     */
    @Override
    public void unlock() {
        // Lua脚本实现了解锁逻辑
        String script = "if redis.call('hexists', KEYS[1], ARGV[1]) == 0 " +
                "then " +
                "   return nil " +
                "elseif redis.call('hincrby', KEYS[1], ARGV[1], -1) == 0 " +
                "then " +
                "   return redis.call('del', KEYS[1]) " +
                "else " +
                "   return 0 " +
                "end";
        // 执行Lua脚本，传递锁名称和线程标识
        Long flag = this.redisTemplate.execute(new DefaultRedisScript<>(script, Long.class), Collections.singletonList(lockName), uuid);
        // 如果脚本执行结果为null，表示锁不属于当前线程，抛出异常
        if (flag == null) {
            throw new IllegalMonitorStateException("this lock doesn't belong to you!");
        }
    }

    @Override
    public Condition newCondition() {
        return null;
    }

    /**
     * 刷新过期时间方法
     * 该方法用于周期性地刷新一个分布式锁的过期时间，以防止锁过早失效
     * 它通过在Redis中检查锁是否存在，并在存在时更新其过期时间来实现
     */
    private void renewExpire() {
        // Lua脚本用于在Redis中执行条件逻辑，以原子方式更新锁的过期时间
        String script = "if redis.call('hexists', KEYS[1], ARGV[1]) == 1 " +
                "then " +
                "   return redis.call('expire', KEYS[1], ARGV[2]) " +
                "else " +
                "   return 0 " +
                "end";

        // 使用定时任务定期执行锁的过期时间刷新操作
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                // 如果锁仍然存在，则执行刷新过期时间的逻辑
                if (redisTemplate.execute(new DefaultRedisScript<>(script, Boolean.class), Collections.singletonList(lockName), uuid, String.valueOf(expire))) {
                    // 刷新成功后，重新安排下一次刷新操作
                    renewExpire();
                }
            }
        }, this.expire * 1000 / 3);
    }

}
