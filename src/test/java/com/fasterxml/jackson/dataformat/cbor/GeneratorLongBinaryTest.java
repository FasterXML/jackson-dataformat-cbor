package com.fasterxml.jackson.dataformat.cbor;

import static org.junit.Assert.assertEquals;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonToken;

public class GeneratorLongBinaryTest
{
	final static int SMALL_LENGTH = 100;
	final static int LARGE_LENGTH = CBORGenerator.BYTE_BUFFER_FOR_OUTPUT + 500;

	 @Rule
	 public TemporaryFolder tempFolder = new TemporaryFolder();

	private File binaryInputFile;
	private File cborFile;
	private File binaryOutputFile;

	@Before
	public void before() throws IOException
	{
		 binaryInputFile = tempFolder.newFile("sourceData.bin");
		 cborFile = tempFolder.newFile("cbor.bin");
		 binaryOutputFile = tempFolder.newFile("outputData.bin");
	}

	private void generateInputFile(File binaryInputFile, int fileSize) throws NoSuchAlgorithmException, IOException
	{
		BufferedOutputStream os = null;
		try
		{
			os = new BufferedOutputStream(new FileOutputStream(binaryInputFile));
			SecureRandom sr = SecureRandom.getInstance("SHA1PRNG");
			byte[] temp = new byte[1024];
			int remaining = fileSize;
			while (remaining > 0)
			{
				sr.nextBytes(temp);
				os.write(temp, 0, Math.min(temp.length, remaining));
				remaining -= temp.length;
			}
		}
		finally
		{
			if (os != null)
			{
				os.close();
			}
		}
	}

	private void testEncodeAndDecodeBytes(int length) throws NoSuchAlgorithmException, IOException
	{
		generateInputFile(this.binaryInputFile, length);
		encodeInCBOR(this.binaryInputFile, this.cborFile);
		decodeFromCborInFile(this.cborFile, this.binaryOutputFile);
		asserFileEquals(this.binaryInputFile, this.binaryOutputFile);
	}

	private void encodeInCBOR(File inputFile, File outputFile) throws NoSuchAlgorithmException, IOException
	{
		CBORFactory f = new CBORFactory();
		OutputStream os = null;
		InputStream is = null;
		JsonGenerator gen = null;
		try
		{
			os = new BufferedOutputStream(new FileOutputStream(outputFile));
			is = new BufferedInputStream(new FileInputStream(inputFile));

			gen = f.createGenerator(os);
			gen.writeBinary(is, is.available());
		}
		finally
		{
			if (gen != null)
			{
				gen.close();
			}
			if (is != null)
			{
				is.close();
			}
			if (os != null)
			{
				os.close();
			}
		}
	}

	private void decodeFromCborInFile(File cborFile, File outputFile) throws NoSuchAlgorithmException, IOException
	{

		CBORFactory f = new CBORFactory();
		OutputStream os = null;
		InputStream is = null;
		CBORParser parser = null;
		try
		{
			is = new BufferedInputStream(new FileInputStream(cborFile));
			parser = f.createParser(is);
			parser.nextToken();
			parser.readBinaryValue(null, new FileOutputStream(outputFile));
		}
		finally
		{
			if (parser != null)
			{
				parser.close();
			}
			if (is != null)
			{
				is.close();
			}
			if (os != null)
			{
				os.close();
			}
		}
	}

	@Test
	public void testSmallByteArray() throws Exception
	{
		testEncodeAndDecodeBytes(SMALL_LENGTH);
		
	}
	
	@Test
	public void testLargeByteArray() throws Exception
	{
		testEncodeAndDecodeBytes(LARGE_LENGTH);
		
	}

	private void asserFileEquals(File file1, File file2) throws IOException
	{
		FileInputStream fis1 = null;
		FileInputStream fis2 = null;
		try
		{
			fis1 = new FileInputStream(file1);
			fis2 = new FileInputStream(file2);

			assertEquals(fis1.available(), fis2.available());
			while (fis1.available() > 0)
			{
				assertEquals(fis1.read(), fis2.read());
			}
		}
		finally
		{
			if (file1 != null)
			{
				fis1.close();
			}
			if (file2 != null)
			{
				fis2.close();
			}
		}

	}
}
