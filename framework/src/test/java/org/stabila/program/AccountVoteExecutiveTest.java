package org.stabila.program;

import com.google.common.collect.Lists;
import com.google.protobuf.ByteString;
import java.io.File;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.stabila.common.application.StabilaApplicationContext;
import org.stabila.consensus.dpos.MaintenanceManager;
import org.stabila.core.Constant;
import org.stabila.core.capsule.AccountCapsule;
import org.stabila.core.capsule.ExecutiveCapsule;
import org.stabila.core.config.DefaultConfig;
import org.stabila.core.config.args.Args;
import org.stabila.core.db.Manager;
import org.stabila.protos.Protocol.AccountType;

@Slf4j
public class AccountVoteExecutiveTest {

  private static StabilaApplicationContext context;

  private static Manager dbManager;
  private static MaintenanceManager maintenanceManager;
  private static String dbPath = "output_executive_test";

  static {
    Args.setParam(new String[]{"-d", dbPath}, Constant.TEST_CONF);
    context = new StabilaApplicationContext(DefaultConfig.class);
  }

  /**
   * init db.
   */
  @BeforeClass
  public static void init() {
    dbManager = context.getBean(Manager.class);
    maintenanceManager = context.getBean(MaintenanceManager.class);
    // Args.setParam(new String[]{}, Constant.TEST_CONF);
    //  dbManager = new Manager();
    //  dbManager.init();
  }

  /**
   * remo db when after test.
   */
  @AfterClass
  public static void removeDb() {
    Args.clearParam();
    context.destroy();
    File dbFolder = new File(dbPath);
    if (deleteFolder(dbFolder)) {
      logger.info("Release resources successful.");
    } else {
      logger.info("Release resources failure.");
    }
  }

  private static Boolean deleteFolder(File index) {
    if (!index.isDirectory() || index.listFiles().length <= 0) {
      return index.delete();
    }
    for (File file : index.listFiles()) {
      if (null != file && !deleteFolder(file)) {
        return false;
      }
    }
    return index.delete();
  }

  @Test
  public void testAccountVoteExecutive() {
    final List<AccountCapsule> accountCapsuleList = this.getAccountList();
    final List<ExecutiveCapsule> executiveCapsuleList = this.getExecutiveList();
    accountCapsuleList.forEach(
        accountCapsule -> {
          dbManager
              .getAccountStore()
              .put(accountCapsule.getAddress().toByteArray(), accountCapsule);
          this.printAccount(accountCapsule.getAddress());
        });
    executiveCapsuleList.forEach(
        executiveCapsule ->
            dbManager
                .getExecutiveStore()
                .put(executiveCapsule.getAddress().toByteArray(), executiveCapsule));
    maintenanceManager.doMaintenance();
    this.printExecutive(ByteString.copyFrom("00000000001".getBytes()));
    this.printExecutive(ByteString.copyFrom("00000000002".getBytes()));
    this.printExecutive(ByteString.copyFrom("00000000003".getBytes()));
    this.printExecutive(ByteString.copyFrom("00000000004".getBytes()));
    this.printExecutive(ByteString.copyFrom("00000000005".getBytes()));
    this.printExecutive(ByteString.copyFrom("00000000006".getBytes()));
    this.printExecutive(ByteString.copyFrom("00000000007".getBytes()));
  }

  private void printAccount(final ByteString address) {
    final AccountCapsule accountCapsule = dbManager.getAccountStore().get(address.toByteArray());
    if (null == accountCapsule) {
      logger.info("address is {}  , account is null", address.toStringUtf8());
      return;
    }
    logger.info(
        "address is {}  ,countVoteSize is {}",
        accountCapsule.getAddress().toStringUtf8(),
        accountCapsule.getVotesList().size());
  }

