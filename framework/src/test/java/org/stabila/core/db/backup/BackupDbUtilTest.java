package org.stabila.core.db.backup;

import java.io.File;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.rocksdb.RocksDB;
import org.stabila.core.config.DefaultConfig;
import org.stabila.core.config.args.Args;
import org.stabila.core.consensus.ConsensusService;
import org.stabila.core.db.Manager;
import org.stabila.core.db2.core.Chainbase;
import org.stabila.core.db2.core.SnapshotManager;
import org.stabila.common.application.Application;
import org.stabila.common.application.ApplicationFactory;
import org.stabila.common.application.TronApplicationContext;
import org.stabila.common.parameter.CommonParameter;
import org.stabila.common.utils.FileUtil;
import org.stabila.common.utils.PropUtil;
import org.stabila.consensus.dpos.DposSlot;
import org.stabila.core.db.ManagerForTest;

@Slf4j
public class BackupDbUtilTest {

  static {
    RocksDB.loadLibrary();
  }

  public TronApplicationContext context;
  public Application AppT = null;
  public BackupDbUtil dbBackupUtil;
  public Manager dbManager;
  public ConsensusService consensusService;
  public DposSlot dposSlot;
  public ManagerForTest mngForTest;
  public String dbPath = "output-BackupDbUtilTest";

  String propPath;
  String bak1Path;
  String bak2Path;
  int frequency;

  @Before
  public void before() {
    Args.setParam(
        new String[]{
            "--output-directory", dbPath,
            "--storage-db-directory", "database",
            "--storage-index-directory", "index"
        },
        "config-test-dbbackup.conf"
    );

    context = new TronApplicationContext(DefaultConfig.class);
    AppT = ApplicationFactory.create(context);
    dbManager = context.getBean(Manager.class);
    dposSlot = context.getBean(DposSlot.class);
    consensusService = context.getBean(ConsensusService.class);
    dbBackupUtil = context.getBean(BackupDbUtil.class);
    consensusService.start();
    mngForTest = new ManagerForTest(dbManager, dposSlot);

    //prepare prop.properties
    propPath = dbPath + File.separator + "test_prop.properties";
    bak1Path = dbPath + File.separator + "bak1/database";
    bak2Path = dbPath + File.separator + "bak2/database";
    frequency = 50;
    CommonParameter parameter = Args.getInstance();
    parameter.getDbBackupConfig()
        .initArgs(true, propPath, bak1Path, bak2Path, frequency);
    FileUtil.createFileIfNotExists(propPath);
  }

  @After
  public void after() {
    context.destroy();
    if (FileUtil.deleteDir(new File(dbPath))) {
      logger.info("Release resources successful.");
    } else {
      logger.info("Release resources failure.");
    }
  }

  @Test
  public void testDoBackup() {
    PropUtil.writeProperty(propPath, BackupDbUtil.getDB_BACKUP_STATE(),
        String.valueOf("11"));
    mngForTest.pushNTestBlock(50);
    List<Chainbase> alist = ((SnapshotManager) dbBackupUtil.getDb()).getDbs();

    Assert.assertTrue(dbManager.getDynamicPropertiesStore().getLatestBlockHeaderNumber() == 50);
    Assert.assertTrue("22".equals(
        PropUtil.readProperty(propPath, BackupDbUtil.getDB_BACKUP_STATE())));

    mngForTest.pushNTestBlock(50);
    Assert.assertTrue(dbManager.getDynamicPropertiesStore().getLatestBlockHeaderNumber() == 100);
    Assert.assertTrue("11".equals(
        PropUtil.readProperty(propPath, BackupDbUtil.getDB_BACKUP_STATE())));

    mngForTest.pushNTestBlock(50);
    Assert.assertTrue(dbManager.getDynamicPropertiesStore().getLatestBlockHeaderNumber() == 150);
    Assert.assertTrue("22".equals(
        PropUtil.readProperty(propPath, BackupDbUtil.getDB_BACKUP_STATE())));

    PropUtil.writeProperty(propPath, BackupDbUtil.getDB_BACKUP_STATE(),
        String.valueOf("1"));
    mngForTest.pushNTestBlock(50);
    Assert.assertTrue(dbManager.getDynamicPropertiesStore().getLatestBlockHeaderNumber() == 200);
    Assert.assertTrue("11".equals(
        PropUtil.readProperty(propPath, BackupDbUtil.getDB_BACKUP_STATE())));

    PropUtil.writeProperty(propPath, BackupDbUtil.getDB_BACKUP_STATE(),
        String.valueOf("2"));
    mngForTest.pushNTestBlock(50);
    Assert.assertTrue(dbManager.getDynamicPropertiesStore().getLatestBlockHeaderNumber() == 250);
    Assert.assertTrue("22".equals(
        PropUtil.readProperty(propPath, BackupDbUtil.getDB_BACKUP_STATE())));
  }
}
