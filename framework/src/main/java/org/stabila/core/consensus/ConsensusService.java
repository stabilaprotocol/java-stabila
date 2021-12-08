package org.stabila.core.consensus;

import static org.stabila.common.utils.ByteArray.fromHexString;

import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.encoders.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.stabila.common.crypto.SignUtils;
import org.stabila.common.parameter.CommonParameter;
import org.stabila.consensus.Consensus;
import org.stabila.consensus.base.Param;
import org.stabila.consensus.base.Param.Miner;
import org.stabila.core.capsule.ExecutiveCapsule;
import org.stabila.core.config.args.Args;
import org.stabila.core.store.ExecutiveStore;

@Slf4j(topic = "consensus")
@Component
public class ConsensusService {

  @Autowired
  private Consensus consensus;

  @Autowired
  private ExecutiveStore executiveStore;

  @Autowired
  private BlockHandleImpl blockHandle;

  @Autowired
  private PbftBaseImpl pbftBaseImpl;

  private CommonParameter parameter = Args.getInstance();

  public void start() {
    Param param = Param.getInstance();
    param.setEnable(parameter.isExecutive());
    param.setGenesisBlock(parameter.getGenesisBlock());
    param.setMinParticipationRate(parameter.getMinParticipationRate());
    param.setBlockProduceTimeoutPercent(Args.getInstance().getBlockProducedTimeOut());
    param.setNeedSyncCheck(parameter.isNeedSyncCheck());
    param.setAgreeNodeCount(parameter.getAgreeNodeCount());
    List<Miner> miners = new ArrayList<>();
    List<String> privateKeys = Args.getLocalExecutives().getPrivateKeys();
    if (privateKeys.size() > 1) {
      for (String key : privateKeys) {
        byte[] privateKey = fromHexString(key);
        byte[] privateKeyAddress = SignUtils
            .fromPrivate(privateKey, Args.getInstance().isECKeyCryptoEngine()).getAddress();
        ExecutiveCapsule executiveCapsule = executiveStore.get(privateKeyAddress);
        if (null == executiveCapsule) {
          logger.warn("Executive {} is not in executiveStore.", Hex.toHexString(privateKeyAddress));
        }
        Miner miner = param.new Miner(privateKey, ByteString.copyFrom(privateKeyAddress),
            ByteString.copyFrom(privateKeyAddress));
        miners.add(miner);
        logger.info("Add executive: {}, size: {}",
            Hex.toHexString(privateKeyAddress), miners.size());
      }
    } else {
      byte[] privateKey =
          fromHexString(Args.getLocalExecutives().getPrivateKey());
      byte[] privateKeyAddress = SignUtils.fromPrivate(privateKey,
          Args.getInstance().isECKeyCryptoEngine()).getAddress();
      byte[] executiveAddress = Args.getLocalExecutives().getExecutiveAccountAddress(
          Args.getInstance().isECKeyCryptoEngine());
      ExecutiveCapsule executiveCapsule = executiveStore.get(executiveAddress);
      if (null == executiveCapsule) {
        logger.warn("Executive {} is not in executiveStore.", Hex.toHexString(executiveAddress));
      }
      Miner miner = param.new Miner(privateKey, ByteString.copyFrom(privateKeyAddress),
          ByteString.copyFrom(executiveAddress));
      miners.add(miner);
    }

    param.setMiners(miners);
    param.setBlockHandle(blockHandle);
    param.setPbftInterface(pbftBaseImpl);
    consensus.start(param);
    logger.info("consensus service start success");
  }

  public void stop() {
    consensus.stop();
  }

}
