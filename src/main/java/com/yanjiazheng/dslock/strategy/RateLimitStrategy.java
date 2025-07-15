package com.yanjiazheng.dslock.strategy;

/**
 * @author hp
 */
public interface RateLimitStrategy {
    boolean allow(String key, String... args);
}