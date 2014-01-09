package com.fasterxml.jackson.dataformat.cbor;

import java.io.*;
import java.net.URL;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.format.InputAccessor;
import com.fasterxml.jackson.core.format.MatchStrength;
import com.fasterxml.jackson.core.io.IOContext;

/**
 * Factory used for constructing {@link CBORParser} and {@link CBORGenerator}
 * instances; both of which handle
 * <a href="https://www.rfc-editor.org/info/rfc7049">CBOR</a>
 * encoded data.
 *<p>
 * Extends {@link JsonFactory} mostly so that users can actually use it in place
 * of regular non-CBOR factory instances.
 *<p>
 * Note on using non-byte-based sources/targets (char based, like
 * {@link java.io.Reader} and {@link java.io.Writer}): these can not be
 * used for CBOR documents; attempt will throw exception.
 * 
 * @author Tatu Saloranta
 */
public class CBORFactory extends JsonFactory
{
    private static final long serialVersionUID = -4517030652345943412L;
//    private static final long serialVersionUID = -1696783009312472365L;

    /*
    /**********************************************************
    /* Constants
    /**********************************************************
     */

    /**
     * Name used to identify CBOR format.
     * (and returned by {@link #getFormatName()}
     */
    public final static String FORMAT_NAME = "CBOR";
    
    /**
     * Bitfield (set of flags) of all parser features that are enabled
     * by default.
     */
    final static int DEFAULT_SMILE_PARSER_FEATURE_FLAGS = CBORParser.Feature.collectDefaults();

    /**
     * Bitfield (set of flags) of all generator features that are enabled
     * by default.
     */
    final static int DEFAULT_SMILE_GENERATOR_FEATURE_FLAGS = CBORGenerator.Feature.collectDefaults();

    /*
    /**********************************************************
    /* Configuration
    /**********************************************************
     */

    /**
     * Whether non-supported methods (ones trying to output using
     * char-based targets like {@link java.io.Writer}, for example)
     * should be delegated to regular Jackson JSON processing
     * (if set to true); or throw {@link UnsupportedOperationException}
     * (if set to false)
     */
    protected boolean _cfgDelegateToTextual;

    protected int _smileParserFeatures;
    protected int _smileGeneratorFeatures;

    /*
    /**********************************************************
    /* Factory construction, configuration
    /**********************************************************
     */

    /**
     * Default constructor used to create factory instances.
     * Creation of a factory instance is a light-weight operation,
     * but it is still a good idea to reuse limited number of
     * factory instances (and quite often just a single instance):
     * factories are used as context for storing some reused
     * processing objects (such as symbol tables parsers use)
     * and this reuse only works within context of a single
     * factory instance.
     */
    public CBORFactory() { this(null); }

    public CBORFactory(ObjectCodec oc) {
        super(oc);
        _smileParserFeatures = DEFAULT_SMILE_PARSER_FEATURE_FLAGS;
        _smileGeneratorFeatures = DEFAULT_SMILE_GENERATOR_FEATURE_FLAGS;
    }

    /**
     * Note: REQUIRES 2.2.1 -- unfortunate intra-patch dep but seems
     * preferable to just leaving bug be as is
     * 
     * @since 2.2.1
     */
    public CBORFactory(CBORFactory src, ObjectCodec oc)
    {
        super(src, oc);
        _cfgDelegateToTextual = src._cfgDelegateToTextual;
        _smileParserFeatures = src._smileParserFeatures;
        _smileGeneratorFeatures = src._smileGeneratorFeatures;
    }

    // @since 2.1
    @Override
    public CBORFactory copy()
    {
        _checkInvalidCopy(CBORFactory.class);
        // note: as with base class, must NOT copy mapper reference
        return new CBORFactory(this, null);
    }
    
    public void delegateToTextual(boolean state) {
        _cfgDelegateToTextual = state;
    }

    /*
    /**********************************************************
    /* Serializable overrides
    /**********************************************************
     */

