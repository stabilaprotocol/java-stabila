package org.stabila.core.services.http;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.stabila.core.Wallet;
import org.stabila.api.GrpcAPI;
import org.stabila.api.GrpcAPI.OvkDecryptParameters;
import org.stabila.common.utils.ByteArray;

@Component
@Slf4j(topic = "API")
public class ScanNoteByOvkServlet extends RateLimiterServlet {

  @Autowired
  private Wallet wallet;

  protected void doPost(HttpServletRequest request, HttpServletResponse response) {
    try {
      PostParams params = PostParams.getPostParams(request);

      OvkDecryptParameters.Builder ovkDecryptParameters = OvkDecryptParameters.newBuilder();
      JsonFormat.merge(params.getParams(), ovkDecryptParameters);

      GrpcAPI.DecryptNotes notes = wallet
          .scanNoteByOvk(ovkDecryptParameters.getStartBlockIndex(),
              ovkDecryptParameters.getEndBlockIndex(),
              ovkDecryptParameters.getOvk().toByteArray());
      response.getWriter().println(ScanNoteByIvkServlet.convertOutput(notes, params.isVisible()));
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
      GrpcAPI.DecryptNotes notes = wallet
          .scanNoteByOvk(startBlockIndex, endBlockIndex, ByteArray.fromHexString(ovk));
      response.getWriter().println(ScanNoteByIvkServlet.convertOutput(notes, visible));
    } catch (Exception e) {
      Util.processError(e, response);
    }
  }
}
