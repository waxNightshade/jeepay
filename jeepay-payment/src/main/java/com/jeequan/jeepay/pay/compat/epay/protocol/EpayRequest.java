package com.jeequan.jeepay.pay.compat.epay.protocol;

public record EpayRequest(
        String pid,
        String type,
        String outTradeNo,
        String notifyUrl,
        String returnUrl,
        String name,
        String money,
        String sign,
        String signType) {
}
