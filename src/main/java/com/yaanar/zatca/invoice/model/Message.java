package com.yaanar.zatca.invoice.model;

import lombok.Data;

@Data
public class Message {
    String type;
    String code;
    String category;
    String message;
    String status;
}
