package com.lizhi.bean;

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