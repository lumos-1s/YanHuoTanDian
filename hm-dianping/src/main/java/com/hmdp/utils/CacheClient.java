package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import static com.hmdp.utils.RedisConstants.*;

@Slf4j
@Component
public class CacheClient {
 private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

/**
 * 设置键值对并指定过期时间
 * @param key 键名
 * @param value 要存储的值，将被转换为JSON字符串
 * @param time 过期时间
 * @param unit 时间单位
 */
    public void set(String key, Object value, Long time, TimeUnit unit) {
    // 使用StringRedisTemplate的ValueOperations设置键值对
    // 将value对象转换为JSON字符串后存储
    // 并设置指定的过期时间
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }
/**
 * 设置带有逻辑过期时间的数据到Redis
 * @param key Redis中的键
 * @param value 要存储的数据值
 * @param time 过期时间数值
 * @param unit 过期时间单位
 */
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        //设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        //写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }
    public <R,ID> R queryWithPassThrough(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        // 1. 定义Redis中的键
        String key = keyPrefix+ id;
        //1.从redis查询商铺缓存
        String Json = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if (StrUtil.isNotBlank(Json)) {
            //3.存在，直接返回

           return JSONUtil.toBean(Json,type);
        }
        //命中的是否是空值
        if ("".equals(Json)) {
            //4.不存在，直接返回错误信息
            return null;
        }
        //4.不存在，根据id查询数据库
        R r = dbFallback.apply(id);

        //5.不存在，返回错误信息
        if (r== null) {
            //将控制写入redis
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //6.存在，将数据存入redis，并返回
        this.set(key, r, time, unit);
        return r;

    }
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    public <R ,ID> R queryWithLogicalExpire(String keyPrefix,ID id,Class <R> type,Function<ID,R> dbFallback,Long time,TimeUnit unit) {
        // 1.从redis查询商铺缓存，使用固定的key前缀拼接商铺ID
        String key =keyPrefix+ id;
        String json = stringRedisTemplate.opsForValue().get(key);

        // 2.判断是否存在，如果缓存为空则直接返回null
        if (StrUtil.isBlank(json)) {
            // 3.不存在，直接返回
            return null;
        }

        // 4.命中，需要先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
         R r= JSONUtil.toBean((JSONObject) redisData.getData(),type);
        LocalDateTime expireTime = redisData.getExpireTime();

        // 5.判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 5.1.未过期，直接返回店铺信息
            return r;
        }

        // 5.2.已过期，需要缓存重建
        // 6.缓存重建
        // 6.1.获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        // 6.2.判断是否获取锁成功
        if (isLock) {
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    //重建缓存
                    //先查数据库
                    R r1= dbFallback.apply(id);
                    //写入redis
                 this.setWithLogicalExpire(key,r1,time,unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unlock(lockKey);
                }
            });
        }


        //6.存在，将数据存入redis，并返回
        return r;
    }
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }
}
