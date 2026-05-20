package com.gcc_0119.finance_tracker.service;

import com.gcc_0119.finance_tracker.dto.AnomalyResult;
import com.gcc_0119.finance_tracker.dto.PaginatedResponse;
import com.gcc_0119.finance_tracker.dto.TransactionDTO;
import com.gcc_0119.finance_tracker.exception.BusinessException;
import com.gcc_0119.finance_tracker.model.Account;
import com.gcc_0119.finance_tracker.model.AccountType;
import com.gcc_0119.finance_tracker.model.Transaction;
import com.gcc_0119.finance_tracker.repository.AccountRepository;
import com.gcc_0119.finance_tracker.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private MongoTemplate mongoTemplate;
    @Mock
    private AnomalyDetectionService anomalyDetectionService;
    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private TransactionService transactionService;

    private static final String USER_ID = "user1";
    private static final String FROM_ACCOUNT_ID = "acc-from";
    private static final String TO_ACCOUNT_ID = "acc-to";

    private TransactionDTO buildRequest(BigDecimal amount) {
        TransactionDTO dto = new TransactionDTO();
        dto.setFromAccountId(FROM_ACCOUNT_ID);
        dto.setToAccountId(TO_ACCOUNT_ID);
        dto.setAmount(amount);
        dto.setDescription("测试交易");
        return dto;
    }

    private Account buildAccount(String id, String userId, BigDecimal balance) {
        Account account = new Account();
        account.setId(id);
        account.setUserId(userId);
        account.setBalance(balance);
        account.setType(AccountType.ASSET);
        return account;
    }

    private Account buildAccount(String id, String userId, BigDecimal balance, AccountType type) {
        Account account = new Account();
        account.setId(id);
        account.setUserId(userId);
        account.setBalance(balance);
        account.setType(type);
        return account;
    }

    @Test
    void createTransaction_success_debitsFromAndCreditsTo() {
        BigDecimal amount = new BigDecimal("100.00");
        Account debitedAccount = buildAccount(FROM_ACCOUNT_ID, USER_ID, new BigDecimal("900.00"));
        Account creditedAccount = buildAccount(TO_ACCOUNT_ID, USER_ID, new BigDecimal("1100.00"));

        when(accountRepository.findByIdAndUserId(FROM_ACCOUNT_ID, USER_ID))
                .thenReturn(Optional.of(buildAccount(FROM_ACCOUNT_ID, USER_ID, new BigDecimal("1000.00"))));
        when(accountRepository.findByIdAndUserId(TO_ACCOUNT_ID, USER_ID))
                .thenReturn(Optional.of(buildAccount(TO_ACCOUNT_ID, USER_ID, new BigDecimal("1000.00"))));
        when(anomalyDetectionService.detectAnomaly(USER_ID, FROM_ACCOUNT_ID, amount))
                .thenReturn(AnomalyResult.normal());
        when(mongoTemplate.findAndModify(
                any(Query.class), any(UpdateDefinition.class),
                Mockito.<FindAndModifyOptions>any(), eq(Account.class)))
                .thenReturn(debitedAccount)
                .thenReturn(creditedAccount);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> {
            Transaction t = inv.getArgument(0);
            t.setId("txn-1");
            t.setCreatedAt(LocalDateTime.now());
            return t;
        });

        TransactionDTO result = transactionService.createTransaction(USER_ID, buildRequest(amount));

        assertEquals("txn-1", result.getId());
        assertEquals(FROM_ACCOUNT_ID, result.getFromAccountId());
        assertEquals(TO_ACCOUNT_ID, result.getToAccountId());
        assertEquals(0, amount.compareTo(result.getAmount()));
        assertFalse(result.isReversed());

        verify(transactionRepository).save(any(Transaction.class));
    }

    @Test
    void createTransaction_insufficientBalance_throwsBusinessException() {
        BigDecimal amount = new BigDecimal("10000.00");

        when(accountRepository.findByIdAndUserId(FROM_ACCOUNT_ID, USER_ID))
                .thenReturn(Optional.of(buildAccount(FROM_ACCOUNT_ID, USER_ID, new BigDecimal("1000.00"))));
        when(accountRepository.findByIdAndUserId(TO_ACCOUNT_ID, USER_ID))
                .thenReturn(Optional.of(buildAccount(TO_ACCOUNT_ID, USER_ID, new BigDecimal("1000.00"))));
        when(anomalyDetectionService.detectAnomaly(USER_ID, FROM_ACCOUNT_ID, amount))
                .thenReturn(AnomalyResult.normal());
        when(mongoTemplate.findAndModify(
                any(Query.class), any(UpdateDefinition.class),
                Mockito.<FindAndModifyOptions>any(), eq(Account.class)))
                .thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> transactionService.createTransaction(USER_ID, buildRequest(amount)));

        assertTrue(ex.getMessage().contains("系统繁忙"));
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void createTransaction_fromAccountNotOwned_throws404() {
        when(accountRepository.findByIdAndUserId(FROM_ACCOUNT_ID, USER_ID))
                .thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> transactionService.createTransaction(USER_ID, buildRequest(new BigDecimal("100.00"))));

        assertEquals(404, ex.getCode());
        assertTrue(ex.getMessage().contains("转出账户"));
        verifyNoInteractions(mongoTemplate);
    }

    @Test
    void createTransaction_toAccountNotOwned_throws404() {
        when(accountRepository.findByIdAndUserId(FROM_ACCOUNT_ID, USER_ID))
                .thenReturn(Optional.of(buildAccount(FROM_ACCOUNT_ID, USER_ID, BigDecimal.TEN)));
        when(accountRepository.findByIdAndUserId(TO_ACCOUNT_ID, USER_ID))
                .thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> transactionService.createTransaction(USER_ID, buildRequest(new BigDecimal("100.00"))));

        assertEquals(404, ex.getCode());
        assertTrue(ex.getMessage().contains("转入账户"));
        verifyNoInteractions(mongoTemplate);
    }

    @Test
    void createTransaction_negativeAmount_throwsBusinessException() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> transactionService.createTransaction(USER_ID, buildRequest(new BigDecimal("-50.00"))));

        assertTrue(ex.getMessage().contains("正数"));
    }

    @Test
    void createTransaction_zeroAmount_throwsBusinessException() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> transactionService.createTransaction(USER_ID, buildRequest(BigDecimal.ZERO)));

        assertTrue(ex.getMessage().contains("正数"));
    }

    @Test
    void createTransaction_sameAccount_throwsBusinessException() {
        TransactionDTO request = new TransactionDTO();
        request.setFromAccountId("same-acc");
        request.setToAccountId("same-acc");
        request.setAmount(new BigDecimal("100.00"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> transactionService.createTransaction(USER_ID, request));

        assertTrue(ex.getMessage().contains("不能相同"));
    }

    @Test
    void createTransaction_anomalousAmount_includesWarningsInResponse() {
        BigDecimal amount = new BigDecimal("5000.00");
        Account debitedAccount = buildAccount(FROM_ACCOUNT_ID, USER_ID, new BigDecimal("5000.00"));
        Account creditedAccount = buildAccount(TO_ACCOUNT_ID, USER_ID, new BigDecimal("15000.00"));

        when(accountRepository.findByIdAndUserId(FROM_ACCOUNT_ID, USER_ID))
                .thenReturn(Optional.of(buildAccount(FROM_ACCOUNT_ID, USER_ID, new BigDecimal("10000.00"))));
        when(accountRepository.findByIdAndUserId(TO_ACCOUNT_ID, USER_ID))
                .thenReturn(Optional.of(buildAccount(TO_ACCOUNT_ID, USER_ID, new BigDecimal("10000.00"))));

        List<String> warnings = List.of("交易金额 5000.00 超过该账户近 90 天平均金额 100.00 的 3 倍");
        AnomalyResult anomalyResult = AnomalyResult.anomalous(warnings, new BigDecimal("100.00"), amount);
        when(anomalyDetectionService.detectAnomaly(USER_ID, FROM_ACCOUNT_ID, amount))
                .thenReturn(anomalyResult);

        when(mongoTemplate.findAndModify(
                any(Query.class), any(UpdateDefinition.class),
                Mockito.<FindAndModifyOptions>any(), eq(Account.class)))
                .thenReturn(debitedAccount)
                .thenReturn(creditedAccount);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> {
            Transaction t = inv.getArgument(0);
            t.setId("txn-anomaly");
            t.setCreatedAt(LocalDateTime.now());
            return t;
        });

        TransactionDTO result = transactionService.createTransaction(USER_ID, buildRequest(amount));

        assertNotNull(result.getAnomalyWarnings());
        assertEquals(1, result.getAnomalyWarnings().size());
        assertTrue(result.getAnomalyWarnings().get(0).contains("超过该账户近 90 天平均金额"));
    }

    @Test
    void reverseTransaction_success_restoresBalancesAndCreatesReversal() {
        Transaction original = new Transaction();
        original.setId("txn-orig");
        original.setUserId(USER_ID);
        original.setFromAccountId(FROM_ACCOUNT_ID);
        original.setToAccountId(TO_ACCOUNT_ID);
        original.setAmount(new BigDecimal("200.00"));
        original.setDescription("原始交易");
        original.setReversed(false);
        original.setTimestamp(LocalDateTime.now().minusHours(1));

        Account refunded = buildAccount(FROM_ACCOUNT_ID, USER_ID, new BigDecimal("1200.00"));
        Account debited = buildAccount(TO_ACCOUNT_ID, USER_ID, new BigDecimal("800.00"));

        Transaction markedOriginal = new Transaction();
        markedOriginal.setId("txn-orig");
        markedOriginal.setReversed(true);
        markedOriginal.setFromAccountId(FROM_ACCOUNT_ID);
        markedOriginal.setToAccountId(TO_ACCOUNT_ID);
        markedOriginal.setAmount(new BigDecimal("200.00"));
        when(mongoTemplate.findAndModify(
                any(Query.class), any(UpdateDefinition.class),
                Mockito.<FindAndModifyOptions>any(), eq(Transaction.class)))
                .thenReturn(markedOriginal);
        when(accountRepository.findByIdAndUserId(FROM_ACCOUNT_ID, USER_ID))
                .thenReturn(Optional.of(buildAccount(FROM_ACCOUNT_ID, USER_ID, new BigDecimal("1000.00"))));
        when(accountRepository.findByIdAndUserId(TO_ACCOUNT_ID, USER_ID))
                .thenReturn(Optional.of(buildAccount(TO_ACCOUNT_ID, USER_ID, new BigDecimal("1000.00"))));
        when(mongoTemplate.findAndModify(
                any(Query.class), any(UpdateDefinition.class),
                Mockito.<FindAndModifyOptions>any(), eq(Account.class)))
                .thenReturn(refunded)
                .thenReturn(debited);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> {
            Transaction t = inv.getArgument(0);
            if (t.getId() == null) {
                t.setId("txn-reversal");
                t.setCreatedAt(LocalDateTime.now());
            }
            return t;
        });

        TransactionDTO result = transactionService.reverseTransaction(USER_ID, "txn-orig");

        assertEquals("txn-reversal", result.getId());
        assertEquals(TO_ACCOUNT_ID, result.getFromAccountId());
        assertEquals(FROM_ACCOUNT_ID, result.getToAccountId());
        assertEquals(0, new BigDecimal("200.00").compareTo(result.getAmount()));
        assertEquals("txn-orig", result.getReversalOfId());

        verify(transactionRepository).save(any(Transaction.class));
    }

    @Test
    void reverseTransaction_alreadyReversed_throwsBusinessException() {
        Transaction original = new Transaction();
        original.setId("txn-orig");
        original.setUserId(USER_ID);
        original.setReversed(true);
        original.setAmount(new BigDecimal("100.00"));

        when(mongoTemplate.findAndModify(
                any(Query.class), any(UpdateDefinition.class),
                Mockito.<FindAndModifyOptions>any(), eq(Transaction.class)))
                .thenReturn(null);
        when(transactionRepository.findById("txn-orig")).thenReturn(Optional.of(original));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> transactionService.reverseTransaction(USER_ID, "txn-orig"));

        assertTrue(ex.getMessage().contains("已被冲正"));
    }

    @Test
    void reverseTransaction_notFound_throws404() {
        when(mongoTemplate.findAndModify(
                any(Query.class), any(UpdateDefinition.class),
                Mockito.<FindAndModifyOptions>any(), eq(Transaction.class)))
                .thenReturn(null);
        when(transactionRepository.findById("nonexistent")).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> transactionService.reverseTransaction(USER_ID, "nonexistent"));

        assertEquals(404, ex.getCode());
        assertTrue(ex.getMessage().contains("不存在"));
    }

    @Test
    void reverseTransaction_wrongUser_throws404() {
        Transaction original = new Transaction();
        original.setId("txn-orig");
        original.setUserId("other-user");
        original.setReversed(false);

        when(mongoTemplate.findAndModify(
                any(Query.class), any(UpdateDefinition.class),
                Mockito.<FindAndModifyOptions>any(), eq(Transaction.class)))
                .thenReturn(null);
        when(transactionRepository.findById("txn-orig")).thenReturn(Optional.of(original));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> transactionService.reverseTransaction(USER_ID, "txn-orig"));

        assertEquals(404, ex.getCode());
    }

    @Test
    void reverseTransaction_insufficientBalanceForDebitBack_throwsBusinessException() {
        Transaction original = new Transaction();
        original.setId("txn-orig");
        original.setUserId(USER_ID);
        original.setFromAccountId(FROM_ACCOUNT_ID);
        original.setToAccountId(TO_ACCOUNT_ID);
        original.setAmount(new BigDecimal("500.00"));
        original.setReversed(false);
        original.setTimestamp(LocalDateTime.now());

        Account refunded = buildAccount(FROM_ACCOUNT_ID, USER_ID, new BigDecimal("1500.00"));

        Transaction markedOriginal = new Transaction();
        markedOriginal.setId("txn-orig");
        markedOriginal.setReversed(true);
        markedOriginal.setFromAccountId(FROM_ACCOUNT_ID);
        markedOriginal.setToAccountId(TO_ACCOUNT_ID);
        markedOriginal.setAmount(new BigDecimal("500.00"));
        when(mongoTemplate.findAndModify(
                any(Query.class), any(UpdateDefinition.class),
                Mockito.<FindAndModifyOptions>any(), eq(Transaction.class)))
                .thenReturn(markedOriginal);
        when(accountRepository.findByIdAndUserId(FROM_ACCOUNT_ID, USER_ID))
                .thenReturn(Optional.of(buildAccount(FROM_ACCOUNT_ID, USER_ID, new BigDecimal("1000.00"))));
        when(accountRepository.findByIdAndUserId(TO_ACCOUNT_ID, USER_ID))
                .thenReturn(Optional.of(buildAccount(TO_ACCOUNT_ID, USER_ID, new BigDecimal("100.00"))));
        when(mongoTemplate.findAndModify(
                any(Query.class), any(UpdateDefinition.class),
                Mockito.<FindAndModifyOptions>any(), eq(Account.class)))
                .thenReturn(refunded)
                .thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> transactionService.reverseTransaction(USER_ID, "txn-orig"));

        assertTrue(ex.getMessage().contains("系统繁忙"));
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void getTransactionsPaginated_returnsCorrectPaginationInfo() {
        Transaction txn1 = new Transaction();
        txn1.setId("txn-1");
        txn1.setUserId(USER_ID);
        txn1.setFromAccountId(FROM_ACCOUNT_ID);
        txn1.setToAccountId(TO_ACCOUNT_ID);
        txn1.setAmount(new BigDecimal("100.00"));
        txn1.setTimestamp(LocalDateTime.now().minusDays(1));

        Transaction txn2 = new Transaction();
        txn2.setId("txn-2");
        txn2.setUserId(USER_ID);
        txn2.setFromAccountId(FROM_ACCOUNT_ID);
        txn2.setToAccountId(TO_ACCOUNT_ID);
        txn2.setAmount(new BigDecimal("200.00"));
        txn2.setTimestamp(LocalDateTime.now());

        when(mongoTemplate.count(any(Query.class), eq(Transaction.class))).thenReturn(2L);
        when(mongoTemplate.find(any(Query.class), eq(Transaction.class)))
                .thenReturn(Arrays.asList(txn2, txn1));

        PaginatedResponse<TransactionDTO> result = transactionService.getTransactionsPaginated(
                USER_ID, 0, 20, null, null, null, null, null);

        assertEquals(2, result.getContent().size());
        assertEquals(0, result.getPage());
        assertEquals(20, result.getSize());
        assertEquals(2L, result.getTotalElements());
        assertEquals(1, result.getTotalPages());
        assertEquals("txn-2", result.getContent().get(0).getId());
        assertEquals("txn-1", result.getContent().get(1).getId());
    }

    @Test
    void getTransactionsPaginated_withFromAccountIdFilter() {
        Transaction txn = new Transaction();
        txn.setId("txn-1");
        txn.setUserId(USER_ID);
        txn.setFromAccountId(FROM_ACCOUNT_ID);
        txn.setToAccountId(TO_ACCOUNT_ID);
        txn.setAmount(new BigDecimal("100.00"));
        txn.setTimestamp(LocalDateTime.now());

        when(mongoTemplate.count(any(Query.class), eq(Transaction.class))).thenReturn(1L);
        when(mongoTemplate.find(any(Query.class), eq(Transaction.class)))
                .thenReturn(Collections.singletonList(txn));

        PaginatedResponse<TransactionDTO> result = transactionService.getTransactionsPaginated(
                USER_ID, 0, 20, FROM_ACCOUNT_ID, null, null, null, null);

        assertEquals(1, result.getContent().size());
        assertEquals(FROM_ACCOUNT_ID, result.getContent().get(0).getFromAccountId());
    }

    @Test
    void getTransactionsPaginated_withToAccountIdFilter() {
        Transaction txn = new Transaction();
        txn.setId("txn-1");
        txn.setUserId(USER_ID);
        txn.setFromAccountId(FROM_ACCOUNT_ID);
        txn.setToAccountId(TO_ACCOUNT_ID);
        txn.setAmount(new BigDecimal("100.00"));
        txn.setTimestamp(LocalDateTime.now());

        when(mongoTemplate.count(any(Query.class), eq(Transaction.class))).thenReturn(1L);
        when(mongoTemplate.find(any(Query.class), eq(Transaction.class)))
                .thenReturn(Collections.singletonList(txn));

        PaginatedResponse<TransactionDTO> result = transactionService.getTransactionsPaginated(
                USER_ID, 0, 20, null, TO_ACCOUNT_ID, null, null, null);

        assertEquals(1, result.getContent().size());
        assertEquals(TO_ACCOUNT_ID, result.getContent().get(0).getToAccountId());
    }

    @Test
    void getTransactionsPaginated_emptyResult() {
        when(mongoTemplate.count(any(Query.class), eq(Transaction.class))).thenReturn(0L);
        when(mongoTemplate.find(any(Query.class), eq(Transaction.class)))
                .thenReturn(Collections.emptyList());

        PaginatedResponse<TransactionDTO> result = transactionService.getTransactionsPaginated(
                USER_ID, 0, 20, null, null, null, null, null);

        assertTrue(result.getContent().isEmpty());
        assertEquals(0L, result.getTotalElements());
        assertEquals(0, result.getTotalPages());
    }

    @Test
    void getTransactionsPaginated_secondPage() {
        Transaction txn = new Transaction();
        txn.setId("txn-3");
        txn.setUserId(USER_ID);
        txn.setFromAccountId(FROM_ACCOUNT_ID);
        txn.setToAccountId(TO_ACCOUNT_ID);
        txn.setAmount(new BigDecimal("300.00"));
        txn.setTimestamp(LocalDateTime.now().minusDays(2));

        when(mongoTemplate.count(any(Query.class), eq(Transaction.class))).thenReturn(3L);
        when(mongoTemplate.find(any(Query.class), eq(Transaction.class)))
                .thenReturn(Collections.singletonList(txn));

        PaginatedResponse<TransactionDTO> result = transactionService.getTransactionsPaginated(
                USER_ID, 1, 2, null, null, null, null, null);

        assertEquals(1, result.getContent().size());
        assertEquals(1, result.getPage());
        assertEquals(2, result.getSize());
        assertEquals(3L, result.getTotalElements());
        assertEquals(2, result.getTotalPages());
    }

    @Test
    void createTransaction_creditAccountFails_throwsBusinessException() {
        BigDecimal amount = new BigDecimal("100.00");
        Account debitedAccount = buildAccount(FROM_ACCOUNT_ID, USER_ID, new BigDecimal("900.00"));

        when(accountRepository.findByIdAndUserId(FROM_ACCOUNT_ID, USER_ID))
                .thenReturn(Optional.of(buildAccount(FROM_ACCOUNT_ID, USER_ID, new BigDecimal("1000.00"))));
        when(accountRepository.findByIdAndUserId(TO_ACCOUNT_ID, USER_ID))
                .thenReturn(Optional.of(buildAccount(TO_ACCOUNT_ID, USER_ID, new BigDecimal("1000.00"))));
        when(anomalyDetectionService.detectAnomaly(USER_ID, FROM_ACCOUNT_ID, amount))
                .thenReturn(AnomalyResult.normal());
        when(mongoTemplate.findAndModify(
                any(Query.class), any(UpdateDefinition.class),
                Mockito.<FindAndModifyOptions>any(), eq(Account.class)))
                .thenReturn(debitedAccount)
                .thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> transactionService.createTransaction(USER_ID, buildRequest(amount)));

        assertTrue(ex.getMessage().contains("系统繁忙"));
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void getTransactionsPaginated_withKeyword_usesEscapedRegex() {
        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);

        when(mongoTemplate.count(queryCaptor.capture(), eq(Transaction.class))).thenReturn(0L);
        when(mongoTemplate.find(queryCaptor.capture(), eq(Transaction.class)))
                .thenReturn(Collections.emptyList());

        String dangerousKeyword = "test.*[a-z](group)";
        transactionService.getTransactionsPaginated(
                USER_ID, 0, 20, null, null, null, null, dangerousKeyword);

        verify(mongoTemplate).count(any(Query.class), eq(Transaction.class));
        verify(mongoTemplate).find(any(Query.class), eq(Transaction.class));
    }
}
