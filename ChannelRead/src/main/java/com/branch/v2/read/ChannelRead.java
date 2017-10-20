package com.branch.v2.read;

import com.branch.v2.read.model.ChannelModel;
import com.branch.v2.read.util.ApkUtils;
import com.branch.v2.read.util.DataSource;
import com.branch.v2.read.util.Pair;
import com.branch.v2.read.util.RandomAccessFileDataSource;
import com.branch.v2.read.zip.ZipFormatException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;

public class ChannelRead {

    public static ChannelModel getChannel(String apkFile) {
        try {
            RandomAccessFileDataSource randomAccessFileDataSource = new RandomAccessFileDataSource(new RandomAccessFile(apkFile, "r"));

            ApkUtils.ZipSections zipSections = ApkUtils.findZipSections(randomAccessFileDataSource);
            Pair<DataSource, Long> dataSourceLongPair = ApkUtils.findApkSigningBlock(randomAccessFileDataSource, zipSections);

            ByteBuffer apkSigningBlock = dataSourceLongPair.getFirst().getByteBuffer(0, (int) dataSourceLongPair.getFirst().size());
            apkSigningBlock.order(ByteOrder.LITTLE_ENDIAN);
            HashMap<Integer, ByteBuffer> hashMap = ApkUtils.findApkSignatureSchemeV2Block(apkSigningBlock);

            ByteBuffer channelBuffer = hashMap.get(ApkUtils.APK_SIGNATURE_SCHEME_V2_CHANNEL_ID);
            if (channelBuffer != null) {
                byte[] result = ApkUtils.getBytes(channelBuffer);

                randomAccessFileDataSource.close();

                return ChannelModel.fromGson(new String(result));
            }

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (ApkUtils.SignatureNotFoundException e) {
            e.printStackTrace();
        } catch (ZipFormatException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }


        return new ChannelModel("");
    }

}
