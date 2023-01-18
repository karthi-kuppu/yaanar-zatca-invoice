package com.yaanar.zatca.invoice.model;

import lombok.Data;

@Data
public class CSRResponse {
    String requestID;
    String dispositionMessage;
    String binarySecurityToken;
    String secret;
    String errors;
}
