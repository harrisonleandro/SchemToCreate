package dev.schemtocreate.io.nbt;

public record NbtByte(byte value) implements NbtTag {

    public static final NbtByte ZERO = new NbtByte((byte) 0);
    public static final NbtByte ONE = new NbtByte((byte) 1);

    public static NbtByte of(boolean flag) {
        return flag ? ONE : ZERO;
    }

    @Override
    public byte typeId() {
        return NbtType.BYTE;
    }

    @Override
    public NbtByte copy() {
        return this;
    }
}
