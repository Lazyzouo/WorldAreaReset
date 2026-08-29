package net.lazyz.worldareareset;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.function.Consumer;

public class AreaCleanupTask {

    private static final int MAX_RESTORE_CHUNK_LOADS = 32;
    private static final int MAX_CLEANUP_CHUNK_LOADS = 16;
    private static final int ONLINE_RESTORE_CHUNK_CONCURRENCY = 2;
    private static final int ONLINE_RESTORE_BLOCK_BUDGET = 512;
    private static final int DEFAULT_RESTORE_BLOCK_BUDGET = 4096;
    private static final int MAX_RESTORE_BLOCK_BUDGET = 16384;
    private static final int ONLINE_CLEANUP_BLOCK_BUDGET = 1024;
    private static final int OFFLINE_CLEANUP_BLOCK_BUDGET = 8192;
    private static final Set<Material> LIQUID_BLOCKS = Set.of(
            Material.WATER, Material.LAVA, Material.BUBBLE_COLUMN);

    private final WorldAreaResetPlugin plugin;
    private ScheduledTask cleanupTimerTask;
    private ScheduledTask recreateTimerTask;
    private ScheduledTask cleanupDelayedTask;
    private ScheduledTask recreateDelayedTask;
    private volatile long cleanupNextRunAtMillis = -1L;
    private volatile long recreateNextRunAtMillis = -1L;
    private final AtomicBoolean restoreRunning = new AtomicBoolean();

    public AreaCleanupTask(WorldAreaResetPlugin plugin) {
        this.plugin = plugin;
    }

    public synchronized void start() {
        FileConfiguration config = plugin.getConfig();
        if (config.getBoolean("cleanup.enabled", false)) {
            scheduleCleanup(config);
        }
        if (config.getBoolean("recreate.enabled", false)) {
            scheduleRecreate(config);
        }
    }

    private void scheduleCleanup(FileConfiguration config) {
        cancelCleanupSchedule();
        long interval = cleanupIntervalMinutes(config);
        cleanupNextRunAtMillis = nextRunAtMillis(interval);
        cleanupTimerTask = plugin.getServer().getAsyncScheduler().runAtFixedRate(plugin,
                task -> {
                    cleanupNextRunAtMillis = nextRunAtMillis(interval);
                    triggerCleanupCountdown();
                }, interval, interval, TimeUnit.MINUTES);
    }

    private void scheduleRecreate(FileConfiguration config) {
        cancelRecreateSchedule();
        long interval = recreateIntervalMinutes(config);
        recreateNextRunAtMillis = nextRunAtMillis(interval);
        recreateTimerTask = plugin.getServer().getAsyncScheduler().runAtFixedRate(plugin,
                task -> {
                    recreateNextRunAtMillis = nextRunAtMillis(interval);
                    triggerRecreateCountdown();
                }, interval, interval, TimeUnit.MINUTES);
    }

