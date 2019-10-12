package com.example.nettydemo;

import com.littlepudding.nettysdk.model.ISendMessage;

/**
 * tcp消息
 */
public class SendMessage implements ISendMessage<Object> {
    private Object data;

    public SendMessage(Object data) {
        this.data = data;
    }

    @Override
    public Object getData() {
        return data;
    }
}
