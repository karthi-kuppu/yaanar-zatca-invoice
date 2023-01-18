package com.yaanar.zatca.invoice.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ProductionCSIDRequest {
    @JsonProperty("compliance_request_id")
    String complianceRequestId;
}