    private long nextRunAtMillis(long intervalMinutes) {
        try {
            return Math.addExact(System.currentTimeMillis(), Math.multiplyExact(intervalMinutes, 60_000L));
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    private void cancelCleanupSchedule() {
        if (cleanupTimerTask != null) { cleanupTimerTask.cancel(); cleanupTimerTask = null; }
        if (cleanupDelayedTask != null) { cleanupDelayedTask.cancel(); cleanupDelayedTask = null; }
        cleanupNextRunAtMillis = -1L;
    }

    private void cancelRecreateSchedule() {
        if (recreateTimerTask != null) { recreateTimerTask.cancel(); recreateTimerTask = null; }
        if (recreateDelayedTask != null) { recreateDelayedTask.cancel(); recreateDelayedTask = null; }
        recreateNextRunAtMillis = -1L;
    }

    public synchronized void stop() {
        cancelCleanupSchedule();
        cancelRecreateSchedule();
    }

    public synchronized void runManualCleanup() {
        cancelCleanupSchedule();
        if (plugin.getConfig().getBoolean("cleanup.enabled", false)) {
            scheduleCleanup(plugin.getConfig());
        }
        triggerCleanupCountdown();
    }

    public synchronized void runManualRecreate() {
        cancelRecreateSchedule();
        if (plugin.getConfig().getBoolean("recreate.enabled", false)) {
            scheduleRecreate(plugin.getConfig());
        }
        triggerRecreateCountdown();
    }

    String cleanupIntervalDescription() {
        return cleanupInterval(plugin.getConfig()).display(plugin.isChineseLanguage());
    }

    String cleanupIntervalAmount() {
        return String.valueOf(cleanupInterval(plugin.getConfig()).amount());
    }

    String cleanupIntervalUnitDescription(boolean chinese) {
        return cleanupInterval(plugin.getConfig()).displayUnit(chinese);
    }

    String recreateIntervalDescription() {
        return recreateInterval(plugin.getConfig()).display(plugin.isChineseLanguage());
    }

    String recreateIntervalAmount() {
        return String.valueOf(recreateInterval(plugin.getConfig()).amount());
    }

    String recreateIntervalUnitDescription(boolean chinese) {
        return recreateInterval(plugin.getConfig()).displayUnit(chinese);
    }

    String cleanupRemainingDescription(boolean chinese) {
        return formatRemaining(cleanupNextRunAtMillis, cleanupInterval(plugin.getConfig()).unit(), chinese);
    }

    String recreateRemainingDescription(boolean chinese) {
        return formatRemaining(recreateNextRunAtMillis, recreateInterval(plugin.getConfig()).unit(), chinese);
    }

    private String formatRemaining(long nextRunAtMillis, String unit, boolean chinese) {
        if (nextRunAtMillis < 0L) {
            return chinese ? "未排程" : "not scheduled";
        }
        long remainingMillis = Math.max(0L, nextRunAtMillis - System.currentTimeMillis());
        long seconds = Math.max(1L, ceilSeconds(remainingMillis));
        long amount = switch (unit) {
            case "days" -> Math.max(1L, (seconds + 86_399L) / 86_400L);
            case "hours" -> Math.max(1L, (seconds + 3_599L) / 3_600L);
            case "minutes" -> Math.max(1L, (seconds + 59L) / 60L);
            default -> seconds;
        };
        if (chinese) {
            return amount + switch (unit) {
                case "days" -> "天";
                case "hours" -> "小时";
                case "minutes" -> "分钟";
                default -> "秒";
            };
        }
        String label = switch (unit) {
            case "days" -> amount == 1 ? "day" : "days";
            case "hours" -> amount == 1 ? "hour" : "hours";
            case "minutes" -> amount == 1 ? "minute" : "minutes";
            default -> amount == 1 ? "second" : "seconds";
        };
        return amount + " " + label;
    }

    private long ceilSeconds(long milliseconds) {
        long seconds = milliseconds / 1_000L;
        return milliseconds % 1_000L == 0L ? seconds : seconds + 1L;
    }

    private long cleanupIntervalMinutes(FileConfiguration config) {
        Interval interval = cleanupInterval(config);
        long multiplier = switch (interval.unit()) {
            case "days" -> 24L * 60L;
            case "hours" -> 60L;
            default -> 1L;
        };
        try {
            return Math.max(1, Math.multiplyExact(interval.amount(), multiplier));
        } catch (ArithmeticException overflow) {
            plugin.getLogger().warning("cleanup.interval 超出可用范围，已回退为 3 小时。" );
            return 180;
        }
    }

    private String cleanupIntervalUnit(FileConfiguration config) {
        Object raw = config.get("cleanup.interval_unit", "hours");
        String configured = raw == null ? "hours" : String.valueOf(raw);
        String unit = configured == null ? "hours" : configured.trim().toLowerCase(Locale.ROOT);
        if (unit.equals("minute") || unit.equals("min")) {
            unit = "minutes";
        } else if (unit.equals("hour") || unit.equals("hr")) {
            unit = "hours";
        } else if (unit.equals("day")) {
            unit = "days";
        }
        if (!unit.equals("minutes") && !unit.equals("hours") && !unit.equals("days")) {
            plugin.getLogger().warning("cleanup.interval_unit 必须是 minutes、hours 或 days，已回退为 hours。" );
            return "hours";
        }
        return unit;
    }

    private long recreateIntervalMinutes(FileConfiguration config) {
        Interval interval = recreateInterval(config);
        String unit = interval.unit();
        long multiplier;
        if (unit.equals("days")) {
            multiplier = 24L * 60L;
        } else if (unit.equals("hours")) {
            multiplier = 60L;
        } else {
            plugin.getLogger().warning("recreate.interval_unit 必须是 hours 或 days，已回退为 hours。" );
            multiplier = 60L;
        }
        try {
            return Math.max(1, Math.multiplyExact(interval.amount(), multiplier));
        } catch (ArithmeticException overflow) {
            plugin.getLogger().warning("recreate.interval 超出可用范围，已回退为 1 小时。" );
            return 60;
        }
    }

    private Interval cleanupInterval(FileConfiguration config) {
        long amount = Math.max(1, config.getLong("cleanup.interval", 3));
        return new Interval(amount, cleanupIntervalUnit(config));
    }

    private Interval recreateInterval(FileConfiguration config) {
        long amount = Math.max(1, config.getLong("recreate.interval", 3));
        Object raw = config.get("recreate.interval_unit", "hours");
        String configured = raw == null ? "hours" : String.valueOf(raw);
        String unit = configured.trim().toLowerCase(Locale.ROOT);
        if (unit.equals("day")) {
            unit = "days";
        } else if (unit.equals("hour") || unit.equals("hr")) {
            unit = "hours";
        } else if (!unit.equals("hours") && !unit.equals("days")) {
            plugin.getLogger().warning("recreate.interval_unit 必须是 hours 或 days，已回退为 hours。");
            unit = "hours";
        }
        return new Interval(amount, unit);
    }

    static record Interval(long amount, String unit) {
        String display(boolean chinese) {
            return amount + " " + displayUnit(chinese);
        }

        String displayUnit(boolean chinese) {
            if (chinese) {
                return switch (unit) {
                    case "minutes" -> "分钟";
                    case "days" -> "天";
                    default -> "小时";
                };
            }
            return amount == 1 ? unit.substring(0, unit.length() - 1) : unit;
        }
    }

    private synchronized void triggerCleanupCountdown() {
        FileConfiguration config = plugin.getConfig();
        String worldLabel = configuredWorlds(config, "cleanup").stream().map(ConfiguredWorld::name)
                .reduce((left, right) -> left + ", " + right).orElse("unknown");
        int countdown = Math.max(0, config.getInt("cleanup.countdown_seconds", 10));
        String prefix = plugin.message("prefix", "<color:#8A2387><bold>[</bold><color:#E62028><bold>WorldAreaReset</bold></color><color:#8A2387><bold>]</bold></color> <color:#555555><bold>»</bold></color> <color:#B9E7FF>");
        String warningMsg = plugin.replaceVariables(plugin.message("warning",
                        "<color:#FFB7D5><bold>Warning: {world} will be cleaned in {time} seconds.</bold></color>"),
                "{world}", worldLabel, "{worlds}", worldLabel, "{time}", String.valueOf(countdown));
        Bukkit.broadcast(plugin.deserializeInGame(prefix, warningMsg));

        cleanupDelayedTask = plugin.getServer().getAsyncScheduler().runDelayed(plugin, task -> {
            synchronized (AreaCleanupTask.this) {
                cleanupDelayedTask = null;
            }
            runAreaClear(plugin.getConfig());
        }, countdown, TimeUnit.SECONDS);
    }

    private synchronized void triggerRecreateCountdown() {
        FileConfiguration config = plugin.getConfig();
        String worldLabel = configuredWorlds(config, "recreate").stream().map(ConfiguredWorld::name).distinct()
                .reduce((left, right) -> left + ", " + right).orElse("unknown");
        int countdown = Math.max(0, config.getInt("recreate.countdown_seconds", 10));
        String prefix = plugin.message("prefix", "<color:#8A2387><bold>[</bold><color:#E62028><bold>WorldAreaReset</bold></color><color:#8A2387><bold>]</bold></color> <color:#555555><bold>»</bold></color> <color:#B9E7FF>");
        String warningMsg = plugin.replaceVariables(plugin.message("restore_warning",
                        "<color:#FFB7D5><bold>Warning: {world} will be restored in {time} seconds.</bold></color>"),
                "{world}", worldLabel, "{worlds}", worldLabel, "{time}", String.valueOf(countdown));
        Bukkit.broadcast(plugin.deserializeInGame(prefix, warningMsg));
        recreateDelayedTask = plugin.getServer().getAsyncScheduler().runDelayed(plugin, task -> {
            synchronized (AreaCleanupTask.this) { recreateDelayedTask = null; }
            runTerrainRestore(plugin.getConfig());
        }, countdown, TimeUnit.SECONDS);
    }

    public void runCleanup() { runAreaClear(plugin.getConfig()); }

    private void runAreaClear(FileConfiguration config) {
        Set<Material> keepBlocks = new HashSet<>();
        for (String matName : config.getStringList("cleanup.keep_blocks")) {
            try {
                keepBlocks.add(Material.valueOf(matName.toUpperCase()));
            } catch (IllegalArgumentException error) {
                plugin.getLogger().warning("配置中存在无效的方块类型: " + matName);
            }
        }

        List<CleanupWorldPlan> plans = new ArrayList<>();
        for (ConfiguredWorld configuredWorld : configuredWorlds(config, "cleanup")) {
            World world = Bukkit.getWorld(configuredWorld.name());
            if (world == null) {
                plugin.getLogger().warning("未找到配置中指定的清理世界: " + configuredWorld.name());
                continue;
            }
            for (Bounds bounds : configuredBoundsList(configuredWorld, world, false, null)) {
                plans.add(new CleanupWorldPlan(world, bounds));
            }
        }
        if (plans.isEmpty()) {
            return;
        }

        String prefix = plugin.message("prefix", "<color:#8A2387><bold>[</bold><color:#E62028><bold>WorldAreaReset</bold></color><color:#8A2387><bold>]</bold></color> <color:#555555><bold>»</bold></color> <color:#B9E7FF>");
        String worldLabel = plans.stream().map(plan -> plan.world().getName()).distinct().reduce((left, right) ->
                left + ", " + right).orElse("");
        String startMessage = replaceWorldVariables(plugin.message("start_cleanup",
                "<color:#B9E7FF>Area cleanup has started in {world}. Please wait...</color>"), worldLabel);
        Bukkit.broadcast(plugin.deserializeInGame(prefix, startMessage));

        CleanupBatch batch = new CleanupBatch(worldLabel, prefix,
                plans.stream().mapToInt(plan -> plan.bounds().chunkCount()).sum(),
                overlappingCleanupWorlds(plans));
        scheduleAreaClear(plans, keepBlocks, protectionRadius(config, false), batch);
    }

    private Set<String> overlappingCleanupWorlds(List<CleanupWorldPlan> plans) {
        Set<String> overlapping = new HashSet<>();
        for (int index = 0; index < plans.size(); index++) {
            CleanupWorldPlan first = plans.get(index);
            for (int otherIndex = index + 1; otherIndex < plans.size(); otherIndex++) {
                CleanupWorldPlan other = plans.get(otherIndex);
                if (first.world().equals(other.world()) && first.bounds().overlaps(other.bounds())) {
                    overlapping.add(first.world().getName());
                    break;
                }
            }
        }
        return Set.copyOf(overlapping);
    }

    private void scheduleAreaClear(List<CleanupWorldPlan> plans, Set<Material> keepBlocks,
                                   int protectionRadius, CleanupBatch batch) {
        Set<ProtectionScanRequest> scans = new LinkedHashSet<>();
        int chunkRadius = protectionChunkRadius(protectionRadius);
        for (CleanupWorldPlan plan : plans) {
            Bounds bounds = plan.bounds();
            for (int chunkX = bounds.minChunkX() - chunkRadius; chunkX <= bounds.maxChunkX() + chunkRadius; chunkX++) {
                for (int chunkZ = bounds.minChunkZ() - chunkRadius; chunkZ <= bounds.maxChunkZ() + chunkRadius; chunkZ++) {
                    scans.add(new ProtectionScanRequest(plan.world(), chunkX, chunkZ));
                }
            }
        }
        List<ProtectionScanRequest> scanList = new ArrayList<>(scans);
        batch.expectedPlayerScans = scanList.size();
        AtomicInteger nextScan = new AtomicInteger();
        Runnable[] scheduleNext = new Runnable[1];
        scheduleNext[0] = () -> {
            int index = nextScan.getAndIncrement();
            if (index >= scanList.size()) {
                return;
            }
            ProtectionScanRequest scan = scanList.get(index);
            scheduleCleanupProtectionScan(scan, plans, keepBlocks, protectionRadius, batch,
                    scheduleNext[0]);
        };
        for (int index = 0; index < Math.min(MAX_CLEANUP_CHUNK_LOADS, scanList.size()); index++) {
            scheduleNext[0].run();
        }
    }

    private void scheduleCleanupProtectionScan(ProtectionScanRequest scan,
                                               List<CleanupWorldPlan> plans,
                                               Set<Material> keepBlocks,
                                               int protectionRadius,
                                               CleanupBatch batch,
                                               Runnable scanDone) {
        loadChunkForRestore(scan.world(), scan.chunkX(), scan.chunkZ(), chunk -> {
            try {
                plugin.getServer().getRegionScheduler().run(plugin, scan.world(), scan.chunkX(), scan.chunkZ(), task -> {
                    try {
                        Set<PlayerPosition> positions = batch.playerPositions
                                .computeIfAbsent(scan.world().getName(), ignored -> ConcurrentHashMap.newKeySet());
                        for (Entity entity : chunk.getEntities()) {
                            if (entity instanceof Player player) {
                                positions.add(playerPosition(player));
                            }
                        }
                    } catch (RuntimeException error) {
                        plugin.getLogger().log(Level.WARNING,
                                "扫描清理保护区块失败: " + scan.world().getName() + " "
                                        + scan.chunkX() + "," + scan.chunkZ(), error);
                    } finally {
                        completeCleanupProtectionScan(plans, keepBlocks, protectionRadius, batch, scanDone);
                    }
                });
            } catch (RuntimeException error) {
                plugin.getLogger().log(Level.WARNING,
                        "安排清理保护扫描失败: " + scan.world().getName() + " "
                                + scan.chunkX() + "," + scan.chunkZ(), error);
                completeCleanupProtectionScan(plans, keepBlocks, protectionRadius, batch, scanDone);
            }
        }, () -> {
            plugin.getLogger().warning("异步加载清理保护区块失败: " + scan.world().getName() + " "
                    + scan.chunkX() + "," + scan.chunkZ());
            completeCleanupProtectionScan(plans, keepBlocks, protectionRadius, batch, scanDone);
        });
    }

    private void completeCleanupProtectionScan(List<CleanupWorldPlan> plans,
                                               Set<Material> keepBlocks,
                                               int protectionRadius,
                                               CleanupBatch batch,
                                               Runnable scanDone) {
        if (batch.playerScansCompleted.incrementAndGet() == batch.expectedPlayerScans) {
            applyAreaClear(plans, keepBlocks, protectionRadius, batch);
        } else {
            scanDone.run();
        }
    }

    private void applyAreaClear(List<CleanupWorldPlan> plans, Set<Material> keepBlocks,
                                int protectionRadius, CleanupBatch batch) {
        List<CleanupChunkProgress> chunks = new ArrayList<>();
        for (CleanupWorldPlan plan : plans) {
            Bounds bounds = plan.bounds();
            Set<PlayerPosition> players = batch.playerPositions.getOrDefault(plan.world().getName(), Set.of());
            Set<Long> processedBlocks = batch.overlappingWorlds.contains(plan.world().getName())
                    ? batch.processedBlocks.computeIfAbsent(plan.world().getName(),
                    ignored -> ConcurrentHashMap.newKeySet()) : Set.of();
            for (int chunkX = bounds.minChunkX(); chunkX <= bounds.maxChunkX(); chunkX++) {
                for (int chunkZ = bounds.minChunkZ(); chunkZ <= bounds.maxChunkZ(); chunkZ++) {
                    final int cx = chunkX;
                    final int cz = chunkZ;
                    chunks.add(new CleanupChunkProgress(plan.world(), bounds, cx, cz,
                            players, protectionRadius, processedBlocks, keepBlocks,
                            cleanupPlayerNearby(players, cx, cz)));
                }
            }
        }
        AtomicInteger nextChunk = new AtomicInteger();
        Runnable[] scheduleNext = new Runnable[1];
        scheduleNext[0] = () -> {
            int index = nextChunk.getAndIncrement();
            if (index < chunks.size()) {
                scheduleCleanupChunk(chunks.get(index), batch, scheduleNext[0]);
            }
        };
        for (int index = 0; index < Math.min(MAX_CLEANUP_CHUNK_LOADS, chunks.size()); index++) {
            scheduleNext[0].run();
        }
    }

    private void scheduleCleanupChunk(CleanupChunkProgress progress, CleanupBatch batch, Runnable chunkDone) {
        loadChunkForRestore(progress.world, progress.chunkX, progress.chunkZ, chunk -> {
            progress.chunk = chunk;
            try {
                plugin.getServer().getRegionScheduler().run(plugin, progress.world,
                        progress.chunkX, progress.chunkZ,
                        task -> processCleanupChunk(progress, batch, chunkDone));
            } catch (RuntimeException error) {
                plugin.getLogger().log(Level.WARNING,
                        "安排清理区块失败: " + progress.world.getName() + " "
                                + progress.chunkX + "," + progress.chunkZ, error);
                completeCleanupChunk(progress, batch, chunkDone);
            }
        }, () -> {
            plugin.getLogger().warning("异步加载清理区块失败: " + progress.world.getName() + " "
                    + progress.chunkX + "," + progress.chunkZ);
            completeCleanupChunk(progress, batch, chunkDone);
        });
    }

    private boolean cleanupPlayerNearby(Set<PlayerPosition> players, int chunkX, int chunkZ) {
        for (PlayerPosition player : players) {
            if (Math.abs((player.x() >> 4) - chunkX) <= 1
                    && Math.abs((player.z() >> 4) - chunkZ) <= 1) {
                return true;
            }
        }
        return false;
    }

    private void processCleanupChunk(CleanupChunkProgress progress, CleanupBatch batch, Runnable chunkDone) {
        int removedInBatch = 0;
        int protectedInBatch = 0;
        boolean failed = false;
        try {
            if (!progress.initialized) {
                progress.snapshot = progress.chunk.getChunkSnapshot(false, false, false, false);
                progress.protectedBlocks = protectedBlocks(progress.players, progress.startX, progress.endX,
                        progress.startZ, progress.endZ, progress.bounds.minY(), progress.bounds.maxY(),
                        progress.protectionRadius);
                progress.nextX = progress.startX;
                progress.nextY = progress.bounds.minY();
                progress.nextZ = progress.startZ;
                progress.initialized = true;
            }
            int budget = progress.playerNearby ? ONLINE_CLEANUP_BLOCK_BUDGET : OFFLINE_CLEANUP_BLOCK_BUDGET;
            int processed = 0;
            while (progress.hasNext() && processed++ < budget) {
                int x = progress.nextX;
                int y = progress.nextY;
                int z = progress.nextZ;
                progress.advance();
                long key = blockKey(x, y, z);
                if (!progress.processedBlocks.isEmpty() && !progress.processedBlocks.add(key)) {
                    continue;
                }
                if (progress.protectedBlocks.contains(key)) {
                    protectedInBatch++;
                    continue;
                }
                Material type = progress.snapshot.getBlockType(x & 15, y, z & 15);
                if (type != Material.AIR && !progress.keepBlocks.contains(type)) {
                    progress.chunk.getBlock(x & 15, y, z & 15).setType(Material.AIR, false);
                    removedInBatch++;
                }
            }
        } catch (RuntimeException error) {
            failed = true;
            plugin.getLogger().log(Level.WARNING,
                    "处理清理区块失败: " + progress.world.getName() + " "
                            + progress.chunkX + "," + progress.chunkZ, error);
        }
        batch.removedBlocks.addAndGet(removedInBatch);
        batch.protectedBlocks.addAndGet(protectedInBatch);
        if (!failed && progress.hasNext()) {
            try {
                plugin.getServer().getRegionScheduler().runDelayed(plugin, progress.world,
                        progress.chunkX, progress.chunkZ,
                        task -> processCleanupChunk(progress, batch, chunkDone), 1L);
                return;
            } catch (RuntimeException error) {
                plugin.getLogger().log(Level.WARNING,
                        "安排清理区块后续任务失败: " + progress.world.getName() + " "
                                + progress.chunkX + "," + progress.chunkZ, error);
            }
        }
        completeCleanupChunk(progress, batch, chunkDone);
    }

    private void completeCleanupChunk(CleanupChunkProgress progress, CleanupBatch batch, Runnable chunkDone) {
        if (progress.chunk != null) {
            try {
                batch.removedEntities.addAndGet(removeNonPlayerEntities(progress.chunk,
                        progress.bounds));
            } catch (RuntimeException error) {
                plugin.getLogger().log(Level.WARNING,
                        "清理区块实体失败: " + progress.world.getName() + " "
                                + progress.chunkX + "," + progress.chunkZ, error);
            }
        }
        if (batch.chunksProcessed.incrementAndGet() == batch.totalChunks) {
            String message = plugin.message("finish_cleanup",
                    "<color:#B9E7FF><bold>Cleanup complete in {world}. Blocks: {blocks}, protected: {protected}, entities: {entities}, time: {time}ms.</bold></color>");
            message = plugin.replaceVariables(replaceWorldVariables(message, batch.worldLabel),
                    "{blocks}", String.valueOf(batch.removedBlocks.get()),
                    "{protected}", String.valueOf(batch.protectedBlocks.get()),
                    "{entities}", String.valueOf(batch.removedEntities.get()),
                    "{time}", String.valueOf(System.currentTimeMillis() - batch.startTime));
            Bukkit.broadcast(plugin.deserializeInGame(batch.prefix, message));
        }
        chunkDone.run();
    }

    private String replaceWorldVariables(String message, String worldLabel) {
        return plugin.replaceVariables(message, "{world}", worldLabel, "{worlds}", worldLabel);
    }

    private int protectionRadius(FileConfiguration config, boolean restore) {
        String path = restore ? "recreate.player_protection_radius" : "cleanup.player_protection_radius";
        return Math.max(0, config.getInt(path, 2));
    }

    private int protectionChunkRadius(int radius) {
        return radius / 16 + (radius % 16 == 0 ? 0 : 1);
    }

    private PlayerPosition playerPosition(Player player) {
        return new PlayerPosition(player.getLocation().getBlockX(), player.getLocation().getBlockY(),
                player.getLocation().getBlockZ());
    }

    private Set<Long> protectedBlocks(Set<PlayerPosition> players, int minX, int maxX, int minZ, int maxZ,
                                      int minY, int maxY, int radius) {
        if (radius <= 0 || players.isEmpty()) {
            if (radius == 0) {
                Set<Long> occupied = new HashSet<>();
                for (PlayerPosition player : players) {
                    if (player.x() >= minX && player.x() <= maxX
                            && player.y() >= minY && player.y() <= maxY
                            && player.z() >= minZ && player.z() <= maxZ) {
                        occupied.add(blockKey(player.x(), player.y(), player.z()));
                    }
                }
                return occupied;
            }
            return Set.of();
        }

        Set<Long> protectedBlocks = new HashSet<>();
        long radiusSquared = (long) radius * radius;
        for (PlayerPosition player : players) {
            int startX = Math.max(minX, player.x() - radius);
            int endX = Math.min(maxX, player.x() + radius);
            int startY = Math.max(minY, player.y() - radius);
            int endY = Math.min(maxY, player.y() + radius);
            int startZ = Math.max(minZ, player.z() - radius);
            int endZ = Math.min(maxZ, player.z() + radius);
            for (int x = startX; x <= endX; x++) {
                int dx = x - player.x();
                for (int y = startY; y <= endY; y++) {
                    int dy = y - player.y();
                    for (int z = startZ; z <= endZ; z++) {
                        int dz = z - player.z();
                        if ((long) dx * dx + (long) dy * dy + (long) dz * dz <= radiusSquared) {
                            protectedBlocks.add(blockKey(x, y, z));
                        }
                    }
                }
            }
        }
        return protectedBlocks;
    }

    private void runTerrainRestore(FileConfiguration config) {
        if (!restoreRunning.compareAndSet(false, true)) {
            plugin.getLogger().warning("已有地形热恢复正在进行，忽略本次重叠任务。" );
            return;
        }

        List<RestoreTarget> targets = new ArrayList<>();
        for (ConfiguredWorld configuredWorld : configuredWorlds(config, "recreate")) {
            try {
                World targetWorld = Bukkit.getWorld(configuredWorld.name());
                if (targetWorld == null) {
                    plugin.getLogger().warning("未找到配置中指定的热恢复世界: " + configuredWorld.name());
                    continue;
                }
                Path templatePath = templatePath(configuredWorld.name());
                if (templatePath == null || !Files.isDirectory(templatePath.resolve("region"))) {
                    plugin.getLogger().warning("未找到热恢复模板目录: templates/" + configuredWorld.name() + "/region/");
                    continue;
                }
                List<Bounds> bounds = configuredBoundsList(configuredWorld, targetWorld, true, templatePath);
                if (!bounds.isEmpty()) {
                    targets.add(new RestoreTarget(targetWorld, bounds, templatePath));
                }
            } catch (RuntimeException error) {
                plugin.getLogger().log(Level.WARNING,
                        "准备热恢复世界失败: " + configuredWorld.name(), error);
            }
        }
        if (targets.isEmpty()) {
            plugin.getLogger().warning("recreate.worlds 未配置任何可恢复的世界和区域。" );
            restoreRunning.set(false);
            return;
        }

        String worldLabel = targets.stream().map(target -> target.targetWorld().getName()).distinct()
                .reduce((left, right) -> left + ", " + right).orElse("");
        String prefix = plugin.message("prefix", "<color:#8A2387><bold>[</bold><color:#E62028><bold>WorldAreaReset</bold></color><color:#8A2387><bold>]</bold></color> <color:#555555><bold>»</bold></color> <color:#B9E7FF>");
        String startMessage = replaceWorldVariables(plugin.message("start_restore",
                "<color:#B9E7FF>Terrain restoration is now running in {world}. Please wait...</color>"), worldLabel);
        Bukkit.broadcast(plugin.deserializeInGame(prefix, startMessage));
        RestoreBatch batch = new RestoreBatch(worldLabel, prefix, targets.size(), restoreOptions(config),
                restoreBlockBudget(config));

        startNextRestoreTarget(config, targets, new AtomicInteger(), batch);
    }

    private void startNextRestoreTarget(FileConfiguration config, List<RestoreTarget> targets,
                                        AtomicInteger nextTarget, RestoreBatch batch) {
        int index = nextTarget.getAndIncrement();
        if (index >= targets.size()) {
            return;
        }
        RestoreTarget target = targets.get(index);
        plugin.getLogger().info("开始热恢复世界 / Starting terrain restore: " + target.targetWorld().getName());
        AtomicBoolean completed = new AtomicBoolean();
        int restoredBefore = batch.restoredBlocks.get();
        int failedBefore = batch.failedChunks.get();
        Runnable completion = () -> {
            if (!completed.compareAndSet(false, true)) {
                return;
            }
            plugin.getLogger().info("完成热恢复世界 / Finished terrain restore: " + target.targetWorld().getName()
                    + " blocks=" + (batch.restoredBlocks.get() - restoredBefore)
                    + " failed=" + (batch.failedChunks.get() - failedBefore));
            finishRestoreRequest(batch);
            scheduleNextRestoreTarget(config, targets, nextTarget, batch);
        };
        try {
            startRestoreFromTemplate(config, target.targetWorld(), target.bounds(), target.templatePath(),
                    batch, completion);
        } catch (RuntimeException error) {
            batch.failedChunks.incrementAndGet();
            plugin.getLogger().log(Level.WARNING,
                    "启动热恢复世界失败: " + target.targetWorld().getName(), error);
            completion.run();
        }
    }

    private void scheduleNextRestoreTarget(FileConfiguration config, List<RestoreTarget> targets,
                                           AtomicInteger nextTarget, RestoreBatch batch) {
        try {
            plugin.getServer().getAsyncScheduler().runNow(plugin,
                    task -> startNextRestoreTarget(config, targets, nextTarget, batch));
        } catch (RuntimeException error) {
            // A shutting-down scheduler should not leave the restore batch locked forever.
            batch.failedChunks.incrementAndGet();
            plugin.getLogger().log(Level.WARNING, "安排下一个热恢复世界失败，将直接继续", error);
            startNextRestoreTarget(config, targets, nextTarget, batch);
        }
    }

    private void startRestoreFromTemplate(FileConfiguration config, World targetWorld,
                                          List<Bounds> boundsList, Path stagedTemplatePath,
                                          RestoreBatch batch, Runnable completion) {
        AnvilTemplateReader sourceReader = new AnvilTemplateReader(
                stagedTemplatePath.resolve("region"), plugin.getLogger());
        List<RestorePlan> plans = new ArrayList<>();
        List<SourceRead> reads = new ArrayList<>();
        for (Bounds bounds : boundsList) {
            // Template and target blocks use the same absolute XYZ coordinates.
            int sourceOriginX = bounds.minX();
            int sourceOriginY = bounds.minY();
            int sourceOriginZ = bounds.minZ();
            for (int cx = bounds.minChunkX(); cx <= bounds.maxChunkX(); cx++) {
                for (int cz = bounds.minChunkZ(); cz <= bounds.maxChunkZ(); cz++) {
                    int startX = Math.max(bounds.minX(), cx << 4);
                    int endX = Math.min(bounds.maxX(), (cx << 4) + 15);
                    int startZ = Math.max(bounds.minZ(), cz << 4);
                    int endZ = Math.min(bounds.maxZ(), (cz << 4) + 15);
                    RestorePlan plan = new RestorePlan(cx, cz, startX, endX, startZ, endZ,
                            bounds.minY(), bounds.maxY(), sourceReader, sourceOriginX, sourceOriginY, sourceOriginZ);
                    plans.add(plan);

                    int sourceStartX = sourceOriginX + startX - bounds.minX();
                    int sourceEndX = sourceOriginX + endX - bounds.minX();
                    int sourceStartZ = sourceOriginZ + startZ - bounds.minZ();
                    int sourceEndZ = sourceOriginZ + endZ - bounds.minZ();
                    for (int sourceChunkX = sourceStartX >> 4; sourceChunkX <= sourceEndX >> 4; sourceChunkX++) {
                        for (int sourceChunkZ = sourceStartZ >> 4; sourceChunkZ <= sourceEndZ >> 4; sourceChunkZ++) {
                            reads.add(new SourceRead(plan, sourceChunkX, sourceChunkZ,
                                    sourceStartX, sourceEndX, sourceStartZ, sourceEndZ,
                                    bounds.minX(), bounds.minZ()));
                        }
                    }
                }
            }
        }

        if (reads.isEmpty()) {
            batch.failedChunks.incrementAndGet();
            abortRestore(completion);
            return;
        }

        // Keep file I/O and NBT decoding off region threads. One task per target also
        // avoids creating thousands of scheduler tasks for a large restore area.
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            try {
                for (SourceRead read : reads) {
                    readSnapshot(read);
                }
                for (RestorePlan plan : plans) {
                    plan.compactPresentStates();
                }
                if (sourceReader.hasReadErrors()) {
                    batch.failedChunks.incrementAndGet();
                    abortRestore(completion);
                } else {
                    scheduleRestoreApply(targetWorld, plans, batch, completion,
                            protectionRadius(config, true));
                }
            } catch (Exception error) {
                plugin.getLogger().log(Level.WARNING, "读取默认地形模板失败", error);
                batch.failedChunks.incrementAndGet();
                abortRestore(completion);
            }
        });
    }

