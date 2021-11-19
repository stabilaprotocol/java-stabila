package org.stabila.core.vm;

public class VMConstant {

  public static final int CONTRACT_NAME_LENGTH = 32;
  public static final int MIN_TOKEN_ID = 1_000_000;
  // Numbers
  public static final int ONE_HUNDRED = 100;
  public static final int ONE_THOUSAND = 1000;
  public static final long UNIT_PER_UCR = 100; // 1 us = 100 UNIT = 100 * 10^-6 STB
  public static final long UCR_LIMIT_IN_CONSTANT_TX = 3_000_000L; // ref: 1 us = 1 ucr


  private VMConstant() {
  }
}
