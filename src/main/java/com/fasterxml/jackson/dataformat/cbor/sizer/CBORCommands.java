package com.fasterxml.jackson.dataformat.cbor.sizer;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedList;

import com.fasterxml.jackson.core.Base64Variant;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.dataformat.cbor.CBORGenerator;

/**
 * Interface will be implemented for the writing method storage 
 */
interface Command {
	void execute() throws JsonGenerationException, IOException;
}

/**
 * This class is implemented by ExecuterOfArraySubCommands and
 * ExecuterOfObjectSubCommands. It offers a method to execute all
 * the commands stored in the LinkedList<Commands> of these classes.
 */
abstract class ExecuterOfSubCommands implements Command
{
	protected void executeQueueContent(LinkedList<Command> commandList) throws IOException
	{				
		for(Command element : commandList){
			try {
				element.execute();
			} catch (JsonGenerationException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		commandList.clear();
	}
}

/**
 * Class used to represent a sub array in data
 * When the execute function is called, the array size is
 * determined by the number of elements contained in the 
 * LinkedList.
 */
class ExecuterOfArraySubCommands extends ExecuterOfSubCommands
{
	LinkedList<Command> 	_subListToExecute;
	CBORGenerator			_cborGenerator;
	
	public ExecuterOfArraySubCommands(CBORGenerator cborGenerator, LinkedList<Command> list)
	{
		this._subListToExecute 	= list;
		this._cborGenerator		= cborGenerator;
	}

	@Override
	public void execute() throws JsonGenerationException, IOException {
		_cborGenerator.writeStartArray(_subListToExecute.size());
		
		executeQueueContent(_subListToExecute);
		
		_cborGenerator.writeEndArray();
	}
}

/**
 * Class used to represent a sub object in data
 * When the execute function is called, the number of pairs
 * is determined by dividing by two the number of elements 
 * contained in the LinkedList.
 */
class ExecuterOfObjectSubCommands extends ExecuterOfSubCommands
{
	LinkedList<Command> 	_subListToExecute;
	CBORGenerator			_cborGenerator;
	
	public ExecuterOfObjectSubCommands(CBORGenerator cborGenerator, LinkedList<Command> list)
	{
		this._subListToExecute 	= list;
		this._cborGenerator		= cborGenerator;
	}

	@Override
	public void execute() throws JsonGenerationException, IOException {
		_cborGenerator.writeStartObject(Math.round(_subListToExecute.size() / 2));
		
		executeQueueContent(_subListToExecute);
		
		_cborGenerator.writeEndObject();
	}
}

class WriterFieldName implements Command{
	private CBORGenerator 	_generator;
	private String 			_name;
	
	public WriterFieldName(CBORGenerator generator, String name)
	{
		this._generator = generator;
		this._name 		= name;
	}

	@Override
	public void execute() throws JsonGenerationException, IOException {
		_generator.writeFieldName(_name);			
	}
}

class WriterStringStr implements Command{
	private CBORGenerator 	_generator;
	private String 			_text;
	
	public WriterStringStr(CBORGenerator generator, String text)
	{
		this._generator = generator;
		this._text 		= text;
	}

	@Override
	public void execute() throws JsonGenerationException, IOException {
		_generator.writeString(_text);			
	}
}

class WriterStringChar implements Command{
	private CBORGenerator 	_generator;
	private char[] 			_text;
	private int 			_offset;
	private int 			_len;
	
	public WriterStringChar(CBORGenerator generator, char[] text, int offset, int len)
	{
		this._generator = generator;
		this._text 		= text;
		this._offset 	= offset;
		this._len 		= len;
	}

	@Override
	public void execute() throws JsonGenerationException, IOException {
		_generator.writeString(_text, _offset, _len);			
	}
}


class WriterRawUTF8String implements Command{
	private CBORGenerator 	_generator;
	private byte[] 			_text;
	private int 			_offset;
	private int 			_len;
	
	public WriterRawUTF8String(CBORGenerator generator, byte[] text, int offset, int length)
	{
		this._generator = generator;
		this._text 		= text;
		this._offset 	= offset;
		this._len 		= length;
	}

	@Override
	public void execute() throws JsonGenerationException, IOException {
		_generator.writeRawUTF8String(_text, _offset, _len);			
	}
}

class WriterUTF8String implements Command{
	private CBORGenerator 	_generator;
	private byte[] 			_text;
	private int 			_offset;
	private int 			_len;
	
	public WriterUTF8String(CBORGenerator generator, byte[] text, int offset, int length)
	{
		this._generator = generator;
		this._text 		= text;
		this._offset 	= offset;
		this._len 		= length;
	}

	@Override
	public void execute() throws JsonGenerationException, IOException {
		_generator.writeUTF8String(_text, _offset, _len);			
	}
}

class WriterRaw implements Command{
	private CBORGenerator 	_generator;
	private String 			_text;
	
	public WriterRaw(CBORGenerator generator, String text)
	{
		this._generator = generator;
		this._text 		= text;
	}

	@Override
	public void execute() throws JsonGenerationException, IOException {
		_generator.writeRaw(_text);			
	}
}

class WriterRawSo implements Command{
	private CBORGenerator 	_generator;
	private String 			_text;
	private int 			_offset;
	private int 			_len;
	
	public WriterRawSo(CBORGenerator generator, String text, int offset, int length)
	{
		this._generator = generator;
		this._text 		= text;
		this._offset 	= offset;
		this._len 		= length;
	}

	@Override
	public void execute() throws JsonGenerationException, IOException {
		_generator.writeRaw(_text, _offset, _len);			
	}
}

class WriterRawCo implements Command{
	private CBORGenerator 	_generator;
	private char[] 			_text;
	private int 			_offset;
	private int 			_len;
	
	public WriterRawCo(CBORGenerator generator, char[] text, int offset, int length)
	{
		this._generator = generator;
		this._text 		= text;
		this._offset 	= offset;
		this._len 		= length;
	}

	@Override
	public void execute() throws JsonGenerationException, IOException {
		_generator.writeRaw(_text, _offset, _len);			
	}
}


class WriterRawC implements Command{
	private CBORGenerator 	_generator;
	private char 			_c;
	
	public WriterRawC(CBORGenerator generator, char c)
	{
		this._generator = generator;
		this._c 		= c;
	}

	@Override
	public void execute() throws JsonGenerationException, IOException {
		_generator.writeRaw(_c);				
	}
}

class WriterBinary implements Command{
	private CBORGenerator 	_generator;
	private Base64Variant 	_bv;
	private byte[] 			_data;
	private int 			_offset;
	private int 			_len;
	
	public WriterBinary(CBORGenerator generator, Base64Variant bv, byte[] data, int offset, int len)
	{
		this._generator = generator;
		this._bv		= bv;
		this._data 		= data;
		this._offset 	= offset;
		this._len 		= len;
	}

	@Override
	public void execute() throws JsonGenerationException, IOException {
		_generator.writeBinary(_bv, _data, _offset, _len);					
	}
}

class WriterNumberInt implements Command{
	private CBORGenerator 	_generator;
	private int 			_number;
	
	public WriterNumberInt(CBORGenerator generator, int v)
	{
		this._generator = generator;
		this._number    = v;
	}

	@Override
	public void execute() throws JsonGenerationException, IOException {	
		_generator.writeNumber(_number);	
	}
}

class WriterNumberLong implements Command{
	private CBORGenerator 	_generator;
	private long 			_number;
	
	public WriterNumberLong(CBORGenerator generator, long v)
	{
		this._generator = generator;
		this._number    = v;
	}

	@Override
	public void execute() throws JsonGenerationException, IOException {	
		_generator.writeNumber(_number);	
	}
}


class WriterNumberBigInteger implements Command{
	private CBORGenerator 	_generator;
	private BigInteger		_number;
	
	public WriterNumberBigInteger(CBORGenerator generator, BigInteger v)
	{
		this._generator = generator;
		this._number    = v;
	}

	@Override
	public void execute() throws JsonGenerationException, IOException {	
		_generator.writeNumber(_number);	
	}
}

class WriterNumberDouble implements Command{
	private CBORGenerator 	_generator;
	private double		_number;
	
	public WriterNumberDouble(CBORGenerator generator, double v)
	{
		this._generator = generator;
		this._number    = v;
	}

	@Override
	public void execute() throws JsonGenerationException, IOException {	
		_generator.writeNumber(_number);	
	}
}

class WriterNumberFloat implements Command{
	private CBORGenerator 	_generator;
	private float			_number;
	
	public WriterNumberFloat(CBORGenerator generator, float v)
	{
		this._generator = generator;
		this._number    = v;
	}

	@Override
	public void execute() throws JsonGenerationException, IOException {	
		_generator.writeNumber(_number);	
	}
}

class WriterNumberBigDecimal implements Command{
	private CBORGenerator 	_generator;
	private BigDecimal		_number;
	
	public WriterNumberBigDecimal(CBORGenerator generator, BigDecimal v)
	{
		this._generator = generator;
		this._number    = v;
	}

	@Override
	public void execute() throws JsonGenerationException, IOException {	
		_generator.writeNumber(_number);	
	}
}

class WriterNumberString implements Command{
	private CBORGenerator 	_generator;
	private String			_number;
	
	public WriterNumberString(CBORGenerator generator, String encodedValue)
	{
		this._generator = generator;
		this._number    = encodedValue;
	}

	@Override
	public void execute() throws JsonGenerationException, IOException {	
		_generator.writeNumber(_number);	
	}
}


class WriterBoolean implements Command{
	private CBORGenerator 	_generator;
	private boolean			_state;
	
	public WriterBoolean(CBORGenerator generator, boolean state)
	{
		this._generator = generator;
		this._state    	= state;
	}

	@Override
	public void execute() throws JsonGenerationException, IOException {	
		_generator.writeBoolean(_state);	
	}
}

class WriterNull implements Command{
	private CBORGenerator 	_generator;
	
	public WriterNull(CBORGenerator generator)
	{
		this._generator = generator;
	}

	@Override
	public void execute() throws JsonGenerationException, IOException {	
		_generator.writeNull();	
	}
}

class WriterTag implements Command{
	private CBORGenerator 	_generator;
	private int				_tag;
	
	public WriterTag(CBORGenerator generator, int tag)
	{
		this._generator = generator;
		this._tag    	= tag;
	}

	@Override
	public void execute() throws JsonGenerationException, IOException {	
		_generator.writeTag(_tag);	
	}
}