    private void readSnapshot(SourceRead read) throws IOException {
        RestorePlan plan = read.plan();
        int startX = Math.max(read.sourceStartX(), read.sourceChunkX() << 4);
        int endX = Math.min(read.sourceEndX(), (read.sourceChunkX() << 4) + 15);
        int startZ = Math.max(read.sourceStartZ(), read.sourceChunkZ() << 4);
        int endZ = Math.min(read.sourceEndZ(), (read.sourceChunkZ() << 4) + 15);
        int sourceStartY = plan.sourceOriginY;
        int sourceEndY = plan.sourceOriginY + plan.maxY - plan.minY;
        int firstSectionY = Math.floorDiv(sourceStartY, 16);
        int lastSectionY = Math.floorDiv(sourceEndY, 16);
        for (int sectionY = firstSectionY; sectionY <= lastSectionY; sectionY++) {
            String[] section = plan.sourceReader.blockDataSectionIfPresent(
                    read.sourceChunkX(), read.sourceChunkZ(), sectionY);
            if (section == null) {
                // Sparse templates are merged: an omitted source section leaves the target intact.
                continue;
            }
            int sectionStartY = Math.max(sourceStartY, sectionY << 4);
            int sectionEndY = Math.min(sourceEndY, (sectionY << 4) + 15);
            for (int sourceX = startX; sourceX <= endX; sourceX++) {
                int localX = Math.floorMod(sourceX, 16);
                for (int sourceZ = startZ; sourceZ <= endZ; sourceZ++) {
                    int localZ = Math.floorMod(sourceZ, 16);
                    for (int sourceY = sectionStartY; sourceY <= sectionEndY; sourceY++) {
                        int targetX = read.targetMinX() + sourceX - plan.sourceOriginX;
                        int targetY = plan.minY + sourceY - plan.sourceOriginY;
                        int targetZ = read.targetMinZ() + sourceZ - plan.sourceOriginZ;
                        plan.states[plan.index(targetX, targetY, targetZ)] =
                                section[((sourceY & 15) << 8) | (localZ << 4) | localX];
                    }
                }
            }
        }
    }

