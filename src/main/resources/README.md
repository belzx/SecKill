高并发秒杀接口的设计：https://github.com/lizhixiong1994

参考：https://www.cnblogs.com/twoheads/p/8360830.html （写的很详细，并且拥有漂亮的个人博客）

version-1.0：springboot+mysql+mybatis

sql建表

```
CREATE TABLE `seckill`(
  `seckill_id` bigint NOT NULL AUTO_INCREMENT COMMENT '商品ID',
  `title` varchar (1000) DEFAULT NULL COMMENT '商品标题',
  `image` varchar (1000) DEFAULT NULL COMMENT '商品图片',
  `price` decimal (10,2) DEFAULT NULL COMMENT '商品原价格',
  `cost_price` decimal (10,2) DEFAULT NULL COMMENT '商品秒杀价格',
  `stock_count` bigint DEFAULT NULL COMMENT '剩余库存数量',
  `start_time` timestamp NOT NULL DEFAULT '1970-02-01 00:00:01' COMMENT '秒杀开始时间',
  `end_time` timestamp NOT NULL DEFAULT '1970-02-01 00:00:01' COMMENT '秒杀结束时间',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `status` int(1) NOT NULL DEFAULT 1 COMMENT '状态1：在用 0：废弃',
  PRIMARY KEY (`seckill_id`),
  KEY `idx_start_time` (`start_time`),
  KEY `idx_end_time` (`end_time`),
  KEY `idx_create_time` (`end_time`),
  KEY `idx_status` (`status`)
) CHARSET=utf8 ENGINE=InnoDB COMMENT '秒杀商品表';

CREATE TABLE `seckill_order`(
  `seckill_id` bigint NOT NULL COMMENT '秒杀商品ID',
  `money` decimal (10, 2) DEFAULT NULL COMMENT '支付金额',
  `user_phone` bigint NOT NULL COMMENT '用户手机号',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '创建时间',
  `state` tinyint NOT NULL DEFAULT -1 COMMENT '状态：-1无效 0成功 1已付款',
  PRIMARY KEY (`seckill_id`, `user_phone`) /*联合主键，保证一个用户只能秒杀一件商品*/
) CHARSET=utf8 ENGINE=InnoDB COMMENT '秒杀订单表';
```   
Bean
```   
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
/**
 * 秒杀商品的单条记录
 */
@Data
@ToString
public class Seckill implements Serializable {

    public static final int STATUS_NEW = 1;
    public static final int STATUS_DEAD = 0;

    private long seckillId; //商品ID
    private String title; //商品标题
    private String image; //商品图片
    private BigDecimal price; //商品原价格
    private BigDecimal costPrice; //商品秒杀价格
    private int status = STATUS_NEW; //状态

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime; //创建时间

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date startTime; //秒杀开始时间

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date endTime; //秒杀结束时间

    private long stockCount; //剩余库存数量
}
```   
```   
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
/**
 * 秒杀商品的订单
 */
@Data
@ToString
public class SeckillOrder implements Serializable {

    private long seckillId; //秒杀到的商品ID
    private BigDecimal money; //支付金额

    private long userPhone; //秒杀用户的手机号

    public SeckillOrder(long seckillId, long userPhone) {
        this.seckillId = seckillId;
        this.userPhone = userPhone;
    }

    /**
     * @DateTimeFormat()(来自springframework)和@JsonFormat()
     * (来自jackson)标识可以实现Controller在返回JSON数据（用@ResponseBody标识的方法或@RestController标识的类）的时候能将Date类型的参数值（经Mybatis查询得到的数据是英文格式的日期，因为实体类中是Date类型）
     * 转换为注解中指定的格式返回给页面（相当于经过了一层SimpleDateFormate）。
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime; //创建时间

    private boolean status; //订单状态， -1:无效 0:成功 1:已付款

    private Seckill seckill; //秒杀商品，和订单是一对多的关系
}

```   
### 流程如下：

1、查询所有秒杀商品信息（select * from seckill where status = 1）

```
    @GetMapping(name = "/getSecKillInfo")
    public Object getSecKillInfo() {
        //获取秒杀的信息，这里只是一个简单的事例
        return seckillService.findSeckillAll();
    }
```

2：前端根据拼接的地址，开始调用秒杀接口秒杀"/{seckillGoodsId}/execution" 

```
    @RequestMapping(value = "/{seckillGoodsId}/{md5}/execution")
    public Boolean execution(@PathVariable("seckillGoodsId") Long seckillGoodsId, @PathVariable("md5") String md5) {
        //获取用户信息
        long userPhone = 123456;//这个是编造的，一般都是通过认证服务获取当前用户的userPhone
        //开始执行秒杀
        try {
            return seckillService.executionSeckillId(seckillGoodsId, userPhone);
        } catch (Exception e) {
            log.warn(e.getMessage());
            return false;
        } 
    }
    
    
```
3：秒杀

