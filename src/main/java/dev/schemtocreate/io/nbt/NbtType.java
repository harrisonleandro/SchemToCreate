package dev.schemtocreate.io.nbt;

/** NBT tag type identifiers, as defined by the original Notchian specification. */
public final class NbtType {

    public static final byte END = 0;
    public static final byte BYTE = 1;
    public static final byte SHORT = 2;
    public static final byte INT = 3;
    public static final byte LONG = 4;
    public static final byte FLOAT = 5;
    public static final byte DOUBLE = 6;
    public static final byte BYTE_ARRAY = 7;
    public static final byte STRING = 8;
    public static final byte LIST = 9;
    public static final byte COMPOUND = 10;
    public static final byte INT_ARRAY = 11;
    public static final byte LONG_ARRAY = 12;

    private static final String[] NAMES = {
            "TAG_End", "TAG_Byte", "TAG_Short", "TAG_Int", "TAG_Long", "TAG_Float",
            "TAG_Double", "TAG_Byte_Array", "TAG_String", "TAG_List", "TAG_Compound",
            "TAG_Int_Array", "TAG_Long_Array"
    };

    private NbtType() {
    }

    public static String name(byte id) {
        return id >= 0 && id < NAMES.length ? NAMES[id] : "TAG_Unknown(" + id + ")";
    }

    public static boolean isValid(byte id) {
        return id >= END && id <= LONG_ARRAY;
    }
}
