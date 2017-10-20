package com.branch.v2;

import com.branch.v2.read.ChannelRead;
import com.branch.v2.read.model.ChannelModel;
import com.branch.v2.read.util.ApkUtils;
import com.branch.v2.read.util.Log;
import com.branch.v2.read.util.StringUtil;
import com.branch.v2.read.zip.ZipFormatException;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class JCommander {

    // -r read channel 1, -w write channel 2
    private int actionType = -1;

    //
    private String inputApkFile;

    // -o
    private String outChannelDir;

    //-c
    private String channelFile;

    // -help

    public void parseCommand(String[] commands) {
        if (commands == null || commands.length == 0) {
            throw new IllegalArgumentException("error command");
        }

        for (int i = 0; i < commands.length; i++) {
            String param = commands[i];

            switch (param) {
                case "-r":
                    actionType = 1;
                    i++;
                    inputApkFile = commands[i];
                    break;
                case "-w":
                    actionType = 2;
                    i++;
                    inputApkFile = commands[i];
                    break;
                case "-o":
                    i++;
                    outChannelDir = commands[i];
                    break;
                case "-c":
                    i++;
                    channelFile = commands[i];
                    break;
            }

        }


        checkCommander();
    }


    private void checkCommander() {

        if (actionType == -1) {
            throw new IllegalArgumentException("需要设置是读渠道(-r)还是写渠道(-w)");
        }

        switch (actionType) {
            case 1:
                if (StringUtil.isEmpty(inputApkFile)) {
                    throw new IllegalArgumentException("需要设置读取渠道的apk文件，-i (apk path)");
                }
                break;
            case 2:
                if (StringUtil.isEmpty(inputApkFile)) {
                    throw new IllegalArgumentException("需要设置读取渠道的apk文件，-i (apk path)");
                }
                if (StringUtil.isEmpty(outChannelDir)) {
                    throw new IllegalArgumentException("需要设置生成渠道apk文件存储目录，-o (out dir)");
                }
                if (StringUtil.isEmpty(channelFile)) {
                    throw new IllegalArgumentException("需要设置渠道文件，-c (channel path)");
                }

                break;
        }

    }


    public void doAction() {

        switch (actionType) {
            case 1:
                ChannelModel channelModel = ChannelRead.getChannel(inputApkFile);
                Log.log("read channel: " + channelModel);
                break;
            case 2:

                try {
                    long startTime = System.currentTimeMillis();
                    BufferedReader byteArrayInputStream = new BufferedReader(new FileReader(channelFile));

                    String channelName = null;
                    while ((channelName = byteArrayInputStream.readLine()) != null) {
                        ChannelWrite.writeChannel(inputApkFile, outChannelDir, channelName);
                    }
                    Log.log("cost time: " + (System.currentTimeMillis() - startTime) / 1000 + "s");
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (ZipFormatException e) {
                    e.printStackTrace();
                } catch (ApkUtils.SignatureNotFoundException e) {
                    e.printStackTrace();
                }

                break;
        }

    }


}
