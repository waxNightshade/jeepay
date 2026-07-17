package com.jeequan.jeepay.pay.compat.epay.protocol;

import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public record EpayOrderMetadata(String protocol, String type) {

    private static final String PROTOCOL = "epay";
    private static final Set<String> SUPPORTED_TYPES = Set.of("alipay", "wxpay");
    private static final Set<String> FIELDS = Set.of("protocol", "type");
    private static final int MAX_JSON_LENGTH = 128;
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    public EpayOrderMetadata {
        if (!PROTOCOL.equals(protocol) || !SUPPORTED_TYPES.contains(type)) {
            throw new EpayValidationException();
        }
    }

    public static EpayOrderMetadata of(String type) {
        return new EpayOrderMetadata(PROTOCOL, type);
    }

    public String toJson() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("protocol", protocol);
        fields.put("type", type);
        String json = JSONUtil.toJsonStr(fields);
        if (json.length() > MAX_JSON_LENGTH) {
            throw new EpayValidationException();
        }
        return json;
    }

    public static EpayOrderMetadata fromJson(String json) {
        if (json == null || json.isBlank() || json.length() > MAX_JSON_LENGTH) {
            throw new EpayValidationException();
        }

        try {
            JsonNode object = JSON_MAPPER.readValue(json, JsonNode.class);
            if (!object.isObject()
                    || object.size() != FIELDS.size()
                    || !object.has("protocol")
                    || !object.has("type")
                    || !object.get("protocol").isTextual()
                    || !object.get("type").isTextual()) {
                throw new EpayValidationException();
            }
            return new EpayOrderMetadata(object.get("protocol").textValue(), object.get("type").textValue());
        } catch (IOException | RuntimeException e) {
            throw new EpayValidationException();
        }
    }

    public boolean isEpay() {
        return PROTOCOL.equals(protocol);
    }
}
