package com.yaanar.zatca.invoice.controller;

import com.yaanar.zatca.invoice.model.InvoiceRequest;
import com.yaanar.zatca.invoice.model.InvoiceResponse;
import com.yaanar.zatca.invoice.service.ComplianceInvoiceService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@AllArgsConstructor
public class InvoiceController {

    ComplianceInvoiceService complianceInvoiceService;

    @PostMapping("/v1/signInvoice")
    public InvoiceResponse retrieveSignedInvoice(@RequestBody InvoiceRequest invoiceRequest) throws Exception {
        InvoiceResponse response = complianceInvoiceService.process(invoiceRequest.getOtp(), invoiceRequest.getEncodedInvoice());
        return response;
    }
}
