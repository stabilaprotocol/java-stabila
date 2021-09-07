package org.stabila.core.services.interfaceOnSolidity.http;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.stabila.core.services.interfaceOnSolidity.WalletOnSolidity;
import org.stabila.core.services.http.GetMarketPriceByPairServlet;


@Component
@Slf4j(topic = "API")
public class GetMarketPriceByPairOnSolidityServlet extends GetMarketPriceByPairServlet {

  @Autowired
  private WalletOnSolidity walletOnSolidity;

  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
    walletOnSolidity.futureGet(() -> super.doGet(request, response));
  }

  protected void doPost(HttpServletRequest request, HttpServletResponse response) {
    walletOnSolidity.futureGet(() -> super.doPost(request, response));
  }
}
