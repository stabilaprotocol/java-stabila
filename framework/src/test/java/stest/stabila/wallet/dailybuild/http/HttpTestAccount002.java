package stest.stabila.wallet.dailybuild.http;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
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
public class HttpTestAccount002 {

  private final String testKey002 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key1");
  private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);
  ECKey ecKey1 = new ECKey(Utils.getRandom());
  byte[] cdBalanceAddress = ecKey1.getAddress();
  String cdBalanceKey = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
  ECKey ecKey2 = new ECKey(Utils.getRandom());
  byte[] receiverResourceAddress = ecKey2.getAddress();
  String receiverResourceKey = ByteArray.toHexString(ecKey2.getPrivKeyBytes());
  Long berforeBalance;
  Long afterBalance;
  Long amount = 10000000L;
  Long cdedBalance = 2000000L;
  private JSONObject responseContent;
  private HttpResponse response;
  private String httpnode = Configuration.getByPath("testng.conf").getStringList("httpnode.ip.list")
      .get(1);
  private String httpSoliditynode = Configuration.getByPath("testng.conf")
      .getStringList("httpnode.ip.list").get(2);
  private String httpPbftNode = Configuration.getByPath("testng.conf")
      .getStringList("httpnode.ip.list").get(4);

  /**
   * constructor.
   */
  @Test(enabled = true, description = "CdBalance for bandwidth by http")
  public void test001CdbalanceForBandwidth() {
    PublicMethed.printAddress(cdBalanceKey);
    //Send stb to test account
    response = HttpMethed.sendCoin(httpnode, fromAddress, cdBalanceAddress, amount, testKey002);
    Assert.assertTrue(HttpMethed.verificationResult(response));
    HttpMethed.waitToProduceOneBlock(httpnode);
    berforeBalance = HttpMethed.getBalance(httpnode, cdBalanceAddress);

    //Cd balance
    response = HttpMethed
        .cdBalance(httpnode, cdBalanceAddress, cdedBalance, 0, 0, cdBalanceKey);
    Assert.assertTrue(HttpMethed.verificationResult(response));
    HttpMethed.waitToProduceOneBlock(httpnode);
    afterBalance = HttpMethed.getBalance(httpnode, cdBalanceAddress);
    Assert.assertTrue(berforeBalance - afterBalance == cdedBalance);
  }

  /**
   * constructor.
   */
  @Test(enabled = true, description = "UnCdBalance for bandwidth by http")
  public void test002UnCdbalanceForBandwidth() {
    berforeBalance = HttpMethed.getBalance(httpnode, cdBalanceAddress);

    //UnCd balance for bandwidth
    response = HttpMethed.unCdBalance(httpnode, cdBalanceAddress, 0, cdBalanceKey);
    Assert.assertTrue(HttpMethed.verificationResult(response));
    HttpMethed.waitToProduceOneBlock(httpnode);
    afterBalance = HttpMethed.getBalance(httpnode, cdBalanceAddress);
    Assert.assertTrue(afterBalance - berforeBalance == cdedBalance);
  }

  /**
   * constructor.
   */
  @Test(enabled = true, description = "CdBalance for ucr by http")
  public void test003CdbalanceForUcr() {
    berforeBalance = HttpMethed.getBalance(httpnode, cdBalanceAddress);

    //Cd balance for ucr
    response = HttpMethed
        .cdBalance(httpnode, cdBalanceAddress, cdedBalance, 0, 1, cdBalanceKey);
    Assert.assertTrue(HttpMethed.verificationResult(response));
    HttpMethed.waitToProduceOneBlock(httpnode);
    afterBalance = HttpMethed.getBalance(httpnode, cdBalanceAddress);
    Assert.assertTrue(berforeBalance - afterBalance == cdedBalance);
  }

  /**
   * constructor.
   */
  @Test(enabled = true, description = "UnCdBalance for ucr by http")
  public void test004UnCdbalanceForUcr() {

    berforeBalance = HttpMethed.getBalance(httpnode, cdBalanceAddress);
    HttpMethed.waitToProduceOneBlock(httpnode);
    //UnCd balance for ucr
    response = HttpMethed.unCdBalance(httpnode, cdBalanceAddress, 1, cdBalanceKey);
    Assert.assertTrue(HttpMethed.verificationResult(response));
    HttpMethed.waitToProduceOneBlock(httpnode);
    afterBalance = HttpMethed.getBalance(httpnode, cdBalanceAddress);
    Assert.assertTrue(afterBalance - berforeBalance == cdedBalance);
  }

  /**
   * constructor.
   */
  @Test(enabled = true, description = "CdBalance with bandwidth for others by http")
  public void test005CdbalanceOfBandwidthForOthers() {
    response = HttpMethed
        .sendCoin(httpnode, fromAddress, receiverResourceAddress, amount, testKey002);
    Assert.assertTrue(HttpMethed.verificationResult(response));
    HttpMethed.waitToProduceOneBlock(httpnode);
    berforeBalance = HttpMethed.getBalance(httpnode, cdBalanceAddress);

    //Cd balance with bandwidth for others
    response = HttpMethed
        .cdBalance(httpnode, cdBalanceAddress, cdedBalance, 0, 0, receiverResourceAddress,
            cdBalanceKey);
    Assert.assertTrue(HttpMethed.verificationResult(response));
    HttpMethed.waitToProduceOneBlock(httpnode);
    afterBalance = HttpMethed.getBalance(httpnode, cdBalanceAddress);
    Assert.assertTrue(berforeBalance - afterBalance == cdedBalance);
  }

  /**
   * constructor.
   */
  @Test(enabled = true, description = "Get Delegated Resource by http")
  public void test006GetDelegatedResource() {
    response = HttpMethed
        .getDelegatedResource(httpnode, cdBalanceAddress, receiverResourceAddress);
    responseContent = HttpMethed.parseResponseContent(response);
    HttpMethed.printJsonContent(responseContent);
    JSONArray jsonArray = JSONArray.parseArray(responseContent.get("delegatedResource").toString());
    Assert.assertTrue(jsonArray.size() >= 1);
    Assert.assertEquals(jsonArray.getJSONObject(0).getString("from"),
        ByteArray.toHexString(cdBalanceAddress));
    Assert.assertEquals(jsonArray.getJSONObject(0).getString("to"),
        ByteArray.toHexString(receiverResourceAddress));
    Assert.assertEquals(jsonArray.getJSONObject(0).getLong("cded_balance_for_bandwidth"),
        cdedBalance);
  }

  /**
   * constructor.
   */
  @Test(enabled = true, description = "Get Delegated Resource from solidity by http")
  public void test007GetDelegatedResourceFromSolidity() {
    HttpMethed.waitToProduceOneBlockFromSolidity(httpnode, httpSoliditynode);
    HttpMethed.waitToProduceOneBlockFromPbft(httpnode, httpPbftNode);
    response = HttpMethed.getDelegatedResourceFromSolidity(httpSoliditynode, cdBalanceAddress,
        receiverResourceAddress);
    responseContent = HttpMethed.parseResponseContent(response);
    HttpMethed.printJsonContent(responseContent);
    JSONArray jsonArray = JSONArray.parseArray(responseContent.get("delegatedResource").toString());
    Assert.assertTrue(jsonArray.size() >= 1);
    Assert.assertEquals(jsonArray.getJSONObject(0).getString("from"),
        ByteArray.toHexString(cdBalanceAddress));
    Assert.assertEquals(jsonArray.getJSONObject(0).getString("to"),
        ByteArray.toHexString(receiverResourceAddress));
    Assert.assertEquals(jsonArray.getJSONObject(0).getLong("cded_balance_for_bandwidth"),
        cdedBalance);
  }

  /**
   * constructor.
   */
  @Test(enabled = true, description = "Get Delegated Resource from PBFT by http")
  public void test008GetDelegatedResourceFromPbft() {
    HttpMethed.waitToProduceOneBlockFromPbft(httpnode, httpPbftNode);
    response = HttpMethed
        .getDelegatedResourceFromPbft(httpPbftNode, cdBalanceAddress, receiverResourceAddress);
    responseContent = HttpMethed.parseResponseContent(response);
    HttpMethed.printJsonContent(responseContent);
    JSONArray jsonArray = JSONArray.parseArray(responseContent.get("delegatedResource").toString());
    Assert.assertTrue(jsonArray.size() >= 1);
    Assert.assertEquals(jsonArray.getJSONObject(0).getString("from"),
        ByteArray.toHexString(cdBalanceAddress));
    Assert.assertEquals(jsonArray.getJSONObject(0).getString("to"),
        ByteArray.toHexString(receiverResourceAddress));
    Assert.assertEquals(jsonArray.getJSONObject(0).getLong("cded_balance_for_bandwidth"),
        cdedBalance);
  }


  /**
   * constructor.
   */
  @Test(enabled = true, description = "Get Delegated Resource Account Index by http")
  public void test009GetDelegatedResourceAccountIndex() {
    response = HttpMethed.getDelegatedResourceAccountIndex(httpnode, cdBalanceAddress);
    responseContent = HttpMethed.parseResponseContent(response);
    HttpMethed.printJsonContent(responseContent);
    Assert.assertFalse(responseContent.get("toAccounts").toString().isEmpty());
    String toAddress = responseContent.getJSONArray("toAccounts").get(0).toString();
    Assert.assertEquals(toAddress, ByteArray.toHexString(receiverResourceAddress));
  }

  /**
   * constructor.
   */
  @Test(enabled = true, description = "Get Delegated Resource Account Index from solidity by http")
  public void test010GetDelegatedResourceAccountIndexFromSolidity() {
    response = HttpMethed
        .getDelegatedResourceAccountIndexFromSolidity(httpSoliditynode, cdBalanceAddress);
    responseContent = HttpMethed.parseResponseContent(response);
    HttpMethed.printJsonContent(responseContent);
    Assert.assertFalse(responseContent.get("toAccounts").toString().isEmpty());
    String toAddress = responseContent.getJSONArray("toAccounts").get(0).toString();
    Assert.assertEquals(toAddress, ByteArray.toHexString(receiverResourceAddress));
  }

  /**
   * constructor.
   */
  @Test(enabled = true, description = "Get Delegated Resource Account Index from PBFT by http")
  public void test011GetDelegatedResourceAccountIndexFromPbft() {
    response = HttpMethed
        .getDelegatedResourceAccountIndexFromPbft(httpPbftNode, cdBalanceAddress);
    responseContent = HttpMethed.parseResponseContent(response);
    HttpMethed.printJsonContent(responseContent);
    Assert.assertFalse(responseContent.get("toAccounts").toString().isEmpty());
    String toAddress = responseContent.getJSONArray("toAccounts").get(0).toString();
    Assert.assertEquals(toAddress, ByteArray.toHexString(receiverResourceAddress));
  }


  /**
   * constructor.
   */
  @Test(enabled = true, description = "UnCdBalance with bandwidth for others by http")
  public void test012UnCdbalanceOfBandwidthForOthers() {
    HttpMethed.waitToProduceOneBlock(httpnode);
    berforeBalance = HttpMethed.getBalance(httpnode, cdBalanceAddress);

    //UnCd balance with bandwidth for others
    response = HttpMethed
        .unCdBalance(httpnode, cdBalanceAddress, 0, receiverResourceAddress,
            cdBalanceKey);
    Assert.assertTrue(HttpMethed.verificationResult(response));
    HttpMethed.waitToProduceOneBlock(httpnode);
    afterBalance = HttpMethed.getBalance(httpnode, cdBalanceAddress);
    Assert.assertTrue(afterBalance - berforeBalance == cdedBalance);
  }

  /**
   * constructor.
   */
  @Test(enabled = true, description = "CdBalance with ucr for others by http")
  public void test013CdbalanceOfUcrForOthers() {
    response = HttpMethed
        .sendCoin(httpnode, fromAddress, receiverResourceAddress, amount, testKey002);
    Assert.assertTrue(HttpMethed.verificationResult(response));
    HttpMethed.waitToProduceOneBlock(httpnode);
    berforeBalance = HttpMethed.getBalance(httpnode, cdBalanceAddress);

    //Cd balance with ucr for others
    response = HttpMethed
        .cdBalance(httpnode, cdBalanceAddress, cdedBalance, 0, 1, receiverResourceAddress,
            cdBalanceKey);
    Assert.assertTrue(HttpMethed.verificationResult(response));
    HttpMethed.waitToProduceOneBlock(httpnode);
    afterBalance = HttpMethed.getBalance(httpnode, cdBalanceAddress);
    Assert.assertTrue(berforeBalance - afterBalance == cdedBalance);
  }

  /**
   * constructor.
   */
  @Test(enabled = true, description = "UnCdBalance with ucr for others by http")
  public void test014UnCdbalanceOfUcrForOthers() {
    berforeBalance = HttpMethed.getBalance(httpnode, cdBalanceAddress);

    //UnCd balance with ucr for others
    response = HttpMethed
        .unCdBalance(httpnode, cdBalanceAddress, 1, receiverResourceAddress,
            cdBalanceKey);
    Assert.assertTrue(HttpMethed.verificationResult(response));
    HttpMethed.waitToProduceOneBlock(httpnode);
    afterBalance = HttpMethed.getBalance(httpnode, cdBalanceAddress);
    Assert.assertTrue(afterBalance - berforeBalance == cdedBalance);
  }

  /**
   * constructor.
   */
  @Test(enabled = true, description = "CdBlance for stabila power by http")
  public void test015CdStabilaPower() {
    HttpMethed.waitToProduceOneBlock(httpnode);
    berforeBalance = HttpMethed.getBalance(httpnode, cdBalanceAddress);

    response = HttpMethed
        .cdBalance(httpnode, cdBalanceAddress, cdedBalance, 0, 2, null,
            cdBalanceKey);
    Assert.assertTrue(HttpMethed.verificationResult(response));
    HttpMethed.waitToProduceOneBlock(httpnode);
    afterBalance = HttpMethed.getBalance(httpnode, cdBalanceAddress);
    Assert.assertTrue(berforeBalance - afterBalance == cdedBalance);
  }

  /**
   * constructor.
   */
  @Test(enabled = true, description = "UnCdBalance for stabila power by http")
  public void test016UnCdBalanceForStabilaPower() {
    berforeBalance = HttpMethed.getBalance(httpnode, cdBalanceAddress);

    //UnCd balance with ucr for others
    response = HttpMethed
        .unCdBalance(httpnode, cdBalanceAddress, 2, null,
            cdBalanceKey);
    Assert.assertTrue(HttpMethed.verificationResult(response));
    HttpMethed.waitToProduceOneBlock(httpnode);
    afterBalance = HttpMethed.getBalance(httpnode, cdBalanceAddress);
    Assert.assertTrue(afterBalance - berforeBalance == cdedBalance);
  }




  /**
   * constructor.
   */
  @AfterClass
  public void shutdown() throws InterruptedException {
    HttpMethed.freedResource(httpnode, cdBalanceAddress, fromAddress, cdBalanceKey);
    HttpMethed.disConnect();
  }
}