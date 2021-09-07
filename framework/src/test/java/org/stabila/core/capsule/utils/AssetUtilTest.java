/*
 * java-tron is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * java-tron is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.stabila.core.capsule.utils;

import com.google.common.collect.Lists;
import com.google.protobuf.ByteString;
import java.io.File;
import java.util.List;
import java.util.Random;
import lombok.extern.slf4j.Slf4j;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Test;
import org.stabila.core.Constant;
import org.stabila.core.capsule.AccountAssetCapsule;
import org.stabila.core.capsule.AccountCapsule;
import org.stabila.core.config.DefaultConfig;
import org.stabila.core.config.args.Args;
import org.stabila.core.db.Manager;
import org.stabila.core.store.AccountAssetStore;
import org.stabila.common.application.StabilaApplicationContext;
import org.stabila.common.utils.ByteArray;
import org.stabila.common.utils.FileUtil;
import org.stabila.protos.Protocol;




@Slf4j
public class AssetUtilTest {

  private static String dbPath = "output_AssetUtil_test";
  private static Manager dbManager;
  private static StabilaApplicationContext context;

  static {
    Args.setParam(new String[]{"-d", dbPath, "-w"}, Constant.TEST_CONF);
    context = new StabilaApplicationContext(DefaultConfig.class);
    dbManager = context.getBean(Manager.class);
  }

  @AfterClass
  public static void removeDb() {
    Args.clearParam();
    FileUtil.deleteDir(new File(dbPath));
  }

  public static byte[] randomBytes(int length) {
    //generate the random number
    byte[] result = new byte[length];
    new Random().nextBytes(result);
    return result;
  }

  private static AccountCapsule createAccount() {
    com.google.protobuf.ByteString accountName =
            com.google.protobuf.ByteString.copyFrom(randomBytes(16));
    com.google.protobuf.ByteString address =
            ByteString.copyFrom(randomBytes(32));
    Protocol.AccountType accountType = Protocol.AccountType.forNumber(1);
    AccountCapsule accountCapsule = new AccountCapsule(accountName, address, accountType);
    accountCapsule.addAssetV2(ByteArray.fromString(String.valueOf(1)), 1000L);
    Protocol.Account build = accountCapsule.getInstance().toBuilder()
            .addAllFrozenSupply(getFrozenList())
            .build();
    accountCapsule.setInstance(build);

    return accountCapsule;
  }

  private static AccountCapsule createAccount2() {
    AccountAssetStore accountAssetStore = dbManager.getAccountAssetStore();
    com.google.protobuf.ByteString accountName =
            com.google.protobuf.ByteString.copyFrom(randomBytes(16));
    com.google.protobuf.ByteString address = ByteString.copyFrom(randomBytes(32));
    Protocol.AccountType accountType = Protocol.AccountType.forNumber(1);
    AccountCapsule accountCapsule = new AccountCapsule(accountName, address, accountType);
    Protocol.AccountAsset accountAsset =
            Protocol.AccountAsset.newBuilder()
            .setAddress(accountCapsule.getInstance().getAddress())
            .setAssetIssuedID(accountCapsule.getAssetIssuedID())
            .setAssetIssuedName(accountCapsule.getAssetIssuedName())
            .build();
    accountAssetStore.put(accountCapsule.createDbKey(),
              new AccountAssetCapsule(
              accountAsset));
    return accountCapsule;
  }

  @Test
  public void testGetAsset() {
    AccountCapsule account = createAccount();
    Protocol.AccountAsset asset = AssetUtil.getAsset(account.getInstance());
    Assert.assertNotNull(asset);
  }

  @Test
  public void testImport() {
    AccountCapsule account = createAccount2();
    Protocol.Account asset = AssetUtil.importAsset(account.getInstance());
    Assert.assertNotNull(asset);
  }

  @Test
  public void tetGetFrozen() {
    AccountCapsule account = createAccount2();
    Protocol.Account build = account.getInstance().toBuilder()
            .addAllFrozenSupply(getFrozenList())
            .build();
    account.setInstance(build);
    Assert.assertNotNull(account.getFrozenSupplyList());
  }

  private static List<Protocol.Account.Frozen> getFrozenList() {
    List<Protocol.Account.Frozen> frozenList = Lists.newArrayList();
    for (int i = 0; i < 3; i++) {
      Protocol.Account.Frozen newFrozen = Protocol.Account.Frozen.newBuilder()
              .setFrozenBalance(i * 1000 + 1)
              .setExpireTime(1000)
              .build();
      frozenList.add(newFrozen);
    }
    return frozenList;
  }

}
