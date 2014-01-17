package com.fasterxml.jackson.dataformat.cbor;

import java.io.*;

public class GeneratorSimpleTest extends CBORTestBase
{
    /**
     * Test for verifying handling of 'true', 'false' and 'null' literals
     */
    public void testSimpleLiterals() throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CBORGenerator gen = cborGenerator(out);
        gen.writeBoolean(true);
        gen.close();
        _verifyBytes(out.toByteArray(), CBORConstants.BYTE_TRUE);

        out = new ByteArrayOutputStream();
        gen = cborGenerator(out);
        gen.writeBoolean(false);
        gen.close();
        _verifyBytes(out.toByteArray(), CBORConstants.BYTE_FALSE);

        out = new ByteArrayOutputStream();
        gen = cborGenerator(out);
        gen.writeNull();
        gen.close();
        _verifyBytes(out.toByteArray(), CBORConstants.BYTE_NULL);
    }

    public void testEmptyArray() throws Exception
    {
        // First: empty array (2 bytes)
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CBORGenerator gen = cborGenerator(out);
        gen.writeStartArray();
        gen.writeEndArray();
        gen.close();
        _verifyBytes(out.toByteArray(), CBORConstants.BYTE_ARRAY_INDEFINITE,
        		CBORConstants.BYTE_BREAK);
    }

    public void testEmptyObject() throws Exception
    {
        // First: empty array (2 bytes)
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CBORGenerator gen = cborGenerator(out);
        gen.writeStartObject();
        gen.writeEndObject();
        gen.close();
        _verifyBytes(out.toByteArray(), CBORConstants.BYTE_OBJECT_INDEFINITE,
               CBORConstants.BYTE_BREAK);
    }
    
    public void testIntValues() throws Exception
    {
        // first, single-byte
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CBORGenerator gen = cborGenerator(out);
        gen.writeNumber(13);
        gen.close();
        _verifyBytes(out.toByteArray(),
                (byte) (CBORConstants.PREFIX_TYPE_INT_POS + 13));

        out = new ByteArrayOutputStream();
        gen = cborGenerator(out);
        gen.writeNumber(-13);
        gen.close();
        _verifyBytes(out.toByteArray(),
                // note: since there is no "-0", number one less than it'd appear
                (byte) (CBORConstants.PREFIX_TYPE_INT_NEG + 12));

        // then two byte
        out = new ByteArrayOutputStream();
        gen = cborGenerator(out);
        gen.writeNumber(0xFF);
        gen.close();
        _verifyBytes(out.toByteArray(),
                (byte) (CBORConstants.PREFIX_TYPE_INT_POS + 24), (byte) 0xFF);

        out = new ByteArrayOutputStream();
        gen = cborGenerator(out);
        gen.writeNumber(-256);
        gen.close();
        _verifyBytes(out.toByteArray(),
                // note: since there is no "-0", number one less than it'd appear
                (byte) (CBORConstants.PREFIX_TYPE_INT_NEG + 24), (byte) 0xFF);

        // and three byte
        out = new ByteArrayOutputStream();
        gen = cborGenerator(out);
        gen.writeNumber(0xFEDC);
        gen.close();
        _verifyBytes(out.toByteArray(),
                (byte) (CBORConstants.PREFIX_TYPE_INT_POS + 25), (byte) 0xFE, (byte) 0xDC);

        out = new ByteArrayOutputStream();
        gen = cborGenerator(out);
        gen.writeNumber(-0xFFFE);
        gen.close();
        _verifyBytes(out.toByteArray(),
                (byte) (CBORConstants.PREFIX_TYPE_INT_NEG + 25), (byte) 0xFF, (byte) 0xFD);
    }

    public void testFloatValues() throws Exception
    {
        // first, 32-bit float
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CBORGenerator gen = cborGenerator(out);
        float f = 1.25f;
        gen.writeNumber(f);
        gen.close();
        int raw = Float.floatToIntBits(f);
        _verifyBytes(out.toByteArray(),
                (byte) (CBORConstants.BYTE_FLOAT32),
                (byte) (raw >> 24),
                (byte) (raw >> 16),
                (byte) (raw >> 8),
                (byte) raw);

        // then 64-bit double
        out = new ByteArrayOutputStream();
        gen = cborGenerator(out);
        double d = 0.75f;
        gen.writeNumber(d);
        gen.close();
        long rawL = Double.doubleToLongBits(d);
        _verifyBytes(out.toByteArray(),
                (byte) (CBORConstants.BYTE_FLOAT64),
                (byte) (rawL >> 56),
                (byte) (rawL >> 48),
                (byte) (rawL >> 40),
                (byte) (rawL >> 32),
                (byte) (rawL >> 24),
                (byte) (rawL >> 16),
                (byte) (rawL >> 8),
                (byte) rawL);
    }
    
    public void testShortText() throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CBORGenerator gen = cborGenerator(out);
        gen.writeString("");
        gen.close();
        _verifyBytes(out.toByteArray(), CBORConstants.BYTE_EMPTY_STRING);

        out = new ByteArrayOutputStream();
        gen = cborGenerator(out);
        gen.writeString("abc");
        gen.close();
        _verifyBytes(out.toByteArray(), (byte) (CBORConstants.PREFIX_TYPE_TEXT + 3),
                (byte) 'a', (byte) 'b', (byte) 'c');
    }
    
    public void testLongerText() throws Exception
    {
        // First, something with 8-bit length
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CBORGenerator gen = cborGenerator(out);
        final String SHORT_ASCII = generateAsciiString(240);
        gen.writeString(SHORT_ASCII);
        gen.close();
        byte[] b = SHORT_ASCII.getBytes("UTF-8");
        int len = b.length;
        _verifyBytes(out.toByteArray(),
                (byte) (CBORConstants.PREFIX_TYPE_TEXT + 24), (byte) len, b);

        // and ditto with fuller Unicode
        out = new ByteArrayOutputStream();
        gen = cborGenerator(out);
        final String SHORT_UNICODE = generateUnicodeString(160);
        gen.writeString(SHORT_UNICODE);
        gen.close();
        b = SHORT_UNICODE.getBytes("UTF-8");
        len = b.length;
        // just a sanity check; will break if generation changes
        assertEquals(196, len);
        _verifyBytes(out.toByteArray(),
                (byte) (CBORConstants.PREFIX_TYPE_TEXT + 24), (byte) len, b);

        // and then something bit more sizable
        out = new ByteArrayOutputStream();
        gen = cborGenerator(out);
        final String MEDIUM_UNICODE = generateUnicodeString(800);
        gen.writeString(MEDIUM_UNICODE);
        gen.close();
        b = MEDIUM_UNICODE.getBytes("UTF-8");
        len = b.length;
        // just a sanity check; will break if generation changes
        assertEquals(926, len);
        _verifyBytes(out.toByteArray(),
                (byte) (CBORConstants.PREFIX_TYPE_TEXT + 25),
                (byte) (len>>8), (byte) len,
                b);
    }

}
