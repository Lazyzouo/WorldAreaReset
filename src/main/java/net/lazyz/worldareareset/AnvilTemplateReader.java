package net.lazyz.worldareareset;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

/** Reads block states from standard Anvil region files without creating a Bukkit world. */
final class AnvilTemplateReader {

    private static final int SECTOR_BYTES = 4096;
    private static final int REGION_HEADER_BYTES = SECTOR_BYTES * 2;
    private static final String AIR_STATE = "minecraft:air";
    private static final TemplateChunk EMPTY_CHUNK = new TemplateChunk(Map.of());
    private static final Map<Integer, Material> LEGACY_MATERIALS = legacyMaterials();

    private final Path regionDirectory;
    private final Logger logger;
    private final Map<Long, TemplateChunk> chunks = new ConcurrentHashMap<>();
    private final Map<Integer, String> legacyStateCache = new ConcurrentHashMap<>();
    private final AtomicBoolean readErrors = new AtomicBoolean();

    AnvilTemplateReader(Path regionDirectory, Logger logger) {
        this.regionDirectory = regionDirectory;
        this.logger = logger;
    }

    String blockData(int x, int y, int z) throws IOException {
        String state = blockDataIfPresent(x, y, z);
        return state == null ? AIR_STATE : state;
    }

    /**
     * Returns the saved state, or null when the template contains no data for this block.
     * A null result is different from an explicitly saved minecraft:air state.
     */
    String blockDataIfPresent(int x, int y, int z) throws IOException {
        int chunkX = Math.floorDiv(x, 16);
        int chunkZ = Math.floorDiv(z, 16);
        TemplateChunk chunk = chunk(chunkX, chunkZ);
        return chunk.blockDataIfPresent(Math.floorMod(x, 16), y, Math.floorMod(z, 16));
    }

    String[] blockDataSectionIfPresent(int chunkX, int chunkZ, int sectionY) throws IOException {
        return chunk(chunkX, chunkZ).section(sectionY);
    }

    boolean hasReadErrors() {
        return readErrors.get();
    }

    /** Returns the chunks that are actually present in the template region files. */
    List<TemplateChunkCoordinate> storedChunks() throws IOException {
        if (!Files.isDirectory(regionDirectory)) {
            return List.of();
        }

        Set<TemplateChunkCoordinate> result = new LinkedHashSet<>();
        try (var files = Files.list(regionDirectory)) {
            for (Path regionFile : files.filter(Files::isRegularFile).sorted().toList()) {
                String fileName = regionFile.getFileName().toString();
                String[] parts = fileName.split("\\.");
                if (parts.length != 4 || !parts[0].equals("r") || !parts[3].equals("mca")) {
                    continue;
                }

                int regionX;
                int regionZ;
                try {
                    regionX = Integer.parseInt(parts[1]);
                    regionZ = Integer.parseInt(parts[2]);
                } catch (NumberFormatException ignored) {
                    logger.warning("跳过名称无效的模板 region 文件: " + regionFile);
                    continue;
                }

                long fileSize = Files.size(regionFile);
                if (fileSize < REGION_HEADER_BYTES) {
                    throw new IOException("模板 region 文件过小: " + regionFile + " (" + fileSize + " bytes)");
                }

                try (RandomAccessFile file = new RandomAccessFile(regionFile.toFile(), "r")) {
                    for (int index = 0; index < 1024; index++) {
                        file.seek(index * 4L);
                        int offset = ((file.readUnsignedByte() & 0xff) << 16)
                                | ((file.readUnsignedByte() & 0xff) << 8)
                                | (file.readUnsignedByte() & 0xff);
                        int sectorCount = file.readUnsignedByte();
                        if (offset != 0 && sectorCount != 0) {
                            result.add(new TemplateChunkCoordinate(
                                    regionX * 32 + index % 32,
                                    regionZ * 32 + index / 32));
                        }
                    }
                } catch (IOException error) {
                    readErrors.set(true);
                    logger.log(Level.WARNING, "读取模板 region 区块目录失败: " + regionFile, error);
                    throw error;
                }
            }
        }
        return List.copyOf(result);
    }

    private TemplateChunk chunk(int chunkX, int chunkZ) throws IOException {
        long key = ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
        TemplateChunk cached = chunks.get(key);
        if (cached != null) {
            return cached;
        }
        synchronized (chunks) {
            cached = chunks.get(key);
            if (cached == null) {
                cached = readChunk(chunkX, chunkZ);
                chunks.put(key, cached);
            }
            return cached;
        }
    }

