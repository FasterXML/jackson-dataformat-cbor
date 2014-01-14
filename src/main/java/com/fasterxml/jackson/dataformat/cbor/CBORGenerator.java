package com.fasterxml.jackson.dataformat.cbor;

import java.io.*;
import java.math.BigDecimal;
import java.math.BigInteger;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.io.*;
import com.fasterxml.jackson.core.json.JsonWriteContext;
import com.fasterxml.jackson.core.base.GeneratorBase;

import static com.fasterxml.jackson.dataformat.cbor.CBORConstants.*;

/**
 * {@link JsonGenerator} implementation that writes CBOR encoded content.
 * 
 * @author Tatu Saloranta
 */
public class CBORGenerator extends GeneratorBase
{
    private final static byte BYTE_MARKER_END_OF_STRING = (byte) 0xFC;
    
    private final static byte TOKEN_MISC_LONG_TEXT_ASCII = (byte) 0xE0;
    private final static byte TOKEN_MISC_LONG_TEXT_UNICODE = (byte) 0xE4;

    private final static int TOKEN_PREFIX_TINY_ASCII = 0x40;
    private final static int TOKEN_PREFIX_TINY_UNICODE = 0x80;

    /**
     * Let's ensure that we have big enough output buffer because of
     * safety margins we need for UTF-8 encoding.
     */
    private final static int BYTE_BUFFER_FOR_OUTPUT = 16000;

    /**
     * Longest char chunk we will output is chosen so that it is guaranteed to fit
     * in an empty buffer even if everything encoded in 3-byte sequences; but also
     * fit two full chunks in case of single-byte (ascii) output.
     */
    private final static int MAX_CHUNK_CHARS = (BYTE_BUFFER_FOR_OUTPUT / 4) - 4;

    /**
     * This is the worst case length (in bytes) of maximum chunk we ever write.
     */
    private final static int MAX_CHUNK_LENGTH = (MAX_CHUNK_CHARS * 3) + 3;
    
    /**
     * Enumeration that defines all togglable features for Smile generators.
     */
    public enum Feature {
        /**
         * Placeholder before any format-specific features are added.
         */
        BOGUS(false),
        ;

        protected final boolean _defaultState;
        protected final int _mask;
        
        /**
         * Method that calculates bit set (flags) of all features that
         * are enabled by default.
         */
        public static int collectDefaults()
        {
            int flags = 0;
            for (Feature f : values()) {
                if (f.enabledByDefault()) {
                    flags |= f.getMask();
                }
            }
            return flags;
        }
        
        private Feature(boolean defaultState) {
            _defaultState = defaultState;
            _mask = (1 << ordinal());
        }
        
        public boolean enabledByDefault() { return _defaultState; }
        public int getMask() { return _mask; }
    }
    
    /**
     * To simplify certain operations, we require output buffer length
     * to allow outputting of contiguous 256 character UTF-8 encoded String
     * value. Length of the longest UTF-8 code point (from Java char) is 3 bytes,
     * and we need both initial token byte and single-byte end marker
     * so we get following value.
     *<p>
     * Note: actually we could live with shorter one; absolute minimum would
     * be for encoding 64-character Strings.
     */
    private final static int MIN_BUFFER_LENGTH = (3 * 256) + 2;

    private final static byte TOKEN_BYTE_LONG_STRING_ASCII = TOKEN_MISC_LONG_TEXT_ASCII;
    
    private final static int SURR1_FIRST = 0xD800;
    private final static int SURR1_LAST = 0xDBFF;
    private final static int SURR2_FIRST = 0xDC00;
    private final static int SURR2_LAST = 0xDFFF;

    private final static long MIN_INT_AS_LONG = (long) Integer.MIN_VALUE;
    private final static long MAX_INT_AS_LONG = (long) Integer.MAX_VALUE;
    
    /*
    /**********************************************************
    /* Configuration
    /**********************************************************
     */

    final protected IOContext _ioContext;

    final protected OutputStream _out;

    /**
     * Bit flag composed of bits that indicate which
     * {@link com.fasterxml.jackson.CBORGenerator.smile.SmileGenerator.Feature}s
     * are enabled.
     */
    protected int _formatFeatures;
    
    /*
    /**********************************************************
    /* Output buffering
    /**********************************************************
     */

    /**
     * Intermediate buffer in which contents are buffered before
     * being written using {@link #_out}.
     */
    protected byte[] _outputBuffer;

    /**
     * Pointer to the next available byte in {@link #_outputBuffer}
     */
    protected int _outputTail = 0;

    /**
     * Offset to index after the last valid index in {@link #_outputBuffer}.
     * Typically same as length of the buffer.
     */
    protected final int _outputEnd;

    /**
     * Intermediate buffer in which characters of a String are copied
     * before being encoded.
     */
    protected char[] _charBuffer;

    protected final int _charBufferLength;
    
    /**
     * Let's keep track of how many bytes have been output, may prove useful
     * when debugging. This does <b>not</b> include bytes buffered in
     * the output buffer, just bytes that have been written using underlying
     * stream writer.
     */
    protected int _bytesWritten;
    
    /*
    /**********************************************************
    /* Shared String detection
    /**********************************************************
     */

    /**
     * Flag that indicates whether the output buffer is recycable (and
     * needs to be returned to recycler once we are done) or not.
     */
    protected boolean _bufferRecyclable;
    
