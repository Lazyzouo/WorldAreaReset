package net.lazyz.worldareareset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AnvilTemplateReaderTest {

    @Test
    void distinguishesDataLessTemplateSectionsFromSavedAir(@TempDir Path temporaryDirectory) throws Exception {
        Path regionDirectory = temporaryDirectory.resolve("region");
        Files.createDirectories(regionDirectory);
        Files.write(regionDirectory.resolve("r.0.0.mca"), regionWithSingleStoneChunk());

        AnvilTemplateReader reader = new AnvilTemplateReader(
                regionDirectory, Logger.getLogger(AnvilTemplateReaderTest.class.getName()));

        assertEquals(List.of(new AnvilTemplateReader.TemplateChunkCoordinate(0, 0)), reader.storedChunks());
        assertEquals("minecraft:stone", reader.blockData(0, 0, 0));
        assertEquals("minecraft:air", reader.blockData(0, 16, 0));
        assertEquals("minecraft:stone", reader.blockDataIfPresent(0, 0, 0));
        assertEquals(null, reader.blockDataIfPresent(0, 16, 0));
        assertFalse(reader.hasReadErrors());
    }

    @Test
    void readsLegacySectionPaletteAndBlockStatesTags(@TempDir Path temporaryDirectory) throws Exception {
        Path regionDirectory = temporaryDirectory.resolve("region");
        Files.createDirectories(regionDirectory);
        Files.write(regionDirectory.resolve("r.0.0.mca"), regionWithLegacyStoneAndDirtChunk());

        AnvilTemplateReader reader = new AnvilTemplateReader(
                regionDirectory, Logger.getLogger(AnvilTemplateReaderTest.class.getName()));

        assertEquals("minecraft:stone", reader.blockData(0, 0, 0));
        assertEquals("minecraft:dirt", reader.blockData(1, 0, 0));
        assertEquals("minecraft:stone", reader.blockData(0, 0, 1));
    }

    @Test
    void readsPreFlatteningBlocksAndDataTags(@TempDir Path temporaryDirectory) throws Exception {
        Path regionDirectory = temporaryDirectory.resolve("region");
        Files.createDirectories(regionDirectory);
        Files.write(regionDirectory.resolve("r.0.0.mca"), regionWithLegacyNumericChunk());

        AnvilTemplateReader reader = new AnvilTemplateReader(
                regionDirectory, Logger.getLogger(AnvilTemplateReaderTest.class.getName()));

        assertEquals("minecraft:stone", reader.blockData(0, 0, 0));
        assertEquals("minecraft:dirt", reader.blockData(1, 0, 0));
        assertEquals("minecraft:air", reader.blockData(0, 16, 0));
    }

    private byte[] regionWithSingleStoneChunk() throws IOException {
        byte[] nbt = stoneChunkNbt();
        ByteArrayOutputStream compressedBytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(compressedBytes)) {
            gzip.write(nbt);
        }

        byte[] compressed = compressedBytes.toByteArray();
        ByteArrayOutputStream chunkBytes = new ByteArrayOutputStream();
        try (DataOutputStream chunk = new DataOutputStream(chunkBytes)) {
            chunk.writeInt(compressed.length + 1);
            chunk.writeByte(1);
            chunk.write(compressed);
        }

        byte[] region = new byte[8192 + 4096];
        region[2] = 2;
        region[3] = 1;
        System.arraycopy(chunkBytes.toByteArray(), 0, region, 8192, chunkBytes.size());
        return region;
    }

    private byte[] stoneChunkNbt() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream nbt = new DataOutputStream(bytes)) {
            nbt.writeByte(10);
            nbt.writeUTF("");
            nbt.writeByte(9);
            nbt.writeUTF("sections");
            nbt.writeByte(10);
            nbt.writeInt(2);

            nbt.writeByte(1);
            nbt.writeUTF("Y");
            nbt.writeByte(0);
            nbt.writeByte(10);
            nbt.writeUTF("block_states");
            nbt.writeByte(9);
            nbt.writeUTF("palette");
            nbt.writeByte(10);
            nbt.writeInt(1);
            nbt.writeByte(8);
            nbt.writeUTF("Name");
            nbt.writeUTF("minecraft:stone");
            nbt.writeByte(0);
            nbt.writeByte(0);
            nbt.writeByte(0);

            // This is the layout reported by sparse map exports: the section exists but
            // carries no block-state tags, so it must be merged rather than written as air.
            nbt.writeByte(1);
            nbt.writeUTF("Y");
            nbt.writeByte(1);
            nbt.writeByte(0);
            nbt.writeByte(0);
        }
        return bytes.toByteArray();
    }

    private byte[] regionWithLegacyStoneAndDirtChunk() throws IOException {
        byte[] nbt = legacyStoneAndDirtChunkNbt();
        ByteArrayOutputStream compressedBytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(compressedBytes)) {
            gzip.write(nbt);
        }

        byte[] compressed = compressedBytes.toByteArray();
        ByteArrayOutputStream chunkBytes = new ByteArrayOutputStream();
        try (DataOutputStream chunk = new DataOutputStream(chunkBytes)) {
            chunk.writeInt(compressed.length + 1);
            chunk.writeByte(1);
            chunk.write(compressed);
        }

        byte[] region = new byte[8192 + 4096];
        region[2] = 2;
        region[3] = 1;
        System.arraycopy(chunkBytes.toByteArray(), 0, region, 8192, chunkBytes.size());
        return region;
    }

    private byte[] legacyStoneAndDirtChunkNbt() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream nbt = new DataOutputStream(bytes)) {
            nbt.writeByte(10);
            nbt.writeUTF("");
            nbt.writeByte(10);
            nbt.writeUTF("Level");
            nbt.writeByte(9);
            nbt.writeUTF("Sections");
            nbt.writeByte(10);
            nbt.writeInt(1);

            nbt.writeByte(1);
            nbt.writeUTF("Y");
            nbt.writeByte(0);
            nbt.writeByte(9);
            nbt.writeUTF("Palette");
            nbt.writeByte(10);
            nbt.writeInt(2);
            writePaletteEntry(nbt, "minecraft:stone");
            writePaletteEntry(nbt, "minecraft:dirt");

            nbt.writeByte(12);
            nbt.writeUTF("BlockStates");
            nbt.writeInt(256);
            nbt.writeLong(1L << 4);
            for (int index = 1; index < 256; index++) nbt.writeLong(0L);

            nbt.writeByte(0);
            nbt.writeByte(0);
            nbt.writeByte(0);
        }
        return bytes.toByteArray();
    }

    private byte[] regionWithLegacyNumericChunk() throws IOException {
        byte[] nbt = legacyNumericChunkNbt();
        ByteArrayOutputStream compressedBytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(compressedBytes)) {
            gzip.write(nbt);
        }

        byte[] compressed = compressedBytes.toByteArray();
        ByteArrayOutputStream chunkBytes = new ByteArrayOutputStream();
        try (DataOutputStream chunk = new DataOutputStream(chunkBytes)) {
            chunk.writeInt(compressed.length + 1);
            chunk.writeByte(1);
            chunk.write(compressed);
        }

        byte[] region = new byte[8192 + 4096];
        region[2] = 2;
        region[3] = 1;
        System.arraycopy(chunkBytes.toByteArray(), 0, region, 8192, chunkBytes.size());
        return region;
    }

    private byte[] legacyNumericChunkNbt() throws IOException {
        byte[] blocks = new byte[4096];
        blocks[0] = 1;
        blocks[1] = 3;
        byte[] data = new byte[2048];

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream nbt = new DataOutputStream(bytes)) {
            nbt.writeByte(10);
            nbt.writeUTF("");
            nbt.writeByte(10);
            nbt.writeUTF("Level");
            nbt.writeByte(9);
            nbt.writeUTF("Sections");
            nbt.writeByte(10);
            nbt.writeInt(1);

            nbt.writeByte(1);
            nbt.writeUTF("Y");
            nbt.writeByte(0);
            writeByteArray(nbt, "Blocks", blocks);
            writeByteArray(nbt, "Data", data);
            nbt.writeByte(0);
            nbt.writeByte(0);
            nbt.writeByte(0);
        }
        return bytes.toByteArray();
    }

    private void writeByteArray(DataOutputStream nbt, String name, byte[] value) throws IOException {
        nbt.writeByte(7);
        nbt.writeUTF(name);
        nbt.writeInt(value.length);
        nbt.write(value);
    }

    private void writePaletteEntry(DataOutputStream nbt, String name) throws IOException {
        nbt.writeByte(8);
        nbt.writeUTF("Name");
        nbt.writeUTF(name);
        nbt.writeByte(0);
    }
}
