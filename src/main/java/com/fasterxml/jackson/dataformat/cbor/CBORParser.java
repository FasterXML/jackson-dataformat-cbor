package com.fasterxml.jackson.dataformat.cbor;

import java.io.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.base.ParserMinimalBase;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.core.io.NumberInput;
import com.fasterxml.jackson.core.json.DupDetector;
import com.fasterxml.jackson.core.sym.BytesToNameCanonicalizer;
import com.fasterxml.jackson.core.sym.Name;
import com.fasterxml.jackson.core.util.ByteArrayBuilder;
import com.fasterxml.jackson.core.util.TextBuffer;

public final class CBORParser extends ParserMinimalBase
{
    /**
     * Enumeration that defines all togglable features for CBOR generators.
     */
    public enum Feature {
        /**
         * Placeholder before any format-specific features are added.
         */
        BOGUS(false)
        ;

        final boolean _defaultState;
        final int _mask;
        
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

    private final static int[] NO_INTS = new int[0];

    private final static int[] UTF8_UNIT_CODES = CBORConstants.sUtf8UnitLengths;

    // Constants for handling of 16-bit "mini-floats"
    private final static double MATH_POW_2_10 = Math.pow(2, 10);
    private final static double MATH_POW_2_NEG14 = Math.pow(2, -14);
    
    /*
    /**********************************************************
    /* Configuration
    /**********************************************************
     */
    
    /**
     * Codec used for data binding when (if) requested.
     */
    protected ObjectCodec _objectCodec;
    
    /*
    /**********************************************************
    /* Generic I/O state
    /**********************************************************
     */

    /**
     * I/O context for this reader. It handles buffer allocation
     * for the reader.
     */
    final protected IOContext _ioContext;

    /**
     * Flag that indicates whether parser is closed or not. Gets
     * set when parser is either closed by explicit call
     * ({@link #close}) or when end-of-input is reached.
     */
    protected boolean _closed;

    /*
    /**********************************************************
    /* Current input data
    /**********************************************************
     */

    // Note: type of actual buffer depends on sub-class, can't include

    /**
     * Pointer to next available character in buffer
     */
    protected int _inputPtr = 0;

    /**
     * Index of character after last available one in the buffer.
     */
    protected int _inputEnd = 0;

    /*
    /**********************************************************
    /* Current input location information
    /**********************************************************
     */

    /**
     * Number of characters/bytes that were contained in previous blocks
     * (blocks that were already processed prior to the current buffer).
     */
    protected long _currInputProcessed = 0L;

    /**
     * Current row location of current point in input buffer, starting
     * from 1, if available.
     */
    protected int _currInputRow = 1;

    /**
     * Current index of the first character of the current row in input
     * buffer. Needed to calculate column position, if necessary; benefit
     * of not having column itself is that this only has to be updated
     * once per line.
     */
    protected int _currInputRowStart = 0;

    /*
    /**********************************************************
    /* Information about starting location of event
    /* Reader is pointing to; updated on-demand
    /**********************************************************
     */

    // // // Location info at point when current token was started

    /**
     * Total number of bytes/characters read before start of current token.
     * For big (gigabyte-sized) sizes are possible, needs to be long,
     * unlike pointers and sizes related to in-memory buffers.
     */
    protected long _tokenInputTotal = 0; 

    /**
     * Input row on which current token starts, 1-based
     */
    protected int _tokenInputRow = 1;

    /**
     * Column on input row that current token starts; 0-based (although
     * in the end it'll be converted to 1-based)
     */
    protected int _tokenInputCol = 0;
    
    /*
    /**********************************************************
    /* Parsing state
    /**********************************************************
     */

    /**
     * Information about parser context, context in which
     * the next token is to be parsed (root, array, object).
     */
    protected CBORReadContext _parsingContext;
    /**
     * Buffer that contains contents of String values, including
     * field names if necessary (name split across boundary,
     * contains escape sequence, or access needed to char array)
     */
    protected final TextBuffer _textBuffer;

    /**
     * Temporary buffer that is needed if field name is accessed
     * using {@link #getTextCharacters} method (instead of String
     * returning alternatives)
     */
    protected char[] _nameCopyBuffer = null;

    /**
     * Flag set to indicate whether the field name is available
     * from the name copy buffer or not (in addition to its String
     * representation  being available via read context)
     */
    protected boolean _nameCopied = false;
    
    /**
     * ByteArrayBuilder is needed if 'getBinaryValue' is called. If so,
     * we better reuse it for remainder of content.
     */
    protected ByteArrayBuilder _byteArrayBuilder = null;

    /**
     * We will hold on to decoded binary data, for duration of
     * current event, so that multiple calls to
     * {@link #getBinaryValue} will not need to decode data more
     * than once.
     */
    protected byte[] _binaryValue;

    /**
     * We will keep track of tag value for possible future use.
     */
    protected int _tagValue = -1;
    
    /*
    /**********************************************************
    /* Input source config, state (from ex StreamBasedParserBase)
    /**********************************************************
     */

    /**
     * Input stream that can be used for reading more content, if one
     * in use. May be null, if input comes just as a full buffer,
     * or if the stream has been closed.
     */
    protected InputStream _inputStream;

    /**
     * Current buffer from which data is read; generally data is read into
     * buffer from input source, but in some cases pre-loaded buffer
     * is handed to the parser.
     */
    protected byte[] _inputBuffer;

    /**
     * Flag that indicates whether the input buffer is recycable (and
     * needs to be returned to recycler once we are done) or not.
     *<p>
     * If it is not, it also means that parser can NOT modify underlying
     * buffer.
     */
    protected boolean _bufferRecyclable;
    
    /*
    /**********************************************************
    /* Additional parsing state
    /**********************************************************
     */

    /**
     * Flag that indicates that the current token has not yet
     * been fully processed, and needs to be finished for
     * some access (or skipped to obtain the next token)
     */
    protected boolean _tokenIncomplete = false;

    /**
     * Type byte of the current token
     */
    protected int _typeByte;

    /*
    /**********************************************************
    /* Symbol handling, decoding
    /**********************************************************
     */

    /**
     * Symbol table that contains field names encountered so far
     */
    final protected BytesToNameCanonicalizer _symbols;
    
    /**
     * Temporary buffer used for name parsing.
     */
    protected int[] _quadBuffer = NO_INTS;

    /**
     * Quads used for hash calculation
     */
    protected int _quad1, _quad2;

    /*
    /**********************************************************
    /* Constants and fields of former 'JsonNumericParserBase'
    /**********************************************************
     */

    final protected static int NR_UNKNOWN = 0;

    // First, integer types

    final protected static int NR_INT = 0x0001;
    final protected static int NR_LONG = 0x0002;
    final protected static int NR_BIGINT = 0x0004;

    // And then floating point types

    final protected static int NR_DOUBLE = 0x008;
    final protected static int NR_BIGDECIMAL = 0x0010;

    // Also, we need some numeric constants

    final static BigInteger BI_MIN_INT = BigInteger.valueOf(Integer.MIN_VALUE);
    final static BigInteger BI_MAX_INT = BigInteger.valueOf(Integer.MAX_VALUE);

    final static BigInteger BI_MIN_LONG = BigInteger.valueOf(Long.MIN_VALUE);
    final static BigInteger BI_MAX_LONG = BigInteger.valueOf(Long.MAX_VALUE);
    
    final static BigDecimal BD_MIN_LONG = new BigDecimal(BI_MIN_LONG);
    final static BigDecimal BD_MAX_LONG = new BigDecimal(BI_MAX_LONG);

    final static BigDecimal BD_MIN_INT = new BigDecimal(BI_MIN_INT);
    final static BigDecimal BD_MAX_INT = new BigDecimal(BI_MAX_INT);

    final static long MIN_INT_L = (long) Integer.MIN_VALUE;
    final static long MAX_INT_L = (long) Integer.MAX_VALUE;

    // These are not very accurate, but have to do... (for bounds checks)

    final static double MIN_LONG_D = (double) Long.MIN_VALUE;
    final static double MAX_LONG_D = (double) Long.MAX_VALUE;

    final static double MIN_INT_D = (double) Integer.MIN_VALUE;
    final static double MAX_INT_D = (double) Integer.MAX_VALUE;

    // Digits, numeric
    final protected static int INT_0 = '0';
    final protected static int INT_9 = '9';

    final protected static int INT_MINUS = '-';
    final protected static int INT_PLUS = '+';

    final protected static char CHAR_NULL = '\0';
    
    // Numeric value holders: multiple fields used for
    // for efficiency

    /**
     * Bitfield that indicates which numeric representations
     * have been calculated for the current type
     */
    protected int _numTypesValid = NR_UNKNOWN;

    // First primitives

    protected int _numberInt;
    protected long _numberLong;
    protected double _numberDouble;

    // And then object types

    protected BigInteger _numberBigInt;
    protected BigDecimal _numberBigDecimal;
    
