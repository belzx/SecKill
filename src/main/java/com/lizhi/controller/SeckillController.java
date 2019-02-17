package com.lizhi.controller;

import com.lizhi.exception.FailedSeckillException;
import com.lizhi.exception.RepeatSeckillException;
import com.lizhi.exception.SeckillCloseException;
import com.lizhi.service.ISeckillService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/*
   version-1.0：
     此时的流程大概如下：
     1：查询所有秒杀商品信息（select * from seckill where status = 1）
     2：根据拼接的地址，开始秒杀"/{seckillGoodsId}/execution" 。秒杀id+md5执行秒杀策略（md5用来防止恶意刷接口）
     3：秒杀
        3.1：获取秒杀商品的信息
               判断是否超时，是否已经开始，是否用户重复秒杀  -从redis-mysql（利用redis做一级缓存，先访问redis，如果redis缓存中没有所需资源或者访问访问超时，则直接进入mysql获取系统资源，将获取的内容更新在redis当中）
        3.2：Mysql执行update，执行秒杀。成功修改数据则返回true，表示秒杀成功

   架构大概就如上了，但是我们
   缺点：
       Mysql执行update的减库存比较低效，一条update操作的压力测试结果是可以抗住4wQPS,也就是说，一个商品在1秒内，可以被买4w次。
       对于一般的秒杀还行，如果人数比较多，则性能会大幅度下降

   高并发缓存下常见的几个概念
     1：缓存穿透：大量不存在的key查询，越过缓存查询数据库，一些恶意攻击、爬虫等造成大量空命中。注意这里数据库也查询不到。
     2：缓存击穿：高并发下,多线程同时查询同一个资源,如果缓存中没有这个资源,那么这些线程都会去数据库查找,对数据库造成极大压力
     3：缓存雪崩：当缓存服务器重启或者大量缓存集中在某一个时间段失效，这样在失效的时候，也会给后端系统(比如DB)带来很大压力，造成数据库后端故障，从而引起应用服务器雪崩

   开始进行秒杀接口的优化。我认为优化的话，主要是从上面的缺点，还有高并发缓存概念上去优化的。
    1：缓存穿透：如何解决，防止恶意攻击啥的
        1：修改秒杀地址，将秒杀地址由"/{seckillGoodsId}/execution"修改为"/{seckillGoodsId}/{md5}/execution"
            我来解释下md5的含义



   秒杀地址接口的优化策略：
   请求地址，
   （策略：超时穿透，主动更新）
   背景：
   Mysql执行update的减库存比较低效，一条update操作的压力测试结果是可以抗住4wQPS,也就是说，一个商品在1秒内，可以被买4w次；
 */
@RestController
public class SeckillController {

    private static Logger log = LoggerFactory.getLogger(SeckillController.class);

    @Resource
    private ISeckillService seckillService;

    @GetMapping(name = "/getSecKillInfo")
    public Object getSecKillInfo() {
        //获取秒杀的信息，这里只是一个简单的事例
        return seckillService.findSeckillAll();
    }

    /**
     * 秒杀接口的设计
     * 1：{MD5} 是为了防止不法分子在知晓了seckillid的情况下，利用插件频繁的刷接口（用户截取了你的访问地址，他看到了当前秒杀ID为1000，他完全可以推测出其他的秒杀地址，或者说他可以造出一批地址；视频中秒杀在数据库中判断了秒杀时间，其他时间他自然是秒杀不到，但是对数据库也有一定的冲击，如果他用定时器或者循环秒杀软件，你的系统承受力是个问题；）
     *
     * @param seckillGoodsId
     * @param md5
     * @return
     */
    @RequestMapping(value = "/{seckillGoodsId}/{md5}/execution")
    public Boolean execution(@PathVariable("seckillGoodsId") Long seckillGoodsId, @PathVariable("md5") String md5) {
        //获取用户信息
        long userPhone = 123456;//这个是编造的，一般都是通过认证服务获取当前用户的userPhone
        //开始执行秒杀
        try {
            return seckillService.executionSeckillId(seckillGoodsId, md5, userPhone);
        } catch (RepeatSeckillException e) {
            log.warn(e.getMessage());
            return false;
        } catch (FailedSeckillException e) {
            log.warn(e.getMessage());
            return false;
        } catch (SeckillCloseException e) {
            log.warn(e.getMessage());
            return false;
        }
    }


}
