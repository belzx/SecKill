package com.lizhi.exception;

/**
 * 订单已经关闭
 */
public class SeckillCloseException extends SeckillException {
    public SeckillCloseException() {
        super();
    }

    public SeckillCloseException(String message) {
        super(message);
    }
}
