package com.fasterxml.jackson.dataformat.cbor;

import java.io.ByteArrayOutputStream;

/**
 * Unit tests geared at testing issues that were raised due to
 * inter-operability with other CBOR codec implementations
 */
public class GeneratorInteropTest extends CBORTestBase
{
    // Test for [Issue#6], for optional writing of CBOR Type Description Tag
    public void testTypeDescriptionTag() throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CBORGenerator gen = cborGenerator(out);
        // as per spec, Type Desc Tag has value 
        gen.writeTag(CBORConstants.TAG_ID_SELF_DESCRIBE);
        gen.writeBoolean(true);
        gen.close();

        _verifyBytes(out.toByteArray(), new byte[] {
            (byte) 0xD9,
            (byte) 0xD9,
            (byte) 0xF7,
            CBORConstants.BYTE_TRUE
        });
    }

}
