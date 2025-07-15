package com.yanjiazheng.dslock.service;

import com.yanjiazheng.dslock.pojo.PaymentResponse;
import org.springframework.stereotype.Service;

/**
 * @author hp
 */
@Service
public class PaymentService {

    public PaymentResponse queryPayment(String orderId) {
        // 模拟支付查询逻辑
        return new PaymentResponse(orderId, "SUCCESS");
    }
}
