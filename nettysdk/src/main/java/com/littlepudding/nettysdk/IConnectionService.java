package com.littlepudding.nettysdk;

import com.littlepudding.nettysdk.model.IInitializeData;
import com.littlepudding.nettysdk.model.ISendMessage;

/**
 * @author liuxiaofeng
 * @description 连接服务
 * @since 2019-10-09
 */
public interface IConnectionService {
    /**
     * 创建连接
     *
     * @param host            主机
     * @param port            端口
     * @param iInitializeData 初始化数据
     */
    void create(String host, int port, IInitializeData iInitializeData);

    /**
     * 添加监听
     *
     * @param iConnectionListener
     */
    void addListener(IConnectionListener iConnectionListener);

    /**
     * 删除监听
     *
     * @param iConnectionListener
     */
    void removeListener(IConnectionListener iConnectionListener);

    /**
     * 开始连接
     */
    void connect();

    /**
     * 断开连接
     */
    void disConnect();

    /**
     * 发送数据
     *
     * @param sendMessage
     * @param isNeedRetry 是否需要重试
     */
    void sendMessage(ISendMessage sendMessage, boolean isNeedRetry);

    /**
     * 是否已经连接
     *
     * @return 连接的状态
     */
    boolean isConnect();
}
