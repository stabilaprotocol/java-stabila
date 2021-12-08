package org.stabila.core.capsule;

import com.google.protobuf.InvalidProtocolBufferException;

import java.util.Objects;

import org.stabila.core.exception.BadItemException;
import org.stabila.protos.contract.BalanceContract;
import org.stabila.protos.contract.BalanceContract.AccountTrace;
import org.stabila.protos.contract.BalanceContract.TransactionBalanceTrace;

public class AccountTraceCapsule implements ProtoCapsule<AccountTrace> {
  private BalanceContract.AccountTrace accountTrace;

  public AccountTraceCapsule() {
    accountTrace = AccountTrace.newBuilder().build();
  }

  public AccountTraceCapsule(long balance) {
    this();
    accountTrace = accountTrace.toBuilder().setBalance(balance).build();
  }

  public AccountTraceCapsule(AccountTrace accountTrace) {
    this.accountTrace = accountTrace;
  }

  public AccountTraceCapsule(byte[] data) throws BadItemException {
    try {
      this.accountTrace = AccountTrace.parseFrom(data);
    } catch (InvalidProtocolBufferException e) {
      throw new BadItemException("AccountTraceCapsule proto data parse exception");
    }
  }

  public Long getBalance() {
    return accountTrace.getBalance();
  }

  @Override
  public byte[] getData() {
    if (Objects.isNull(accountTrace)) {
      return null;
    }

    if (accountTrace.getBalance() == 0) {
      accountTrace = accountTrace.toBuilder().setPlaceholder(1).build();
    }

    return accountTrace.toByteArray();
  }

  @Override
  public AccountTrace getInstance() {
    return accountTrace;
  }
}
