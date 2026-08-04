package dev.schemtocreate.io.nbt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NbtCodecTest {

    @TempDir
    Path temp;

    @Test
    @DisplayName("every tag type survives a write/read round trip")
    void roundTripsAllTagTypes() throws IOException {
        NbtCompound root = new NbtCompound()
                .putByte("byte", (byte) -7)
                .putShort("short", (short) 30000)
                .putInt("int", Integer.MIN_VALUE)
                .putLong("long", Long.MAX_VALUE)
                .putDouble("double", Math.PI)
                .putString("string", "olá mundo ☃")
                .putIntArray("intArray", 1, -2, 3);
        root.put("float", new NbtFloat(1.5f));
        root.put("byteArray", new NbtByteArray(new byte[]{1, 2, 3, 4}));
        root.put("longArray", new NbtLongArray(1L, -1L));
        root.put("list", NbtList.ofDoubles(1.0, 2.0, 3.0));
        root.put("nested", new NbtCompound().putString("inner", "value"));

        Path file = temp.resolve("all-types.nbt");
        NbtIo.writeCompressed(file, "", root);

        assertThat(NbtIo.isGzipped(file)).isTrue();
        NamedTag read = NbtIo.read(file);
        assertThat(read.name()).isEmpty();
        assertThat(read.requireCompound()).isEqualTo(root);
    }

    @Test
    @DisplayName("an empty list is written with element type TAG_End, as vanilla does")
    void emptyListUsesEndElementType() throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (NbtWriter writer = new NbtWriter(buffer)) {
            writer.beginRootCompound("");
            writer.beginList("entities", NbtType.COMPOUND, 0);
            writer.endCompound();
        }

        NbtList entities = readRoot(buffer).getList("entities");
        assertThat(entities).isNotNull();
        assertThat(entities.isEmpty()).isTrue();
        assertThat(entities.elementType()).isEqualTo(NbtType.END);
    }

    @Test
    @DisplayName("a list of compounds can be streamed element by element")
    void streamsListElements() throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (NbtWriter writer = new NbtWriter(buffer)) {
            writer.beginRootCompound("");
            writer.beginList("blocks", NbtType.COMPOUND, 3);
            for (int i = 0; i < 3; i++) {
                writer.beginList("pos", NbtType.INT, 3);
                writer.payloadInt(i);
                writer.payloadInt(i * 2);
                writer.payloadInt(i * 3);
                writer.putInt("state", i);
                writer.endCompound();
            }
            writer.endCompound();
        }

        NbtList blocks = readRoot(buffer).getList("blocks");
        assertThat(blocks.size()).isEqualTo(3);
        NbtCompound third = (NbtCompound) blocks.get(2);
        assertThat(third.getInt("state", -1)).isEqualTo(2);
        assertThat(third.getIntTriple("pos")).containsExactly(2, 4, 6);
    }

    @Test
    @DisplayName("uncompressed input is detected and read without a GZIP wrapper")
    void readsUncompressedFiles() throws IOException {
        Path file = temp.resolve("plain.nbt");
        NbtIo.writeUncompressed(file, "Schematic", new NbtCompound().putInt("Version", 2));

        assertThat(NbtIo.isGzipped(file)).isFalse();
        NamedTag read = NbtIo.read(file);
        assertThat(read.name()).isEqualTo("Schematic");
        assertThat(read.requireCompound().getInt("Version", -1)).isEqualTo(2);
    }

    @Test
    @DisplayName("a truncated stream fails instead of returning partial data")
    void rejectsTruncatedInput() throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (NbtWriter writer = new NbtWriter(buffer)) {
            writer.beginRootCompound("");
            writer.putString("value", "a reasonably long string that will be cut off");
            writer.endCompound();
        }
        byte[] truncated = new byte[buffer.size() - 10];
        System.arraycopy(buffer.toByteArray(), 0, truncated, 0, truncated.length);

        try (NbtReader reader = new NbtReader(new ByteArrayInputStream(truncated))) {
            assertThatThrownBy(reader::readRoot).isInstanceOf(EOFException.class);
        }
    }

    @Test
    @DisplayName("an implausible array length is rejected before allocating")
    void rejectsOversizedArrays() {
        // TAG_Compound "" { TAG_Byte_Array "a" of declared length 0x7FFFFFF0 }
        byte[] bomb = {10, 0, 0, 7, 0, 1, 'a', 0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xF0};
        NbtLimits tight = new NbtLimits(64, 1024);

        try (NbtReader reader = new NbtReader(new ByteArrayInputStream(bomb), tight)) {
            assertThatThrownBy(reader::readRoot)
                    .isInstanceOf(NbtFormatException.class)
                    .hasMessageContaining("exceeds the limit");
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    @DisplayName("a large byte array is skipped on the first pass and streamed on demand")
    void defersLargeByteArrays() throws IOException {
        byte[] payload = new byte[8192];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (i % 251);
        }
        Path file = temp.resolve("deferred.nbt");
        NbtCompound root = new NbtCompound()
                .putString("before", "header")
                .put("Blocks", new NbtCompound().put("Data", new NbtByteArray(payload)));
        root.putString("after", "trailer");
        NbtIo.writeCompressed(file, "", root);

        NbtDocument document = NbtDocument.load(file, 1024, NbtLimits.DEFAULT);

        assertThat(document.isDeferred("Blocks/Data")).isTrue();
        assertThat(document.root().getString("after", null)).isEqualTo("trailer");
        assertThat(document.byteSource("Blocks/Data").open().readAllBytes()).isEqualTo(payload);
    }

    @Test
    @DisplayName("a small byte array stays inline and needs no second pass")
    void keepsSmallByteArraysInline() throws IOException {
        byte[] payload = {9, 8, 7};
        Path file = temp.resolve("inline.nbt");
        NbtIo.writeCompressed(file, "", new NbtCompound()
                .put("Blocks", new NbtCompound().put("Data", new NbtByteArray(payload))));

        NbtDocument document = NbtDocument.load(file, 1024, NbtLimits.DEFAULT);

        assertThat(document.isDeferred("Blocks/Data")).isFalse();
        assertThat(document.byteSource("Blocks/Data").open().readAllBytes()).isEqualTo(payload);
    }

    @Test
    @DisplayName("deferred and inline paths yield identical bytes for the same file")
    void deferredAndInlineAgree() throws IOException {
        byte[] payload = new byte[64 * 1024];
        new java.util.Random(42).nextBytes(payload);
        Path file = temp.resolve("agree.nbt");
        NbtIo.writeCompressed(file, "", new NbtCompound()
                .putLong("padding", 1234567890L)
                .put("Blocks", new NbtCompound().put("Data", new NbtByteArray(payload))));

        byte[] streamed = NbtDocument.load(file, 16, NbtLimits.DEFAULT)
                .byteSource("Blocks/Data").open().readAllBytes();
        byte[] buffered = NbtDocument.load(file, Long.MAX_VALUE, NbtLimits.DEFAULT)
                .byteSource("Blocks/Data").open().readAllBytes();

        assertThat(streamed).isEqualTo(buffered).isEqualTo(payload);
    }

    @Test
    @DisplayName("compression level changes the file size but not its contents")
    void compressionLevelAffectsSizeOnly() throws IOException {
        NbtCompound root = new NbtCompound();
        for (int i = 0; i < 500; i++) {
            root.putString("key" + i, "a repetitive value that compresses well");
        }
        Path fast = writeAtLevel(root, temp.resolve("fast.nbt"), 1);
        Path best = writeAtLevel(root, temp.resolve("best.nbt"), 9);

        assertThat(Files.size(best)).isLessThanOrEqualTo(Files.size(fast));
        assertThat(NbtIo.read(best).requireCompound()).isEqualTo(NbtIo.read(fast).requireCompound());
    }

    private static Path writeAtLevel(NbtCompound root, Path target, int level) throws IOException {
        try (var sink = NbtIo.gzipSink(Files.newOutputStream(target), level);
             NbtWriter writer = new NbtWriter(sink)) {
            writer.beginRootCompound("");
            writer.putAll(root);
            writer.endCompound();
        }
        return target;
    }

    private static NbtCompound readRoot(ByteArrayOutputStream buffer) throws IOException {
        try (NbtReader reader = new NbtReader(new ByteArrayInputStream(buffer.toByteArray()))) {
            return reader.readRoot().requireCompound();
        }
    }
}
