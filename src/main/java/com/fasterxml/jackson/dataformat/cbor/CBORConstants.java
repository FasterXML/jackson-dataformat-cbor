package com.fasterxml.jackson.dataformat.cbor;

/**
 * Constants used by {@link CBORGenerator} and {@link CBORParser}
 * 
 * @author Tatu Saloranta
 */
public final class CBORConstants
{
    /*
    /**********************************************************
    /* Major type constants
    /**********************************************************
     */

    public final static int MAJOR_TYPE_INT_POS = 0;
    public final static int MAJOR_TYPE_INT_NEG = 1;
    public final static int MAJOR_TYPE_BYTES = 2;
    public final static int MAJOR_TYPE_TEXT = 3;
    public final static int MAJOR_TYPE_ARRAY = 4;
    public final static int MAJOR_TYPE_OBJECT = 5;
    public final static int MAJOR_TYPE_TAG = 6;
    public final static int MAJOR_TYPE_MISC = 7;

    public final static int PREFIX_TYPE_INT_POS = (MAJOR_TYPE_INT_POS << 5);
    public final static int PREFIX_TYPE_INT_NEG = (MAJOR_TYPE_INT_NEG << 5);
    public final static int PREFIX_TYPE_BYTES = (MAJOR_TYPE_BYTES << 5);
    public final static int PREFIX_TYPE_TEXT = (MAJOR_TYPE_TEXT << 5);
    public final static int PREFIX_TYPE_ARRAY = (MAJOR_TYPE_ARRAY << 5);
    public final static int PREFIX_TYPE_OBJECT = (MAJOR_TYPE_OBJECT << 5);
    public final static int PREFIX_TYPE_TAG = (MAJOR_TYPE_TAG << 5);
    public final static int PREFIX_TYPE_MISC = (MAJOR_TYPE_MISC << 5);

    public final static int SUFFIX_INDEFINITE = 0x1F;
    
    public final static byte BYTE_ARRAY_INDEFINITE = (byte) (PREFIX_TYPE_ARRAY + SUFFIX_INDEFINITE);
    public final static byte BYTE_OBJECT_INDEFINITE = (byte) (PREFIX_TYPE_OBJECT + SUFFIX_INDEFINITE);

    public final static byte BYTE_FALSE = (byte) (PREFIX_TYPE_MISC + 20);
    public final static byte BYTE_TRUE = (byte) (PREFIX_TYPE_MISC + 21);
    public final static byte BYTE_NULL = (byte) (PREFIX_TYPE_MISC + 22);

    public final static byte BYTE_FLOAT32 = (byte) (PREFIX_TYPE_MISC + 26);
    public final static byte BYTE_FLOAT64 = (byte) (PREFIX_TYPE_MISC + 27);
    
    public final static byte BYTE_BREAK = (byte) 0xFF;
    
    /*
    /**********************************************************
    /* Thresholds
    /**********************************************************
     */

    /**
     * Encoding has special "short" forms for value Strings that can
     * be represented by 64 bytes of UTF-8 or less.
     */
    public final static int MAX_SHORT_VALUE_STRING_BYTES = 64;

    /**
     * Encoding has special "short" forms for field names that can
     * be represented by 64 bytes of UTF-8 or less.
     */
    public final static int MAX_SHORT_NAME_ASCII_BYTES = 64;

    /**
     * Maximum byte length for short non-ASCII names is slightly
     * less due to having to reserve bytes 0xF8 and above (but
     * we get one more as values 0 and 1 are not valid)
     */
    public final static int MAX_SHORT_NAME_UNICODE_BYTES = 56;
    
    /**
     * Longest back reference we use for field names is 10 bits; no point
     * in keeping much more around
     */
    public final static int MAX_SHARED_NAMES = 1024;

    /**
     * Longest back reference we use for short shared String values is 10 bits,
     * so up to (1 << 10) values to keep track of.
     */
    public final static int MAX_SHARED_STRING_VALUES = 1024;

