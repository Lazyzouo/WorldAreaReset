package net.lazyz.worldareareset;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class AreaCleanupTask {

    private final WorldAreaResetPlugin plugin;
    private ScheduledTask timerTask;
    private ScheduledTask delayedTask;

    public AreaCleanupTask(WorldAreaResetPlugin plugin) {
        this.plugin = plugin;
    }

    public synchronized void start() {
        FileConfiguration config = plugin.getConfig();
        if (!config.getBoolean("cleanup.enabled", false)) {
            plugin.getLogger().info("区域清理在配置中处于关闭状态。");
            return;
        }

        long intervalMinutes = config.getLong("cleanup.interval_minutes", 180);
        scheduleAutomaticCleanup(intervalMinutes);
        plugin.getLogger().info("已启动自动区域清理任务，清理周期: " + intervalMinutes + "分钟");
    }

    private void scheduleAutomaticCleanup(long intervalMinutes) {
        if (this.timerTask != null) {
            this.timerTask.cancel();
        }

        this.timerTask = plugin.getServer().getAsyncScheduler().runAtFixedRate(plugin, task -> {
            triggerCountdown();
        }, intervalMinutes, intervalMinutes, TimeUnit.MINUTES);
    }

    public synchronized void stop() {
        if (this.timerTask != null) {
            this.timerTask.cancel();
            this.timerTask = null;
        }
        if (this.delayedTask != null) {
            this.delayedTask.cancel();
            this.delayedTask = null;
        }
    }

    public synchronized void runManualCleanup() {
        stop();
        triggerCountdown();

        FileConfiguration config = plugin.getConfig();
        if (config.getBoolean("cleanup.enabled", false)) {
            long intervalMinutes = config.getLong("cleanup.interval_minutes", 180);
            scheduleAutomaticCleanup(intervalMinutes);
            plugin.getLogger().info("手动区域清理倒计时已启动，自动清理倒计时已重置为 " + intervalMinutes + " 分钟。");
        }
    }

    private synchronized void triggerCountdown() {
        FileConfiguration config = plugin.getConfig();
        String worldName = config.getString("cleanup.world", "world_nether");
        int countdown = config.getInt("cleanup.countdown_seconds", 10);

        String prefix = plugin.message("prefix", "&8[&6WorldAreaReset&8] &r");
        String warningMsg = plugin.message("warning", "&c[Warning] &e{world} &cwill be cleaned in &e{time} &cseconds.");

        warningMsg = warningMsg.replace("{world}", worldName)
                .replace("{time}", String.valueOf(countdown));

        Bukkit.broadcast(plugin.deserializeInGame(prefix, warningMsg));

        this.delayedTask = plugin.getServer().getAsyncScheduler().runDelayed(plugin, task -> {
            synchronized (AreaCleanupTask.this) {
                AreaCleanupTask.this.delayedTask = null;
            }
            runCleanup();
        }, countdown, TimeUnit.SECONDS);
    }

    public void runCleanup() {
        FileConfiguration config = plugin.getConfig();
        String worldName = config.getString("cleanup.world", "world_nether");
        World world = Bukkit.getWorld(worldName);

        if (world == null) {
            plugin.getLogger().warning("未找到配置中指定的清理世界: " + worldName);
            return;
        }

        int minX = config.getInt("cleanup.min_x");
        int minY = config.getInt("cleanup.min_y");
        int minZ = config.getInt("cleanup.min_z");
        int maxX = config.getInt("cleanup.max_x");
        int maxY = config.getInt("cleanup.max_y");
        int maxZ = config.getInt("cleanup.max_z");

        List<String> keepBlocksList = config.getStringList("cleanup.keep_blocks");
        Set<Material> keepBlocks = new HashSet<>();
        for (String matName : keepBlocksList) {
            try {
                keepBlocks.add(Material.valueOf(matName.toUpperCase()));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("配置中存在无效的方块类型: " + matName);
            }
        }

        String prefix = plugin.message("prefix", "&8[&6WorldAreaReset&8] &r");
        String startMsg = plugin.message("start_cleanup", "&eArea cleanup has started. Please wait...");
        Bukkit.broadcast(plugin.deserializeInGame(prefix, startMsg));

        long startTime = System.currentTimeMillis();
        AtomicInteger totalRemovedBlocks = new AtomicInteger(0);
        AtomicInteger totalRemovedEntities = new AtomicInteger(0);

        int minChunkX = minX >> 4;
        int maxChunkX = maxX >> 4;
        int minChunkZ = minZ >> 4;
        int maxChunkZ = maxZ >> 4;

        int totalChunks = (maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1);
        AtomicInteger chunksProcessed = new AtomicInteger(0);

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                final int chunkX = cx;
                final int chunkZ = cz;

                plugin.getServer().getRegionScheduler().run(plugin, world, chunkX, chunkZ, scheduledTask -> {
                    int removedBlocksInChunk = 0;
                    int removedEntitiesInChunk = 0;

                    int startX = Math.max(minX, chunkX << 4);
                    int endX = Math.min(maxX, (chunkX << 4) + 15);
                    int startZ = Math.max(minZ, chunkZ << 4);
                    int endZ = Math.min(maxZ, (chunkZ << 4) + 15);

                    for (int x = startX; x <= endX; x++) {
                        for (int y = minY; y <= maxY; y++) {
                            for (int z = startZ; z <= endZ; z++) {
                                Block block = world.getBlockAt(x, y, z);
                                if (block.getType() != Material.AIR && !keepBlocks.contains(block.getType())) {
                                    block.setType(Material.AIR, false);
                                    removedBlocksInChunk++;
                                }
                            }
                        }
                    }

                    Chunk chunk = world.getChunkAt(chunkX, chunkZ);
                    for (Entity entity : chunk.getEntities()) {
                        if (entity instanceof Player) {
                            continue;
                        }

                        int ex = entity.getLocation().getBlockX();
                        int ey = entity.getLocation().getBlockY();
                        int ez = entity.getLocation().getBlockZ();

                        if (ex >= minX && ex <= maxX && ey >= minY && ey <= maxY && ez >= minZ && ez <= maxZ) {
                            entity.remove();
                            removedEntitiesInChunk++;
                        }
                    }

                    totalRemovedBlocks.addAndGet(removedBlocksInChunk);
                    totalRemovedEntities.addAndGet(removedEntitiesInChunk);

                    int processed = chunksProcessed.incrementAndGet();

                    if (processed == totalChunks) {
                        long timeTaken = System.currentTimeMillis() - startTime;

                        String finishMsg = plugin.message("finish_cleanup", "&aCleanup complete. Blocks: {blocks}, entities: {entities}, time: {time}ms.");
                        finishMsg = finishMsg.replace("{blocks}", String.valueOf(totalRemovedBlocks.get()))
                                .replace("{entities}", String.valueOf(totalRemovedEntities.get()))
                                .replace("{time}", String.valueOf(timeTaken));

                        Bukkit.broadcast(plugin.deserializeInGame(prefix, finishMsg));
                    }
                });
            }
        }
    }
}
