package com.lizhi.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * redicache 工具类
 *
 */
@Service
public class RedisService {
    private static Logger log = LoggerFactory.getLogger(RedisService.class);

    /**
     * redisTemplate.opsForValue();//操作字符串
     * redisTemplate.opsForHash();//操作hash
     * redisTemplate.opsForList();//操作list
     * redisTemplate.opsForSet();//操作set
     * redisTemplate.opsForZSet();//操作有序set
     */
    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 批量删除对应的value
     *
     * @param keys
     */
    public void remove(final String... keys) {
        for (String key : keys) {
            remove(key);
        }
    }
    /**
     * 批量删除key
     *
     * @param pattern
     */
    public void removePattern(final String pattern) {
        Set<Serializable> keys = redisTemplate.keys(pattern);
        if (keys.size() > 0)
            redisTemplate.delete(keys);
    }


    /**
     * 删除对应的value
     *
     * @param key
     */
    public void remove(final String key) {
        if (exists(key)) {
            redisTemplate.delete(key);
        }
    }
    /**
     * 判断缓存中是否有对应的value
     *
     * @param key
     * @return
     */
    public boolean exists(final String key) {
        return redisTemplate.hasKey(key);
    }
    /**
     * 读取缓存
     *
     * @param key
     * @return
     */
    public Object get(final String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        }catch (Exception e){
            log.error("Redis Failed to get",e);
        }
        return null;
    }
    /**
     * 写入缓存
     *
     * @param key
     * @param value
     * @return
     */
    public boolean set(final String key, Object value) {
        try {
            redisTemplate.opsForValue().set(key,value);
        } catch (Exception e) {
            log.error("Redis Failed to set",e);
            return false;
        }
        return true;
    }

    /**
     * 写入缓存
     * @param expireTime 过期时间s
     * @return
     */
    public boolean set(final String key, Object value, Long expireTime) {
        try {
            ValueOperations<Serializable, Object> operations = redisTemplate.opsForValue();
            operations.set(key, value);
            redisTemplate.expire(key, expireTime, TimeUnit.SECONDS);
            return true;
        } catch (Exception e) {
            log.error("",e);
        }
        return false;
    }

    public  boolean hmset(String key, Map<String, String> value) {
        try {
            redisTemplate.opsForHash().putAll(key, value);
            return true;
        } catch (Exception e) {
            log.error("",e);
        }
        return false;
    }

    public  Map<String,String> hmget(String key) {
        try {
            return redisTemplate.opsForHash().entries(key);
        } catch (Exception e) {
            log.error("",e);
        }
        return null;
    }

    public void lset(String key,Object object){
        try {
            redisTemplate.opsForList().leftPush(key, object);
        } catch (Exception e) {
            log.error("",e);
        }
    }

    public Object lget(String key){
        try {
            return   redisTemplate.opsForList().leftPop(key);
        } catch (Exception e) {
            log.error("",e);
            return null;
        }
    }

    public Object lgetAll(String key){
        List<Object> o = new ArrayList();
        try {
            Object lget;
            while((lget = lget(key)) != null){
                o.add(lget);
            }
            return  o;
        } catch (Exception e) {
            log.error("",e);
            return null;
        }
    }

    /**
     * setBit Boolean setBit(K key, long offset, boolean value);
     * 对 key 所储存的字符串值，设置或清除指定偏移量上的位(bit)
     * key键对应的值value对应的ascii码,在offset的位置(从左向右数)变为value
     * 因为二进制只有0和1，在setbit中true为1，false为0，因此我要变为'b'的话第六位设置为1，第七位设置为0
     * @param key
     * @param index
     */
    public boolean setBit(String key,long index,boolean value ){
        return redisTemplate.opsForValue().setBit(key,index, value);
    }

    public boolean getBit(String key,long index){
        return redisTemplate.opsForValue().getBit(key,index);
    }

    /**
     * 向某一个频道发送消息
     * @param channel
     * @param message
     */
    public void sendMessage(String channel, String message) {
        redisTemplate.convertAndSend(channel, message);
    }
}