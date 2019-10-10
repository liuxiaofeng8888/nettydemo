package com.littlepudding.nettysdk.model;

/**
 * @author liuxiaofeng
 * @description 发送数据模型
 * @since 2019-10-09
 */
public interface ISendMessage<T> {

    /**
     * 获取消息
     * @return 消息模型
     */
    T getData();
}
