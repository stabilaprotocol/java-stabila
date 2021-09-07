package org.stabila.core.services.ratelimiter.adapter;

public interface IPreemptibleRateLimiter extends IRateLimiter {

  void release();
}
