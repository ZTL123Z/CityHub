package com.hmdp.utils;

public interface ILock {

    /**
     * 尝试加锁
     * @param timeoutSec 超时时间
     * @return 是否加锁成功
     */
    boolean tryLock(long timeoutSec);

    /**
     * 解锁
     */
    void unlock();
}
