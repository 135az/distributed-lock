package com.yanjiazheng.dslock.util;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.springframework.stereotype.Component;

@Component
public class ZkClient {

    private static final String CONNECT_STRING = "127.0.0.1:2181";

    private static final String ROOT_PATH = "/distributed";

    private ZooKeeper zooKeeper;

    @PostConstruct
    public void init() {
        try {
            // 连接zookeeper服务器
            this.zooKeeper = new ZooKeeper(CONNECT_STRING, 30000, event -> System.out.println("获取链接成功！！"));

            // 创建分布式锁根节点
            if (this.zooKeeper.exists(ROOT_PATH, false) == null) {
                this.zooKeeper.create(ROOT_PATH, null, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            }
        } catch (Exception e) {
            System.out.println("获取链接失败！");
            e.printStackTrace();
        }
    }

    @PreDestroy
    public void destroy() {
        try {
            if (zooKeeper != null) {
                zooKeeper.close();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * 初始化zk分布式锁对象方法
     *
     * @param lockName
     * @return
     */
    public ZkDistributedLock getZkDistributedLock(String lockName) {
        return new ZkDistributedLock(zooKeeper, lockName);
    }
}
