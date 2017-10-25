/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.branch.v2.read.util;


import com.branch.v2.read.zip.ZipFormatException;
import com.branch.v2.read.zip.ZipUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * APK utilities.
 */
public class ApkUtils {


    public static final long APK_SIG_BLOCK_MAGIC_HI = 0x3234206b636f6c42L;
    public static final long APK_SIG_BLOCK_MAGIC_LO = 0x20676953204b5041L;
    private static final int APK_SIG_BLOCK_MIN_SIZE = 32;

    private static final int APK_SIGNATURE_SCHEME_V2_BLOCK_ID = 0x7109871a;

    public static final int APK_SIGNATURE_SCHEME_V2_CHANNEL_ID = 0x0010086a;


    /**
     * Finds the main ZIP sections of the provided APK.
     *
     * @throws IOException        if an I/O error occurred while reading the APK
     * @throws ZipFormatException if the APK is malformed
     */
    public static ZipSections findZipSections(DataSource apk)
            throws IOException, ZipFormatException {
        Pair<ByteBuffer, Long> eocdAndOffsetInFile =
                ZipUtils.findZipEndOfCentralDirectoryRecord(apk);
        if (eocdAndOffsetInFile == null) {
            throw new ZipFormatException("ZIP End of Central Directory record not found");
        }

        ByteBuffer eocdBuf = eocdAndOffsetInFile.getFirst();
        long eocdOffset = eocdAndOffsetInFile.getSecond();
        eocdBuf.order(ByteOrder.LITTLE_ENDIAN);
        long cdStartOffset = ZipUtils.getZipEocdCentralDirectoryOffset(eocdBuf);
        if (cdStartOffset > eocdOffset) {
            throw new ZipFormatException(
                    "ZIP Central Directory start offset out of range: " + cdStartOffset
                            + ". ZIP End of Central Directory offset: " + eocdOffset);
        }

        long cdSizeBytes = ZipUtils.getZipEocdCentralDirectorySizeBytes(eocdBuf);
        long cdEndOffset = cdStartOffset + cdSizeBytes;
        if (cdEndOffset > eocdOffset) {
            throw new ZipFormatException(
                    "ZIP Central Directory overlaps with End of Central Directory"
                            + ". CD end: " + cdEndOffset
                            + ", EoCD start: " + eocdOffset);
        }

        int cdRecordCount = ZipUtils.getZipEocdCentralDirectoryTotalRecordCount(eocdBuf);

        return new ZipSections(
                cdStartOffset,
                cdSizeBytes,
                cdRecordCount,
                eocdOffset,
                eocdBuf);
    }

    /**
     * Information about the ZIP sections of an APK.
     */
    public static class ZipSections {
        private final long mCentralDirectoryOffset;
        private final long mCentralDirectorySizeBytes;
        private final int mCentralDirectoryRecordCount;
        private final long mEocdOffset;
        private final ByteBuffer mEocd;

        public ZipSections(
                long centralDirectoryOffset,
                long centralDirectorySizeBytes,
                int centralDirectoryRecordCount,
                long eocdOffset,
                ByteBuffer eocd) {
            mCentralDirectoryOffset = centralDirectoryOffset;
            mCentralDirectorySizeBytes = centralDirectorySizeBytes;
            mCentralDirectoryRecordCount = centralDirectoryRecordCount;
            mEocdOffset = eocdOffset;
            mEocd = eocd;
        }

        /**
         * Returns the start offset of the ZIP Central Directory. This value is taken from the
         * ZIP End of Central Directory record.
         */
        public long getZipCentralDirectoryOffset() {
            return mCentralDirectoryOffset;
        }

        /**
         * Returns the size (in bytes) of the ZIP Central Directory. This value is taken from the
         * ZIP End of Central Directory record.
         */
        public long getZipCentralDirectorySizeBytes() {
            return mCentralDirectorySizeBytes;
        }

