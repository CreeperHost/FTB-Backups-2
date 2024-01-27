package net.creeperhost.ftbbackups;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.covers1624.quack.util.SneakyUtils;
import net.creeperhost.levelio.lib.nbt.ICompoundTag;
import net.creeperhost.levelio.lib.nbt.IListTag;
import net.creeperhost.levelio.lib.nbt.ITag;
import net.creeperhost.levelio.lib.nbt.NBTHandler;
import net.minecraft.nbt.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

/**
 * Created by brandon3055 on 12/08/2023
 */
public class MCNBTImpl implements NBTHandler {

    private static InputStream handleCompression(InputStream is) throws IOException {
        PushbackInputStream pbis = new PushbackInputStream(is, 2);
        int signature = (pbis.read() & 0xFF) + (pbis.read() << 8);
        pbis.unread(signature >> 8);
        pbis.unread(signature & 0xFF);
        if (signature == GZIPInputStream.GZIP_MAGIC) {
            return new GZIPInputStream(pbis);
        }
        return pbis;
    }

    @Nullable
    @Override
    public ICompoundTag read(Path path) throws IOException {
        try (InputStream is = handleCompression(Files.newInputStream(path))) {
            return read(is);
        }
    }

    @Nullable
    @Override
    public ICompoundTag read(InputStream is) throws IOException {
        return new CompoundWrapper(NbtIo.read(new DataInputStream(is)));
    }

    @Nullable
    @Override
    public ICompoundTag fromSNBT(String snbt) throws IOException {
        try {
            return new CompoundWrapper(NbtUtils.snbtToStructure(snbt));
        } catch (CommandSyntaxException e) {
            return null;
        }
    }

    @Override
    public void write(ICompoundTag tag, Path path) throws IOException {
        NbtIo.write((CompoundTag) tag.unwrap(), path);
    }

    @Override
    public ICompoundTag emptyCompound() {
        return new CompoundWrapper(new CompoundTag());
    }

    public static class CompoundWrapper implements ICompoundTag {
        private final CompoundTag tag;

        public CompoundWrapper(CompoundTag tag) {
            this.tag = tag;
        }

        @Override
        public void put(String name, ITag value) {
            tag.put(name, (Tag) value.unwrap());
        }

        @Override
        public void putByte(String name, byte value) {
            tag.putByte(name, value);
        }

        @Override
        public void putShort(String name, short value) {
            tag.putShort(name, value);
        }

        @Override
        public void putInt(String name, int value) {
            tag.putInt(name, value);
        }

        @Override
        public void putLong(String name, long value) {
            tag.putLong(name, value);
        }

        @Override
        public void putUUID(String name, UUID value) {
            tag.putIntArray(name, uuidToIntArray(value));
        }

        private static int[] uuidToIntArray(UUID uuid) {
            long most = uuid.getMostSignificantBits();
            long least = uuid.getLeastSignificantBits();
            return leastMostToIntArray(most, least);
        }

        private static int[] leastMostToIntArray(long most, long least) {
            return new int[]{(int) (most >> 32), (int) most, (int) (least >> 32), (int) least};
        }

        private static UUID uuidFromIntArray(int[] uuidInts) {
            return new UUID((long) uuidInts[0] << 32 | (long) uuidInts[1] & 4294967295L, (long) uuidInts[2] << 32 | (long) uuidInts[3] & 4294967295L);
        }

        @Override
        public UUID getUUID(String name) {
            int[] ints = tag.getIntArray(name);
            if (ints.length == 4) {
                return uuidFromIntArray(ints);
            }
            throw new IllegalArgumentException("Invalid UUID tag, Expected int array of length 4, found " + ints);
        }

        @Override
        public boolean hasUUID(String name) {
            int[] ints = tag.getIntArray(name);
            return ints.length == 4;
        }

        @Override
        public void putFloat(String name, float value) {
            tag.putFloat(name, value);
        }

        @Override
        public void putDouble(String name, double value) {
            tag.putDouble(name, value);
        }

        @Override
        public void putString(String name, String value) {
            tag.putString(name, value);
        }

