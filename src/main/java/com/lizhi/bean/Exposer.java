package com.lizhi.bean;

import lombok.Data;
import lombok.ToString;

@ToString
@Data
public class Exposer {

    private static final int STATUS_NEW = 0;

    private static final int STATUS_SECKILLING = 1;

    private static final int STATUS_CLOSE = 2;

    //秒杀是否开启
    private int status = STATUS_NEW;

    //  秒杀的id
    private long seckillId;

    //加密措施
    private String md5;

    public Exposer() {
    }

    public Exposer(int status, long seckillId, String md5) {
        this.status = status;
        this.seckillId = seckillId;
        this.md5 = md5;
    }
}