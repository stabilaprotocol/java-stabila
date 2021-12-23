package org.stabila.core.services.http;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.stabila.api.GrpcAPI;
import org.stabila.api.GrpcAPI.IvkDecryptSRC20Parameters;
import org.stabila.common.utils.ByteArray;
import org.stabila.core.Wallet;

@Component
@Slf4j(topic = "API")
public class ScanShieldedSRC20NotesByIvkServlet extends RateLimiterServlet {

  @Autowired
  private Wallet wallet;

  public static String convertOutput(GrpcAPI.DecryptNotesSRC20 notes, boolean visible) {
    String resultString = JsonFormat.printToString(notes, visible);
    if (notes.getNoteTxsCount() == 0) {
      return resultString;
    } else {
      JSONObject jsonNotes = JSONObject.parseObject(resultString);
      JSONArray array = jsonNotes.getJSONArray("noteTxs");
      for (int index = 0; index < array.size(); index++) {
        JSONObject item = array.getJSONObject(index);
        item.put("index", notes.getNoteTxs(index).getIndex()); // Avoid automatically ignoring 0
      }
      return jsonNotes.toJSONString();
    }
  }

  protected void doPost(HttpServletRequest request, HttpServletResponse response) {
    try {
      PostParams params = PostParams.getPostParams(request);
      IvkDecryptSRC20Parameters.Builder ivkDecryptSRC20Parameters = IvkDecryptSRC20Parameters
          .newBuilder();
      JsonFormat.merge(params.getParams(), ivkDecryptSRC20Parameters, params.isVisible());

      GrpcAPI.DecryptNotesSRC20 notes = wallet
          .scanShieldedSRC20NotesByIvk(ivkDecryptSRC20Parameters.getStartBlockIndex(),
              ivkDecryptSRC20Parameters.getEndBlockIndex(),
              ivkDecryptSRC20Parameters.getShieldedSRC20ContractAddress().toByteArray(),
              ivkDecryptSRC20Parameters.getIvk().toByteArray(),
              ivkDecryptSRC20Parameters.getAk().toByteArray(),
              ivkDecryptSRC20Parameters.getNk().toByteArray(),
              ivkDecryptSRC20Parameters.getEventsList());
      response.getWriter().println(convertOutput(notes, params.isVisible()));
    } catch (Exception e) {
      Util.processError(e, response);
    }
  }

  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
    try {
      boolean visible = Util.getVisible(request);
      long startNum = Long.parseLong(request.getParameter("start_block_index"));
      long endNum = Long.parseLong(request.getParameter("end_block_index"));
      String ivk = request.getParameter("ivk");

      String contractAddress = request.getParameter("shielded_SRC20_contract_address");
      if (visible) {
        contractAddress = Util.getHexAddress(contractAddress);
      }

      String ak = request.getParameter("ak");
      String nk = request.getParameter("nk");

      GrpcAPI.DecryptNotesSRC20 notes = wallet
          .scanShieldedSRC20NotesByIvk(startNum, endNum,
              ByteArray.fromHexString(contractAddress), ByteArray.fromHexString(ivk),
              ByteArray.fromHexString(ak), ByteArray.fromHexString(nk), null);
      response.getWriter().println(convertOutput(notes, visible));
    } catch (Exception e) {
      Util.processError(e, response);
    }
  }
}
