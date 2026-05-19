package com.gcc_0119.finance_tracker.service;

import com.gcc_0119.finance_tracker.dto.CsvImportPreview;
import com.gcc_0119.finance_tracker.dto.TransactionDTO;
import com.gcc_0119.finance_tracker.model.Account;
import com.gcc_0119.finance_tracker.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CsvImportServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private CsvImportService csvImportService;

    private static final String USER_ID = "user1";

    private Account buildAccount(String id, String name) {
        Account account = new Account();
        account.setId(id);
        account.setUserId(USER_ID);
        account.setName(name);
        account.setBalance(new BigDecimal("1000.00"));
        return account;
    }

    private InputStream csvStream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void preview_validCsv_returnsPreview() {
        String csv = "description,amount,fromAccountName,toAccountName,timestamp\n"
                + "午餐,50.00,现金,银行卡,2024-01-15 12:30:00\n"
                + "交通,20.50,现金,银行卡,2024-01-15 18:00:00";

        when(accountRepository.findByUserIdAndName(USER_ID, "现金"))
                .thenReturn(Optional.of(buildAccount("acc-cash", "现金")));
        when(accountRepository.findByUserIdAndName(USER_ID, "银行卡"))
                .thenReturn(Optional.of(buildAccount("acc-bank", "银行卡")));

        CsvImportPreview preview = csvImportService.preview(csvStream(csv), USER_ID);

        assertEquals(2, preview.getTotalRows());
        assertEquals(2, preview.getValidRows());
        assertEquals(0, preview.getInvalidRows());
        assertTrue(preview.getErrors().isEmpty());
        assertEquals(2, preview.getTransactions().size());

        TransactionDTO first = preview.getTransactions().get(0);
        assertEquals("午餐", first.getDescription());
        assertEquals(0, new BigDecimal("50.00").compareTo(first.getAmount()));
        assertEquals("acc-cash", first.getFromAccountId());
        assertEquals("acc-bank", first.getToAccountId());
    }

    @Test
    void preview_invalidAmount_returnsError() {
        String csv = "description,amount,fromAccountName,toAccountName,timestamp\n"
                + "午餐,abc,现金,银行卡,2024-01-15 12:30:00";

        CsvImportPreview preview = csvImportService.preview(csvStream(csv), USER_ID);

        assertEquals(1, preview.getTotalRows());
        assertEquals(0, preview.getValidRows());
        assertEquals(1, preview.getInvalidRows());
        assertEquals(1, preview.getErrors().size());
        assertTrue(preview.getErrors().get(0).contains("金额格式错误"));
    }

    @Test
    void preview_missingAccount_returnsError() {
        String csv = "description,amount,fromAccountName,toAccountName,timestamp\n"
                + "午餐,50.00,不存在账户,银行卡,2024-01-15 12:30:00";

        when(accountRepository.findByUserIdAndName(USER_ID, "不存在账户"))
                .thenReturn(Optional.empty());

        CsvImportPreview preview = csvImportService.preview(csvStream(csv), USER_ID);

        assertEquals(1, preview.getTotalRows());
        assertEquals(0, preview.getValidRows());
        assertEquals(1, preview.getInvalidRows());
        assertEquals(1, preview.getErrors().size());
        assertTrue(preview.getErrors().get(0).contains("来源账户不存在"));
    }

    @Test
    void preview_emptyFile_returnsEmptyPreview() {
        String csv = "description,amount,fromAccountName,toAccountName,timestamp";

        CsvImportPreview preview = csvImportService.preview(csvStream(csv), USER_ID);

        assertEquals(0, preview.getTotalRows());
        assertEquals(0, preview.getValidRows());
        assertEquals(0, preview.getInvalidRows());
        assertTrue(preview.getErrors().isEmpty());
        assertTrue(preview.getTransactions().isEmpty());
    }

    @Test
    void preview_negativeAmount_returnsError() {
        String csv = "description,amount,fromAccountName,toAccountName,timestamp\n"
                + "午餐,-50.00,现金,银行卡,2024-01-15 12:30:00";

        CsvImportPreview preview = csvImportService.preview(csvStream(csv), USER_ID);

        assertEquals(1, preview.getInvalidRows());
        assertTrue(preview.getErrors().get(0).contains("金额必须为正数"));
    }

    @Test
    void preview_invalidTimestamp_returnsError() {
        String csv = "description,amount,fromAccountName,toAccountName,timestamp\n"
                + "午餐,50.00,现金,银行卡,not-a-date";

        CsvImportPreview preview = csvImportService.preview(csvStream(csv), USER_ID);

        assertEquals(1, preview.getInvalidRows());
        assertTrue(preview.getErrors().get(0).contains("时间格式错误"));
    }

    @Test
    void preview_insufficientColumns_returnsError() {
        String csv = "description,amount,fromAccountName\n"
                + "午餐,50.00,现金";

        CsvImportPreview preview = csvImportService.preview(csvStream(csv), USER_ID);

        assertEquals(1, preview.getInvalidRows());
        assertTrue(preview.getErrors().get(0).contains("列数不足"));
    }

    @Test
    void preview_mixedValidAndInvalid_returnsPartialPreview() {
        String csv = "description,amount,fromAccountName,toAccountName,timestamp\n"
                + "午餐,50.00,现金,银行卡,2024-01-15 12:30:00\n"
                + "无效,abc,现金,银行卡,2024-01-15 18:00:00\n"
                + "交通,20.00,现金,银行卡,2024-01-16 09:00:00";

        when(accountRepository.findByUserIdAndName(USER_ID, "现金"))
                .thenReturn(Optional.of(buildAccount("acc-cash", "现金")));
        when(accountRepository.findByUserIdAndName(USER_ID, "银行卡"))
                .thenReturn(Optional.of(buildAccount("acc-bank", "银行卡")));

        CsvImportPreview preview = csvImportService.preview(csvStream(csv), USER_ID);

        assertEquals(3, preview.getTotalRows());
        assertEquals(2, preview.getValidRows());
        assertEquals(1, preview.getInvalidRows());
        assertEquals(2, preview.getTransactions().size());
        assertEquals(1, preview.getErrors().size());
    }
}
