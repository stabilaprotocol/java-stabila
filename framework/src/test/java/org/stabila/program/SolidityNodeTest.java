package org.stabila.program;

import java.io.File;
import lombok.extern.slf4j.Slf4j;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.stabila.common.application.Application;
import org.stabila.common.application.ApplicationFactory;
import org.stabila.common.application.StabilaApplicationContext;
import org.stabila.common.overlay.client.DatabaseGrpcClient;
import org.stabila.core.Constant;
import org.stabila.core.config.DefaultConfig;
import org.stabila.core.config.args.Args;
import org.stabila.core.services.RpcApiService;
import org.stabila.protos.Protocol.Block;
import org.stabila.protos.Protocol.DynamicProperties;

@Slf4j
public class SolidityNodeTest {

  private static StabilaApplicationContext context;

  private static RpcApiService rpcApiService;
  private static Application appT;
  private static String dbPath = "output_executive_test";

  static {
    Args.setParam(new String[]{"-d", dbPath}, Constant.TEST_CONF);
    context = new StabilaApplicationContext(DefaultConfig.class);
    Args.getInstance().setSolidityNode(true);
    appT = ApplicationFactory.create(context);
    rpcApiService = context.getBean(RpcApiService.class);
  }

  /**
   * init db.
   */
  @BeforeClass
  public static void init() {
    rpcApiService.start();
  }

  /**
   * remo db when after test.
   */
  @AfterClass
  public static void removeDb() {
    Args.clearParam();
    rpcApiService.stop();
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
  public void testSolidityArgs() {
    Assert.assertNotNull(Args.getInstance().getTrustNodeAddr());
    Assert.assertTrue(Args.getInstance().isSolidityNode());
  }

  @Test
  public void testSolidityGrpcCall() {
    DatabaseGrpcClient databaseGrpcClient = null;
    String addr = Args.getInstance().getTrustNodeAddr();
    try {
      databaseGrpcClient = new DatabaseGrpcClient(addr);
    } catch (Exception e) {
      logger.error("Failed to create database grpc client {}", addr);
    }

    Assert.assertNotNull(databaseGrpcClient);
    DynamicProperties dynamicProperties = databaseGrpcClient.getDynamicProperties();
    Assert.assertNotNull(dynamicProperties);

    Block genesisBlock = databaseGrpcClient.getBlock(0);
    Assert.assertNotNull(genesisBlock);
    Assert.assertFalse(genesisBlock.getTransactionsList().isEmpty());
  }

}
