package com.jeequan.jeepay.pay.compat.epay.service;

import com.jeequan.jeepay.core.constants.CS;
import com.jeequan.jeepay.core.entity.MchApp;
import com.jeequan.jeepay.core.entity.PayOrder;
import com.jeequan.jeepay.pay.compat.epay.model.EpayBrowserStatus;
import com.jeequan.jeepay.service.impl.MchAppService;
import com.jeequan.jeepay.service.impl.PayOrderService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.regex.Pattern;

@Service
public class EpayBrowserStatusService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String TOKEN_CONTEXT = "epay-browser-status\n";
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[0-9A-Fa-f]{64}");

    private final PayOrderService payOrderService;
    private final MchAppService mchAppService;
    private final EpayCallbackService callbackService;

    public EpayBrowserStatusService(
            PayOrderService payOrderService,
            MchAppService mchAppService,
            EpayCallbackService callbackService) {
        this.payOrderService = payOrderService;
        this.mchAppService = mchAppService;
        this.callbackService = callbackService;
    }

    public String createStatusUrl(String payOrderId) {
        AccessContext context = load(payOrderId);
        String token = sign(payOrderId, context.app().getAppSecret());
        return UriComponentsBuilder.fromPath("/api/epay/pay-orders/{payOrderId}/status")
                .queryParam("token", token)
                .buildAndExpand(payOrderId)
                .encode()
                .toUriString();
    }

    public EpayBrowserStatus query(String payOrderId, String token) {
        AccessContext context = load(payOrderId);
        byte[] expected = parseHex(sign(payOrderId, context.app().getAppSecret()));
        byte[] actual = parseHex(token);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new AccessDeniedException();
        }

        Byte state = context.order().getState();
        if (Byte.valueOf(PayOrder.STATE_INIT).equals(state)
                || Byte.valueOf(PayOrder.STATE_ING).equals(state)) {
            return new EpayBrowserStatus("pending", null);
        }
        if (Byte.valueOf(PayOrder.STATE_SUCCESS).equals(state)) {
            return new EpayBrowserStatus(
                    "success",
                    callbackService.createReturnUrl(context.order(), context.app().getAppSecret()));
        }
        return new EpayBrowserStatus("failed", null);
    }

    private AccessContext load(String payOrderId) {
        if (StringUtils.isBlank(payOrderId)) {
            throw new AccessDeniedException();
        }
        PayOrder order = payOrderService.getById(payOrderId);
        if (order == null
                || callbackService.classify(order) != EpayCallbackService.OrderClassification.EPAY) {
            throw new AccessDeniedException();
        }
        MchApp app = mchAppService.getOneByMch(order.getMchNo(), order.getAppId());
        if (app == null
                || !Byte.valueOf(CS.YES).equals(app.getState())
                || StringUtils.isBlank(app.getAppSecret())) {
            throw new AccessDeniedException();
        }
        return new AccessContext(order, app);
    }

    private String sign(String payOrderId, String appSecret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return HexFormat.of().formatHex(
                    mac.doFinal((TOKEN_CONTEXT + payOrderId).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 is not available", e);
        }
    }

    private byte[] parseHex(String token) {
        if (token == null || !TOKEN_PATTERN.matcher(token).matches()) {
            throw new AccessDeniedException();
        }
        try {
            return HexFormat.of().parseHex(token);
        } catch (IllegalArgumentException e) {
            throw new AccessDeniedException();
        }
    }

    private record AccessContext(PayOrder order, MchApp app) {
    }

    public static final class AccessDeniedException extends RuntimeException {
        public AccessDeniedException() {
            super(null, null, false, false);
        }
    }
}
