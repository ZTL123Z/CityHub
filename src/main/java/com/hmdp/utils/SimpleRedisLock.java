package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{

    private String name;
    private StringRedisTemplate stringRedisTemplate;

    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString() + "-";

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }


    @Override
    public boolean tryLock(long timeoutSec) {
        //获得线程标识
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        //获得锁, 自动拆箱可以为null
        return stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + name, threadId, timeoutSec, TimeUnit.SECONDS) != null;
    }

    @Override
    public void unlock() {
        //获得线程标识
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        //判断是否是当前线程加锁
        if (!stringRedisTemplate.opsForValue().get(KEY_PREFIX + name).equals(threadId)) {
            return;
        }
        //删除锁
        stringRedisTemplate.delete(KEY_PREFIX + name);
    }
}
