package com.yanjiazheng.dslock.test;

import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;

import java.util.concurrent.CountDownLatch;

public class ZkTest {

    public static void main(String[] args) throws InterruptedException {

        CountDownLatch countDownLatch = new CountDownLatch(1);
        ZooKeeper zooKeeper = null;
        try {
            zooKeeper = new ZooKeeper("127.0.0.1:2181", 30000, event -> {
                if (Watcher.Event.KeeperState.SyncConnected.equals(event.getState())
                        && Watcher.Event.EventType.None.equals(event.getType())) {
                    System.out.println("获取链接成功。。。。。。" + event);
                    countDownLatch.countDown();
                }
            });
            countDownLatch.await();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (zooKeeper != null) {
                System.out.println("关闭链接。。。。。。");
                zooKeeper.close();
            }
        }
    }
}
