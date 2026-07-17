package com.jeequan.jeepay.pay.compat.epay.web;

import com.jeequan.jeepay.core.constants.CS;
import com.jeequan.jeepay.core.entity.PayOrder;
import com.jeequan.jeepay.core.model.ApiRes;
import com.jeequan.jeepay.pay.compat.epay.model.EpayPreparedOrder;
import com.jeequan.jeepay.pay.compat.epay.model.EpayPresentation;
import com.jeequan.jeepay.pay.compat.epay.protocol.EpayValidationException;
import com.jeequan.jeepay.pay.compat.epay.service.EpayBrowserStatusService;
import com.jeequan.jeepay.pay.compat.epay.service.EpayPresentationSnapshotService;
import com.jeequan.jeepay.pay.compat.epay.service.EpaySubmitService;
import com.jeequan.jeepay.pay.ctrl.payorder.AbstractPayOrderController;
import com.jeequan.jeepay.pay.rqrs.payorder.UnifiedOrderRQ;
import com.jeequan.jeepay.pay.rqrs.payorder.UnifiedOrderRS;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class EpaySubmitController extends AbstractPayOrderController {

    private static final String INVALID_REQUEST_MESSAGE = "支付请求无效";
    private static final String ORDER_CONFLICT_MESSAGE = "商户订单号已存在且请求不一致";
    private static final String INITIALIZATION_INCOMPLETE_MESSAGE = "支付初始化未完成";
    private static final String CREATION_FAILED_MESSAGE = "支付创建失败";

    private final EpaySubmitService submitService;
    private final EpayPresentationSnapshotService snapshotService;
    private final EpayBrowserStatusService browserStatusService;
    private final EpayPresentationResponseFactory responseFactory;

    public EpaySubmitController(
            EpaySubmitService submitService,
            EpayPresentationSnapshotService snapshotService,
            EpayBrowserStatusService browserStatusService,
            EpayPresentationResponseFactory responseFactory) {
        this.submitService = submitService;
        this.snapshotService = snapshotService;
        this.browserStatusService = browserStatusService;
        this.responseFactory = responseFactory;
    }

    @Override
    protected void afterPayOrderPersisted(PayOrder payOrder) {
        snapshotService.initialize(payOrder.getPayOrderId());
    }

    @PostMapping(value = "/submit.php", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public Object submit(@RequestParam MultiValueMap<String, String> form) {
        try {
            EpayPreparedOrder prepared = submitService.prepare(form);
            if (prepared instanceof EpayPreparedOrder.Replay replay) {
                responseFactory.validate(replay.presentation(), replay);
                String statusUrl = statusUrl(
                        replay.presentation(), replay.payOrder().getPayOrderId());
                return responseFactory.create(replay.presentation(), replay, statusUrl);
            }
            if (!(prepared instanceof EpayPreparedOrder.NewOrder newOrder)
                    || newOrder.request() == null) {
                return creationFailed();
            }

            newOrder.request().setClientIp(getClientIp());
            UnifiedOrderRQ bizRequest = newOrder.request().buildBizRQ();
            ApiRes<?> apiResult = unifiedOrder(newOrder.wayCode(), bizRequest);
            if (apiResult == null || !(apiResult.getData() instanceof UnifiedOrderRS result)) {
                return creationFailed();
            }
            if (isBlank(result.getPayOrderId())) {
                return creationFailed();
            }

            EpayPresentation presentation = new EpayPresentation(
                    result.buildPayDataType(), result.buildPayData());
            responseFactory.validate(presentation, newOrder);
            snapshotService.save(result.getPayOrderId(), presentation);
            String statusUrl = statusUrl(presentation, result.getPayOrderId());
            return responseFactory.create(presentation, newOrder, statusUrl);
        } catch (EpayValidationException | EpaySubmitService.MerchantUnavailableException e) {
            return responseFactory.failure(HttpStatus.BAD_REQUEST, INVALID_REQUEST_MESSAGE);
        } catch (EpaySubmitService.OrderConflictException e) {
            return responseFactory.failure(HttpStatus.CONFLICT, ORDER_CONFLICT_MESSAGE);
        } catch (EpaySubmitService.InitializationIncompleteException e) {
            return responseFactory.failure(HttpStatus.CONFLICT, INITIALIZATION_INCOMPLETE_MESSAGE);
        } catch (RuntimeException e) {
            return creationFailed();
        }
    }

    private Object creationFailed() {
        return responseFactory.failure(HttpStatus.BAD_GATEWAY, CREATION_FAILED_MESSAGE);
    }

    private String statusUrl(EpayPresentation presentation, String payOrderId) {
        if (CS.PAY_DATA_TYPE.CODE_IMG_URL.equals(presentation.payDataType())) {
            return browserStatusService.createStatusUrl(payOrderId);
        }
        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
