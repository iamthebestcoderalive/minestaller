package com.minestaller.model;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class Nbt {

    public static abstract class Tag {
        public abstract byte getId();
        public abstract void write(DataOutput out) throws IOException;
    }

    public static class TagEnd extends Tag {
        @Override
        public byte getId() { return 0; }
        @Override
        public void write(DataOutput out) throws IOException {}
    }

    public static class TagByte extends Tag {
        public byte value;
        public TagByte(byte value) { this.value = value; }
        @Override
        public byte getId() { return 1; }
        @Override
        public void write(DataOutput out) throws IOException {
            out.writeByte(value);
        }
    }

    public static class TagShort extends Tag {
        public short value;
        public TagShort(short value) { this.value = value; }
        @Override
        public byte getId() { return 2; }
        @Override
        public void write(DataOutput out) throws IOException {
            out.writeShort(value);
        }
    }

    public static class TagInt extends Tag {
        public int value;
        public TagInt(int value) { this.value = value; }
        @Override
        public byte getId() { return 3; }
        @Override
        public void write(DataOutput out) throws IOException {
            out.writeInt(value);
        }
    }

    public static class TagLong extends Tag {
        public long value;
        public TagLong(long value) { this.value = value; }
        @Override
        public byte getId() { return 4; }
        @Override
        public void write(DataOutput out) throws IOException {
            out.writeLong(value);
        }
    }

    public static class TagFloat extends Tag {
        public float value;
        public TagFloat(float value) { this.value = value; }
        @Override
        public byte getId() { return 5; }
        @Override
        public void write(DataOutput out) throws IOException {
            out.writeFloat(value);
        }
    }

    public static class TagDouble extends Tag {
        public double value;
        public TagDouble(double value) { this.value = value; }
        @Override
        public byte getId() { return 6; }
        @Override
        public void write(DataOutput out) throws IOException {
            out.writeDouble(value);
        }
    }

    public static class TagByteArray extends Tag {
        public byte[] value;
        public TagByteArray(byte[] value) { this.value = value; }
        @Override
        public byte getId() { return 7; }
        @Override
        public void write(DataOutput out) throws IOException {
            out.writeInt(value.length);
            out.write(value);
        }
    }

    public static class TagString extends Tag {
        public String value;
        public TagString(String value) { this.value = value; }
        @Override
        public byte getId() { return 8; }
        @Override
        public void write(DataOutput out) throws IOException {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            out.writeShort(bytes.length);
            out.write(bytes);
        }
    }

    public static class TagList extends Tag {
        public byte elementType;
        public List<Tag> elements;
        public TagList(byte elementType, List<Tag> elements) {
            this.elementType = elementType;
            this.elements = elements;
        }
        @Override
        public byte getId() { return 9; }
        @Override
        public void write(DataOutput out) throws IOException {
            out.writeByte(elementType);
            out.writeInt(elements.size());
            for (Tag t : elements) {
                t.write(out);
            }
        }
    }

    public static class TagCompound extends Tag {
        public Map<String, Tag> tags;
        public TagCompound() {
            this.tags = new LinkedHashMap<>();
        }
        public TagCompound(Map<String, Tag> tags) {
            this.tags = tags;
        }
        @Override
        public byte getId() { return 10; }
        @Override
        public void write(DataOutput out) throws IOException {
            for (Map.Entry<String, Tag> entry : tags.entrySet()) {
                out.writeByte(entry.getValue().getId());
                byte[] nameBytes = entry.getKey().getBytes(StandardCharsets.UTF_8);
                out.writeShort(nameBytes.length);
                out.write(nameBytes);
                entry.getValue().write(out);
            }
            out.writeByte(0); // TagEnd
        }

        public Tag get(String key) {
            return tags.get(key);
        }
        public void put(String key, Tag tag) {
            tags.put(key, tag);
        }

        public String getString(String key, String def) {
            Tag t = get(key);
            return (t instanceof TagString) ? ((TagString) t).value : def;
        }
        public int getInt(String key, int def) {
            Tag t = get(key);
            return (t instanceof TagInt) ? ((TagInt) t).value : def;
        }
        public byte getByte(String key, byte def) {
            Tag t = get(key);
            return (t instanceof TagByte) ? ((TagByte) t).value : def;
        }
        public void putString(String key, String value) {
            put(key, new TagString(value));
        }
        public void putInt(String key, int value) {
            put(key, new TagInt(value));
        }
        public void putByte(String key, byte value) {
            put(key, new TagByte(value));
        }
    }

    public static class TagIntArray extends Tag {
        public int[] value;
        public TagIntArray(int[] value) { this.value = value; }
        @Override
        public byte getId() { return 11; }
        @Override
        public void write(DataOutput out) throws IOException {
            out.writeInt(value.length);
            for (int v : value) {
                out.writeInt(v);
            }
        }
    }

    public static class TagLongArray extends Tag {
        public long[] value;
        public TagLongArray(long[] value) { this.value = value; }
        @Override
        public byte getId() { return 12; }
        @Override
        public void write(DataOutput out) throws IOException {
            out.writeInt(value.length);
            for (long v : value) {
                out.writeLong(v);
            }
        }
    }

    public static class NamedTag {
        public String name;
        public TagCompound tag;
        public NamedTag(String name, TagCompound tag) {
            this.name = name;
            this.tag = tag;
        }
    }

    public static Tag readTagPayload(byte id, DataInput in) throws IOException {
        switch (id) {
            case 1: return new TagByte(in.readByte());
            case 2: return new TagShort(in.readShort());
            case 3: return new TagInt(in.readInt());
            case 4: return new TagLong(in.readLong());
            case 5: return new TagFloat(in.readFloat());
            case 6: return new TagDouble(in.readDouble());
            case 7: {
                int len = in.readInt();
                byte[] bytes = new byte[len];
                in.readFully(bytes);
                return new TagByteArray(bytes);
            }
            case 8: {
                int len = in.readUnsignedShort();
                byte[] bytes = new byte[len];
                in.readFully(bytes);
                return new TagString(new String(bytes, StandardCharsets.UTF_8));
            }
            case 9: {
                byte elementId = in.readByte();
                int len = in.readInt();
                List<Tag> list = new ArrayList<>(len);
                for (int i = 0; i < len; i++) {
                    list.add(readTagPayload(elementId, in));
                }
                return new TagList(elementId, list);
            }
            case 10: {
                Map<String, Tag> tags = new LinkedHashMap<>();
                while (true) {
                    byte type = in.readByte();
                    if (type == 0) break;
                    int nameLen = in.readUnsignedShort();
                    byte[] nameBytes = new byte[nameLen];
                    in.readFully(nameBytes);
                    String name = new String(nameBytes, StandardCharsets.UTF_8);
                    Tag tag = readTagPayload(type, in);
                    tags.put(name, tag);
                }
                return new TagCompound(tags);
            }
            case 11: {
                int len = in.readInt();
                int[] ints = new int[len];
                for (int i = 0; i < len; i++) {
                    ints[i] = in.readInt();
                }
                return new TagIntArray(ints);
            }
            case 12: {
                int len = in.readInt();
                long[] longs = new long[len];
                for (int i = 0; i < len; i++) {
                    longs[i] = in.readLong();
                }
                return new TagLongArray(longs);
            }
            default: throw new IOException("Unknown tag type: " + id);
        }
    }

    public static NamedTag readFile(File file) throws IOException {
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(new FileInputStream(file)))) {
            byte type = in.readByte();
            if (type != 10) {
                throw new IOException("Root tag must be TagCompound (10), got: " + type);
            }
            int nameLen = in.readUnsignedShort();
            byte[] nameBytes = new byte[nameLen];
            in.readFully(nameBytes);
            String name = new String(nameBytes, StandardCharsets.UTF_8);
            TagCompound compound = (TagCompound) readTagPayload((byte) 10, in);
            return new NamedTag(name, compound);
        }
    }

    public static void writeFile(NamedTag root, File file) throws IOException {
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(new FileOutputStream(file)))) {
            out.writeByte(10);
            byte[] nameBytes = root.name.getBytes(StandardCharsets.UTF_8);
            out.writeShort(nameBytes.length);
            out.write(nameBytes);
            root.tag.write(out);
        }
    }
}