3.1：获取秒杀商品的信息

判断是否超时，是否已经开始，是否用户重复秒杀  -从redis-mysql（利用redis做一级缓存，先访问redis，如果redis缓存中没有所需资源或者访问访问超时，则直接进入mysql获取系统资源，将获取的内容更新在redis当中）

3.2：Mysql执行update，执行秒杀。成功修改数据则返回true，表示秒杀成功
    
    
```
    @Override
    @Transactional//关于事务回滚，默认对runtimgexception 以及其他限定的几个异常进行回滚。
    public Boolean executionSeckillId(Long seckillGoodsId, long userPhone) {

        //1：获取秒杀商品的记录。
        Seckill seckill = getSeckillBySeckillId(seckillGoodsId);
        if (seckill == null) {//没有找到抢购记录
            return false;
        }

        //2：查看是否已经过期
        if (seckill.getEndTime().getTime() < System.currentTimeMillis()) {
            throw new RuntimeException("秒杀时间已经结束");
        }

        //3:开始保存订单,如果下过订单，则不能继续秒杀。seckillGoodsId 以及 userPhone是联合主键。保证一个人对于一个抢购只能有一次下订单的记录
        if (seckillMapper.insertOrder(seckillGoodsId, seckill.getCostPrice(), userPhone) == 0) {
            throw new RuntimeException("订单重复");//重复订单
        }

        //4:开始执行秒杀策略，将抢购的商品库存减1
        if (seckillMapper.reduceStock(seckillGoodsId, new Date()) > 0) {
            return true;
        } else {
            //秒杀失败,交给事务处理回滚
            throw new RuntimeException("秒杀失败");
        }
    }
    
```

涉及到的sql如下：这里因为篇幅有限，暂时只贴出核心部分，如果想要看全部的话，请去github上查看源码

```
 <!--减少库存数量-->
    <update id="reduceStock">
        UPDATE seckill
          SET stock_count = stock_count - 1
        WHERE seckill_id = #{seckillId}
        <![CDATA[
        AND start_time < #{killTime}
        AND end_time >= #{killTime}
          ]]>
        AND stock_count > 0
        AND status = 1
    </update>
    
    
    <!--插入订单-->
    <insert id="insertOrder">
        INSERT ignore INTO seckill_order(seckill_id, money, user_phone)
        VALUES (#{seckillId}, #{money}, #{userPhone})
    </insert>
    
    <!--查询秒杀-->
    <select id="findBySeckillId" resultMap="seckillResult">
        select
          *
          from seckill
          where seckill_id = #{seckillId}
    </select>

    <!--查询所有秒杀-->
    <select id="findSeckillAll" resultMap="seckillResult">
        select
          *
          from seckill
          where
           status = 1
    </select>
```

架构大概就如上。这个只是最原始方案，对这个方案，我觉得有义务去优化

先说下上面架构的缺点。

缺点：

1：数据库压力巨大：每一条秒杀背后都会涉及到对数据库的一次查询操作已经一次更新操作。在并发量比较少的时候无影响，但是一旦并发量比较高。数据库压力会巨大

2：接口并不安全：无法防止人恶意去刷接口，获取利用插件去刷接口，或者恶意搞破环，弄一些不存在的seckillGoodsId去执行秒杀

3：Mysql执行update的减库存比较低效，一条update操作的压力测试结果是可以抗住4wQPS,也就是说，一个商品在1秒内，可以被买4w次。并不适用高并发。

解决方案：

先了解高并发缓存下常见的几个概念，来自：https://www.cnblogs.com/silyvin/p/9106696.html

1：缓存穿透：缓存不命中导致的穿透问题，大量不存在的key查询，越过缓存查询数据库，一些恶意攻击、爬虫等造成大量空命中。注意这里数据库也查询不到。

2：缓存无底洞：缓存无底洞现象

3：缓存雪崩：当缓存服务器重启或者大量缓存集中在某一个时间段失效，这样在失效的时候，也会给后端系统(比如DB)带来很大压力，造成数据库后端故障，从而引起应用服务器雪崩

4：缓存一致性：保证缓存中的数据与数据库中的保持一致

5：缓存颠簸：缓存的颠簸问题，有些地方可能被称为“缓存抖动”，可以看作是一种比“雪崩”更轻微的故障，但是也会在一段时间内对系统造成冲击和性能影响。一般是由于缓存节点故障导致。业内推荐的做法是通过一致性Hash算法来解决
    
     
开始进行优化：

1：先解决数据库压力巨大的问题：增加一个nosql中间件，redis。用于缓存数据，在这个多读少写的情况下，最合适不过了。

1.1：搭建redis，不多说，github上面有很多实例，去搜索就可以了

1.2：利用redis进行查询缓存：代码如下

1.3：缓存的失效时间特殊设置，防止缓存雪崩
        