        @Override
        public void putByteArray(String name, byte[] value) {
            tag.putByteArray(name, value);
        }

        @Override
        public void putIntArray(String name, int[] value) {
            tag.putIntArray(name, value);
        }

        @Override
        public void putLongArray(String name, long[] value) {
            tag.putLongArray(name, value);
        }

        @Override
        public void putBoolean(String name, boolean value) {
            tag.putBoolean(name, value);
        }

        @Override
        public byte getTagType(String name) {
            return tag.getTagType(name);
        }

        @Override
        public boolean contains(String name) {
            return tag.contains(name);
        }

        @Override
        public boolean contains(String name, int type) {
            return tag.contains(name, type);
        }

        @Override
        public byte getByte(String name) {
            return tag.getByte(name);
        }

        @Override
        public short getShort(String name) {
            return tag.getShort(name);
        }

        @Override
        public int getInt(String name) {
            return tag.getInt(name);
        }

        @Override
        public long getLong(String name) {
            return tag.getLong(name);
        }

        @Override
        public float getFloat(String name) {
            return tag.getFloat(name);
        }

        @Override
        public double getDouble(String name) {
            return tag.getDouble(name);
        }

        @NotNull
        @Override
        public String getString(String name) {
            return tag.getString(name);
        }

        @Override
        public byte[] getByteArray(String name) {
            return tag.getByteArray(name);
        }

        @Override
        public int[] getIntArray(String name) {
            return tag.getIntArray(name);
        }

        @Override
        public long[] getLongArray(String name) {
            return tag.getLongArray(name);
        }

        @NotNull
        @Override
        public ICompoundTag getCompound(String name) {
            return new CompoundWrapper(this.tag.getCompound(name));
        }

        @Override
        public IListTag getList(String name, int type) {
            if (!tag.contains(name, Tag.TAG_LIST)) {
                return null;
            }
            return new ListWrapper(tag.getList(name, type));
        }

        @Override
        public boolean getBoolean(String name) {
            return tag.getBoolean(name);
        }

        @Override
        public void remove(String name) {
            tag.remove(name);
        }

        @Override
        public CompoundTag unwrap() {
            return tag;
        }

        @Override
        public int hashCode() {
            return tag.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof CompoundTag) {
                return tag.equals(obj);
            } else if (obj instanceof CompoundWrapper) {
                return tag.equals(((CompoundWrapper) obj).tag);
            }
            return super.equals(obj);
        }
    }

    public static class ListWrapper implements IListTag {
        private final ListTag tag;

        public ListWrapper(ListTag tag) {
            this.tag = tag;
        }

        @Override
        public void remove(int index) {
            tag.remove(index);
        }

        @Override
        public boolean isEmpty() {
            return tag.size() == 0;
        }

        @Override
        public ICompoundTag getCompound(int index) {
            return new CompoundWrapper(tag.getCompound(index));
        }

        @Nullable
        @Override
        public IListTag getList(int index) {
            return new ListWrapper(tag.getList(index));
        }

        @Override
        public short getShort(int index) {
            return tag.getShort(index);
        }

        @Override
        public int getInt(int index) {
            return tag.getInt(index);
        }

        @Override
        public int[] getIntArray(int index) {
            return tag.getIntArray(index);
        }

        @Override
        public long[] getLongArray(int index) {
            return tag.getLongArray(index);
        }

        @Override
        public double getDouble(int index) {
            return tag.getDouble(index);
        }

        @Override
        public float getFloat(int index) {
            return tag.getFloat(index);
        }

        @Override
        public String getString(int index) {
            return tag.getString(index);
        }

        @Override
        public int size() {
            return tag.size();
        }

        @Override
        public void set(int index, ITag tag) {
            this.tag.set(index, SneakyUtils.unsafeCast(tag.unwrap()));
        }

        @Override
        public void add(ITag tag) {
            this.tag.add(SneakyUtils.unsafeCast(tag.unwrap()));
        }

        @Override
        public ListTag unwrap() {
            return tag;
        }
    }

}