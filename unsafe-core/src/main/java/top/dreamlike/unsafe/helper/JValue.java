package top.dreamlike.unsafe.helper;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;

public class JValue {

    private final MemorySegment nativeJvalue;

    public JValue(MemorySegment nativeJvalue) {
        this.nativeJvalue = nativeJvalue;
    }

    public boolean getBoolean() {
        return (boolean) jbooleanVarhandle.get(nativeJvalue);
    }

    public byte getByte() {
        return (byte) jbyteVarhandle.get(nativeJvalue);
    }

    public char getChar() {
        return (char) jcharVarhandle.get(nativeJvalue);
    }

    public short getShort() {
        return (short) jshortVarhandle.get(nativeJvalue);
    }

    public int getInt() {
        return (int) jintVarhandle.get(nativeJvalue);
    }

    public long getLong() {
        return (long) jlongVarhandle.get(nativeJvalue);
    }

    public float getFloat() {
        return (float) jfloatVarhandle.get(nativeJvalue);
    }

    public double getDouble() {
        return (double) jdoubleVarhandle.get(nativeJvalue);
    }

    public MemorySegment getObject() {
        return (MemorySegment) jobjectVarhandle.get(nativeJvalue);
    }

    public static final MemoryLayout Nativejvalue = MemoryLayout.unionLayout(
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

    private static final VarHandle jbooleanVarhandle = Nativejvalue.varHandle(MemoryLayout.PathElement.groupElement("z"));
    private static final VarHandle jbyteVarhandle = Nativejvalue.varHandle(MemoryLayout.PathElement.groupElement("b"));
    private static final VarHandle jcharVarhandle = Nativejvalue.varHandle(MemoryLayout.PathElement.groupElement("c"));
    private static final VarHandle jshortVarhandle = Nativejvalue.varHandle(MemoryLayout.PathElement.groupElement("s"));
    private static final VarHandle jintVarhandle = Nativejvalue.varHandle(MemoryLayout.PathElement.groupElement("i"));
    private static final VarHandle jlongVarhandle = Nativejvalue.varHandle(MemoryLayout.PathElement.groupElement("j"));
    private static final VarHandle jfloatVarhandle = Nativejvalue.varHandle(MemoryLayout.PathElement.groupElement("f"));
    private static final VarHandle jdoubleVarhandle = Nativejvalue.varHandle(MemoryLayout.PathElement.groupElement("d"));
    private static final VarHandle jobjectVarhandle = Nativejvalue.varHandle(MemoryLayout.PathElement.groupElement("l"));


}