    /*
    /**********************************************************
    /* Life-cycle
    /**********************************************************
     */
    
    public CBORGenerator(IOContext ctxt, int jsonFeatures, int smileFeatures,
            ObjectCodec codec, OutputStream out)
    {
        super(jsonFeatures, codec);
        _formatFeatures = smileFeatures;
        _ioContext = ctxt;
        _out = out;
        _bufferRecyclable = true;
        _outputBuffer = ctxt.allocWriteEncodingBuffer(BYTE_BUFFER_FOR_OUTPUT);
        _outputEnd = _outputBuffer.length;
        _charBuffer = ctxt.allocConcatBuffer();
        _charBufferLength = _charBuffer.length;
        // let's just sanity check to prevent nasty odd errors
        if (_outputEnd < MIN_BUFFER_LENGTH) {
            throw new IllegalStateException("Internal encoding buffer length ("+_outputEnd
                    +") too short, must be at least "+MIN_BUFFER_LENGTH);
        }
    }

    public CBORGenerator(IOContext ctxt, int jsonFeatures, int smileFeatures,
            ObjectCodec codec, OutputStream out, byte[] outputBuffer, int offset, boolean bufferRecyclable)
    {
        super(jsonFeatures, codec);
        _formatFeatures = smileFeatures;
        _ioContext = ctxt;
        _out = out;
        _bufferRecyclable = bufferRecyclable;
        _outputTail = offset;
        _outputBuffer = outputBuffer;
        _outputEnd = _outputBuffer.length;
        _charBuffer = ctxt.allocConcatBuffer();
        _charBufferLength = _charBuffer.length;
        // let's just sanity check to prevent nasty odd errors
        if (_outputEnd < MIN_BUFFER_LENGTH) {
            throw new IllegalStateException("Internal encoding buffer length ("+_outputEnd
                    +") too short, must be at least "+MIN_BUFFER_LENGTH);
        }
    }

    /*                                                                                       
    /**********************************************************                              
    /* Versioned                                                                             
    /**********************************************************                              
     */

    @Override
    public Version version() {
        return PackageVersion.VERSION;
    }

    /*
    /**********************************************************
    /* Capability introspection
    /**********************************************************
     */

    @Override
    public boolean canWriteBinaryNatively() {
        return true;
    }

    /*
    /**********************************************************
    /* Overridden methods, configuration
    /**********************************************************
     */

    /**
     * No way (or need) to indent anything, so let's block any attempts.
     * (should we throw an exception instead?)
     */
    @Override
    public JsonGenerator useDefaultPrettyPrinter() {
        return this;
    }

    /**
     * No way (or need) to indent anything, so let's block any attempts.
     * (should we throw an exception instead?)
     */
    @Override
    public JsonGenerator setPrettyPrinter(PrettyPrinter pp) {
        return this;
    }

    @Override
    public Object getOutputTarget() {
        return _out;
    }
    
    /*
    /**********************************************************
    /* Overridden methods, write methods
    /**********************************************************
     */

    /* And then methods overridden to make final, streamline some
     * aspects...
     */

    @Override
    public final void writeFieldName(String name)  throws IOException
    {
        if (_writeContext.writeFieldName(name) == JsonWriteContext.STATUS_EXPECT_VALUE) {
            _reportError("Can not write a field name, expecting a value");
        }
        _writeString(name);
    }

    @Override
    public final void writeFieldName(SerializableString name)
        throws IOException
    {
        // Object is a value, need to verify it's allowed
        if (_writeContext.writeFieldName(name.getValue()) == JsonWriteContext.STATUS_EXPECT_VALUE) {
            _reportError("Can not write a field name, expecting a value");
        }
        _writeString(name.getValue());
    }

    @Override
    public final void writeStringField(String fieldName, String value)
        throws IOException
    {
        if (_writeContext.writeFieldName(fieldName) == JsonWriteContext.STATUS_EXPECT_VALUE) {
            _reportError("Can not write a field name, expecting a value");
        }
        _writeString(fieldName);
        writeString(value);
    }
    
    /*
    /**********************************************************
    /* Extended API, configuration
    /**********************************************************
     */

    public CBORGenerator enable(Feature f) {
        _formatFeatures |= f.getMask();
        return this;
    }

    public CBORGenerator disable(Feature f) {
        _formatFeatures &= ~f.getMask();
        return this;
    }

    public final boolean isEnabled(Feature f) {
        return (_formatFeatures & f.getMask()) != 0;
    }

    public CBORGenerator configure(Feature f, boolean state) {
        if (state) {
            enable(f);
        } else {
            disable(f);
        }
        return this;
    }

    /*
    /**********************************************************
    /* Extended API, other
    /**********************************************************
     */

    /**
     * Method for directly inserting specified byte in output at
     * current position.
     *<p>
     * NOTE: only use this method if you really know what you are doing.
     */
    public void writeRaw(byte b) throws IOException {
        _writeByte(b);
    }

    /**
     * Method for directly inserting specified bytes in output at
     * current position.
     *<p>
     * NOTE: only use this method if you really know what you are doing.
     */
    public void writeBytes(byte[] data, int offset, int len) throws IOException {
        _writeBytes(data, offset, len);
    }
    
