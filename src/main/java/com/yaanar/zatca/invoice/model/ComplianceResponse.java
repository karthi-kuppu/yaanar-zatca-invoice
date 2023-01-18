package com.yaanar.zatca.invoice.model;

import lombok.Data;

@Data
public class ComplianceResponse {
    ValidationResults validationResults;
    String reportingStatus;
    String clearanceStatus;
    String qrSellertStatus;
    String qrBuyertStatus;
}
