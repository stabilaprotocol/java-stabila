package org.stabila.core.services.ratelimiter.adapter;

import org.stabila.core.services.ratelimiter.RuntimeData;
import org.stabila.core.services.ratelimiter.strategy.GlobalPreemptibleStrategy;

public class GlobalPreemptibleAdapter implements IPreemptibleRateLimiter {

  private GlobalPreemptibleStrategy strategy;

  public GlobalPreemptibleAdapter(String paramString) {

    strategy = new GlobalPreemptibleStrategy(paramString);
  }

  @Override
  public void release() {
    strategy.release();
  }

  @Override
  public boolean acquire(RuntimeData data) {
    return strategy.acquire();
  }

}