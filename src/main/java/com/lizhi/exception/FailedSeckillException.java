package com.lizhi.exception;

/**
 * 重复订单
 */
public class FailedSeckillException extends SeckillException {
    public FailedSeckillException() {
        super();
    }

    public FailedSeckillException(String message) {
        super(message);
    }
}