    private void scheduleRestoreApply(World targetWorld, List<RestorePlan> plans,
                                      RestoreBatch batch, Runnable completion, int protectionRadius) {
        if (plans.isEmpty()) {
            completion.run();
            return;
        }

        Set<Long> scanKeys = new HashSet<>();
        int chunkRadius = protectionChunkRadius(protectionRadius);
        for (RestorePlan plan : plans) {
            for (int chunkX = plan.chunkX - chunkRadius; chunkX <= plan.chunkX + chunkRadius; chunkX++) {
                for (int chunkZ = plan.chunkZ - chunkRadius; chunkZ <= plan.chunkZ + chunkRadius; chunkZ++) {
                    scanKeys.add(chunkKey(chunkX, chunkZ));
                }
            }
        }
        List<ProtectionScanRequest> scans = scanKeys.stream()
                .map(key -> new ProtectionScanRequest(targetWorld, chunkKeyX(key), chunkKeyZ(key)))
                .toList();
        Set<Long> targetChunkKeys = plans.stream()
                .map(plan -> chunkKey(plan.chunkX, plan.chunkZ))
                .collect(java.util.stream.Collectors.toSet());
        if (!targetWorldHasPlayers(targetWorld)) {
            scheduleRestoreTargetLoads(targetWorld, targetChunkKeys, plans, batch, completion,
                    protectionRadius);
            return;
        }
        Set<PlayerPosition> playerPositions = ConcurrentHashMap.newKeySet();
        AtomicInteger nextScan = new AtomicInteger();
        AtomicInteger scansCompleted = new AtomicInteger();
        AtomicBoolean scanFailed = new AtomicBoolean();
        for (int index = 0; index < Math.min(MAX_RESTORE_CHUNK_LOADS, scans.size()); index++) {
            scheduleNextRestoreScan(targetWorld, scans, nextScan, scansCompleted, scanFailed,
                    targetChunkKeys, playerPositions, plans, batch, completion,
                    protectionRadius);
        }
    }

    private boolean targetWorldHasPlayers(World targetWorld) {
        try {
            return !targetWorld.getPlayers().isEmpty();
        } catch (RuntimeException error) {
            plugin.getLogger().log(Level.WARNING,
                    "检查恢复世界玩家失败，将启用玩家保护扫描: " + targetWorld.getName(), error);
            return true;
        }
    }

    private void scheduleRestoreTargetLoads(World targetWorld, Set<Long> targetChunkKeys,
                                             List<RestorePlan> plans, RestoreBatch batch,
                                             Runnable completion, int protectionRadius) {
        List<Long> chunks = new ArrayList<>(targetChunkKeys);
        if (chunks.isEmpty()) {
            completion.run();
            return;
        }
        AtomicInteger nextChunk = new AtomicInteger();
        AtomicInteger chunksCompleted = new AtomicInteger();
        AtomicBoolean loadFailed = new AtomicBoolean();
        for (int index = 0; index < Math.min(MAX_RESTORE_CHUNK_LOADS, chunks.size()); index++) {
            scheduleNextRestoreTargetLoad(targetWorld, chunks, nextChunk, chunksCompleted, loadFailed,
                    targetChunkKeys, plans, batch, completion, protectionRadius);
        }
    }

    private void scheduleNextRestoreTargetLoad(World targetWorld, List<Long> chunks,
                                               AtomicInteger nextChunk, AtomicInteger chunksCompleted,
                                               AtomicBoolean loadFailed, Set<Long> targetChunkKeys,
                                               List<RestorePlan> plans, RestoreBatch batch,
                                               Runnable completion, int protectionRadius) {
        int index = nextChunk.getAndIncrement();
        if (index >= chunks.size()) {
            return;
        }
        long key = chunks.get(index);
        int chunkX = chunkKeyX(key);
        int chunkZ = chunkKeyZ(key);
        loadChunkForRestore(targetWorld, chunkX, chunkZ, ignored ->
                plugin.getServer().getRegionScheduler().run(plugin, targetWorld, chunkX, chunkZ, task -> {
                    try {
                        targetWorld.addPluginChunkTicket(chunkX, chunkZ, plugin);
                    } catch (RuntimeException error) {
                        loadFailed.set(true);
                        batch.failedChunks.incrementAndGet();
                        plugin.getLogger().log(Level.WARNING,
                                "添加恢复区块票失败: " + targetWorld.getName() + " " + chunkX + "," + chunkZ,
                                error);
                    } finally {
                        completeRestoreTargetLoad(targetWorld, chunks, nextChunk, chunksCompleted,
                                loadFailed, targetChunkKeys, plans, batch, completion, protectionRadius);
                    }
                }), () -> {
                    loadFailed.set(true);
                    batch.failedChunks.incrementAndGet();
                    completeRestoreTargetLoad(targetWorld, chunks, nextChunk, chunksCompleted,
                            loadFailed, targetChunkKeys, plans, batch, completion, protectionRadius);
                });
    }