    /*
    /**********************************************************
    /* Life-cycle
    /**********************************************************
     */
    
    public CBORParser(IOContext ctxt, int parserFeatures, int cborFeatures,
            ObjectCodec codec,
            BytesToNameCanonicalizer sym,
            InputStream in, byte[] inputBuffer, int start, int end,
            boolean bufferRecyclable)
    {
        super(parserFeatures);
        _ioContext = ctxt;
        _objectCodec = codec;
        _symbols = sym;

        _inputStream = in;
        _inputBuffer = inputBuffer;
        _inputPtr = start;
        _inputEnd = end;
        _bufferRecyclable = bufferRecyclable;
        _textBuffer = ctxt.constructTextBuffer();
        DupDetector dups = JsonParser.Feature.STRICT_DUPLICATE_DETECTION.enabledIn(parserFeatures)
                ? DupDetector.rootDetector(this) : null;
        _parsingContext = CBORReadContext.createRootContext(dups);

        _tokenInputRow = -1;
        _tokenInputCol = -1;
    }

    @Override
    public ObjectCodec getCodec() {
        return _objectCodec;
    }

    @Override
    public void setCodec(ObjectCodec c) {
        _objectCodec = c;
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
    /* Former StreamBasedParserBase methods
    /**********************************************************
     */

    @Override
    public int releaseBuffered(OutputStream out) throws IOException
    {
        int count = _inputEnd - _inputPtr;
        if (count < 1) {
            return 0;
        }
        // let's just advance ptr to end
        int origPtr = _inputPtr;
        out.write(_inputBuffer, origPtr, count);
        return count;
    }
    
    @Override
    public Object getInputSource() {
        return _inputStream;
    }

    /**
     * Overridden since we do not really have character-based locations,
     * but we do have byte offset to specify.
     */
    @Override
    public JsonLocation getTokenLocation()
    {
        // token location is correctly managed...
        return new JsonLocation(_ioContext.getSourceReference(),
                _tokenInputTotal, // bytes
                -1, -1, (int) _tokenInputTotal); // char offset, line, column
    }   

    /**
     * Overridden since we do not really have character-based locations,
     * but we do have byte offset to specify.
     */
    @Override
    public JsonLocation getCurrentLocation()
    {
        final long offset = _currInputProcessed + _inputPtr;
        return new JsonLocation(_ioContext.getSourceReference(),
                offset, // bytes
                -1, -1, (int) offset); // char offset, line, column
    }

    /**
     * Method that can be called to get the name associated with
     * the current event.
     */
    @Override
    public String getCurrentName() throws IOException
    {
        // [JACKSON-395]: start markers require information from parent
        if (_currToken == JsonToken.START_OBJECT || _currToken == JsonToken.START_ARRAY) {
            CBORReadContext parent = _parsingContext.getParent();
            return parent.getCurrentName();
        }
        return _parsingContext.getCurrentName();
    }

    @Override
    public void overrideCurrentName(String name)
    {
        // Simple, but need to look for START_OBJECT/ARRAY's "off-by-one" thing:
        CBORReadContext ctxt = _parsingContext;
        if (_currToken == JsonToken.START_OBJECT || _currToken == JsonToken.START_ARRAY) {
            ctxt = ctxt.getParent();
        }
        // Unfortunate, but since we did not expose exceptions, need to wrap
        try {
            ctxt.setCurrentName(name);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
    
    @Override
    public void close() throws IOException {
        if (!_closed) {
            _closed = true;
            _symbols.release();
            try {
                _closeInput();
            } finally {
                // as per [JACKSON-324], do in finally block
                // Also, internal buffer(s) can now be released as well
                _releaseBuffers();
            }
        }
    }

    @Override
    public boolean isClosed() { return _closed; }

    @Override
    public CBORReadContext getParsingContext() {
        return _parsingContext;
    }

    /*
    /**********************************************************
    /* Overridden methods
    /**********************************************************
     */

    @Override
    public boolean hasTextCharacters()
    {
        if (_currToken == JsonToken.VALUE_STRING) {
            // yes; is or can be made available efficiently as char[]
            return _textBuffer.hasTextAsCharacters();
        }
        if (_currToken == JsonToken.FIELD_NAME) {
            // not necessarily; possible but:
            return _nameCopied;
        }
        // other types, no benefit from accessing as char[]
        return false;
    }

    /**
     * Method called to release internal buffers owned by the base
     * reader. This may be called along with {@link #_closeInput} (for
     * example, when explicitly closing this reader instance), or
     * separately (if need be).
     */
    protected void _releaseBuffers() throws IOException
    {
         if (_bufferRecyclable) {
             byte[] buf = _inputBuffer;
             if (buf != null) {
                 _inputBuffer = null;
                 _ioContext.releaseReadIOBuffer(buf);
             }
         }
         _textBuffer.releaseBuffers();
         char[] buf = _nameCopyBuffer;
         if (buf != null) {
             _nameCopyBuffer = null;
             _ioContext.releaseNameCopyBuffer(buf);
         }
    }

    /*
    /**********************************************************
    /* JsonParser impl
    /**********************************************************
     */

    @Override
    public JsonToken nextToken() throws IOException
    {
        _numTypesValid = NR_UNKNOWN;
        _tagValue = -1;
        // For longer tokens (text, binary), we'll only read when requested
        if (_tokenIncomplete) {
            _skipIncomplete();
        }
        _tokenInputTotal = _currInputProcessed + _inputPtr;
        // also: clear any data retained so far
        _binaryValue = null;
        
        /* First: need to keep track of lengths of defined-length Arrays and
         * Objects (to materialize END_ARRAY/END_OBJECT as necessary);
         * as well as handle names for Object entries.
         */
        if (_parsingContext.inObject()) {
            if (_currToken != JsonToken.FIELD_NAME) {
                // completed the whole Object?
                if (!_parsingContext.expectMoreValues()) {
                    _parsingContext = _parsingContext.getParent();
                    return (_currToken = JsonToken.END_OBJECT);
                }
                return (_currToken = _decodeFieldName());
            }
        } else {
            if (!_parsingContext.expectMoreValues()) {
                _parsingContext = _parsingContext.getParent();
                return (_currToken = JsonToken.END_ARRAY);
            }
        }
        if (_inputPtr >= _inputEnd) {
            if (!loadMore()) {
                _handleEOF();
                /* NOTE: here we can and should close input, release buffers,
                 * since this is "hard" EOF, not a boundary imposed by
                 * header token.
                 */
                close();
                return (_currToken = null);
            }
        }
        int ch = _inputBuffer[_inputPtr++];
        final int lowBits = ch & 0x1F;
        switch ((ch >> 5) & 0x7) {
        case 0: // positive int
            _numTypesValid = NR_INT;
            if (lowBits <= 23) {
                _numberInt = lowBits;
            } else {
                switch (lowBits - 24) {
                case 0:
                    _numberInt = _decode8Bits();
                    break;
                case 1:
                    _numberInt = _decode16Bits();
                    break;
                case 2:
                    _numberInt = _decode32Bits();
                    break;
                case 3:
                    _numberLong = _decode64Bits();
                    _numTypesValid = NR_LONG;
                    break;
                default:
                    _invalidToken(ch);
                }
            }
            return (_currToken = JsonToken.VALUE_NUMBER_INT);
        case 1: // negative int
            _numTypesValid = NR_INT;
            if (lowBits <= 23) {
                _numberInt = -lowBits - 1;
            } else {
                switch (lowBits - 24) {
                case 0:
                    _numberInt = -_decode8Bits() - 1;
                    break;
                case 1:
                    _numberInt = -_decode16Bits() - 1;
                    break;
                case 2:
                    _numberInt = -_decode32Bits() - 1;
                    break;
                case 3:
                    _numberLong = -_decode64Bits() - 1L;
                    _numTypesValid = NR_LONG;
                    break;
                default:
                    _invalidToken(ch);
                }
            }
            return (_currToken = JsonToken.VALUE_NUMBER_INT);

        case 2: // byte[]
            _typeByte = ch;
            _tokenIncomplete = true;
            return (_currToken = JsonToken.VALUE_EMBEDDED_OBJECT);

        case 3: // String
            _typeByte = ch;
            _tokenIncomplete = true;
            return (_currToken = JsonToken.VALUE_STRING);

        case 4: // Array
            _currToken = JsonToken.START_ARRAY;
            {
                int len = _decodeExplicitLength(lowBits);
                _parsingContext = _parsingContext.createChildArrayContext(len);
            }
            return _currToken;

        case 5: // Object
            _currToken = JsonToken.START_OBJECT;
            {
                int len = _decodeExplicitLength(lowBits);
                _parsingContext = _parsingContext.createChildObjectContext(len);
            }
            return _currToken;

        case 6: // tag
            _tagValue = Integer.valueOf(_decodeTag(lowBits));
            return nextToken();
            
        default: // misc: tokens, floats
            switch (lowBits) {
            case 20:
                return (_currToken = JsonToken.VALUE_FALSE);
            case 21:
                return (_currToken = JsonToken.VALUE_TRUE);
            case 22:
                return (_currToken = JsonToken.VALUE_NULL);
            case 25: // 16-bit float... 
                // As per [http://stackoverflow.com/questions/5678432/decompressing-half-precision-floats-in-javascript]
                {
                    float f = (float) _decodeHalfSizeFloat();
                    _numberDouble = (double) f;
                    _numTypesValid = NR_DOUBLE;
                }
                return (_currToken = JsonToken.VALUE_NUMBER_FLOAT);
            case 26: // Float32
                {
                    float f = Float.intBitsToFloat(_decode32Bits());
                    _numberDouble = (double) f;
                    _numTypesValid = NR_DOUBLE;
                }
                return (_currToken = JsonToken.VALUE_NUMBER_FLOAT);
            case 27: // Float64
                _numberDouble = Double.longBitsToDouble(_decode64Bits());
                _numTypesValid = NR_DOUBLE;
                return (_currToken = JsonToken.VALUE_NUMBER_FLOAT);
            case 31: // Break
                if (_parsingContext.inArray()) {
                    if (!_parsingContext.hasExpectedLength()) {
                        _parsingContext = _parsingContext.getParent();
                        return (_currToken = JsonToken.END_ARRAY);
                    }
                }
                // Object end-marker can't occur here
                _reportUnexpectedBreak();
            }
            _invalidToken(ch);
        }
        return null;
    }

    protected String _numberToName(int ch, boolean neg) throws IOException
    {
        final int lowBits = ch & 0x1F;
        int i;
        if (lowBits <= 23) {
            i = lowBits;
        } else {
            switch (lowBits) {
            case 24:
                i = _decode8Bits();
                break;
            case 25:
                i = _decode16Bits();
                break;
            case 26:
                i = _decode32Bits();
                break;
            case 27:
                {
                    long l = _decode64Bits();
                    if (neg) {
                        l = -l - 1L;
                    }
                    return String.valueOf(l);
                }
            default:
                throw _constructError("Invalid length indicator for ints ("+lowBits+"), token 0x"+Integer.toHexString(ch));
            }
        }
        if (neg) {
            i = -i - 1;
        }
        return String.valueOf(1);
    }
    
    // base impl is fine:
    //public String getCurrentName() throws IOException

    protected void _invalidToken(int ch) throws JsonParseException {
        ch &= 0xFF;
        if (ch == 0xFF) {
            throw _constructError("Mismatched BREAK byte (0xFF): encountered where value expected");
        }
        throw _constructError("Invalid CBOR value token (first byte): 0x"+Integer.toHexString(ch));
    }
    
    /*
    /**********************************************************
    /* Public API, traversal, nextXxxValue/nextFieldName
    /**********************************************************
     */

    /*
    @Override
    public boolean nextFieldName(SerializableString str) throws IOException
    {
        // Two parsing modes; can only succeed if expecting field name, so handle that first:
        if (_parsingContext.inObject() && _currToken != JsonToken.FIELD_NAME) {
            // ...
        }
        // otherwise just fall back to default handling; should occur rarely
        return (nextToken() == JsonToken.FIELD_NAME) && str.getValue().equals(getCurrentName());
    }
    */

    /*
    @Override
    public String nextTextValue() throws IOException
    {
        // can't get text value if expecting name, so
        if (!_parsingContext.inObject() || _currToken == JsonToken.FIELD_NAME) {
        }
        // otherwise fall back to generic handling:
        return (nextToken() == JsonToken.VALUE_STRING) ? getText() : null;
    }
    */

    @Override
    public int nextIntValue(int defaultValue) throws IOException
    {
        if (nextToken() == JsonToken.VALUE_NUMBER_INT) {
            return getIntValue();
        }
        return defaultValue;
    }

    @Override
    public long nextLongValue(long defaultValue) throws IOException
    {
        if (nextToken() == JsonToken.VALUE_NUMBER_INT) {
            return getLongValue();
        }
        return defaultValue;
    }

    @Override
    public Boolean nextBooleanValue() throws IOException
    {
        switch (nextToken()) {
        case VALUE_TRUE:
            return Boolean.TRUE;
        case VALUE_FALSE:
            return Boolean.FALSE;
        default:
            return null;
        }
    }
    
    /*
    /**********************************************************
    /* Public API, access to token information, text
    /**********************************************************
     */

    /**
     * Method for accessing textual representation of the current event;
     * if no current event (before first call to {@link #nextToken}, or
     * after encountering end-of-input), returns null.
     * Method can be called for any event.
     */
    @Override    
    public String getText() throws IOException
    {
        if (_tokenIncomplete) {
            _finishToken();
        }
        if (_currToken == JsonToken.VALUE_STRING) {
            return _textBuffer.contentsAsString();
        }
        JsonToken t = _currToken;
        if (t == null) { // null only before/after document
            return null;
        }
        if (t == JsonToken.FIELD_NAME) {
            return _parsingContext.getCurrentName();
        }
        if (t.isNumeric()) {
            return getNumberValue().toString();
        }
        return _currToken.asString();
    }

    @Override
    public char[] getTextCharacters() throws IOException
    {
        if (_currToken != null) { // null only before/after document
            if (_tokenIncomplete) {
                _finishToken();
            }
            switch (_currToken) {                
            case VALUE_STRING:
                return _textBuffer.getTextBuffer();
            case FIELD_NAME:
                return _parsingContext.getCurrentName().toCharArray();
                // fall through
            case VALUE_NUMBER_INT:
            case VALUE_NUMBER_FLOAT:
                return getNumberValue().toString().toCharArray();
                
            default:
                return _currToken.asCharArray();
            }
        }
        return null;
    }

    @Override    
    public int getTextLength() throws IOException
    {
        if (_currToken != null) { // null only before/after document
            if (_tokenIncomplete) {
                _finishToken();
            }
            switch (_currToken) {
            case VALUE_STRING:
                return _textBuffer.size();                
            case FIELD_NAME:
                return _parsingContext.getCurrentName().length();
                // fall through
            case VALUE_NUMBER_INT:
            case VALUE_NUMBER_FLOAT:
                return getNumberValue().toString().length();
                
            default:
                return _currToken.asCharArray().length;
            }
        }
        return 0;
    }

    @Override
    public int getTextOffset() throws IOException {
        return 0;
    }

    @Override
    public String getValueAsString() throws IOException
    {
        if (_currToken != JsonToken.VALUE_STRING) {
            if (_currToken == null || _currToken == JsonToken.VALUE_NULL || !_currToken.isScalarValue()) {
                return null;
            }
        }
        return getText();
    }

    @Override
    public String getValueAsString(String defaultValue) throws IOException
    {
        if (_currToken != JsonToken.VALUE_STRING) {
            if (_currToken == null || _currToken == JsonToken.VALUE_NULL || !_currToken.isScalarValue()) {
                return defaultValue;
            }
        }
        return getText();
    }
    
    /*
    /**********************************************************
    /* Public API, access to token information, binary
    /**********************************************************
     */

    @Override
    public byte[] getBinaryValue(Base64Variant b64variant) throws IOException
    {
        if (_tokenIncomplete) {
            _finishToken();
        }
        if (_currToken != JsonToken.VALUE_EMBEDDED_OBJECT ) {
            // TODO, maybe: support base64 for text?
            _reportError("Current token ("+_currToken+") not VALUE_EMBEDDED_OBJECT, can not access as binary");
        }
        return _binaryValue;
    }

    @Override
    public Object getEmbeddedObject() throws IOException
    {
        if (_tokenIncomplete) {
            _finishToken();
        }
        if (_currToken == JsonToken.VALUE_EMBEDDED_OBJECT ) {
            return _binaryValue;
        }
        return null;
    }

    @Override
    public int readBinaryValue(Base64Variant b64variant, OutputStream out) throws IOException
    {
        if (_currToken != JsonToken.VALUE_EMBEDDED_OBJECT ) {
            // Todo, maybe: support base64 for text?
            _reportError("Current token ("+_currToken+") not VALUE_EMBEDDED_OBJECT, can not access as binary");
        }
        if (!_tokenIncomplete) { // someone already decoded or read
            if (_binaryValue == null) { // if this method called twice in a row
                return 0;
            }
            final int len = _binaryValue.length;
            out.write(_binaryValue, 0, len);
            return len;
        }

        _tokenIncomplete = false;
        int len = _decodeExplicitLength(_typeByte & 0x1F);
        if (len >= 0) { // non-chunked
            return _readAndWriteBytes(out, len);
        }
        // Chunked...
        int total = 0;
        while (true) {
            len = _decodeChunkLength(CBORConstants.MAJOR_TYPE_BYTES);
            if (len < 0) {
                return total;
            }
            total += _readAndWriteBytes(out, len);
        }
    }

    private int _readAndWriteBytes(OutputStream out, int total) throws IOException
    {
        int left = total;
        while (left > 0) {
            int avail = _inputEnd - _inputPtr;
            if (_inputPtr >= _inputEnd) {
                loadMoreGuaranteed();
                avail = _inputEnd - _inputPtr;
            }
            int count = Math.min(avail, left);
            out.write(_inputBuffer, _inputPtr, count);
            _inputPtr += count;
            left -= count;
        }
        _tokenIncomplete = false;
        return total;
    }
    
    /*
    /**********************************************************
    /* Numeric accessors of public API
    /**********************************************************
     */
    
    @Override
    public Number getNumberValue() throws IOException
    {
        if (_numTypesValid == NR_UNKNOWN) {
            _checkNumericValue(NR_UNKNOWN); // will also check event type
        }
        // Separate types for int types
        if (_currToken == JsonToken.VALUE_NUMBER_INT) {
            if ((_numTypesValid & NR_INT) != 0) {
                return _numberInt;
            }
            if ((_numTypesValid & NR_LONG) != 0) {
                return _numberLong;
            }
            if ((_numTypesValid & NR_BIGINT) != 0) {
                return _numberBigInt;
            }
            // Shouldn't get this far but if we do
            return _numberBigDecimal;
        }
    
        /* And then floating point types. But here optimal type
         * needs to be big decimal, to avoid losing any data?
         */
        if ((_numTypesValid & NR_BIGDECIMAL) != 0) {
            return _numberBigDecimal;
        }
        if ((_numTypesValid & NR_DOUBLE) == 0) { // sanity check
            _throwInternal();
        }
        return _numberDouble;
    }
    
    @Override
    public NumberType getNumberType() throws IOException
    {
        if (_numTypesValid == NR_UNKNOWN) {
            _checkNumericValue(NR_UNKNOWN); // will also check event type
        }
        if (_currToken == JsonToken.VALUE_NUMBER_INT) {
            if ((_numTypesValid & NR_INT) != 0) {
                return NumberType.INT;
            }
            if ((_numTypesValid & NR_LONG) != 0) {
                return NumberType.LONG;
            }
            return NumberType.BIG_INTEGER;
        }
    
        /* And then floating point types. Here optimal type
         * needs to be big decimal, to avoid losing any data?
         * However... using BD is slow, so let's allow returning
         * double as type if no explicit call has been made to access
         * data as BD?
         */
        if ((_numTypesValid & NR_BIGDECIMAL) != 0) {
            return NumberType.BIG_DECIMAL;
        }
        return NumberType.DOUBLE;
    }
    
    @Override
    public int getIntValue() throws IOException
    {
        if ((_numTypesValid & NR_INT) == 0) {
            if (_numTypesValid == NR_UNKNOWN) { // not parsed at all
                _checkNumericValue(NR_INT); // will also check event type
            }
            if ((_numTypesValid & NR_INT) == 0) { // wasn't an int natively?
                convertNumberToInt(); // let's make it so, if possible
            }
        }
        return _numberInt;
    }
    
    @Override
    public long getLongValue() throws IOException
    {
        if ((_numTypesValid & NR_LONG) == 0) {
            if (_numTypesValid == NR_UNKNOWN) {
                _checkNumericValue(NR_LONG);
            }
            if ((_numTypesValid & NR_LONG) == 0) {
                convertNumberToLong();
            }
        }
        return _numberLong;
    }
    
    @Override
    public BigInteger getBigIntegerValue() throws IOException
    {
        if ((_numTypesValid & NR_BIGINT) == 0) {
            if (_numTypesValid == NR_UNKNOWN) {
                _checkNumericValue(NR_BIGINT);
            }
            if ((_numTypesValid & NR_BIGINT) == 0) {
                convertNumberToBigInteger();
            }
        }
        return _numberBigInt;
    }
    
    @Override
    public float getFloatValue() throws IOException
    {
        double value = getDoubleValue();
        /* 22-Jan-2009, tatu: Bounds/range checks would be tricky
         *   here, so let's not bother even trying...
         */
        /*
        if (value < -Float.MAX_VALUE || value > MAX_FLOAT_D) {
            _reportError("Numeric value ("+getText()+") out of range of Java float");
        }
        */
        return (float) value;
    }
    
    @Override
    public double getDoubleValue() throws IOException
    {
        if ((_numTypesValid & NR_DOUBLE) == 0) {
            if (_numTypesValid == NR_UNKNOWN) {
                _checkNumericValue(NR_DOUBLE);
            }
            if ((_numTypesValid & NR_DOUBLE) == 0) {
                convertNumberToDouble();
            }
        }
        return _numberDouble;
    }
    
    @Override
    public BigDecimal getDecimalValue() throws IOException
    {
        if ((_numTypesValid & NR_BIGDECIMAL) == 0) {
            if (_numTypesValid == NR_UNKNOWN) {
                _checkNumericValue(NR_BIGDECIMAL);
            }
            if ((_numTypesValid & NR_BIGDECIMAL) == 0) {
                convertNumberToBigDecimal();
            }
        }
        return _numberBigDecimal;
    }

    /*
    /**********************************************************
    /* Numeric conversions
    /**********************************************************
     */    

    protected void _checkNumericValue(int expType) throws IOException
    {
        // Int or float?
        if (_currToken == JsonToken.VALUE_NUMBER_INT || _currToken == JsonToken.VALUE_NUMBER_FLOAT) {
            return;
        }
        _reportError("Current token ("+_currToken+") not numeric, can not use numeric value accessors");
    }

    protected void convertNumberToInt() throws IOException
    {
        // First, converting from long ought to be easy
        if ((_numTypesValid & NR_LONG) != 0) {
            // Let's verify it's lossless conversion by simple roundtrip
            int result = (int) _numberLong;
            if (((long) result) != _numberLong) {
                _reportError("Numeric value ("+getText()+") out of range of int");
            }
            _numberInt = result;
        } else if ((_numTypesValid & NR_BIGINT) != 0) {
            if (BI_MIN_INT.compareTo(_numberBigInt) > 0 
                    || BI_MAX_INT.compareTo(_numberBigInt) < 0) {
                reportOverflowInt();
            }
            _numberInt = _numberBigInt.intValue();
        } else if ((_numTypesValid & NR_DOUBLE) != 0) {
            // Need to check boundaries
            if (_numberDouble < MIN_INT_D || _numberDouble > MAX_INT_D) {
                reportOverflowInt();
            }
            _numberInt = (int) _numberDouble;
        } else if ((_numTypesValid & NR_BIGDECIMAL) != 0) {
            if (BD_MIN_INT.compareTo(_numberBigDecimal) > 0 
                || BD_MAX_INT.compareTo(_numberBigDecimal) < 0) {
                reportOverflowInt();
            }
            _numberInt = _numberBigDecimal.intValue();
        } else {
            _throwInternal();
        }
        _numTypesValid |= NR_INT;
    }
    
    protected void convertNumberToLong() throws IOException
    {
        if ((_numTypesValid & NR_INT) != 0) {
            _numberLong = (long) _numberInt;
        } else if ((_numTypesValid & NR_BIGINT) != 0) {
            if (BI_MIN_LONG.compareTo(_numberBigInt) > 0 
                    || BI_MAX_LONG.compareTo(_numberBigInt) < 0) {
                reportOverflowLong();
            }
            _numberLong = _numberBigInt.longValue();
        } else if ((_numTypesValid & NR_DOUBLE) != 0) {
            // Need to check boundaries
            if (_numberDouble < MIN_LONG_D || _numberDouble > MAX_LONG_D) {
                reportOverflowLong();
            }
            _numberLong = (long) _numberDouble;
        } else if ((_numTypesValid & NR_BIGDECIMAL) != 0) {
            if (BD_MIN_LONG.compareTo(_numberBigDecimal) > 0 
                || BD_MAX_LONG.compareTo(_numberBigDecimal) < 0) {
                reportOverflowLong();
            }
            _numberLong = _numberBigDecimal.longValue();
        } else {
            _throwInternal();
        }
        _numTypesValid |= NR_LONG;
    }
    
    protected void convertNumberToBigInteger() throws IOException
    {
        if ((_numTypesValid & NR_BIGDECIMAL) != 0) {
            // here it'll just get truncated, no exceptions thrown
            _numberBigInt = _numberBigDecimal.toBigInteger();
        } else if ((_numTypesValid & NR_LONG) != 0) {
            _numberBigInt = BigInteger.valueOf(_numberLong);
        } else if ((_numTypesValid & NR_INT) != 0) {
            _numberBigInt = BigInteger.valueOf(_numberInt);
        } else if ((_numTypesValid & NR_DOUBLE) != 0) {
            _numberBigInt = BigDecimal.valueOf(_numberDouble).toBigInteger();
        } else {
            _throwInternal();
        }
        _numTypesValid |= NR_BIGINT;
    }
    
    protected void convertNumberToDouble() throws IOException
    {
        // Note: this MUST start with more accurate representations, since we don't know which
        //  value is the original one (others get generated when requested)
        if ((_numTypesValid & NR_BIGDECIMAL) != 0) {
            _numberDouble = _numberBigDecimal.doubleValue();
        } else if ((_numTypesValid & NR_BIGINT) != 0) {
            _numberDouble = _numberBigInt.doubleValue();
        } else if ((_numTypesValid & NR_LONG) != 0) {
            _numberDouble = (double) _numberLong;
        } else if ((_numTypesValid & NR_INT) != 0) {
            _numberDouble = (double) _numberInt;
        } else {
            _throwInternal();
        }
        _numTypesValid |= NR_DOUBLE;
    }
    
    protected void convertNumberToBigDecimal() throws IOException
    {
        // Note: this MUST start with more accurate representations, since we don't know which
        //  value is the original one (others get generated when requested)
        if ((_numTypesValid & NR_DOUBLE) != 0) {
            // Let's parse from String representation, to avoid rounding errors that
            //non-decimal floating operations would incur
            _numberBigDecimal = NumberInput.parseBigDecimal(getText());
        } else if ((_numTypesValid & NR_BIGINT) != 0) {
            _numberBigDecimal = new BigDecimal(_numberBigInt);
        } else if ((_numTypesValid & NR_LONG) != 0) {
            _numberBigDecimal = BigDecimal.valueOf(_numberLong);
        } else if ((_numTypesValid & NR_INT) != 0) {
            _numberBigDecimal = BigDecimal.valueOf(_numberInt);
        } else {
            _throwInternal();
        }
        _numTypesValid |= NR_BIGDECIMAL;
    }

    protected void reportOverflowInt() throws IOException {
        _reportError("Numeric value ("+getText()+") out of range of int ("+Integer.MIN_VALUE+" - "+Integer.MAX_VALUE+")");
    }
    
    protected void reportOverflowLong() throws IOException {
        _reportError("Numeric value ("+getText()+") out of range of long ("+Long.MIN_VALUE+" - "+Long.MAX_VALUE+")");
    }    
    
    /*
    /**********************************************************
    /* Internal methods, secondary parsing
    /**********************************************************
     */

    /**
     * Method called to finish parsing of a token so that token contents
     * are retriable
     */
    protected void _finishToken() throws IOException
    {
        _tokenIncomplete = false;
        int ch = _typeByte;
        final int type = ((ch >> 5) & 0x7);
        ch &= 0x1F;

        // Either String or byte[]
        if (type != CBORConstants.MAJOR_TYPE_TEXT) {
            if (type == CBORConstants.MAJOR_TYPE_BYTES) {
                _finishBytes(_decodeExplicitLength(ch));
                return;
            }
            // should never happen so
            _throwInternal();
        }

        // String value, decode
        final int len = _decodeExplicitLength(ch);

        if (len <= 0) {
            if (len < 0) {
                _finishChunkedText();
            } else {
                _textBuffer.resetWithEmpty();
            }
            return;
        }
        if (len > (_inputEnd - _inputPtr)) {
            // or if not, could we read?
            if (len >= _inputBuffer.length) {
                // If not enough space, need handling similar to chunked
                _finishLongText(len);
                return;
            }
            _loadToHaveAtLeast(len);
        }
        // offline for better optimization
        _finishShortText(len);
    }

    private final void _finishShortText(int len) throws IOException
    {
        char[] outBuf = _textBuffer.emptyAndGetCurrentSegment();
        if (outBuf.length < len) { // one minor complication
            outBuf = _textBuffer.expandCurrentSegment(len);
        }
        
        int outPtr = 0;
        int inPtr = _inputPtr;
        _inputPtr += len;
        final int[] codes = UTF8_UNIT_CODES;
        final byte[] inputBuf = _inputBuffer;

        // Let's actually do a tight loop for ASCII first:
        final int end = inPtr + len;

        while (true) {
            int i = inputBuf[inPtr] & 0xFF;
            // tight(er) loop for ASCII
            if (codes[i] != 0) {
                break;
            }
            outBuf[outPtr++] = (char) i;
            if (++inPtr == end) {
                _textBuffer.setCurrentLength(outPtr);
                return;
            }
        }
        do {
            int i = inputBuf[inPtr++] & 0xFF;
            switch (codes[i]) {
            case 0:
                break;
            case 1:
                i = ((i & 0x1F) << 6) | (inputBuf[inPtr++] & 0x3F);
                break;
            case 2:
                i = ((i & 0x0F) << 12)
                   | ((inputBuf[inPtr++] & 0x3F) << 6)
                   | (inputBuf[inPtr++] & 0x3F);
                break;
            case 3:
                i = ((i & 0x07) << 18)
                 | ((inputBuf[inPtr++] & 0x3F) << 12)
                 | ((inputBuf[inPtr++] & 0x3F) << 6)
                 | (inputBuf[inPtr++] & 0x3F);
                // note: this is the codepoint value; need to split, too
                i -= 0x10000;
                outBuf[outPtr++] = (char) (0xD800 | (i >> 10));
                i = 0xDC00 | (i & 0x3FF);
                break;
            default: // invalid
                _reportError("Invalid byte "+Integer.toHexString(i)+" in Unicode text block");
            }
            outBuf[outPtr++] = (char) i;
        } while (inPtr < end);
        _textBuffer.setCurrentLength(outPtr);
    }

    private final void _finishLongText(int len) throws IOException
    {
        char[] outBuf = _textBuffer.emptyAndGetCurrentSegment();
        int outPtr = 0;
        final int[] codes = UTF8_UNIT_CODES;
        int outEnd = outBuf.length;

        while (--len >= 0) {
            int c = (int) _nextByteAsInt();
            int code = codes[c];
            if (code == 0 && outPtr < outEnd) {
                outBuf[outPtr++] = (char) c;
                continue;
            }
            if ((len -= code) < 0) { // may need to improve error here but...
                throw _constructError("Malformed UTF-8 character at end of long (non-chunked) text segment");
            }
            
            switch (code) {
            case 0:
                break;
            case 1: // 2-byte UTF
                {
                    int d = _nextByte();
                    if ((d & 0xC0) != 0x080) {
                        _reportInvalidOther(d & 0xFF, _inputPtr);
                    }
                    c = ((c & 0x1F) << 6) | (d & 0x3F);
                }
                break;
            case 2: // 3-byte UTF
                c = _decodeUtf8_3(c);
                break;
            case 3: // 4-byte UTF
                c = _decodeUtf8_4(c);
                // Let's add first part right away:
                outBuf[outPtr++] = (char) (0xD800 | (c >> 10));
                if (outPtr >= outBuf.length) {
                    outBuf = _textBuffer.finishCurrentSegment();
                    outPtr = 0;
                    outEnd = outBuf.length;
                }
                c = 0xDC00 | (c & 0x3FF);
                // And let the other char output down below
                break;
            default:
                // Is this good enough error message?
                _reportInvalidChar(c);
            }
            // Need more room?
            if (outPtr >= outEnd) {
                outBuf = _textBuffer.finishCurrentSegment();
                outPtr = 0;
                outEnd = outBuf.length;
            }
            // Ok, let's add char to output:
            outBuf[outPtr++] = (char) c;
        }
        _textBuffer.setCurrentLength(outPtr);
    }

    // !!! TODO
    private final void _finishChunkedText() throws IOException
    {
        char[] outBuf = _textBuffer.emptyAndGetCurrentSegment();
        int outPtr = 0;
        final int[] codes = UTF8_UNIT_CODES;
        int outEnd = outBuf.length;
        
        while (true) {
            int len = _decodeChunkLength(CBORConstants.MAJOR_TYPE_TEXT);
            if (len < 0) {
                break;
            }
            while (--len >= 0) {
                int c = (int) _nextByteAsInt();
                int code = codes[c];
                if (code == 0 && outPtr < outEnd) {
                    outBuf[outPtr++] = (char) c;
                    continue;
                }
                if ((len -= code) < 0) { // may or may not matter
                    throw _constructError("Malformed UTF-8 character at end of long (non-chunked) text segment");
                }
                
                switch (code) {
                case 0:
                    break;
                case 1: // 2-byte UTF
                    {
                        int d = _nextByte();
                        if ((d & 0xC0) != 0x080) {
                            _reportInvalidOther(d & 0xFF, _inputPtr);
                        }
                        c = ((c & 0x1F) << 6) | (d & 0x3F);
                    }
                    break;
                case 2: // 3-byte UTF
                    c = _decodeUtf8_3(c);
                    break;
                case 3: // 4-byte UTF
                    c = _decodeUtf8_4(c);
                    // Let's add first part right away:
                    outBuf[outPtr++] = (char) (0xD800 | (c >> 10));
                    if (outPtr >= outBuf.length) {
                        outBuf = _textBuffer.finishCurrentSegment();
                        outPtr = 0;
                        outEnd = outBuf.length;
                    }
                    c = 0xDC00 | (c & 0x3FF);
                    // And let the other char output down below
                    break;
                default:
                    // Is this good enough error message?
                    _reportInvalidChar(c);
                }
                // Need more room?
                if (outPtr >= outEnd) {
                    outBuf = _textBuffer.finishCurrentSegment();
                    outPtr = 0;
                    outEnd = outBuf.length;
                }
                // Ok, let's add char to output:
                outBuf[outPtr++] = (char) c;
            }
        }
        _textBuffer.setCurrentLength(outPtr);

        //////////

//        int len = _decodeChunkLength(CBORConstants.MAJOR_TYPE_TEXT);
    }

    private final int _nextByte() throws IOException {
        int inPtr = _inputPtr;
        if (inPtr >= _inputEnd) {
            loadMoreGuaranteed();
            inPtr = _inputPtr;
        }
        int ch = _inputBuffer[inPtr];
        _inputPtr = inPtr+1;
        return ch;
    }
    
    private final int _nextByteAsInt() throws IOException {
        int inPtr = _inputPtr;
        if (inPtr >= _inputEnd) {
            loadMoreGuaranteed();
            inPtr = _inputPtr;
        }
        int ch = _inputBuffer[inPtr] & 0xFF;
        _inputPtr = inPtr+1;
        return ch;
    }
    
    @SuppressWarnings("resource")
    protected void _finishBytes(int len) throws IOException
    {
        // First, simple: non-chunked
        if (len >= 0) {
            _binaryValue = new byte[len];
            if (_inputPtr >= _inputEnd) {
                loadMoreGuaranteed();
            }
            int ptr = 0;
            while (true) {
                int toAdd = Math.min(len, _inputEnd - _inputPtr);
                System.arraycopy(_inputBuffer, _inputPtr, _binaryValue, ptr, toAdd);
                _inputPtr += toAdd;
                ptr += toAdd;
                len -= toAdd;
                if (len <= 0) {
                    return;
                }
                loadMoreGuaranteed();
            }
        }

        // or, if not, chunked...
        ByteArrayBuilder bb = _getByteArrayBuilder();
        while (true) {
            if (_inputPtr >= _inputEnd) {
                loadMoreGuaranteed();
            }
            int ch = _inputBuffer[_inputPtr++] & 0xFF;
            if (ch == 0xFF) { // end marker
                break;
            }
            // verify that type matches
            int type = (ch >> 5);
            if (type != CBORConstants.MAJOR_TYPE_BYTES) {
                throw _constructError("Mismatched chunk in chunked content: expected "+CBORConstants.MAJOR_TYPE_BYTES
                        +" but encountered "+type);
            }
            len = _decodeExplicitLength(ch & 0x1F);
            if (len < 0) {
                throw _constructError("Illegal chunked-length indicator within chunked-length value (type "+CBORConstants.MAJOR_TYPE_BYTES+")");
            }
            while (len > 0) {
                int avail = _inputEnd - _inputPtr;
                if (_inputPtr >= _inputEnd) {
                    loadMoreGuaranteed();
                    avail = _inputEnd - _inputPtr;
                }
                int count = Math.min(avail, len);
                bb.write(_inputBuffer, _inputPtr, count);
                _inputPtr += count;
                len -= count;
            }
        }
        _binaryValue = bb.toByteArray();
    }
    
    protected final JsonToken _decodeFieldName() throws IOException
    {     
        if (_inputPtr >= _inputEnd) {
            loadMoreGuaranteed();
        }
        final int ch = _inputBuffer[_inputPtr++];
        final int type = ((ch >> 5) & 0x7);

        // Expecting a String, but let's allow int numbers too
        if (type != CBORConstants.MAJOR_TYPE_TEXT) { // the usual case
            if (ch == -1) {
                if (!_parsingContext.hasExpectedLength()) {
                    _parsingContext = _parsingContext.getParent();
                    return JsonToken.END_OBJECT;
                }
                _reportUnexpectedBreak();
            }
            _decodeNonStringName(ch);
            return JsonToken.FIELD_NAME;
        }
        final int lenMarker = ch & 0x1F;
        String name;
        if (lenMarker <= 23) {
            if (lenMarker == 0) {
                name = "";
            } else {
                Name n = _findDecodedFromSymbols(lenMarker);
                if (n != null) {
                    name = n.getName();
                    _inputPtr += lenMarker;
                } else {
                    name = _decodeShortName(lenMarker);
                    name = _addDecodedToSymbols(lenMarker, name);
                }
            }
        } else {
            final int actualLen = _decodeExplicitLength(lenMarker);
            if (actualLen < 0) {
                name = _decodeChunkedName();
            } else {
                name = _decodeLongerName(actualLen);
            }
        }
        _parsingContext.setCurrentName(name);
        return JsonToken.FIELD_NAME;
    }
    
    private final String _decodeShortName(int len) throws IOException
    {
        // note: caller ensures we have enough bytes available
        int outPtr = 0;
        char[] outBuf = _textBuffer.emptyAndGetCurrentSegment();
        if (outBuf.length < len) { // one minor complication
            outBuf = _textBuffer.expandCurrentSegment(len);
        }
        int inPtr = _inputPtr;
        _inputPtr += len;
        final int[] codes = UTF8_UNIT_CODES;
        final byte[] inBuf = _inputBuffer;

        // First a tight loop for Ascii
        final int end = inPtr + len;
        while (true) {
            int i = inBuf[inPtr] & 0xFF;
            int code = codes[i];
            if (code != 0) {
                break;
            }
            outBuf[outPtr++] = (char) i;
            if (++inPtr == end) {
                _textBuffer.setCurrentLength(outPtr);
                return _textBuffer.contentsAsString();
            }
        }

        // But in case there's multi-byte char, use a full loop
        while (inPtr < end) {
            int i = inBuf[inPtr++] & 0xFF;
            int code = codes[i];
            if (code != 0) {
                // trickiest one, need surrogate handling
                switch (code) {
                case 1:
                    i = ((i & 0x1F) << 6) | (inBuf[inPtr++] & 0x3F);
                    break;
                case 2:
                    i = ((i & 0x0F) << 12)
                    | ((inBuf[inPtr++] & 0x3F) << 6)
                    | (inBuf[inPtr++] & 0x3F);
                    break;
                case 3:
                    i = ((i & 0x07) << 18)
                    | ((inBuf[inPtr++] & 0x3F) << 12)
                    | ((inBuf[inPtr++] & 0x3F) << 6)
                    | (inBuf[inPtr++] & 0x3F);
                    // note: this is the codepoint value; need to split, too
                    i -= 0x10000;
                    outBuf[outPtr++] = (char) (0xD800 | (i >> 10));
                    i = 0xDC00 | (i & 0x3FF);
                    break;
                default: // invalid
                    _reportError("Invalid byte "+Integer.toHexString(i)+" in Object name");
                }
            }
            outBuf[outPtr++] = (char) i;
        }
        _textBuffer.setCurrentLength(outPtr);
        return _textBuffer.contentsAsString();
    }

    private final String _decodeLongerName(int len) throws IOException
    {
        // Do we have enough buffered content to read?
        if ((_inputEnd - _inputPtr) < len) {
            // or if not, could we read?
            if (len >= _inputBuffer.length) {
                // If not enough space, need handling similar to chunked
                _finishLongText(len);
                return _textBuffer.contentsAsString();
            }
            _loadToHaveAtLeast(len);
        }
        Name n = _findDecodedFromSymbols(len);
        if (n != null) {
            _inputPtr += len;
            return n.getName();
        }
        String name = _decodeShortName(len);
        return _addDecodedToSymbols(len, name);
    }
    
    private final String _decodeChunkedName() throws IOException
    {
        _finishChunkedText();
        return _textBuffer.contentsAsString();
    }
    
    /**
     * Method that handles initial token type recognition for token
     * that has to be either FIELD_NAME or END_OBJECT.
     */
    protected final void _decodeNonStringName(int ch) throws IOException
    {
        final int type = ((ch >> 5) & 0x7);
        String name;
        if (type == CBORConstants.MAJOR_TYPE_INT_POS) {
            name = _numberToName(ch, false);
        } else if (type == CBORConstants.MAJOR_TYPE_INT_NEG) {
            name = _numberToName(ch, true);
        } else {
            if ((ch & 0xFF) == CBORConstants.INT_BREAK) {
                _reportUnexpectedBreak();
            }
            throw _constructError("Unsupported major type ("+type+") for CBOR Objects, not (yet?) supported, only Strings");
        }
        _parsingContext.setCurrentName(name);
    }
    
    private final Name _findDecodedFromSymbols(int len) throws IOException
    {
        if ((_inputEnd - _inputPtr) < len) {
            _loadToHaveAtLeast(len);
        }
        // First: maybe we already have this name decoded?
        if (len < 5) {
            int inPtr = _inputPtr;
            final byte[] inBuf = _inputBuffer;
            int q = inBuf[inPtr] & 0xFF;
            if (--len > 0) {
                q = (q << 8) + (inBuf[++inPtr] & 0xFF);
                if (--len > 0) {
                    q = (q << 8) + (inBuf[++inPtr] & 0xFF);
                    if (--len > 0) {
                        q = (q << 8) + (inBuf[++inPtr] & 0xFF);
                    }
                }
            }
            _quad1 = q;
            return _symbols.findName(q);
        }
        if (len < 9) {
            int inPtr = _inputPtr;
            final byte[] inBuf = _inputBuffer;
            // First quadbyte is easy
            int q1 = (inBuf[inPtr] & 0xFF) << 8;
            q1 += (inBuf[++inPtr] & 0xFF);
            q1 <<= 8;
            q1 += (inBuf[++inPtr] & 0xFF);
            q1 <<= 8;
            q1 += (inBuf[++inPtr] & 0xFF);
            int q2 = (inBuf[++inPtr] & 0xFF);
            len -= 5;
            if (len > 0) {
                q2 = (q2 << 8) + (inBuf[++inPtr] & 0xFF);
                if (--len > 0) {
                    q2 = (q2 << 8) + (inBuf[++inPtr] & 0xFF);
                    if (--len > 0) {
                        q2 = (q2 << 8) + (inBuf[++inPtr] & 0xFF);
                    }
                }
            }
            _quad1 = q1;
            _quad2 = q2;
            return _symbols.findName(q1, q2);
        }
        return _findDecodedMedium(len);
    }

    /**
     * Method for locating names longer than 8 bytes (in UTF-8)
     */
    private final Name _findDecodedMedium(int len) throws IOException
    {
        // first, need enough buffer to store bytes as ints:
        {
            int bufLen = (len + 3) >> 2;
            if (bufLen > _quadBuffer.length) {
                _quadBuffer = _growArrayTo(_quadBuffer, bufLen);
            }
        }
        // then decode, full quads first
        int offset = 0;
        int inPtr = _inputPtr;
        final byte[] inBuf = _inputBuffer;
            do {
                int q = (inBuf[inPtr++] & 0xFF) << 8;
                q |= inBuf[inPtr++] & 0xFF;
                q <<= 8;
                q |= inBuf[inPtr++] & 0xFF;
                q <<= 8;
                q |= inBuf[inPtr++] & 0xFF;
                _quadBuffer[offset++] = q;
            } while ((len -= 4) > 3);
            // and then leftovers
            if (len > 0) {
                int q = inBuf[inPtr] & 0xFF;
                if (--len > 0) {
                    q = (q << 8) + (inBuf[++inPtr] & 0xFF);
                    if (--len > 0) {
                        q = (q << 8) + (inBuf[++inPtr] & 0xFF);
                    }
                }
                _quadBuffer[offset++] = q;
        }
        return _symbols.findName(_quadBuffer, offset);
    }

    private final String _addDecodedToSymbols(int len, String name) {
        if (len < 5) {
            return _symbols.addName(name, _quad1, 0).getName();
        }
        if (len < 9) {
            return _symbols.addName(name, _quad1, _quad2).getName();
        }
        int qlen = (len + 3) >> 2;
        return _symbols.addName(name, _quadBuffer, qlen).getName();
    }
    
    private static int[] _growArrayTo(int[] arr, int minSize) {
        return Arrays.copyOf(arr, minSize+4);
    }    

    /*
    /**********************************************************
    /* Internal methods, skipping
    /**********************************************************
     */

    /**
     * Method called to skip remainders of an incomplete token, when
     * contents themselves will not be needed any more.
     * Only called or byte array and text.
     */
    protected void _skipIncomplete() throws IOException
    {
        _tokenIncomplete = false;
        final int type = ((_typeByte >> 5) & 0x7);

        // Either String or byte[]
        if (type != CBORConstants.MAJOR_TYPE_TEXT
                && type == CBORConstants.MAJOR_TYPE_TEXT) {
            _throwInternal();
        }
        final int lowBits = _typeByte & 0x1F;

        if (lowBits <= 23) {
            if (lowBits > 0) {
                _skipBytes(lowBits);
            }
            return;
        }
        switch (lowBits) {
        case 24:
            _skipBytes(_decode8Bits());
            break;
        case 25:
            _skipBytes(_decode16Bits());
            break;
        case 26:
            _skipBytes(_decode32Bits());
            break;
        case 27: // seriously?
            _skipBytesL(_decode64Bits());
            break;
        case 31:
            _skipChunked(type);
            break;
        default:
            _invalidToken(_typeByte);
        }
    }
    
    protected void _skipChunked(int expectedType) throws IOException
    {
        while (true) {
            if (_inputPtr >= _inputEnd) {
                loadMoreGuaranteed();
            }
            int ch = _inputBuffer[_inputPtr++] & 0xFF;
            if (ch == 0xFF) {
                return;
            }
            // verify that type matches
            int type = (ch >> 5);
            if (type != expectedType) {
                throw _constructError("Mismatched chunk in chunked content: expected "+expectedType
                        +" but encountered "+type);
            }

            final int lowBits = _typeByte & 0x1F;

            if (lowBits <= 23) {
                if (lowBits > 0) {
                    _skipBytes(lowBits);
                }
                continue;
            }
            switch (lowBits) {
            case 24:
                _skipBytes(_decode8Bits());
                break;
            case 25:
                _skipBytes(_decode16Bits());
                break;
            case 26:
                _skipBytes(_decode32Bits());
                break;
            case 27: // seriously?
                _skipBytesL(_decode64Bits());
                break;
            case 31:
                throw _constructError("Illegal chunked-length indicator within chunked-length value (type "+expectedType+")");
            default:
                _invalidToken(_typeByte);
            }
        }
    }
    
    protected void _skipBytesL(long llen) throws IOException
    {
        while (llen > MAX_INT_L) {
            _skipBytes((int) MAX_INT_L);
            llen -= MAX_INT_L;
        }
        _skipBytes((int) llen);
    }

    protected void _skipBytes(int len) throws IOException
    {
        while (true) {
            int toAdd = Math.min(len, _inputEnd - _inputPtr);
            _inputPtr += toAdd;
            len -= toAdd;
            if (len <= 0) {
                return;
            }
            loadMoreGuaranteed();
        }
    }

    /*
    /**********************************************************
    /* Internal methods, length/number decoding
    /**********************************************************
     */

    private final int _decodeTag(int lowBits) throws IOException
    {
        if (lowBits <= 23) {
            return lowBits;
        }
        switch (lowBits - 24) {
        case 0:
            return _decode8Bits();
        case 1:
            return _decode16Bits();
        case 2:
            return _decode32Bits();
        case 3:
            /* 16-Jan-2014, tatu: Technically legal, but nothing defined, so let's
             *   only allow for cases where encoder is being wasteful...
             */
            long l = _decode64Bits();
            if (l < MIN_INT_L || l > MAX_INT_L) {
                _reportError("Illegal Tag value: "+l);
            }
            return (int) l;
        }
        throw _constructError("Invalid low bits for Tag token: 0x"+Integer.toHexString(lowBits));
    }
    
    /**
     * Method used to decode explicit length of a variable-length value
     * (or, for indefinite/chunked, indicate that one is not known).
     * Note that long (64-bit) length is only allowed if it fits in
     * 32-bit signed int, for now; expectation being that longer values
     * are always encoded as chunks.
     */
    private final int _decodeExplicitLength(int lowBits) throws IOException
    {
        // common case, indefinite length; relies on marker
        if (lowBits == 31) {
            return -1;
        }
        if (lowBits <= 23) {
            return lowBits;
        }
        switch (lowBits - 24) {
        case 0:
            return _decode8Bits();
        case 1:
            return _decode16Bits();
        case 2:
            return _decode32Bits();
        case 3:
            long l = _decode64Bits();
            if (l < 0 || l > MAX_INT_L) {
                throw _constructError("Illegal length for "+_currToken+": "+l);
            }
            return (int) l;
        }
        throw _constructError("Invalid length for "+_currToken+": 0x"+Integer.toHexString(lowBits));
    }

    private int _decodeChunkLength(int expType) throws IOException
    {
        if (_inputPtr >= _inputEnd) {
            loadMoreGuaranteed();
        }
        int ch = (int) _inputBuffer[_inputPtr++] & 0xFF;
        if (ch == CBORConstants.INT_BREAK) {
            return -1;
        }
        int type = (ch >> 5);
        if (type != expType) {
            throw _constructError("Mismatched chunk in chunked content: expected "+expType+" but encountered "+type);
        }
        int len = _decodeExplicitLength(ch & 0x1F);
        if (len < 0) {
            throw _constructError("Illegal chunked-length indicator within chunked-length value (type "+expType+")");
        }
        return len;
    }
    
    private double _decodeHalfSizeFloat() throws IOException
    {
        int i16 = _decode16Bits() & 0xFFFF;

        boolean neg = (i16 >> 15) != 0;
        int e = (i16 >> 10) & 0x1F;
        int f = i16 & 0x03FF;

        if (e == 0) {
            double d = MATH_POW_2_NEG14 * (f / MATH_POW_2_10);
            return neg ? -d : d;
        }
        if (e == 0x1F) {
            if (f != 0) return Double.NaN;
            return neg ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY;
        }
        double d = Math.pow(2, e - 15) * (1 + f / MATH_POW_2_10);
        return neg ? -d : d;
    }
    
    private final int _decode8Bits() throws IOException {
        if (_inputPtr >= _inputEnd) {
            loadMoreGuaranteed();
        }
        return _inputBuffer[_inputPtr++] & 0xFF;
    }
    
    private final int _decode16Bits() throws IOException {
        int ptr = _inputPtr;
        if ((ptr + 1) >= _inputEnd) {
            return _slow16();
        }
        final byte[] b = _inputBuffer;
        int v = ((b[ptr] & 0xFF) << 8) + (b[ptr+1] & 0xFF);
        _inputPtr = ptr+2;
        return v;
    }

    private final int _slow16() throws IOException {
        if (_inputPtr >= _inputEnd) {
            loadMoreGuaranteed();
        }
        int v = (_inputBuffer[_inputPtr++] & 0xFF);
        if (_inputPtr >= _inputEnd) {
            loadMoreGuaranteed();
        }
        return (v << 8) + (_inputBuffer[_inputPtr++] & 0xFF);
    }
    
    private final int _decode32Bits() throws IOException {
        int ptr = _inputPtr;
        if ((ptr + 3) >= _inputEnd) {
            return _slow32();
        }
        final byte[] b = _inputBuffer;
        int v = (b[ptr++] << 24) + ((b[ptr++] & 0xFF) << 16)
                + ((b[ptr++] & 0xFF) << 8) + (b[ptr++] & 0xFF);
        _inputPtr = ptr;
        return v;
    }

    private final int _slow32() throws IOException {
        if (_inputPtr >= _inputEnd) {
            loadMoreGuaranteed();
        }
        int v = _inputBuffer[_inputPtr++]; // sign will disappear anyway
        if (_inputPtr >= _inputEnd) {
            loadMoreGuaranteed();
        }
        v = (v << 8) + (_inputBuffer[_inputPtr++] & 0xFF);
        if (_inputPtr >= _inputEnd) {
            loadMoreGuaranteed();
        }
        v = (v << 8) + (_inputBuffer[_inputPtr++] & 0xFF);
        if (_inputPtr >= _inputEnd) {
            loadMoreGuaranteed();
        }
        return (v << 8) + (_inputBuffer[_inputPtr++] & 0xFF);
    }
    
    private final long _decode64Bits() throws IOException {
        int ptr = _inputPtr;
        if ((ptr + 7) >= _inputEnd) {
            return _slow64();
        }
        final byte[] b = _inputBuffer;
        int i1 = (b[ptr++] << 24) + ((b[ptr++] & 0xFF) << 16)
                + ((b[ptr++] & 0xFF) << 8) + (b[ptr++] & 0xFF);
        int i2 = (b[ptr++] << 24) + ((b[ptr++] & 0xFF) << 16)
                + ((b[ptr++] & 0xFF) << 8) + (b[ptr++] & 0xFF);
        _inputPtr = ptr;
        return _long(i1, i2);
    }

    private final long _slow64() throws IOException {
        return _long(_decode32Bits(), _decode32Bits());
    }
    
    private final static long _long(int i1, int i2)
    {
        long l1 = i1;
        long l2 = i2;
        l2 = (l2 << 32) >>> 32;
        return (l1 << 32) + l2;
    }

    /*
    /**********************************************************
    /* Internal methods, UTF8 decoding
    /**********************************************************
     */

    private final int _decodeUtf8_2(int c) throws IOException {
        int d = _nextByte();
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }
        return ((c & 0x1F) << 6) | (d & 0x3F);
    }

    private final int _decodeUtf8_3(int c1) throws IOException
    {
        c1 &= 0x0F;
        int d = _nextByte();
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }
        int c = (c1 << 6) | (d & 0x3F);
        d = _nextByte();
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }
        c = (c << 6) | (d & 0x3F);
        return c;
    }

