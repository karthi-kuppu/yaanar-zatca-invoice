package com.yaanar.zatca.invoice.model;

import lombok.Data;

@Data
public class ProductionReportResponse {
    ValidationResults validationResults;
    String reportingStatus;
}