    private void completeRestoreTargetLoad(World targetWorld, List<Long> chunks,
                                            AtomicInteger nextChunk, AtomicInteger chunksCompleted,
                                            AtomicBoolean loadFailed, Set<Long> targetChunkKeys,
                                            List<RestorePlan> plans, RestoreBatch batch,
                                            Runnable completion, int protectionRadius) {
        if (chunksCompleted.incrementAndGet() == chunks.size()) {
            if (loadFailed.get()) {
                releaseRestoreChunkTickets(targetWorld, targetChunkKeys);
                completion.run();
            } else {
                applyRestoreBlocks(targetWorld, plans, batch, completion, protectionRadius, Set.of());
            }
            return;
        }
        scheduleNextRestoreTargetLoad(targetWorld, chunks, nextChunk, chunksCompleted, loadFailed,
                targetChunkKeys, plans, batch, completion, protectionRadius);
    }

    private void scheduleNextRestoreScan(World targetWorld, List<ProtectionScanRequest> scans,
                                         AtomicInteger nextScan, AtomicInteger scansCompleted,
                                         AtomicBoolean scanFailed, Set<Long> targetChunkKeys,
                                         Set<PlayerPosition> playerPositions, List<RestorePlan> plans,
                                         RestoreBatch batch, Runnable completion, int protectionRadius) {
        int index = nextScan.getAndIncrement();
        if (index >= scans.size()) {
            return;
        }
        ProtectionScanRequest scan = scans.get(index);
        loadChunkForRestore(targetWorld, scan.chunkX(), scan.chunkZ(), chunk ->
                        plugin.getServer().getRegionScheduler().run(plugin, targetWorld,
                        scan.chunkX(), scan.chunkZ(), task -> {
                            boolean targetChunk = targetChunkKeys.contains(
                                    chunkKey(scan.chunkX(), scan.chunkZ()));
                            try {
                                if (targetChunk) {
                                    targetWorld.addPluginChunkTicket(scan.chunkX(), scan.chunkZ(), plugin);
                                }
                                for (Entity entity : chunk.getEntities()) {
                                    if (entity instanceof Player player) {
                                        playerPositions.add(playerPosition(player));
                                    }
                                }
                            } catch (RuntimeException error) {
                                scanFailed.set(true);
                                batch.failedChunks.incrementAndGet();
                                plugin.getLogger().log(Level.WARNING,
                                        "扫描恢复区块中的玩家失败: " + targetWorld.getName() + " "
                                                + scan.chunkX() + "," + scan.chunkZ(), error);
                            } finally {
                                if (!targetChunk) {
                                    try {
                                        targetWorld.removePluginChunkTicket(scan.chunkX(), scan.chunkZ(), plugin);
                                    } catch (RuntimeException error) {
                                        plugin.getLogger().log(Level.WARNING,
                                                "释放恢复扫描区块失败: " + targetWorld.getName() + " "
                                                        + scan.chunkX() + "," + scan.chunkZ(), error);
                                    }
                                }
                                completeRestoreScan(targetWorld, scans, nextScan, scansCompleted,
                                        scanFailed, targetChunkKeys, playerPositions, plans, batch,
                                        completion, protectionRadius);
                            }
                        }), () -> {
                            scanFailed.set(true);
                            batch.failedChunks.incrementAndGet();
                            completeRestoreScan(targetWorld, scans, nextScan, scansCompleted,
                                    scanFailed, targetChunkKeys, playerPositions, plans, batch,
                                    completion, protectionRadius);
                        });
    }

    private void completeRestoreScan(World targetWorld, List<ProtectionScanRequest> scans,
                                     AtomicInteger nextScan, AtomicInteger scansCompleted,
                                     AtomicBoolean scanFailed, Set<Long> targetChunkKeys,
                                     Set<PlayerPosition> playerPositions, List<RestorePlan> plans,
                                     RestoreBatch batch, Runnable completion, int protectionRadius) {
        if (scansCompleted.incrementAndGet() == scans.size()) {
            if (scanFailed.get()) {
                releaseRestoreChunkTickets(targetWorld, targetChunkKeys);
                completion.run();
            } else {
                applyRestoreBlocks(targetWorld, plans, batch, completion, protectionRadius, playerPositions);
            }
            return;
        }
        scheduleNextRestoreScan(targetWorld, scans, nextScan, scansCompleted, scanFailed,
                targetChunkKeys, playerPositions, plans, batch, completion,
                protectionRadius);
    }

    private void loadChunkForRestore(World world, int chunkX, int chunkZ,
                                     Consumer<Chunk> loaded, Runnable failed) {
        try {
            // generate=true handles target areas that have never been loaded. urgent=false
            // lets Paper/Folia perform the disk/generation work without blocking a region.
            world.getChunkAtAsync(chunkX, chunkZ, true, false, chunk -> {
                if (chunk == null) {
                    failed.run();
                } else {
                    try {
                        loaded.accept(chunk);
                    } catch (RuntimeException error) {
                        plugin.getLogger().log(Level.WARNING,
                                "安排恢复区块任务失败: " + world.getName() + " " + chunkX + "," + chunkZ, error);
                        failed.run();
                    }
                }
            });
        } catch (RuntimeException error) {
            plugin.getLogger().log(Level.WARNING,
                    "异步加载恢复区块失败: " + world.getName() + " " + chunkX + "," + chunkZ, error);
            failed.run();
        }
    }

    private void applyRestoreBlocks(World targetWorld, List<RestorePlan> plans,
                                     RestoreBatch batch, Runnable completion, int protectionRadius,
                                     Set<PlayerPosition> players) {
        AtomicInteger chunksProcessed = new AtomicInteger();
        Map<Long, List<RestorePlan>> plansByChunk = new LinkedHashMap<>();
        Map<String, BlockData> blockDataCache = new ConcurrentHashMap<>();
        Set<String> invalidStates = ConcurrentHashMap.newKeySet();
        for (RestorePlan plan : plans) {
            plansByChunk.computeIfAbsent(chunkKey(plan.chunkX, plan.chunkZ),
                    ignored -> new ArrayList<>()).add(plan);
        }
        if (!players.isEmpty()) {
            scheduleOnlineRestoreChunks(targetWorld, new ArrayList<>(plansByChunk.entrySet()), batch,
                    completion, blockDataCache, invalidStates, players, protectionRadius);
            return;
        }
        for (Map.Entry<Long, List<RestorePlan>> entry : plansByChunk.entrySet()) {
            int chunkX = chunkKeyX(entry.getKey());
            int chunkZ = chunkKeyZ(entry.getKey());
            RestoreChunkProgress progress = new RestoreChunkProgress(entry.getValue());
            scheduleRestoreChunk(targetWorld, chunkX, chunkZ, progress, batch, completion,
                    blockDataCache, invalidStates, players, protectionRadius,
                    chunksProcessed, plansByChunk.size(), null);
        }
    }

    private void scheduleOnlineRestoreChunks(World targetWorld,
                                             List<Map.Entry<Long, List<RestorePlan>>> chunks,
                                             RestoreBatch batch, Runnable completion,
                                             Map<String, BlockData> blockDataCache,
                                             Set<String> invalidStates, Set<PlayerPosition> players,
                                             int protectionRadius) {
        if (chunks.isEmpty()) {
            completion.run();
            return;
        }
        AtomicInteger nextChunk = new AtomicInteger();
        AtomicInteger chunksProcessed = new AtomicInteger();
        int totalChunks = chunks.size();
        Runnable[] scheduleNext = new Runnable[1];
        scheduleNext[0] = () -> {
            int index = nextChunk.getAndIncrement();
            if (index >= totalChunks) {
                return;
            }
            Map.Entry<Long, List<RestorePlan>> entry = chunks.get(index);
            int chunkX = chunkKeyX(entry.getKey());
            int chunkZ = chunkKeyZ(entry.getKey());
            Runnable chunkDone = () -> {
                try {
                    plugin.getServer().getRegionScheduler().runDelayed(
                            plugin, targetWorld, chunkX, chunkZ, task -> scheduleNext[0].run(), 1L);
                } catch (RuntimeException error) {
                    batch.failedChunks.incrementAndGet();
                    plugin.getLogger().log(Level.WARNING,
                            "安排下一个恢复区块失败: " + targetWorld.getName() + " " + chunkX + "," + chunkZ,
                            error);
                    completion.run();
                }
            };
            RestoreChunkProgress progress = new RestoreChunkProgress(entry.getValue());
            scheduleRestoreChunk(targetWorld, chunkX, chunkZ, progress, batch, completion,
                    blockDataCache, invalidStates, players, protectionRadius,
                    chunksProcessed, totalChunks, chunkDone);
        };
        for (int index = 0; index < Math.min(ONLINE_RESTORE_CHUNK_CONCURRENCY, totalChunks); index++) {
            scheduleNext[0].run();
        }
    }

    private void scheduleRestoreChunk(World targetWorld, int chunkX, int chunkZ,
                                      RestoreChunkProgress progress, RestoreBatch batch,
                                      Runnable completion,
                                      Map<String, BlockData> blockDataCache, Set<String> invalidStates,
                                      Set<PlayerPosition> players, int protectionRadius,
                                      AtomicInteger chunksProcessed, int totalChunks,
                                      Runnable chunkDone) {
        loadChunkForRestore(targetWorld, chunkX, chunkZ, chunk -> {
            progress.chunk = chunk;
            try {
                plugin.getServer().getRegionScheduler().run(plugin, targetWorld, chunkX, chunkZ,
                        task -> processRestoreChunk(targetWorld, chunkX, chunkZ, progress, batch, completion,
                                blockDataCache, invalidStates, players, protectionRadius,
                                chunksProcessed, totalChunks, chunkDone));
            } catch (RuntimeException error) {
                batch.failedChunks.incrementAndGet();
                plugin.getLogger().log(Level.WARNING,
                        "安排恢复区块失败: " + targetWorld.getName() + " " + chunkX + "," + chunkZ,
                        error);
                completeFailedRestoreChunk(targetWorld, chunkX, chunkZ, progress, batch, completion,
                        chunksProcessed, totalChunks, chunkDone);
            }
        }, () -> {
            batch.failedChunks.incrementAndGet();
            plugin.getLogger().warning("异步加载恢复区块失败: " + targetWorld.getName() + " "
                    + chunkX + "," + chunkZ);
            completeFailedRestoreChunk(targetWorld, chunkX, chunkZ, progress, batch, completion,
                    chunksProcessed, totalChunks, chunkDone);
        });
    }

