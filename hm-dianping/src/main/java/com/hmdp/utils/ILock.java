package com.hmdp.utils;

public interface ILock {
/**
 * 尝试在指定的时间内获取锁，如果超时则返回false
 * @param timeoutSec 等待锁的最大时间（秒）
 * @return 如果成功获取锁返回true，如果在指定时间内未获取到锁返回false
 */
    boolean tryLock(long timeoutSec);

    void unLock();
}