    /*
    /**********************************************************
    /* Output method implementations, structural
    /**********************************************************
     */

    @Override
    public final void writeStartArray() throws IOException
    {
        _verifyValueWrite("start an array");
        _writeContext = _writeContext.createChildArrayContext();
        _writeByte(BYTE_ARRAY_INDEFINITE);
    }

    @Override
    public final void writeEndArray() throws IOException
    {
        if (!_writeContext.inArray()) {
            _reportError("Current context not an ARRAY but "+_writeContext.getTypeDesc());
        }
        _writeByte(BYTE_BREAK);
        _writeContext = _writeContext.getParent();
    }

    @Override
    public final void writeStartObject() throws IOException
    {
        _verifyValueWrite("start an object");
        _writeContext = _writeContext.createChildObjectContext();
        _writeByte(BYTE_OBJECT_INDEFINITE);
    }

    @Override
    public final void writeEndObject() throws IOException
    {
        if (!_writeContext.inObject()) {
            _reportError("Current context not an object but "+_writeContext.getTypeDesc());
        }
        _writeContext = _writeContext.getParent();
        _writeByte(BYTE_BREAK);
    }

    /*
    /**********************************************************
    /* Output method implementations, textual
    /**********************************************************
     */

    @Override
    public void writeString(String text) throws IOException,JsonGenerationException
    {
        if (text == null) {
            writeNull();
            return;
        }
        _verifyValueWrite("write String value");
        _writeString(text);
    }

    @Override
    public final void writeString(SerializableString sstr) throws IOException
    {
        _verifyValueWrite("write String value");
        byte[] raw = sstr.asUnquotedUTF8();
        final int len = raw.length;
        if (len == 0) {
            _writeByte(BYTE_EMPTY_STRING);
            return;
        }
        _writeInt32(PREFIX_TYPE_TEXT, len);
        _writeBytes(raw, 0, len);
    }
    
    @Override
    public void writeString(char[] text, int offset, int len) throws IOException
    {
        _verifyValueWrite("write String value");
        _writeString(text, offset, len);
    }

    @Override
    public void writeRawUTF8String(byte[] raw, int offset, int len) throws IOException
    {
        _verifyValueWrite("write String value");
        if (len == 0) {
            _writeByte(BYTE_EMPTY_STRING);
            return;
        }
        if (len == 0) {
            _writeByte(BYTE_EMPTY_STRING);
            return;
        }
        _writeInt32(PREFIX_TYPE_TEXT, len);
        _writeBytes(raw, 0, len);
    }

    @Override
    public final void writeUTF8String(byte[] text, int offset, int len) throws IOException
    {
        // Since no escaping is needed, same as 'writeRawUTF8String'
        writeRawUTF8String(text, offset, len);
    }

    /*
    protected void _writeString(byte[] raw, int offset, int len) throws IOException
    {
        if (len == 0) {
            _writeByte(TOKEN_LITERAL_EMPTY_STRING);
            return;
        }
        _writeInt32(PREFIX_TYPE_TEXT, len);
        _writeBytes(raw, offset, len);
    }
    */

    /*
    /**********************************************************
    /* Output method implementations, unprocessed ("raw")
    /**********************************************************
     */

    @Override
    public void writeRaw(String text) throws IOException {
        throw _notSupported();
    }

    @Override
    public void writeRaw(String text, int offset, int len) throws IOException {
        throw _notSupported();
    }

    @Override
    public void writeRaw(char[] text, int offset, int len) throws IOException {
        throw _notSupported();
    }

    @Override
    public void writeRaw(char c) throws IOException {
        throw _notSupported();
    }

    @Override
    public void writeRawValue(String text) throws IOException {
        throw _notSupported();
    }

    @Override
    public void writeRawValue(String text, int offset, int len) throws IOException {
        throw _notSupported();
    }

    @Override
    public void writeRawValue(char[] text, int offset, int len) throws IOException {
        throw _notSupported();
    }
    
    /*
    /**********************************************************
    /* Output method implementations, base64-encoded binary
    /**********************************************************
     */

    @Override
    public void writeBinary(Base64Variant b64variant, byte[] data, int offset, int len) throws IOException
    {
        if (data == null) {
            writeNull();
            return;
        }
        _verifyValueWrite("write Binary value");
        _writeInt32(PREFIX_TYPE_BYTES, len);
        _writeBytes(data, offset, len);
    }

    @Override
    public int writeBinary(InputStream data, int dataLength)
        throws IOException
    {
        // Smile requires knowledge of length in advance, since binary is length-prefixed
        if (dataLength < 0) {
            throw new UnsupportedOperationException("Must pass actual length for Smile encoded data");
        }
        _verifyValueWrite("write Binary value");
        int missing;

        _writeInt32(PREFIX_TYPE_BYTES, dataLength);
        missing = _writeBytes(data, dataLength);
        if (missing > 0) {
            _reportError("Too few bytes available: missing "+missing+" bytes (out of "+dataLength+")");
        }
        return dataLength;
    }
    
    @Override
    public int writeBinary(Base64Variant b64variant, InputStream data, int dataLength)
        throws IOException
    {
        return writeBinary(data, dataLength);
    }
    
    /*
    /**********************************************************
    /* Output method implementations, primitive
    /**********************************************************
     */

