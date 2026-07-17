package com.jeequan.jeepay.pay.compat.epay.protocol;

import org.springframework.util.MultiValueMap;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class EpayRequestValidator {

    private static final int MAX_PARAMETER_VALUES = 16;
    private static final Pattern MD5_SIGNATURE = Pattern.compile("[0-9a-fA-F]{32}");
    private static final Set<String> BASE_FIELDS = Set.of(
            "pid",
            "type",
            "out_trade_no",
            "notify_url",
            "return_url",
            "name",
            "money",
            "sign",
            "sign_type");

    private EpayRequestValidator() {
    }

    public static UnsignedRequest validate(MultiValueMap<String, String> form) {
        Map<String, String> params = requireSingleValues(form);

        String pid = requireField(params, "pid", 64);
        String type = requireField(params, "type", Integer.MAX_VALUE);
        String outTradeNo = requireField(params, "out_trade_no", 64);
        String notifyUrl = requireField(params, "notify_url", 128);
        String returnUrl = requireField(params, "return_url", 128);
        String name = requireField(params, "name", 64);
        String money = requireField(params, "money", 20);
        String sign = requireField(params, "sign", 32);
        String signType = requireField(params, "sign_type", Integer.MAX_VALUE);

        if (!"MD5".equalsIgnoreCase(signType) || !MD5_SIGNATURE.matcher(sign).matches()) {
            throw invalid();
        }

        String wayCode = switch (type) {
            case "alipay" -> "ALI_PC";
            case "wxpay" -> "WX_NATIVE";
            default -> throw invalid();
        };

        long amountCents = EpayMoney.parseCents(money);
        requireCallbackUrl(notifyUrl);
        requireCallbackUrl(returnUrl);

        EpayRequest request = new EpayRequest(
                pid,
                type,
                outTradeNo,
                notifyUrl,
                returnUrl,
                name,
                money,
                sign,
                signType);
        return new UnsignedRequest(request, params, amountCents, wayCode);
    }

    public static VerifiedRequest verifySignature(UnsignedRequest unsigned, String appSecret) {
        if (unsigned == null || appSecret == null || appSecret.isBlank()) {
            throw invalid();
        }
        if (!EpaySigner.verify(
                unsigned.signingParams,
                appSecret,
                unsigned.request.sign())) {
            throw invalid();
        }
        return new VerifiedRequest(
                unsigned.request,
                unsigned.amountCents,
                unsigned.wayCode);
    }

    private static Map<String, String> requireSingleValues(MultiValueMap<String, String> form) {
        if (form == null) {
            throw invalid();
        }

        int valueCount = 0;
        for (List<String> values : form.values()) {
            if (values == null) {
                throw invalid();
            }
            valueCount += values.size();
            if (valueCount > MAX_PARAMETER_VALUES) {
                throw invalid();
            }
        }

        boolean hasDevice = form.containsKey("device");
        if (!BASE_FIELDS.equals(form.keySet())
                && !(hasDevice
                && form.size() == BASE_FIELDS.size() + 1
                && form.keySet().containsAll(BASE_FIELDS))) {
            throw invalid();
        }

        Map<String, String> params = new LinkedHashMap<>();
        for (String field : BASE_FIELDS) {
            List<String> values = form.get(field);
            if (values == null) {
                throw invalid();
            }
            if (values.size() != 1) {
                throw invalid();
            }
            String value = values.get(0);
            if (value == null) {
                throw invalid();
            }
            params.put(field, value);
        }
        if (hasDevice) {
            List<String> values = form.get("device");
            if (values == null || values.size() != 1 || !"pc".equals(values.get(0))) {
                throw invalid();
            }
            params.put("device", values.get(0));
        }
        return Map.copyOf(params);
    }

    private static String requireField(Map<String, String> params, String field, int maxLength) {
        String value = params.get(field);
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw invalid();
        }
        return value;
    }

    private static void requireCallbackUrl(String value) {
        if (parseHttpUri(value).getRawFragment() != null) {
            throw invalid();
        }
    }

    private static URI parseHttpUri(String value) {
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            if (!uri.isAbsolute()
                    || scheme == null
                    || (!("http".equalsIgnoreCase(scheme)) && !("https".equalsIgnoreCase(scheme)))
                    || uri.getHost() == null
                    || uri.getHost().isBlank()
                    || uri.getRawUserInfo() != null
                    || uri.getRawAuthority().endsWith(":")) {
                throw invalid();
            }
            int port = uri.getPort();
            if (port == 0 || port > 65535) {
                throw invalid();
            }
            return uri;
        } catch (URISyntaxException e) {
            throw invalid();
        }
    }

    private static EpayValidationException invalid() {
        return new EpayValidationException();
    }

    public static final class UnsignedRequest {

        private final EpayRequest request;
        private final Map<String, String> signingParams;
        private final long amountCents;
        private final String wayCode;

        private UnsignedRequest(
                EpayRequest request,
                Map<String, String> signingParams,
                long amountCents,
                String wayCode) {
            this.request = request;
            this.signingParams = Map.copyOf(signingParams);
            this.amountCents = amountCents;
            this.wayCode = wayCode;
        }

        public String pid() {
            return request.pid();
        }
    }

    public static final class VerifiedRequest {

        private final EpayRequest request;
        private final long amountCents;
        private final String wayCode;

        private VerifiedRequest(EpayRequest request, long amountCents, String wayCode) {
            this.request = request;
            this.amountCents = amountCents;
            this.wayCode = wayCode;
        }

        public EpayRequest request() {
            return request;
        }

        public long amountCents() {
            return amountCents;
        }

        public String wayCode() {
            return wayCode;
        }
    }

}