    private TemplateChunk readChunk(int chunkX, int chunkZ) throws IOException {
        Path regionFile = regionDirectory.resolve("r." + Math.floorDiv(chunkX, 32)
                + "." + Math.floorDiv(chunkZ, 32) + ".mca");
        if (!Files.isRegularFile(regionFile)) {
            return EMPTY_CHUNK;
        }
        long fileSize = Files.size(regionFile);
        if (fileSize < REGION_HEADER_BYTES) {
            readErrors.set(true);
            logger.warning("跳过过小的模板 region 文件: " + regionFile + " (" + fileSize + " bytes)");
            return EMPTY_CHUNK;
        }

        int localChunkX = Math.floorMod(chunkX, 32);
        int localChunkZ = Math.floorMod(chunkZ, 32);
        int locationIndex = localChunkX + localChunkZ * 32;
        try (RandomAccessFile file = new RandomAccessFile(regionFile.toFile(), "r")) {
            file.seek(locationIndex * 4L);
            int offset = ((file.readUnsignedByte() & 0xff) << 16)
                    | ((file.readUnsignedByte() & 0xff) << 8)
                    | (file.readUnsignedByte() & 0xff);
            int sectorCount = file.readUnsignedByte();
            if (offset == 0 || sectorCount == 0) {
                return EMPTY_CHUNK;
            }
            long chunkPosition = offset * (long) SECTOR_BYTES;
            if (chunkPosition < REGION_HEADER_BYTES
                    || chunkPosition + (long) sectorCount * SECTOR_BYTES > fileSize) {
                readErrors.set(true);
                logger.warning("模板 region 文件中的区块位置无效: " + regionFile
                        + " chunk=" + chunkX + "," + chunkZ);
                return EMPTY_CHUNK;
            }
            file.seek(chunkPosition);
            int length = file.readInt();
            int compression = file.readUnsignedByte();
            if (length <= 1 || length > sectorCount * SECTOR_BYTES - 4) {
                readErrors.set(true);
                logger.warning("模板区块数据长度无效: " + regionFile + " chunk=" + chunkX + "," + chunkZ);
                return EMPTY_CHUNK;
            }
            byte[] compressed = new byte[length - 1];
            file.readFully(compressed);
            try (InputStream decoded = decompressor(compressed, compression);
                 DataInputStream nbt = new DataInputStream(decoded)) {
                Object root = readRoot(nbt);
                return decodeChunk(root);
            }
        } catch (EOFException error) {
            readErrors.set(true);
            logger.log(Level.WARNING, "模板区块数据不完整: " + regionFile + " chunk=" + chunkX + "," + chunkZ, error);
            return EMPTY_CHUNK;
        } catch (IOException error) {
            readErrors.set(true);
            logger.log(Level.WARNING, "读取模板区块失败: " + regionFile + " chunk=" + chunkX + "," + chunkZ, error);
            return EMPTY_CHUNK;
        } catch (RuntimeException error) {
            readErrors.set(true);
            logger.log(Level.WARNING, "解析模板区块失败: " + regionFile + " chunk=" + chunkX + "," + chunkZ, error);
            return EMPTY_CHUNK;
        }
    }

    private InputStream decompressor(byte[] compressed, int compression) throws IOException {
        ByteArrayInputStream bytes = new ByteArrayInputStream(compressed);
        return switch (compression) {
            case 1 -> new GZIPInputStream(bytes);
            case 2 -> new InflaterInputStream(bytes);
            case 3 -> bytes;
            default -> throw new IOException("不支持的模板区块压缩类型: " + compression);
        };
    }

    private Object readRoot(DataInputStream input) throws IOException {
        int type = input.readUnsignedByte();
        if (type != 10) {
            throw new IOException("模板 NBT 根标签不是 Compound: " + type);
        }
        input.readUTF();
        return readPayload(input, type);
    }

