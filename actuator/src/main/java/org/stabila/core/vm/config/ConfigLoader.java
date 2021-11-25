package org.stabila.core.vm.config;


import lombok.extern.slf4j.Slf4j;
import org.stabila.core.capsule.ReceiptCapsule;
import org.stabila.core.store.DynamicPropertiesStore;
import org.stabila.core.store.StoreFactory;
import org.stabila.common.parameter.CommonParameter;

@Slf4j(topic = "VMConfigLoader")
public class ConfigLoader {

  //only for unit test
  public static boolean disable = false;

  public static void load(StoreFactory storeFactory) {
    if (!disable) {
      DynamicPropertiesStore ds = storeFactory.getChainBaseManager().getDynamicPropertiesStore();
      VMConfig.setVmTrace(CommonParameter.getInstance().isVmTrace());
      if (ds != null) {
        VMConfig.initVmHardFork(ReceiptCapsule.checkForUcrLimit(ds));
        VMConfig.initAllowMultiSign(ds.getAllowMultiSign());
        VMConfig.initAllowSvmTransferTrc10(ds.getAllowSvmTransferTrc10());
        VMConfig.initAllowSvmConstantinople(ds.getAllowSvmConstantinople());
        VMConfig.initAllowSvmSolidity059(ds.getAllowSvmSolidity059());
        VMConfig.initAllowShieldedTRC20Transaction(ds.getAllowShieldedTRC20Transaction());
        VMConfig.initAllowSvmIstanbul(ds.getAllowSvmIstanbul());
        VMConfig.initAllowSvmCd(ds.getAllowSvmCd());
        VMConfig.initAllowSvmVote(ds.getAllowSvmVote());
      }
    }
  }
}
