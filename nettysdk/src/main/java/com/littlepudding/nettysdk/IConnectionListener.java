package com.littlepudding.nettysdk;

import java.lang.reflect.Type;

/**
 * @author liuxiaofeng
 * @description netty长连接的接口
 * @since 2019-10-09
 */
public interface IConnectionListener<T> {

    /**
     * 连接成功
     */
    void onConnect();

    /**
     * 断连
     */
    void onDisConnect();

    /**
     * 接受到服务器发送的消息
     */
    Object onReceive(T data);

    /**
     * 重新连接
     */
    void onReConnect();

    /**
     * 当接收的数据为json时 需要返回
     * 接收的数据类型
     * @return 接受的数据类型
     */
    Type getMessageType();
}
