package stest.stabila.wallet.dailybuild.http;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpResponse;
import org.junit.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;
import org.stabila.common.crypto.ECKey;
import org.stabila.common.utils.ByteArray;
import org.stabila.common.utils.Utils;
import stest.stabila.wallet.common.client.Configuration;
import stest.stabila.wallet.common.client.utils.HttpMethed;
import stest.stabila.wallet.common.client.utils.PublicMethed;

@Slf4j
public class HttpTestAccount003 {

  private static String updateAccountName =
      "updateAccount_" + System.currentTimeMillis();
  private static String updateUrl =
      "http://www.update.url" + System.currentTimeMillis();
  private final String testKey002 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key1");
  private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);
  private final String executiveKey001 = Configuration.getByPath("testng.conf")
      .getString("executive.key1");
  private final byte[] executive1Address = PublicMethed.getFinalAddress(executiveKey001);
  private final String executiveKey002 = Configuration.getByPath("testng.conf")
      .getString("executive.key2");
  private final byte[] executive2Address = PublicMethed.getFinalAddress(executiveKey002);
  private final Long createExecutiveAmount = Configuration.getByPath("testng.conf")
      .getLong("defaultParameter.createExecutiveAmount");
  ECKey ecKey1 = new ECKey(Utils.getRandom());
  byte[] newAccountAddress = ecKey1.getAddress();
  String newAccountKey = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
  ECKey ecKey2 = new ECKey(Utils.getRandom());
  byte[] updateAccountAddress = ecKey2.getAddress();
  String updateAccountKey = ByteArray.toHexString(ecKey2.getPrivKeyBytes());
  Long amount = 50000000L;
  JsonArray voteKeys = new JsonArray();
  JsonObject voteElement = new JsonObject();
  private JSONObject responseContent;
  private HttpResponse response;
  private String httpnode = Configuration.getByPath("testng.conf").getStringList("httpnode.ip.list")
      .get(0);
  private String httpSoliditynode = Configuration.getByPath("testng.conf")
      .getStringList("httpnode.ip.list").get(2);
  private String httpPbftNode = Configuration.getByPath("testng.conf")
      .getStringList("httpnode.ip.list").get(4);

  /**
   * constructor.
   */
  @Test(enabled = true, description = "Update account by http")
  public void test01UpdateAccount() {
    response = HttpMethed.sendCoin(httpnode, fromAddress, updateAccountAddress, amount, testKey002);
    Assert.assertTrue(HttpMethed.verificationResult(response));
    HttpMethed.waitToProduceOneBlock(httpnode);

    response = HttpMethed
        .updateAccount(httpnode, updateAccountAddress, updateAccountName, updateAccountKey);
    Assert.assertTrue(HttpMethed.verificationResult(response));
    HttpMethed.waitToProduceOneBlock(httpnode);

    response = HttpMethed.getAccount(httpnode, updateAccountAddress);
    responseContent = HttpMethed.parseResponseContent(response);
    HttpMethed.printJsonContent(responseContent);
    Assert.assertTrue(responseContent.getString("account_name")
        .equalsIgnoreCase(HttpMethed.str2hex(updateAccountName)));

    Assert.assertFalse(responseContent.getString("active_permission").isEmpty());
  }

  /**
   * constructor.
   */
  @Test(enabled = true, description = "Vote executive account by http")
  public void test02VoteExecutiveAccount() {
    //Cd balance
    response = HttpMethed
        .cdBalance(httpnode, updateAccountAddress, 40000000L, 0, 2, updateAccountKey);
    responseContent = HttpMethed.parseResponseContent(response);
    Assert.assertTrue(HttpMethed.verificationResult(response));
    HttpMethed.printJsonContent(responseContent);
    HttpMethed.waitToProduceOneBlock(httpnode);
    voteElement.addProperty("vote_address", ByteArray.toHexString(executive1Address));
    voteElement.addProperty("vote_count", 11);
    voteKeys.add(voteElement);

    voteElement.remove("vote_address");
    voteElement.remove("vote_count");
    voteElement.addProperty("vote_address", ByteArray.toHexString(executive2Address));
    voteElement.addProperty("vote_count", 12);
    voteKeys.add(voteElement);

    response = HttpMethed
        .voteExecutiveAccount(httpnode, updateAccountAddress, voteKeys, updateAccountKey);
    Assert.assertTrue(HttpMethed.verificationResult(response));
    HttpMethed.waitToProduceOneBlock(httpnode);
    response = HttpMethed.getAccount(httpnode, updateAccountAddress);
    responseContent = HttpMethed.parseResponseContent(response);
    HttpMethed.printJsonContent(responseContent);
    Assert.assertTrue(!responseContent.getString("votes").isEmpty());
  }

  /**
   * constructor.
   */
  @Test(enabled = true, description = "List executives by http")
  public void test03ListExecutive() {
    response = HttpMethed.listexecutives(httpnode);
    responseContent = HttpMethed.parseResponseContent(response);
    HttpMethed.printJsonContent(responseContent);
    JSONArray jsonArray = JSONArray.parseArray(responseContent.getString("executives"));
    Assert.assertTrue(jsonArray.size() >= 2);
  }

  /**
   * constructor.
   */
  @Test(enabled = true, description = "List executives from solidity by http")
  public void test04ListExecutiveFromSolidity() {
    HttpMethed.waitToProduceOneBlockFromSolidity(httpnode, httpSoliditynode);
    response = HttpMethed.listexecutivesFromSolidity(httpSoliditynode);
    responseContent = HttpMethed.parseResponseContent(response);
    HttpMethed.printJsonContent(responseContent);
    JSONArray jsonArray = JSONArray.parseArray(responseContent.getString("executives"));
    Assert.assertTrue(jsonArray.size() >= 2);
  }

  /**
   * constructor.
   */
  @Test(enabled = true, description = "List executives from PBFT by http")
  public void test05ListExecutiveFromPbft() {
    HttpMethed.waitToProduceOneBlockFromSolidity(httpnode, httpSoliditynode);
    response = HttpMethed.listexecutivesFromPbft(httpPbftNode);
    responseContent = HttpMethed.parseResponseContent(response);
    HttpMethed.printJsonContent(responseContent);
    JSONArray jsonArray = JSONArray.parseArray(responseContent.getString("executives"));
    Assert.assertTrue(jsonArray.size() >= 2);
  }


  /**
   * constructor.
   */
  @Test(enabled = true, description = "Update executive by http")
  public void test06UpdateExecutive() {
    response = HttpMethed.updateExecutive(httpnode, executive1Address, updateUrl, executiveKey001);
    Assert.assertTrue(HttpMethed.verificationResult(response));
    HttpMethed.waitToProduceOneBlock(httpnode);

    response = HttpMethed.listexecutives(httpnode);
    responseContent = HttpMethed.parseResponseContent(response);
    HttpMethed.printJsonContent(responseContent);
    Assert.assertTrue(responseContent.getString("executives").indexOf(updateUrl) != -1);
    //logger.info("result is " + responseContent.getString("executives").indexOf(updateUrl));
  }

  /**
   * constructor.
   */
  @Test(enabled = true, description = "Create account by http")
  public void test07CreateAccount() {
    PublicMethed.printAddress(newAccountKey);
    response = HttpMethed.createAccount(httpnode, fromAddress, newAccountAddress, testKey002);
    Assert.assertTrue(HttpMethed.verificationResult(response));
    HttpMethed.waitToProduceOneBlock(httpnode);
    response = HttpMethed.getAccount(httpnode, newAccountAddress);
    responseContent = HttpMethed.parseResponseContent(response);
    HttpMethed.printJsonContent(responseContent);
    Assert.assertTrue(responseContent.getLong("create_time") > 3);
  }

  /**
   * constructor.
   */
  @Test(enabled = true, description = "Create executive by http")
  public void test08CreateExecutive() {
    response = HttpMethed
        .sendCoin(httpnode, fromAddress, newAccountAddress, createExecutiveAmount, testKey002);
    Assert.assertTrue(HttpMethed.verificationResult(response));
    HttpMethed.waitToProduceOneBlock(httpnode);
    PublicMethed.printAddress(newAccountKey);

    response = HttpMethed.createExecutive(httpnode, newAccountAddress, updateUrl);
    responseContent = HttpMethed.parseResponseContent(response);
    HttpMethed.printJsonContent(responseContent);
    Assert.assertTrue(!responseContent.getString("txID").isEmpty());
  }

  /**
   * constructor.
   */
  @Test(enabled = true, description = "Withdraw by http")
  public void test09Withdraw() {
    response = HttpMethed.withdrawBalance(httpnode, executive1Address);
    responseContent = HttpMethed.parseResponseContent(response);
    HttpMethed.printJsonContent(responseContent);
    Assert
        .assertTrue(responseContent.getString("Error").indexOf("is a guard representative") != -1);
  }

  /**
   * constructor.
   */
  @Test(enabled = true, description = "Uncd balance for stabila power by http")
  public void test10UncdStabilaPower() {
    response = HttpMethed.unCdBalance(httpnode, updateAccountAddress,2,updateAccountKey);
    responseContent = HttpMethed.parseResponseContent(response);
    HttpMethed.printJsonContent(responseContent);


  }


  /**
   * constructor.
   */
  @AfterClass
  public void shutdown() throws InterruptedException {
    HttpMethed.freedResource(httpnode, updateAccountAddress, fromAddress, updateAccountKey);
    HttpMethed.disConnect();
  }
}