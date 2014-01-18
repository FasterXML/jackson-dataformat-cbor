package com.fasterxml.jackson.dataformat.cbor;

import java.io.*;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.JsonParser.NumberType;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Unit tests for simple value types.
 */
public class ParserSimpleTest extends CBORTestBase
{
    private final ObjectMapper MAPPER = cborMapper();

    /**
     * Test for verifying handling of 'true', 'false' and 'null' literals
     */
    public void testSimpleLiterals() throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JsonGenerator gen = cborGenerator(out);
        gen.writeBoolean(true);
        gen.close();
        JsonParser p = cborParser(out);
        assertEquals(JsonToken.VALUE_TRUE, p.nextToken());
        assertNull(p.nextToken());
        p.close();

        out = new ByteArrayOutputStream();
        gen = cborGenerator(out);
        gen.writeBoolean(false);
        gen.close();
        p = cborParser(out);
        assertEquals(JsonToken.VALUE_FALSE, p.nextToken());
        assertNull(p.nextToken());
        p.close();

        out = new ByteArrayOutputStream();
        gen = cborGenerator(out);
        gen.writeNull();
        gen.close();
        p = cborParser(out);
        assertEquals(JsonToken.VALUE_NULL, p.nextToken());
        assertNull(p.nextToken());
        p.close();
    }
    
    public void testIntValues() throws Exception
    {
        // first, single-byte
        CBORFactory f = cborFactory();
        // single byte
        _verifyInt(f, 13);
        _verifyInt(f, -19);
        // two bytes
        _verifyInt(f, 255);
        _verifyInt(f, -127);
        // three
        _verifyInt(f, 256);
        _verifyInt(f, 0xFFFF);
        _verifyInt(f, -300);
        _verifyInt(f, -0xFFFF);
        // and all 4 bytes
        _verifyInt(f, 0x7FFFFFFF);
        _verifyInt(f, 0x70000000 << 1);
    }

    private void _verifyInt(CBORFactory f, int value) throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JsonGenerator gen = cborGenerator(f, out);
        gen.writeNumber(value);
        gen.close();
        JsonParser p = cborParser(f, out.toByteArray());
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(NumberType.INT, p.getNumberType());
        assertEquals(value, p.getIntValue());
        assertEquals((double) value, p.getDoubleValue());
        assertNull(p.nextToken());
        p.close();
    }

    public void testFloatValues() throws Exception
    {
        // first, single-byte
        CBORFactory f = cborFactory();
        // single byte
        _verifyFloat(f, 0.25);
        _verifyFloat(f, 20.5);
    }

    private void _verifyFloat(CBORFactory f, double value) throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JsonGenerator gen = cborGenerator(f, out);
        gen.writeNumber(value);
        gen.close();
        JsonParser p = cborParser(f, out.toByteArray());
        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
        assertEquals(NumberType.DOUBLE, p.getNumberType());
        assertEquals(value, p.getDoubleValue());
        assertNull(p.nextToken());
        p.close();
    }

    public void testSimpleArray() throws Exception
    {
        byte[] b = MAPPER.writeValueAsBytes(new int[] { 1, 2, 3, 4});
        int[] output = MAPPER.readValue(b, int[].class);
        assertEquals(4, output.length);
        for (int i = 1; i <= output.length; ++i) {
            assertEquals(i, output[i-1]);
        }
    }

    public void testSimpleObject() throws Exception
    {
        Map<String,Integer> input = new LinkedHashMap<String,Integer>();
        input.put("a", 1);
        input.put("bar", 2);
        input.put("foobar", 3);
        byte[] b = MAPPER.writeValueAsBytes(input);

        // First, using streaming API
        JsonParser p = cborParser(b);
        assertToken(JsonToken.START_OBJECT, p.nextToken());
        assertToken(JsonToken.FIELD_NAME, p.nextToken());
        assertEquals("a", p.getCurrentName());
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(1, p.getIntValue());
        assertToken(JsonToken.FIELD_NAME, p.nextToken());
        assertEquals("bar", p.getCurrentName());
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(2, p.getIntValue());
        assertToken(JsonToken.FIELD_NAME, p.nextToken());
        assertEquals("foobar", p.getCurrentName());
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(3, p.getIntValue());
        assertToken(JsonToken.END_OBJECT, p.nextToken());
        p.close();
        
        Map<?,?> output = MAPPER.readValue(b, Map.class);
        assertEquals(3, output.size());
        assertEquals(Integer.valueOf(1), output.get("a"));
        assertEquals(Integer.valueOf(2), output.get("bar"));
        assertEquals(Integer.valueOf(3), output.get("foobar"));
    }

    /*
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
*/
}
