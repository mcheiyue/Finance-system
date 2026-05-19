package com.gcc_0119.finance_tracker.service;

import com.gcc_0119.finance_tracker.dto.TransactionDTO;
import com.gcc_0119.finance_tracker.exception.BusinessException;
import com.gcc_0119.finance_tracker.model.Account;
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
        when(mongoTemplate.findAndModify(
                any(Query.class), any(UpdateDefinition.class),
                Mockito.<FindAndModifyOptions>any(), eq(Account.class)))
                .thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> transactionService.createTransaction(USER_ID, buildRequest(amount)));

        assertTrue(ex.getMessage().contains("余额不足"));
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

        when(transactionRepository.findById("txn-orig")).thenReturn(Optional.of(original));
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

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, times(2)).save(captor.capture());
        Transaction markedOriginal = captor.getAllValues().get(0);
        assertTrue(markedOriginal.isReversed());
        assertEquals("txn-orig", markedOriginal.getId());
    }

    @Test
    void reverseTransaction_alreadyReversed_throwsBusinessException() {
        Transaction original = new Transaction();
        original.setId("txn-orig");
        original.setUserId(USER_ID);
        original.setReversed(true);
        original.setAmount(new BigDecimal("100.00"));

        when(transactionRepository.findById("txn-orig")).thenReturn(Optional.of(original));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> transactionService.reverseTransaction(USER_ID, "txn-orig"));

        assertTrue(ex.getMessage().contains("已被冲正"));
        verifyNoInteractions(mongoTemplate);
    }

    @Test
    void reverseTransaction_notFound_throws404() {
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

        when(transactionRepository.findById("txn-orig")).thenReturn(Optional.of(original));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> transactionService.reverseTransaction(USER_ID, "txn-orig"));

        assertEquals(404, ex.getCode());
    }

    @Test
    void reverseTransaction_insufficientBalanceForDebitBack_throwsAndCompensates() {
        Transaction original = new Transaction();
        original.setId("txn-orig");
        original.setUserId(USER_ID);
        original.setFromAccountId(FROM_ACCOUNT_ID);
        original.setToAccountId(TO_ACCOUNT_ID);
        original.setAmount(new BigDecimal("500.00"));
        original.setReversed(false);
        original.setTimestamp(LocalDateTime.now());

        Account refunded = buildAccount(FROM_ACCOUNT_ID, USER_ID, new BigDecimal("1500.00"));

        when(transactionRepository.findById("txn-orig")).thenReturn(Optional.of(original));
        when(mongoTemplate.findAndModify(
                any(Query.class), any(UpdateDefinition.class),
                Mockito.<FindAndModifyOptions>any(), eq(Account.class)))
                .thenReturn(refunded)
                .thenReturn(null)
                .thenReturn(buildAccount(FROM_ACCOUNT_ID, USER_ID, new BigDecimal("1000.00")));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> transactionService.reverseTransaction(USER_ID, "txn-orig"));

        assertTrue(ex.getMessage().contains("余额不足"));
        verify(transactionRepository, never()).save(any());
    }
}
