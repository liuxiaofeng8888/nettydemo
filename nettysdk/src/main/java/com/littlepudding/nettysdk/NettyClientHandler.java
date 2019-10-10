package com.littlepudding.nettysdk;

import android.util.Log;

import com.google.gson.Gson;
import com.littlepudding.nettysdk.model.ConnectionAggrement;
import com.littlepudding.nettysdk.model.IInitializeData;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.ReferenceCountUtil;

/**
 * @author liuxiaofeng
 * @description netty的handler类
 */
public class NettyClientHandler extends SimpleChannelInboundHandler {
    public static final String TAG = NettyClientHandler.class.getSimpleName();
    private static final Object lock = new Object();
    private static Gson gson = new Gson();
    private boolean isForceClose = false;//是否是主动关闭，非断线重连
    private static final int RECONNECT_DELY_HANDLER = 5;//断线五秒重连
    private static final int TIMES_UNRECEIVED_PONG = 10;//10次没有收到pong就断开连接,三秒一次pong
    private static final int SIZE_LINKED_QUEUE = 4;
    private volatile int mUnReceivedPongTimes = 0;//没有收到pong的次数，初始化为0次
    public static LinkedList<String> sChannelActiveQueue = new LinkedList<>();//存储连线日志容器
    public static LinkedList<String> sChannelInActiveQueue = new LinkedList<>();//存储离线日志容器
    private ArrayList<IConnectionListener> connectionListeners;
    private boolean isConnect = false;//netty是否连接成功服务器
    private IConnectionService iConnectionService;
    private IInitializeData iInitializeData;

    public NettyClientHandler(boolean isForceClose, IInitializeData iInitializeData, ArrayList<IConnectionListener> connectionListeners,
                              IConnectionService iConnectionService) {
        this.isForceClose = isForceClose;
        this.iInitializeData = iInitializeData;
        this.connectionListeners = connectionListeners;
        this.iConnectionService = iConnectionService;
    }