        /**
         * Returns the number of records in the ZIP Central Directory. This value is taken from the
         * ZIP End of Central Directory record.
         */
        public int getZipCentralDirectoryRecordCount() {
            return mCentralDirectoryRecordCount;
        }

        /**
         * Returns the start offset of the ZIP End of Central Directory record. The record extends
         * until the very end of the APK.
         */
        public long getZipEndOfCentralDirectoryOffset() {
            return mEocdOffset;
        }

        /**
         * Returns the contents of the ZIP End of Central Directory.
         */
        public ByteBuffer getZipEndOfCentralDirectory() {
            return mEocd;
        }
    }

    /**
     * Sets the offset of the start of the ZIP Central Directory in the APK's ZIP End of Central
     * Directory record.
     *
     * @param zipEndOfCentralDirectory APK's ZIP End of Central Directory record
     * @param offset                   offset of the ZIP Central Directory relative to the start of the archive. Must
     *                                 be between {@code 0} and {@code 2^32 - 1} inclusive.
     */
    public static void setZipEocdCentralDirectoryOffset(
            ByteBuffer zipEndOfCentralDirectory, long offset) {
        ByteBuffer eocd = zipEndOfCentralDirectory.slice();
        eocd.order(ByteOrder.LITTLE_ENDIAN);
        ZipUtils.setZipEocdCentralDirectoryOffset(eocd, offset);
    }

    /**
     * Returns the APK Signature Scheme v2 block contained in the provided APK file and the
     * additional information relevant for verifying the block against the file.
     *
     * @throws SignatureNotFoundException if the APK is not signed using APK Signature Scheme v2
     * @throws IOException                if an I/O error occurs while reading the APK
     */
    public static ByteBuffer findSignature(
            DataSource apk, ApkUtils.ZipSections zipSections)
            throws IOException, SignatureNotFoundException {
        // Find the APK Signing Block. The block immediately precedes the Central Directory.
        ByteBuffer eocd = zipSections.getZipEndOfCentralDirectory();
        Pair<DataSource, Long> apkSigningBlockAndOffset = findApkSigningBlock(apk, zipSections);
        DataSource apkSigningBlock = apkSigningBlockAndOffset.getFirst();
        long apkSigningBlockOffset = apkSigningBlockAndOffset.getSecond();
        ByteBuffer apkSigningBlockBuf =
                apkSigningBlock.getByteBuffer(0, (int) apkSigningBlock.size());
        apkSigningBlockBuf.order(ByteOrder.LITTLE_ENDIAN);

        // Find the APK Signature Scheme v2 Block inside the APK Signing Block.
        HashMap<Integer, ByteBuffer> apkSignatureSchemeV2Block =
                findApkSignatureSchemeV2Block(apkSigningBlockBuf);

        return apkSignatureSchemeV2Block.get(APK_SIGNATURE_SCHEME_V2_BLOCK_ID);
    }

