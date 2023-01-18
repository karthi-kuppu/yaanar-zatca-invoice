package com.yaanar.zatca.invoice.model;

import lombok.Data;

@Data
public class WarningMessages {
    String type;
    String code;
    String category;
    String message;
    String status;
}