    /**
     * Also: whereas we can refer to names of any length, we will only consider
     * text values that are considered "tiny" or "short" (ones encoded with
     * length prefix); this value thereby has to be maximum length of Strings
     * that can be encoded as such.
     */
    public final static int MAX_SHARED_STRING_LENGTH_BYTES = 65;
    
    /**
     * And to make encoding logic tight and simple, we can always
     * require that output buffer has this amount of space
     * available before encoding possibly short String (3 bytes since
     * longest UTF-8 encoded Java char is 3 bytes).
     * Two extra bytes need to be reserved as well; first for token indicator,
     * and second for terminating null byte (in case it's not a short String after all)
     */
    public final static int MIN_BUFFER_FOR_POSSIBLE_SHORT_STRING = 1 + (3 * 65);

    /*
    /**********************************************************
    /* Byte markers
    /**********************************************************
     */
    
    /**
     * We need a byte marker to denote end of variable-length Strings. Although
     * null byte is commonly used, let's try to avoid using it since it can't
     * be embedded in Web Sockets content (similarly, 0xFF can't). There are
     * multiple candidates for bytes UTF-8 can not have; 0xFC is chosen to
     * allow reasonable ordering (highest values meaning most significant
     * framing function; 0xFF being end-of-content and so on)
     */
    public final static int INT_MARKER_END_OF_STRING = 0xFC;

    public final static byte BYTE_MARKER_END_OF_STRING = (byte) INT_MARKER_END_OF_STRING;
    
    /**
     * In addition we can use a marker to allow simple framing; splitting
     * of physical data (like file) into distinct logical sections like
     * JSON documents. 0xFF makes sense here since it is also used
     * as end marker for Web Sockets.
     */
    public final static byte BYTE_MARKER_END_OF_CONTENT = (byte) 0xFF;

    /*
    /**********************************************************
    /* Type prefixes: 3 MSB of token byte
    /**********************************************************
     */

    public final static int TOKEN_PREFIX_INTEGER = 0x24;

    public final static int TOKEN_PREFIX_FP = 0x28;
    
    // Shared strings are back references for last 63 short (< 64 byte) string values
    // NOTE: 0x00 is reserved, not used with current version (may be used in future)
    public final static int TOKEN_PREFIX_SHARED_STRING_SHORT = 0x00;
    // literals are put between 0x20 and 0x3F to reserve markers (smiley), along with ints/doubles
    //public final static int TOKEN_PREFIX_MISC_NUMBERS = 0x20;

    public final static int TOKEN_PREFIX_SHARED_STRING_LONG = 0xEC;
    
    public final static int TOKEN_PREFIX_TINY_ASCII = 0x40;
    public final static int TOKEN_PREFIX_SMALL_ASCII = 0x60;
    public final static int TOKEN_PREFIX_TINY_UNICODE = 0x80;
    public final static int TOKEN_PREFIX_SHORT_UNICODE = 0xA0;

    // Small ints are 4-bit (-16 to +15) integer constants
    public final static int TOKEN_PREFIX_SMALL_INT = 0xC0;

    // And misc types have empty at the end too, to reserve 0xF8 - 0xFF
    public final static int TOKEN_PREFIX_MISC_OTHER = 0xE0;

    /*
    /**********************************************************
    /* Token literals, normal mode
    /**********************************************************
     */
    
    // First, non-structured literals

    public final static byte TOKEN_LITERAL_EMPTY_STRING = 0x20;

    // And then structured literals
    
    public final static byte TOKEN_LITERAL_START_ARRAY = (byte) 0xF8;
    public final static byte TOKEN_LITERAL_END_ARRAY = (byte) 0xF9;
    public final static byte TOKEN_LITERAL_START_OBJECT = (byte) 0xFA;
    public final static byte TOKEN_LITERAL_END_OBJECT = (byte) 0xFB;

    /*
    /**********************************************************
    /* Subtype constants for misc text/binary types
    /**********************************************************
     */

    /**
     * Type (for misc, other) used for
     * variable length UTF-8 encoded text, when it is known to only contain ASCII chars.
     * Note: 2 LSB are reserved for future use; must be zeroes for now
     */
    public final static byte TOKEN_MISC_LONG_TEXT_ASCII = (byte) 0xE0;

