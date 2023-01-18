package com.yaanar.zatca.invoice.model;

import lombok.Data;

@Data
public class InvoiceResponse {
    String signedInvoice;
    String invoiceHash;
    ComplianceResponse complianceResponse;
}
