package org.stabila.core.services.http;

import com.alibaba.fastjson.JSONObject;
import com.google.protobuf.ByteString;
import io.netty.util.internal.StringUtil;
import java.io.IOException;
import java.security.InvalidParameterException;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.stabila.api.GrpcAPI.Return;
import org.stabila.api.GrpcAPI.Return.response_code;
import org.stabila.api.GrpcAPI.TransactionExtention;
import org.stabila.common.utils.ByteArray;
import org.stabila.core.Wallet;
import org.stabila.core.capsule.TransactionCapsule;
import org.stabila.core.exception.ContractValidateException;
import org.stabila.protos.Protocol.Transaction;
import org.stabila.protos.Protocol.Transaction.Contract.ContractType;
import org.stabila.protos.contract.SmartContractOuterClass.TriggerSmartContract;


@Component
@Slf4j(topic = "API")
public class TriggerConstantContractServlet extends RateLimiterServlet {

  private final String functionSelector = "function_selector";

  @Autowired
  private Wallet wallet;

  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
  }

  protected void validateParameter(String contract) {
    JSONObject jsonObject = JSONObject.parseObject(contract);
    if (!jsonObject.containsKey("owner_address")
        || StringUtil.isNullOrEmpty(jsonObject.getString("owner_address"))) {
      throw new InvalidParameterException("owner_address isn't set.");
    }
    if (!jsonObject.containsKey("contract_address")
        || StringUtil.isNullOrEmpty(jsonObject.getString("contract_address"))) {
      throw new InvalidParameterException("contract_address isn't set.");
    }
  }

  protected void doPost(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    TriggerSmartContract.Builder build = TriggerSmartContract.newBuilder();
    TransactionExtention.Builder stbExtBuilder = TransactionExtention.newBuilder();
    Return.Builder retBuilder = Return.newBuilder();
    boolean visible = false;
    try {
      String contract = request.getReader().lines()
          .collect(Collectors.joining(System.lineSeparator()));
      Util.checkBodySize(contract);
      visible = Util.getVisiblePost(contract);
      validateParameter(contract);
      JsonFormat.merge(contract, build, visible);
      JSONObject jsonObject = JSONObject.parseObject(contract);

      boolean isFunctionSelectorSet = jsonObject.containsKey(functionSelector)
          && !StringUtil.isNullOrEmpty(jsonObject.getString(functionSelector));
      String data;
      if (isFunctionSelectorSet) {
        String selector = jsonObject.getString(functionSelector);
        String parameter = jsonObject.getString("parameter");
        data = Util.parseMethod(selector, parameter);
        build.setData(ByteString.copyFrom(ByteArray.fromHexString(data)));
      } else {
        build.setData(ByteString.copyFrom(new byte[0]));
      }
      long feeLimit = Util.getJsonLongValue(jsonObject, "fee_limit");

      TransactionCapsule stbCap = wallet
          .createTransactionCapsule(build.build(), ContractType.TriggerSmartContract);

      Transaction.Builder txBuilder = stbCap.getInstance().toBuilder();
      Transaction.raw.Builder rawBuilder = stbCap.getInstance().getRawData().toBuilder();
      rawBuilder.setFeeLimit(feeLimit);
      txBuilder.setRawData(rawBuilder);

      Transaction stb = wallet
          .triggerConstantContract(build.build(), new TransactionCapsule(txBuilder.build()),
              stbExtBuilder,
              retBuilder);
      stb = Util.setTransactionPermissionId(jsonObject, stb);
      stb = Util.setTransactionExtraData(jsonObject, stb, visible);
      stbExtBuilder.setTransaction(stb);
      retBuilder.setResult(true).setCode(response_code.SUCCESS);
    } catch (ContractValidateException e) {
      retBuilder.setResult(false).setCode(response_code.CONTRACT_VALIDATE_ERROR)
          .setMessage(ByteString.copyFromUtf8(e.getMessage()));
    } catch (Exception e) {
      String errString = null;
      if (e.getMessage() != null) {
        errString = e.getMessage().replaceAll("[\"]", "\'");
      }
      retBuilder.setResult(false).setCode(response_code.OTHER_ERROR)
          .setMessage(ByteString.copyFromUtf8(e.getClass() + " : " + errString));
    }
    stbExtBuilder.setResult(retBuilder);
    response.getWriter().println(Util.printTransactionExtention(stbExtBuilder.build(), visible));
  }
}
