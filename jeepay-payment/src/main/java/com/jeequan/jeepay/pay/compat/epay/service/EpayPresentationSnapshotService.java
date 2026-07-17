package com.jeequan.jeepay.pay.compat.epay.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jeequan.jeepay.core.constants.CS;
import com.jeequan.jeepay.core.entity.MchNotifyRecord;
import com.jeequan.jeepay.core.entity.OrderSnapshot;
import com.jeequan.jeepay.pay.compat.epay.model.EpayPresentation;
import com.jeequan.jeepay.service.impl.OrderSnapshotService;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class EpayPresentationSnapshotService {

    private static final Set<String> SUPPORTED_PAY_DATA_TYPES = Set.of(
            CS.PAY_DATA_TYPE.PAY_URL,
            CS.PAY_DATA_TYPE.FORM,
            CS.PAY_DATA_TYPE.CODE_IMG_URL
    );
    private static final Set<String> FIELDS = Set.of("payDataType", "payData");
    private static final String INITIALIZING_SNAPSHOT = "{\"state\":\"initializing\"}";
    private static final String INVALID_SNAPSHOT_MESSAGE = "Invalid EPay presentation snapshot";
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private final OrderSnapshotService orderSnapshotService;

    public EpayPresentationSnapshotService(OrderSnapshotService orderSnapshotService) {
        this.orderSnapshotService = orderSnapshotService;
    }

    public void initialize(String payOrderId) {
        validateOrderId(payOrderId);
        orderSnapshotService.upsertMchResponse(
                payOrderId, MchNotifyRecord.TYPE_PAY_ORDER, INITIALIZING_SNAPSHOT, new Date());
    }

    public boolean isTracked(String payOrderId) {
        validateOrderId(payOrderId);
        return orderSnapshotService.findByOrder(
                payOrderId, MchNotifyRecord.TYPE_PAY_ORDER) != null;
    }

    public void save(String payOrderId, EpayPresentation presentation) {
        validateOrderId(payOrderId);
        if (presentation == null
                || !isSupportedType(presentation.payDataType())
                || isBlank(presentation.payData())) {
            throw new IllegalArgumentException("Invalid EPay presentation");
        }

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("payDataType", presentation.payDataType());
        fields.put("payData", presentation.payData());

        final String json;
        try {
            json = JSON_MAPPER.writeValueAsString(fields);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize EPay presentation snapshot");
        }

        orderSnapshotService.upsertMchResponse(
                payOrderId, MchNotifyRecord.TYPE_PAY_ORDER, json, new Date());
    }

    public Optional<EpayPresentation> find(String payOrderId) {
        validateOrderId(payOrderId);

        OrderSnapshot snapshot = orderSnapshotService.findByOrder(
                payOrderId, MchNotifyRecord.TYPE_PAY_ORDER);
        if (snapshot == null) {
            return Optional.empty();
        }
        if (INITIALIZING_SNAPSHOT.equals(snapshot.getMchRespData())) {
            return Optional.empty();
        }
        if (isBlank(snapshot.getMchRespData())) {
            throw invalidSnapshot();
        }

        try {
            JsonNode object = JSON_MAPPER.readValue(snapshot.getMchRespData(), JsonNode.class);
            if (!object.isObject()
                    || object.size() != FIELDS.size()
                    || !object.has("payDataType")
                    || !object.has("payData")
                    || !object.get("payDataType").isTextual()
                    || !object.get("payData").isTextual()) {
                throw invalidSnapshot();
            }

            String payDataType = object.get("payDataType").textValue();
            String payData = object.get("payData").textValue();
            if (!isSupportedType(payDataType) || isBlank(payData)) {
                throw invalidSnapshot();
            }
            return Optional.of(new EpayPresentation(payDataType, payData));
        } catch (JsonProcessingException e) {
            throw invalidSnapshot();
        }
    }

    private static void validateOrderId(String payOrderId) {
        if (isBlank(payOrderId)) {
            throw new IllegalArgumentException("Pay order ID is required");
        }
    }

    private static boolean isSupportedType(String payDataType) {
        return payDataType != null && SUPPORTED_PAY_DATA_TYPES.contains(payDataType);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static IllegalStateException invalidSnapshot() {
        return new IllegalStateException(INVALID_SNAPSHOT_MESSAGE);
    }
}
