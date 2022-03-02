package org.stabila.core.services.http;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.stabila.api.GrpcAPI;
import org.stabila.api.GrpcAPI.NfSRC20Parameters;
import org.stabila.core.Wallet;

@Component
@Slf4j(topic = "API")
public class IsShieldedSRC20ContractNoteSpentServlet extends RateLimiterServlet {

  @Autowired
  private Wallet wallet;

  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
  }

  protected void doPost(HttpServletRequest request, HttpServletResponse response) {
    try {
      PostParams params = PostParams.getPostParams(request);
      NfSRC20Parameters.Builder build = NfSRC20Parameters.newBuilder();
      JsonFormat.merge(params.getParams(), build, params.isVisible());
      GrpcAPI.NullifierResult result = wallet.isShieldedSRC20ContractNoteSpent(build.build());
      response.getWriter().println(JsonFormat.printToString(result, params.isVisible()));
    } catch (Exception e) {
      Util.processError(e, response);
    }
  }
}
