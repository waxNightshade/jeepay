package com.jeequan.jeepay.service.impl;

import com.jeequan.jeepay.core.entity.OrderSnapshot;
import com.jeequan.jeepay.service.mapper.OrderSnapshotMapper;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class OrderSnapshotService {

    private final OrderSnapshotMapper orderSnapshotMapper;

    public OrderSnapshotService(OrderSnapshotMapper orderSnapshotMapper) {
        this.orderSnapshotMapper = orderSnapshotMapper;
    }

    public OrderSnapshot findByOrder(String orderId, Byte orderType) {
        return orderSnapshotMapper.selectByOrder(orderId, orderType);
    }

    public void upsertMchResponse(String orderId, Byte orderType, String mchRespData, Date mchRespTime) {
        orderSnapshotMapper.upsertMchResponse(orderId, orderType, mchRespData, mchRespTime);
    }
}
