package com.jeequan.jeepay.pay.compat.epay.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class EpayBrowserStatus {

    private final String status;
    private final String returnUrl;

    public EpayBrowserStatus(String status, String returnUrl) {
        this.status = status;
        this.returnUrl = returnUrl;
    }

    public String getStatus() {
        return status;
    }

    public String getReturnUrl() {
        return returnUrl;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof EpayBrowserStatus that)) {
            return false;
        }
        return Objects.equals(status, that.status)
                && Objects.equals(returnUrl, that.returnUrl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, returnUrl);
    }

    @Override
    public String toString() {
        return "EpayBrowserStatus[status=" + status + ", returnUrl=" + returnUrl + ']';
    }
}
