package com.yanjiazheng.dslock.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.yanjiazheng.dslock.mapper.LockMapper;
import com.yanjiazheng.dslock.mapper.StockMapper;
import com.yanjiazheng.dslock.pojo.Lock;
import com.yanjiazheng.dslock.pojo.Stock;
import com.yanjiazheng.dslock.util.DistributedLockClient;
import com.yanjiazheng.dslock.util.DistributedRedisLock;
import com.yanjiazheng.dslock.util.ZkClient;
import com.yanjiazheng.dslock.util.ZkDistributedLock;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;


@Service
public class StockService {

    @Autowired
    private StockMapper stockMapper;

    @Autowired
    private LockMapper lockMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private DistributedLockClient distributedLockClient;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private ZkClient zkclient;

    @Autowired
    private CuratorFramework curatorFramework;

    /***
     * 使用synchronized关键字实现加锁
     * result: 
     *       10,000 concurrency ： Average 209 ms Throughput 468.9/sec
     *       100,000 concurrency ： Average 2068 ms Throughput 471.2/sec
     */
    public synchronized void checkAndLockByJvm() {
        // 先查询库存是否充足
        Stock stock = this.stockMapper.selectById(1L);

        // 再减库存
        if (stock != null && stock.getCount() > 0) {
            stock.setCount(stock.getCount() - 1);
            this.stockMapper.updateById(stock);
        }
    }

    /***
     * 使用数据库事务操作实现加锁
     * result: 10,000 concurrency ：Average 205 ms Throughput --> 483.7/sec 
     */
    @Transactional
    public void checkAndLockByTransaction() {

        // 先查询库存是否充足
        Stock stock = this.stockMapper.selectStockForUpdate(1L);

        // 再减库存
        if (stock != null && stock.getCount() > 0) {
            stock.setCount(stock.getCount() - 1);
            this.stockMapper.updateById(stock);
        }
    }

    /***
     * 使用数据库乐观锁实现加锁
     * result: 10,000 concurrency ：Average 354 ms Throughput --> 261.3/sec
     */
    public void checkAndLockByOptimisticLock() {

        // 先查询库存是否充足
        Stock stock = this.stockMapper.selectById(1L);

        // 再减库存
        if (stock != null && stock.getCount() > 0) {
            // 获取版本号
            Long version = stock.getVersion();

            stock.setCount(stock.getCount() - 1);
            // 每次更新 版本号 + 1
            stock.setVersion(stock.getVersion() + 1);
            // 更新之前先判断是否是之前查询的那个版本，如果不是重试
            if (this.stockMapper.update(stock, new UpdateWrapper<Stock>().eq("id", stock.getId()).eq("version", version)) == 0) {
                checkAndLockByOptimisticLock();
            }
        }
    }

    /***
     * 使用redis乐观锁实现加锁
     * result: 10,000 concurrency ：Average 164 ms Throughput --> 523.9/sec
     */
    public void deductByRedisOptimisticLock() {

        this.redisTemplate.execute(new SessionCallback() {
            @Override
            public Object execute(RedisOperations operations) throws DataAccessException {
                operations.watch("stock");
                // 1. 查询库存信息
                Object stock = operations.opsForValue().get("stock");
                // 2. 判断库存是否充足
                int st = 0;
                if (stock != null && (st = Integer.parseInt(stock.toString())) > 0) {
                    // 3. 扣减库存
                    operations.multi();
                    operations.opsForValue().set("stock", String.valueOf(--st));
                    List exec = operations.exec();
                    if (exec == null || exec.isEmpty()) {
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        deductByRedisOptimisticLock();
                    }
                    return exec;
                }
                return null;
            }
        });
    }

