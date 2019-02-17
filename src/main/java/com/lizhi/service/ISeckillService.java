package com.lizhi.service;

import com.lizhi.bean.Exposer;
import com.lizhi.bean.Seckill;
import com.lizhi.exception.FailedSeckillException;
import com.lizhi.exception.RepeatSeckillException;
import com.lizhi.exception.SeckillCloseException;

import java.util.List;


public interface ISeckillService {

    List<Exposer> findSeckillAll();

    Seckill getSeckillBySeckillId(long seckillId);

    /**
     * 执行秒杀的接口
     * @param seckillGoodsId 秒杀商品的id
     * @param md5            id的md5，防止恶意刷接口
     * @param userPhone      秒杀人的手机号。一个手机号只能抢到一个商品
     * @return
     */
    Boolean executionSeckillId(long seckillGoodsId, String md5, long userPhone) throws RepeatSeckillException, FailedSeckillException, SeckillCloseException;
}
