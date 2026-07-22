package com.example.keyframevideo.service;

import java.time.Duration;
import java.util.function.Supplier;

public interface DistributedLockService {

    <T> T executeWithLock(String lockKey, Duration lockTtl, Duration waitTimeout, Supplier<T> supplier);
}
