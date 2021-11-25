package org.stabila.consensus.dpos;

import static org.stabila.core.config.Parameter.ChainConstant.EXECUTIVE_STANDBY_LENGTH;

import com.google.protobuf.ByteString;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.stabila.consensus.ConsensusDelegate;
import org.stabila.core.capsule.AccountCapsule;

@Slf4j(topic = "consensus")
@Component
public class IncentiveManager {

  @Autowired
  private ConsensusDelegate consensusDelegate;

  public void reward(List<ByteString> executives) {
    if (consensusDelegate.allowChangeDelegation()) {
      return;
    }
    if (executives.size() > EXECUTIVE_STANDBY_LENGTH) {
      executives = executives.subList(0, EXECUTIVE_STANDBY_LENGTH);
    }
    long voteSum = 0;
    for (ByteString executive : executives) {
      voteSum += consensusDelegate.getExecutive(executive.toByteArray()).getVoteCount();
    }
    if (voteSum <= 0) {
      return;
    }
    long totalPay = consensusDelegate.getExecutiveStandbyAllowance();
    for (ByteString executive : executives) {
      byte[] address = executive.toByteArray();
      long pay = (long) (consensusDelegate.getExecutive(address).getVoteCount() * ((double) totalPay
          / voteSum));
      AccountCapsule accountCapsule = consensusDelegate.getAccount(address);
      accountCapsule.setAllowance(accountCapsule.getAllowance() + pay);
      consensusDelegate.saveAccount(accountCapsule);
    }
  }
}