```
    /**
     * 应用于在秒杀业务场景下，抢购信息的单条查询
     * 使用二级缓存
     * @param seckillId 秒杀的id
     * @return
     */
    public Seckill getSeckillBySeckillId(long seckillId) {
        //获取一条秒杀的商品的信息
        //缓存中 读多，写少
        //1：从redis获取
        //2：从mysql获取
        Seckill seckill = (Seckill) redisService.get("seckill" + seckillId);
        if (seckill == null) {
            seckill = seckillMapper.findBySeckillId(seckillId);
            if (seckill != null) {
                redisService.set("seckill" + seckillId, seckill, getRandomExpireTime(seckill.getEndTime()));
            }
        }
        return seckill;
    }
    
    /*
    设置失效时间，失效时间由endTime-startTime
    为什么后面还加一个随机值：这是为了防止缓存雪崩的现象出现。
    列如：2：00：00后抢购结束，但是用户之后依然有在查询此条记录，如果所有的缓存都设定在2：00：00后统一失效，则又会重新查询mysql。
    */
    private long getRandomExpireTime(Date endTime) {
        return (endTime.getTime() - System.currentTimeMillis()) / 1000 + (long) (Math.random() * 1000);//单位为秒
    }
```

2：防止恶意攻击，提高接口安全性。

2.1：修改秒杀地址，将秒杀地址由"/{seckillGoodsId}/execution"修改为"/{seckillGoodsId}/{md5}/execution"

这里解释下md5的作用：

暴露秒杀接口之后，有人会利用一些插件，反复秒杀不同的商品。如果加入了md5，在发送回后台的时候由于seckillId不一样，则md5就不一样，这样就能防止上述情况的发生。

获取md5的值如下：
   
```
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
```

2.2：增加布隆过滤,防止缓存穿透

在查询一个秒杀是否存在的时候时候，增加一个布隆过滤。所有的秒杀商品的id都会被记录在布隆过滤中。减少对mysql查询的压力,防止缓存穿透，大量的空命中。

这个布隆过滤需要自己去维护，列如一开始就加载所有的id，然后维护缓存与mysql的一致性。
        
最后秒杀接口关于service的代码如下

```

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
    public static final String SECKILLID_BLOOM_FILTER = "seckill_filter";

    private final static Logger log = LoggerFactory.getLogger(SeckillServiceImpl.class);

    private final double bloomsize  = Math.pow(2, 32);

    @Resource
    private SeckillMapper seckillMapper;

    @Resource
    public RedisService redisService;

    /**
     * 预缓存所有的秒杀的id
     * 这个过滤需要自己去维护，每删除或增加一条，都需要，维护到redis缓存中。
     */
    @PostConstruct
    private void init(){
        List<Seckill> seckillAll = this.seckillMapper.findSeckillAll();
        seckillAll.forEach(d -> {
            redisService.getBit(SECKILLID_BLOOM_FILTER,(long) (d.getSeckillId() % bloomsize));
        });
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
        if(!redisService.getBit(SECKILLORDER_BLOOM_FILTER,(long) (bloomValue.hashCode() % bloomsize))){
            if(seckillMapper.insertOrder(seckillGoodsId, seckill.getCostPrice(), userPhone) == 0){
                //更新bloom
                redisService.setBit(SECKILLORDER_BLOOM_FILTER,(long) (bloomValue.hashCode() % bloomsize),true);
                throw new RepeatSeckillException("订单重复");//重复订单
            }
        }else {
            throw new RepeatSeckillException("订单重复");//重复订单
        }

        //6:开始执行秒杀策略
        if (seckillMapper.reduceStock(seckillGoodsId, new Date()) > 0) {
            //更新bloom
            redisService.setBit(SECKILLORDER_BLOOM_FILTER,(long) (bloomValue.hashCode() % bloomsize),true);
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

    /**
     * 应用于在秒杀业务场景下，抢购信息的单条查询
     * 使用二级缓存
     * 1：redis
     *
     * @param seckillId
     * @return
     */
    public Seckill updateSeckillBySeckill(long seckillId) {
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
```
ps：关于布隆过滤，这一块在guava中有已经实现的工具。在上面我为什么考虑用redis去实现。因为这套接口我的想法是适应于集群环境下的。

现在一般都是前后分离。后台通常都是2台或以上的服务器，这种背景下，缓存必须要用redis作为中间件。

还有一个的优化是

Mysql执行update的减库存比较低效。

我上网搜索了一波，可以使用存储过程，这一块得自行百度了。

关于这一块，基本上我只写了dao层还有service层，这一块我觉得是核心。。。其实还有很多的提升空间，这个只是我自己的一个小demo。。。

看业务量而定。并发超级高的话，可以做微服务分开。可以把订单这块单独拉出来作为一个服务。可以利用nginx做负载均衡，redis做集群，mysql也做集群。。。。

        

   