package org.stabila.core.capsule.utils;

import com.google.protobuf.ByteString;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.stabila.core.capsule.AccountAssetCapsule;
import org.stabila.core.store.AccountAssetStore;
import org.stabila.core.store.DynamicPropertiesStore;
import org.stabila.protos.Protocol.Account;
import org.stabila.protos.Protocol.AccountAsset;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class AssetUtil {

  private static AccountAssetStore accountAssetStore;

  private static DynamicPropertiesStore dynamicPropertiesStore;

  public static AccountAsset getAsset(Account account) {
    if (!hasAsset(account)) {
      return null;
    }
    return AccountAsset.newBuilder()
            .setAddress(account.getAddress())
            .setAssetIssuedID(account.getAssetIssuedID())
            .setAssetIssuedName(account.getAssetIssuedName())
            .putAllAsset(account.getAssetMap())
            .putAllAssetV2(account.getAssetV2Map())
            .putAllFreeAssetNetUsage(account.getFreeAssetNetUsageMap())
            .putAllFreeAssetNetUsageV2(account.getFreeAssetNetUsageV2Map())
            .putAllLatestAssetOperationTime(account.getLatestAssetOperationTimeMap())
            .putAllLatestAssetOperationTimeV2(
                    account.getLatestAssetOperationTimeV2Map())
            .addAllCdedSupply(getCded(account.getCdedSupplyList()))
            .build();
  }

  private static List<AccountAsset.Cded> getCded(List<Account.Cded> cdedSupplyList) {
    return cdedSupplyList
            .stream()
            .map(cded -> AccountAsset.Cded.newBuilder()
                    .setExpireTime(cded.getExpireTime())
                    .setCdedBalance(cded.getCdedBalance())
                    .build())
            .collect(Collectors.toList());
  }


  public static Account importAsset(Account account) {
    if (AssetUtil.hasAsset(account)) {
      return null;
    }
    AccountAssetCapsule accountAssetCapsule = accountAssetStore.get(account.getAddress().toByteArray());
    if (accountAssetCapsule == null) {
      return null;
    }

    return account.toBuilder()
            .setAssetIssuedID(accountAssetCapsule.getAssetIssuedID())
            .setAssetIssuedName(accountAssetCapsule.getAssetIssuedName())
            .putAllAsset(accountAssetCapsule.getAssetMap())
            .putAllAssetV2(accountAssetCapsule.getAssetMapV2())
            .putAllFreeAssetNetUsage(accountAssetCapsule.getAllFreeAssetNetUsage())
            .putAllFreeAssetNetUsageV2(accountAssetCapsule.getAllFreeAssetNetUsageV2())
            .putAllLatestAssetOperationTime(accountAssetCapsule.getLatestAssetOperationTimeMap())
            .putAllLatestAssetOperationTimeV2(
                    accountAssetCapsule.getLatestAssetOperationTimeMapV2())
            .addAllCdedSupply(getAccountCdedSupplyList(accountAssetCapsule.getCdedSupplyList()))
            .build();
  }

  private static List<Account.Cded> getAccountCdedSupplyList(List<AccountAsset.Cded> cdedSupplyList) {
    return Optional.ofNullable(cdedSupplyList)
            .orElseGet(ArrayList::new)
            .stream()
            .map(cded -> Account.Cded.newBuilder()
                    .setExpireTime(cded.getExpireTime())
                    .setCdedBalance(cded.getCdedBalance())
                    .build())
            .collect(Collectors.toList());
  }

  public static Account clearAsset(Account account) {
    return account.toBuilder()
            .clearAssetIssuedID()
            .clearAssetIssuedName()
            .clearAsset()
            .clearAssetV2()
            .clearFreeAssetNetUsage()
            .clearFreeAssetNetUsageV2()
            .clearLatestAssetOperationTime()
            .clearLatestAssetOperationTimeV2()
            .clearCdedSupply()
            .build();
  }

  public static boolean hasAsset(Account account) {
    if (MapUtils.isNotEmpty(account.getAssetMap()) ||
            MapUtils.isNotEmpty(account.getAssetV2Map())) {
      return true;
    }
    ByteString assetIssuedName = account.getAssetIssuedName();
    if (assetIssuedName != null && !assetIssuedName.isEmpty()) {
      return true;
    }
    ByteString assetIssuedID = account.getAssetIssuedID();
    if (assetIssuedID != null && !assetIssuedID.isEmpty()) {
      return true;
    }
    if (MapUtils.isNotEmpty(account.getLatestAssetOperationTimeMap()) ||
            MapUtils.isNotEmpty(account.getLatestAssetOperationTimeV2Map())) {
      return true;
    }
    if (MapUtils.isNotEmpty(account.getFreeAssetNetUsageMap())) {
      return true;
    }
    if (MapUtils.isNotEmpty(account.getFreeAssetNetUsageV2Map())) {
      return true;
    }
    List<Account.Cded> cdedSupplyList =
            account.getCdedSupplyList();
    if (CollectionUtils.isNotEmpty(cdedSupplyList)
            && cdedSupplyList.size() > 0) {
      return true;
    }
    return false;
  }

  public static void setAccountAssetStore(
          AccountAssetStore accountAssetStore) {
    AssetUtil.accountAssetStore = accountAssetStore;
  }

  public static void setDynamicPropertiesStore(DynamicPropertiesStore dynamicPropertiesStore) {
    AssetUtil.dynamicPropertiesStore = dynamicPropertiesStore;
  }

  public static boolean isAllowAssetOptimization() {
    return dynamicPropertiesStore.supportAllowAccountAssetOptimization();
  }

}