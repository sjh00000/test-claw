package com.example.aigenstudio.service.impl;

import com.example.aigenstudio.exception.BusinessException;
import com.example.aigenstudio.service.DistributedLockService;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisDistributedLockServiceImpl implements DistributedLockService {

    private static final long LOCK_RETRY_INTERVAL_MS = 50L;
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public <T> T executeWithLock(String lockKey, Duration lockTtl, Duration waitTimeout, Supplier<T> supplier) {
        String lockValue = UUID.randomUUID().toString();
        long waitDeadline = System.currentTimeMillis() + waitTimeout.toMillis();
        try {
            while (System.currentTimeMillis() <= waitDeadline) {
                if (tryAcquire(lockKey, lockValue, lockTtl)) {
                    try {
                        // 执行业务逻辑
                        return supplier.get();
                    } finally {
                        release(lockKey, lockValue);
                    }
                }
                sleepQuietly();
            }
            throw new BusinessException("当前任务正在提交中，请稍后再试");
        } catch (RedisConnectionFailureException | RedisSystemException ex) {
            log.error("获取 Redis 分布式锁失败，lockKey={}", lockKey, ex);
            throw new BusinessException("系统繁忙，请稍后重试");
        }
    }

    private boolean tryAcquire(String lockKey, String lockValue, Duration lockTtl) {
        // SET NX PX 语义保证加锁原子性，TTL 用于进程异常退出后的锁自动释放。
        return Boolean.TRUE.equals(stringRedisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, lockTtl));
    }

    private void release(String lockKey, String lockValue) {
        try {
            // Lua 脚本比较 value 后删除，避免误删已经被其他请求重新获取的锁。
            stringRedisTemplate.execute(UNLOCK_SCRIPT, List.of(lockKey), lockValue);
        } catch (RedisConnectionFailureException | RedisSystemException ex) {
            log.warn("释放 Redis 分布式锁失败，lockKey={}", lockKey, ex);
        }
    }

    private void sleepQuietly() {
        try {
            Thread.sleep(LOCK_RETRY_INTERVAL_MS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException("获取任务锁被中断，请稍后重试");
        }
    }
}