    /**
     * Returns the APK Signing Block and its offset in the provided APK.
     *
     * @throws SignatureNotFoundException if the APK does not contain an APK Signing Block
     */
    public static Pair<DataSource, Long> findApkSigningBlock(
            DataSource apk, ApkUtils.ZipSections zipSections)
            throws IOException, SignatureNotFoundException {
        // FORMAT:
        // OFFSET       DATA TYPE  DESCRIPTION
        // * @+0  bytes uint64:    size in bytes (excluding this field)
        // * @+8  bytes payload
        // * @-24 bytes uint64:    size in bytes (same as the one above)
        // * @-16 bytes uint128:   magic

        long centralDirStartOffset = zipSections.getZipCentralDirectoryOffset();
        long centralDirEndOffset =
                centralDirStartOffset + zipSections.getZipCentralDirectorySizeBytes();
        long eocdStartOffset = zipSections.getZipEndOfCentralDirectoryOffset();
        if (centralDirEndOffset != eocdStartOffset) {
            throw new SignatureNotFoundException(
                    "ZIP Central Directory is not immediately followed by End of Central Directory"
                            + ". CD end: " + centralDirEndOffset
                            + ", EoCD start: " + eocdStartOffset);
        }

        if (centralDirStartOffset < APK_SIG_BLOCK_MIN_SIZE) {
            throw new SignatureNotFoundException(
                    "APK too small for APK Signing Block. ZIP Central Directory offset: "
                            + centralDirStartOffset);
        }
        // Read the magic and offset in file from the footer section of the block:
        // * uint64:   size of block
        // * 16 bytes: magic
        ByteBuffer footer = apk.getByteBuffer(centralDirStartOffset - 24, 24);
        footer.order(ByteOrder.LITTLE_ENDIAN);
        if ((footer.getLong(8) != APK_SIG_BLOCK_MAGIC_LO)
                || (footer.getLong(16) != APK_SIG_BLOCK_MAGIC_HI)) {
            throw new SignatureNotFoundException(
                    "No APK Signing Block before ZIP Central Directory");
        }
        // Read and compare size fields
        long apkSigBlockSizeInFooter = footer.getLong(0);
        if ((apkSigBlockSizeInFooter < footer.capacity())
                || (apkSigBlockSizeInFooter > Integer.MAX_VALUE - 8)) {
            throw new SignatureNotFoundException(
                    "APK Signing Block size out of range: " + apkSigBlockSizeInFooter);
        }
        int totalSize = (int) (apkSigBlockSizeInFooter + 8);
        long apkSigBlockOffset = centralDirStartOffset - totalSize;
        if (apkSigBlockOffset < 0) {
            throw new SignatureNotFoundException(
                    "APK Signing Block offset out of range: " + apkSigBlockOffset);
        }
        ByteBuffer apkSigBlock = apk.getByteBuffer(apkSigBlockOffset, 8);
        apkSigBlock.order(ByteOrder.LITTLE_ENDIAN);
        long apkSigBlockSizeInHeader = apkSigBlock.getLong(0);
        if (apkSigBlockSizeInHeader != apkSigBlockSizeInFooter) {
            throw new SignatureNotFoundException(
                    "APK Signing Block sizes in header and footer do not match: "
                            + apkSigBlockSizeInHeader + " vs " + apkSigBlockSizeInFooter);
        }
        return Pair.of(apk.slice(apkSigBlockOffset, totalSize), apkSigBlockOffset);
    }

    public static HashMap<Integer, ByteBuffer> findApkSignatureSchemeV2Block(
            ByteBuffer apkSigningBlock) throws SignatureNotFoundException {
        checkByteOrderLittleEndian(apkSigningBlock);
        // FORMAT:
        // OFFSET       DATA TYPE  DESCRIPTION
        // * @+0  bytes uint64:    size in bytes (excluding this field)
        // * @+8  bytes pairs
        // * @-24 bytes uint64:    size in bytes (same as the one above)
        // * @-16 bytes uint128:   magic
        ByteBuffer pairs = sliceFromTo(apkSigningBlock, 8, apkSigningBlock.capacity() - 24);

        HashMap<Integer, ByteBuffer> map = new HashMap<>();
        int entryCount = 0;
        while (pairs.hasRemaining()) {
            entryCount++;
            if (pairs.remaining() < 8) {
                throw new SignatureNotFoundException(
                        "Insufficient data to read size of APK Signing Block entry #" + entryCount);
            }
            long lenLong = pairs.getLong();
            if ((lenLong < 4) || (lenLong > Integer.MAX_VALUE)) {
                throw new SignatureNotFoundException(
                        "APK Signing Block entry #" + entryCount
                                + " size out of range: " + lenLong);
            }
            int len = (int) lenLong;
            int nextEntryPos = pairs.position() + len;
            if (len > pairs.remaining()) {
                throw new SignatureNotFoundException(
                        "APK Signing Block entry #" + entryCount + " size out of range: " + len
                                + ", available: " + pairs.remaining());
            }
            int id = pairs.getInt();

            map.put(id, getByteBuffer(pairs, len - 4));

            pairs.position(nextEntryPos);
        }

        return map;
    }

