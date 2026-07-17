package com.jeequan.jeepay.pay.compat.epay.service;

import com.jeequan.jeepay.core.constants.CS;
import com.jeequan.jeepay.core.entity.MchApp;
import com.jeequan.jeepay.core.entity.MchInfo;
import com.jeequan.jeepay.core.entity.PayOrder;
import com.jeequan.jeepay.pay.compat.epay.model.EpayPreparedOrder;
import com.jeequan.jeepay.pay.compat.epay.model.EpayPresentation;
import com.jeequan.jeepay.pay.compat.epay.protocol.EpayOrderMetadata;
import com.jeequan.jeepay.pay.compat.epay.protocol.EpayRequest;
import com.jeequan.jeepay.pay.compat.epay.protocol.EpayRequestValidator;
import com.jeequan.jeepay.pay.rqrs.payorder.UnifiedOrderRQ;
import com.jeequan.jeepay.service.impl.MchAppService;
import com.jeequan.jeepay.service.impl.MchInfoService;
import com.jeequan.jeepay.service.impl.PayOrderService;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;

import java.util.Objects;

@Service
public class EpaySubmitService {

    private static final String ALI_PC_CHANNEL_EXTRA = "{\"payDataType\":\"payUrl\"}";
    private static final String WX_NATIVE_CHANNEL_EXTRA = "{\"payDataType\":\"codeImgUrl\"}";

    private final MchAppService mchAppService;
    private final MchInfoService mchInfoService;
    private final PayOrderService payOrderService;
    private final EpayPresentationSnapshotService snapshotService;

    public EpaySubmitService(
            MchAppService mchAppService,
            MchInfoService mchInfoService,
            PayOrderService payOrderService,
            EpayPresentationSnapshotService snapshotService) {
        this.mchAppService = mchAppService;
        this.mchInfoService = mchInfoService;
        this.payOrderService = payOrderService;
        this.snapshotService = snapshotService;
    }

    public EpayPreparedOrder prepare(MultiValueMap<String, String> form) {
        EpayRequestValidator.UnsignedRequest unsigned =
                EpayRequestValidator.validate(form);

        MchApp app = mchAppService.getById(unsigned.pid());
        if (!isAvailable(app)) {
            throw new MerchantUnavailableException();
        }

        MchInfo merchant = mchInfoService.getById(app.getMchNo());
        if (merchant == null || !Byte.valueOf(CS.YES).equals(merchant.getState())) {
            throw new MerchantUnavailableException();
        }

        EpayRequestValidator.VerifiedRequest verified =
                EpayRequestValidator.verifySignature(unsigned, app.getAppSecret());
        EpayRequest request = verified.request();
        PayOrder existing = payOrderService.queryMchOrder(
                app.getMchNo(), null, request.outTradeNo());

        if (existing == null) {
            return prepareNew(app.getMchNo(), verified);
        }
        return prepareReplay(existing, verified);
    }

    private static boolean isAvailable(MchApp app) {
        return app != null
                && Byte.valueOf(CS.YES).equals(app.getState())
                && !isBlank(app.getMchNo())
                && !isBlank(app.getAppSecret());
    }

    private static EpayPreparedOrder.NewOrder prepareNew(
            String mchNo,
            EpayRequestValidator.VerifiedRequest verified) {
        EpayRequest request = verified.request();
        UnifiedOrderRQ unifiedOrder = new UnifiedOrderRQ();
        unifiedOrder.setMchNo(mchNo);
        unifiedOrder.setAppId(request.pid());
        unifiedOrder.setMchOrderNo(request.outTradeNo());
        unifiedOrder.setWayCode(verified.wayCode());
        unifiedOrder.setAmount(verified.amountCents());
        unifiedOrder.setCurrency("cny");
        unifiedOrder.setSubject(request.name());
        unifiedOrder.setBody(request.name());
        unifiedOrder.setNotifyUrl(request.notifyUrl());
        unifiedOrder.setReturnUrl(request.returnUrl());
        unifiedOrder.setChannelExtra(channelExtra(verified.wayCode()));
        unifiedOrder.setExtParam(EpayOrderMetadata.of(request.type()).toJson());
        return new EpayPreparedOrder.NewOrder(verified.wayCode(), unifiedOrder);
    }

    private EpayPreparedOrder.Replay prepareReplay(
            PayOrder existing,
            EpayRequestValidator.VerifiedRequest verified) {
        EpayRequest request = verified.request();
        if (!Objects.equals(existing.getAppId(), request.pid())
                || !Objects.equals(existing.getAmount(), verified.amountCents())
                || !Objects.equals(existing.getWayCode(), verified.wayCode())
                || !Objects.equals(existing.getSubject(), request.name())
                || !Objects.equals(existing.getNotifyUrl(), request.notifyUrl())
                || !Objects.equals(existing.getReturnUrl(), request.returnUrl())) {
            throw new OrderConflictException();
        }

        if (isBlank(existing.getPayOrderId())) {
            throw new InitializationIncompleteException();
        }
        EpayPresentation presentation = snapshotService.find(existing.getPayOrderId())
                .orElseThrow(InitializationIncompleteException::new);
        return new EpayPreparedOrder.Replay(existing, presentation);
    }

    private static String channelExtra(String wayCode) {
        return switch (wayCode) {
            case "ALI_PC" -> ALI_PC_CHANNEL_EXTRA;
            case "WX_NATIVE" -> WX_NATIVE_CHANNEL_EXTRA;
            default -> throw new IllegalArgumentException("Unsupported EPay way code");
        };
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public static final class MerchantUnavailableException extends RuntimeException {

        public MerchantUnavailableException() {
            super("商户配置不可用");
        }
    }

    public static final class OrderConflictException extends RuntimeException {

        public OrderConflictException() {
            super("商户订单号已存在且请求不一致");
        }
    }

    public static final class InitializationIncompleteException extends RuntimeException {

        public InitializationIncompleteException() {
            super("支付初始化未完成");
        }
    }
}
