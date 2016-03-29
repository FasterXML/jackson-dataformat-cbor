package com.fasterxml.jackson.dataformat.cbor.sizer;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Stack;

import com.fasterxml.jackson.core.Base64Variant;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.base.GeneratorBase;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.dataformat.cbor.CBORGenerator;

/**
 * 
 * Implementation of JsonGenerator which permits to use definite size array amd
 * maps when it is possible. To do so, the data is encoded when all the array or
 * map content is known. This class should not be used in the cases where the
 * memory efficiency is a priority.
 */
public class CBORGeneratorSizer extends GeneratorBase {
    /**
     * This class decorate CBORGenerator. All the writing methods are wrapped
     */
    private CBORGenerator _cborGenerator;

    /**
     * For arrays and map, the data is stored in these lists.
     */
    private Stack<Queue<Command>> _commandsQueueStack;
    private Queue<Command> _commandsQueue;

    /**
     * The constructor initializes the wrapped CBORGenerator.
     * 
     * @param ctxt
     * @param stdFeatures
     * @param formatFeatures
     * @param codec
     * @param out
     */
    public CBORGeneratorSizer(IOContext ctxt, int stdFeatures, int formatFeatures, ObjectCodec codec,
            OutputStream out) {
        super(stdFeatures, codec);
        _cborGenerator = new CBORGenerator(ctxt, stdFeatures, formatFeatures, codec, out);
    }

    private void enqueue(Command cmd) {
        _commandsQueue.add(cmd);
    }

    private void clearQueue() {
        _commandsQueue.clear();
    }

    /**
     * The queuing is activated only for the map and arrays
     */
    private boolean isQueuingIsEnabled() {
        return (_commandsQueueStack != null);
    }

    private void createObjectContext() {
        if (_commandsQueueStack == null) {
            _commandsQueueStack = new Stack<Queue<Command>>();
        }
        if (_commandsQueue != null && !_commandsQueue.isEmpty()) {
            _commandsQueueStack.add(_commandsQueue);
        }
        _commandsQueue = new LinkedList<Command>();
    }

    @Override
    public void flush() throws IOException {
        _cborGenerator.flush();
    }

    @Override
    /**
     * The array is opened only at the end when the number of object is known.
     * The queuing is activated and the current list of data is pushed in the
     * queue list.
     */
    public void writeStartArray() throws IOException {
        createObjectContext();
    }

    @Override
    /**
     * The content of the last array is added to the list of element to write.
     * If this is the last end, all the stored commands are executed
     */
    public void writeEndArray() throws IOException {
        Command arraySubCommand = new ExecuterOfArraySubCommands(_cborGenerator, _commandsQueue);
        if (!_commandsQueueStack.empty()) {
            _commandsQueue = _commandsQueueStack.pop();
            _commandsQueue.add(arraySubCommand);
        } else {
            arraySubCommand.execute();
        }
    }

    @Override
    /**
     * The map is opened only at the end when the number of object is known. The
     * queuing is activated and the current list of data is pushed in the queue
     * list.
     */
    public void writeStartObject() throws IOException {
        createObjectContext();
    }

    @Override
    /**
     * The content of the map array is added to the list of element to write If
     * this is the last end, all the stored commands are executed
     */
    public void writeEndObject() throws IOException {
        Command objectSubCommand = new ExecuterOfObjectSubCommands(_cborGenerator, _commandsQueue);
        if (!_commandsQueueStack.empty()) {
            _commandsQueue = _commandsQueueStack.pop();
            _commandsQueue.add(objectSubCommand);
        } else {
            objectSubCommand.execute();
        }
    }

    /**
     * Wrapped methods: if the queuing is activated, the commands are stored and
     * will be executed on the last map or array end.
     */

    @Override
    public void writeFieldName(String name) throws IOException {
        if (isQueuingIsEnabled()) {
            enqueue(new WriterFieldName(this._cborGenerator, name));
        } else {
            _cborGenerator.writeFieldName(name);
        }
    }

    @Override
    public void writeString(String text) throws IOException {
        if (isQueuingIsEnabled()) {
            enqueue(new WriterStringStr(this._cborGenerator, text));
        } else {
            _cborGenerator.writeString(text);
        }
    }

    @Override
    public void writeString(char[] text, int offset, int len) throws IOException {
        if (isQueuingIsEnabled()) {
            enqueue(new WriterStringChar(this._cborGenerator, text, offset, len));
        } else {
            _cborGenerator.writeString(text, offset, len);
        }
    }