    /**
     * Returns new byte buffer whose content is a shared subsequence of this buffer's content
     * between the specified start (inclusive) and end (exclusive) positions. As opposed to
     * {@link ByteBuffer#slice()}, the returned buffer's byte order is the same as the source
     * buffer's byte order.
     */
    private static ByteBuffer sliceFromTo(ByteBuffer source, int start, int end) {
        if (start < 0) {
            throw new IllegalArgumentException("start: " + start);
        }
        if (end < start) {
            throw new IllegalArgumentException("end < start: " + end + " < " + start);
        }
        int capacity = source.capacity();
        if (end > source.capacity()) {
            throw new IllegalArgumentException("end > capacity: " + end + " > " + capacity);
        }
        int originalLimit = source.limit();
        int originalPosition = source.position();
        try {
            source.position(0);
            source.limit(end);
            source.position(start);
            ByteBuffer result = source.slice();
            result.order(source.order());
            return result;
        } finally {
            source.position(0);
            source.limit(originalLimit);
            source.position(originalPosition);
        }
    }

    /**
     * Relative <em>get</em> method for reading {@code size} number of bytes from the current
     * position of this buffer.
     * <p>
     * <p>This method reads the next {@code size} bytes at this buffer's current position,
     * returning them as a {@code ByteBuffer} with start set to 0, limit and capacity set to
     * {@code size}, byte order set to this buffer's byte order; and then increments the position by
     * {@code size}.
     */
    private static ByteBuffer getByteBuffer(ByteBuffer source, int size)
            throws BufferUnderflowException {
        if (size < 0) {
            throw new IllegalArgumentException("size: " + size);
        }
        int originalLimit = source.limit();
        int position = source.position();
        int limit = position + size;
        if ((limit < position) || (limit > originalLimit)) {
            throw new BufferUnderflowException();
        }
        source.limit(limit);
        try {
            ByteBuffer result = source.slice();
            result.order(source.order());
            source.position(limit);
            return result;
        } finally {
            source.limit(originalLimit);
        }
    }

    /**
     * get data from byteBuffer
     *
     * @param byteBuffer buffer
     * @return useful data
     */
    public static byte[] getBytes(final ByteBuffer byteBuffer) {
        final byte[] array = byteBuffer.array();
        final int arrayOffset = byteBuffer.arrayOffset();
        return Arrays.copyOfRange(array, arrayOffset + byteBuffer.position(),
                arrayOffset + byteBuffer.limit());
    }


    public static void outputChannelApk(ByteBuffer apk, String inputApkFile, String outputApkDir, String channelName) throws IOException {


        File apkFile = new File(inputApkFile);
        String name = apkFile.getName();
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        String date = format.format(new Date());
        if (name.endsWith(".apk")) {
            int apkIndex = name.lastIndexOf(".apk");
            name = name.substring(0, apkIndex);
        }
        name += "-" + channelName + "-" + date + ".apk";

        Log.log("output: " + (outputApkDir + name));
        try {
            FileOutputStream fileOutputStream = new FileOutputStream(outputApkDir + name);
            fileOutputStream.write(apk.array());
            fileOutputStream.close();
            fileOutputStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private static void checkByteOrderLittleEndian(ByteBuffer buffer) {
        if (buffer.order() != ByteOrder.LITTLE_ENDIAN) {
            throw new IllegalArgumentException("ByteBuffer byte order must be little endian");
        }
    }

    public static class SignatureNotFoundException extends Exception {
        private static final long serialVersionUID = 1L;

        public SignatureNotFoundException(String message) {
            super(message);
        }

        public SignatureNotFoundException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
