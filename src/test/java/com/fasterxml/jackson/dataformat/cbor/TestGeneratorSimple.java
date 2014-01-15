package com.fasterxml.jackson.dataformat.cbor;

import java.io.*;
import java.util.Random;

public class TestGeneratorSimple extends CBORTestBase
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

    public void testLongerText() throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CBORGenerator gen = cborGenerator(out);

        final String SHORT_ASCII = generateAsciiString(240);
        
        gen.writeString(SHORT_ASCII);
        gen.close();
        byte[] b = SHORT_ASCII.getBytes("UTF-8");
        final int len = b.length;
        _verifyBytes(out.toByteArray(),
                (byte) (CBORConstants.PREFIX_TYPE_TEXT + 24), (byte) len, b);
    }

}