    /**
     * Method that we need to override to actually make restoration go
     * through constructors etc.
     * Also: must be overridden by sub-classes as well.
     */
    @Override
    protected Object readResolve() {
        return new CBORFactory(this, _objectCodec);
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
    /* Format detection functionality
    /**********************************************************
     */
    
    @Override
    public String getFormatName() {
        return FORMAT_NAME;
    }

    // Defaults work fine for this:
    // public boolean canUseSchema(FormatSchema schema) { }
    
    /**
     * Sub-classes need to override this method (as of 1.8)
     */
    @Override
    public MatchStrength hasFormat(InputAccessor acc) throws IOException {
        return CBORParserBootstrapper.hasCBORFormat(acc);
    }

    /*
    /**********************************************************
    /* Capability introspection
    /**********************************************************
     */
    
    @Override
    public boolean canHandleBinaryNatively() {
        return true;
    }

    /*
    /**********************************************************
    /* Configuration, parser settings
    /**********************************************************
     */

    /**
     * Method for enabling or disabling specified parser feature
     * (check {@link CBORParser.Feature} for list of features)
     */
    public final CBORFactory configure(CBORParser.Feature f, boolean state)
    {
        if (state) {
            enable(f);
        } else {
            disable(f);
        }
        return this;
    }

    /**
     * Method for enabling specified parser feature
     * (check {@link CBORParser.Feature} for list of features)
     */
    public CBORFactory enable(CBORParser.Feature f) {
        _smileParserFeatures |= f.getMask();
        return this;
    }

    /**
     * Method for disabling specified parser features
     * (check {@link CBORParser.Feature} for list of features)
     */
    public CBORFactory disable(CBORParser.Feature f) {
        _smileParserFeatures &= ~f.getMask();
        return this;
    }

    /**
     * Checked whether specified parser feature is enabled.
     */
    public final boolean isEnabled(CBORParser.Feature f) {
        return (_smileParserFeatures & f.getMask()) != 0;
    }

    /*
    /**********************************************************
    /* Configuration, generator settings
    /**********************************************************
     */

    /**
     * Method for enabling or disabling specified generator feature
     * (check {@link CBORGenerator.Feature} for list of features)
     *
     * @since 1.2
     */
    public final CBORFactory configure(CBORGenerator.Feature f, boolean state) {
        if (state) {
            enable(f);
        } else {
            disable(f);
        }
        return this;
    }


    /**
     * Method for enabling specified generator features
     * (check {@link CBORGenerator.Feature} for list of features)
     */
    public CBORFactory enable(CBORGenerator.Feature f) {
        _smileGeneratorFeatures |= f.getMask();
        return this;
    }

    /**
     * Method for disabling specified generator feature
     * (check {@link CBORGenerator.Feature} for list of features)
     */
    public CBORFactory disable(CBORGenerator.Feature f) {
        _smileGeneratorFeatures &= ~f.getMask();
        return this;
    }

    /**
     * Check whether specified generator feature is enabled.
     */
    public final boolean isEnabled(CBORGenerator.Feature f) {
        return (_smileGeneratorFeatures & f.getMask()) != 0;
    }
    
    /*
    /**********************************************************
    /* Overridden parser factory methods, new (2.1)
    /**********************************************************
     */

    @SuppressWarnings("resource")
    @Override
    public CBORParser createParser(File f)
        throws IOException, JsonParseException
    {
        return _createParser(new FileInputStream(f), _createContext(f, true));
    }

    @Override
    public CBORParser createParser(URL url)
        throws IOException, JsonParseException
    {
        return _createParser(_optimizedStreamFromURL(url), _createContext(url, true));
    }

    @Override
    public CBORParser createParser(InputStream in)
        throws IOException, JsonParseException
    {
        return _createParser(in, _createContext(in, false));
    }

    //public JsonParser createJsonParser(Reader r)
    
    @Override
    public CBORParser createParser(byte[] data)
        throws IOException, JsonParseException
    {
        IOContext ctxt = _createContext(data, true);
        return _createParser(data, 0, data.length, ctxt);
    }
    
    @Override
    public CBORParser createParser(byte[] data, int offset, int len)
        throws IOException, JsonParseException
    {
        return _createParser(data, offset, len, _createContext(data, true));
    }
   
    /*
    /**********************************************************
    /* Overridden parser factory methods, old (pre-2.1)
    /**********************************************************
     */
    
    /**
     * @deprecated Since 2.1 Use {@link #createParser(File)} instead
     * @since 2.1
     */
    @SuppressWarnings("resource")
    @Deprecated
    @Override
    public CBORParser createJsonParser(File f)
        throws IOException, JsonParseException
    {
        return _createParser(new FileInputStream(f), _createContext(f, true));
    }

    /**
     * @deprecated Since 2.1 Use {@link #createParser(URL)} instead
     * @since 2.1
     */
    @Deprecated
    @Override
    public CBORParser createJsonParser(URL url)
        throws IOException, JsonParseException
    {
        return _createParser(_optimizedStreamFromURL(url), _createContext(url, true));
    }

    /**
     * @deprecated Since 2.1 Use {@link #createParser(InputStream)} instead
     * @since 2.1
     */
    @Deprecated
    @Override
    public CBORParser createJsonParser(InputStream in)
        throws IOException, JsonParseException
    {
        return _createParser(in, _createContext(in, false));
    }

    //public JsonParser createJsonParser(Reader r)
    
    /**
     * @deprecated Since 2.1 Use {@link #createParser(byte[])} instead
     * @since 2.1
     */
    @Deprecated
    @Override
    public CBORParser createJsonParser(byte[] data)
        throws IOException, JsonParseException
    {
        IOContext ctxt = _createContext(data, true);
        return _createParser(data, 0, data.length, ctxt);
    }
    
    /**
     * @deprecated Since 2.1 Use {@link #createParser(byte[],int,int)} instead
     * @since 2.1
     */
    @Deprecated
    @Override
    public CBORParser createJsonParser(byte[] data, int offset, int len)
        throws IOException, JsonParseException
    {
        return _createParser(data, offset, len, _createContext(data, true));
    }

    /*
    /**********************************************************
    /* Overridden generator factory methods, new (2.1)
    /**********************************************************
     */

    /**
     * Method for constructing {@link JsonGenerator} for generating
     * CBOR-encoded output.
     *<p>
     * Since CBOR format always uses UTF-8 internally, <code>enc</code>
     * argument is ignored.
     */
    @Override
    public CBORGenerator createGenerator(OutputStream out, JsonEncoding enc)
        throws IOException
    {
        // false -> we won't manage the stream unless explicitly directed to
        return _createGenerator(out, _createContext(out, false));
    }

    /**
     * Method for constructing {@link JsonGenerator} for generating
     * CBOR-encoded output.
     *<p>
     * Since CBOR format always uses UTF-8 internally, no encoding need
     * to be passed to this method.
     */
    @Override
    public CBORGenerator createGenerator(OutputStream out) throws IOException
    {
        // false -> we won't manage the stream unless explicitly directed to
        return _createGenerator(out, _createContext(out, false));
    }
    
    /*
    /**********************************************************
    /* Overridden generator factory methods, old (pre-2.1)
    /**********************************************************
     */
    
    /**
     * @deprecated Since 2.1 Use {@link #createGenerator(OutputStream)} instead
     * @since 2.1
     */
    @Deprecated
    @Override
    public CBORGenerator createJsonGenerator(OutputStream out, JsonEncoding enc)
        throws IOException
    {
        // false -> we won't manage the stream unless explicitly directed to
        return _createGenerator(out, _createContext(out, false));
    }

    /**
     * @deprecated Since 2.1 Use {@link #createGenerator(OutputStream)} instead
     * @since 2.1
     */
    @Deprecated
    @Override
    public CBORGenerator createJsonGenerator(OutputStream out) throws IOException
    {
        // false -> we won't manage the stream unless explicitly directed to
        IOContext ctxt = _createContext(out, false);
        return _createGenerator(out, ctxt);
    }

    @Deprecated
    @Override
    protected CBORGenerator _createUTF8JsonGenerator(OutputStream out, IOContext ctxt)
        throws IOException
    {
        return _createGenerator(out, ctxt);
    }
    
    /*
    /******************************************************
    /* Overridden internal factory methods
    /******************************************************
     */

    //protected IOContext _createContext(Object srcRef, boolean resourceManaged)

    /**
     * Overridable factory method that actually instantiates desired
     * parser.
     */
    @Override
    protected CBORParser _createParser(InputStream in, IOContext ctxt)
        throws IOException, JsonParseException
    {
        return new CBORParserBootstrapper(ctxt, in).constructParser(_parserFeatures,
        		_smileParserFeatures, isEnabled(JsonFactory.Feature.INTERN_FIELD_NAMES),
        		_objectCodec, _rootByteSymbols);
    }

    /**
     * Overridable factory method that actually instantiates desired
     * parser.
     */
    @Override
    protected JsonParser _createParser(Reader r, IOContext ctxt)
        throws IOException, JsonParseException
    {
        if (_cfgDelegateToTextual) {
            return super._createParser(r, ctxt);
        }
        throw new UnsupportedOperationException("Can not create generator for non-byte-based target");
    }

    /**
     * Overridable factory method that actually instantiates desired
     * parser.
     */
    @Override
    protected CBORParser _createParser(byte[] data, int offset, int len, IOContext ctxt)
        throws IOException, JsonParseException
    {
        return new CBORParserBootstrapper(ctxt, data, offset, len).constructParser(
                _parserFeatures, _smileParserFeatures,
                isEnabled(JsonFactory.Feature.INTERN_FIELD_NAMES),
                _objectCodec, _rootByteSymbols);
    }

    /**
     * Overridable factory method that actually instantiates desired
     * generator.
     */
    @Override
    protected JsonGenerator _createGenerator(Writer out, IOContext ctxt)
        throws IOException
    {
        if (_cfgDelegateToTextual) {
            return super._createGenerator(out, ctxt);
        }
        throw new UnsupportedOperationException("Can not create generator for non-byte-based target");
    }

    @Override
    protected JsonGenerator _createUTF8Generator(OutputStream out, IOContext ctxt) throws IOException {
        return _createGenerator(out, ctxt);
    }
    
    //public BufferRecycler _getBufferRecycler()

    @Override
    protected Writer _createWriter(OutputStream out, JsonEncoding enc, IOContext ctxt) throws IOException
    {
        if (_cfgDelegateToTextual) {
            return super._createWriter(out, enc, ctxt);
        }
        throw new UnsupportedOperationException("Can not create generator for non-byte-based target");
    }
    
    /*
    /**********************************************************
    /* Internal methods
    /**********************************************************
     */
    
    protected CBORGenerator _createGenerator(OutputStream out, IOContext ctxt)
        throws IOException
    {
        int feats = _smileGeneratorFeatures;
        /* One sanity check: MUST write header if shared string values setting is enabled,
         * or quoting of binary data disabled.
         * But should we force writing, or throw exception, if settings are in conflict?
         * For now, let's error out...
         */
        CBORGenerator gen = new CBORGenerator(ctxt, _generatorFeatures, feats, _objectCodec, out);

        /*
        if ((feats & CBORGenerator.Feature.WRITE_HEADER.getMask()) != 0) {
            gen.writeHeader();
        } else {
            if ((feats & CBORGenerator.Feature.CHECK_SHARED_STRING_VALUES.getMask()) != 0) {
                throw new JsonGenerationException(
                        "Inconsistent settings: WRITE_HEADER disabled, but CHECK_SHARED_STRING_VALUES enabled; can not construct generator"
                        +" due to possible data loss (either enable WRITE_HEADER, or disable CHECK_SHARED_STRING_VALUES to resolve)");
            }
            if ((feats & CBORGenerator.Feature.ENCODE_BINARY_AS_7BIT.getMask()) == 0) {
        	throw new JsonGenerationException(
        			"Inconsistent settings: WRITE_HEADER disabled, but ENCODE_BINARY_AS_7BIT disabled; can not construct generator"
        			+" due to possible data loss (either enable WRITE_HEADER, or ENCODE_BINARY_AS_7BIT to resolve)");
            }
        }
        */
        return gen;
    }
}
