/*
 * Copyright 2014 NAVER Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.navercorp.pinpoint.common.util;

import static org.junit.Assert.*;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.apache.thrift.TException;
import org.apache.thrift.protocol.TCompactProtocol;
import org.apache.thrift.transport.TMemoryBuffer;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.primitives.Ints;

public class BytesUtilsTest {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Test
    public void testStringLongLongToBytes() {
        BytesUtils.stringLongLongToBytes("123", 3, 1, 2);
        try {
            BytesUtils.stringLongLongToBytes("123", 2, 1, 2);
            Assert.fail("fail");
        } catch (IndexOutOfBoundsException ignore) {
        }
    }

    @Test
    public void testStringLongLongToBytes2() {
        byte[] bytes = BytesUtils.stringLongLongToBytes("123", 10, 1, 2);
        String s = BytesUtils.toStringAndRightTrim(bytes, 0, 10);
        Assert.assertEquals("123", s);
        long l = BytesUtils.bytesToLong(bytes, 10);
        Assert.assertEquals(l, 1);
        long l2 = BytesUtils.bytesToLong(bytes, 10 + BytesUtils.LONG_BYTE_LENGTH);
        Assert.assertEquals(l2, 2);
    }

    @Test
    public void testRightTrim() {
        String trim = BytesUtils.trimRight("test  ");
        Assert.assertEquals("test", trim);

        String trim1 = BytesUtils.trimRight("test");
        Assert.assertEquals("test", trim1);

        String trim2 = BytesUtils.trimRight("  test");
        Assert.assertEquals("  test", trim2);

    }

    @Test
    public void testInt() {
        int i = Integer.MAX_VALUE - 5;
        checkInt(i);
        checkInt(23464);
    }

    private void checkInt(int i) {
        byte[] bytes = Ints.toByteArray(i);
        int i2 = BytesUtils.bytesToInt(bytes, 0);
        Assert.assertEquals(i, i2);
        int i3 = Ints.fromByteArray(bytes);
        Assert.assertEquals(i, i3);
    }

    @Test
    public void testAddStringLong() {
        byte[] testAgents = BytesUtils.add("testAgent", 11L);
        byte[] buf = ByteBuffer.allocate(17).put("testAgent".getBytes()).putLong(11L).array();
        Assert.assertArrayEquals(testAgents, buf);
    }

    @Test
    public void testAddStringLong_NullError() {
        try {
            BytesUtils.add((String) null, 11L);
            Assert.fail();
        } catch (NullPointerException ignore) {
        }
    }

    @Test
    public void testToFixedLengthBytes() {
        byte[] testValue = BytesUtils.toFixedLengthBytes("test", 10);
        Assert.assertEquals(testValue.length, 10);
        Assert.assertEquals(testValue[5], 0);

        try {
            BytesUtils.toFixedLengthBytes("test", 2);
            Assert.fail();
        } catch (IndexOutOfBoundsException ignore) {
        }

        try {
            BytesUtils.toFixedLengthBytes("test", -1);
            Assert.fail();
        } catch (IndexOutOfBoundsException ignore) {
        }

        byte[] testValue2 = BytesUtils.toFixedLengthBytes(null, 10);
        Assert.assertEquals(testValue2.length, 10);

    }

    @Test
    public void testMerge() {
        byte[] b1 = new byte[] { 1, 2 };
        byte[] b2 = new byte[] { 3, 4 };

        byte[] b3 = BytesUtils.merge(b1, b2);

        Assert.assertTrue(Arrays.equals(new byte[] { 1, 2, 3, 4 }, b3));
    }

    @Test
    public void testZigZag() {
        testEncodingDecodingZigZag(0);
        testEncodingDecodingZigZag(1);
        testEncodingDecodingZigZag(2);
        testEncodingDecodingZigZag(3);
    }

    private void testEncodingDecodingZigZag(int value) {
        int encode = BytesUtils.intToZigZag(value);
        int decode = BytesUtils.zigzagToInt(encode);
        Assert.assertEquals(value, decode);
    }

    @Test
    public void compactProtocolVint() throws TException {
        TMemoryBuffer tMemoryBuffer = writeVInt32(BytesUtils.zigzagToInt(64));
        logger.debug("length:{}", tMemoryBuffer.length());

        TMemoryBuffer tMemoryBuffer2 = writeVInt32(64);
        logger.debug("length:{}", tMemoryBuffer2.length());

    }

    private TMemoryBuffer writeVInt32(int i) throws TException {
        TMemoryBuffer tMemoryBuffer = new TMemoryBuffer(10);
        TCompactProtocol tCompactProtocol = new TCompactProtocol(tMemoryBuffer);
        tCompactProtocol.writeI32(i);
        return tMemoryBuffer;
    }

    @Test
    public void testWriteBytes1() {
        byte[] buffer = new byte[10];
        byte[] write = new byte[] { 1, 2, 3, 4 };

        Assert.assertEquals(BytesUtils.writeBytes(buffer, 0, write), write.length);
        Assert.assertArrayEquals(Arrays.copyOf(buffer, write.length), write);
    }

    @Test
    public void testWriteBytes2() {
        byte[] buffer = new byte[10];
        byte[] write = new byte[] { 1, 2, 3, 4 };
        int startOffset = 1;
        Assert.assertEquals(BytesUtils.writeBytes(buffer, startOffset, write), write.length + startOffset);
        Assert.assertArrayEquals(Arrays.copyOfRange(buffer, startOffset, write.length + startOffset), write);
    }

    @Test
    public void testAppropriateWriteBytes() {
        byte[] dst = new byte[10];
        byte[] src = new byte[5];
        src[0] = 1;
        src[1] = 2;
        src[2] = 3;
        src[3] = 4;
        src[4] = 5;
        // proper return?
        Assert.assertEquals(3, BytesUtils.writeBytes(dst, 1, src, 2, 2));
        // successful write?
        Assert.assertEquals(3, dst[1]);
        Assert.assertEquals(4, dst[2]);
    }

    @Test
    public void testOverflowDestinationWriteBytes() {
        byte[] dst = new byte[5];
        byte[] src = new byte[10];
        for (int i = 0; i < 10; i++) {
            src[i] = (byte) (i + 1);
        }
        try {
            // overflow!
            BytesUtils.writeBytes(dst, 0, src);
            // if it does not catch any errors, it means memory leak!
            fail("invalid memory access");
        } catch (Exception e) {
            // nice
        }
    }

    @Test
    public void testAppropriateBytesToLong() {
        byte[] such_long = new byte[12];
        int i;
        for (i = 0; i < 12; i++) {
            such_long[i] = (byte) ((i << 4) + i);
        }
        Assert.assertEquals(0x33445566778899AAl, BytesUtils.bytesToLong(such_long, 3));
    }

    @Test
    public void testOverflowBytesToLong() {
        byte[] such_long = new byte[12];
        int i;
        for (i = 0; i < 12; i++) {
            such_long[i] = (byte) ((i << 4) + i);
        }
        try {
            // overflow!
            BytesUtils.bytesToLong(such_long, 9);
            // if it does not catch any errors, it means memory leak!
            fail("invalid memory access");
        } catch (Exception e) {
            // nice
        }
    }

    @Test
    public void testWriteLong() {
        try {
            BytesUtils.writeLong(1234, null, 0);
            fail("null pointer accessed");
        } catch (Exception e) {

        }
        byte[] such_long = new byte[13];
        try {
            BytesUtils.writeLong(1234, such_long, -1);
            fail("negative offset did not catched");
        } catch (Exception e) {

        }
        try {
            BytesUtils.writeLong(2222, such_long, 9);
            fail("index out of range exception did not catched");
        } catch (Exception e) {

        }
        BytesUtils.writeLong(-1l, such_long, 2);
        for (int i = 2; i < 10; i++) {
            Assert.assertEquals((byte) 0xFF, such_long[i]);
        }
    }

    @Test
    public void testTrimRight() {
        String testStr = new String();
        // no space
        testStr = "Shout-out! EE!";
        Assert.assertEquals("Shout-out! EE!", BytesUtils.trimRight(testStr));
        // right spaced
        testStr = "Shout-out! YeeYee!       ";
        Assert.assertEquals("Shout-out! YeeYee!", BytesUtils.trimRight(testStr));
    }

    @Test
    public void testByteTrimRight() {
        String testStr = new String();
        // no space
        testStr = "Shout-out! EE!";
        byte[] testByte1 = new byte[testStr.length()];
        for (int i = 0; i < testByte1.length; i++) {
            testByte1[i] = (byte) testStr.charAt(i);
        }
        Assert.assertEquals("out-out!", BytesUtils.toStringAndRightTrim(testByte1, 2, 9));
        // right spaced
        testStr = "Shout-out! YeeYee!       ";
        byte[] testByte2 = new byte[testStr.length()];
        for (int i = 0; i < testByte2.length; i++) {
            testByte2[i] = (byte) testStr.charAt(i);
        }
        Assert.assertEquals(" YeeYee!", BytesUtils.toStringAndRightTrim(testByte2, 10, 10));
    }
}
