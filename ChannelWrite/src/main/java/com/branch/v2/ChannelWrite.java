package com.branch.v2;

import com.branch.v2.read.util.ApkUtils;
import com.branch.v2.read.model.ChannelModel;
import com.branch.v2.read.util.DataSource;
import com.branch.v2.read.util.Log;
import com.branch.v2.read.util.Pair;
import com.branch.v2.read.util.RandomAccessFileDataSource;
import com.branch.v2.read.zip.ZipFormatException;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;

/**
 * channel write
 */
public class ChannelWrite {

    public static void writeChannel(String inputApkPath, String outputApkPath, String channelName)
            throws IOException, ZipFormatException, ApkUtils.SignatureNotFoundException {

        File inputApkFile = new File(inputApkPath);
        RandomAccessFile randomAccessFile = new RandomAccessFile(inputApkFile, "rw");
        RandomAccessFileDataSource randomAccessFileDataSource = new RandomAccessFileDataSource(randomAccessFile);
        ApkUtils.ZipSections zipSections = ApkUtils.findZipSections(randomAccessFileDataSource);

        Pair<DataSource, Long> pairs = ApkUtils.findApkSigningBlock(randomAccessFileDataSource, zipSections);

        final long apkLength = randomAccessFile.length();

        DataSource apkSigningBlockSource = pairs.getFirst();

        final long apkSigningBlockOffset = pairs.getSecond();

        ChannelModel channelModel = new ChannelModel(channelName);
        byte[] channelByte = channelModel.getBytes();
        final int channelLength = channelByte.length;


        ByteBuffer apkSigningBlockSourceByteBuffer = apkSigningBlockSource.getByteBuffer(0, (int) apkSigningBlockSource.size());
        apkSigningBlockSourceByteBuffer.order(ByteOrder.LITTLE_ENDIAN);


        //apk signing block total size
        final int apkSigningBlockSize = (int) (apkSigningBlockSourceByteBuffer.getLong() + 8);


        /**
         * channel pair write byte
         * 8 size
         * + 4 key
         * + size-4 value
         */
        final int channelPairSize = 8 + 4 + channelLength;

        // old + channel +key(8)
        final int newApkBlockSize = apkSigningBlockSize + channelPairSize;


        // v2 signing real data
        final int v2Size = apkSigningBlockSize - 32;
        ByteBuffer apkSigningSchemeV2 = ByteBuffer.allocate(v2Size);
        apkSigningBlockSource.copyTo(8, v2Size, apkSigningSchemeV2);


        Log.log("apksigblock size: " + apkSigningBlockSize + " last: "
                + apkSigningBlockSourceByteBuffer.getLong(apkSigningBlockSourceByteBuffer.array().length - 24));


        //step 0 start
        final int newTotalSize = (int) (apkLength + channelPairSize);
        ByteBuffer
                newApkByteBuffer = ByteBuffer.allocate(newTotalSize);
        newApkByteBuffer.order(ByteOrder.LITTLE_ENDIAN);


        // step 1  copy all data before apk signing block
        randomAccessFileDataSource.copyTo(0, (int) apkSigningBlockOffset, newApkByteBuffer);

        //step 2 new block size
        newApkByteBuffer.putLong(newApkBlockSize - 8);

        //step 3 v2 signing info
        newApkByteBuffer.put(apkSigningSchemeV2.array());


        //step 4 channel pair
        /**
         *
         * channel
         * 8 size（value size +key size）
         * +4 key
         * +size-4 value
         */
        ByteBuffer channelNameByteBuffer = ByteBuffer.allocate(8); // Long.BYTES
        channelNameByteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        channelNameByteBuffer.putLong(channelLength + 4);
        channelNameByteBuffer.flip();
        newApkByteBuffer.put(channelNameByteBuffer.array());

        /**
         * 4 key
         * + size-4 value
         */
        ByteBuffer channelByteByteBuffer = ByteBuffer.allocate(4);
        channelByteByteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        channelByteByteBuffer.putInt(ApkUtils.APK_SIGNATURE_SCHEME_V2_CHANNEL_ID);
        channelByteByteBuffer.flip();
        newApkByteBuffer.put(channelByteByteBuffer.array());
        newApkByteBuffer.put(channelByte);

        //step 5 block size exclude this field
        newApkByteBuffer.putLong(newApkBlockSize - 8);

        //step 6  signing block  magic
        newApkByteBuffer.putLong(ApkUtils.APK_SIG_BLOCK_MAGIC_LO);
        newApkByteBuffer.putLong(ApkUtils.APK_SIG_BLOCK_MAGIC_HI);

        Log.log("getZipCentralDirectoryOffset : " + zipSections.getZipCentralDirectoryOffset());

        Log.log("last data length: " + (apkLength - zipSections.getZipCentralDirectoryOffset()));

        Log.log("byteBuffer left: " + (newApkByteBuffer.limit() - newApkByteBuffer.position()));

        //step 7 copy last Central Directory and End of Central Directory
        randomAccessFileDataSource.copyTo(zipSections.getZipCentralDirectoryOffset(),
                (int) (apkLength - zipSections.getZipCentralDirectoryOffset()), newApkByteBuffer);


        // get old central directory offset
        long oldOffsetCd = zipSections.getZipCentralDirectoryOffset();

        Log.log("oldOffsetCd: " + oldOffsetCd + " old total size: " + apkLength + " newTotalSize: " + newTotalSize + " channelLength: " + channelLength);

        // step 8 modify central directory offset
        /**
         * old eocd offset
         * + channel pair size
         * + 16
         */
        newApkByteBuffer.putInt((int) (zipSections.getZipEndOfCentralDirectoryOffset() + channelPairSize + 16)
                , (int) (oldOffsetCd + channelPairSize));

        Log.log("newoffset cd: " + newApkByteBuffer.getInt((int) (zipSections.getZipEndOfCentralDirectoryOffset() + channelPairSize + 16)));

        // step 9 end
        newApkByteBuffer.flip();

        randomAccessFile.close();

        // step output channel apk
        ApkUtils.outputChannelApk(newApkByteBuffer, inputApkPath, outputApkPath, channelName);


    }


}