    public void setForceClose(boolean forceClose) {
        isForceClose = forceClose;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent idleStateEvent = (IdleStateEvent) evt;
            Log.d(TAG, "userEventTriggered: status: " + idleStateEvent.state().name());
            switch (idleStateEvent.state()) {
                case WRITER_IDLE://写空闲
                    Log.d(TAG, "userEventTriggered: mUnReceivedPongTimes: " + mUnReceivedPongTimes);
                    break;
                case READER_IDLE://读空闲
                    mUnReceivedPongTimes++;
                    if (mUnReceivedPongTimes > TIMES_UNRECEIVED_PONG) {
                        mUnReceivedPongTimes = 0;
                        Log.d(TAG, "userEventTriggered: mUnReceivePongTimes >= TIMES_UNRECEIVED_PONG");
                        ctx.close();
                    } else {
                        if (iInitializeData.getHeartBeatMessage() != null && iInitializeData.getHeartBeatMessage().getData() != null) {
                            if (iInitializeData.getAggrement() == ConnectionAggrement.STRING) {
                                if (iInitializeData.getHeartBeatMessage().getData() instanceof String) {
                                    ctx.writeAndFlush(((String) iInitializeData.getHeartBeatMessage().getData()));
                                } else {
                                    ctx.writeAndFlush(gson.toJson(iInitializeData.getHeartBeatMessage().getData()));
                                }
                            } else if (iInitializeData.getAggrement() == ConnectionAggrement.JSON) {
                                ctx.writeAndFlush(gson.toJson(iInitializeData.getHeartBeatMessage().getData()));
                            } else if (iInitializeData.getAggrement() == ConnectionAggrement.PROTOBUF) {
                                ctx.writeAndFlush(iInitializeData.getHeartBeatMessage().getData());
                            }
                        }
                    }
                    break;
                default:
                    break;
            }
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        SocketChannel activeSocketChannel = (SocketChannel) ctx.channel();
        if (iInitializeData.getChannelActivieMessage() != null && iInitializeData.getChannelActivieMessage().getData() != null) {
            if (iInitializeData.getAggrement() == ConnectionAggrement.STRING) {
                if (iInitializeData.getChannelActivieMessage().getData() instanceof String) {
                    ctx.writeAndFlush(((String) iInitializeData.getChannelActivieMessage().getData()));
                } else {
                    ctx.writeAndFlush(iInitializeData.getChannelActivieMessage().getData());
                }
            } else if (iInitializeData.getAggrement() == ConnectionAggrement.JSON) {
                activeSocketChannel.writeAndFlush(gson.toJson(iInitializeData.getChannelActivieMessage().getData()));
            } else if (iInitializeData.getAggrement() == ConnectionAggrement.PROTOBUF) {
                activeSocketChannel.writeAndFlush(iInitializeData.getChannelActivieMessage().getData());
            }
        }

        synchronized (lock) {
            long currentTimeStamp = System.currentTimeMillis();
            sChannelActiveQueue.add(activeSocketChannel.id().toString() + ":" + activeSocketChannel.isOpen() + ":" + currentTimeStamp);

            for (String channelInfo : sChannelActiveQueue) {
                Log.d(TAG, "channelActive: " + channelInfo);
            }
            Log.d(TAG, "channelActive: size= " + sChannelActiveQueue.size());
            if (sChannelActiveQueue.size() > SIZE_LINKED_QUEUE) {
                Log.d(TAG, "channelActive: remove first channelinfo= " + sChannelActiveQueue.pollFirst());
            }
        }

        ctx.fireChannelActive();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        try {
            ctx.close();
            Log.d(TAG, "exceptionCaught: " + cause.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 销毁
     */
    public void destroy() {
        iConnectionService = null;
    }

    @Override
    protected void messageReceived(ChannelHandlerContext ctx, Object msg) {
        Object returnMsg = null;
        mUnReceivedPongTimes = 0;
        if (connectionListeners != null) {
            for (IConnectionListener iConnectionListener : connectionListeners) {
                if (!isConnect) {
                    isConnect = true;
                    iConnectionListener.onConnect();
                }
                if (iInitializeData.getAggrement() == ConnectionAggrement.STRING) {
                    returnMsg = iConnectionListener.onReceive(msg.toString());
                } else if (iInitializeData.getAggrement() == ConnectionAggrement.JSON) {
                    returnMsg = iConnectionListener.onReceive(gson.fromJson(msg.toString(), iConnectionListener.getMessageType()));
                } else if (iInitializeData.getAggrement() == ConnectionAggrement.PROTOBUF) {
                    returnMsg = iConnectionListener.onReceive(msg);
                }

                if (returnMsg != null) {
                    break;
                }
            }
        }


        if (returnMsg != null) {
            if (iInitializeData.getAggrement() == ConnectionAggrement.STRING) {
                if (returnMsg instanceof String) {
                    ReferenceCountUtil.release((String) returnMsg);
                } else {
                    ReferenceCountUtil.release(gson.toJson(returnMsg));
                }
            } else if (iInitializeData.getAggrement() == ConnectionAggrement.JSON) {
                ReferenceCountUtil.release(gson.toJson(returnMsg));//减少引用计数
            } else if (iInitializeData.getAggrement() == ConnectionAggrement.PROTOBUF) {
                ReferenceCountUtil.release(returnMsg);
            }
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (isForceClose) return;

        if (connectionListeners != null) {
            for (IConnectionListener iConnectionListener : connectionListeners) {
                isConnect = false;
                iConnectionListener.onDisConnect();
            }
        }

        SocketChannel inActiveSocketChannel = (SocketChannel) ctx.channel();
        Log.d(TAG, "channelInactive: " + inActiveSocketChannel.localAddress().getAddress() + ":" + inActiveSocketChannel.localAddress().getPort()
                + "channelId" + inActiveSocketChannel.id());
        mUnReceivedPongTimes = 0;
        synchronized (lock) {
            long currentTimeStamp = System.currentTimeMillis();
            String lastChannelInfo = sChannelInActiveQueue.peekLast();
            Log.d(TAG, "channelInactive: " + lastChannelInfo + ",正常的断线重连得和channelActive的id不同");
            Log.d(TAG, "channelInactive: 目前active latest channelId = " + lastChannelInfo.substring(0, lastChannelInfo.indexOf(":")));
            String latestChannelId = lastChannelInfo.substring(0, lastChannelInfo.indexOf(":"));
            sChannelInActiveQueue.add(inActiveSocketChannel.id().toString() + ":" + inActiveSocketChannel.isOpen() + ":" + currentTimeStamp);
            for (String channelInfo : sChannelInActiveQueue) {
                Log.d(TAG, "channelInactive: " + channelInfo);
            }
            Log.d(TAG, "channelInactive: size= " + sChannelInActiveQueue.size());
            if (sChannelInActiveQueue.size() > SIZE_LINKED_QUEUE) {
                Log.d(TAG, "channelInactive: remove first channelInfo=" + sChannelInActiveQueue.pollFirst());
            }
            if (latestChannelId.equals(inActiveSocketChannel.id().toString())) {
                Log.d(TAG, "channelInactive: channelId与last channelActive相同，reconnect");
                ctx.channel().eventLoop().schedule(new Runnable() {
                    @Override
                    public void run() {
                        iConnectionService.connect();
                    }
                }, RECONNECT_DELY_HANDLER, TimeUnit.SECONDS);
            } else {//如果channelActive的channelId和channelInactive callback的channelId不同表示有多余的重连  不重新连接
                Log.d(TAG, "channelInactive: channelId 与last channelActive不同，断开连接");
            }
        }
    }
}
