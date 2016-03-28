package com.fasterxml.jackson.dataformat.cbor.sizer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

import com.fasterxml.jackson.dataformat.cbor.CBORConstants;

public class GeneratorSizerTest extends CBORTestBaseSizer
{    
    /**
     * Tests
     */
    public void testArray_less31elt() throws Exception
    {
    	// Test arrays with less of 31 element
    	ByteArrayOutputStream out = new ByteArrayOutputStream();
    	CBORGeneratorSizer gen = cborGeneratorSizer(out);
    	
    	gen.writeStartArray();
    	gen.writeNumber(1); 
    	gen.writeNumber(2);
    	gen.writeNumber(3);
    	gen.writeEndArray();    	
    	gen.close();
	
        _verifyBytes(out.toByteArray(),
                (byte) (CBORConstants.PREFIX_TYPE_ARRAY + 3),	// # array(3)
                (byte) 0x01, 									// # unsigned(1)
                (byte) 0x02,  									// # unsigned(2)
                (byte) 0x03); 									// # unsigned(3)
    }
    
    public void testArray_more31elt() throws Exception
    {
        // Test arrays with more of 31 elements
    	ByteArrayOutputStream out = new ByteArrayOutputStream();
    	CBORGeneratorSizer gen = cborGeneratorSizer(out);
    	
    	gen.writeStartArray();
    	
    	for(int i=1; i<=32; i++){
    		gen.writeNumber(i % 16);
    	}
    	
    	gen.writeEndArray();    	
    	gen.close();
    	  
        _verifyBytes(out.toByteArray(),
                (byte) (CBORConstants.BYTE_ARRAY_INDEFINITE),
                (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04, (byte) 0x05, (byte) 0x06, (byte) 0x07, 
                (byte) 0x08, (byte) 0x09, (byte) 0x0A, (byte) 0x0B, (byte) 0x0C, (byte) 0x0D, (byte) 0x0E, 
                (byte) 0x0F, (byte) 0x00, (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04, (byte) 0x05, 
                (byte) 0x06, (byte) 0x07, (byte) 0x08, (byte) 0x09, (byte) 0x0A, (byte) 0x0B, (byte) 0x0C, 
                (byte) 0x0D, (byte) 0x0E, (byte) 0x0F, (byte) 0x00, 
                CBORConstants.BYTE_BREAK);
    }
    
    public void  testMap_less31elt() throws Exception
    {
    	// Test map with less of 31 elements
    	ByteArrayOutputStream out = new ByteArrayOutputStream();
    	CBORGeneratorSizer gen = cborGeneratorSizer(out);
    	
    	gen.writeStartObject();
    	gen.writeFieldName("Fun");
    	gen.writeBoolean(true);
    	gen.writeFieldName("Amt");
    	gen.writeNumber(-2);
    	gen.writeEndObject();
    	gen.close();
    	
        _verifyBytes(out.toByteArray(),
                (byte) (CBORConstants.PREFIX_TYPE_OBJECT + 2),		// # map(2)
                (byte) 0x63,									    // # text(3)
                (byte) 0x46, (byte) 0x75, (byte) 0x6e, 				// # "Fun"
                (byte) 0xf5,        								// # primitive(true)
                (byte) 0x63,        								// # text(3)
                (byte) 0x41, (byte) 0x6d, (byte) 0x74, 				// # "Amt"
                (byte) 0x21);        								// # negative(1)
    }
        
    public void  testMap_more31elt() throws Exception
    {
        // Test map with more of 31 elements
    	ByteArrayOutputStream out = new ByteArrayOutputStream();
    	CBORGeneratorSizer gen = cborGeneratorSizer(out);
    	
    	gen.writeStartObject();
        for(int i=1; i<=32; i++)
        {
        	gen.writeFieldName(Integer.toString(i % 10));
        	gen.writeNumber(i % 10);
        }
        gen.writeEndObject();
        gen.close();
        
        assertEquals((out.toByteArray())[0]								, CBORConstants.BYTE_OBJECT_INDEFINITE);
        assertEquals((out.toByteArray())[out.toByteArray().length - 1]	, CBORConstants.BYTE_BREAK);
        
        for(int i=1; i<(out.toByteArray().length - 2) / 3; i=i+3)
        {
        	assertEquals((char) 0x61, (out.toByteArray())[i]);												// # text(1)
        	assertEquals((out.toByteArray())[i+1], (Integer.toString(((i / 3) + 1) % 10)).getBytes()[0]);	// "x"
        	assertEquals((out.toByteArray())[i+2], (char) ((i / 3) + 1) % 10);								// unsigned(x)
        } 
    }
    
    public void testNestedArrays() throws Exception
    {
        // Test nested arrays.
    	ByteArrayOutputStream out = new ByteArrayOutputStream();
    	CBORGeneratorSizer gen = cborGeneratorSizer(out);
    	
    	gen.writeStartArray();
    	gen.writeNumber(1);
    	gen.writeNumber(2);
    	gen.writeStartArray();
    	gen.writeNumber(3);
    	gen.writeNumber(4);
    	gen.writeEndArray(); 
    	gen.writeNumber(5);
    	gen.writeEndArray();    	
    	gen.close();
  	
        _verifyBytes(out.toByteArray(),
                (byte) (CBORConstants.PREFIX_TYPE_ARRAY + 4),	// # array(4)
                (byte) 0x01, 									// # unsigned(1)
                (byte) 0x02,  									// # unsigned(2)
                (byte) (CBORConstants.PREFIX_TYPE_ARRAY + 2),	// 		# array(2)
                (byte) 0x03, 									// 		# unsigned(3)
                (byte) 0x04, 									// 		# unsigned(4)
                (byte) 0x05); 									// # unsigned(5)
    }
       
    public void testNestedMap() throws Exception
    {
     // Test nested maps
    	ByteArrayOutputStream out = new ByteArrayOutputStream();
    	CBORGeneratorSizer gen = cborGeneratorSizer(out);
    	
    	gen.writeStartObject();
    	gen.writeFieldName("F1");
    	gen.writeNumber(1);
    	gen.writeFieldName("F2");
    	gen.writeNumber(2);
    	gen.writeFieldName("Sub");
    	gen.writeStartObject();
    	gen.writeFieldName("F3");
    	gen.writeNumber(3);
    	gen.writeFieldName("F4");
    	gen.writeNumber(4);
    	gen.writeEndObject();
    	gen.writeFieldName("F5");
    	gen.writeNumber(5);
    	gen.writeEndObject();
    	gen.close();
    	    	
        _verifyBytes(out.toByteArray(),
                (byte) (CBORConstants.PREFIX_TYPE_OBJECT + 4),		// # map(2)
                (byte) 0x62,									    // # text(2)
                (byte) 0x46, (byte) 0x31,							// # "F1"
                (byte) 0x01,        								// # unsigned(1)
                (byte) 0x62,									    // # text(2)
                (byte) 0x46, (byte) 0x32,							// # "F2"
                (byte) 0x02,        								// # unsigned(2)
                (byte) 0x63,        								// # text(3)
                (byte) 0x53, (byte) 0x75, (byte) 0x62, 				// # "Sub"
                (byte) 0xA2,       									// 		# map(2)
                (byte) 0x62,									    // 		# text(2)
                (byte) 0x46, (byte) 0x33,							// 		# "F3"
                (byte) 0x03,        								// 		# unsigned(3)
                (byte) 0x62,									    // 		# text(2)
                (byte) 0x46, (byte) 0x34,							// 		# "F4"
                (byte) 0x04,        								// 		# unsigned(4)
                (byte) 0x62,									    // # text(2)
                (byte) 0x46, (byte) 0x35,							// # "F5"
                (byte) 0x05);        								// # unsigned(5)
    }
        
    public void testNestedMapAndArray() throws Exception
    {
        // Test map nested in array and reciprocally
    	ByteArrayOutputStream out = new ByteArrayOutputStream();
    	CBORGeneratorSizer gen = cborGeneratorSizer(out);
    	
    	gen.writeStartArray();
    	gen.writeNumber(1);
    	gen.writeString("Str");
    	gen.writeStartObject();
    	gen.writeFieldName("Int");
    	gen.writeNumber(2);
    	gen.writeFieldName("Bool");
    	gen.writeBoolean(true);
    	gen.writeFieldName("Array");    	
    	gen.writeStartArray();
    	gen.writeNumber(3);
    	gen.writeNumber(4);
    	gen.writeEndArray(); 
    	gen.writeEndObject();
    	gen.writeString("Str2");
    	gen.writeEndArray();    	
    	gen.close();
    	        
    	_verifyBytes(out.toByteArray(),
                (byte) (CBORConstants.PREFIX_TYPE_ARRAY + 4),						// # array(4)
                (byte) 0x01,               											// # unsigned(1)
                (byte) 0x63,               											// # text(3)
                (byte) 0x53, (byte) 0x74, (byte) 0x72,        						// # "Str"
                (byte) 0xa3,               											// # map(3)
                (byte) 0x63,            											// 		# text(3)
    			(byte) 0x49, (byte) 0x6e, (byte) 0x74,     	 						//		# "Int"
    			(byte) 0x02,            											//		# unsigned(2)
    			(byte) 0x64,          												//		# text(4)
    			(byte) 0x42, (byte) 0x6f, (byte) 0x6f, (byte) 0x6c,   				//		# "Bool"
    			(byte) 0xf5,           												//		# boolean(true)
    			(byte) 0x65,            											//		# text(5)
    			(byte) 0x41, (byte) 0x72, (byte) 0x72, (byte) 0x61, (byte) 0x79, 	//		# "Array"
    			(byte) 0x82,            											//		# array(2)
    			(byte) 0x03,         												//			# unsigned(3)
    			(byte) 0x04,         												//			# unsigned(4)
    			(byte) 0x64,               											// # text(4)
    			(byte) 0x53, (byte) 0x74, (byte) 0x72, (byte) 0x32);      			// # "Str2"
    }
    
    public void  testStringType_Str_queuingEnabled() throws Exception
    {
    	// Test writeString(String text) - queuing enabled
    	ByteArrayOutputStream out = new ByteArrayOutputStream();
    	CBORGeneratorSizer gen = cborGeneratorSizer(out);
    	
    	String valueStr = "Test";
    	gen.writeStartArray();
    	gen.writeString(valueStr);
    	gen.writeEndArray();    	
    	gen.close();
  	
        _verifyBytes(out.toByteArray(),
                (byte) (CBORConstants.PREFIX_TYPE_ARRAY + 1),			// # array(1)
                (byte) 0x64,											// # text(4)
                (byte) 0x54, (byte) 0x65, (byte) 0x73, (byte) 0x74);	// # "Test"
    }
  
    public void  testStringType_Str_queuingDisabled() throws Exception
    {
    	// Test writeString(String text) - queuing disabled
    	ByteArrayOutputStream out = new ByteArrayOutputStream();
    	CBORGeneratorSizer gen = cborGeneratorSizer(out);
    	
    	String valueStr = "Test";
    	gen.writeString(valueStr); 	
    	gen.close();
 	
        _verifyBytes(out.toByteArray(),
                (byte) 0x64,											// # text(4)
                (byte) 0x54, (byte) 0x65, (byte) 0x73, (byte) 0x74);	// # "Test"
    }
       
    public void  testStringType_CharO_queuingEnabled() throws Exception
    {
        // Test writeString(char[] text, int offset, int len) - queuing enabled
    	ByteArrayOutputStream out = new ByteArrayOutputStream();
    	CBORGeneratorSizer gen = cborGeneratorSizer(out);
    	
    	char[] valueChar = {'T', 'e', 's', 't'};
    	gen.writeStartArray();
    	gen.writeString(valueChar, 0, 4);
    	gen.writeEndArray();    	
    	gen.close();
    	 	
        _verifyBytes(out.toByteArray(),
                (byte) (CBORConstants.PREFIX_TYPE_ARRAY + 1),			// # array(1)
                (byte) 0x64,											// # text(4)
                (byte) 0x54, (byte) 0x65, (byte) 0x73, (byte) 0x74);	// # "Test"    	
    }
    
    public void  testStringType_CharO_queuingDisabled() throws Exception
    {   
        // Test writeString(char[] text, int offset, int len) - queuing disabled
    	ByteArrayOutputStream out = new ByteArrayOutputStream();
    	CBORGeneratorSizer gen = cborGeneratorSizer(out);
    	
    	char[] valueChar = {'T', 'e', 's', 't'};
    	gen.writeString(valueChar, 0, 4);  	
    	gen.close();
    	 	
        _verifyBytes(out.toByteArray(),
                (byte) 0x64,											// # text(4)
                (byte) 0x54, (byte) 0x65, (byte) 0x73, (byte) 0x74);	// # "Test"        
    }
    
    public void  testStringType_ByteO_queuingEnabled() throws Exception
    {
        // Test writeRawUTF8String(byte[] text, int offset, int length) - queuing enabled
    	ByteArrayOutputStream out = new ByteArrayOutputStream();
    	CBORGeneratorSizer gen = cborGeneratorSizer(out);
    	
    	byte[] valueCharUtf8 = "Test".getBytes();
    	gen.writeStartArray();
    	gen.writeRawUTF8String(valueCharUtf8, 0, 4);
    	gen.writeEndArray();    	
    	gen.close();
    	 	
        _verifyBytes(out.toByteArray(),
                (byte) (CBORConstants.PREFIX_TYPE_ARRAY + 1),			// # array(1)
                (byte) 0x64,											// # text(4)
                (byte) 0x54, (byte) 0x65, (byte) 0x73, (byte) 0x74);	// # "Test"
    }
    
    public void  testStringType_ByteO_queuingDisabled() throws Exception
    {
        // Test writeRawUTF8String(byte[] text, int offset, int length) - queuing disabled
    	ByteArrayOutputStream out = new ByteArrayOutputStream();
    	CBORGeneratorSizer gen = cborGeneratorSizer(out);

    	byte[] valueCharUtf8 = "Test".getBytes();
    	gen.writeRawUTF8String(valueCharUtf8, 0, 4);	
    	gen.close();
    	  	
        _verifyBytes(out.toByteArray(),
                (byte) 0x64,											// # text(4)
                (byte) 0x54, (byte) 0x65, (byte) 0x73, (byte) 0x74);	// # "Test"
    }
    
    public void  testStringType_ByteArray_queuingEnabled() throws Exception
    {
        // Test writeUTF8String(byte[] text, int offset, int length) - queuing enabled
    	ByteArrayOutputStream out = new ByteArrayOutputStream();
    	CBORGeneratorSizer gen = cborGeneratorSizer(out);
    	
    	byte[] valueCharUtf8 = "Test".getBytes();
    	gen.writeStartArray();
    	gen.writeUTF8String(valueCharUtf8, 0, 4);
    	gen.writeEndArray();    	
    	gen.close();
    	 	
        _verifyBytes(out.toByteArray(),
                (byte) (CBORConstants.PREFIX_TYPE_ARRAY + 1),			// # array(1)
                (byte) 0x64,											// # text(4)
                (byte) 0x54, (byte) 0x65, (byte) 0x73, (byte) 0x74);	// # "Test"
    }
    
    public void  testStringType_ByteArray_queuingDisabled() throws Exception
    {
        // Test writeUTF8String(byte[] text, int offset, int length) - queuing disabled
    	ByteArrayOutputStream out = new ByteArrayOutputStream();
    	CBORGeneratorSizer gen = cborGeneratorSizer(out);
    	
    	byte[] valueCharUtf8 = "Test".getBytes();
    	gen.writeUTF8String(valueCharUtf8, 0, 4); 	
    	gen.close();
    	 	
        _verifyBytes(out.toByteArray(),
                (byte) 0x64,											// # text(4)
                (byte) 0x54, (byte) 0x65, (byte) 0x73, (byte) 0x74);	// # "Test"
    }
    
    public void testNumberType_int_queuingEnabled() throws IOException 
    {
    	// Test writeNumber(int v) - queuing enabled
    	ByteArrayOutputStream out = new ByteArrayOutputStream();
    	CBORGeneratorSizer gen = cborGeneratorSizer(out);
    	
    	gen.writeStartArray();
    	gen.writeNumber(Integer.MAX_VALUE);
    	gen.writeEndArray();    	
    	gen.close();
 	
        _verifyBytes(out.toByteArray(),
                (byte) (CBORConstants.PREFIX_TYPE_ARRAY + 1),			// # array(3)
                (byte) 0x1A,											// # Unsigned
                (byte) 0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF);	// # int max value
    }
    
    public void testNumberType_int_queuingDisabled() throws IOException 
    {
    	// Test writeNumber(int v) - queuing disabled
    	ByteArrayOutputStream out = new ByteArrayOutputStream();
    	CBORGeneratorSizer gen = cborGeneratorSizer(out);
    	
    	gen.writeNumber(Integer.MAX_VALUE); 	
    	gen.close();
 	
        _verifyBytes(out.toByteArray(),
                (byte) 0x1A,											// # Unsigned
                (byte) 0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF);	// # int max value
    }
    
    public void testNumberType_long_queuingEnabled() throws IOException 
    {
    	// Test writeNumber(long v) - queuing enabled
    	ByteArrayOutputStream out = new ByteArrayOutputStream();
    	CBORGeneratorSizer gen = cborGeneratorSizer(out);
    	
    	gen.writeStartArray();
    	gen.writeNumber(Long.MAX_VALUE);
    	gen.writeEndArray();    	
    	gen.close();
	
        _verifyBytes(out.toByteArray(),
                (byte) (CBORConstants.PREFIX_TYPE_ARRAY + 1),			// # array(3)
                (byte) 0x1B,											// # Unsigned
                (byte) 0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
                (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF);	// # long max value
    }
    
    public void testNumberType_long_queuingDisabled() throws IOException 
    {
    	// Test writeNumber(long v) - queuing disabled
    	ByteArrayOutputStream out = new ByteArrayOutputStream();
    	CBORGeneratorSizer gen = cborGeneratorSizer(out);
    	
    	gen.writeNumber(Long.MAX_VALUE);   	
    	gen.close();
 	
        _verifyBytes(out.toByteArray(),
                (byte) 0x1B,											// # Unsigned
                (byte) 0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
                (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF);	// # long max value
    }
    
    public void testNumberType_BigInteger_queuingEnabled() throws IOException 
    {
    	// Test writeNumber(BigInteger v) - queuing enabled
    	ByteArrayOutputStream out = new ByteArrayOutputStream();
    	CBORGeneratorSizer gen = cborGeneratorSizer(out);
    	
    	gen.writeStartArray();
    	gen.writeNumber(BigInteger.ONE);
    	gen.writeEndArray();    	
    	gen.close();
 	
        _verifyBytes(out.toByteArray(),
                (byte) (CBORConstants.PREFIX_TYPE_ARRAY + 1),			// # array(3)
                (byte) 0xC2,											// # tag(2) - Positive bignum
                (byte) 0x41,											// # bytes(1)
                (byte) 0x01);											// # \x01
    }
    
    public void testNumberType_BigInteger_queuingDisabled() throws IOException 
    {
    	// Test writeNumber(BigInteger v) - queuing enabled
    	ByteArrayOutputStream out = new ByteArrayOutputStream();
    	CBORGeneratorSizer gen = cborGeneratorSizer(out);
    	
    	gen.writeNumber(BigInteger.ONE); 	
    	gen.close();
 	
        _verifyBytes(out.toByteArray(),
                (byte) 0xC2,											// # tag(2) - Positive bignum
                (byte) 0x41,											// # bytes(1)
                (byte) 0x01);											// # \x01
    }
    
    public void testNumberType_double_queuingEnabled() throws IOException 
    {
    	// Test writeNumber(double v) - queuing enabled
    	ByteArrayOutputStream out = new ByteArrayOutputStream();
    	CBORGeneratorSizer gen = cborGeneratorSizer(out);
    	
    	gen.writeStartArray();
    	gen.writeNumber(Double.MAX_VALUE);
    	gen.writeEndArray();    	
    	gen.close();
	
        _verifyBytes(out.toByteArray(),
                (byte) (CBORConstants.PREFIX_TYPE_ARRAY + 1),			// # array(3)
                (byte) 0xFB,											
                (byte) 0x7F, (byte) 0xEF, (byte) 0xFF, (byte) 0xFF,
                (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF);	// # double max value
    }
    
    public void testNumberType_double_queuingDisabled() throws IOException 
    {
    	// Test writeNumber(double v) - queuing disabled
    	ByteArrayOutputStream out = new ByteArrayOutputStream();
    	CBORGeneratorSizer gen = cborGeneratorSizer(out);
    	
    	gen.writeNumber(Double.MAX_VALUE);  	
    	gen.close();
 	
        _verifyBytes(out.toByteArray(),
                (byte) 0xFB,											
                (byte) 0x7F, (byte) 0xEF, (byte) 0xFF, (byte) 0xFF,
                (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF);	// # double max value
    }
    
    public void testNumberType_float_queuingEnabled() throws IOException 
    {
    	// Test writeNumber(float v) - queuing enabled
    	ByteArrayOutputStream out = new ByteArrayOutputStream();
    	CBORGeneratorSizer gen = cborGeneratorSizer(out);
    	
    	gen.writeStartArray();
    	gen.writeNumber(Float.MAX_VALUE);
    	gen.writeEndArray();    	
    	gen.close();
	
        _verifyBytes(out.toByteArray(),
                (byte) (CBORConstants.PREFIX_TYPE_ARRAY + 1),			// # array(3)
                (byte) 0xFA,											
                (byte) 0x7F, (byte) 0x7F, (byte) 0xFF, (byte) 0xFF);	// # float max value
    }
    
    public void testNumberType_float_queuingDisabled() throws IOException 
    {
    	// Test writeNumber(float v) - queuing disabled
    	ByteArrayOutputStream out = new ByteArrayOutputStream();
    	CBORGeneratorSizer gen = cborGeneratorSizer(out);
    	
    	gen.writeNumber(Float.MAX_VALUE);  	
    	gen.close();
  	
        _verifyBytes(out.toByteArray(),
                (byte) 0xFA,											
                (byte) 0x7F, (byte) 0x7F, (byte) 0xFF, (byte) 0xFF);	// # float max value
    }
    
    public void testNumberType_BigDecimal_queuingEnabled() throws IOException 
    {
    	// Test writeNumber(BigDecimal v) - queuing enabled
    	ByteArrayOutputStream out = new ByteArrayOutputStream();
    	CBORGeneratorSizer gen = cborGeneratorSizer(out);
    	
    	gen.writeStartArray();
    	gen.writeNumber(BigDecimal.TEN);
    	gen.writeEndArray();    	
    	gen.close();
 	
        _verifyBytes(out.toByteArray(),
                (byte) (CBORConstants.PREFIX_TYPE_ARRAY + 1),			// # array(3)
                (byte) 0xC5,											// # tag(5) - BigFloat
                (byte) 0x82,											// # array(2)
                (byte) 0x00,											// Unsigned(0)
                (byte) 0x0A);											// Unsigned(10)
    }
    
    public void testNumberType_BigDecimal_queuingDisabled() throws IOException 
    {
    	// Test writeNumber(BigDecimal v) - queuing disabled
    	ByteArrayOutputStream out = new ByteArrayOutputStream();
    	CBORGeneratorSizer gen = cborGeneratorSizer(out);
    	
    	gen.writeNumber(BigDecimal.TEN);	
    	gen.close();
 	
        _verifyBytes(out.toByteArray(),
                (byte) 0xC5,											// # tag(5) - BigFloat
                (byte) 0x82,											// # array(2)
                (byte) 0x00,											// Unsigned(0)
                (byte) 0x0A);											// Unsigned(10)
    }
    
    public void testNumberType_String_queuingEnabled() throws IOException 
    {
    	// Test writeNumber(String v) - queuing enabled
    	ByteArrayOutputStream out = new ByteArrayOutputStream();
    	CBORGeneratorSizer gen = cborGeneratorSizer(out);
    	
    	gen.writeStartArray();
    	gen.writeNumber("42");
    	gen.writeEndArray();    	
    	gen.close();
	
        _verifyBytes(out.toByteArray(),
                (byte) (CBORConstants.PREFIX_TYPE_ARRAY + 1),			// # array(1)
                (byte) 0x62,											// # text(2)
                (byte) 0x34, (byte) 0x32);								// "42"
    }
    
    public void testNumberType_String_queuingDisabled() throws IOException 
    {
    	// Test writeNumber(String v) - queuing disabled
    	ByteArrayOutputStream out = new ByteArrayOutputStream();
    	CBORGeneratorSizer gen = cborGeneratorSizer(out);
    	
    	gen.writeNumber("42");   	
    	gen.close();
 	
        _verifyBytes(out.toByteArray(),
                (byte) 0x62,											// # text(2)
                (byte) 0x34, (byte) 0x32);								// "42"
    }
    
    public void testBooleanType_queuingEnabled() throws IOException 
    {
    	// Test writeBoolean(boolean state) - queuing enabled
    	ByteArrayOutputStream out = new ByteArrayOutputStream();
    	CBORGeneratorSizer gen = cborGeneratorSizer(out);
    	
    	gen.writeStartArray();
    	gen.writeBoolean(true);
    	gen.writeEndArray();    	
    	gen.close();
	
        _verifyBytes(out.toByteArray(),
                (byte) (CBORConstants.PREFIX_TYPE_ARRAY + 1),			// # array(1)
                (byte) 0xF5);											// # true
    }
    
    public void testBooleanType_queuingDisabled() throws IOException 
    {
    	// Test writeBoolean(boolean state) - queuing disabled
    	ByteArrayOutputStream out = new ByteArrayOutputStream();
    	CBORGeneratorSizer gen = cborGeneratorSizer(out);
    	
    	gen.writeBoolean(true);   	
    	gen.close();
	
        _verifyBytes(out.toByteArray(),
                (byte) 0xF5);											// # true
    }
    
    public void testNull_queuingEnabled() throws IOException 
    {
    	// Test writeNull() - queuing enabled
    	ByteArrayOutputStream out = new ByteArrayOutputStream();
    	CBORGeneratorSizer gen = cborGeneratorSizer(out);
    	
    	gen.writeStartArray();
    	gen.writeNull();
    	gen.writeEndArray();    	
    	gen.close();
 	
        _verifyBytes(out.toByteArray(),
                (byte) (CBORConstants.PREFIX_TYPE_ARRAY + 1),			// # array(1)
                (byte) 0xF6);											// # null
    }
    
    public void testNull_queuingDisabled() throws IOException 
    {
    	// Test writeNull() - queuing disabled
    	ByteArrayOutputStream out = new ByteArrayOutputStream();
    	CBORGeneratorSizer gen = cborGeneratorSizer(out);
    	
    	gen.writeNull();   	
    	gen.close();
	
        _verifyBytes(out.toByteArray(),
                (byte) 0xF6);											// # null
    }
    
    public void testTag_queuingEnabled() throws IOException
    {
    	// Test writeTag(int tagId) - queuing enabled
    	ByteArrayOutputStream out = new ByteArrayOutputStream();
    	CBORGeneratorSizer gen = cborGeneratorSizer(out);
    	
    	gen.writeStartArray();
    	gen.writeTag(1);
    	gen.writeEndArray();  	
    	gen.close();
 	
        _verifyBytes(out.toByteArray(),
                (byte) (CBORConstants.PREFIX_TYPE_ARRAY + 1),			// # array(1)
                (byte) 0xC1);											// # tag(1)
    }
    
    public void testTag_queuingDisabled() throws IOException
    {
    	// Test writeTag(int tagId) - queuing disabled
    	ByteArrayOutputStream out = new ByteArrayOutputStream();
    	CBORGeneratorSizer gen = cborGeneratorSizer(out);
    	
    	gen.writeTag(1); 	
    	gen.close();
 	
        _verifyBytes(out.toByteArray(),
                (byte) 0xC1);											// # tag(1)
    }
    
    public void testWrappedMethod() throws IOException
    {
    	ByteArrayOutputStream out = new ByteArrayOutputStream();
    	CBORGeneratorSizer gen = cborGeneratorSizer(out);
    	
    	gen.flush();
    }
    
    public void testUnsupportedMethods_RawChar_queuingEnabled() throws IOException
    {
    	ByteArrayOutputStream out = new ByteArrayOutputStream();
    	CBORGeneratorSizer gen = cborGeneratorSizer(out);
    	
    	try{
        	gen.writeStartArray();
    		gen.writeRaw('A');
        	gen.writeEndArray(); 
    		fail("Should thrown not supported exception");
    	}
    	catch(UnsupportedOperationException aExp){
    	}
    }
    
    public void testUnsupportedMethods_RawChar_queuingDisabled() throws IOException
    {
    	ByteArrayOutputStream out = new ByteArrayOutputStream();
    	CBORGeneratorSizer gen = cborGeneratorSizer(out);
    	
    	try{
    		gen.writeRaw('A');
    		fail("Should thrown not supported exception");
    	}
    	catch(UnsupportedOperationException aExp){
    	}
    }
    
    public void testUnsupportedMethods_RawStr_queuingEnabled() throws IOException
    {
    	ByteArrayOutputStream out = new ByteArrayOutputStream();
    	CBORGeneratorSizer gen = cborGeneratorSizer(out);
    	
    	try{
        	gen.writeStartArray();
    		gen.writeRaw((String) "test");
        	gen.writeEndArray(); 
    		fail("Should thrown not supported exception");
    	}
    	catch(UnsupportedOperationException aExp){
    		// Check the method is not supported
    	}
    }
    
    public void testUnsupportedMethods_RawStr_queuingDisabled() throws IOException
    {
    	ByteArrayOutputStream out = new ByteArrayOutputStream();
    	CBORGeneratorSizer gen = cborGeneratorSizer(out);
    	
    	try{
    		gen.writeRaw((String) "test"); 
    		fail("Should thrown not supported exception");
    	}
    	catch(UnsupportedOperationException aExp){
    		// Check the method is not supported
    	}
    }
    
    public void testUnsupportedMethods_RawStrO_queuingEnabled() throws IOException
    {
    	ByteArrayOutputStream out = new ByteArrayOutputStream();
    	CBORGeneratorSizer gen = cborGeneratorSizer(out);
    	
    	try{
        	gen.writeStartArray();
    		gen.writeRaw((String) "test", 0, 4);
        	gen.writeEndArray(); 
    		fail("Should thrown not supported exception");
    	}
    	catch(UnsupportedOperationException aExp){
    		// Check the method is not supported
    	}
    }
    
    public void testUnsupportedMethods_RawStrO_queuingDisabled() throws IOException
    {
    	ByteArrayOutputStream out = new ByteArrayOutputStream();
    	CBORGeneratorSizer gen = cborGeneratorSizer(out);
    	
    	try{
    		gen.writeRaw((String) "test", 0, 4);
    		fail("Should thrown not supported exception");
    	}
    	catch(UnsupportedOperationException aExp){
    		// Check the method is not supported
    	}
    }
    
    public void testUnsupportedMethods_RawValueStrO_queuingEnabled() throws IOException
    {
    	ByteArrayOutputStream out = new ByteArrayOutputStream();
    	CBORGeneratorSizer gen = cborGeneratorSizer(out);
    	
    	try{
        	gen.writeStartArray();
    		gen.writeRawValue((String) "test", 0, 4);
        	gen.writeEndArray(); 
    		fail("Should thrown not supported exception");
    	}
    	catch(UnsupportedOperationException aExp){
    		// Check the method is not supported
    	}
    }
    
    public void testUnsupportedMethods_RawValueStrO_queuingDisabled() throws IOException
    {
    	ByteArrayOutputStream out = new ByteArrayOutputStream();
    	CBORGeneratorSizer gen = cborGeneratorSizer(out);
    	
    	try{
    		gen.writeRawValue((String) "test", 0, 4);
    		fail("Should thrown not supported exception");
    	}
    	catch(UnsupportedOperationException aExp){
    		// Check the method is not supported
    	}
    }
    
    public void testUnsupportedMethods_RawCharArrayO_queuingEnabled() throws IOException
    {
    	ByteArrayOutputStream out = new ByteArrayOutputStream();
    	CBORGeneratorSizer gen = cborGeneratorSizer(out);
    	
    	try{
        	gen.writeStartArray();
        	char[] valueChar = {'T', 'e', 's', 't'};
    		gen.writeRaw(valueChar, 0, 4);
        	gen.writeEndArray(); 
    		fail("Should thrown not supported exception");
    	}
    	catch(UnsupportedOperationException aExp){
    		// Check the method is not supported
    	}
    }
    
    public void testUnsupportedMethods_RawCharArrayO_queuingDisabled() throws IOException
    {
    	ByteArrayOutputStream out = new ByteArrayOutputStream();
    	CBORGeneratorSizer gen = cborGeneratorSizer(out);
    	
    	try{
        	char[] valueChar = {'T', 'e', 's', 't'};
    		gen.writeRaw(valueChar, 0, 4);
    		fail("Should thrown not supported exception");
    	}
    	catch(UnsupportedOperationException aExp){
    		// Check the method is not supported
    	}
    }
    
}
