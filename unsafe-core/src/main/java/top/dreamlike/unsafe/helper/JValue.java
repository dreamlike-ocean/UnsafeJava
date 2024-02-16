package top.dreamlike.unsafe.helper;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;

public class JValue {

    final long nativeJvalue;

    private final MemorySegment heapSegment;

    public JValue(long returnValue) {
        this.nativeJvalue = returnValue;
        this.heapSegment = MemorySegment.ofArray(new long[1]);
        heapSegment.set(ValueLayout.JAVA_LONG, 0, returnValue);
    }

    public boolean getBoolean() {
        return (boolean) jbooleanVarhandle.get(heapSegment);
    }

    public byte getByte() {
        return (byte) jbyteVarhandle.get(heapSegment);
    }

    public char getChar() {
        return (char) jcharVarhandle.get(heapSegment);
    }

    public short getShort() {
        return (short) jshortVarhandle.get(heapSegment);
    }

    public int getInt() {
        return (int) jintVarhandle.get(heapSegment);
    }

    public long getLong() {
        return (long) jlongVarhandle.get(heapSegment);
    }

    public float getFloat() {
        return (float) jfloatVarhandle.get(heapSegment);
    }

    public double getDouble() {
        return (double) jdoubleVarhandle.get(heapSegment);
    }

    public MemorySegment getObject() {
        return (MemorySegment) jobjectVarhandle.get(heapSegment);
    }

    public MemorySegment toPtr() {
        return MemorySegment.ofAddress(nativeJvalue);
    }

    public static final MemoryLayout jvalueLayout = MemoryLayout.unionLayout(
            ValueLayout.JAVA_BOOLEAN.withName("z"),
            ValueLayout.JAVA_BYTE.withName("b"),
            ValueLayout.JAVA_CHAR.withName("c"),
            ValueLayout.JAVA_SHORT.withName("s"),
            ValueLayout.JAVA_INT.withName("i"),
            ValueLayout.JAVA_LONG.withName("j"),
            ValueLayout.JAVA_FLOAT.withName("f"),
            ValueLayout.JAVA_DOUBLE.withName("d"),
            ValueLayout.ADDRESS.withName("l")
    );

    public static final VarHandle jbooleanVarhandle = jvalueLayout.varHandle(MemoryLayout.PathElement.groupElement("z"));
    public static final VarHandle jbyteVarhandle = jvalueLayout.varHandle(MemoryLayout.PathElement.groupElement("b"));
    public static final VarHandle jcharVarhandle = jvalueLayout.varHandle(MemoryLayout.PathElement.groupElement("c"));
    public static final VarHandle jshortVarhandle = jvalueLayout.varHandle(MemoryLayout.PathElement.groupElement("s"));
    public static final VarHandle jintVarhandle = jvalueLayout.varHandle(MemoryLayout.PathElement.groupElement("i"));
    public static final VarHandle jlongVarhandle = jvalueLayout.varHandle(MemoryLayout.PathElement.groupElement("j"));
    public static final VarHandle jfloatVarhandle = jvalueLayout.varHandle(MemoryLayout.PathElement.groupElement("f"));
    public static final VarHandle jdoubleVarhandle = jvalueLayout.varHandle(MemoryLayout.PathElement.groupElement("d"));
    public static final VarHandle jobjectVarhandle = jvalueLayout.varHandle(MemoryLayout.PathElement.groupElement("l"));


}
