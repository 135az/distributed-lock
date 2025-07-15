package com.yanjiazheng.dslock.pojo;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("db_stock")
public class Stock {

    @TableId
    private Long id;

    private String productCode;

    private String stockCode;

    private Integer count;

    private Long version;
}