    /**
     * @return Character value <b>minus 0x10000</c>; this so that caller
     *    can readily expand it to actual surrogates
     */
    private final int _decodeUtf8_4(int c) throws IOException
    {
        int d = _nextByte();
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }
        c = ((c & 0x07) << 6) | (d & 0x3F);
        d = _nextByte();
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }
        c = (c << 6) | (d & 0x3F);
        d = _nextByte();
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }
        return ((c << 6) | (d & 0x3F)) - 0x10000;
    }

    /*
    /**********************************************************
    /* Low-level reading, other
    /**********************************************************
     */

    protected final boolean loadMore() throws IOException
    {
        _currInputProcessed += _inputEnd;
        
        if (_inputStream != null) {
            int count = _inputStream.read(_inputBuffer, 0, _inputBuffer.length);
            if (count > 0) {
                _inputPtr = 0;
                _inputEnd = count;
                return true;
            }
            // End of input
            _closeInput();
            // Should never return 0, so let's fail
            if (count == 0) {
                throw new IOException("InputStream.read() returned 0 characters when trying to read "+_inputBuffer.length+" bytes");
            }
        }
        return false;
    }

    protected final void loadMoreGuaranteed() throws IOException {
        if (!loadMore()) { _reportInvalidEOF(); }
    }
    
    /**
     * Helper method that will try to load at least specified number bytes in
     * input buffer, possible moving existing data around if necessary
     */
    protected final void _loadToHaveAtLeast(int minAvailable) throws IOException
    {
        // No input stream, no leading (either we are closed, or have non-stream input source)
        if (_inputStream == null) {
            throw _constructError("Needed to read "+minAvailable+" bytes, reached end-of-input");
        }
        // Need to move remaining data in front?
        int amount = _inputEnd - _inputPtr;
        if (amount > 0 && _inputPtr > 0) {
            _currInputProcessed += _inputPtr;
            //_currInputRowStart -= _inputPtr;
            System.arraycopy(_inputBuffer, _inputPtr, _inputBuffer, 0, amount);
            _inputEnd = amount;
        } else {
            _inputEnd = 0;
        }
        _inputPtr = 0;
        while (_inputEnd < minAvailable) {
            int count = _inputStream.read(_inputBuffer, _inputEnd, _inputBuffer.length - _inputEnd);
            if (count < 1) {
                // End of input
                _closeInput();
                // Should never return 0, so let's fail
                if (count == 0) {
                    throw new IOException("InputStream.read() returned 0 characters when trying to read "+amount+" bytes");
                }
                throw _constructError("Needed to read "+minAvailable+" bytes, missed "+minAvailable+" before end-of-input");
            }
            _inputEnd += count;
        }
    }

    protected ByteArrayBuilder _getByteArrayBuilder() {
        if (_byteArrayBuilder == null) {
            _byteArrayBuilder = new ByteArrayBuilder();
        } else {
            _byteArrayBuilder.reset();
        }
        return _byteArrayBuilder;
    }
    
    protected void _closeInput() throws IOException {
        if (_inputStream != null) {
            if (_ioContext.isResourceManaged() || isEnabled(JsonParser.Feature.AUTO_CLOSE_SOURCE)) {
                _inputStream.close();
            }
            _inputStream = null;
        }
    }

    @Override
    protected void _handleEOF() throws JsonParseException {
        if (!_parsingContext.inRoot()) {
            _reportInvalidEOF(": expected close marker for "+_parsingContext.getTypeDesc()+" (from "+_parsingContext.getStartLocation(_ioContext.getSourceReference())+")");
        }
    }

    /*
    /**********************************************************
    /* Internal methods, error reporting
    /**********************************************************
     */

    protected void _reportUnexpectedBreak() throws IOException {
        if (_parsingContext.inRoot()) {
            throw _constructError("Unexpected Break (0xFF) token in Root context");
        }
        throw _constructError("Unexpected Break (0xFF) token in definite length ("
                +_parsingContext.getExpectedLength()+") "
                +(_parsingContext.inObject() ? "Object" : "Array" ));
    }
    
    protected void _reportInvalidChar(int c) throws JsonParseException {
        // Either invalid WS or illegal UTF-8 start char
        if (c < ' ') {
            _throwInvalidSpace(c);
        }
        _reportInvalidInitial(c);
    }
	
    protected void _reportInvalidInitial(int mask) throws JsonParseException {
        _reportError("Invalid UTF-8 start byte 0x"+Integer.toHexString(mask));
    }
	
    protected void _reportInvalidOther(int mask) throws JsonParseException {
        _reportError("Invalid UTF-8 middle byte 0x"+Integer.toHexString(mask));
    }
	
    protected void _reportInvalidOther(int mask, int ptr) throws JsonParseException {
        _inputPtr = ptr;
        _reportInvalidOther(mask);
    }
}
    