    @Override
    public void writeBoolean(boolean state) throws IOException
    {
        _verifyValueWrite("write boolean value");
        if (state) {
            _writeByte(BYTE_TRUE);
        } else {
            _writeByte(BYTE_FALSE);             
        }
    }

    @Override
    public void writeNull() throws IOException
    {
        _verifyValueWrite("write null value");
        _writeByte(BYTE_FALSE);
    }

    @Override
    public void writeNumber(int i) throws IOException
    {
        _verifyValueWrite("write number");
        int marker;
        if (i < 0) {
            i += 1;
            i = -1;
            marker = PREFIX_TYPE_INT_NEG;
        } else {
            marker = PREFIX_TYPE_INT_POS;
        }
        _writeInt32(marker, i);
    }

    @Override
    public void writeNumber(long l) throws IOException
    {
        // First: maybe 32 bits is enough?
        if (l <= MAX_INT_AS_LONG && l >= MIN_INT_AS_LONG) {
            writeNumber((int) l);
            return;
        }
        _verifyValueWrite("write number");
        _ensureRoomForOutput(9);
        if (l < 0) {
            l += 1;
            l = -1;
            _outputBuffer[_outputTail++] = (PREFIX_TYPE_INT_NEG + 27);
        } else {
            _outputBuffer[_outputTail++] = (PREFIX_TYPE_INT_POS + 27);
        }
        int i = (int) (l >> 32);
        _outputBuffer[_outputTail++] = (byte) (i >> 24);
        _outputBuffer[_outputTail++] = (byte) (i >> 16);
        _outputBuffer[_outputTail++] = (byte) (i >> 8);
        _outputBuffer[_outputTail++] = (byte) i;
        i = (int) l;
        _outputBuffer[_outputTail++] = (byte) (i >> 24);
        _outputBuffer[_outputTail++] = (byte) (i >> 16);
        _outputBuffer[_outputTail++] = (byte) (i >> 8);
        _outputBuffer[_outputTail++] = (byte) i;
    }

    @Override
    public void writeNumber(BigInteger v) throws IOException
    {
        if (v == null) {
            writeNull();
            return;
        }
        _verifyValueWrite("write number");
        
        /* Supported by using type tags, as per spec: major type for tag '6';
         * 5 LSB either 2 for positive bignum or 3 for negative bignum.
         * And then byte sequence that encode variable length integer.
         */
        if (v.signum() < 0) {
            _writeByte(BYTE_TAG_BIGNUM_NEG);
            v = v.negate();
        } else {
            _writeByte(BYTE_TAG_BIGNUM_POS);
        }
        byte[] data = v.toByteArray();
        final int len = data.length;
        _writeInt32(PREFIX_TYPE_BYTES, len);
        _writeBytes(data, 0, len);
    }
    
    @Override
    public void writeNumber(double d) throws IOException
    {
        _verifyValueWrite("write number");
        _ensureRoomForOutput(11);
        /* 17-Apr-2010, tatu: could also use 'doubleToIntBits', but it seems more accurate to use
         * exact representation; and possibly faster. However, if there are cases
         * where collapsing of NaN was needed (for non-Java clients), this can
         * be changed
         */
        long l = Double.doubleToRawLongBits(d);
        _outputBuffer[_outputTail++] = BYTE_FLOAT64;

        int i = (int) (l >> 32);
        _outputBuffer[_outputTail++] = (byte) (i >> 24);
        _outputBuffer[_outputTail++] = (byte) (i >> 16);
        _outputBuffer[_outputTail++] = (byte) (i >> 8);
        _outputBuffer[_outputTail++] = (byte) i;
        i = (int) l;
        _outputBuffer[_outputTail++] = (byte) (i >> 24);
        _outputBuffer[_outputTail++] = (byte) (i >> 16);
        _outputBuffer[_outputTail++] = (byte) (i >> 8);
        _outputBuffer[_outputTail++] = (byte) i;
    }

    @Override
    public void writeNumber(float f) throws IOException
    {
        // Ok, now, we needed token type byte plus 5 data bytes (7 bits each)
        _ensureRoomForOutput(6);
        _verifyValueWrite("write number");
        
        /* 17-Apr-2010, tatu: could also use 'floatToIntBits', but it seems more accurate to use
         * exact representation; and possibly faster. However, if there are cases
         * where collapsing of NaN was needed (for non-Java clients), this can
         * be changed
         */
        int i = Float.floatToRawIntBits(f);
        _outputBuffer[_outputTail++] = BYTE_FLOAT64;
        _outputBuffer[_outputTail++] = (byte) (i >> 24);
        _outputBuffer[_outputTail++] = (byte) (i >> 16);
        _outputBuffer[_outputTail++] = (byte) (i >> 8);
        _outputBuffer[_outputTail++] = (byte) i;
    }

