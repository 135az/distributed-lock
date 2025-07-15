package com.yanjiazheng.dslock.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author hp
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PaymentResponse {

    private String orderId;
    private String status;

}
