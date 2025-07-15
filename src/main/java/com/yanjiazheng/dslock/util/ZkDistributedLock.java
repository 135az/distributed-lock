package com.yanjiazheng.dslock.util;


import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;

import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * @author hp
 */
public class ZkDistributedLock {

    private static final String ROOT_PATH = "/distributed";
    private static final ThreadLocal<Integer> THREAD_LOCAL = new ThreadLocal<>();

    private String path;

    private ZooKeeper zooKeeper;

    public ZkDistributedLock(ZooKeeper zooKeeper, String lockName) {
        try {
            this.zooKeeper = zooKeeper;
            if (THREAD_LOCAL.get() == null || THREAD_LOCAL.get() == 0) {
                this.path = zooKeeper.create(ROOT_PATH + "/" + lockName + "-", null, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);
            }
        } catch (KeeperException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * 获取分布式锁
     * 该方法通过在ZooKeeper中创建节点并监听前一个节点的状态来实现分布式锁的功能
     * 使用ThreadLocal来记录当前线程的锁深度，以支持递归调用
     */
    public void lock() {
        // 检查当前线程是否已经持有锁，如果是，则增加锁的计数
        Integer flag = THREAD_LOCAL.get();
        if (flag != null && flag > 0) {
            THREAD_LOCAL.set(flag + 1);
            return;
        }
        try {
            // 获取当前节点的前一个节点路径
            String preNode = getPreNode(path);
            // 如果该节点没有前一个节点，说明该节点时最小节点，放行执行业务逻辑
            if (StringUtils.isEmpty(preNode)) {
                THREAD_LOCAL.set(1);
                return;
            } else {
                // 创建一个计数器，用于阻塞当前线程直到前一个节点被删除
                CountDownLatch countDownLatch = new CountDownLatch(1);
                // 检查前一个节点是否存在，如果不存在，则放行执行业务逻辑
                if (this.zooKeeper.exists(ROOT_PATH + "/" + preNode, event -> countDownLatch.countDown()) == null) {
                    THREAD_LOCAL.set(1);
                    return;
                }
                // 阻塞当前线程，直到前一个节点被删除
                countDownLatch.await();
                THREAD_LOCAL.set(1);
                return;
            }
        } catch (Exception e) {
            // 异常处理：打印异常信息，并在200毫秒后重试获取锁
            e.printStackTrace();
            try {
                Thread.sleep(200);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
            lock();
        }
    }

    /**
     * 解锁方法，用于释放锁
     * <p>
     * 此方法通过减少线程本地变量中的计数来实现解锁逻辑当计数降至0时，表明该线程不再持有锁，此时会尝试移除ZooKeeper中的对应节点以释放锁
     * 如果移除节点操作成功，也将从线程本地变量中移除相关数据
     * <p>
     * 注意：此方法忽略了异常处理，仅打印异常堆栈信息在实际应用中，可能需要更详细的异常处理逻辑
     */
    public void unlock() {
        try {
            // 减少线程本地变量中的计数值，表示释放一个锁
            THREAD_LOCAL.set(THREAD_LOCAL.get() - 1);
            // 当计数值为0时，表明该线程不再持有锁，尝试删除ZooKeeper中的节点以释放锁
            if (THREAD_LOCAL.get() == 0) {
                this.zooKeeper.delete(path, 0);
                // 从线程本地变量中移除相关数据，避免内存泄漏
                THREAD_LOCAL.remove();
            }
        } catch (InterruptedException | KeeperException e) {
            // 打印异常信息，实际应用中可能需要更详细的异常处理逻辑
            e.printStackTrace();
        }
    }


    /**
     * 获取指定节点的前节点
     *
     * @param path 后端节点的路径
     * @return 返回前一个节点的路径，如果不存在则返回null
     */
    private String getPreNode(String path) {

        try {
            // 获取当前节点的序列化号
            long curSerial = Long.parseLong(StringUtils.substringAfterLast(path, "-"));
            // 获取根路径下的所有序列化子节点
            List<String> nodes = this.zooKeeper.getChildren(ROOT_PATH, false);

            // 判空
            if (CollectionUtils.isEmpty(nodes)) {
                return null;
            }

            // 获取前一个节点
            long flag = 0L;
            String preNode = null;
            for (String node : nodes) {
                // 获取每个节点的序列化号
                long serial = Long.parseLong(StringUtils.substringAfterLast(node, "-"));
                if (serial < curSerial && serial > flag) {
                    flag = serial;
                    preNode = node;
                }
            }

            return preNode;
        } catch (KeeperException | InterruptedException e) {
            e.printStackTrace();
        }
        return null;
    }

}