    private void completeFailedRestoreChunk(World targetWorld, int chunkX, int chunkZ,
                                            RestoreChunkProgress progress, RestoreBatch batch,
                                            Runnable completion, AtomicInteger chunksProcessed,
                                            int totalChunks, Runnable chunkDone) {
        try {
            plugin.getServer().getRegionScheduler().run(plugin, targetWorld, chunkX, chunkZ,
                    task -> completeRestoreChunk(targetWorld, chunkX, chunkZ, progress, batch,
                            completion, chunksProcessed, totalChunks, chunkDone));
        } catch (RuntimeException error) {
            plugin.getLogger().log(Level.WARNING,
                    "安排恢复区块失败收尾任务失败: " + targetWorld.getName() + " " + chunkX + "," + chunkZ,
                    error);
            if (chunksProcessed.incrementAndGet() == totalChunks) {
                completion.run();
            } else if (chunkDone != null) {
                chunkDone.run();
            }
        }
    }

    private void processRestoreChunk(World targetWorld, int chunkX, int chunkZ,
                                     RestoreChunkProgress progress, RestoreBatch batch,
                                     Runnable completion,
                                     Map<String, BlockData> blockDataCache, Set<String> invalidStates,
                                     Set<PlayerPosition> players, int protectionRadius,
                                     AtomicInteger chunksProcessed, int totalChunks,
                                     Runnable chunkDone) {
        int restoredInBatch = 0;
        int ignoredInBatch = 0;
        boolean failed = false;
        try {
            if (!progress.initialized) {
                progress.snapshot = progress.chunk.getChunkSnapshot(false, false, false, false);
                progress.overlappingPlans = plansOverlap(progress.plans);
                progress.simplePath = players.isEmpty() && !batch.options.filtersEnabled()
                        && !progress.overlappingPlans;
                progress.appliedBlocks = progress.overlappingPlans ? new HashSet<>() : null;
                if (!players.isEmpty()) {
                    for (RestorePlan plan : progress.plans) {
                        progress.playerBlocks.addAll(protectedBlocks(players, plan.startX, plan.endX,
                                plan.startZ, plan.endZ, plan.minY, plan.maxY, protectionRadius));
                    }
                }
                progress.initialized = true;
            }

            boolean playersOnline = !players.isEmpty() || targetWorldHasPlayers(targetWorld);
            int budget = !playersOnline ? batch.blocksPerTick
                    : Math.min(batch.blocksPerTick, ONLINE_RESTORE_BLOCK_BUDGET);
            int processed = 0;
            while (processed++ < budget && progress.hasNext()) {
                RestorePlan plan = progress.currentPlan();
                int stateIndex = progress.nextStateIndex();
                int depth = plan.endZ - plan.startZ + 1;
                int height = plan.maxY - plan.minY + 1;
                int x = plan.startX + stateIndex / (height * depth);
                int y = plan.minY + (stateIndex / depth) % height;
                int z = plan.startZ + stateIndex % depth;
                if (progress.simplePath) {
                    BlockData data = simpleBlockData(plan.states[stateIndex], blockDataCache, invalidStates,
                            progress.localBlockDataCache, progress.localInvalidStates);
                    if (data != null && !progress.snapshot.getBlockData(x & 15, y, z & 15).equals(data)) {
                        try {
                            progress.chunk.getBlock(x & 15, y, z & 15).setBlockData(data, false);
                            restoredInBatch++;
                        } catch (RuntimeException error) {
                            batch.failedChunks.incrementAndGet();
                            plugin.getLogger().log(Level.WARNING,
                                    "写入恢复方块失败: " + targetWorld.getName() + " " + x + "," + y + "," + z,
                                    error);
                        }
                    }
                } else {
                    int result = applyRestoreState(targetWorld, progress.chunk, progress.snapshot, plan,
                            x, y, z, x & 15, stateIndex, batch, blockDataCache, invalidStates,
                            progress.localBlockDataCache, progress.localInvalidStates,
                            progress.stateMaterials, progress.appliedBlocks, progress.playerBlocks,
                            !progress.playerBlocks.isEmpty());
                    if (result == RESTORE_RESULT_RESTORED) {
                        restoredInBatch++;
                    } else if (result == RESTORE_RESULT_IGNORED) {
                        ignoredInBatch++;
                    }
                }
            }
        } catch (RuntimeException error) {
            failed = true;
            batch.failedChunks.incrementAndGet();
            plugin.getLogger().log(Level.WARNING,
                    "处理恢复区块失败: " + targetWorld.getName() + " " + chunkX + "," + chunkZ,
                    error);
        }

        batch.restoredBlocks.addAndGet(restoredInBatch);
        batch.ignoredBlocks.addAndGet(ignoredInBatch);
        if (!failed && progress.hasNext()) {
            try {
                plugin.getServer().getRegionScheduler().runDelayed(plugin, targetWorld, chunkX, chunkZ,
                        task -> processRestoreChunk(targetWorld, chunkX, chunkZ, progress, batch, completion,
                                blockDataCache, invalidStates, players, protectionRadius,
                                chunksProcessed, totalChunks, chunkDone), 1L);
                return;
            } catch (RuntimeException error) {
                batch.failedChunks.incrementAndGet();
                plugin.getLogger().log(Level.WARNING,
                        "安排恢复区块后续任务失败: " + targetWorld.getName() + " " + chunkX + "," + chunkZ,
                        error);
            }
        }

        completeRestoreChunk(targetWorld, chunkX, chunkZ, progress, batch, completion,
                chunksProcessed, totalChunks, chunkDone);
    }

    private void completeRestoreChunk(World targetWorld, int chunkX, int chunkZ,
                                      RestoreChunkProgress progress, RestoreBatch batch,
                                      Runnable completion, AtomicInteger chunksProcessed,
                                      int totalChunks, Runnable chunkDone) {
        if (progress.chunk != null) {
            try {
                batch.removedEntities.addAndGet(removeNonPlayerEntities(progress.chunk, progress.plans));
            } catch (RuntimeException error) {
                batch.failedChunks.incrementAndGet();
                plugin.getLogger().log(Level.WARNING,
                        "清理恢复区块实体失败: " + targetWorld.getName() + " " + chunkX + "," + chunkZ,
                        error);
            }
        }
        try {
            targetWorld.removePluginChunkTicket(chunkX, chunkZ, plugin);
        } catch (RuntimeException error) {
            plugin.getLogger().log(Level.WARNING,
                    "释放恢复区块票失败: " + targetWorld.getName() + " " + chunkX + "," + chunkZ,
                    error);
        }
        boolean allChunks = chunksProcessed.incrementAndGet() == totalChunks;
        if (allChunks) {
            completion.run();
        } else if (chunkDone != null) {
            chunkDone.run();
        }
    }

    private int processSimpleRestorePlan(World targetWorld, Chunk chunk, ChunkSnapshot snapshot,
                                         RestorePlan plan, RestoreBatch batch,
                                         Map<String, BlockData> blockDataCache,
                                         Set<String> invalidStates) {
        int restored = 0;
        Map<String, BlockData> localCache = new HashMap<>();
        Set<String> localInvalid = new HashSet<>();
        int depth = plan.endZ - plan.startZ + 1;
        int height = plan.maxY - plan.minY + 1;
        if (plan.denseStates) {
            for (int x = plan.startX; x <= plan.endX; x++) {
                int localX = x & 15;
                int rowOffset = (x - plan.startX) * height * depth;
                for (int y = plan.minY; y <= plan.maxY; y++) {
                    int stateOffset = rowOffset + (y - plan.minY) * depth;
                    for (int z = plan.startZ; z <= plan.endZ; z++) {
                        String state = plan.states[stateOffset + z - plan.startZ];
                        BlockData data = simpleBlockData(state, blockDataCache, invalidStates,
                                localCache, localInvalid);
                        if (data == null || snapshot.getBlockData(localX, y, z & 15).equals(data)) {
                            continue;
                        }
                        try {
                            chunk.getBlock(localX, y, z & 15).setBlockData(data, false);
                            restored++;
                        } catch (RuntimeException error) {
                            batch.failedChunks.incrementAndGet();
                            plugin.getLogger().log(Level.WARNING,
                                    "写入恢复方块失败: " + targetWorld.getName() + " " + x + "," + y + "," + z,
                                    error);
                        }
                    }
                }
            }
            return restored;
        }
        for (int index : plan.presentIndices) {
            int x = plan.startX + index / (height * depth);
            int y = plan.minY + (index / depth) % height;
            int z = plan.startZ + index % depth;
            BlockData data = simpleBlockData(plan.states[index], blockDataCache, invalidStates,
                    localCache, localInvalid);
            if (data == null || snapshot.getBlockData(x & 15, y, z & 15).equals(data)) {
                continue;
            }
            try {
                chunk.getBlock(x & 15, y, z & 15).setBlockData(data, false);
                restored++;
            } catch (RuntimeException error) {
                batch.failedChunks.incrementAndGet();
                plugin.getLogger().log(Level.WARNING,
                        "写入恢复方块失败: " + targetWorld.getName() + " " + x + "," + y + "," + z,
                        error);
            }
        }
        return restored;
    }

    private BlockData simpleBlockData(String state, Map<String, BlockData> sharedCache,
                                      Set<String> sharedInvalid, Map<String, BlockData> localCache,
                                      Set<String> localInvalid) {
        BlockData data = localCache.get(state);
        if (data != null) {
            return data;
        }
        if (localInvalid.contains(state) || sharedInvalid.contains(state)) {
            return null;
        }
        data = sharedCache.get(state);
        if (data == null) {
            try {
                data = Bukkit.createBlockData(state);
                BlockData existing = sharedCache.putIfAbsent(state, data);
                if (existing != null) {
                    data = existing;
                }
            } catch (IllegalArgumentException error) {
                if (sharedInvalid.add(state)) {
                    batchInvalidState(state);
                }
                localInvalid.add(state);
                return null;
            }
        }
        localCache.put(state, data);
        return data;
    }

    private static final int RESTORE_RESULT_NONE = 0;
    private static final int RESTORE_RESULT_RESTORED = 1;
    private static final int RESTORE_RESULT_IGNORED = 2;

