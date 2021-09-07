package org.stabila.core.services.http;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.stabila.core.Wallet;
import org.stabila.api.GrpcAPI.PrivateShieldedTRC20ParametersWithoutAsk;
import org.stabila.api.GrpcAPI.ShieldedTRC20Parameters;

@Component
@Slf4j(topic = "API")
public class CreateShieldedContractParametersWithoutAskServlet extends RateLimiterServlet {

  @Autowired
  private Wallet wallet;

  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
  }

  protected void doPost(HttpServletRequest request, HttpServletResponse response) {
    try {
      PostParams params = PostParams.getPostParams(request);
      PrivateShieldedTRC20ParametersWithoutAsk.Builder build =
          PrivateShieldedTRC20ParametersWithoutAsk.newBuilder();
      JsonFormat.merge(params.getParams(), build, params.isVisible());
      ShieldedTRC20Parameters shieldedTRC20Parameters = wallet
          .createShieldedContractParametersWithoutAsk(build.build());
      response.getWriter().println(JsonFormat
              .printToString(shieldedTRC20Parameters, params.isVisible()));
    } catch (Exception e) {
      Util.processError(e, response);
    }
  }
}
