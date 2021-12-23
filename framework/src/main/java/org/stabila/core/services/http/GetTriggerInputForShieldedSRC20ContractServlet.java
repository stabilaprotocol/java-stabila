package org.stabila.core.services.http;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.stabila.api.GrpcAPI.BytesMessage;
import org.stabila.api.GrpcAPI.ShieldedSRC20TriggerContractParameters;
import org.stabila.core.Wallet;

@Component
@Slf4j(topic = "API")
public class GetTriggerInputForShieldedSRC20ContractServlet extends RateLimiterServlet {

  @Autowired
  private Wallet wallet;

  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
  }

  protected void doPost(HttpServletRequest request, HttpServletResponse response) {
    try {
      PostParams params = PostParams.getPostParams(request);
      ShieldedSRC20TriggerContractParameters.Builder builder =
          ShieldedSRC20TriggerContractParameters
              .newBuilder();
      JsonFormat.merge(params.getParams(), builder, params.isVisible());
      BytesMessage result = wallet.getTriggerInputForShieldedSRC20Contract(builder.build());
      response.getWriter().println(JsonFormat.printToString(result, params.isVisible()));
    } catch (Exception e) {
      Util.processError(e, response);
    }
  }
}