    /***
     * 使用redis分布式锁实现加锁的一个简单实现
     * result: 10,000 concurrency ：Average 129 ms Throughput --> 511.8/sec
     */
    public void deductByRedisDistributedLock() {
        // 生成唯一的value值
        String uuid = UUID.randomUUID().toString();
        // setnx加锁
        while (Boolean.FALSE.equals(this.redisTemplate.opsForValue().setIfAbsent("lock", uuid, 3, TimeUnit.SECONDS))) {
            try {
                // 休眠50毫秒后再次尝试加锁
                Thread.sleep(50);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        try {
            // 1. 查询库存信息
            String stock = redisTemplate.opsForValue().get("stock");

            // 2. 判断库存是否充足
            if (stock != null && !stock.isEmpty()) {
                int st = Integer.parseInt(stock);
                if (st > 0) {
                    // 3.扣减库存
                    redisTemplate.opsForValue().set("stock", String.valueOf(--st));
                }
            }
        } finally {
            // 先判断是否自己的锁，再解锁
            String script = "if redis.call('get', KEYS[1]) == ARGV[1] " +
                    "then " +
                    "   return redis.call('del', KEYS[1]) " +
                    "else " +
                    "   return 0 " +
                    "end";
            // 使用Lua脚本解锁，保证操作的原子性
            this.redisTemplate.execute(new DefaultRedisScript<>(script, Boolean.class), List.of("lock"), uuid);
        }
    }


    /***
     *  使用redis实现分布式锁，包括获取锁、可重入、自动续期、解锁
     *  result: 10,000 concurrency ：Average 202 ms Throughput --> 418.8/sec
     */
    public void deductByRedisLockEncapsulation() {
        DistributedRedisLock redisLock = this.distributedLockClient.getRedisLock("lock");
        redisLock.lock();

        try {
            // 1. 查询库存信息
            String stock = redisTemplate.opsForValue().get("stock").toString();

            // 2. 判断库存是否充足
            if (stock != null && !stock.isEmpty()) {
                int st = Integer.parseInt(stock);
                if (st > 0) {
                    // 3.扣减库存
                    redisTemplate.opsForValue().set("stock", String.valueOf(--st));
                }
            }
        } finally {
            redisLock.unlock();
        }
    }

    /***
     * 使用redisson实现分布式锁
     * result: 10,000 concurrency ：Average 308 ms Throughput --> 320.7/sec
     */
    public void checkAndLockByRedisson() {
        // 加锁，获取锁失败重试
        RLock lock = this.redissonClient.getLock("lock");
        lock.lock();

        // 先查询库存是否充足
        Stock stock = this.stockMapper.selectById(1L);
        // 再减库存
        if (stock != null && stock.getCount() > 0) {
            stock.setCount(stock.getCount() - 1);
            this.stockMapper.updateById(stock);
        }

        // 释放锁
        lock.unlock();
    }

    /***
     *  使用zookeeper实现分布式锁
     *  result: 10,000 concurrency ：Average 394 ms Throughput --> 251.7/sec
     */
    public void checkAndLockByZookeeper() {
        // 加锁，获取锁失败重试
        ZkDistributedLock lock = this.zkclient.getZkDistributedLock("lock");
        lock.lock();

        // 先查询库存是否充足
        Stock stock = this.stockMapper.selectById(1L);
        // 再减库存
        if (stock != null && stock.getCount() > 0) {
            stock.setCount(stock.getCount() - 1);
            this.stockMapper.updateById(stock);
        }

        // 释放锁
        lock.unlock();
    }

    /***
     *  使用curator实现分布式锁
     *  result: 10,000 concurrency ：Average 442 ms Throughput --> 224.5/sec
     */
    public void checkAndLockByCurator() {
        InterProcessMutex mutex = new InterProcessMutex(curatorFramework, "/curator/lock");
        try {
            // 加锁
            mutex.acquire();

            // 先查询库存是否充足
            Stock stock = this.stockMapper.selectById(1L);
            // 再减库存
            if (stock != null && stock.getCount() > 0) {
                stock.setCount(stock.getCount() - 1);
                this.stockMapper.updateById(stock);
            }

            // 释放锁
            mutex.release();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /***
     *  使用mysql创建锁表实现分布式锁
     *  result: 10,000 concurrency ：Average 2,298 ms Throughput --> 42.7/sec
     */
    public void checkAndLockByMySQL() {

        // 加锁
        Lock lock = new Lock();
        lock.setLockName("lock");
        lock.setClassName(this.getClass().getName());
        lock.setCreateTime(new Date());
        try {
            this.lockMapper.insert(lock);
        } catch (Exception ex) {
            // 获取锁失败，则重试
            try {
                Thread.sleep(50);
                this.checkAndLockByMySQL();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        // 先查询库存是否充足
        Stock stock = this.stockMapper.selectById(1L);

        // 再减库存
        if (stock != null && stock.getCount() > 0) {

            stock.setCount(stock.getCount() - 1);
            this.stockMapper.updateById(stock);
        }

        // 释放锁
        this.lockMapper.deleteById(lock.getId());
    }

}