    /**
     * Type (for misc, other) used
     * for variable length UTF-8 encoded text, when it is NOT known to only contain ASCII chars
     * (which means it MAY have multi-byte characters)
     * Note: 2 LSB are reserved for future use; must be zeroes for now
     */
    public final static byte TOKEN_MISC_LONG_TEXT_UNICODE = (byte) 0xE4;
    
    /**
     * Type (for misc, other) used
     * for "safe" (encoded by only using 7 LSB, giving 8/7 expansion ratio).
     * This is usually done to ensure that certain bytes are never included
     * in encoded data (like 0xFF)
     * Note: 2 LSB are reserved for future use; must be zeroes for now
     */
    public final static byte TOKEN_MISC_BINARY_7BIT = (byte) 0xE8;

    /*
    /**********************************************************
    /* Modifiers for numeric entries
    /**********************************************************
     */

    /**
     * Numeric subtype (2 LSB) for {@link #TOKEN_MISC_INTEGER},
     * indicating 32-bit integer (int)
     */
    public final static int TOKEN_MISC_INTEGER_32 = 0x00;

    /**
     * Numeric subtype (2 LSB) for {@link #TOKEN_MISC_INTEGER},
     * indicating 32-bit integer (long)
     */
    public final static int TOKEN_MISC_INTEGER_64 = 0x01;

    /**
     * Numeric subtype (2 LSB) for {@link #TOKEN_MISC_INTEGER},
     * indicating {@link java.math.BigInteger} type.
     */
    public final static int TOKEN_MISC_INTEGER_BIG = 0x02;

    // Note: type 3 (0xF3) reserved for future use
    
    /**
     * Numeric subtype (2 LSB) for {@link #TOKEN_MISC_FP},
     * indicating 32-bit IEEE single precision floating point number.
     */
    public final static int TOKEN_MISC_FLOAT_32 = 0x00;

    /**
     * Numeric subtype (2 LSB) for {@link #TOKEN_MISC_FP},
     * indicating 64-bit IEEE double precision floating point number.
     */
    public final static int TOKEN_MISC_FLOAT_64 = 0x01;

    /**
     * Numeric subtype (2 LSB) for {@link #TOKEN_MISC_FP},
     * indicating {@link java.math.BigDecimal} type.
     */
    public final static int TOKEN_MISC_FLOAT_BIG = 0x02;

    // Note: type 3 (0xF7) reserved for future use
    
    /*
    /**********************************************************
    /* Token types for keys
    /**********************************************************
     */

    /**
     * Let's use same code for empty key as for empty String value
     */
    public final static byte TOKEN_KEY_EMPTY_STRING = 0x20;
    
    public final static byte TOKEN_KEY_LONG_STRING = 0x34;
    
    public final static int TOKEN_PREFIX_KEY_ASCII = 0x80;

    public final static int TOKEN_PREFIX_KEY_UNICODE = 0xC0;

    /*
    /**********************************************************
    /* Basic UTF-8 decode/encode table
    /**********************************************************
     */
    
    /**
     * Additionally we can combine UTF-8 decoding info into similar
     * data table.
     * Values indicate "byte length - 1"; meaning -1 is used for
     * invalid bytes, 0 for single-byte codes, 1 for 2-byte codes
     * and 2 for 3-byte codes.
     */
    public final static int[] sUtf8UnitLengths;
    static {
        int[] table = new int[256];
        for (int c = 128; c < 256; ++c) {
            int code;

            // We'll add number of bytes needed for decoding
            if ((c & 0xE0) == 0xC0) { // 2 bytes (0x0080 - 0x07FF)
                code = 1;
            } else if ((c & 0xF0) == 0xE0) { // 3 bytes (0x0800 - 0xFFFF)
                code = 2;
            } else if ((c & 0xF8) == 0xF0) {
                // 4 bytes; double-char with surrogates and all...
                code = 3;
            } else {
                // And -1 seems like a good "universal" error marker...
                code = -1;
            }
            table[c] = code;
        }
        sUtf8UnitLengths = table;
    }
}
