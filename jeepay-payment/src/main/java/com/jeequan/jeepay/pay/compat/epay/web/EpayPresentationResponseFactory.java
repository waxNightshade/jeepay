package com.jeequan.jeepay.pay.compat.epay.web;

import com.jeequan.jeepay.core.constants.CS;
import com.jeequan.jeepay.core.entity.PayOrder;
import com.jeequan.jeepay.pay.compat.epay.model.EpayPreparedOrder;
import com.jeequan.jeepay.pay.compat.epay.model.EpayPresentation;
import com.jeequan.jeepay.pay.compat.epay.protocol.EpayMoney;
import com.jeequan.jeepay.pay.rqrs.payorder.UnifiedOrderRQ;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.ModelAndView;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

@Component
public class EpayPresentationResponseFactory {

    private static final MediaType UTF8_HTML =
            new MediaType("text", "html", StandardCharsets.UTF_8);
    private static final MediaType UTF8_TEXT =
            new MediaType("text", "plain", StandardCharsets.UTF_8);

    public void validate(EpayPresentation presentation, EpayPreparedOrder preparedOrder) {
        if (presentation == null || presentation.payDataType() == null) {
            throw invalidPresentation();
        }
        switch (presentation.payDataType()) {
            case CS.PAY_DATA_TYPE.PAY_URL -> safeHttpUri(presentation.payData());
            case CS.PAY_DATA_TYPE.FORM -> {
                if (isBlank(presentation.payData())) {
                    throw invalidPresentation();
                }
            }
            case CS.PAY_DATA_TYPE.CODE_IMG_URL -> {
                safeHttpUri(presentation.payData());
                DisplayOrder order = displayOrder(preparedOrder);
                EpayMoney.formatCents(order.amount());
            }
            default -> throw invalidPresentation();
        }
    }

    public Object create(
            EpayPresentation presentation,
            EpayPreparedOrder preparedOrder,
            String statusUrl) {
        validate(presentation, preparedOrder);

        return switch (presentation.payDataType()) {
            case CS.PAY_DATA_TYPE.PAY_URL -> redirect(presentation.payData());
            case CS.PAY_DATA_TYPE.FORM -> channelForm(presentation.payData());
            case CS.PAY_DATA_TYPE.CODE_IMG_URL ->
                    qrCode(presentation.payData(), preparedOrder, statusUrl);
            default -> throw invalidPresentation();
        };
    }

    public ResponseEntity<String> failure(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .contentType(UTF8_TEXT)
                .body(message);
    }

    private static ResponseEntity<Void> redirect(String value) {
        URI uri = safeHttpUri(value);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, uri.toASCIIString())
                .build();
    }

    private static ResponseEntity<String> channelForm(String value) {
        if (isBlank(value)) {
            throw invalidPresentation();
        }
        return ResponseEntity.ok()
                .contentType(UTF8_HTML)
                .body(value);
    }

    private static ModelAndView qrCode(
            String value, EpayPreparedOrder preparedOrder, String statusUrl) {
        URI uri = safeHttpUri(value);
        URI safeStatusUri = safeStatusUrl(statusUrl);
        DisplayOrder displayOrder = displayOrder(preparedOrder);
        ModelAndView modelAndView = new ModelAndView("epay/wxNative");
        modelAndView.addObject("codeImgUrl", uri.toASCIIString());
        modelAndView.addObject("orderNo", displayOrder.orderNo());
        modelAndView.addObject("money", EpayMoney.formatCents(displayOrder.amount()));
        modelAndView.addObject("name", displayOrder.name());
        modelAndView.addObject("statusUrl", safeStatusUri.toASCIIString());
        return modelAndView;
    }

    private static DisplayOrder displayOrder(EpayPreparedOrder preparedOrder) {
        if (preparedOrder instanceof EpayPreparedOrder.NewOrder newOrder) {
            UnifiedOrderRQ request = newOrder.request();
            if (request == null || request.getAmount() == null) {
                throw invalidPresentation();
            }
            return new DisplayOrder(
                    request.getMchOrderNo(), request.getAmount(), request.getSubject());
        }
        if (preparedOrder instanceof EpayPreparedOrder.Replay replay) {
            PayOrder order = replay.payOrder();
            if (order == null || order.getAmount() == null) {
                throw invalidPresentation();
            }
            return new DisplayOrder(
                    order.getMchOrderNo(), order.getAmount(), order.getSubject());
        }
        throw invalidPresentation();
    }

    private static URI safeHttpUri(String value) {
        if (isBlank(value)) {
            throw invalidPresentation();
        }
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            int port = uri.getPort();
            if (uri.isOpaque()
                    || scheme == null
                    || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    || isBlank(uri.getHost())
                    || uri.getRawUserInfo() != null
                    || uri.getRawFragment() != null
                    || port == 0
                    || port > 65535) {
                throw invalidPresentation();
            }
            return uri;
        } catch (URISyntaxException | IllegalArgumentException e) {
            throw invalidPresentation();
        }
    }

    private static URI safeStatusUrl(String value) {
        if (isBlank(value)) {
            throw invalidPresentation();
        }
        try {
            URI uri = new URI(value);
            String path = uri.getRawPath();
            if (uri.isOpaque()
                    || uri.getScheme() != null
                    || uri.getRawAuthority() != null
                    || uri.getRawFragment() != null
                    || path == null
                    || !path.startsWith("/api/epay/pay-orders/")
                    || !path.endsWith("/status")) {
                throw invalidPresentation();
            }
            return uri;
        } catch (URISyntaxException | IllegalArgumentException e) {
            throw invalidPresentation();
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static IllegalArgumentException invalidPresentation() {
        return new IllegalArgumentException("Invalid EPay presentation");
    }

    private record DisplayOrder(String orderNo, long amount, String name) {
    }
}
