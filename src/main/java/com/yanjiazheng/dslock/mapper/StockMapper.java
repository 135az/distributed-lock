package com.yanjiazheng.dslock.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yanjiazheng.dslock.pojo.Stock;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author hp
 */
@Mapper
public interface StockMapper extends BaseMapper<Stock> {

    public Stock selectStockForUpdate(Long id);
}
