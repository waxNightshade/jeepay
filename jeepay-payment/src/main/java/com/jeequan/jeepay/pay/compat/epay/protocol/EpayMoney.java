package com.jeequan.jeepay.pay.compat.epay.protocol;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Pattern;

public final class EpayMoney {

    private static final BigDecimal CENTS_PER_UNIT = BigDecimal.valueOf(100);
    private static final Pattern DECIMAL_AMOUNT = Pattern.compile("[0-9]+(?:\\.[0-9]{1,2})?");

    private EpayMoney() {
    }

    public static long parseCents(String value) {
        if (value == null || !DECIMAL_AMOUNT.matcher(value).matches()) {
            throw new EpayValidationException();
        }
        try {
            BigDecimal amount = new BigDecimal(value);
            if (amount.signum() <= 0 || amount.scale() > 2) {
                throw new EpayValidationException();
            }
            return amount.multiply(CENTS_PER_UNIT).longValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            throw new EpayValidationException();
        }
    }

    public static String formatCents(long cents) {
        if (cents <= 0) {
            throw new EpayValidationException();
        }
        return BigDecimal.valueOf(cents)
                .divide(CENTS_PER_UNIT, 2, RoundingMode.UNNECESSARY)
                .toPlainString();
    }
}