    private int applyRestoreState(World targetWorld, Chunk chunk, ChunkSnapshot snapshot,
                                  RestorePlan plan, int x, int y, int z, int localX, int stateIndex,
                                  RestoreBatch batch, Map<String, BlockData> blockDataCache,
                                  Set<String> invalidStates, Map<String, BlockData> localBlockDataCache,
                                  Set<String> localInvalidStates, Map<String, Material> stateMaterials,
                                  Set<Long> appliedBlocks, Set<Long> playerBlocks,
                                  boolean hasProtectedPlayerBlocks) {
        String state = plan.states[stateIndex];
        if (state == null) {
            // Sparse templates are merged: an omitted source section leaves the target intact.
            return RESTORE_RESULT_NONE;
        }
        if (appliedBlocks != null || hasProtectedPlayerBlocks) {
            long key = blockKey(x, y, z);
            if (appliedBlocks != null && !appliedBlocks.add(key)) {
                return RESTORE_RESULT_NONE;
            }
            if (hasProtectedPlayerBlocks && playerBlocks.contains(key)) {
                return RESTORE_RESULT_NONE;
            }
        }
        if (ignoredTemplateState(state, batch.options, stateMaterials)) {
            return RESTORE_RESULT_IGNORED;
        }

        BlockData data = cachedBlockData(state, blockDataCache, invalidStates,
                localBlockDataCache, localInvalidStates);
        if (data == null) {
            return RESTORE_RESULT_NONE;
        }
        int localZ = z & 15;
                    if (snapshot.getBlockData(localX, y, localZ).equals(data)) {
            return RESTORE_RESULT_NONE;
        }
        try {
            chunk.getBlock(localX, y, localZ).setBlockData(data, false);
            return RESTORE_RESULT_RESTORED;
        } catch (RuntimeException error) {
            batch.failedChunks.incrementAndGet();
            plugin.getLogger().log(Level.WARNING,
                    "写入恢复方块失败: " + targetWorld.getName() + " " + x + "," + y + "," + z,
                    error);
            return RESTORE_RESULT_NONE;
        }
    }

