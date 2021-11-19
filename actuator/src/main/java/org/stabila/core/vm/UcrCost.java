package org.stabila.core.vm;


public class UcrCost {

  private static UcrCost instance = null;
  private final int BALANCE = 6;
  private final int SHA3 = 10;
  private final int SHA3_WORD = 2;
  private final int SLOAD = 16;
  private final int STOP = 0;
  private final int SUICIDE = 0;
  private final int CLEAR_SSTORE = 1666;
  private final int SET_SSTORE = 6666;
  private final int RESET_SSTORE = 1666;
  private final int REFUND_SSTORE = 5000;
  private final int CREATE = 1666;
  private final int CALL = 13;
  private final int STIPEND_CALL = 766;
  private final int VT_CALL = 3000;  // value transfer call
  private final int NEW_ACCT_CALL = 8333;  // new account call
  private final int MEMORY = 1;
  private final int CREATE_DATA = 66;
  private final int LOG_UCR = 125;
  private final int LOG_DATA_UCR = 2;
  private final int LOG_TOPIC_UCR = 125;
  private final int COPY_UCR = 1;
  private final int EXP_UCR = 3;
  private final int EXP_BYTE_UCR = 3;
  private final int EXT_CODE_SIZE = 6;
  private final int EXT_CODE_COPY = 6;
  private final int EXT_CODE_HASH = 133;
  private final int NEW_ACCT_SUICIDE = 0;
  private final int CD = 6666;
  private final int UNCD = 6666;
  private final int CD_EXPIRE_TIME = 16;
  private final int VOTE_WITNESS = 10000;
  private final int WITHDRAW_REWARD = 6666;

  public static UcrCost getInstance() {
    if (instance == null) {
      instance = new UcrCost();
    }

    return instance;
  }

  public int getBalance() {
    return BALANCE;
  }

  public int getSha3() {
    return SHA3;
  }

  public int getSha3Word() {
    return SHA3_WORD;
  }

  public int getSLoad() {
    return SLOAD;
  }

  public int getStop() {
    return STOP;
  }

  public int getSuicide() {
    return SUICIDE;
  }

  public int getClearSStore() {
    return CLEAR_SSTORE;
  }

  public int getSetSStore() {
    return SET_SSTORE;
  }

  public int getResetSStore() {
    return RESET_SSTORE;
  }

  public int getRefundSStore() {
    return REFUND_SSTORE;
  }

  public int getCreate() {
    return CREATE;
  }

  public int getCall() {
    return CALL;
  }

  public int getStipendCall() {
    return STIPEND_CALL;
  }

  public int getVtCall() {
    return VT_CALL;
  }

  public int getNewAcctCall() {
    return NEW_ACCT_CALL;
  }

  public int getNewAcctSuicide() {
    return NEW_ACCT_SUICIDE;
  }

  public int getMemory() {
    return MEMORY;
  }

  public int getCREATE_DATA() {
    return CREATE_DATA;
  }

  public int getLogUcr() {
    return LOG_UCR;
  }

  public int getLogDataUcr() {
    return LOG_DATA_UCR;
  }

  public int getLogTopicUcr() {
    return LOG_TOPIC_UCR;
  }

  public int getCopyUcr() {
    return COPY_UCR;
  }

  public int getExpUcr() {
    return EXP_UCR;
  }

  public int getExpByteUcr() {
    return EXP_BYTE_UCR;
  }

  public int getExtCodeSize() {
    return EXT_CODE_SIZE;
  }

  public int getExtCodeCopy() {
    return EXT_CODE_COPY;
  }

  public int getExtCodeHash() {
    return EXT_CODE_HASH;
  }

  public int getCd() {
    return CD;
  }

  public int getUncd() {
    return UNCD;
  }

  public int getCdExpireTime() {
    return CD_EXPIRE_TIME;
  }

  public int getVoteWitness() {
    return VOTE_WITNESS;
  }

  public int getWithdrawReward() {
    return WITHDRAW_REWARD;
  }
}
