package org.stabila.core.zksnark;

import com.alibaba.fastjson.JSONArray;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.google.protobuf.ByteString;
import java.io.File;
import java.util.List;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.stabila.core.Wallet;
import org.stabila.core.capsule.IncrementalMerkleTreeCapsule;
import org.stabila.core.capsule.IncrementalMerkleVoucherCapsule;
import org.stabila.core.capsule.PedersenHashCapsule;
import org.stabila.core.config.DefaultConfig;
import org.stabila.core.config.args.Args;
import org.stabila.core.db.Manager;
import org.testng.collections.Lists;
import org.stabila.common.application.StabilaApplicationContext;
import org.stabila.common.utils.ByteArray;
import org.stabila.common.utils.ByteUtil;
import org.stabila.common.utils.FileUtil;
import org.stabila.common.zksnark.IncrementalMerkleTreeContainer;
import org.stabila.common.zksnark.IncrementalMerkleTreeContainer.EmptyMerkleRoots;
import org.stabila.common.zksnark.IncrementalMerkleVoucherContainer;
import org.stabila.common.zksnark.MerklePath;
import org.stabila.protos.contract.ShieldContract.PedersenHash;

public class MerkleTreeTest {

  public static final long totalBalance = 1000_0000_000_000L;
  private static String dbPath = "output_ShieldedTransaction_test";
  private static String dbDirectory = "db_ShieldedTransaction_test";
  private static String indexDirectory = "index_ShieldedTransaction_test";
  private static AnnotationConfigApplicationContext context;
  private static Manager dbManager;
  private static Wallet wallet;

  static {
    Args.setParam(
        new String[]{
            "--output-directory", dbPath,
            "--storage-db-directory", dbDirectory,
            "--storage-index-directory", indexDirectory,
            "-w",
            "--debug"
        },
        "config-test-mainnet.conf"
    );
    context = new StabilaApplicationContext(DefaultConfig.class);
  }

  /**
   * Init data.
   */
  @BeforeClass
  public static void init() {
    dbManager = context.getBean(Manager.class);
    wallet = context.getBean(Wallet.class);
    //init ucr
    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(1526647838000L);
    dbManager.getDynamicPropertiesStore().saveTotalUcrWeight(100_000L);

    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(0);
  }

  @AfterClass
  public static void removeDb() {
    Args.clearParam();
    context.destroy();
    FileUtil.deleteDir(new File(dbPath));
  }

  private JSONArray readFile(String fileName) throws Exception {
    String file1 = SendCoinShieldTest.class.getClassLoader()
        .getResource("json" + File.separator + fileName).getFile();
    List<String> readLines = Files.readLines(new File(file1),
        Charsets.UTF_8);

    JSONArray array = JSONArray
        .parseArray(readLines.stream().reduce((s, s2) -> s + s2).get());

    return array;
  }

  private String PedersenHash2String(PedersenHash hash) {
    return ByteArray.toHexString(hash.getContent().toByteArray());
  }

  @Test
  public void testComplexTreePath() throws Exception {
    IncrementalMerkleTreeContainer.setDEPTH(4);
    EmptyMerkleRoots.setEmptyMerkleRootsInstance(new EmptyMerkleRoots());

    JSONArray root_tests = readFile("merkle_roots_sapling.json");
    JSONArray path_tests = readFile("merkle_path_sapling.json");
    JSONArray commitment_tests = readFile("merkle_commitments_sapling.json");
    int path_i = 0;
    IncrementalMerkleTreeContainer tree = new IncrementalMerkleTreeCapsule()
        .toMerkleTreeContainer();
    tree.toVoucher().setDEPTH(4);
    System.out.println("tree depth is " + IncrementalMerkleVoucherContainer.getDEPTH());

    // The root of the tree at this point is expected to be the root of the
    // empty tree.
    Assert.assertEquals(PedersenHash2String(tree.root()),
        PedersenHash2String(IncrementalMerkleTreeContainer.emptyRoot()));
    try {
      tree.last();
      Assert.fail("The tree doesn't have a 'last' element added since it's blank.");
    } catch (Exception ex) {
      System.out.println(ex.getMessage());
    }
    // The tree is empty.
    Assert.assertEquals(0, tree.size());

    // We need to executive at every single point in the tree, so
    // that the consistency of the tree and the merkle paths can
    // be checked.
    List<IncrementalMerkleVoucherCapsule> executives = Lists.newArrayList();

    for (int i = 0; i < 16; i++) {
      // Executive here
      executives.add(tree.toVoucher().getVoucherCapsule());

      System.out.println("i=" + i + ", depth is: " + IncrementalMerkleVoucherContainer.getDEPTH());

      PedersenHashCapsule test_commitment = new PedersenHashCapsule();
      byte[] bytes = ByteArray.fromHexString(commitment_tests.getString(i));
      ByteUtil.reverse(bytes);
      test_commitment.setContent(ByteString.copyFrom(bytes));
      // Now append a commitment to the tree
      tree.append(test_commitment.getInstance());
      // Size incremented by one.
      Assert.assertEquals(i + 1, tree.size());
      // Last element added to the tree was `test_commitment`
      Assert.assertEquals(PedersenHash2String(test_commitment.getInstance()),
          PedersenHash2String(tree.last()));
      //todo:
      // Check tree root consistency
      Assert.assertEquals(root_tests.getString(i),
          PedersenHash2String(tree.root()));

      boolean first = true; // The first executive can never form a path
      for (IncrementalMerkleVoucherCapsule wit : executives) {
        // Append the same commitment to all the executives
        wit.toMerkleVoucherContainer().append(test_commitment.getInstance());
        if (first) {
          try {
            wit.toMerkleVoucherContainer().path();
            Assert.fail("The first executive can never form a path");
          } catch (Exception ex) {
            System.out.println(ex.getMessage());
          }
          try {
            wit.toMerkleVoucherContainer().element();
            Assert.fail("The first executive can never form a path");
          } catch (Exception ex) {
            System.out.println(ex.getMessage());
          }
        } else {
          MerklePath path = wit.toMerkleVoucherContainer().path();
          Assert.assertEquals(path_tests.getString(path_i++), ByteArray.toHexString(path.encode()));
        }
        Assert.assertEquals(
            PedersenHash2String(wit.toMerkleVoucherContainer().root()),
            PedersenHash2String(tree.root()));
        first = false;
      }
    }
    try {
      tree.append(new PedersenHashCapsule().getInstance());
      Assert.fail("Tree should be full now");
    } catch (Exception ex) {
      System.out.println(ex.getMessage());
    }
    for (IncrementalMerkleVoucherCapsule wit : executives) {
      try {
        wit.toMerkleVoucherContainer().append(new PedersenHashCapsule().getInstance());
        Assert.fail("Tree should be full now");
      } catch (Exception ex) {
        System.out.println(ex.getMessage());
      }
    }

    IncrementalMerkleTreeContainer.setDEPTH(32);
    IncrementalMerkleVoucherContainer.setDEPTH(32);
    EmptyMerkleRoots.setEmptyMerkleRootsInstance(new EmptyMerkleRoots());
  }
}