    private boolean plansOverlap(List<RestorePlan> plans) {
        for (int index = 0; index < plans.size(); index++) {
            Bounds first = plans.get(index).bounds();
            for (int otherIndex = index + 1; otherIndex < plans.size(); otherIndex++) {
                if (first.overlaps(plans.get(otherIndex).bounds())) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean ignoredTemplateState(String state, RestoreOptions options,
                                         Map<String, Material> stateMaterials) {
        if (!options.ignoreLiquids && options.ignoredBlocks.isEmpty()) {
            return false;
        }
        Material material = stateMaterials.computeIfAbsent(state, this::materialFromState);
        return material != null && (options.ignoredBlocks.contains(material)
                || options.ignoreLiquids && LIQUID_BLOCKS.contains(material));
    }

    private Material materialFromState(String state) {
        int end = state.indexOf('[');
        String name = end >= 0 ? state.substring(0, end) : state;
        int namespaceSeparator = name.indexOf(':');
        if (namespaceSeparator >= 0) {
            name = name.substring(namespaceSeparator + 1);
        }
        try {
            return Material.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    private RestoreOptions restoreOptions(FileConfiguration config) {
        Set<Material> ignoredBlocks = new HashSet<>();
        for (String configuredName : config.getStringList("recreate.ignore_blocks")) {
            Material material = materialFromState(configuredName.trim());
            if (material == null) {
                plugin.getLogger().warning("recreate.ignore_blocks 中存在无效方块类型: " + configuredName);
            } else {
                ignoredBlocks.add(material);
            }
        }
        return new RestoreOptions(config.getBoolean("recreate.ignore_liquids", false),
                Set.copyOf(ignoredBlocks));
    }

    private int restoreBlockBudget(FileConfiguration config) {
        int configured = config.getInt("recreate.blocks_per_tick", DEFAULT_RESTORE_BLOCK_BUDGET);
        if (configured <= 0) {
            plugin.getLogger().warning("recreate.blocks_per_tick 必须大于 0，已回退为 "
                    + DEFAULT_RESTORE_BLOCK_BUDGET + "。" );
            return DEFAULT_RESTORE_BLOCK_BUDGET;
        }
        return Math.min(configured, MAX_RESTORE_BLOCK_BUDGET);
    }

    private BlockData cachedBlockData(String state, Map<String, BlockData> cache,
                                      Set<String> invalidStates) {
        if (state == null || invalidStates.contains(state)) {
            return null;
        }
        BlockData cached = cache.get(state);
        if (cached != null) {
            return cached;
        }
        try {
            BlockData created = Bukkit.createBlockData(state);
            BlockData existing = cache.putIfAbsent(state, created);
            return existing == null ? created : existing;
        } catch (IllegalArgumentException error) {
            if (invalidStates.add(state)) {
                batchInvalidState(state);
            }
            return null;
        }
    }

    private BlockData cachedBlockData(String state, Map<String, BlockData> cache,
                                      Set<String> invalidStates, Map<String, BlockData> localCache,
                                      Set<String> localInvalidStates) {
        if (state == null || invalidStates.contains(state) || localInvalidStates.contains(state)) {
            return null;
        }
        BlockData local = localCache.get(state);
        if (local != null) {
            return local;
        }
        BlockData shared = cache.get(state);
        if (shared != null) {
            localCache.put(state, shared);
            return shared;
        }
        BlockData created = cachedBlockData(state, cache, invalidStates);
        if (created == null) {
            localInvalidStates.add(state);
            return null;
        }
        localCache.put(state, created);
        return created;
    }

    private void batchInvalidState(String state) {
        plugin.getLogger().warning("默认地形模板包含无效方块数据: " + state);
    }

    private void releaseRestoreChunkTickets(World world, Set<Long> chunkKeys) {
        for (long key : chunkKeys) {
            int chunkX = chunkKeyX(key);
            int chunkZ = chunkKeyZ(key);
            plugin.getServer().getRegionScheduler().run(plugin, world, chunkX, chunkZ,
                    task -> world.removePluginChunkTicket(chunkX, chunkZ, plugin));
        }
    }

    private void abortRestore(Runnable completion) {
        completion.run();
    }

    private void completeRestoreRequest(RestoreBatch batch) {
        if (batch.requestsCompleted.incrementAndGet() != batch.totalRequests) {
            return;
        }
        String message = plugin.message("finish_restore",
                "<color:#B9E7FF><bold>Terrain restoration complete in {world}. Blocks restored: {blocks}, ignored template blocks: {ignored}, entities: {entities}, failed: {failed}, time: {time}ms.</bold></color>");
        message = withoutZeroIgnoredNotification(message, batch.ignoredBlocks.get());
        message = plugin.replaceVariables(replaceWorldVariables(message, batch.worldLabel),
                "{blocks}", String.valueOf(batch.restoredBlocks.get()),
                "{ignored}", String.valueOf(batch.ignoredBlocks.get()),
                "{entities}", String.valueOf(batch.removedEntities.get()),
                "{failed}", String.valueOf(batch.failedChunks.get()),
                "{time}", String.valueOf(System.currentTimeMillis() - batch.startTime));
        Bukkit.broadcast(plugin.deserializeInGame(batch.prefix, message));
        restoreRunning.set(false);
    }

    private String withoutZeroIgnoredNotification(String message, int ignoredBlocks) {
        if (ignoredBlocks != 0 || !message.contains("{ignored}")) {
            return message;
        }
        return message.lines()
                .filter(line -> !line.contains("{ignored}"))
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    private void finishRestoreRequest(RestoreBatch batch) {
        completeRestoreRequest(batch);
    }

    private Path templatePath(String folderName) {
        if (folderName.isEmpty() || folderName.contains("..") || folderName.contains("/")
                || folderName.contains("\\") || new File(folderName).isAbsolute()) {
            return null;
        }
        Path root = new File(plugin.getDataFolder(), "templates").toPath().toAbsolutePath().normalize();
        Path path = root.resolve(folderName).normalize();
        return path.startsWith(root) ? path : null;
    }

    private static long blockKey(int x, int y, int z) {
        return ((long) (x & 0x3ffffff) << 38) | ((long) (z & 0x3ffffff) << 12) | (y & 0xfffL);
    }

    private static long chunkKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private static int chunkKeyX(long key) {
        return (int) (key >> 32);
    }

    private static int chunkKeyZ(long key) {
        return (int) key;
    }

    private int removeNonPlayerEntities(Chunk chunk, Bounds bounds) {
        int removed = 0;
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof Player) {
                continue;
            }
            int x = entity.getLocation().getBlockX();
            int y = entity.getLocation().getBlockY();
            int z = entity.getLocation().getBlockZ();
            if (bounds.contains(x, y, z)) {
                entity.remove();
                removed++;
            }
        }
        return removed;
    }

    private int removeNonPlayerEntities(Chunk chunk, List<RestorePlan> plans) {
        int removed = 0;
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof Player) {
                continue;
            }
            int x = entity.getLocation().getBlockX();
            int y = entity.getLocation().getBlockY();
            int z = entity.getLocation().getBlockZ();
            if (plans.stream().anyMatch(plan -> plan.bounds().contains(x, y, z))) {
                entity.remove();
                removed++;
            }
        }
        return removed;
    }

    private List<ConfiguredWorld> configuredWorlds(FileConfiguration config, String section) {
        Map<String, List<Map<?, ?>>> regionsByWorld = new LinkedHashMap<>();
        for (Map<?, ?> values : config.getMapList(section + ".worlds")) {
            Object configuredName = values.get("name");
            String name = configuredName == null ? "" : String.valueOf(configuredName).trim();
            if (name.isEmpty()) {
                plugin.getLogger().warning(section + ".worlds 中存在缺少 name 的世界模块，已跳过。" );
                continue;
            }
            List<Map<?, ?>> regions = regionsByWorld.computeIfAbsent(name, ignored -> new ArrayList<>());
            Object configuredRegions = values.get("regions");
            if (configuredRegions instanceof List<?> list) {
                for (Object region : list) {
                    if (region instanceof Map<?, ?> map) {
                        regions.add(map);
                    }
                }
            }
        }
        return regionsByWorld.entrySet().stream()
                .map(entry -> new ConfiguredWorld(entry.getKey(), List.copyOf(entry.getValue())))
                .toList();
    }

    private List<Bounds> configuredBoundsList(ConfiguredWorld configuredWorld, World world,
                                               boolean allowTemplateChunks, Path templatePath) {
        Set<Bounds> bounds = new LinkedHashSet<>();
        for (Map<?, ?> values : configuredWorld.regions()) {
            Bounds region = mapBounds(values, world);
            if (region != null) {
                bounds.add(region);
            }
        }
        if (!bounds.isEmpty()) {
            return List.copyOf(bounds);
        }
        if (!configuredWorld.regions().isEmpty()) {
            plugin.getLogger().warning("世界模块没有任何有效区域: " + configuredWorld.name());
            return List.of();
        }
        if (allowTemplateChunks) {
            return templateChunkBounds(world, templatePath);
        }
        plugin.getLogger().warning("未给清理世界配置有效区域: " + configuredWorld.name());
        return List.of();
    }

    private Bounds mapBounds(Map<?, ?> values, World world) {
        int minX = number(values.get("min_x"), -200);
        int minY = number(values.get("min_y"), world.getMinHeight());
        int minZ = number(values.get("min_z"), -200);
        int maxX = number(values.get("max_x"), 200);
        int maxY = number(values.get("max_y"), world.getMaxHeight() - 1);
        int maxZ = number(values.get("max_z"), 200);
        if (minY < world.getMinHeight() || maxY >= world.getMaxHeight()) {
            plugin.getLogger().warning("区域 Y 坐标超出世界高度: " + world.getName());
            return null;
        }
        return boundsOrNull(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private int number(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private List<Bounds> templateChunkBounds(World world, Path templatePath) {
        try {
            AnvilTemplateReader reader = new AnvilTemplateReader(
                    templatePath.resolve("region"), plugin.getLogger());
            List<AnvilTemplateReader.TemplateChunkCoordinate> chunks = reader.storedChunks();
            if (chunks.isEmpty()) {
                plugin.getLogger().warning("模板中没有可恢复区块: " + world.getName());
                return List.of();
            }
            List<Bounds> bounds = new ArrayList<>(chunks.size());
            for (AnvilTemplateReader.TemplateChunkCoordinate chunk : chunks) {
                int minX = chunk.x() << 4;
                int minZ = chunk.z() << 4;
                bounds.add(new Bounds(minX, world.getMinHeight(), minZ,
                        minX + 15, world.getMaxHeight() - 1, minZ + 15));
            }
            return bounds;
        } catch (IOException error) {
            plugin.getLogger().log(Level.WARNING, "读取模板区块目录失败: " + templatePath, error);
            return List.of();
        }
    }

    private Bounds boundsOrNull(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            plugin.getLogger().warning("范围无效：每个 min 坐标必须小于或等于对应的 max 坐标。" );
            return null;
        }
        return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        private int minChunkX() { return minX >> 4; }
        private int maxChunkX() { return maxX >> 4; }
        private int minChunkZ() { return minZ >> 4; }
        private int maxChunkZ() { return maxZ >> 4; }
        private int chunkCount() { return (maxChunkX() - minChunkX() + 1) * (maxChunkZ() - minChunkZ() + 1); }
        private boolean contains(int x, int y, int z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }
        private boolean overlaps(Bounds other) {
            return minX <= other.maxX && maxX >= other.minX
                    && minY <= other.maxY && maxY >= other.minY
                    && minZ <= other.maxZ && maxZ >= other.minZ;
        }
    }

    private record CleanupWorldPlan(World world, Bounds bounds) {
    }

    private static final class CleanupChunkProgress {
        private final World world;
        private final Bounds bounds;
        private final int chunkX;
        private final int chunkZ;
        private final Set<PlayerPosition> players;
        private final int protectionRadius;
        private final Set<Long> processedBlocks;
        private final Set<Material> keepBlocks;
        private final boolean playerNearby;
        private final int startX;
        private final int endX;
        private final int startZ;
        private final int endZ;
        private Chunk chunk;
        private ChunkSnapshot snapshot;
        private Set<Long> protectedBlocks;
        private int nextX;
        private int nextY;
        private int nextZ;
        private boolean initialized;

        private CleanupChunkProgress(World world, Bounds bounds, int chunkX, int chunkZ,
                                     Set<PlayerPosition> players, int protectionRadius,
                                     Set<Long> processedBlocks, Set<Material> keepBlocks,
                                     boolean playerNearby) {
            this.world = world;
            this.bounds = bounds;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.players = players;
            this.protectionRadius = protectionRadius;
            this.processedBlocks = processedBlocks;
            this.keepBlocks = keepBlocks;
            this.playerNearby = playerNearby;
            this.startX = Math.max(bounds.minX(), chunkX << 4);
            this.endX = Math.min(bounds.maxX(), (chunkX << 4) + 15);
            this.startZ = Math.max(bounds.minZ(), chunkZ << 4);
            this.endZ = Math.min(bounds.maxZ(), (chunkZ << 4) + 15);
        }

        private boolean hasNext() {
            return nextX <= endX;
        }

        private void advance() {
            nextZ++;
            if (nextZ > endZ) {
                nextZ = startZ;
                nextY++;
                if (nextY > bounds.maxY()) {
                    nextY = bounds.minY();
                    nextX++;
                }
            }
        }
    }

    private static final class CleanupBatch {
        private final String worldLabel;
        private final String prefix;
        private final int totalChunks;
        private final Set<String> overlappingWorlds;
        private final long startTime = System.currentTimeMillis();
        private final AtomicInteger chunksProcessed = new AtomicInteger();
        private final AtomicInteger playerScansCompleted = new AtomicInteger();
        private final AtomicInteger removedBlocks = new AtomicInteger();
        private final AtomicInteger protectedBlocks = new AtomicInteger();
        private final AtomicInteger removedEntities = new AtomicInteger();
        private final Map<String, Set<PlayerPosition>> playerPositions = new ConcurrentHashMap<>();
        private final Map<String, Set<Long>> processedBlocks = new ConcurrentHashMap<>();
        private volatile int expectedPlayerScans;

        private CleanupBatch(String worldLabel, String prefix, int totalChunks,
                             Set<String> overlappingWorlds) {
            this.worldLabel = worldLabel;
            this.prefix = prefix;
            this.totalChunks = totalChunks;
            this.overlappingWorlds = overlappingWorlds;
        }
    }

    private record RestoreTarget(World targetWorld, List<Bounds> bounds, Path templatePath) {
    }

    private record RestoreOptions(boolean ignoreLiquids, Set<Material> ignoredBlocks) {
        private boolean filtersEnabled() {
            return ignoreLiquids || !ignoredBlocks.isEmpty();
        }
    }

    private static final class RestoreBatch {
        private final String worldLabel;
        private final String prefix;
        private final int totalRequests;
        private final RestoreOptions options;
        private final int blocksPerTick;
        private final long startTime = System.currentTimeMillis();
        private final AtomicInteger requestsCompleted = new AtomicInteger();
        private final AtomicInteger restoredBlocks = new AtomicInteger();
        private final AtomicInteger ignoredBlocks = new AtomicInteger();
        private final AtomicInteger removedEntities = new AtomicInteger();
        private final AtomicInteger failedChunks = new AtomicInteger();

        private RestoreBatch(String worldLabel, String prefix, int totalRequests, RestoreOptions options,
                             int blocksPerTick) {
            this.worldLabel = worldLabel;
            this.prefix = prefix;
            this.totalRequests = totalRequests;
            this.options = options;
            this.blocksPerTick = blocksPerTick;
        }
    }

    private static final class RestoreChunkProgress {
        private final List<RestorePlan> plans;
        private final Map<String, BlockData> localBlockDataCache = new HashMap<>();
        private final Set<String> localInvalidStates = new HashSet<>();
        private final Map<String, Material> stateMaterials = new HashMap<>();
        private final Set<Long> playerBlocks = new HashSet<>();
        private Chunk chunk;
        private ChunkSnapshot snapshot;
        private Set<Long> appliedBlocks;
        private boolean initialized;
        private boolean simplePath;
        private boolean overlappingPlans;
        private int planIndex;
        private int stateIndex;

        private RestoreChunkProgress(List<RestorePlan> plans) {
            this.plans = plans;
        }

        private boolean hasNext() {
            while (planIndex < plans.size()) {
                RestorePlan plan = plans.get(planIndex);
                int stateCount = plan.denseStates ? plan.states.length : plan.presentIndices.length;
                if (stateIndex < stateCount) {
                    return true;
                }
                planIndex++;
                stateIndex = 0;
            }
            return false;
        }

        private RestorePlan currentPlan() {
            return plans.get(planIndex);
        }

        private int nextStateIndex() {
            RestorePlan plan = currentPlan();
            int cursor = stateIndex++;
            return plan.denseStates ? cursor : plan.presentIndices[cursor];
        }
    }

    private static final class RestorePlan {
        private final int chunkX;
        private final int chunkZ;
        private final int startX;
        private final int endX;
        private final int startZ;
        private final int endZ;
        private final int minY;
        private final int maxY;
        private final String[] states;
        private int[] presentIndices = new int[0];
        private boolean denseStates;
        private final AnvilTemplateReader sourceReader;
        private final int sourceOriginX;
        private final int sourceOriginY;
        private final int sourceOriginZ;

        private RestorePlan(int chunkX, int chunkZ, int startX, int endX, int startZ, int endZ,
                            int minY, int maxY, AnvilTemplateReader sourceReader,
                            int sourceOriginX, int sourceOriginY, int sourceOriginZ) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.startX = startX;
            this.endX = endX;
            this.startZ = startZ;
            this.endZ = endZ;
            this.minY = minY;
            this.maxY = maxY;
            this.sourceReader = sourceReader;
            this.sourceOriginX = sourceOriginX;
            this.sourceOriginY = sourceOriginY;
            this.sourceOriginZ = sourceOriginZ;
            this.states = new String[(endX - startX + 1) * (endZ - startZ + 1) * (maxY - minY + 1)];
        }

        private int index(int x, int y, int z) {
            int depth = endZ - startZ + 1;
            int height = maxY - minY + 1;
            return ((x - startX) * height + (y - minY)) * depth + (z - startZ);
        }

        private void compactPresentStates() {
            int count = 0;
            for (String state : states) {
                if (state != null) {
                    count++;
                }
            }
            int[] indices = new int[count];
            int next = 0;
            for (int index = 0; index < states.length; index++) {
                if (states[index] != null) {
                    indices[next++] = index;
                }
            }
            presentIndices = indices;
            denseStates = count == states.length;
        }

        private Bounds bounds() {
            return new Bounds(startX, minY, startZ, endX, maxY, endZ);
        }
    }

    private record SourceRead(RestorePlan plan, int sourceChunkX, int sourceChunkZ,
                              int sourceStartX, int sourceEndX, int sourceStartZ, int sourceEndZ,
                              int targetMinX, int targetMinZ) {
    }

    private record ProtectionScanRequest(World world, int chunkX, int chunkZ) {
    }

    private record PlayerPosition(int x, int y, int z) {
    }

    private record ConfiguredWorld(String name, List<Map<?, ?>> regions) {
    }
}
