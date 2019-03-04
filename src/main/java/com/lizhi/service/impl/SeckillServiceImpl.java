package com.lizhi.service.impl;

import com.lizhi.bean.Exposer;
import com.lizhi.bean.Seckill;
import com.lizhi.dao.SeckillMapper;
import com.lizhi.exception.FailedSeckillException;
import com.lizhi.exception.RepeatSeckillException;
import com.lizhi.exception.SeckillCloseException;
import com.lizhi.service.IRedisBloomFilter;
import com.lizhi.service.ISeckillService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
public class SeckillServiceImpl implements ISeckillService {

    public static final String SECKILL_CACHE = "seckill";

    /**
     * 已有订单的商品布隆过滤
     */
    public static final String SECKILLORDER_BLOOM_FILTER = "seckill_filter";

    /**
     * 所有秒杀的id布隆过滤
     */
    public static final String SECKILLID_BLOOM_FILTER = "seckillid_filter";

    private final static Logger log = LoggerFactory.getLogger(SeckillServiceImpl.class);

    private final double bloomsize  = Math.pow(2, 32);

    @Resource
    private SeckillMapper seckillMapper;

    @Resource
    public RedisService redisService;

    IRedisBloomFilter bloomFilter;

    /**
     * 预缓存所有的秒杀的id
     * 这个过滤需要自己去维护，每删除或增加一条，都需要，维护到redis缓存中。
     */
    @PostConstruct
    private void init(){
        bloomFilter = redisService.getBloomFilter(0.000001, Integer.MAX_VALUE);
//        bloomFilter.init(SECKILLID_BLOOM_FILTER,);//不初始化，则默认缓存无期限
    }

    @Override
    public List<Exposer> findSeckillAll() {
        List<Exposer> result = new ArrayList<>();
        List<Seckill> seckillAll = this.seckillMapper.findSeckillAll();
        seckillAll.forEach(d -> {
            result.add(new Exposer(1, d.getSeckillId(), getMD5(d.getSeckillId())));
        });

        return result;
    }

    @Override
    @Transactional//关于事务回滚，默认对runtimgexception 以及其他限定的几个异常进行回滚。
    public Boolean executionSeckillId(long seckillGoodsId, String md5, long userPhone) throws RepeatSeckillException, FailedSeckillException, SeckillCloseException {

        //1：判断接口是否正确,防止插件恶意刷新
        if (seckillGoodsId == 0 || !getMD5(seckillGoodsId).equals(md5)) {
            //非法的参数
            log.warn("invalid parameter seckillGoodsId:[{}],userphome:[{}]",seckillGoodsId,userPhone);
            return false;
        }


        //2：查看是否有这条商品的记录
        //Redis中有一个数据结构叫做Bitmap,可以用这个作为布隆过滤。防止大量的无效请求对数据库造成影响
        if(!redisService.getBit(SECKILLID_BLOOM_FILTER,(long) (seckillGoodsId % bloomsize))){
            //秒杀的商品不存在
            log.warn("seckillGoodsId:[{}],userphome:[{}];not exists in bloom filter",seckillGoodsId,userPhone);
            return false;
        }

        //3：获取秒杀商品的记录。
        Seckill seckill = getSeckillBySeckillId(seckillGoodsId);
        if (seckill == null) {//没有找到抢购记录
            log.warn("seckill is null seckillGoodsId:[{}],userphome:[{}]",seckillGoodsId,userPhone);
            return false;
        }

        //4：查看是否已经过期
        if (seckill.getEndTime().getTime() < System.currentTimeMillis()) {
            throw new SeckillCloseException("秒杀时间已经结束");
        }

        //5:开始下订单,如果下过订单，则不能继续秒杀。判断订单重复这里使用到了布隆过滤（这个算法非常使用于大数据中判断一个值是否存在）
        //Redis中有一个数据结构叫做Bitmap,可以用这个作为布隆过滤
        //bloom中如果存在这个订单，则返回true
        //如果为false，则
        String bloomValue = String.valueOf(seckillGoodsId) + String.valueOf(userPhone);
        if(!bloomFilter.contains(SECKILLORDER_BLOOM_FILTER,bloomValue)){
            if(seckillMapper.insertOrder(seckillGoodsId, seckill.getCostPrice(), userPhone) == 0){
                //更新bloom
                bloomFilter.add(SECKILLORDER_BLOOM_FILTER,bloomValue);
                throw new RepeatSeckillException("订单重复");//重复订单
            }
        }else {
            throw new RepeatSeckillException("订单重复");//重复订单
        }

        //6:开始执行秒杀策略
        if (seckillMapper.reduceStock(seckillGoodsId, new Date()) > 0) {
            //更新bloom
            bloomFilter.add(SECKILLORDER_BLOOM_FILTER,bloomValue);
            return true;
        } else {
            //秒杀失败,交给事务处理回滚
            throw new FailedSeckillException("秒杀失败");
        }
    }

    /**
     * 应用于在秒杀业务场景下，抢购信息的查询
     * 使用二级缓存
     * 1：redis
     *
     * @param seckillId
     * @return
     */
    public Seckill getSeckillBySeckillId(long seckillId) {
        //获取一条秒杀的商品的信息
        //缓存中 读多，写少
        //1：从redis获取
        //2：从mysql获取
        Seckill seckill = (Seckill) redisService.get(SECKILL_CACHE + seckillId);
        if (seckill == null) {
            seckill = seckillMapper.findBySeckillId(seckillId);
            if (seckill != null) {
                redisService.set(SECKILL_CACHE + seckillId, seckill, getRandomExpireTime(seckill.getEndTime()));
            }
        }
        return seckill;
    }

    private long getRandomExpireTime(Date endTime) {
        return (endTime.getTime() - System.currentTimeMillis()) / 1000 + (long) (Math.random() * 1000);
    }

    /**
     * 两次加密过程 md5已经被破解，建议使用更加安全的不可逆算法
     * 如 sha256 及以上等
     *
     * @param seckillId
     * @return
     */
    private String getMD5(long seckillId) {
        String md51 = DigestUtils.md5DigestAsHex((seckillId + "/test").getBytes());
        return DigestUtils.md5DigestAsHex((md51 + "/test2").getBytes());
    }
}
