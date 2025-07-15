package com.yanjiazheng.dslock.controller;

import com.yanjiazheng.dslock.service.StockService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StockController {

    @Autowired
    private StockService stockService;

    @GetMapping("check/lock")
    public String checkAndLock() {

        // 1. 使用jvm加锁
        // this.stockService.checkAndLockByJvm();
        // 2. 使用数据库事务操作加锁
        // this.stockService.checkAndLockByTransaction();
        // 3. 使用数据库乐观锁加锁
        // this.stockService.checkAndLockByOptimisticLock();
        // 4.  使用redis乐观锁加锁
        // this.stockService.deductByRedisOptimisticLock();
        // 5. 使用redis分布式锁加锁--简单加锁
        // this.stockService.deductByRedisDistributedLock();
        // 6. 使用redis分布式锁加锁--全面封装
        // this.stockService.deductByRedisLockEncapsulation();
        // 7. 使用Redisson分布式锁加锁
        // this.stockService.checkAndLockByRedisson();
        // 8 使用zookeeper分布式锁加锁
        // this.stockService.checkAndLockByZookeeper();
        // 9. 使用curator实现分布式锁
        // this.stockService.checkAndLockByCurator();
        // 10. 使用mysql数据库实现分布式锁
        this.stockService.checkAndLockByMySQL();
        return "验库存并锁库存成功！";
    }


}
