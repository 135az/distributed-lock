package com.yanjiazheng.dslock.controller;

import com.yanjiazheng.dslock.annotations.FixedWindowRateLimit;
import com.yanjiazheng.dslock.pojo.PaymentResponse;
import com.yanjiazheng.dslock.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author hp
 */
@RestController
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    // @HybridBucketRateLimit(tokenLimit = 150, queueLimit = 100, window = 5) 
    @FixedWindowRateLimit(limit = 100, window = 5) // 每 5 秒最多允许 100 次请求
    @GetMapping("/payment/query")
    public PaymentResponse queryPayment(@RequestParam String orderId) {
        if (orderId == null || orderId.trim().isEmpty()) {
            throw new IllegalArgumentException("orderId cannot be empty");
        }
        return paymentService.queryPayment(orderId);
    }
}
