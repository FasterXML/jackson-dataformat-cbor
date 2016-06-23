package com.fasterxml.jackson.dataformat.cbor.sizer;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Queue;

import com.fasterxml.jackson.core.Base64Variant;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.dataformat.cbor.CBORGenerator;

/**
 * Interface will be implemented for the writing method storage
 */
abstract class Command {
    protected CBORGenerator _cborGenerator;

    public Command(CBORGenerator cborGenerator) {
        _cborGenerator = cborGenerator;
    }

    public abstract void execute() throws JsonGenerationException, IOException;
}

/**
 * This class is implemented by ExecuterOfArraySubCommands and
 * ExecuterOfObjectSubCommands. It offers a method to execute all the commands
 * stored in the LinkedList<Commands> of these classes.
 */
abstract class ExecuterOfSubCommands extends Command {
    protected Queue<Command> _subListToExecute;

    public ExecuterOfSubCommands(CBORGenerator cborGenerator, Queue<Command> list) {
        super(cborGenerator);
        this._subListToExecute = list;
    }

    protected void executeQueueContent(Queue<Command> commandList) throws IOException {
        try {
            for (Command element : commandList) {
                element.execute();
            }
        } finally {
            commandList.clear();
        }
    }
}

/**
 * Class used to represent a sub array in data When the execute function is
 * called, the array size is determined by the number of elements contained in
 * the LinkedList.
 */
class ExecuterOfArraySubCommands extends ExecuterOfSubCommands {
    public ExecuterOfArraySubCommands(CBORGenerator cborGenerator, Queue<Command> list) {
        super(cborGenerator, list);
    }

    @Override
    public void execute() throws JsonGenerationException, IOException {
        _cborGenerator.writeStartArray(_subListToExecute.size());

        executeQueueContent(_subListToExecute);

        _cborGenerator.writeEndArray();
    }
}

/**
 * Class used to represent a sub object in data When the execute function is
 * called, the number of pairs is determined by dividing by two the number of
 * elements contained in the LinkedList.
 */
class ExecuterOfObjectSubCommands extends ExecuterOfSubCommands {
    public ExecuterOfObjectSubCommands(CBORGenerator cborGenerator, Queue<Command> list) {
        super(cborGenerator, list);
    }

    @Override
    public void execute() throws JsonGenerationException, IOException {
        _cborGenerator.writeStartObject(Math.round(_subListToExecute.size() / 2));

        executeQueueContent(_subListToExecute);

        _cborGenerator.writeEndObject();
    }
}

class WriterFieldName extends Command {
    private String _name;

    public WriterFieldName(CBORGenerator generator, String name) {
        super(generator);
        this._name = name;
    }

    @Override
    public void execute() throws JsonGenerationException, IOException {
        _cborGenerator.writeFieldName(_name);
    }
}

class WriterStringStr extends Command {
    private String _text;

    public WriterStringStr(CBORGenerator generator, String text) {
        super(generator);
        this._text = text;
    }

    @Override
    public void execute() throws JsonGenerationException, IOException {
        _cborGenerator.writeString(_text);
    }
}

class WriterStringChar extends Command {
    private char[] _text;
    private int _offset;
    private int _len;

    public WriterStringChar(CBORGenerator generator, char[] text, int offset, int len) {
        super(generator);
        this._text = text;
        this._offset = offset;
        this._len = len;
    }

    @Override
    public void execute() throws JsonGenerationException, IOException {
        _cborGenerator.writeString(_text, _offset, _len);
    }
}

class WriterRawUTF8String extends Command {
    private byte[] _text;
    private int _offset;
    private int _len;

    public WriterRawUTF8String(CBORGenerator generator, byte[] text, int offset, int length) {
        super(generator);
        this._text = text;
        this._offset = offset;
        this._len = length;
    }

    @Override
    public void execute() throws JsonGenerationException, IOException {
        _cborGenerator.writeRawUTF8String(_text, _offset, _len);
    }
}

class WriterUTF8String extends Command {
    private byte[] _text;
    private int _offset;
    private int _len;

    public WriterUTF8String(CBORGenerator generator, byte[] text, int offset, int length) {
        super(generator);
        this._text = text;
        this._offset = offset;
        this._len = length;
    }

    @Override
    public void execute() throws JsonGenerationException, IOException {
        _cborGenerator.writeUTF8String(_text, _offset, _len);
    }
}

class WriterRaw extends Command {
    private String _text;

    public WriterRaw(CBORGenerator generator, String text) {
        super(generator);
        this._text = text;
    }

    @Override
    public void execute() throws JsonGenerationException, IOException {
        _cborGenerator.writeRaw(_text);
    }
}

