package com.jeequan.jeepay.service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jeequan.jeepay.core.entity.OrderSnapshot;
import org.apache.ibatis.annotations.Param;

import java.util.Date;

public interface OrderSnapshotMapper extends BaseMapper<OrderSnapshot> {

    OrderSnapshot selectByOrder(@Param("orderId") String orderId,
                                @Param("orderType") Byte orderType);

    int upsertMchResponse(@Param("orderId") String orderId,
                          @Param("orderType") Byte orderType,
                          @Param("mchRespData") String mchRespData,
                          @Param("mchRespTime") Date mchRespTime);
}
