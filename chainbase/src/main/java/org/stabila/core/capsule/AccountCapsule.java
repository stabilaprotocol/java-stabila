/*
 * java-stabila is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * java-stabila is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.stabila.core.capsule;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.stabila.core.store.AssetIssueStore;
import org.stabila.core.store.DynamicPropertiesStore;
import org.stabila.common.utils.ByteArray;
import org.stabila.core.capsule.utils.AssetUtil;
import org.stabila.protos.Protocol.Account;
import org.stabila.protos.Protocol.Account.AccountResource;
import org.stabila.protos.Protocol.Account.Builder;
import org.stabila.protos.Protocol.Account.Cded;
import org.stabila.protos.Protocol.AccountType;
import org.stabila.protos.Protocol.Key;
import org.stabila.protos.Protocol.Permission;
import org.stabila.protos.Protocol.Permission.PermissionType;
import org.stabila.protos.Protocol.Vote;
import org.stabila.protos.contract.AccountContract.AccountCreateContract;
import org.stabila.protos.contract.AccountContract.AccountUpdateContract;

@Slf4j(topic = "capsule")
public class AccountCapsule implements ProtoCapsule<Account>, Comparable<AccountCapsule> {

  private Account account;

  @Getter
  @Setter
  private Boolean isAssetImport = false;

  /**
   * get account from bytes data.
   */
  public AccountCapsule(byte[] data) {
    try {
      this.account = Account.parseFrom(data);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage());
    }
  }

  /**
   * initial account capsule.
   */
  public AccountCapsule(ByteString accountName, ByteString address, AccountType accountType,
      long balance) {
    this.account = Account.newBuilder()
        .setAccountName(accountName)
        .setType(accountType)
        .setAddress(address)
        .setBalance(balance)
        .build();
  }

  /**
   * construct account from AccountCreateContract.
   */
  public AccountCapsule(final AccountCreateContract contract) {
    this.account = Account.newBuilder()
        .setType(contract.getType())
        .setAddress(contract.getAccountAddress())
        .setTypeValue(contract.getTypeValue())
        .build();
  }

  /**
   * construct account from AccountCreateContract and createTime.
   */
  public AccountCapsule(final AccountCreateContract contract, long createTime,
      boolean withDefaultPermission, DynamicPropertiesStore dynamicPropertiesStore) {
    if (withDefaultPermission) {
      Permission owner = createDefaultOwnerPermission(contract.getAccountAddress());
      Permission active = createDefaultActivePermission(contract.getAccountAddress(),
          dynamicPropertiesStore);

      this.account = Account.newBuilder()
          .setType(contract.getType())
          .setAddress(contract.getAccountAddress())
          .setTypeValue(contract.getTypeValue())
          .setCreateTime(createTime)
          .setOwnerPermission(owner)
          .addActivePermission(active)
          .build();
    } else {
      this.account = Account.newBuilder()
          .setType(contract.getType())
          .setAddress(contract.getAccountAddress())
          .setTypeValue(contract.getTypeValue())
          .setCreateTime(createTime)
          .build();
    }
  }


  /**
   * construct account from AccountUpdateContract
   */
  public AccountCapsule(final AccountUpdateContract contract) {

  }

  /**
   * get account from address and account name.
   */
  public AccountCapsule(ByteString address, ByteString accountName,
      AccountType accountType) {
    this.account = Account.newBuilder()
        .setType(accountType)
        .setAccountName(accountName)
        .setAddress(address)
        .build();
  }

  /**
   * get account from address.
   */
  public AccountCapsule(ByteString address,
      AccountType accountType) {
    this.account = Account.newBuilder()
        .setType(accountType)
        .setAddress(address)
        .build();
  }

  /**
   * get account from address.
   */
  public AccountCapsule(ByteString address,
      AccountType accountType, long createTime,
      boolean withDefaultPermission, DynamicPropertiesStore dynamicPropertiesStore) {
    if (withDefaultPermission) {
      Permission owner = createDefaultOwnerPermission(address);
      Permission active = createDefaultActivePermission(address, dynamicPropertiesStore);

      this.account = Account.newBuilder()
          .setType(accountType)
          .setAddress(address)
          .setCreateTime(createTime)
          .setOwnerPermission(owner)
          .addActivePermission(active)
          .build();
    } else {
      this.account = Account.newBuilder()
          .setType(accountType)
          .setAddress(address)
          .setCreateTime(createTime)
          .build();
    }

  }

  /**
   * get account from address.
   */
  public AccountCapsule(Account account) {
    this.account = account;
  }

  private static ByteString getActiveDefaultOperations(
      DynamicPropertiesStore dynamicPropertiesStore) {
    return ByteString.copyFrom(dynamicPropertiesStore.getActiveDefaultOperations());
  }

  public static Permission createDefaultOwnerPermission(ByteString address) {
    Key.Builder key = Key.newBuilder();
    key.setAddress(address);
    key.setWeight(1);

    Permission.Builder owner = Permission.newBuilder();
    owner.setType(PermissionType.Owner);
    owner.setId(0);
    owner.setPermissionName("owner");
    owner.setThreshold(1);
    owner.setParentId(0);
    owner.addKeys(key);

    return owner.build();
  }

  public static Permission createDefaultActivePermission(ByteString address,
      DynamicPropertiesStore dynamicPropertiesStore) {
    Key.Builder key = Key.newBuilder();
    key.setAddress(address);
    key.setWeight(1);

    Permission.Builder active = Permission.newBuilder();
    active.setType(PermissionType.Active);
    active.setId(2);
    active.setPermissionName("active");
    active.setThreshold(1);
    active.setParentId(0);
    active.setOperations(getActiveDefaultOperations(dynamicPropertiesStore));
    active.addKeys(key);

    return active.build();
  }

  public static Permission createDefaultExecutivePermission(ByteString address) {
    Key.Builder key = Key.newBuilder();
    key.setAddress(address);
    key.setWeight(1);

    Permission.Builder active = Permission.newBuilder();
    active.setType(PermissionType.Executive);
    active.setId(1);
    active.setPermissionName("executive");
    active.setThreshold(1);
    active.setParentId(0);
    active.addKeys(key);

    return active.build();
  }

  public static Permission getDefaultPermission(ByteString owner) {
    return createDefaultOwnerPermission(owner);
  }

  @Override
  public int compareTo(AccountCapsule otherObject) {
    return Long.compare(otherObject.getBalance(), this.getBalance());
  }

  public byte[] getData() {
    return this.account.toByteArray();
  }

  @Override
  public Account getInstance() {
    return this.account;
  }

  public void setInstance(Account account) {
    this.account = account;
  }

  public ByteString getAddress() {
    return this.account.getAddress();
  }

  public byte[] createDbKey() {
    return getAddress().toByteArray();
  }

  public String createReadableString() {
    return ByteArray.toHexString(getAddress().toByteArray());
  }

  public AccountType getType() {
    return this.account.getType();
  }

  public ByteString getAccountName() {
    return this.account.getAccountName();
  }

  /**
   * set account name
   */
  public void setAccountName(byte[] name) {
    this.account = this.account.toBuilder().setAccountName(ByteString.copyFrom(name)).build();
  }

  public ByteString getAccountId() {
    return this.account.getAccountId();
  }

  /**
   * set account id
   */
  public void setAccountId(byte[] id) {
    this.account = this.account.toBuilder().setAccountId(ByteString.copyFrom(id)).build();
  }

  public void setDefaultExecutivePermission(DynamicPropertiesStore dynamicPropertiesStore) {
    Builder builder = this.account.toBuilder();
    Permission executive = createDefaultExecutivePermission(this.getAddress());
    if (!this.account.hasOwnerPermission()) {
      Permission owner = createDefaultOwnerPermission(this.getAddress());
      builder.setOwnerPermission(owner);
    }
    if (this.account.getActivePermissionCount() == 0) {
      Permission active = createDefaultActivePermission(this.getAddress(), dynamicPropertiesStore);
      builder.addActivePermission(active);
    }
    this.account = builder.setExecutivePermission(executive).build();
  }

  public byte[] getExecutivePermissionAddress() {
    if (this.account.getExecutivePermission().getKeysCount() == 0) {
      return getAddress().toByteArray();
    } else {
      return this.account.getExecutivePermission().getKeys(0).getAddress().toByteArray();
    }
  }

  public long getBalance() {
    return this.account.getBalance();
  }

  public void setBalance(long balance) {
    this.account = this.account.toBuilder().setBalance(balance).build();
  }

  public long getLatestOperationTime() {
    return this.account.getLatestOprationTime();
  }

  public void setLatestOperationTime(long latest_time) {
    this.account = this.account.toBuilder().setLatestOprationTime(latest_time).build();
  }

  public long getLatestConsumeTime() {
    return this.account.getLatestConsumeTime();
  }

  public void setLatestConsumeTime(long latest_time) {
    this.account = this.account.toBuilder().setLatestConsumeTime(latest_time).build();
  }

  public long getLatestConsumeFreeTime() {
    return this.account.getLatestConsumeFreeTime();
  }

  public void setLatestConsumeFreeTime(long latest_time) {
    this.account = this.account.toBuilder().setLatestConsumeFreeTime(latest_time).build();
  }

  public void addDelegatedCdedBalanceForBandwidth(long balance) {
    this.account = this.account.toBuilder().setDelegatedCdedBalanceForBandwidth(
        this.account.getDelegatedCdedBalanceForBandwidth() + balance).build();
  }

  public long getAcquiredDelegatedCdedBalanceForBandwidth() {
    return this.account.getAcquiredDelegatedCdedBalanceForBandwidth();
  }

  public void setAcquiredDelegatedCdedBalanceForBandwidth(long balance) {
    this.account = this.account.toBuilder().setAcquiredDelegatedCdedBalanceForBandwidth(balance)
        .build();
  }

  public void addAcquiredDelegatedCdedBalanceForBandwidth(long balance) {
    this.account = this.account.toBuilder().setAcquiredDelegatedCdedBalanceForBandwidth(
        this.account.getAcquiredDelegatedCdedBalanceForBandwidth() + balance)
        .build();
  }

  public void safeAddAcquiredDelegatedCdedBalanceForBandwidth(long balance) {
    this.account = this.account.toBuilder().setAcquiredDelegatedCdedBalanceForBandwidth(
        Math.max(0, this.account.getAcquiredDelegatedCdedBalanceForBandwidth() + balance))
        .build();
  }

  public long getAcquiredDelegatedCdedBalanceForUcr() {
    return getAccountResource().getAcquiredDelegatedCdedBalanceForUcr();
  }

  public void setAcquiredDelegatedCdedBalanceForUcr(long balance) {
    AccountResource newAccountResource = getAccountResource().toBuilder()
        .setAcquiredDelegatedCdedBalanceForUcr(balance).build();

    this.account = this.account.toBuilder()
        .setAccountResource(newAccountResource)
        .build();
  }

  public long getDelegatedCdedBalanceForUcr() {
    return getAccountResource().getDelegatedCdedBalanceForUcr();
  }

  public long getDelegatedCdedBalanceForBandwidth() {
    return this.account.getDelegatedCdedBalanceForBandwidth();
  }

  public void setDelegatedCdedBalanceForBandwidth(long balance) {
    this.account = this.account.toBuilder()
        .setDelegatedCdedBalanceForBandwidth(balance)
        .build();
  }

  public void setDelegatedCdedBalanceForUcr(long balance){
    AccountResource newAccountResource = getAccountResource().toBuilder()
        .setDelegatedCdedBalanceForUcr(balance).build();

    this.account = this.account.toBuilder()
        .setAccountResource(newAccountResource)
        .build();
  }

  public void addAcquiredDelegatedCdedBalanceForUcr(long balance) {
    AccountResource newAccountResource = getAccountResource().toBuilder()
        .setAcquiredDelegatedCdedBalanceForUcr(
            getAccountResource().getAcquiredDelegatedCdedBalanceForUcr() + balance).build();

    this.account = this.account.toBuilder()
        .setAccountResource(newAccountResource)
        .build();
  }

  public void safeAddAcquiredDelegatedCdedBalanceForUcr(long balance) {
    AccountResource newAccountResource = getAccountResource().toBuilder()
        .setAcquiredDelegatedCdedBalanceForUcr(
            Math.max(0, getAccountResource().getAcquiredDelegatedCdedBalanceForUcr() + balance))
        .build();

    this.account = this.account.toBuilder()
        .setAccountResource(newAccountResource)
        .build();
  }

  public void addDelegatedCdedBalanceForUcr(long balance) {
    AccountResource newAccountResource = getAccountResource().toBuilder()
        .setDelegatedCdedBalanceForUcr(
            getAccountResource().getDelegatedCdedBalanceForUcr() + balance).build();

    this.account = this.account.toBuilder()
        .setAccountResource(newAccountResource)
        .build();
  }

  @Override
  public String toString() {
    return this.account.toString();
  }

  /**
   * set votes.
   */
  public void addVotes(ByteString voteAddress, long voteAdd) {
    this.account = this.account.toBuilder()
        .addVotes(Vote.newBuilder().setVoteAddress(voteAddress).setVoteCount(voteAdd).build())
        .build();
  }

  public void clearAssetV2() {
    importAsset();
    this.account = this.account.toBuilder()
        .clearAssetV2()
        .build();
  }

  public void clearLatestAssetOperationTimeV2() {
    importAsset();
    this.account = this.account.toBuilder()
        .clearLatestAssetOperationTimeV2()
        .build();
  }

  public void clearFreeAssetNetUsageV2() {
    importAsset();
    this.account = this.account.toBuilder()
        .clearFreeAssetNetUsageV2()
        .build();
  }

  public void clearVotes() {
    this.account = this.account.toBuilder()
        .clearVotes()
        .build();
  }

  /**
   * get votes.
   */
  public List<Vote> getVotesList() {
    if (this.account.getVotesList() != null) {
      return this.account.getVotesList();
    } else {
      return Lists.newArrayList();
    }
  }

  public long getStabilaPowerUsage() {
    if (this.account.getVotesList() != null) {
      return this.account.getVotesList().stream().mapToLong(Vote::getVoteCount).sum();
    } else {
      return 0L;
    }
  }

  //tp:Stabila_Power
  public long getStabilaPower() {
    long tp = 0;
    for (int i = 0; i < account.getCdedCount(); ++i) {
      tp += account.getCded(i).getCdedBalance();
    }

    tp += account.getAccountResource().getCdedBalanceForUcr().getCdedBalance();
    tp += account.getDelegatedCdedBalanceForBandwidth();
    tp += account.getAccountResource().getDelegatedCdedBalanceForUcr();
    return tp;
  }

  public long getAllStabilaPower() {
    if (account.getOldStabilaPower() == -1) {
      return getStabilaPowerCdedBalance();
    } else if (account.getOldStabilaPower() == 0) {
      return getStabilaPower() + getStabilaPowerCdedBalance();
    } else {
      return account.getOldStabilaPower() + getStabilaPowerCdedBalance();
    }
  }

  /**
   * asset balance enough
   */
  public boolean assetBalanceEnough(byte[] key, long amount) {
    importAsset();
    Map<String, Long> assetMap = this.account.getAssetMap();
    String nameKey = ByteArray.toStr(key);
    Long currentAmount = assetMap.get(nameKey);

    return amount > 0 && null != currentAmount && amount <= currentAmount;
  }

  public boolean assetBalanceEnoughV2(byte[] key, long amount,
      DynamicPropertiesStore dynamicPropertiesStore) {
    importAsset();
    Map<String, Long> assetMap;
    String nameKey;
    Long currentAmount;
    if (dynamicPropertiesStore.getAllowSameTokenName() == 0) {
      assetMap = this.account.getAssetMap();
      nameKey = ByteArray.toStr(key);
      currentAmount = assetMap.get(nameKey);
    } else {
      String tokenID = ByteArray.toStr(key);
      assetMap = this.account.getAssetV2Map();
      currentAmount = assetMap.get(tokenID);
    }

    return amount > 0 && null != currentAmount && amount <= currentAmount;
  }

  /**
   * reduce asset amount.
   */
  public boolean reduceAssetAmount(byte[] key, long amount) {
    importAsset();
    Map<String, Long> assetMap = this.account.getAssetMap();
    String nameKey = ByteArray.toStr(key);
    Long currentAmount = assetMap.get(nameKey);
    if (amount > 0 && null != currentAmount && amount <= currentAmount) {
      this.account = this.account.toBuilder()
          .putAsset(nameKey, Math.subtractExact(currentAmount, amount)).build();
      return true;
    }

    return false;
  }

  /**
   * reduce asset amount.
   */
  public boolean reduceAssetAmountV2(byte[] key, long amount,
      DynamicPropertiesStore dynamicPropertiesStore, AssetIssueStore assetIssueStore) {
    //key is token name
    importAsset();
    if (dynamicPropertiesStore.getAllowSameTokenName() == 0) {
      Map<String, Long> assetMap = this.account.getAssetMap();
      AssetIssueCapsule assetIssueCapsule = assetIssueStore.get(key);
      String tokenID = assetIssueCapsule.getId();
      String nameKey = ByteArray.toStr(key);
      Long currentAmount = assetMap.get(nameKey);
      if (amount > 0 && null != currentAmount && amount <= currentAmount) {
        this.account = this.account.toBuilder()
            .putAsset(nameKey, Math.subtractExact(currentAmount, amount))
            .putAssetV2(tokenID, Math.subtractExact(currentAmount, amount))
            .build();
        return true;
      }
    }
    //key is token id
    if (dynamicPropertiesStore.getAllowSameTokenName() == 1) {
      String tokenID = ByteArray.toStr(key);
      Map<String, Long> assetMapV2 = this.account.getAssetV2Map();
      Long currentAmount = assetMapV2.get(tokenID);
      if (amount > 0 && null != currentAmount && amount <= currentAmount) {
        this.account = this.account.toBuilder()
            .putAssetV2(tokenID, Math.subtractExact(currentAmount, amount))
            .build();
        return true;
      }
    }

    return false;
  }

  /**
   * add asset amount.
   */
  public boolean addAssetAmount(byte[] key, long amount) {
    importAsset();
    Map<String, Long> assetMap = this.account.getAssetMap();
    String nameKey = ByteArray.toStr(key);
    Long currentAmount = assetMap.get(nameKey);
    if (currentAmount == null) {
      currentAmount = 0L;
    }
    this.account = this.account.toBuilder().putAsset(nameKey, Math.addExact(currentAmount, amount))
        .build();
    return true;
  }

  /**
   * add asset amount.
   */
  public boolean addAssetAmountV2(byte[] key, long amount,
      DynamicPropertiesStore dynamicPropertiesStore, AssetIssueStore assetIssueStore) {
    importAsset();
    //key is token name
    if (dynamicPropertiesStore.getAllowSameTokenName() == 0) {
      Map<String, Long> assetMap = this.account.getAssetMap();
      AssetIssueCapsule assetIssueCapsule = assetIssueStore.get(key);
      String tokenID = assetIssueCapsule.getId();
      String nameKey = ByteArray.toStr(key);
      Long currentAmount = assetMap.get(nameKey);
      if (currentAmount == null) {
        currentAmount = 0L;
      }
      this.account = this.account.toBuilder()
          .putAsset(nameKey, Math.addExact(currentAmount, amount))
          .putAssetV2(tokenID, Math.addExact(currentAmount, amount))
          .build();
    }
    //key is token id
    if (dynamicPropertiesStore.getAllowSameTokenName() == 1) {
      String tokenIDStr = ByteArray.toStr(key);
      Map<String, Long> assetMapV2 = this.account.getAssetV2Map();
      Long currentAmount = assetMapV2.get(tokenIDStr);
      if (currentAmount == null) {
        currentAmount = 0L;
      }
      this.account = this.account.toBuilder()
          .putAssetV2(tokenIDStr, Math.addExact(currentAmount, amount))
          .build();
    }
    return true;
  }

  /**
   * add asset.
   */
  public boolean addAsset(byte[] key, long value) {
    importAsset();
    Map<String, Long> assetMap = this.account.getAssetMap();
    String nameKey = ByteArray.toStr(key);
    if (!assetMap.isEmpty() && assetMap.containsKey(nameKey)) {
      return false;
    }
    this.account = this.account.toBuilder().putAsset(nameKey, value).build();
    return true;
  }

  public boolean addAssetV2(byte[] key, long value) {
    importAsset();
    String tokenID = ByteArray.toStr(key);
    Map<String, Long> assetV2Map = this.account.getAssetV2Map();
    if (!assetV2Map.isEmpty() && assetV2Map.containsKey(tokenID)) {
      return false;
    }

    this.account = this.account.toBuilder()
        .putAssetV2(tokenID, value)
        .build();
    return true;
  }

  /**
   * add asset.
   */
  public boolean addAssetMapV2(Map<String, Long> assetMap) {
    importAsset();
    this.account = this.account.toBuilder().putAllAssetV2(assetMap).build();
    return true;
  }

  public Map<String, Long> getAssetMap() {
    importAsset();
    Map<String, Long> assetMap = this.account.getAssetMap();
    if (assetMap.isEmpty()) {
      assetMap = Maps.newHashMap();
    }

    return assetMap;
  }

  public Map<String, Long> getAssetMapV2() {
    importAsset();
    Map<String, Long> assetMap = this.account.getAssetV2Map();
    if (assetMap.isEmpty()) {
      assetMap = Maps.newHashMap();
    }

    return assetMap;
  }

  public boolean addAllLatestAssetOperationTimeV2(Map<String, Long> map) {
    importAsset();
    this.account = this.account.toBuilder().putAllLatestAssetOperationTimeV2(map).build();
    return true;
  }

  public Map<String, Long> getLatestAssetOperationTimeMap() {
    importAsset();
    return this.account.getLatestAssetOperationTimeMap();
  }

  public Map<String, Long> getLatestAssetOperationTimeMapV2() {
    importAsset();
    return this.account.getLatestAssetOperationTimeV2Map();
  }

  public long getLatestAssetOperationTime(String assetName) {
    importAsset();
    return this.account.getLatestAssetOperationTimeOrDefault(assetName, 0);
  }

  public long getLatestAssetOperationTimeV2(String assetName) {
    importAsset();
    return this.account.getLatestAssetOperationTimeV2OrDefault(assetName, 0);
  }

  public void putLatestAssetOperationTimeMap(String key, Long value) {
    importAsset();
    this.account = this.account.toBuilder().putLatestAssetOperationTime(key, value).build();
  }

  public void putLatestAssetOperationTimeMapV2(String key, Long value) {
    importAsset();
    this.account = this.account.toBuilder().putLatestAssetOperationTimeV2(key, value).build();
  }

  public int getCdedCount() {
    return getInstance().getCdedCount();
  }

  public List<Cded> getCdedList() {
    return getInstance().getCdedList();
  }

  public long getCdedBalance() {
    List<Cded> cdedList = getCdedList();
    final long[] cdedBalance = {0};
    cdedList.forEach(cded -> cdedBalance[0] = Long.sum(cdedBalance[0],
        cded.getCdedBalance()));
    return cdedBalance[0];
  }

  public long getAllCdedBalanceForBandwidth() {
    return getCdedBalance() + getAcquiredDelegatedCdedBalanceForBandwidth();
  }

  public int getCdedSupplyCount() {
    importAsset();
    return getInstance().getCdedSupplyCount();
  }

  public List<Cded> getCdedSupplyList() {
    importAsset();
    return getInstance().getCdedSupplyList();
  }

  public long getCdedSupplyBalance() {
    List<Cded> cdedSupplyList = getCdedSupplyList();
    final long[] cdedSupplyBalance = {0};
    cdedSupplyList.forEach(cded -> cdedSupplyBalance[0] = Long.sum(cdedSupplyBalance[0],
        cded.getCdedBalance()));
    return cdedSupplyBalance[0];
  }

  public ByteString getAssetIssuedName() {
    importAsset();
    return getInstance().getAssetIssuedName();
  }

  public void setAssetIssuedName(byte[] nameKey) {
    importAsset();
    ByteString assetIssuedName = ByteString.copyFrom(nameKey);
    this.account = this.account.toBuilder().setAssetIssuedName(assetIssuedName).build();
  }

  public ByteString getAssetIssuedID() {
    importAsset();
    return getInstance().getAssetIssuedID();
  }

  public void setAssetIssuedID(byte[] id) {
    importAsset();
    ByteString assetIssuedID = ByteString.copyFrom(id);
    this.account = this.account.toBuilder().setAssetIssuedID(assetIssuedID).build();
  }

  public long getAllowance() {
    return getInstance().getAllowance();
  }

  public void setAllowance(long allowance) {
    this.account = this.account.toBuilder().setAllowance(allowance).build();
  }

  public long getLatestWithdrawTime() {
    return getInstance().getLatestWithdrawTime();
  }

  //for test only
  public void setLatestWithdrawTime(long latestWithdrawTime) {
    this.account = this.account.toBuilder()
        .setLatestWithdrawTime(latestWithdrawTime)
        .build();
  }

  public boolean getIsExecutive() {
    return getInstance().getIsExecutive();
  }

  public void setIsExecutive(boolean isExecutive) {
    this.account = this.account.toBuilder().setIsExecutive(isExecutive).build();
  }

  public boolean getIsCommittee() {
    return getInstance().getIsCommittee();
  }

  public void setIsCommittee(boolean isCommittee) {
    this.account = this.account.toBuilder().setIsCommittee(isCommittee).build();
  }

  public void setCdedForBandwidth(long cdedBalance, long expireTime) {
    Cded newCded = Cded.newBuilder()
        .setCdedBalance(cdedBalance)
        .setExpireTime(expireTime)
        .build();

    long cdedCount = getCdedCount();
    if (cdedCount == 0) {
      setInstance(getInstance().toBuilder()
          .addCded(newCded)
          .build());
    } else {
      setInstance(getInstance().toBuilder()
          .setCded(0, newCded)
          .build()
      );
    }
  }

  //set CdedBalanceForBandwidth
  //for test only
  public void setCded(long cdedBalance, long expireTime) {
    Cded newCded = Cded.newBuilder()
        .setCdedBalance(cdedBalance)
        .setExpireTime(expireTime)
        .build();

    this.account = this.account.toBuilder()
        .addCded(newCded)
        .build();
  }

  public long getNetUsage() {
    return this.account.getNetUsage();
  }

  public void setNetUsage(long netUsage) {
    this.account = this.account.toBuilder()
        .setNetUsage(netUsage).build();
  }

  public AccountResource getAccountResource() {
    return this.account.getAccountResource();
  }

  public void setCdedForUcr(long newCdedBalanceForUcr, long time) {
    Cded newCdedForUcr = Cded.newBuilder()
        .setCdedBalance(newCdedBalanceForUcr)
        .setExpireTime(time)
        .build();

    AccountResource newAccountResource = getAccountResource().toBuilder()
        .setCdedBalanceForUcr(newCdedForUcr).build();

    this.account = this.account.toBuilder()
        .setAccountResource(newAccountResource)
        .build();
  }

  public long getUcrCdedBalance() {
    return this.account.getAccountResource().getCdedBalanceForUcr().getCdedBalance();
  }

  public boolean oldStabilaPowerIsNotInitialized() {
    return this.account.getOldStabilaPower() == 0;
  }

  public boolean oldStabilaPowerIsInvalid() {
    return this.account.getOldStabilaPower() == -1;
  }

  public void initializeOldStabilaPower() {
    long value = getStabilaPower();
    if (value == 0) {
      value = -1;
    }
    setInstance(getInstance().toBuilder()
        .setOldStabilaPower(value)
        .build());
  }

  public void invalidateOldStabilaPower() {
    setInstance(getInstance().toBuilder()
        .setOldStabilaPower(-1)
        .build());
  }


  public void setOldStabilaPower(long value) {
    setInstance(getInstance().toBuilder()
        .setOldStabilaPower(value)
        .build());
  }

  public void setCdedForStabilaPower(long cdedBalance, long expireTime) {
    Cded newCded = Cded.newBuilder()
        .setCdedBalance(cdedBalance)
        .setExpireTime(expireTime)
        .build();

    setInstance(getInstance().toBuilder()
        .setStabilaPower(newCded)
        .build());
  }

  public long getStabilaPowerCdedBalance() {
    return this.account.getStabilaPower().getCdedBalance();
  }

  public long getUcrUsage() {
    return this.account.getAccountResource().getUcrUsage();
  }

  public void setUcrUsage(long ucrUsage) {
    this.account = this.account.toBuilder()
        .setAccountResource(
            this.account.getAccountResource().toBuilder().setUcrUsage(ucrUsage).build())
        .build();
  }

  public long getAllCdedBalanceForUcr() {
    return getUcrCdedBalance() + getAcquiredDelegatedCdedBalanceForUcr();
  }

  public long getLatestConsumeTimeForUcr() {
    return this.account.getAccountResource().getLatestConsumeTimeForUcr();
  }

  public void setLatestConsumeTimeForUcr(long latest_time) {
    this.account = this.account.toBuilder()
        .setAccountResource(
            this.account.getAccountResource().toBuilder().setLatestConsumeTimeForUcr(latest_time)
                .build()).build();
  }

  public long getFreeNetUsage() {
    return this.account.getFreeNetUsage();
  }

  public void setFreeNetUsage(long freeNetUsage) {
    this.account = this.account.toBuilder()
        .setFreeNetUsage(freeNetUsage).build();
  }

  public boolean addAllFreeAssetNetUsageV2(Map<String, Long> map) {
    importAsset();
    this.account = this.account.toBuilder().putAllFreeAssetNetUsageV2(map).build();
    return true;
  }

  public long getFreeAssetNetUsage(String assetName) {
    importAsset();
    return this.account.getFreeAssetNetUsageOrDefault(assetName, 0);
  }

  public long getFreeAssetNetUsageV2(String assetName) {
    importAsset();
    return this.account.getFreeAssetNetUsageV2OrDefault(assetName, 0);
  }

  public Map<String, Long> getAllFreeAssetNetUsage() {
    importAsset();
    return this.account.getFreeAssetNetUsageMap();
  }

  public Map<String, Long> getAllFreeAssetNetUsageV2() {
    importAsset();
    return this.account.getFreeAssetNetUsageV2Map();
  }

  public void putFreeAssetNetUsage(String s, long freeAssetNetUsage) {
    importAsset();
    this.account = this.account.toBuilder()
        .putFreeAssetNetUsage(s, freeAssetNetUsage).build();
  }

  public void putFreeAssetNetUsageV2(String s, long freeAssetNetUsage) {
    importAsset();
    this.account = this.account.toBuilder()
        .putFreeAssetNetUsageV2(s, freeAssetNetUsage).build();
  }

  public long getStorageLimit() {
    return this.account.getAccountResource().getStorageLimit();
  }

  public void setStorageLimit(long limit) {
    AccountResource accountResource = this.account.getAccountResource();
    accountResource = accountResource.toBuilder().setStorageLimit(limit).build();

    this.account = this.account.toBuilder()
        .setAccountResource(accountResource)
        .build();
  }

  public long getStorageUsage() {
    return this.account.getAccountResource().getStorageUsage();
  }

  public void setStorageUsage(long usage) {
    AccountResource accountResource = this.account.getAccountResource();
    accountResource = accountResource.toBuilder().setStorageUsage(usage).build();

    this.account = this.account.toBuilder()
        .setAccountResource(accountResource)
        .build();
  }

  public long getStorageLeft() {
    return getStorageLimit() - getStorageUsage();
  }

  public long getLatestExchangeStorageTime() {
    return this.account.getAccountResource().getLatestExchangeStorageTime();
  }

  public void setLatestExchangeStorageTime(long time) {
    AccountResource accountResource = this.account.getAccountResource();
    accountResource = accountResource.toBuilder().setLatestExchangeStorageTime(time).build();

    this.account = this.account.toBuilder()
        .setAccountResource(accountResource)
        .build();
  }

  public void addStorageUsage(long storageUsage) {
    if (storageUsage <= 0) {
      return;
    }
    AccountResource accountResource = this.account.getAccountResource();
    accountResource = accountResource.toBuilder()
        .setStorageUsage(accountResource.getStorageUsage() + storageUsage).build();

    this.account = this.account.toBuilder()
        .setAccountResource(accountResource)
        .build();
  }

  public Permission getPermissionById(int id) {
    if (id == 0) {
      if (this.account.hasOwnerPermission()) {
        return this.account.getOwnerPermission();
      }
      return getDefaultPermission(this.account.getAddress());
    }
    if (id == 1) {
      if (this.account.hasExecutivePermission()) {
        return this.account.getExecutivePermission();
      }
      return null;
    }
    for (Permission permission : this.account.getActivePermissionList()) {
      if (id == permission.getId()) {
        return permission;
      }
    }
    return null;
  }

  public void updatePermissions(Permission owner, Permission executive, List<Permission> actives) {
    Builder builder = this.account.toBuilder();
    owner = owner.toBuilder().setId(0).build();
    builder.setOwnerPermission(owner);
    if (builder.getIsExecutive()) {
      executive = executive.toBuilder().setId(1).build();
      builder.setExecutivePermission(executive);
    }
    builder.clearActivePermission();
    for (int i = 0; i < actives.size(); i++) {
      Permission permission = actives.get(i).toBuilder().setId(i + 2).build();
      builder.addActivePermission(permission);
    }
    this.account = builder.build();
  }

  public void updateAccountType(AccountType accountType) {
    this.account = this.account.toBuilder().setType(accountType).build();
  }

  // just for vm create2 instruction
  public void clearDelegatedResource() {
    Builder builder = account.toBuilder();
    AccountResource newAccountResource = getAccountResource().toBuilder()
        .setAcquiredDelegatedCdedBalanceForUcr(0L).build();
    builder.setAccountResource(newAccountResource);
    builder.setAcquiredDelegatedCdedBalanceForBandwidth(0L);
    this.account = builder.build();
  }

  public void importAsset() {
    if (!AssetUtil.isAllowAssetOptimization()) {
      return;
    }
    if (!this.isAssetImport) {
      Account account = AssetUtil.importAsset(this.account);
      if (null != account) {
        this.account = account;
      }
      this.isAssetImport = true;
    }
  }

  public boolean hasEnoughBalanceForDeployContract(DynamicPropertiesStore dynamicPropertiesStore) {
    return this.account.getBalance() >= dynamicPropertiesStore.getDeployContractFee();
  }
}