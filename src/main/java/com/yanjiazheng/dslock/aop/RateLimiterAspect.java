package com.yanjiazheng.dslock.aop;

import com.yanjiazheng.dslock.annotations.*;
import com.yanjiazheng.dslock.strategy.RateLimitStrategy;
import com.yanjiazheng.dslock.strategy.RateLimiterFactory;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author hp
 */
@Aspect
@Component
public class RateLimiterAspect {

    @Autowired
    private RateLimiterFactory factory;

    @Around("@annotation(fixed)")
    public Object aroundFixed(ProceedingJoinPoint pjp, FixedWindowRateLimit fixed) throws Throwable {
        return doProceed(pjp, "fixedWindowRateLimiter", pjp.getSignature().toShortString(),
                String.valueOf(fixed.limit()), String.valueOf(fixed.window()),
                String.valueOf(System.currentTimeMillis()));
    }

    @Around("@annotation(sliding)")
    public Object aroundSliding(ProceedingJoinPoint pjp, SlidingWindowRateLimit sliding) throws Throwable {
        return doProceed(pjp, "slidingWindowRateLimiter", pjp.getSignature().toShortString(),
                String.valueOf(System.currentTimeMillis()), String.valueOf(sliding.limit()),
                String.valueOf(sliding.window()));
    }

    @Around("@annotation(leaky)")
    public Object aroundLeaky(ProceedingJoinPoint pjp, LeakyBucketRateLimit leaky) throws Throwable {
        return doProceed(pjp, "leakyBucketRateLimiter", pjp.getSignature().toShortString(),
                String.valueOf(leaky.capacity()), String.valueOf(leaky.rate()),
                String.valueOf(System.currentTimeMillis()));
    }

    @Around("@annotation(token)")
    public Object aroundToken(ProceedingJoinPoint pjp, TokenBucketRateLimit token) throws Throwable {
        return doProceed(pjp, "tokenBucketRateLimiter", pjp.getSignature().toShortString(),
                String.valueOf(token.capacity()), String.valueOf(token.rate()),
                String.valueOf(System.currentTimeMillis()));
    }

    @Around("@annotation(hybrid)")
    public Object aroundHybrid(ProceedingJoinPoint pjp, HybridBucketRateLimit hybrid) throws Throwable {
        return doProceed(pjp, "hybridBucketRateLimiter", pjp.getSignature().toShortString(),
                String.valueOf(hybrid.tokenLimit()), String.valueOf(hybrid.queueLimit()),
                String.valueOf(hybrid.window()), String.valueOf(System.currentTimeMillis()));
    }

    private Object doProceed(ProceedingJoinPoint pjp, String beanName,
                             String keyPrefix, String... args) throws Throwable {
        RateLimitStrategy strat = factory.get(beanName);
        if (!strat.allow(keyPrefix, args)) {
            throw new RuntimeException("Rate limit exceeded: " + beanName);
        }
        return pjp.proceed();
    }
}