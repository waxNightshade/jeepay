package com.jeequan.jeepay.pay.compat.epay.protocol;

public class EpayValidationException extends RuntimeException {

    public EpayValidationException() {
        super("Invalid EPay data");
    }
}
