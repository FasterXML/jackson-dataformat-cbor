package com.fasterxml.jackson.dataformat.cbor;

import java.io.*;

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
    
    public void testShortAscii() throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CBORGenerator gen = cborGenerator(out);
        gen.writeString("abc");
        gen.close();
        _verifyBytes(out.toByteArray(),
                (byte) (CBORConstants.PREFIX_TYPE_TEXT + 3),
                (byte) 'a', (byte) 'b', (byte) 'c');
    }

    public void testSmallInt() throws Exception
    {
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
    }
}
