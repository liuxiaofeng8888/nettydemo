package com.littlepudding.nettysdk.model;

/**
 * @author liuxiaofeng
 * @description 初始化数据
 * @since 2019-10-09
 */
public interface IInitializeData {

    /**
     * 获取协议
     */
    ConnectionAggrement getAggrement();

    /**
     * 获取心跳信息
     */
    ISendMessage getHeartBeatMessage();

    /**
     * 获取注册信息
     */
    ISendMessage getChannelActivieMessage();

    /**
     * 获取协议内容信息
     */
    Object aggrementMessageData();
}