class WriterRawSo extends Command {
    private String _text;
    private int _offset;
    private int _len;

    public WriterRawSo(CBORGenerator generator, String text, int offset, int length) {
        super(generator);
        this._text = text;
        this._offset = offset;
        this._len = length;
    }

    @Override
    public void execute() throws JsonGenerationException, IOException {
        _cborGenerator.writeRaw(_text, _offset, _len);
    }
}

class WriterRawCo extends Command {
    private char[] _text;
    private int _offset;
    private int _len;

    public WriterRawCo(CBORGenerator generator, char[] text, int offset, int length) {
        super(generator);
        this._text = text;
        this._offset = offset;
        this._len = length;
    }

    @Override
    public void execute() throws JsonGenerationException, IOException {
        _cborGenerator.writeRaw(_text, _offset, _len);
    }
}

class WriterRawC extends Command {
    private char _c;

    public WriterRawC(CBORGenerator generator, char c) {
        super(generator);
        this._c = c;
    }

    @Override
    public void execute() throws JsonGenerationException, IOException {
        _cborGenerator.writeRaw(_c);
    }
}

class WriterBinary extends Command {
    private Base64Variant _bv;
    private byte[] _data;
    private int _offset;
    private int _len;

    public WriterBinary(CBORGenerator generator, Base64Variant bv, byte[] data, int offset, int len) {
        super(generator);
        this._bv = bv;
        this._data = data;
        this._offset = offset;
        this._len = len;
    }

    @Override
    public void execute() throws JsonGenerationException, IOException {
        _cborGenerator.writeBinary(_bv, _data, _offset, _len);
    }
}

class WriterNumberInt extends Command {
    private int _number;

    public WriterNumberInt(CBORGenerator generator, int v) {
        super(generator);
        this._number = v;
    }

    @Override
    public void execute() throws JsonGenerationException, IOException {
        _cborGenerator.writeNumber(_number);
    }
}

class WriterNumberLong extends Command {
    private long _number;

    public WriterNumberLong(CBORGenerator generator, long v) {
        super(generator);
        this._number = v;
    }

    @Override
    public void execute() throws JsonGenerationException, IOException {
        _cborGenerator.writeNumber(_number);
    }
}

class WriterNumberBigInteger extends Command {
    private BigInteger _number;

    public WriterNumberBigInteger(CBORGenerator generator, BigInteger v) {
        super(generator);
        this._number = v;
    }

    @Override
    public void execute() throws JsonGenerationException, IOException {
        _cborGenerator.writeNumber(_number);
    }
}

class WriterNumberDouble extends Command {
    private double _number;

    public WriterNumberDouble(CBORGenerator generator, double v) {
        super(generator);
        this._number = v;
    }

    @Override
    public void execute() throws JsonGenerationException, IOException {
        _cborGenerator.writeNumber(_number);
    }
}

class WriterNumberFloat extends Command {
    private float _number;

    public WriterNumberFloat(CBORGenerator generator, float v) {
        super(generator);
        this._number = v;
    }

    @Override
    public void execute() throws JsonGenerationException, IOException {
        _cborGenerator.writeNumber(_number);
    }
}

class WriterNumberBigDecimal extends Command {
    private BigDecimal _number;

    public WriterNumberBigDecimal(CBORGenerator generator, BigDecimal v) {
        super(generator);
        this._number = v;
    }

    @Override
    public void execute() throws JsonGenerationException, IOException {
        _cborGenerator.writeNumber(_number);
    }
}

class WriterNumberString extends Command {
    private String _number;

    public WriterNumberString(CBORGenerator generator, String encodedValue) {
        super(generator);
        this._number = encodedValue;
    }

    @Override
    public void execute() throws JsonGenerationException, IOException {
        _cborGenerator.writeNumber(_number);
    }
}

class WriterBoolean extends Command {
    private boolean _state;

    public WriterBoolean(CBORGenerator generator, boolean state) {
        super(generator);
        this._state = state;
    }

    @Override
    public void execute() throws JsonGenerationException, IOException {
        _cborGenerator.writeBoolean(_state);
    }
}

class WriterNull extends Command {
    public WriterNull(CBORGenerator generator) {
        super(generator);
    }

    @Override
    public void execute() throws JsonGenerationException, IOException {
        _cborGenerator.writeNull();
    }
}

class WriterTag extends Command {
    private int _tag;

    public WriterTag(CBORGenerator generator, int tag) {
        super(generator);
        this._tag = tag;
    }

    @Override
    public void execute() throws JsonGenerationException, IOException {
        _cborGenerator.writeTag(_tag);
    }
}
