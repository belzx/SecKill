package com.lizhi.bean;

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