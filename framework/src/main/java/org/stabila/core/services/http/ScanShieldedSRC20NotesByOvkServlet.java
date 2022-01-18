package org.stabila.core.services.http;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.stabila.api.GrpcAPI;
import org.stabila.api.GrpcAPI.OvkDecryptSRC20Parameters;
import org.stabila.common.utils.ByteArray;
import org.stabila.core.Wallet;

@Component
@Slf4j(topic = "API")
public class ScanShieldedSRC20NotesByOvkServlet extends RateLimiterServlet {

  @Autowired
  private Wallet wallet;

  protected void doPost(HttpServletRequest request, HttpServletResponse response) {
    try {
      PostParams params = PostParams.getPostParams(request);
      OvkDecryptSRC20Parameters.Builder ovkDecryptSRC20Parameters = OvkDecryptSRC20Parameters
          .newBuilder();
      JsonFormat.merge(params.getParams(), ovkDecryptSRC20Parameters, params.isVisible());

      GrpcAPI.DecryptNotesSRC20 notes = wallet
          .scanShieldedSRC20NotesByOvk(ovkDecryptSRC20Parameters.getStartBlockIndex(),
              ovkDecryptSRC20Parameters.getEndBlockIndex(),
              ovkDecryptSRC20Parameters.getOvk().toByteArray(),
              ovkDecryptSRC20Parameters.getShieldedSRC20ContractAddress().toByteArray(),
              ovkDecryptSRC20Parameters.getEventsList()
          );
      response.getWriter()
          .println(ScanShieldedSRC20NotesByIvkServlet.convertOutput(notes, params.isVisible()));
    } catch (Exception e) {
      Util.processError(e, response);
    }
  }

  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
    try {
      boolean visible = Util.getVisible(request);
      long startBlockIndex = Long.parseLong(request.getParameter("start_block_index"));
      long endBlockIndex = Long.parseLong(request.getParameter("end_block_index"));
      String ovk = request.getParameter("ovk");
      String contractAddress = request.getParameter("shielded_SRC20_contract_address");
      if (visible) {
        contractAddress = Util.getHexAddress(contractAddress);
      }
      GrpcAPI.DecryptNotesSRC20 notes = wallet
          .scanShieldedSRC20NotesByOvk(startBlockIndex, endBlockIndex,
              ByteArray.fromHexString(ovk), ByteArray.fromHexString(contractAddress), null);

      response.getWriter()
          .println(ScanShieldedSRC20NotesByIvkServlet.convertOutput(notes, visible));
    } catch (Exception e) {
      Util.processError(e, response);
    }
  }
}