    private Object readPayload(DataInputStream input, int type) throws IOException {
        return switch (type) {
            case 1 -> input.readByte();
            case 2 -> input.readShort();
            case 3 -> input.readInt();
            case 4 -> input.readLong();
            case 5 -> input.readFloat();
            case 6 -> input.readDouble();
            case 7 -> {
                int length = input.readInt();
                if (length < 0 || length > 64 * 1024 * 1024) throw new IOException("NBT byte array too large");
                byte[] value = new byte[length];
                input.readFully(value);
                yield value;
            }
            case 8 -> input.readUTF();
            case 9 -> {
                int elementType = input.readUnsignedByte();
                int length = input.readInt();
                if (length < 0 || length > 1_000_000) throw new IOException("NBT list too large");
                List<Object> values = new ArrayList<>(length);
                for (int index = 0; index < length; index++) {
                    values.add(readPayload(input, elementType));
                }
                yield new NbtList(elementType, values);
            }
            case 10 -> {
                Map<String, Object> values = new LinkedHashMap<>();
                while (true) {
                    int childType = input.readUnsignedByte();
                    if (childType == 0) break;
                    String name = input.readUTF();
                    values.put(name, readPayload(input, childType));
                }
                yield values;
            }
            case 11 -> {
                int length = input.readInt();
                if (length < 0 || length > 16 * 1024 * 1024) throw new IOException("NBT int array too large");
                int[] value = new int[length];
                for (int index = 0; index < length; index++) value[index] = input.readInt();
                yield value;
            }
            case 12 -> {
                int length = input.readInt();
                if (length < 0 || length > 16 * 1024 * 1024) throw new IOException("NBT long array too large");
                long[] value = new long[length];
                for (int index = 0; index < length; index++) value[index] = input.readLong();
                yield value;
            }
            default -> throw new IOException("未知的 NBT 标签类型: " + type);
        };
    }

    private TemplateChunk decodeChunk(Object root) throws IOException {
        if (!(root instanceof Map<?, ?> rootMap)) {
            throw new IOException("模板 NBT 根数据不是 Compound");
        }
        Object sectionsValue = rootMap.get("sections");
        if (!(sectionsValue instanceof NbtList sections)) {
            sectionsValue = rootMap.get("Sections");
        }
        if (!(sectionsValue instanceof NbtList sections)) {
            Object level = rootMap.get("Level");
            if (level instanceof Map<?, ?> levelMap) {
                sectionsValue = levelMap.get("sections");
                if (!(sectionsValue instanceof NbtList)) sectionsValue = levelMap.get("Sections");
            }
        }
        if (!(sectionsValue instanceof NbtList sections)) {
            return EMPTY_CHUNK;
        }

        Map<Integer, String[]> decodedSections = new LinkedHashMap<>();
        for (Object sectionValue : sections.values()) {
            if (!(sectionValue instanceof Map<?, ?> section)) {
                throw new IOException("模板区块 section 数据无效");
            }
            Object sectionYValue = section.get("Y");
            if (!(sectionYValue instanceof Number sectionY)) {
                throw new IOException("模板区块 section 缺少 Y 坐标");
            }
            Object statesValue = section.get("block_states");
            Object paletteValue;
            Object dataValue;
            if (statesValue instanceof Map<?, ?> stateMap) {
                paletteValue = stateMap.get("palette");
                if (!(paletteValue instanceof NbtList)) paletteValue = stateMap.get("Palette");
                dataValue = stateMap.get("data");
                if (!(dataValue instanceof long[])) dataValue = stateMap.get("BlockStates");
            } else {
                // Minecraft 1.13-1.16 stores these two tags directly on the section.
                paletteValue = section.get("Palette");
                dataValue = section.get("BlockStates");
            }
            if (paletteValue == null && section.get("Blocks") instanceof byte[]) {
                decodedSections.put(sectionY.intValue(), legacyBlocks(section));
                continue;
            }
            if (paletteValue == null && statesValue == null && dataValue == null) {
                // A section without block-state tags was not part of the saved template
                // area. Keep it absent so restoration can merge sparse template regions.
                continue;
            }
            if (!(paletteValue instanceof NbtList paletteList) || paletteList.values().isEmpty()) {
                throw new IOException("模板区块 section 缺少 palette");
            }

            List<String> states = new ArrayList<>(paletteList.values().size());
            for (Object paletteEntry : paletteList.values()) {
                if (!(paletteEntry instanceof Map<?, ?> entry)) {
                    throw new IOException("模板方块 palette 条目无效");
                }
                states.add(blockState(entry));
            }
            String[] blocks = new String[4096];
            if (states.size() == 1) {
                Arrays.fill(blocks, states.get(0));
            } else {
                if (!(dataValue instanceof long[] data)) {
                    throw new IOException("模板方块状态缺少 packed data");
                }
                int bits = Math.max(4, 32 - Integer.numberOfLeadingZeros(states.size() - 1));
                long mask = bits == 64 ? -1L : (1L << bits) - 1L;
                int valuesPerLong = 64 / bits;
                for (int index = 0; index < blocks.length; index++) {
                    int longIndex = index / valuesPerLong;
                    int bitOffset = (index % valuesPerLong) * bits;
                    long value = data[longIndex] >>> bitOffset;
                    int paletteIndex = (int) (value & mask);
                    blocks[index] = paletteIndex < states.size() ? states.get(paletteIndex) : states.get(0);
                }
            }
            decodedSections.put(sectionY.intValue(), blocks);
        }
        return decodedSections.isEmpty() ? EMPTY_CHUNK : new TemplateChunk(decodedSections);
    }

