package com.lizhi.exception;

/**
 * 重复订单
 */
public class RepeatSeckillException extends SeckillException {
    public RepeatSeckillException() {
        super();
    }

    public RepeatSeckillException(String message) {
        super(message);
    }
}
