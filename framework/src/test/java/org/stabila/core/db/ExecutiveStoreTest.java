package org.stabila.core.db;

import com.google.protobuf.ByteString;
import java.io.File;
import lombok.extern.slf4j.Slf4j;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.stabila.core.Constant;
import org.stabila.core.capsule.ExecutiveCapsule;
import org.stabila.core.config.DefaultConfig;
import org.stabila.core.config.args.Args;
import org.stabila.core.store.ExecutiveStore;
import org.stabila.common.application.StabilaApplicationContext;
import org.stabila.common.utils.FileUtil;

@Slf4j
public class ExecutiveStoreTest {

  private static final String dbPath = "output-executiveStore-test";
  private static StabilaApplicationContext context;

  static {
    Args.setParam(new String[]{"-d", dbPath}, Constant.TEST_CONF);
    context = new StabilaApplicationContext(DefaultConfig.class);
  }

  ExecutiveStore executiveStore;

  @AfterClass
  public static void destroy() {
    Args.clearParam();
    context.destroy();
    FileUtil.deleteDir(new File(dbPath));
  }

  @Before
  public void initDb() {
    this.executiveStore = context.getBean(ExecutiveStore.class);
  }

  @Test
  public void putAndGetExecutive() {
    ExecutiveCapsule executiveCapsule = new ExecutiveCapsule(ByteString.copyFromUtf8("100000000x"), 100L,
        "");

    this.executiveStore.put(executiveCapsule.getAddress().toByteArray(), executiveCapsule);
    ExecutiveCapsule executiveSource = this.executiveStore
        .get(ByteString.copyFromUtf8("100000000x").toByteArray());
    Assert.assertEquals(executiveCapsule.getAddress(), executiveSource.getAddress());
    Assert.assertEquals(executiveCapsule.getVoteCount(), executiveSource.getVoteCount());

    Assert.assertEquals(ByteString.copyFromUtf8("100000000x"), executiveSource.getAddress());
    Assert.assertEquals(100L, executiveSource.getVoteCount());

    executiveCapsule = new ExecutiveCapsule(ByteString.copyFromUtf8(""), 100L, "");

    this.executiveStore.put(executiveCapsule.getAddress().toByteArray(), executiveCapsule);
    executiveSource = this.executiveStore.get(ByteString.copyFromUtf8("").toByteArray());
    Assert.assertEquals(executiveCapsule.getAddress(), executiveSource.getAddress());
    Assert.assertEquals(executiveCapsule.getVoteCount(), executiveSource.getVoteCount());

    Assert.assertEquals(ByteString.copyFromUtf8(""), executiveSource.getAddress());
    Assert.assertEquals(100L, executiveSource.getVoteCount());
  }


}