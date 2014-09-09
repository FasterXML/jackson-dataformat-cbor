package com.fasterxml.jackson.dataformat.cbor;

import com.fasterxml.jackson.core.*;

/**
 * Unit tests geared at testing issues that were raised due to
 * inter-operability with other CBOR codec implementations
 */
public class ParserInteropTest extends CBORTestBase
{
    // for [Issue#5]; Perl CBOR::XS module uses binary encoding for
    // Map/Object keys; presumably in UTF-8.
    public void testBinaryEncodedKeys() throws Exception
    {
        // from equivalent of '{"query":{} }'
        final byte[] INPUT = { (byte) 0xa1, 0x45, 0x71, 0x75, 0x65, 0x72, 0x79, (byte) 0xa0 };
        JsonParser p = cborParser(INPUT);

        assertToken(JsonToken.START_OBJECT, p.nextToken());
        assertToken(JsonToken.FIELD_NAME, p.nextToken());
        assertEquals("query", p.getCurrentName());
        assertToken(JsonToken.START_OBJECT, p.nextToken());
        assertToken(JsonToken.END_OBJECT, p.nextToken());
        assertToken(JsonToken.END_OBJECT, p.nextToken());

        assertNull(p.nextToken());
        
        p.close();
    }
}
