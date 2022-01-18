package org.stabila.core.db;

import static java.lang.Long.max;
import static org.stabila.core.config.Parameter.ChainConstant.BLOCK_PRODUCED_INTERVAL;
import static org.stabila.core.config.Parameter.ChainConstant.STB_PRECISION;

import lombok.extern.slf4j.Slf4j;
import org.stabila.common.parameter.CommonParameter;
import org.stabila.core.capsule.AccountCapsule;
import org.stabila.core.capsule.TransactionCapsule;
import org.stabila.core.config.Parameter.AdaptiveResourceLimitConstants;
import org.stabila.core.exception.AccountResourceInsufficientException;
import org.stabila.core.exception.ContractValidateException;
import org.stabila.core.store.AccountStore;
import org.stabila.core.store.DynamicPropertiesStore;
import org.stabila.protos.Protocol.Account.AccountResource;

@Slf4j(topic = "DB")
public class UcrProcessor extends ResourceProcessor {

  public UcrProcessor(DynamicPropertiesStore dynamicPropertiesStore, AccountStore accountStore) {
    super(dynamicPropertiesStore, accountStore);
  }

  public static long getHeadSlot(DynamicPropertiesStore dynamicPropertiesStore) {
    return (dynamicPropertiesStore.getLatestBlockHeaderTimestamp() -
        Long.parseLong(CommonParameter.getInstance()
            .getGenesisBlock().getTimestamp()))
        / BLOCK_PRODUCED_INTERVAL;
  }

  @Override
  public void updateUsage(AccountCapsule accountCapsule) {
    long now = getHeadSlot();
    updateUsage(accountCapsule, now);
  }

  private void updateUsage(AccountCapsule accountCapsule, long now) {
    AccountResource accountResource = accountCapsule.getAccountResource();

    long oldUcrUsage = accountResource.getUcrUsage();
    long latestConsumeTime = accountResource.getLatestConsumeTimeForUcr();

    accountCapsule.setUcrUsage(increase(oldUcrUsage, 0, latestConsumeTime, now));
  }

  public void updateTotalUcrAverageUsage() {
    long now = getHeadSlot();
    long blockUcrUsage = dynamicPropertiesStore.getBlockUcrUsage();
    long totalUcrAverageUsage = dynamicPropertiesStore
        .getTotalUcrAverageUsage();
    long totalUcrAverageTime = dynamicPropertiesStore.getTotalUcrAverageTime();

    long newPublicUcrAverageUsage = increase(totalUcrAverageUsage, blockUcrUsage,
        totalUcrAverageTime, now, averageWindowSize);

    dynamicPropertiesStore.saveTotalUcrAverageUsage(newPublicUcrAverageUsage);
    dynamicPropertiesStore.saveTotalUcrAverageTime(now);
  }

  public void updateAdaptiveTotalUcrLimit() {
    long totalUcrAverageUsage = dynamicPropertiesStore
        .getTotalUcrAverageUsage();
    long targetTotalUcrLimit = dynamicPropertiesStore.getTotalUcrTargetLimit();
    long totalUcrCurrentLimit = dynamicPropertiesStore
        .getTotalUcrCurrentLimit();

    long result;
    if (totalUcrAverageUsage > targetTotalUcrLimit) {
      result = totalUcrCurrentLimit * AdaptiveResourceLimitConstants.CONTRACT_RATE_NUMERATOR
          / AdaptiveResourceLimitConstants.CONTRACT_RATE_DENOMINATOR;
    } else {
      result = totalUcrCurrentLimit * AdaptiveResourceLimitConstants.EXPAND_RATE_NUMERATOR
          / AdaptiveResourceLimitConstants.EXPAND_RATE_DENOMINATOR;
    }

    dynamicPropertiesStore.saveTotalUcrCurrentLimit(result);
    logger.debug(
        "adjust totalUcrCurrentLimit, old[" + totalUcrCurrentLimit + "], new[" + result
            + "]");
  }

  @Override
  public void consume(TransactionCapsule stb,
      TransactionTrace trace)
      throws ContractValidateException, AccountResourceInsufficientException {
    throw new RuntimeException("Not support");
  }

  public boolean useUcr(AccountCapsule accountCapsule, long ucr, long now) {

    long ucrUsage = accountCapsule.getUcrUsage();
    long latestConsumeTime = accountCapsule.getAccountResource().getLatestConsumeTimeForUcr();
    long ucrLimit = calculateGlobalUcrLimit(accountCapsule);

    long newUcrUsage = increase(ucrUsage, 0, latestConsumeTime, now);

    if (ucr > (ucrLimit - newUcrUsage)
        && dynamicPropertiesStore.getAllowSvmCd() == 0) {
      return false;
    }

    latestConsumeTime = now;
    long latestOperationTime = dynamicPropertiesStore.getLatestBlockHeaderTimestamp();
    newUcrUsage = increase(newUcrUsage, ucr, latestConsumeTime, now);
    accountCapsule.setUcrUsage(newUcrUsage);
    accountCapsule.setLatestOperationTime(latestOperationTime);
    accountCapsule.setLatestConsumeTimeForUcr(latestConsumeTime);

    accountStore.put(accountCapsule.createDbKey(), accountCapsule);

    if (dynamicPropertiesStore.getAllowAdaptiveUcr() == 1) {
      long blockUcrUsage = dynamicPropertiesStore.getBlockUcrUsage() + ucr;
      dynamicPropertiesStore.saveBlockUcrUsage(blockUcrUsage);
    }

    return true;
  }

  public long calculateGlobalUcrLimit(AccountCapsule accountCapsule) {
    long frozeBalance = accountCapsule.getAllCdedBalanceForUcr();
    if (frozeBalance < STB_PRECISION) {
      return 0;
    }

    long ucrWeight = frozeBalance / STB_PRECISION;
    long totalUcrLimit = dynamicPropertiesStore.getTotalUcrCurrentLimit();
    long totalUcrWeight = dynamicPropertiesStore.getTotalUcrWeight();

    assert totalUcrWeight > 0;

    return (long) (ucrWeight * ((double) totalUcrLimit / totalUcrWeight));
  }

  public long getAccountLeftUcrFromCd(AccountCapsule accountCapsule) {
    long now = getHeadSlot();
    long ucrUsage = accountCapsule.getUcrUsage();
    long latestConsumeTime = accountCapsule.getAccountResource().getLatestConsumeTimeForUcr();
    long ucrLimit = calculateGlobalUcrLimit(accountCapsule);

    long newUcrUsage = increase(ucrUsage, 0, latestConsumeTime, now);

    return max(ucrLimit - newUcrUsage, 0); // us
  }

  private long getHeadSlot() {
    return getHeadSlot(dynamicPropertiesStore);
  }


}


