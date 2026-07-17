package com.jeequan.jeepay.pay.compat.epay.model;

import com.jeequan.jeepay.core.entity.PayOrder;
import com.jeequan.jeepay.pay.rqrs.payorder.UnifiedOrderRQ;

public sealed interface EpayPreparedOrder
        permits EpayPreparedOrder.NewOrder, EpayPreparedOrder.Replay {

    record NewOrder(String wayCode, UnifiedOrderRQ request) implements EpayPreparedOrder {
    }

    record Replay(PayOrder payOrder, EpayPresentation presentation) implements EpayPreparedOrder {
    }
}
