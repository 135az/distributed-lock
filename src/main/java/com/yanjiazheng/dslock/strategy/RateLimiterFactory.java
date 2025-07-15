package com.yanjiazheng.dslock.strategy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * @author hp
 */
@Component
public class RateLimiterFactory {
    @Autowired
    private Map<String, RateLimitStrategy> strategies;

    public RateLimitStrategy get(String beanName) {
        RateLimitStrategy strat = strategies.get(beanName);
        if (strat == null) {
            throw new IllegalArgumentException("No rate limiter named: " + beanName);
        }
        return strat;
    }
}
