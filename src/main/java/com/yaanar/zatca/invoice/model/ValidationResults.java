package com.yaanar.zatca.invoice.model;

import lombok.Data;

import java.util.List;

@Data
public class ValidationResults {
    List<InfoMessages> infoMessages;
    List<WarningMessages> warningMessages;
    List<ErrorMessages> errorMessages;
    String status;
}
