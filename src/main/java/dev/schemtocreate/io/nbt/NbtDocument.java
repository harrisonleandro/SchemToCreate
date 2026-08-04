package dev.schemtocreate.io.nbt;

import dev.schemtocreate.io.ByteSource;
import dev.schemtocreate.util.BoundedInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * An NBT file parsed into a tag tree, except that byte arrays above a size threshold are
 * recorded as <em>locations</em> rather than contents.
 *
 * <p>This is the mechanism behind low-memory conversion. A schematic's bulk block data is a
 * single {@code TAG_Byte_Array} that can run to hundreds of megabytes; parsing it into a
 * tree would dominate memory use and cap the tool at whatever fits in the heap. Instead the
 * first pass notes the array's offset within the decompressed stream and skips over it, and
 * {@link #byteSource} hands out a stream that re-inflates the file up to that offset on
 * demand. Small arrays are kept inline, so ordinary schematics never pay for a second pass.
 *
 * <p>Paths are {@code /}-separated compound keys; list elements append {@code #index}.
 */
public final class NbtDocument {

    /** Byte arrays at or below this size are held in memory. */
    public static final long DEFAULT_INLINE_THRESHOLD = 32L * 1024 * 1024;

    private record Location(long offset, int length) {
    }

    private final Path source;
    private final String rootName;
    private final NbtCompound root;
    private final Map<String, Location> deferred;

    private NbtDocument(Path source, String rootName, NbtCompound root, Map<String, Location> deferred) {
        this.source = source;
        this.rootName = rootName;
        this.root = root;
        this.deferred = deferred;
    }

    public static NbtDocument load(Path source) throws IOException {
        return load(source, DEFAULT_INLINE_THRESHOLD, NbtLimits.DEFAULT);
    }

    public static NbtDocument load(Path source, long inlineThreshold, NbtLimits limits) throws IOException {
        try (NbtReader reader = new NbtReader(NbtIo.openDecompressed(source), limits)) {
            Loader loader = new Loader(reader, inlineThreshold);
            byte type = reader.readTypeId();
            if (type != NbtType.COMPOUND) {
                throw new NbtFormatException("Root tag is " + NbtType.name(type) + ", expected TAG_Compound");
            }
            String name = reader.readName();
            NbtCompound tree = loader.readCompound("");
            return new NbtDocument(source, name, tree, loader.deferred);
        }
    }

    /** Name of the file's root tag — {@code "Schematic"} for Sponge v1/v2, empty for v3. */
    public String rootName() {
        return rootName;
    }

    public NbtCompound root() {
        return root;
    }

    /** True if the byte array at {@code path} was skipped and must be streamed. */
    public boolean isDeferred(String path) {
        return deferred.containsKey(path);
    }

    /**
     * A {@link ByteSource} for the byte array at {@code path}, whether it was kept inline or
     * deferred. Returns {@code null} when no such array exists.
     */
    public ByteSource byteSource(String path) {
        Location location = deferred.get(path);
        if (location != null) {
            return new DeferredByteSource(source, location);
        }
        byte[] inline = resolveInline(path);
        return inline == null ? null : ByteSource.ofArray(inline);
    }

    private byte[] resolveInline(String path) {
        NbtTag current = root;
        for (String segment : path.split("/")) {
            if (segment.isEmpty()) {
                continue;
            }
            current = descend(current, segment);
            if (current == null) {
                return null;
            }
        }
        return current instanceof NbtByteArray array ? array.value() : null;
    }

    private static NbtTag descend(NbtTag current, String segment) {
        int hash = segment.indexOf('#');
        if (hash >= 0) {
            NbtTag container = descend(current, segment.substring(0, hash));
            int index = Integer.parseInt(segment.substring(hash + 1));
            return container instanceof NbtList list && index < list.size() ? list.get(index) : null;
        }
        return current instanceof NbtCompound compound ? compound.get(segment) : null;
    }

    /** Re-inflates the source file and exposes exactly the recorded region. */
    private record DeferredByteSource(Path source, Location location) implements ByteSource {

        @Override
        public long length() {
            return location.length();
        }

        @Override
        public InputStream open() throws IOException {
            InputStream stream = NbtIo.openDecompressed(source);
            try {
                stream.skipNBytes(location.offset());
            } catch (IOException | RuntimeException e) {
                stream.close();
                throw e;
            }
            return new BoundedInputStream(stream, location.length());
        }
    }

    /** Walks the file once, materialising everything except oversized byte arrays. */
    private static final class Loader {

        private final NbtReader reader;
        private final long inlineThreshold;
        private final Map<String, Location> deferred = new HashMap<>();

        Loader(NbtReader reader, long inlineThreshold) {
            this.reader = reader;
            this.inlineThreshold = inlineThreshold;
        }

        NbtCompound readCompound(String path) throws IOException {
            NbtCompound compound = new NbtCompound();
            byte type;
            while ((type = reader.readTypeId()) != NbtType.END) {
                String key = reader.readName();
                compound.put(key, readValue(type, path.isEmpty() ? key : path + "/" + key));
            }
            return compound;
        }

        private NbtTag readValue(byte type, String path) throws IOException {
            return switch (type) {
                case NbtType.COMPOUND -> readCompound(path);
                case NbtType.LIST -> readList(path);
                case NbtType.BYTE_ARRAY -> readByteArray(path);
                default -> reader.readPayload(type);
            };
        }

        private NbtTag readList(String path) throws IOException {
            byte elementType = reader.readByteValue();
            int size = reader.readIntValue();
            NbtList list = new NbtList();
            if (size <= 0) {
                return list;
            }
            boolean nested = elementType == NbtType.COMPOUND
                    || elementType == NbtType.LIST
                    || elementType == NbtType.BYTE_ARRAY;
            for (int i = 0; i < size; i++) {
                list.add(nested
                        ? readValue(elementType, path + "#" + i)
                        : reader.readPayload(elementType));
            }
            return list;
        }

        private NbtTag readByteArray(String path) throws IOException {
            int length = reader.readIntValue();
            if (length < 0) {
                throw new NbtFormatException("Negative byte array length " + length + " at " + path);
            }
            if (length <= inlineThreshold) {
                byte[] data = new byte[length];
                reader.readFully(data);
                return new NbtByteArray(data);
            }
            deferred.put(path, new Location(reader.bytesConsumed(), length));
            reader.skipBytes(length);
            // The placeholder keeps the tree structurally faithful; contents come from
            // byteSource(path).
            return new NbtByteArray(new byte[0]);
        }
    }
}