    @Override
    public void writeNumber(BigDecimal dec) throws IOException
    {
        if (dec == null) {
            writeNull();
            return;
        }
        _verifyValueWrite("write number");
        /* Supported by using type tags, as per spec: major type for tag '6';
         * 5 LSB 4.
         * And then a two-int array, with mantissa and exponent
         */
        _writeByte(BYTE_TAG_BIGFLOAT);
        _writeByte(BYTE_ARRAY_2_ELEMENTS);
                

        /*
        _writeInt32(PREFIX_TYPE_BYTES, len);
        _writeBytes(data, 0, len);
        */
        int scale = dec.scale();
        _writeIntValue(scale);

        /* Hmmmh. Specification suggest use of regular integer for mantissa.
         * But... it may or may not fit. Let's try to do that, if it works;
         * if not, use byte array.
         */
        BigInteger unscaled = dec.unscaledValue();
        byte[] data = unscaled.toByteArray();
        if (data.length <= 4) {
            int v = data[0]; // let it be sign extended on purpose
            for (int i = 1; i < data.length; ++i) {
                v = (v << 8) + (data[i] & 0xFF);
            }
            _writeIntValue(v);
        } else if (data.length <= 8) {
            long v = data[0]; // let it be sign extended on purpose
            for (int i = 1; i < data.length; ++i) {
                v = (v << 8) + (data[i] & 0xFF);
            }
            _writeLongValue(v);
        } else {
            final int len = data.length;
            _writeInt32(PREFIX_TYPE_BYTES, len);
            _writeBytes(data, 0, len);
        }
    }

    @Override
    public void writeNumber(String encodedValue) throws IOException,JsonGenerationException, UnsupportedOperationException
    {
        // just write as a String then?
        writeString(encodedValue);
    }

    /*
    /**********************************************************
    /* Implementations for other methods
    /**********************************************************
     */
    
    @Override
    protected final void _verifyValueWrite(String typeMsg) throws IOException
    {
        int status = _writeContext.writeValue();
        if (status == JsonWriteContext.STATUS_EXPECT_NAME) {
            _reportError("Can not "+typeMsg+", expecting field name");
        }
    }
    
    /*
    /**********************************************************
    /* Low-level output handling
    /**********************************************************
     */

    @Override
    public final void flush() throws IOException
    {
        _flushBuffer();
        if (isEnabled(JsonGenerator.Feature.FLUSH_PASSED_TO_STREAM)) {
            _out.flush();
        }
    }

    @Override
    public void close() throws IOException
    {
        // First: let's see that we still have buffers...
        if (_outputBuffer != null
            && isEnabled(JsonGenerator.Feature.AUTO_CLOSE_JSON_CONTENT)) {
            while (true) {
                JsonStreamContext ctxt = getOutputContext();
                if (ctxt.inArray()) {
                    writeEndArray();
                } else if (ctxt.inObject()) {
                    writeEndObject();
                } else {
                    break;
                }
            }
        }
//        boolean wasClosed = _closed;
        super.close();

        /*
        if (!wasClosed && isEnabled(Feature.WRITE_END_MARKER)) {
            _writeByte(BYTE_MARKER_END_OF_CONTENT);
        }
        */
        _flushBuffer();

        if (_ioContext.isResourceManaged() || isEnabled(JsonGenerator.Feature.AUTO_CLOSE_TARGET)) {
            _out.close();
        } else {
            // If we can't close it, we should at least flush
            _out.flush();
        }
        // Internal buffer(s) generator has can now be released as well
        _releaseBuffers();
    }
    

    /*
    /**********************************************************
    /* Internal methods: low-level text output
    /**********************************************************
     */

    protected final void _writeString(String name) throws IOException
    {
        int len = name.length();
        if (len == 0) {
            _writeByte(BYTE_EMPTY_STRING);
            return;
        }
        char[] cbuf = _charBuffer;
        if (len > cbuf.length) {
            _charBuffer = cbuf = new char[Math.max(_charBuffer.length + 32, len)];
        }
        name.getChars(0, len, cbuf, 0);
        _writeString(cbuf, 0, len);
    }

    protected void _writeString(char[] text, int offset, int len) throws IOException
    {
        if (len <= 23) { // possibly short strings (not necessarily)
            // first: ensure we have enough space
            if ((_outputTail + 23) >= _outputEnd) {
                _flushBuffer();
            }
            int origOffset = _outputTail;
            ++_outputTail; // to leave room for type token
            int byteLen = _shortUTF8Encode(text, offset, offset+len);
            byte typeToken;
            if (byteLen <= 255) { // yes, is short indeed
                if (byteLen == len) { // and all ASCII
                    typeToken = (byte) ((TOKEN_PREFIX_TINY_ASCII - 1) + byteLen);
                } else { // not just ASCII
                    typeToken = (byte) ((TOKEN_PREFIX_TINY_UNICODE - 2) + byteLen);
                }
            } else { // nope, longer non-ASCII Strings
                typeToken = TOKEN_MISC_LONG_TEXT_UNICODE;
                // and we will need String end marker byte
                _outputBuffer[_outputTail++] = BYTE_MARKER_END_OF_STRING;
            }
            // and then sneak in type token now that know the details
            _outputBuffer[origOffset] = typeToken;
        } else { // "long" String, never shared
            // but might still fit within buffer?
            int maxLen = len + len + len + 2;
            if (maxLen <= _outputBuffer.length) { // yes indeed
                if ((_outputTail + maxLen) >= _outputEnd) {
                    _flushBuffer();
                }
                int origOffset = _outputTail;
                _writeByte(TOKEN_MISC_LONG_TEXT_UNICODE);
                int byteLen = _shortUTF8Encode(text, offset, offset+len);
                // if it's ASCII, let's revise our type determination (to help decoder optimize)
                if (byteLen == len) {
                    _outputBuffer[origOffset] = TOKEN_BYTE_LONG_STRING_ASCII;
                }
                _outputBuffer[_outputTail++] = BYTE_MARKER_END_OF_STRING;
            } else {
                _writeByte(TOKEN_MISC_LONG_TEXT_UNICODE);
                _mediumUTF8Encode(text, offset, offset+len);
                _writeByte(BYTE_MARKER_END_OF_STRING);
            }
        }
    }

    
    /*
    /**********************************************************
    /* Internal methods, UTF-8 encoding
    /**********************************************************
    */

