package com.littlepudding.nettysdk;

import android.annotation.SuppressLint;

import com.google.gson.Gson;
import com.google.protobuf.MessageLite;
import com.littlepudding.nettysdk.model.ConnectionAggrement;
import com.littlepudding.nettysdk.model.IInitializeData;
import com.littlepudding.nettysdk.model.ISendMessage;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.timeout.IdleStateHandler;
import io.reactivex.Observable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

/**
 * @author liuxiaofeng
 * @description netty实现服务封装核心类
 */
public class NettyConnectionService implements IConnectionService {
    private String host;//主机
    private int port;//端口
    private Bootstrap bootstrap;//netty辅助引导类
    private SocketChannel socketChannel;//SocketChannel是一种面向流连接只sockets套接字的可选择通道
    private NettyClientHandler nettyClientHandler;
    private static final int RECONNECT_DELAY = 3000;//重连时间间隔
    private ArrayList<IConnectionListener> connectionListeners;
    private Gson gson;
    private LinkedBlockingQueue<ISendMessage> messageQueue;
    //    private Thread sendThread;
    private IInitializeData iInitializeData;
    private ExecutorService service;

    @Override
    public void create(String host, int port, IInitializeData iInitializeData) {
        this.host = host;
        this.port = port;
        this.iInitializeData = iInitializeData;
        connectionListeners = new ArrayList<>();
        createBootstrap(iInitializeData);
        gson = new Gson();
        messageQueue = new LinkedBlockingQueue<>();
        createSendThread();
    }

    @Override
    public void addListener(IConnectionListener iConnectionListener) {
        connectionListeners.add(iConnectionListener);
    }

    @Override
    public void removeListener(IConnectionListener iConnectionListener) {
        connectionListeners.remove(iConnectionListener);
    }

    @SuppressLint("CheckResult")
    @Override
    public void connect() {
        if (socketChannel != null && socketChannel.isOpen()) {
            return;
        }
        if (bootstrap == null) {
            createBootstrap(iInitializeData);
        }

//        if (sendThread != null && !sendThread.isAlive()) {
//            createSendThread();
//        }
        try {
            Observable.timer(3, TimeUnit.SECONDS).subscribeOn(Schedulers.io()).subscribe(new Consumer<Long>() {
                @Override
                public void accept(Long aLong) throws InterruptedException {
                    ChannelFuture future = null;
                    future = bootstrap.connect(new InetSocketAddress(host, port)).sync();
                    if (future.isSuccess()) {
                        socketChannel = (SocketChannel) future.channel();
                    } else {
                        future.channel().closeFuture().sync();
                        if (socketChannel != null) {
                            socketChannel.eventLoop().schedule(new Runnable() {
                                @Override
                                public void run() {

                                }
                            }, RECONNECT_DELAY, TimeUnit.MILLISECONDS);
                        }
                    }
                }
            }, new Consumer<Throwable>() {
                @Override
                public void accept(Throwable throwable) throws Exception {
                    connect();
                    throwable.printStackTrace();
                }
            });
        } catch (Exception e) {
            Observable.timer(3, TimeUnit.SECONDS).subscribeOn(Schedulers.io()).subscribe(new Consumer<Long>() {
                @Override
                public void accept(Long aLong) throws Exception {
                    connect();
                }
            });
            e.printStackTrace();
        }
    }

    @Override
    public void disConnect() {
        if (socketChannel != null) {
            socketChannel.close();
        }
        nettyClientHandler.destroy();
        bootstrap = null;
        nettyClientHandler = null;
//        sendThread.interrupt();
        service.shutdown();
    }

