package org.stabila.core.services.ratelimiter.adapter;

import org.stabila.core.services.ratelimiter.RuntimeData;

public interface IRateLimiter {

  boolean acquire(RuntimeData data);

}
