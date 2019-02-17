package com.lizhi.test;

import com.lizhi.bean.Seckill;
import com.lizhi.cache.RedisService;
import com.lizhi.service.ISeckillService;
import com.lizhi.service.impl.SeckillServiceImpl;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.web.WebAppConfiguration;

import javax.annotation.Resource;

/**
 * Created by lightClouds917
 * Date 2018/2/2
 * Description:测试类
 */

@RunWith(SpringRunner.class)
@SpringBootTest
//由于是Web项目，Junit需要模拟ServletContext，因此我们需要给我们的测试类加上@WebAppConfiguration。
@WebAppConfiguration
public class SpringbootTestEntFileTest {

    private Logger log = LoggerFactory.getLogger(this.getClass());

    @Resource
    private RedisService redisService;

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Autowired
    private ISeckillService iSeckillService;

    //@Ignore("not ready yet")
    @Test
    public void testGetEntFileById() {
        try {
            long a = System.currentTimeMillis();
            Seckill seckill = iSeckillService.getSeckillBySeckillId(1L);
            log.info("get id cast {}ms:seckill:{}", System.currentTimeMillis() - a, seckill);
        } catch (Exception e) {
            log.error("", e);
        }
    }

    @Test
    public void testFindAll() {
        try {
            System.out.println(iSeckillService.findSeckillAll());
        } catch (Exception e) {
            log.error("", e);
        }
    }

    @Test
    public void testSkill() {

//        redisService.remove(SeckillServiceImpl.SECKILLORDER_BLOOM_FILTER);
//        redisService.remove(SeckillServiceImpl.SECKILLID_BLOOM_FILTER);
//        redisService.remove(SeckillServiceImpl.SECKILL_CACHE+1);

        for (int i = 0; i < 2000; i++) {
            long a = System.currentTimeMillis();
            try {
                Boolean aBoolean = iSeckillService.executionSeckillId(1L, "1ac3c9ba6b9a0be911d7d4fb0a0fee93", (int)(Math.random()*100000));
                System.out.println("秒杀结果：" + aBoolean + "耗时：" + (System.currentTimeMillis() - a));
            } catch (Exception e) {
                System.out.println("秒杀失败：" + e.getMessage());
            }
        }

    }


    @Test
    public void testSetbit() {
        try {
//            boolean b = redisService.setBit("1", 1, true);
//            System.out.println(b);
            redisService.setBit("3", (long) (1L % Math.pow(2, 32)), true);
            System.out.println(redisService.getBit("3", (long) (1L % Math.pow(2, 32))));
        } catch (Exception e) {
            log.error("", e);
        }
    }

}