    @Override
    public void sendMessage(ISendMessage sendMessage, boolean isNeedRetry) {
        if (isNeedRetry) {
            try {
                messageQueue.put(sendMessage);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } else {
            if (socketChannel != null) {
                if (iInitializeData.getAggrement() == ConnectionAggrement.STRING) {
                    if (sendMessage.getData() instanceof String) {
                        socketChannel.writeAndFlush((String) sendMessage.getData());
                    } else {
                        socketChannel.writeAndFlush(gson.toJson(sendMessage.getData()));
                    }
                } else if (iInitializeData.getAggrement() == ConnectionAggrement.JSON) {
                    socketChannel.writeAndFlush(gson.toJson(sendMessage.getData()));
                } else if (iInitializeData.getAggrement() == ConnectionAggrement.PROTOBUF) {
                    socketChannel.writeAndFlush(sendMessage.getData());
                }
            }
        }
    }

    @Override
    public boolean isConnect() {
        if (socketChannel != null) {
            return socketChannel.isOpen();
        }
        return false;
    }

    /**
     * 创建引导类
     *
     * @param iInitializeData 初始化数据
     */
    private void createBootstrap(final IInitializeData iInitializeData) {
        EventLoopGroup eventLoopGroup = new NioEventLoopGroup();
        bootstrap = new Bootstrap();
        bootstrap.channel(NioSocketChannel.class);
        bootstrap.option(ChannelOption.SO_KEEPALIVE, true);
        bootstrap.group(eventLoopGroup);
        bootstrap.remoteAddress(host, port);
        bootstrap.handler(new ChannelInitializer<io.netty.channel.socket.SocketChannel>() {

            @Override
            protected void initChannel(io.netty.channel.socket.SocketChannel socketChannel) {
                socketChannel.pipeline().addLast(new IdleStateHandler(3, 3, 0));
                socketChannel.pipeline().addLast("frameDecoder", new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 0, 4, 0, 4));
                socketChannel.pipeline().addLast("frameEncoder", new LengthFieldPrepender(4));
                if (iInitializeData.getAggrement() == ConnectionAggrement.PROTOBUF) {
                    socketChannel.pipeline().addLast(new ProtobufVarint32FrameDecoder())
                            .addLast(new ProtobufDecoder((MessageLite) iInitializeData.aggrementMessageData()))
                            .addLast(new ProtobufVarint32LengthFieldPrepender())
                            .addLast(new ProtobufEncoder());
                } else {
                    socketChannel.pipeline().addLast("decoder", new StringDecoder());
                    socketChannel.pipeline().addLast("encoder", new StringEncoder());
                }
                nettyClientHandler = new NettyClientHandler(false, iInitializeData, connectionListeners, NettyConnectionService.this);
                socketChannel.pipeline().addLast(nettyClientHandler);
            }
        });
    }

    /**
     * 创建发送消息的线程池
     */
    private void createSendThread() {
        service = Executors.newFixedThreadPool(1);
        service.submit(new Runnable() {
            @Override
            public void run() {
                ISendMessage message = null;
                while (true) {
                    message = messageQueue.poll();
                    boolean isSendSuccess = false;
                    try {
                        while (!isSendSuccess && message != null) {
                            if (socketChannel != null) {
                                if (iInitializeData.getAggrement() == ConnectionAggrement.STRING) {
                                    if (message.getData() instanceof String) {
                                        isSendSuccess = socketChannel.writeAndFlush((String) message.getData()).await(3000);
                                    } else {
                                        isSendSuccess = socketChannel.writeAndFlush(gson.toJson(message.getData())).await(3000);
                                    }
                                } else if (iInitializeData.getAggrement() == ConnectionAggrement.JSON) {
                                    isSendSuccess = socketChannel.writeAndFlush(gson.toJson(message.getData())).await(3000);
                                } else if (iInitializeData.getAggrement() == ConnectionAggrement.PROTOBUF) {
                                    isSendSuccess = socketChannel.writeAndFlush(message.getData()).await(3000);
                                }
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });
//        if (sendThread != null) {
//            sendThread.interrupt();
//        }
//        sendThread = new Thread(new Runnable() {
//            @Override
//            public void run() {
//                ISendMessage message = null;
//                while (true) {
//                    message = messageQueue.poll();
//                    boolean isSendSuccess = false;
//                    try {
//                        while (!isSendSuccess && message != null) {
//                            if (socketChannel != null) {
//                                if (iInitializeData.getAggrement() == ConnectionAggrement.STRING) {
//                                    if (message.getData() instanceof String) {
//                                        isSendSuccess = socketChannel.writeAndFlush((String) message.getData()).await(3000);
//                                    } else {
//                                        isSendSuccess = socketChannel.writeAndFlush(gson.toJson(message.getData())).await(3000);
//                                    }
//                                } else if (iInitializeData.getAggrement() == ConnectionAggrement.JSON) {
//                                    isSendSuccess = socketChannel.writeAndFlush(gson.toJson(message.getData())).await(3000);
//                                } else if (iInitializeData.getAggrement() == ConnectionAggrement.PROTOBUF) {
//                                    isSendSuccess = socketChannel.writeAndFlush(message.getData()).await(3000);
//                                }
//                            }
//                        }
//                    } catch (Exception e) {
//                        e.printStackTrace();
//                    }
//                }
//            }
//        });
//        sendThread.start();
    }

}