    /**
     * Helper method called when the whole character sequence is known to
     * fit in the output buffer regardless of UTF-8 expansion.
     */
    private final int _shortUTF8Encode(char[] str, int i, int end)
    {
        // First: let's see if it's all ASCII: that's rather fast
        int ptr = _outputTail;
        final byte[] outBuf = _outputBuffer;
        do {
            int c = str[i];
            if (c > 0x7F) {
                return _shortUTF8Encode2(str, i, end, ptr);
            }
            outBuf[ptr++] = (byte) c;
        } while (++i < end);
        int codedLen = ptr - _outputTail;
        _outputTail = ptr;
        return codedLen;
    }

    /**
     * Helper method called when the whole character sequence is known to
     * fit in the output buffer, but not all characters are single-byte (ASCII)
     * characters.
     */
    private final int _shortUTF8Encode2(char[] str, int i, int end, int outputPtr)
    {
        final byte[] outBuf = _outputBuffer;
        while (i < end) {
            int c = str[i++];
            if (c <= 0x7F) {
                outBuf[outputPtr++] = (byte) c;
                continue;
            }
            // Nope, multi-byte:
            if (c < 0x800) { // 2-byte
                outBuf[outputPtr++] = (byte) (0xc0 | (c >> 6));
                outBuf[outputPtr++] = (byte) (0x80 | (c & 0x3f));
                continue;
            }
            // 3 or 4 bytes (surrogate)
            // Surrogates?
            if (c < SURR1_FIRST || c > SURR2_LAST) { // nope, regular 3-byte character
                outBuf[outputPtr++] = (byte) (0xe0 | (c >> 12));
                outBuf[outputPtr++] = (byte) (0x80 | ((c >> 6) & 0x3f));
                outBuf[outputPtr++] = (byte) (0x80 | (c & 0x3f));
                continue;
            }
            // Yup, a surrogate pair
            if (c > SURR1_LAST) { // must be from first range; second won't do
                _throwIllegalSurrogate(c);
            }
            // ... meaning it must have a pair
            if (i >= end) {
                _throwIllegalSurrogate(c);
            }
            c = _convertSurrogate(c, str[i++]);
            if (c > 0x10FFFF) { // illegal in JSON as well as in XML
                _throwIllegalSurrogate(c);
            }
            outBuf[outputPtr++] = (byte) (0xf0 | (c >> 18));
            outBuf[outputPtr++] = (byte) (0x80 | ((c >> 12) & 0x3f));
            outBuf[outputPtr++] = (byte) (0x80 | ((c >> 6) & 0x3f));
            outBuf[outputPtr++] = (byte) (0x80 | (c & 0x3f));
        }
        int codedLen = outputPtr - _outputTail;
        _outputTail = outputPtr;
        return codedLen;
    }
    
    private void _slowUTF8Encode(String str) throws IOException
    {
        final int len = str.length();
        int inputPtr = 0;
        final int bufferEnd = _outputEnd - 4;
        
        output_loop:
        for (; inputPtr < len; ) {
            /* First, let's ensure we can output at least 4 bytes
             * (longest UTF-8 encoded codepoint):
             */
            if (_outputTail >= bufferEnd) {
                _flushBuffer();
            }
            int c = str.charAt(inputPtr++);
            // And then see if we have an ASCII char:
            if (c <= 0x7F) { // If so, can do a tight inner loop:
                _outputBuffer[_outputTail++] = (byte)c;
                // Let's calc how many ASCII chars we can copy at most:
                int maxInCount = (len - inputPtr);
                int maxOutCount = (bufferEnd - _outputTail);

                if (maxInCount > maxOutCount) {
                    maxInCount = maxOutCount;
                }
                maxInCount += inputPtr;
                ascii_loop:
                while (true) {
                    if (inputPtr >= maxInCount) { // done with max. ascii seq
                        continue output_loop;
                    }
                    c = str.charAt(inputPtr++);
                    if (c > 0x7F) {
                        break ascii_loop;
                    }
                    _outputBuffer[_outputTail++] = (byte) c;
                }
            }

            // Nope, multi-byte:
            if (c < 0x800) { // 2-byte
                _outputBuffer[_outputTail++] = (byte) (0xc0 | (c >> 6));
                _outputBuffer[_outputTail++] = (byte) (0x80 | (c & 0x3f));
            } else { // 3 or 4 bytes
                // Surrogates?
                if (c < SURR1_FIRST || c > SURR2_LAST) {
                    _outputBuffer[_outputTail++] = (byte) (0xe0 | (c >> 12));
                    _outputBuffer[_outputTail++] = (byte) (0x80 | ((c >> 6) & 0x3f));
                    _outputBuffer[_outputTail++] = (byte) (0x80 | (c & 0x3f));
                    continue;
                }
                // Yup, a surrogate:
                if (c > SURR1_LAST) { // must be from first range
                    _throwIllegalSurrogate(c);
                }
                // and if so, followed by another from next range
                if (inputPtr >= len) {
                    _throwIllegalSurrogate(c);
                }
                c = _convertSurrogate(c, str.charAt(inputPtr++));
                if (c > 0x10FFFF) { // illegal, as per RFC 4627
                    _throwIllegalSurrogate(c);
                }
                _outputBuffer[_outputTail++] = (byte) (0xf0 | (c >> 18));
                _outputBuffer[_outputTail++] = (byte) (0x80 | ((c >> 12) & 0x3f));
                _outputBuffer[_outputTail++] = (byte) (0x80 | ((c >> 6) & 0x3f));
                _outputBuffer[_outputTail++] = (byte) (0x80 | (c & 0x3f));
            }
        }
    }