  private void printExecutive(final ByteString address) {
    final ExecutiveCapsule executiveCapsule = dbManager.getExecutiveStore().get(address.toByteArray());
    if (null == executiveCapsule) {
      logger.info("address is {}  , executive is null", address.toStringUtf8());
      return;
    }
    logger.info(
        "address is {}  ,countVote is {}",
        executiveCapsule.getAddress().toStringUtf8(),
        executiveCapsule.getVoteCount());
  }

  private List<AccountCapsule> getAccountList() {
    final List<AccountCapsule> accountCapsuleList = Lists.newArrayList();
    final AccountCapsule accountStabila =
        new AccountCapsule(
            ByteString.copyFrom("00000000001".getBytes()),
            ByteString.copyFromUtf8("Stabila"),
            AccountType.Normal);
    final AccountCapsule accountMarcus =
        new AccountCapsule(
            ByteString.copyFrom("00000000002".getBytes()),
            ByteString.copyFromUtf8("Marcus"),
            AccountType.Normal);
    final AccountCapsule accountOlivier =
        new AccountCapsule(
            ByteString.copyFrom("00000000003".getBytes()),
            ByteString.copyFromUtf8("Olivier"),
            AccountType.Normal);
    final AccountCapsule accountSasaXie =
        new AccountCapsule(
            ByteString.copyFrom("00000000004".getBytes()),
            ByteString.copyFromUtf8("SasaXie"),
            AccountType.Normal);
    final AccountCapsule accountVivider =
        new AccountCapsule(
            ByteString.copyFrom("00000000005".getBytes()),
            ByteString.copyFromUtf8("Vivider"),
            AccountType.Normal);
    // accountStabila addVotes
    accountStabila.addVotes(accountMarcus.getAddress(), 100);
    accountStabila.addVotes(accountOlivier.getAddress(), 100);
    accountStabila.addVotes(accountSasaXie.getAddress(), 100);
    accountStabila.addVotes(accountVivider.getAddress(), 100);

    // accountMarcus addVotes
    accountMarcus.addVotes(accountStabila.getAddress(), 100);
    accountMarcus.addVotes(accountOlivier.getAddress(), 100);
    accountMarcus.addVotes(accountSasaXie.getAddress(), 100);
    accountMarcus.addVotes(ByteString.copyFrom("00000000006".getBytes()), 100);
    accountMarcus.addVotes(ByteString.copyFrom("00000000007".getBytes()), 100);
    // accountOlivier addVotes
    accountOlivier.addVotes(accountStabila.getAddress(), 100);
    accountOlivier.addVotes(accountMarcus.getAddress(), 100);
    accountOlivier.addVotes(accountSasaXie.getAddress(), 100);
    accountOlivier.addVotes(accountVivider.getAddress(), 100);
    // accountSasaXie addVotes
    // accountVivider addVotes
    accountCapsuleList.add(accountStabila);
    accountCapsuleList.add(accountMarcus);
    accountCapsuleList.add(accountOlivier);
    accountCapsuleList.add(accountSasaXie);
    accountCapsuleList.add(accountVivider);
    return accountCapsuleList;
  }

  private List<ExecutiveCapsule> getExecutiveList() {
    final List<ExecutiveCapsule> executiveCapsuleList = Lists.newArrayList();
    final ExecutiveCapsule executiveStabila =
        new ExecutiveCapsule(ByteString.copyFrom("00000000001".getBytes()), 0, "");
    final ExecutiveCapsule executiveOlivier =
        new ExecutiveCapsule(ByteString.copyFrom("00000000003".getBytes()), 100, "");
    final ExecutiveCapsule executiveVivider =
        new ExecutiveCapsule(ByteString.copyFrom("00000000005".getBytes()), 200, "");
    final ExecutiveCapsule executiveSenaLiu =
        new ExecutiveCapsule(ByteString.copyFrom("00000000006".getBytes()), 300, "");
    executiveCapsuleList.add(executiveStabila);
    executiveCapsuleList.add(executiveOlivier);
    executiveCapsuleList.add(executiveVivider);
    executiveCapsuleList.add(executiveSenaLiu);
    return executiveCapsuleList;
  }
}
