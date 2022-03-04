package org.stabila.core.services.interfaceOnPBFT.http;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.stabila.core.services.http.IsShieldedSRC20ContractNoteSpentServlet;
import org.stabila.core.services.interfaceOnPBFT.WalletOnPBFT;

@Component
@Slf4j(topic = "API")
public class IsShieldedSRC20ContractNoteSpentOnPBFTServlet extends
    IsShieldedSRC20ContractNoteSpentServlet {

  @Autowired
  private WalletOnPBFT walletOnPBFT;

  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
    walletOnPBFT.futureGet(() -> super.doGet(request, response));
  }

  protected void doPost(HttpServletRequest request, HttpServletResponse response) {
    walletOnPBFT.futureGet(() -> super.doPost(request, response));
  }
}