    private void _mediumUTF8Encode(char[] str, int inputPtr, int inputEnd) throws IOException
    {
        final int bufferEnd = _outputEnd - 4;
        
        output_loop:
        while (inputPtr < inputEnd) {
            /* First, let's ensure we can output at least 4 bytes
             * (longest UTF-8 encoded codepoint):
             */
            if (_outputTail >= bufferEnd) {
                _flushBuffer();
            }
            int c = str[inputPtr++];
            // And then see if we have an ASCII char:
            if (c <= 0x7F) { // If so, can do a tight inner loop:
                _outputBuffer[_outputTail++] = (byte)c;
                // Let's calc how many ASCII chars we can copy at most:
                int maxInCount = (inputEnd - inputPtr);
                int maxOutCount = (bufferEnd - _outputTail);

                if (maxInCount > maxOutCount) {
                    maxInCount = maxOutCount;
                }
                maxInCount += inputPtr;
                ascii_loop:
                while (true) {
                    if (inputPtr >= maxInCount) { // done with max. ascii seq
                        continue output_loop;
                    }
                    c = str[inputPtr++];
                    if (c > 0x7F) {
                        break ascii_loop;
                    }
                    _outputBuffer[_outputTail++] = (byte) c;
                }
            }

            // Nope, multi-byte:
            if (c < 0x800) { // 2-byte
                _outputBuffer[_outputTail++] = (byte) (0xc0 | (c >> 6));
                _outputBuffer[_outputTail++] = (byte) (0x80 | (c & 0x3f));
            } else { // 3 or 4 bytes
                // Surrogates?
                if (c < SURR1_FIRST || c > SURR2_LAST) {
                    _outputBuffer[_outputTail++] = (byte) (0xe0 | (c >> 12));
                    _outputBuffer[_outputTail++] = (byte) (0x80 | ((c >> 6) & 0x3f));
                    _outputBuffer[_outputTail++] = (byte) (0x80 | (c & 0x3f));
                    continue;
                }
                // Yup, a surrogate:
                if (c > SURR1_LAST) { // must be from first range
                    _throwIllegalSurrogate(c);
                }
                // and if so, followed by another from next range
                if (inputPtr >= inputEnd) {
                    _throwIllegalSurrogate(c);
                }
                c = _convertSurrogate(c, str[inputPtr++]);
                if (c > 0x10FFFF) { // illegal, as per RFC 4627
                    _throwIllegalSurrogate(c);
                }
                _outputBuffer[_outputTail++] = (byte) (0xf0 | (c >> 18));
                _outputBuffer[_outputTail++] = (byte) (0x80 | ((c >> 12) & 0x3f));
                _outputBuffer[_outputTail++] = (byte) (0x80 | ((c >> 6) & 0x3f));
                _outputBuffer[_outputTail++] = (byte) (0x80 | (c & 0x3f));
            }
        }
    }
    
    /**
     * Method called to calculate UTF codepoint, from a surrogate pair.
     */
    private int _convertSurrogate(int firstPart, int secondPart)
    {
        // Ok, then, is the second part valid?
        if (secondPart < SURR2_FIRST || secondPart > SURR2_LAST) {
            throw new IllegalArgumentException("Broken surrogate pair: first char 0x"+Integer.toHexString(firstPart)+", second 0x"+Integer.toHexString(secondPart)+"; illegal combination");
        }
        return 0x10000 + ((firstPart - SURR1_FIRST) << 10) + (secondPart - SURR2_FIRST);
    }

    private void _throwIllegalSurrogate(int code)
    {
        if (code > 0x10FFFF) { // over max?
            throw new IllegalArgumentException("Illegal character point (0x"+Integer.toHexString(code)+") to output; max is 0x10FFFF as per RFC 4627");
        }
        if (code >= SURR1_FIRST) {
            if (code <= SURR1_LAST) { // Unmatched first part (closing without second part?)
                throw new IllegalArgumentException("Unmatched first part of surrogate pair (0x"+Integer.toHexString(code)+")");
            }
            throw new IllegalArgumentException("Unmatched second part of surrogate pair (0x"+Integer.toHexString(code)+")");
        }
        // should we ever get this?
        throw new IllegalArgumentException("Illegal character point (0x"+Integer.toHexString(code)+") to output");
    }

    /*
    /**********************************************************
    /* Internal methods, writing bytes
    /**********************************************************
    */

