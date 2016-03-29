package com.fasterxml.jackson.dataformat.cbor.sizer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORGenerator;
import com.fasterxml.jackson.dataformat.cbor.CBORParser;
import com.fasterxml.jackson.dataformat.cbor.CBORTestBase;
import com.fasterxml.jackson.dataformat.cbor.sizer.CBORFactorySizer;

public abstract class CBORTestBaseSizer extends CBORTestBase {
    /*
     * Factory methods
     */

    @Override
    protected CBORParser cborParser(byte[] input) throws IOException {
        return cborParser(cborFactorySizer(), input);
    }

    @Override
    protected CBORParser cborParser(InputStream in) throws IOException {
        CBORFactorySizer f = cborFactorySizer();
        return cborParser(f, in);
    }

    protected CBORParser cborParser(CBORFactorySizer f, byte[] input) throws IOException {
        return f.createParser(input);
    }

    protected CBORParser cborParser(CBORFactorySizer f, InputStream in) throws IOException {
        return f.createParser(in);
    }

    @Override
    protected ObjectMapper cborMapper() {
        return new ObjectMapper(cborFactorySizer());
    }

    protected CBORFactorySizer cborFactorySizer() {
        CBORFactorySizer f = new CBORFactorySizer();
        f.disable(CBORGenerator.Feature.WRITE_TYPE_HEADER);
        return f;
    }

    @Override
    protected byte[] cborDoc(String json) throws IOException {
        return cborDoc(cborFactorySizer(), json);
    }

    protected byte[] cborDoc(CBORFactorySizer cborF, String json) throws IOException {
        JsonFactory jf = new JsonFactory();
        JsonParser jp = jf.createParser(json);
        ByteArrayOutputStream out = new ByteArrayOutputStream(json.length());
        JsonGenerator dest = cborF.createGenerator(out);

        while (jp.nextToken() != null) {
            dest.copyCurrentEvent(jp);
        }
        jp.close();
        dest.close();
        return out.toByteArray();
    }

    protected CBORGeneratorSizer cborGeneratorSizer(ByteArrayOutputStream result) throws IOException {
        return cborGeneratorSizer(cborFactorySizer(), result);
    }

    protected CBORGeneratorSizer cborGeneratorSizer(CBORFactorySizer f, ByteArrayOutputStream result)
            throws IOException {
        return f.createGenerator(result, null);
    }
}
