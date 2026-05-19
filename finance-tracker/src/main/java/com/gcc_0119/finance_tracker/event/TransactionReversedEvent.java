package com.gcc_0119.finance_tracker.event;

import com.gcc_0119.finance_tracker.model.Account;
import com.gcc_0119.finance_tracker.model.Transaction;

public class TransactionReversedEvent {

    private final Transaction originalTransaction;
    private final Account fromAccount;
    private final Account toAccount;

    public TransactionReversedEvent(Transaction originalTransaction, Account fromAccount, Account toAccount) {
        this.originalTransaction = originalTransaction;
        this.fromAccount = fromAccount;
        this.toAccount = toAccount;
    }

    public Transaction getOriginalTransaction() { return originalTransaction; }
    public Account getFromAccount() { return fromAccount; }
    public Account getToAccount() { return toAccount; }
}
