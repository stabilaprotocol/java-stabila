package org.stabila.core.db;

import com.google.common.collect.Maps;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.stabila.core.ChainBaseManager;
import org.stabila.core.capsule.AccountCapsule;
import org.stabila.core.capsule.BlockCapsule;
import org.stabila.core.capsule.ExecutiveCapsule;
import org.stabila.common.crypto.ECKey;
import org.stabila.common.utils.ByteArray;
import org.stabila.common.utils.Sha256Hash;
import org.stabila.common.utils.Utils;
import org.stabila.consensus.dpos.DposSlot;
import org.stabila.protos.Protocol.Account;

public class ManagerForTest {

  private Manager dbManager;
  private ChainBaseManager chainBaseManager;
  private DposSlot dposSlot;

  public ManagerForTest(Manager dbManager, DposSlot dposSlot) {
    this.dbManager = dbManager;
    this.chainBaseManager = dbManager.getChainBaseManager();
    this.dposSlot = dposSlot;
  }

  private Map<ByteString, String> addTestExecutiveAndAccount() {
    chainBaseManager.getExecutives().clear();
    return IntStream.range(0, 2)
        .mapToObj(
            i -> {
              ECKey ecKey = new ECKey(Utils.getRandom());
              String privateKey = ByteArray.toHexString(ecKey.getPrivKey().toByteArray());
              ByteString address = ByteString.copyFrom(ecKey.getAddress());

              ExecutiveCapsule executiveCapsule = new ExecutiveCapsule(address);
              chainBaseManager.getExecutiveStore().put(address.toByteArray(), executiveCapsule);
              chainBaseManager.addExecutive(address);

              AccountCapsule accountCapsule =
                  new AccountCapsule(Account.newBuilder().setAddress(address).build());
              chainBaseManager.getAccountStore().put(address.toByteArray(), accountCapsule);

              return Maps.immutableEntry(address, privateKey);
            })
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private ByteString getExecutiveAddress(long time) {
    return dposSlot.getScheduledExecutive(dposSlot.getSlot(time));
  }

  public BlockCapsule createTestBlockCapsule(long time,
                                             long number, ByteString hash) {

    Map<ByteString, String> addressToProvateKeys = addTestExecutiveAndAccount();
    ByteString executiveAddress = getExecutiveAddress(time);

    BlockCapsule blockCapsule = new BlockCapsule(number, Sha256Hash.wrap(hash), time,
        executiveAddress);
    blockCapsule.generatedByMyself = true;
    blockCapsule.setMerkleRoot();
    blockCapsule.sign(ByteArray.fromHexString(addressToProvateKeys.get(executiveAddress)));
    return blockCapsule;
  }

  public boolean pushNTestBlock(int count) {
    try {
      for (int i = 1; i <= count; i++) {
        ByteString hash = dbManager.getDynamicPropertiesStore().getLatestBlockHeaderHash()
            .getByteString();
        long time =
            chainBaseManager.getDynamicPropertiesStore().getLatestBlockHeaderTimestamp() + 3000L;
        long number =
            chainBaseManager.getDynamicPropertiesStore().getLatestBlockHeaderNumber() + 1;
        chainBaseManager.getExecutiveScheduleStore().saveActiveExecutives(new ArrayList<>());
        BlockCapsule blockCapsule = createTestBlockCapsule(time, number, hash);
        dbManager.pushBlock(blockCapsule);
      }
    } catch (Exception ignore) {
      return false;
    }
    return true;
  }
}
