package com.example.nettydemo;

import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.carplusgo.commonbase.protobuf.CarplusgoMessage;
import com.littlepudding.nettysdk.IConnectionListener;
import com.littlepudding.nettysdk.IConnectionService;
import com.littlepudding.nettysdk.NettyConnectionService;
import com.littlepudding.nettysdk.model.ConnectionAggrement;
import com.littlepudding.nettysdk.model.IInitializeData;
import com.littlepudding.nettysdk.model.ISendMessage;

import java.lang.reflect.Type;

public class MainActivity extends AppCompatActivity implements IConnectionListener {
    private IConnectionService iConnectionService;
    private IInitializeData iInitializeData;
    public static final String TAG = MainActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        findViewById(R.id.tv_test).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                createConnection();
            }
        });

        findViewById(R.id.tv_send_message).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendMessage();
            }
        });
    }


    private void createConnection() {
        iConnectionService = new NettyConnectionService();
        iInitializeData = new IInitializeData() {
            @Override
            public ConnectionAggrement getAggrement() {
                return ConnectionAggrement.PROTOBUF;
            }

            @Override
            public ISendMessage getHeartBeatMessage() {
                CarplusgoMessage.Request pingMessage = CarplusgoMessage.Request.newBuilder()
                        .setMsgType(CarplusgoMessage.MSG_TYPE.PING)
                        .build();
                return new SendMessage(pingMessage);
            }

            @Override
            public ISendMessage getChannelActivieMessage() {
                CarplusgoMessage.Request registerMsg = CarplusgoMessage.Request.newBuilder()
                        .build();
                return new SendMessage(registerMsg);
            }

            @Override
            public Object aggrementMessageData() {
                return CarplusgoMessage.Response.getDefaultInstance();
            }
        };
        iConnectionService.create("192.168.21.74", 9998, iInitializeData);
        iConnectionService.addListener(this);
        iConnectionService.connect();
    }

    private void sendMessage() {
        CarplusgoMessage.Request request = CarplusgoMessage.Request.newBuilder()
                .build();
        iConnectionService.sendMessage(new SendMessage(request), false);
    }


    @Override
    public void onConnect() {
        Log.e(TAG, "onConnect: ");
    }

    @Override
    public void onDisConnect() {
        Log.e(TAG, "onDisConnect: ");
    }

    @Override
    public Object onReceive(Object data) {
        return null;
    }

    @Override
    public void onReConnect() {

    }

    @Override
    public Type getMessageType() {
        return null;
    }
}