    @Override
    public void writeRawUTF8String(byte[] text, int offset, int length) throws IOException {
        if (isQueuingIsEnabled()) {
            enqueue(new WriterRawUTF8String(this._cborGenerator, text, offset, length));
        } else {
            _cborGenerator.writeRawUTF8String(text, offset, length);
        }
    }

    @Override
    public void writeUTF8String(byte[] text, int offset, int length) throws IOException {
        if (isQueuingIsEnabled()) {
            enqueue(new WriterUTF8String(this._cborGenerator, text, offset, length));
        } else {
            _cborGenerator.writeUTF8String(text, offset, length);
        }
    }

    @Override
    public void writeRaw(String text) throws IOException {
        if (isQueuingIsEnabled()) {
            enqueue(new WriterRaw(this._cborGenerator, text));
        } else {
            _cborGenerator.writeRaw(text);
        }
    }

    @Override
    public void writeRaw(String text, int offset, int len) throws IOException {
        if (isQueuingIsEnabled()) {
            enqueue(new WriterRawSo(this._cborGenerator, text, offset, len));
        } else {
            _cborGenerator.writeRaw(text, offset, len);
        }
    }

    @Override
    public void writeRaw(char[] text, int offset, int len) throws IOException {
        if (isQueuingIsEnabled()) {
            enqueue(new WriterRawCo(this._cborGenerator, text, offset, len));
        } else {
            _cborGenerator.writeRaw(text, offset, len);
        }
    }

    @Override
    public void writeRaw(char c) throws IOException {
        if (isQueuingIsEnabled()) {
            enqueue(new WriterRawC(this._cborGenerator, c));
        } else {
            _cborGenerator.writeRaw(c);
        }
    }

    @Override
    public void writeBinary(Base64Variant bv, byte[] data, int offset, int len) throws IOException {
        if (isQueuingIsEnabled()) {
            enqueue(new WriterBinary(this._cborGenerator, bv, data, offset, len));
        } else {
            _cborGenerator.writeBinary(bv, data, offset, len);
        }
    }

    @Override
    public void writeNumber(int v) throws IOException {
        if (isQueuingIsEnabled()) {
            enqueue(new WriterNumberInt(_cborGenerator, v));
        } else {
            _cborGenerator.writeNumber(v);
        }
    }

    @Override
    public void writeNumber(long v) throws IOException {
        if (isQueuingIsEnabled()) {
            enqueue(new WriterNumberLong(_cborGenerator, v));
        } else {
            _cborGenerator.writeNumber(v);
        }
    }

    @Override
    public void writeNumber(BigInteger v) throws IOException {
        if (isQueuingIsEnabled()) {
            enqueue(new WriterNumberBigInteger(_cborGenerator, v));
        } else {
            _cborGenerator.writeNumber(v);
        }
    }

    @Override
    public void writeNumber(double v) throws IOException {
        if (isQueuingIsEnabled()) {
            enqueue(new WriterNumberDouble(_cborGenerator, v));
        } else {
            _cborGenerator.writeNumber(v);
        }
    }

    @Override
    public void writeNumber(float v) throws IOException {
        if (isQueuingIsEnabled()) {
            enqueue(new WriterNumberFloat(_cborGenerator, v));
        } else {
            _cborGenerator.writeNumber(v);
        }
    }

    @Override
    public void writeNumber(BigDecimal v) throws IOException {
        if (isQueuingIsEnabled()) {
            enqueue(new WriterNumberBigDecimal(_cborGenerator, v));
        } else {
            _cborGenerator.writeNumber(v);
        }
    }

    @Override
    public void writeNumber(String encodedValue) throws IOException {
        if (isQueuingIsEnabled()) {
            enqueue(new WriterNumberString(_cborGenerator, encodedValue));
        } else {
            _cborGenerator.writeNumber(encodedValue);
        }
    }

    @Override
    public void writeBoolean(boolean state) throws IOException {
        if (isQueuingIsEnabled()) {
            enqueue(new WriterBoolean(_cborGenerator, state));
        } else {
            _cborGenerator.writeBoolean(state);
        }
    }

    public void writeTag(int tagId) throws IOException {
        if (isQueuingIsEnabled()) {
            enqueue(new WriterTag(_cborGenerator, tagId));
        } else {
            _cborGenerator.writeTag(tagId);
        }
    }

    @Override
    public void writeNull() throws IOException {
        if (isQueuingIsEnabled()) {
            enqueue(new WriterNull(_cborGenerator));
        } else {
            _cborGenerator.writeNull();
        }
    }

    @Override
    public void close() throws IOException {
        _cborGenerator.close();
    }

    @Override
    protected void _releaseBuffers() {
    }

    @Override
    protected void _verifyValueWrite(String typeMsg) throws IOException {
    }
}
