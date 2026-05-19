package com.gcc_0119.finance_tracker.dto;

import lombok.Data;
import java.util.List;

@Data
public class CsvImportPreview {
    private List<TransactionDTO> transactions;
    private int totalRows;
    private int validRows;
    private int invalidRows;
    private List<String> errors;
}
