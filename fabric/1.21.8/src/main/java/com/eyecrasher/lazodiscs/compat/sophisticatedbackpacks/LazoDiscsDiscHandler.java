package com.eyecrasher.lazodiscs.compat.sophisticatedbackpacks;

import com.eyecrasher.lazodiscs.LazoDiscs;
import com.eyecrasher.lazodiscs.compat.SablePositionCompat;
import com.eyecrasher.lazodiscs.config.LazoDiscsConfig;
import com.eyecrasher.lazodiscs.data.CustomDiscData;
import com.eyecrasher.lazodiscs.data.DiscDataUtil;
import com.eyecrasher.lazodiscs.voice.PlayingVoiceSource;
import com.eyecrasher.lazodiscs.voice.PlasmoVoiceBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LazoDiscs integration with SophisticatedCore's Jukebox Upgrade — REFLECTION-BASED.
 *
 * <p>This class is <b>loader-agnostic</b>: it uses no Fabric or NeoForge event-bus APIs.
 * The server-tick poller is invoked from {@code JukeboxEvents.onServerTick} (which is
 * platform-specific and calls {@link #onServerTick(MinecraftServer)} here).
 *
 * <h3>Where the sound plays</h3>
 * <p>SophisticatedCore itself decides whether a backpack's Jukebox Upgrade plays at a fixed
 * block position or follows an entity — see {@code JukeboxUpgradeContainer.handlePacket}:
 * a block-placed backpack (Sophisticated Storage-style) uses the BlockPos overload of
 * {@code playDisc}; any item-based backpack (worn, held, or just sitting in a regular
 * inventory slot — SophisticatedCore does not distinguish between these) uses the
 * Vec3+entityId overload, with the entity being the backpack's owner. We simply start a
 * Plasmo Voice source immediately for whichever overload is called — no additional
 * hand/armor-slot gating is needed.
 *
 * <p>For an entity-bound source, Plasmo's {@code ServerEntitySource} auto-tracks the
 * entity's position — no per-tick setPosition is needed.
 */
public final class LazoDiscsDiscHandler {

    public static final LazoDiscsDiscHandler INSTANCE = new LazoDiscsDiscHandler();

    private static final Map<UUID, ActiveLazoSource> ACTIVE = new ConcurrentHashMap<>();

    // Reflection-cached SophisticatedCore classes/methods.
    private static volatile boolean reflectionProbed = false;
    private static volatile boolean sophisticatedCoreAvailable = false;
    private static volatile boolean registered = false;
    private static volatile Method discHandlerRegistry_getHandlers;
    private static volatile Method serverStorageSoundHandler_putSoundInfo;
    private static volatile Method serverStorageSoundHandler_stopPlayingDisc;
    private static volatile Class<?> idiscHandlerClass;
    private static volatile Field worldStorageSoundInfosField;

    private LazoDiscsDiscHandler() {
    }

    private static void probeReflection() {
        if (reflectionProbed) return;
        synchronized (LazoDiscsDiscHandler.class) {
            if (reflectionProbed) return;
            try {
                Class<?> registryClass = Class.forName(
                        "net.p3pp3rf1y.sophisticatedcore.upgrades.jukebox.DiscHandlerRegistry");
                Class<?> soundHandlerClass = Class.forName(
                        "net.p3pp3rf1y.sophisticatedcore.upgrades.jukebox.ServerStorageSoundHandler");
                idiscHandlerClass = Class.forName(
                        "net.p3pp3rf1y.sophisticatedcore.api.IDiscHandler");

                discHandlerRegistry_getHandlers = registryClass.getMethod("getHandlers");

                serverStorageSoundHandler_putSoundInfo = soundHandlerClass.getMethod(
                        "putSoundInfo",
                        ServerLevel.class, UUID.class, Runnable.class, Vec3.class, long.class);

                serverStorageSoundHandler_stopPlayingDisc = soundHandlerClass.getMethod(
                        "stopPlayingDisc",
                        Level.class, Vec3.class, UUID.class);

                worldStorageSoundInfosField = soundHandlerClass.getDeclaredField("worldStorageSoundInfos");
                worldStorageSoundInfosField.setAccessible(true);

                sophisticatedCoreAvailable = true;
                LazoDiscs.LOGGER.info(
                        "LazoDiscs detected SophisticatedCore; Jukebox Upgrade integration enabled.");
            } catch (Throwable t) {
                sophisticatedCoreAvailable = false;
                LazoDiscs.LOGGER.debug(
                        "SophisticatedCore not detected; Jukebox Upgrade integration disabled. " +
                        "LazoDiscs will use the vanilla jukebox path only. Reason: {}", t.toString());
            } finally {
                reflectionProbed = true;
            }
        }
    }

    /**
     * Register this handler with SophisticatedCore. Idempotent. No-op if SophisticatedCore
     * isn't installed.
     */
    public static void register() {
        if (registered) return;
        synchronized (LazoDiscsDiscHandler.class) {
            if (registered) return;
            probeReflection();
            if (!sophisticatedCoreAvailable) return;

            try {
                Object proxy = java.lang.reflect.Proxy.newProxyInstance(
                        idiscHandlerClass.getClassLoader(),
                        new Class<?>[]{idiscHandlerClass},
                        (proxyObj, method, args) -> dispatchMethod(proxyObj, method.getName(), args)
                );

                Object handlers = discHandlerRegistry_getHandlers.invoke(null);
                if (handlers instanceof java.util.List<?> list) {
                    @SuppressWarnings("unchecked")
                    java.util.List<Object> rawList = (java.util.List<Object>) list;
                    rawList.add(0, proxy);
                    registered = true;
                    LazoDiscs.LOGGER.info(
                            "Registered LazoDiscs handler with SophisticatedCore " +
                            "(priority before VanillaDiscHandler, via reflection).");
                }
            } catch (Throwable t) {
                LazoDiscs.LOGGER.warn(
                        "Failed to register LazoDiscs handler with SophisticatedCore: {}",
                        t.toString());
            }
        }
    }

    private static Object dispatchMethod(Object proxyObj, String methodName, Object[] args) {
        try {
            switch (methodName) {
                case "supports" -> {
                    ItemStack stack = (ItemStack) args[0];
                    return DiscDataUtil.hasCustomDisc(stack);
                }
                case "getSongInfo" -> {
                    ItemStack stack = (ItemStack) args[0];
                    return DiscDataUtil.read(stack).map(d -> (Object) new LazoSongInfo(d.title()));
                }
                case "getMusicLengthInTicks" -> {
                    return Optional.empty();
                }
                case "getRandomDisc" -> {
                    return Optional.empty();
                }
                case "getMusicDiscSize" -> {
                    return 0;
                }
                case "playDisc" -> {
                    ServerLevel level = (ServerLevel) args[0];
                    UUID storageUuid = (UUID) args[2];
                    ItemStack discStack = (ItemStack) args[3];
                    Runnable onFinished;
                    if (args.length == 5) {
                        BlockPos pos = (BlockPos) args[1];
                        onFinished = (Runnable) args[4];
                        playDiscBlock(level, pos, storageUuid, discStack, onFinished);
                    } else {
                        Vec3 pos = (Vec3) args[1];
                        int entityId = (int) args[4];
                        onFinished = (Runnable) args[5];
                        playDiscEntity(level, pos, storageUuid, discStack, entityId, onFinished);
                    }
                    return null;
                }
                case "toString" -> { return "LazoDiscsDiscHandler-proxy"; }
                case "hashCode" -> { return System.identityHashCode(proxyObj); }
                case "equals" -> { return proxyObj == args[0]; }
                default -> {
                    return null;
                }
            }
        } catch (Throwable t) {
            LazoDiscs.LOGGER.warn("LazoDiscs handler dispatch failed for '{}': {}",
                    methodName, t.toString());
            return null;
        }
    }

    public record LazoSongInfo(String title) {
    }

    // -------------------------------------------------------------------------
    // playDisc — BlockPos overload (block-placed backpack)
    // -------------------------------------------------------------------------
    private static void playDiscBlock(ServerLevel level, BlockPos pos, UUID storageUuid,
                                      ItemStack discStack, Runnable onFinished) {
        DiscDataUtil.read(discStack).ifPresent(disc -> {
            boolean probablySubLevel = SablePositionCompat.isProbablySubLevel(pos);

            if (probablySubLevel && !LazoDiscsConfig.ALLOW_PLAYBACK_ON_SABLE_PLATFORMS.get()) {
                LazoDiscs.LOGGER.info(
                        "Refusing to play LazoDisc '{}' for SophisticatedCore Jukebox Upgrade " +
                        "(block at {}) — block is on a Sable sub-level and " +
                        "allowPlaybackOnSablePlatforms is disabled in config.",
                        disc.title(), pos.toShortString()
                );
                return;
            }

            Vec3 projected = SablePositionCompat.projectJukeboxCenter(level, pos);
            boolean dynamicPosition = probablySubLevel
                    || projected.distanceToSqr(Vec3.atCenterOf(pos)) > 0.0001D;

            LazoDiscs.LOGGER.info(
                    "SophisticatedCore Jukebox Upgrade (block at {}) requested LazoDisc '{}'{}",
                    pos.toShortString(), disc.title(),
                    dynamicPosition ? " (Sable dynamic position — per-tick updates enabled)" : "");

            stopExisting(level, storageUuid, projected);

            try {
                PlayingVoiceSource source = PlasmoVoiceBridge.INSTANCE.startStaticSource(
                        level, pos, disc,
                        () -> onPlasmoFinished(level, projected, storageUuid, onFinished)
                );
                ActiveLazoSource active = new ActiveLazoSource(
                        level, /*entityId*/ -1, disc, onFinished,
                        projected, projected,
                        pos.immutable(), dynamicPosition
                );
                active.sourceRef.set(source);
                ACTIVE.put(storageUuid, active);

                long safeFinishTime = level.getGameTime() + (20L * 60 * 60 * 24);
                invokePutSoundInfo(level, storageUuid,
                        (Runnable) () -> onPlasmoFinished(level, projected, storageUuid, onFinished),
                        projected, safeFinishTime);
            } catch (Throwable t) {
                LazoDiscs.LOGGER.warn(
                        "Failed to start LazoDiscs source for SophisticatedCore at {}: {}",
                        pos.toShortString(), t.toString());
            }
        });
    }

    // -------------------------------------------------------------------------
    // playDisc — Vec3+entityId overload (entity-worn backpack)
    // -------------------------------------------------------------------------
    private static void playDiscEntity(ServerLevel level, Vec3 position, UUID storageUuid,
                                       ItemStack discStack, int entityId, Runnable onFinished) {
        DiscDataUtil.read(discStack).ifPresent(disc -> {
            LazoDiscs.LOGGER.info(
                    "SophisticatedCore Jukebox Upgrade (entityId={}, pos={}) requested LazoDisc '{}'",
                    entityId,
                    String.format(Locale.ROOT, "%.2f,%.2f,%.2f", position.x, position.y, position.z),
                    disc.title());

            stopExisting(level, storageUuid, position);

            ActiveLazoSource active = new ActiveLazoSource(
                    level, entityId, disc, onFinished,
                    position, position,
                    /*blockPos*/ null, /*dynamicPosition*/ false
            );
            ACTIVE.put(storageUuid, active);

            long safeFinishTime = level.getGameTime() + (20L * 60 * 60 * 24);
            invokePutSoundInfo(level, storageUuid,
                    (Runnable) () -> onPlasmoFinished(level, position, storageUuid, onFinished),
                    position, safeFinishTime);

            startPlasmoSourceForEntity(active, storageUuid);
        });
    }

    /**
     * Starts the Plasmo entity-bound source for the given active record.
     */
    private static void startPlasmoSourceForEntity(ActiveLazoSource active, UUID storageUuid) {
        if (active.sourceRef.get() != null) {
            return; // already started
        }
        try {
            PlayingVoiceSource source = PlasmoVoiceBridge.INSTANCE.startEntitySource(
                    active.level, active.entityId, active.lastPosition, active.disc,
                    () -> onPlasmoFinished(active.level, active.lastPosition, storageUuid, active.onFinished)
            );
            active.sourceRef.set(source);
            LazoDiscs.LOGGER.info(
                    "Started Plasmo entity source for storageUuid={} (disc='{}')",
                    storageUuid, active.disc.title());
        } catch (Throwable t) {
            LazoDiscs.LOGGER.warn(
                    "Failed to start Plasmo entity source for storageUuid={}: {}",
                    storageUuid, t.toString());
        }
    }

    // -------------------------------------------------------------------------
    // Reflection helpers for SophisticatedCore
    // -------------------------------------------------------------------------

    private static void invokePutSoundInfo(ServerLevel level, UUID storageUuid,
                                           Runnable onFinished, Vec3 pos, long finishTime) {
        if (!sophisticatedCoreAvailable) return;
        try {
            serverStorageSoundHandler_putSoundInfo.invoke(null, level, storageUuid, onFinished, pos, finishTime);
        } catch (Throwable t) {
            LazoDiscs.LOGGER.debug("putSoundInfo reflection call failed: {}", t.toString());
        }
    }

    private static void invokeStopPlayingDisc(Level level, Vec3 pos, UUID storageUuid) {
        if (!sophisticatedCoreAvailable) return;
        try {
            serverStorageSoundHandler_stopPlayingDisc.invoke(null, level, pos, storageUuid);
        } catch (Throwable t) {
            LazoDiscs.LOGGER.debug("stopPlayingDisc reflection call failed: {}", t.toString());
        }
    }

    @SuppressWarnings("unchecked")
    private static boolean invokeIsStorageStillRegistered(UUID storageUuid, Level level) {
        if (!sophisticatedCoreAvailable) return false;
        try {
            Object rootMap = worldStorageSoundInfosField.get(null);
            if (!(rootMap instanceof Map<?, ?> dimMap)) {
                return false;
            }
            Object dimEntry = dimMap.get(level.dimension());
            if (dimEntry == null) {
                return false;
            }
            if (!(dimEntry instanceof Map<?, ?> uuidMap)) return false;
            return uuidMap.containsKey(storageUuid);
        } catch (Throwable t) {
            LazoDiscs.LOGGER.debug("worldStorageSoundInfos field probe failed for storage {}: {}",
                    storageUuid, t.toString());
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    private static void stopExisting(ServerLevel level, UUID storageUuid, Vec3 fallbackPos) {
        ActiveLazoSource existing = ACTIVE.remove(storageUuid);
        if (existing != null) {
            PlayingVoiceSource source = existing.sourceRef.getAndSet(null);
            if (source != null) {
                try {
                    source.stop();
                } catch (Throwable t) {
                    LazoDiscs.LOGGER.debug("Failed to stop existing LazoDiscs source for storage {}: {}",
                            storageUuid, t.toString());
                }
            }
        }
        invokeStopPlayingDisc(level, existing != null ? existing.lastPosition : fallbackPos, storageUuid);
    }

    private static void onPlasmoFinished(ServerLevel level, Vec3 pos, UUID storageUuid, Runnable onFinished) {
        ActiveLazoSource active = ACTIVE.get(storageUuid);
        if (active != null) {
            active.sourceRef.set(null);
        }
        invokeStopPlayingDisc(level, pos, storageUuid);
        ACTIVE.remove(storageUuid);
        if (onFinished != null) {
            try {
                onFinished.run();
            } catch (Throwable t) {
                LazoDiscs.LOGGER.warn("SophisticatedCore onFinished callback threw: {}", t.toString());
            }
        }
    }

    /**
     * Server tick poller — runs every 5 ticks (~250ms). Called from
     * {@code JukeboxEvents.onServerTick} (platform-specific) which passes the
     * {@link MinecraftServer}. This is loader-agnostic — no event bus registration needed.
     *
     * <p><b>Stop detection</b> is its only job for entity-bound sources: {@code IDiscHandler}
     * has no {@code stop()} callback, so the only way to learn that SophisticatedCore removed
     * our SoundInfo is to poll its internal map via reflection.
     *
     * <p>For <b>block-placed backpacks on Sable moving platforms</b> ({@code dynamicPosition}),
     * this poller also re-projects the position every 5 ticks as a fallback; the primary,
     * more responsive update path is {@link #onSablePostPhysicsTick}.
     */
    public static void onServerTick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;
        if (!sophisticatedCoreAvailable) return;

        long tickCount = server.getTickCount();
        if (tickCount % 5 != 0) return;

        Iterator<Map.Entry<UUID, ActiveLazoSource>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, ActiveLazoSource> e = it.next();
            UUID storageUuid = e.getKey();
            ActiveLazoSource active = e.getValue();
            try {
                boolean stillRegistered = invokeIsStorageStillRegistered(storageUuid, active.level);
                if (!stillRegistered) {
                    LazoDiscs.LOGGER.info(
                            "SophisticatedCore removed SoundInfo for storage {} — stopping LazoDiscs source",
                            storageUuid);
                    it.remove();
                    PlayingVoiceSource source = active.sourceRef.getAndSet(null);
                    if (source != null) {
                        try {
                            source.stop();
                        } catch (Throwable t) {
                            LazoDiscs.LOGGER.debug("Failed to stop LazoDiscs source for storage {}: {}",
                                    storageUuid, t.toString());
                        }
                    }
                    continue;
                }

                if (active.blockPos != null) {
                    // Detect Sable platform assembly: block at active.blockPos is now AIR.
                    if (!active.dynamicPosition.get()
                            && !SablePositionCompat.isProbablySubLevel(active.blockPos)
                            && active.level.isLoaded(active.blockPos)
                            && active.level.getBlockState(active.blockPos).isAir()) {
                        LazoDiscs.LOGGER.info(
                                "SophisticatedCore backpack block at {} is now AIR in the main " +
                                "world (likely assembled into a Sable platform) — stopping " +
                                "LazoDiscs source for storageUuid={}",
                                active.blockPos, storageUuid);
                        it.remove();
                        PlayingVoiceSource source = active.sourceRef.getAndSet(null);
                        if (source != null) {
                            try {
                                source.stop();
                            } catch (Throwable t) {
                                LazoDiscs.LOGGER.debug(
                                        "Failed to stop LazoDiscs source for storage {}: {}",
                                        storageUuid, t.toString());
                            }
                        }
                        invokeStopPlayingDisc(active.level, active.lastPosition, storageUuid);
                        continue;
                    }

                    if (!active.dynamicPosition.get()
                            && SablePositionCompat.isProbablySubLevel(active.blockPos)) {
                        active.dynamicPosition.set(true);
                        LazoDiscs.LOGGER.info(
                                "Backpack Jukebox Upgrade (storageUuid={}) is now on a Sable " +
                                "sub-level — switching to dynamic position tracking",
                                storageUuid);
                    }
                    if (active.dynamicPosition.get()) {
                        PlayingVoiceSource source = active.sourceRef.get();
                        if (source != null) {
                            try {
                                Vec3 projected = SablePositionCompat.projectJukeboxCenter(
                                        active.level, active.blockPos
                                );
                                source.updatePosition(active.level, projected);
                            } catch (Throwable t) {
                                LazoDiscs.LOGGER.debug(
                                        "Failed to update Sable dynamic position for storage {}: {}",
                                        storageUuid, t.toString());
                            }
                        }
                    }
                }
            } catch (Throwable t) {
                LazoDiscs.LOGGER.debug("LazoDiscsDiscHandler tick poller error for storage {}: {}",
                        storageUuid, t.toString());
            }
        }
    }

    public static void stopAll(String reason) {
        if (ACTIVE.isEmpty()) return;
        LazoDiscs.LOGGER.info("Stopping {} LazoDiscs sources tracked by SophisticatedCore ({})",
                ACTIVE.size(), reason);
        for (ActiveLazoSource a : ACTIVE.values()) {
            PlayingVoiceSource source = a.sourceRef.getAndSet(null);
            if (source != null) {
                try {
                    source.stop();
                } catch (Throwable t) {
                    LazoDiscs.LOGGER.debug("Failed to stop LazoDiscs source during stopAll: {}", t.toString());
                }
            }
        }
        ACTIVE.clear();
    }

    /**
     * Called from {@link com.eyecrasher.lazodiscs.event.SablePhysicsEvents} after every Sable
     * physics step (typically at 60 Hz) to update Plasmo source positions for block-placed
     * backpacks on Sable moving platforms.
     */
    public static void onSablePostPhysicsTick(ServerLevel level) {
        if (ACTIVE.isEmpty()) return;

        for (Map.Entry<UUID, ActiveLazoSource> e : ACTIVE.entrySet()) {
            ActiveLazoSource active = e.getValue();
            if (!active.level.dimension().equals(level.dimension())) continue;
            if (active.blockPos == null) continue;
            if (!active.dynamicPosition.get()) {
                if (!SablePositionCompat.isProbablySubLevel(active.blockPos)) continue;
                active.dynamicPosition.set(true);
            }

            PlayingVoiceSource source = active.sourceRef.get();
            if (source == null) continue;

            try {
                Vec3 projected = SablePositionCompat.projectJukeboxCenter(level, active.blockPos);
                source.updatePosition(level, projected);
            } catch (Throwable t) {
                LazoDiscs.LOGGER.debug(
                        "SablePostPhysicsTick position update failed for storage {}: {}",
                        e.getKey(), t.toString());
            }
        }
    }

    /**
     * Tracks state for a SophisticatedCore Jukebox Upgrade playback.
     */
    private static final class ActiveLazoSource {
        final ServerLevel level;
        final int entityId;
        final CustomDiscData disc;
        final Runnable onFinished;
        final Vec3 lastPosition;
        final Vec3 initialPosition;
        final BlockPos blockPos;
        final java.util.concurrent.atomic.AtomicBoolean dynamicPosition;
        final java.util.concurrent.atomic.AtomicReference<PlayingVoiceSource> sourceRef =
                new java.util.concurrent.atomic.AtomicReference<>();

        ActiveLazoSource(ServerLevel level, int entityId, CustomDiscData disc, Runnable onFinished,
                         Vec3 lastPosition, Vec3 initialPosition,
                         BlockPos blockPos, boolean dynamicPosition) {
            this.level = level;
            this.entityId = entityId;
            this.disc = disc;
            this.onFinished = onFinished;
            this.lastPosition = lastPosition;
            this.initialPosition = initialPosition;
            this.blockPos = blockPos;
            this.dynamicPosition = new java.util.concurrent.atomic.AtomicBoolean(dynamicPosition);
        }
    }
}
