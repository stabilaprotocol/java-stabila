package org.stabila.core.net.message;

import java.util.List;
import org.stabila.core.capsule.TransactionCapsule;
import org.stabila.protos.Protocol;
import org.stabila.protos.Protocol.Transaction;

public class TransactionsMessage extends StabilaMessage {

  private Protocol.Transactions transactions;

  public TransactionsMessage(List<Transaction> stbs) {
    Protocol.Transactions.Builder builder = Protocol.Transactions.newBuilder();
    stbs.forEach(stb -> builder.addTransactions(stb));
    this.transactions = builder.build();
    this.type = MessageTypes.STBS.asByte();
    this.data = this.transactions.toByteArray();
  }

  public TransactionsMessage(byte[] data) throws Exception {
    super(data);
    this.type = MessageTypes.STBS.asByte();
    this.transactions = Protocol.Transactions.parseFrom(getCodedInputStream(data));
    if (isFilter()) {
      compareBytes(data, transactions.toByteArray());
      TransactionCapsule.validContractProto(transactions.getTransactionsList());
    }
  }

  public Protocol.Transactions getTransactions() {
    return transactions;
  }

  @Override
  public String toString() {
    return new StringBuilder().append(super.toString()).append("stb size: ")
        .append(this.transactions.getTransactionsList().size()).toString();
  }

  @Override
  public Class<?> getAnswerMessage() {
    return null;
  }

}