    private final void _ensureRoomForOutput(int needed) throws IOException
    {
        if ((_outputTail + needed) >= _outputEnd) {
            _flushBuffer();
        }        
    }

    private final void _writeIntValue(int i) throws IOException
    {
        int marker;
        if (i < 0) {
            i += 1;
            i = -1;
            marker = PREFIX_TYPE_INT_NEG;
        } else {
            marker = PREFIX_TYPE_INT_POS;
        }
        _writeInt32(marker, i);
    }

    private final void _writeLongValue(long l) throws IOException
    {
        _ensureRoomForOutput(9);
        if (l < 0) {
            l += 1;
            l = -1;
            _outputBuffer[_outputTail++] = (PREFIX_TYPE_INT_NEG + 27);
        } else {
            _outputBuffer[_outputTail++] = (PREFIX_TYPE_INT_POS + 27);
        }
        int i = (int) (l >> 32);
        _outputBuffer[_outputTail++] = (byte) (i >> 24);
        _outputBuffer[_outputTail++] = (byte) (i >> 16);
        _outputBuffer[_outputTail++] = (byte) (i >> 8);
        _outputBuffer[_outputTail++] = (byte) i;
        i = (int) l;
        _outputBuffer[_outputTail++] = (byte) (i >> 24);
        _outputBuffer[_outputTail++] = (byte) (i >> 16);
        _outputBuffer[_outputTail++] = (byte) (i >> 8);
        _outputBuffer[_outputTail++] = (byte) i;
    }
    
    private final void _writeInt32(int majorType, int i) throws IOException
    {
        _ensureRoomForOutput(5);
        if (i < 24) {
            _outputBuffer[_outputTail++] = (byte) (majorType + i);
            return;
        }
        if (i <= 0xFF) {
            _outputBuffer[_outputTail++] = (byte) (majorType + 24);
            _outputBuffer[_outputTail++] = (byte) i;
            return;
        }
        final byte b0 = (byte) i;
        i >>= 8;
        if (i <= 0xFF) {
            _outputBuffer[_outputTail++] = (byte) (majorType + 25);
            _outputBuffer[_outputTail++] = (byte) i;
            _outputBuffer[_outputTail++] = b0;
            return;
        }
        _outputBuffer[_outputTail++] = (byte) (majorType + 26);
        _outputBuffer[_outputTail++] = (byte) (i >> 16);
        _outputBuffer[_outputTail++] = (byte) (i >> 8);
        _outputBuffer[_outputTail++] = (byte) i;
        _outputBuffer[_outputTail++] = b0;
    }
    
    private final void _writeByte(byte b) throws IOException
    {
        if (_outputTail >= _outputEnd) {
            _flushBuffer();
        }
        _outputBuffer[_outputTail++] = b;
    }

    /*
    private final void _writeBytes(byte b1, byte b2) throws IOException
    {
        if ((_outputTail + 1) >= _outputEnd) {
            _flushBuffer();
        }
        _outputBuffer[_outputTail++] = b1;
        _outputBuffer[_outputTail++] = b2;
    }
    */

    private final void _writeBytes(byte[] data, int offset, int len) throws IOException
    {
        if (len == 0) {
            return;
        }
        if ((_outputTail + len) >= _outputEnd) {
            _writeBytesLong(data, offset, len);
            return;
        }
        // common case, non-empty, fits in just fine:
        System.arraycopy(data, offset, _outputBuffer, _outputTail, len);
        _outputTail += len;
    }

    private final int _writeBytes(InputStream in, int bytesLeft) throws IOException
    {
        while (bytesLeft > 0) {
            int room = _outputEnd - _outputTail;
            if (room < 0) {
                _flushBuffer();
                room = _outputEnd - _outputTail;
            }
            int count = in.read(_outputBuffer, _outputTail, room);
            if (count < 0) {
                break;
            }
            _outputTail += count;
            bytesLeft -= count;
        }
        return bytesLeft;
    }
    
    private final void _writeBytesLong(byte[] data, int offset, int len) throws IOException
    {
        if (_outputTail >= _outputEnd) {
            _flushBuffer();
        }
        while (true) {
            int currLen = Math.min(len, (_outputEnd - _outputTail));
            System.arraycopy(data, offset, _outputBuffer, _outputTail, currLen);
            _outputTail += currLen;
            if ((len -= currLen) == 0) {
                break;
            }
            offset += currLen;
            _flushBuffer();
        }
    }

    /*
    /**********************************************************
    /* Internal methods, buffer handling
    /**********************************************************
     */
    
    @Override
    protected void _releaseBuffers()
    {
        byte[] buf = _outputBuffer;
        if (buf != null && _bufferRecyclable) {
            _outputBuffer = null;
            _ioContext.releaseWriteEncodingBuffer(buf);
        }
        char[] cbuf = _charBuffer;
        if (cbuf != null) {
            _charBuffer = null;
            _ioContext.releaseConcatBuffer(cbuf);
        }
    }

    protected final void _flushBuffer() throws IOException
    {
        if (_outputTail > 0) {
            _bytesWritten += _outputTail;
            _out.write(_outputBuffer, 0, _outputTail);
            _outputTail = 0;
        }
    }

    /*
    /**********************************************************
    /* Internal methods, error reporting
    /**********************************************************
     */

    protected UnsupportedOperationException _notSupported() {
        return new UnsupportedOperationException();
    }    
}
