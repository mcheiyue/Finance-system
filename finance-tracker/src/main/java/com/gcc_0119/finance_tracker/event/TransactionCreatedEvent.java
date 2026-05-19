package com.gcc_0119.finance_tracker.event;

import com.gcc_0119.finance_tracker.model.Account;
import com.gcc_0119.finance_tracker.model.Transaction;

public class TransactionCreatedEvent {

    private final Transaction transaction;
    private final Account fromAccount;
    private final Account toAccount;

    public TransactionCreatedEvent(Transaction transaction, Account fromAccount, Account toAccount) {
        this.transaction = transaction;
        this.fromAccount = fromAccount;
        this.toAccount = toAccount;
    }

    public Transaction getTransaction() { return transaction; }
    public Account getFromAccount() { return fromAccount; }
    public Account getToAccount() { return toAccount; }
}
