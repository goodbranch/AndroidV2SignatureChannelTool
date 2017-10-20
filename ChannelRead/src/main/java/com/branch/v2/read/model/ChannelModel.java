package com.branch.v2.read.model;

import com.branch.v2.read.util.StringUtil;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.Serializable;
import java.util.HashMap;

/**
 * channel model 方便扩展
 */
public class ChannelModel implements Serializable {

    private String channelName;

    public ChannelModel(String channelName) {
        this.channelName = channelName;
    }

    public byte[] getBytes() {
        Gson gson = new Gson();
        String channel = gson.toJson(this);

        return channel.getBytes();
    }

    public static ChannelModel fromGson(String channelGson) {

        if (StringUtil.isEmpty(channelGson)) {
            return new ChannelModel("");
        }

        Gson gson = new Gson();
        return gson.fromJson(channelGson, new TypeToken<ChannelModel>() {
        }.getType());

    }

    @Override
    public String toString() {
        return "ChannelModel{" +
                "channelName='" + channelName + '\'' +
                '}';
    }
}
