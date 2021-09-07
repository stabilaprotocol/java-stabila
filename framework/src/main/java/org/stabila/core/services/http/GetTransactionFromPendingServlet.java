package org.stabila.core.services.http;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.stabila.core.capsule.TransactionCapsule;
import org.stabila.core.db.Manager;
import org.tron.api.GrpcAPI.BytesMessage;
import org.tron.common.utils.ByteArray;


@Component
@Slf4j(topic = "API")
public class GetTransactionFromPendingServlet extends RateLimiterServlet {

  @Autowired
  private Manager manager;

  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
    try {
      boolean visible = Util.getVisible(request);
      String input = request.getParameter("value");
      TransactionCapsule reply = manager.getTxFromPending(input);
      if (reply != null) {
        response.getWriter().println(Util.printTransaction(reply.getInstance(), visible));
      } else {
        response.getWriter().println("{}");
      }
    } catch (Exception e) {
      Util.processError(e, response);
    }
  }

  protected void doPost(HttpServletRequest request, HttpServletResponse response) {
    try {
      PostParams params = PostParams.getPostParams(request);
      BytesMessage.Builder build = BytesMessage.newBuilder();
      JsonFormat.merge(params.getParams(), build, params.isVisible());
      TransactionCapsule reply = manager
          .getTxFromPending(ByteArray.toHexString(build.getValue().toByteArray()));
      if (reply != null) {
        response.getWriter()
            .println(Util.printTransaction(reply.getInstance(), params.isVisible()));
      } else {
        response.getWriter().println("{}");
      }
    } catch (Exception e) {
      Util.processError(e, response);
    }
  }
}
