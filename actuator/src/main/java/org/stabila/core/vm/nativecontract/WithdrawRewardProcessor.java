package org.stabila.core.vm.nativecontract;

import static org.stabila.core.actuator.ActuatorConstant.ACCOUNT_EXCEPTION_STR;
import static org.stabila.core.actuator.ActuatorConstant.STORE_NOT_EXIST;

import com.google.common.math.LongMath;
import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;
import org.stabila.common.parameter.CommonParameter;
import org.stabila.common.utils.StringUtil;
import org.stabila.core.capsule.AccountCapsule;
import org.stabila.core.capsule.WitnessCapsule;
import org.stabila.core.exception.ContractExeException;
import org.stabila.core.exception.ContractValidateException;
import org.stabila.core.vm.nativecontract.param.WithdrawRewardParam;
import org.stabila.core.vm.repository.Repository;
import org.stabila.core.vm.utils.VoteRewardUtil;

@Slf4j(topic = "Processor")
public class WithdrawRewardProcessor {

  public void validate(WithdrawRewardParam param, Repository repo) throws ContractValidateException {
    if (repo == null) {
      throw new ContractValidateException(STORE_NOT_EXIST);
    }

    byte[] ownerAddress = param.getOwnerAddress();

    //boolean isGP = CommonParameter.getInstance()
    //    .getGenesisBlock().getWitnesses().stream().anyMatch(witness ->
    //        Arrays.equals(ownerAddress, witness.getAddress()));
    boolean isGP = repo.getWitnessStore().getAllWitnesses().stream().filter(WitnessCapsule::getIsJobs).anyMatch(wc -> Arrays.equals(wc.getAddress().toByteArray(), ownerAddress));
    if (isGP) {
      throw new ContractValidateException(
          ACCOUNT_EXCEPTION_STR + StringUtil.encode58Check(ownerAddress)
              + "] is a guard representative and is not allowed to withdraw Balance");
    }
  }

  public long execute(WithdrawRewardParam param, Repository repo) throws ContractExeException {
    byte[] ownerAddress = param.getOwnerAddress();

    VoteRewardUtil.withdrawReward(ownerAddress, repo);

    AccountCapsule accountCapsule = repo.getAccount(ownerAddress);
    long oldBalance = accountCapsule.getBalance();
    long allowance = accountCapsule.getAllowance();
    long newBalance = 0;

    try {
      newBalance = LongMath.checkedAdd(oldBalance, allowance);
    } catch (ArithmeticException e) {
      logger.debug(e.getMessage(), e);
      throw new ContractExeException(e.getMessage());
    }

    // If no allowance, do nothing and just return zero.
    if (allowance <= 0) {
      return 0;
    }

    accountCapsule.setInstance(accountCapsule.getInstance().toBuilder()
        .setBalance(newBalance)
        .setAllowance(0L)
        .setLatestWithdrawTime(param.getNowInMs())
        .build());

    repo.updateAccount(accountCapsule.createDbKey(), accountCapsule);
    return allowance;
  }
}
