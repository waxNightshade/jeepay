package com.jeequan.jeepay.pay.compat.epay.web;

import com.jeequan.jeepay.pay.compat.epay.model.EpayBrowserStatus;
import com.jeequan.jeepay.pay.compat.epay.service.EpayBrowserStatusService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EpayBrowserStatusController {

    private final EpayBrowserStatusService statusService;

    public EpayBrowserStatusController(EpayBrowserStatusService statusService) {
        this.statusService = statusService;
    }

    @GetMapping("/api/epay/pay-orders/{payOrderId}/status")
    public ResponseEntity<EpayBrowserStatus> status(
            @PathVariable String payOrderId,
            @RequestParam String token) {
        try {
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(statusService.query(payOrderId, token));
        } catch (EpayBrowserStatusService.AccessDeniedException e) {
            return ResponseEntity.notFound()
                    .cacheControl(CacheControl.noStore())
                    .build();
        }
    }
}