    private String[] legacyBlocks(Map<?, ?> section) throws IOException {
        Object blocksValue = section.get("Blocks");
        Object dataValue = section.get("Data");
        if (!(blocksValue instanceof byte[] blocks) || blocks.length != 4096) {
            throw new IOException("旧版模板 section 的 Blocks 长度无效");
        }
        if (!(dataValue instanceof byte[] data) || data.length < 2048) {
            throw new IOException("旧版模板 section 的 Data 长度无效");
        }
        byte[] add = section.get("Add") instanceof byte[] addValue ? addValue : null;
        String[] states = new String[4096];
        for (int index = 0; index < states.length; index++) {
            int blockId = blocks[index] & 0xff;
            if (add != null && add.length >= 2048) {
                blockId |= nibble(add, index) << 8;
            }
            states[index] = legacyBlockState(blockId, nibble(data, index));
        }
        return states;
    }

    private String legacyBlockState(int blockId, int data) throws IOException {
        int cacheKey = (blockId << 4) | (data & 0xf);
        String cached = legacyStateCache.get(cacheKey);
        if (cached != null) return cached;

        Material legacyMaterial = LEGACY_MATERIALS.get(blockId);
        if (legacyMaterial == null) {
            throw new IOException("未知的旧版模板方块 ID: " + blockId);
        }

        String state;
        try {
            BlockData converted = Bukkit.getUnsafe().fromLegacy(legacyMaterial, (byte) data);
            state = converted == null ? legacyFallbackState(legacyMaterial) : converted.getAsString();
        } catch (RuntimeException ignored) {
            // Unit tests do not have a running Bukkit server; keep a usable base state there.
            state = legacyFallbackState(legacyMaterial);
        }
        String existing = legacyStateCache.putIfAbsent(cacheKey, state);
        return existing == null ? state : existing;
    }

    private String legacyFallbackState(Material material) {
        String name = material.name().toLowerCase(Locale.ROOT);
        if (name.startsWith("legacy_")) name = name.substring("legacy_".length());
        name = switch (name) {
            case "air" -> "air";
            case "grass" -> "grass_block";
            case "wood" -> "oak_planks";
            case "log" -> "oak_log";
            case "leaves" -> "oak_leaves";
            case "snow" -> "snow_layer";
            case "stationary_water" -> "water";
            case "stationary_lava" -> "lava";
            case "hard_clay" -> "terracotta";
            case "step" -> "stone_slab";
            case "double_step" -> "stone_slab";
            default -> name;
        };
        return "minecraft:" + name;
    }

    private int nibble(byte[] values, int index) {
        int value = values[index >> 1] & 0xff;
        return (index & 1) == 0 ? value & 0xf : (value >>> 4) & 0xf;
    }

    private static Map<Integer, Material> legacyMaterials() {
        Map<Integer, Material> materials = new HashMap<>();
        for (Material material : Material.values()) {
            if (material.isLegacy() && material.getId() >= 0 && material.getId() <= 4095) {
                materials.putIfAbsent(material.getId(), material);
            }
        }
        return Map.copyOf(materials);
    }

    private String blockState(Map<?, ?> entry) throws IOException {
        Object nameValue = entry.get("Name");
        if (!(nameValue instanceof String name) || name.isBlank()) {
            throw new IOException("模板 palette 缺少方块名称");
        }
        Object propertiesValue = entry.get("Properties");
        if (!(propertiesValue instanceof Map<?, ?> properties) || properties.isEmpty()) {
            return name;
        }
        StringBuilder result = new StringBuilder(name).append('[');
        boolean first = true;
        for (Map.Entry<?, ?> property : properties.entrySet()) {
            if (!(property.getKey() instanceof String key) || !(property.getValue() instanceof String value)) continue;
            if (!first) result.append(',');
            result.append(key).append('=').append(value);
            first = false;
        }
        return first ? name : result.append(']').toString();
    }

    private record NbtList(int elementType, List<Object> values) {
    }

    record TemplateChunkCoordinate(int x, int z) {
    }

    private record TemplateChunk(Map<Integer, String[]> sections) {
        private String blockDataIfPresent(int localX, int y, int localZ) {
            int sectionY = Math.floorDiv(y, 16);
            String[] blocks = sections.get(sectionY);
            if (blocks == null) return null;
            int index = (Math.floorMod(y, 16) << 8) | (localZ << 4) | localX;
            return blocks[index];
        }

        private String[] section(int sectionY) {
            return sections.get(sectionY);
        }
    }
}
