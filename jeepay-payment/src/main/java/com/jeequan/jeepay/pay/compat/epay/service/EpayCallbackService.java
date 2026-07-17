package com.jeequan.jeepay.pay.compat.epay.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jeequan.jeepay.core.entity.PayOrder;
import com.jeequan.jeepay.core.utils.StringKit;
import com.jeequan.jeepay.pay.compat.epay.protocol.EpayMoney;
import com.jeequan.jeepay.pay.compat.epay.protocol.EpayOrderMetadata;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class EpayCallbackService {

    private static final String INVALID_CALLBACK = "Invalid EPay callback request";
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final Set<String> RESERVED_QUERY_KEYS = Set.of(
            "pid", "trade_no", "out_trade_no", "type", "name", "money",
            "trade_status", "sign_type", "sign");
    private final EpayPresentationSnapshotService snapshotService;

    public enum OrderClassification {
        ORDINARY,
        EPAY,
        CORRUPTED_EPAY
    }

    public EpayCallbackService(EpayPresentationSnapshotService snapshotService) {
        this.snapshotService = snapshotService;
    }

    public boolean isEpayOrder(PayOrder payOrder) {
        return classify(payOrder) == OrderClassification.EPAY;
    }

    public OrderClassification classify(PayOrder payOrder) {
        if (payOrder == null || StringUtils.isBlank(payOrder.getPayOrderId())) {
            return OrderClassification.ORDINARY;
        }

        final boolean tracked;
        try {
            tracked = snapshotService.isTracked(payOrder.getPayOrderId());
        } catch (RuntimeException e) {
            return hasReadEpayProtocol(payOrder.getExtParam())
                    ? OrderClassification.CORRUPTED_EPAY : OrderClassification.ORDINARY;
        }
        if (!tracked) {
            return OrderClassification.ORDINARY;
        }

        try {
            EpayOrderMetadata.fromJson(payOrder.getExtParam());
            return OrderClassification.EPAY;
        } catch (RuntimeException e) {
            return OrderClassification.CORRUPTED_EPAY;
        }
    }

    private boolean hasReadEpayProtocol(String metadata) {
        if (StringUtils.isBlank(metadata)) {
            return false;
        }

        boolean markerRead = false;
        try (JsonParser parser = JSON_MAPPER.getFactory().createParser(metadata)) {
            parser.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                return false;
            }

            JsonToken token;
            while ((token = parser.nextToken()) != null && token != JsonToken.END_OBJECT) {
                if (token != JsonToken.FIELD_NAME) {
                    parser.skipChildren();
                    continue;
                }
                String field = parser.currentName();
                JsonToken value = parser.nextToken();
                if (value == null) {
                    break;
                }
                if ("protocol".equals(field)
                        && value == JsonToken.VALUE_STRING
                        && "epay".equals(parser.getText())) {
                    markerRead = true;
                }
                parser.skipChildren();
            }
            while (parser.nextToken() != null) {
                parser.skipChildren();
            }
        } catch (IOException | RuntimeException e) {
            return markerRead;
        }
        return markerRead;
    }

    public String createNotifyUrl(PayOrder payOrder, String appSecret) {
        return createUrl(payOrder, appSecret, true);
    }

    public String createReturnUrl(PayOrder payOrder, String appSecret) {
        return createUrl(payOrder, appSecret, false);
    }

    private String createUrl(PayOrder payOrder, String appSecret, boolean notify) {
        EpayOrderMetadata metadata = validate(payOrder, appSecret, notify);
        ValidatedCallbackUrl callback = validateCallbackUrl(
                notify ? payOrder.getNotifyUrl() : payOrder.getReturnUrl());
        Map<String, String> callbackParams = new LinkedHashMap<>();
        callbackParams.put("pid", payOrder.getAppId());
        callbackParams.put("trade_no", payOrder.getPayOrderId());
        callbackParams.put("out_trade_no", payOrder.getMchOrderNo());
        callbackParams.put("type", metadata.type());
        callbackParams.put("name", payOrder.getSubject());
        callbackParams.put("money", EpayMoney.formatCents(payOrder.getAmount()));
        callbackParams.put("trade_status", notify || payOrder.getState() == PayOrder.STATE_SUCCESS
                ? "TRADE_SUCCESS" : "WAIT_BUYER_PAY");
        callbackParams.put("sign_type", "MD5");

        Map<String, String> signatureParams = new LinkedHashMap<>(callback.existingQuery());
        signatureParams.putAll(callbackParams);
        callbackParams.put("sign", signGoCompatible(signatureParams, appSecret));

        Map<String, Object> query = new LinkedHashMap<>(callbackParams);
        return StringKit.appendUrlQuery(callback.appendUrl(), query);
    }

    private EpayOrderMetadata validate(PayOrder payOrder, String appSecret, boolean notify) {
        if (payOrder == null
                || StringUtils.isBlank(appSecret)
                || StringUtils.isBlank(notify ? payOrder.getNotifyUrl() : payOrder.getReturnUrl())
                || StringUtils.isBlank(payOrder.getAppId())
                || StringUtils.isBlank(payOrder.getPayOrderId())
                || StringUtils.isBlank(payOrder.getMchOrderNo())
                || StringUtils.isBlank(payOrder.getSubject())
                || payOrder.getAmount() == null
                || payOrder.getAmount() <= 0
                || (!notify && payOrder.getState() == null)) {
            throw new IllegalArgumentException(INVALID_CALLBACK);
        }
        if (classify(payOrder) != OrderClassification.EPAY) {
            throw new IllegalArgumentException(INVALID_CALLBACK);
        }
        return EpayOrderMetadata.fromJson(payOrder.getExtParam());
    }

    private ValidatedCallbackUrl validateCallbackUrl(String callbackUrl) {
        try {
            URI uri = new URI(callbackUrl);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || StringUtils.isBlank(uri.getHost())
                    || uri.getPort() > 65535
                    || uri.getRawUserInfo() != null
                    || uri.getRawFragment() != null) {
                throw new IllegalArgumentException(INVALID_CALLBACK);
            }

            String rawQuery = uri.getRawQuery();
            Map<String, String> existingQuery = new LinkedHashMap<>();
            if (rawQuery != null && !rawQuery.isEmpty()) {
                for (String pair : rawQuery.split("&", -1)) {
                    if (pair.indexOf(';') >= 0) {
                        throw new IllegalArgumentException(INVALID_CALLBACK);
                    }
                    int separator = pair.indexOf('=');
                    String rawKey = separator < 0 ? pair : pair.substring(0, separator);
                    String rawValue = separator < 0 ? "" : pair.substring(separator + 1);
                    String key = decodeQueryComponent(rawKey);
                    String value = decodeQueryComponent(rawValue);
                    if (key.isEmpty()
                            || RESERVED_QUERY_KEYS.contains(key)
                            || existingQuery.containsKey(key)) {
                        throw new IllegalArgumentException(INVALID_CALLBACK);
                    }
                    existingQuery.put(key, value);
                }
                if (callbackUrl.indexOf('=') < 0 && !callbackUrl.endsWith("&")) {
                    callbackUrl += "&";
                }
            }
            return new ValidatedCallbackUrl(callbackUrl, existingQuery);
        } catch (URISyntaxException | RuntimeException e) {
            throw new IllegalArgumentException(INVALID_CALLBACK);
        }
    }

    private record ValidatedCallbackUrl(String appendUrl, Map<String, String> existingQuery) {
    }

    private String decodeQueryComponent(String rawComponent) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(rawComponent.length());
        for (int index = 0; index < rawComponent.length();) {
            char current = rawComponent.charAt(index);
            if (current == '+') {
                bytes.write(' ');
                index++;
                continue;
            }
            if (current == '%') {
                if (index + 2 >= rawComponent.length()) {
                    throw new IllegalArgumentException(INVALID_CALLBACK);
                }
                int high = Character.digit(rawComponent.charAt(index + 1), 16);
                int low = Character.digit(rawComponent.charAt(index + 2), 16);
                if (high < 0 || low < 0) {
                    throw new IllegalArgumentException(INVALID_CALLBACK);
                }
                bytes.write((high << 4) | low);
                index += 3;
                continue;
            }

            if (Character.isHighSurrogate(current)
                    && (index + 1 >= rawComponent.length()
                    || !Character.isLowSurrogate(rawComponent.charAt(index + 1)))) {
                throw new IllegalArgumentException(INVALID_CALLBACK);
            }
            if (Character.isLowSurrogate(current)) {
                throw new IllegalArgumentException(INVALID_CALLBACK);
            }
            int codePoint = rawComponent.codePointAt(index);
            bytes.writeBytes(new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8));
            index += Character.charCount(codePoint);
        }

        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes.toByteArray()))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException(INVALID_CALLBACK);
        }
    }

    private String signGoCompatible(Map<String, String> params, String key) {
        String canonical = params.entrySet().stream()
                .filter(entry -> !"sign".equals(entry.getKey()))
                .filter(entry -> !"sign_type".equals(entry.getKey()))
                .filter(entry -> entry.getValue() != null && !entry.getValue().isEmpty())
                .sorted((left, right) -> compareUtf8(left.getKey(), right.getKey()))
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            return HexFormat.of().formatHex(
                    digest.digest((canonical + key).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 is not available", e);
        }
    }

    private int compareUtf8(String left, String right) {
        byte[] leftBytes = left.getBytes(StandardCharsets.UTF_8);
        byte[] rightBytes = right.getBytes(StandardCharsets.UTF_8);
        int length = Math.min(leftBytes.length, rightBytes.length);
        for (int index = 0; index < length; index++) {
            int comparison = Integer.compare(
                    Byte.toUnsignedInt(leftBytes[index]), Byte.toUnsignedInt(rightBytes[index]));
            if (comparison != 0) {
                return comparison;
            }
        }
        return Integer.compare(leftBytes.length, rightBytes.length);
    }
}
