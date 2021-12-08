package org.stabila.consensus;

import com.google.protobuf.ByteString;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.stabila.core.capsule.AccountCapsule;
import org.stabila.core.capsule.ExecutiveCapsule;
import org.stabila.core.store.AccountStore;
import org.stabila.core.store.DelegationStore;
import org.stabila.core.store.DynamicPropertiesStore;
import org.stabila.core.store.VotesStore;
import org.stabila.core.store.ExecutiveScheduleStore;
import org.stabila.core.store.ExecutiveStore;

@Slf4j(topic = "consensus")
@Component
public class ConsensusDelegate {

  @Autowired
  private DynamicPropertiesStore dynamicPropertiesStore;

  @Autowired
  private DelegationStore delegationStore;

  @Autowired
  private AccountStore accountStore;

  @Autowired
  private ExecutiveStore executiveStore;

  @Autowired
  private ExecutiveScheduleStore executiveScheduleStore;

  @Autowired
  private VotesStore votesStore;

  public DynamicPropertiesStore getDynamicPropertiesStore() {
    return dynamicPropertiesStore;
  }

  public DelegationStore getDelegationStore() {
    return delegationStore;
  }

  public VotesStore getVotesStore() {
    return votesStore;
  }

  public int calculateFilledSlotsCount() {
    return dynamicPropertiesStore.calculateFilledSlotsCount();
  }

  public void saveRemoveThePowerOfTheGr(long rate) {
    dynamicPropertiesStore.saveRemoveThePowerOfTheGr(rate);
  }

  public long getRemoveThePowerOfTheGr() {
    return dynamicPropertiesStore.getRemoveThePowerOfTheGr();
  }

  public long getExecutiveStandbyAllowance() {
    return dynamicPropertiesStore.getExecutiveStandbyAllowance();
  }

  public long getLatestBlockHeaderTimestamp() {
    return dynamicPropertiesStore.getLatestBlockHeaderTimestamp();
  }

  public long getLatestBlockHeaderNumber() {
    return dynamicPropertiesStore.getLatestBlockHeaderNumber();
  }

  public boolean lastHeadBlockIsMaintenance() {
    return dynamicPropertiesStore.getStateFlag() == 1;
  }

  public long getMaintenanceSkipSlots() {
    return dynamicPropertiesStore.getMaintenanceSkipSlots();
  }

  public void saveActiveExecutives(List<ByteString> addresses) {
    executiveScheduleStore.saveActiveExecutives(addresses);
  }

  public List<ByteString> getActiveExecutives() {
    return executiveScheduleStore.getActiveExecutives();
  }

  public AccountCapsule getAccount(byte[] address) {
    return accountStore.get(address);
  }

  public void saveAccount(AccountCapsule accountCapsule) {
    accountStore.put(accountCapsule.createDbKey(), accountCapsule);
  }

  public ExecutiveCapsule getExecutive(byte[] address) {
    return executiveStore.get(address);
  }

  public void saveExecutive(ExecutiveCapsule executiveCapsule) {
    executiveStore.put(executiveCapsule.createDbKey(), executiveCapsule);
  }

  public List<ExecutiveCapsule> getAllExecutives() {
    return executiveStore.getAllExecutives();
  }

  public void saveStateFlag(int flag) {
    dynamicPropertiesStore.saveStateFlag(flag);
  }

  public void updateNextMaintenanceTime(long time) {
    dynamicPropertiesStore.updateNextMaintenanceTime(time);
  }

  public long getNextMaintenanceTime() {
    return dynamicPropertiesStore.getNextMaintenanceTime();
  }

  public long getLatestSolidifiedBlockNum() {
    return dynamicPropertiesStore.getLatestSolidifiedBlockNum();
  }

  public void saveLatestSolidifiedBlockNum(long num) {
    dynamicPropertiesStore.saveLatestSolidifiedBlockNum(num);
  }

  public void applyBlock(boolean flag) {
    dynamicPropertiesStore.applyBlock(flag);
  }

  public boolean allowChangeDelegation() {
    return dynamicPropertiesStore.allowChangeDelegation();
  }
}