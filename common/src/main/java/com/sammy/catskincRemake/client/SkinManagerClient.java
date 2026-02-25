package com.sammy.catskincRemake.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SkinManagerClient {
    private static final Map<UUID, Identifier> CACHE = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> SLIM = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> PREFERRED_SLIM = new ConcurrentHashMap<>();
    private static final Set<UUID> IN_FLIGHT = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Long> LAST_CHECK = new ConcurrentHashMap<>();
    private static final Map<UUID, String> LAST_URL = new ConcurrentHashMap<>();

    private static volatile long refreshIntervalMs = 1_000L;

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "CatSkinC-SkinManager");
        thread.setDaemon(true);
        return thread;
    });

    private SkinManagerClient() {
    }

    public static void setRefreshIntervalMs(long intervalMs) {
        refreshIntervalMs = Math.max(500L, intervalMs);
        ModLog.debug("Skin refresh interval set to {} ms", refreshIntervalMs);
    }

    public static Identifier getOrFetch(AbstractClientPlayerEntity player) {
        if (player == null) {
            return null;
        }
        return getOrFetch(player.getUuid());
    }

    public static Identifier getOrFetch(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        Identifier cached = CACHE.get(uuid);
        if (cached == null) {
            fetchAndApplyFor(uuid);
            return null;
        }
        if (shouldPoll(uuid)) {
            fetchAndApplyFor(uuid);
        }
        return cached;
    }

    public static Identifier getCached(UUID uuid) {
        return uuid == null ? null : CACHE.get(uuid);
    }

    public static void ensureFetch(UUID uuid) {
        if (uuid == null) {
            return;
        }
        if (!CACHE.containsKey(uuid) || shouldPoll(uuid)) {
            fetchAndApplyFor(uuid);
        }
    }

    public static void forceFetch(UUID uuid) {
        if (uuid == null) {
            return;
        }
        LAST_CHECK.remove(uuid);
        fetchAndApplyFor(uuid);
    }

    public static void refresh(UUID uuid) {
        if (uuid == null) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            TextureManager textureManager = client.getTextureManager();
            Identifier removed = CACHE.remove(uuid);
            if (removed != null) {
                textureManager.destroyTexture(removed);
            }
        }
        LAST_URL.remove(uuid);
        fetchAndApplyFor(uuid);
    }

    public static void fetchAndApplyFor(UUID uuid) {
        if (uuid == null || !IN_FLIGHT.add(uuid)) {
            if (uuid != null) {
                ModLog.trace("Fetch skipped (already in flight): {}", uuid);
            }
            return;
        }
        ModLog.trace("Fetch queued for {}", uuid);

        CompletableFuture<ServerApiClient.SelectedSkin> selected = ServerApiClient.fetchSelectedAsync(uuid);
        selected.thenCompose(skin -> {
            if (skin == null || skin.url() == null || skin.url().isBlank()) {
                ModLog.trace("No remote skin available for {}", uuid);
                return CompletableFuture.completedFuture(null);
            }

            SLIM.put(uuid, skin.slim());

            String previousUrl = LAST_URL.get(uuid);
            if (skin.url().equals(previousUrl)) {
                ModLog.trace("Skipping download for {} (URL unchanged)", uuid);
                return CompletableFuture.completedFuture(null);
            }

            LAST_URL.put(uuid, skin.url());
            return ServerApiClient.downloadTextureAsync(skin.url());
        }).whenCompleteAsync((texture, throwable) -> {
            IN_FLIGHT.remove(uuid);
            if (throwable != null) {
                ModLog.error("Skin apply failed for uuid=" + uuid, throwable);
                return;
            }
            if (texture == null) {
                ModLog.trace("No texture to apply for {}", uuid);
                return;
            }

            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null) {
                ModLog.trace("Client not ready; dropping texture update for {}", uuid);
                return;
            }
            client.execute(() -> {
                Identifier id = idFor(uuid);
                TextureManager textureManager = client.getTextureManager();
                textureManager.destroyTexture(id);
                textureManager.registerTexture(id, texture);
                CACHE.put(uuid, id);
                ModLog.trace("Texture applied for {}", uuid);
            });
        }, EXECUTOR);
    }

    public static Boolean isSlimOrNull(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        Boolean direct = SLIM.get(uuid);
        if (direct != null) {
            return direct;
        }
        return PREFERRED_SLIM.get(uuid);
    }

    public static void setSlim(UUID uuid, boolean slim) {
        if (uuid == null) {
            return;
        }
        SLIM.put(uuid, slim);
        PREFERRED_SLIM.put(uuid, slim);
    }

    public static void clearAll() {
        int cacheSize = CACHE.size();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            TextureManager textureManager = client.getTextureManager();
            for (Identifier id : CACHE.values()) {
                textureManager.destroyTexture(id);
            }
        }
        CACHE.clear();
        SLIM.clear();
        PREFERRED_SLIM.clear();
        LAST_CHECK.clear();
        LAST_URL.clear();
        IN_FLIGHT.clear();
        ModLog.debug("Skin caches cleared ({} entries)", cacheSize);
    }

    private static Identifier idFor(UUID uuid) {
        return Identifiers.mod("remote/" + uuid.toString().replace("-", ""));
    }

    private static boolean shouldPoll(UUID uuid) {
        long now = System.currentTimeMillis();
        long lastCheck = LAST_CHECK.getOrDefault(uuid, 0L);
        if (now - lastCheck < refreshIntervalMs) {
            return false;
        }
        LAST_CHECK.put(uuid, now);
        return true;
    }
}

