package com.eyecrasher.lazodiscs.voice;

import com.eyecrasher.lazodiscs.LazoDiscs;
import com.eyecrasher.lazodiscs.data.CustomDiscData;
import com.eyecrasher.lazodiscs.config.LazoDiscsConfig;
import com.eyecrasher.lazodiscs.compat.SablePositionCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import su.plo.voice.api.server.PlasmoVoiceServer;
import su.plo.voice.api.server.audio.line.ServerSourceLine;
import su.plo.voice.api.server.audio.provider.ArrayAudioFrameProvider;
import su.plo.voice.api.server.audio.source.AudioSender;
import su.plo.voice.api.server.audio.source.ServerStaticSource;
import su.plo.slib.api.server.position.ServerPos3d;
import su.plo.slib.api.server.world.McServerWorld;

import java.io.InputStream;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class PlasmoVoiceBridge {
    public static final PlasmoVoiceBridge INSTANCE = new PlasmoVoiceBridge();

    private volatile PlasmoVoiceServer voiceServer;
    private volatile ServerSourceLine discsLine;

    private PlasmoVoiceBridge() {
    }

    public void initialize(PlasmoVoiceServer voiceServer, Object addon) {
        this.voiceServer = voiceServer;

        String sourceLineName = LazoDiscsConfig.SOURCE_LINE_NAME.get();
        if (sourceLineName == null || sourceLineName.isBlank()) {
            sourceLineName = "Discs";
        }

        try (InputStream icon = getIconResource()) {
            if (icon != null) {
                var builder = voiceServer.getSourceLineManager().createBuilder(
                        addon,
                        "discs",
                        sourceLineName,
                        icon,
                        50
                );
                builder.setDefaultVolume(LazoDiscsConfig.SOURCE_LINE_DEFAULT_VOLUME.get().floatValue());
                this.discsLine = builder.build();
            } else {
                var builder = voiceServer.getSourceLineManager().createBuilder(
                        addon,
                        "discs",
                        sourceLineName,
                        "lazodiscs:textures/icons/discs.png",
                        50
                );
                builder.setDefaultVolume(LazoDiscsConfig.SOURCE_LINE_DEFAULT_VOLUME.get().floatValue());
                this.discsLine = builder.build();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Could not register LazoDiscs Plasmo Voice source line", e);
        }

        LazoDiscs.LOGGER.info("Registered Plasmo Voice source line 'discs' for LazoDiscs volume control");
    }

    private InputStream getIconResource() {
        InputStream icon = getClass().getClassLoader().getResourceAsStream("assets/lazodiscs/icon.png");
        if (icon != null) return icon;
        return getClass().getClassLoader().getResourceAsStream("lazodiscs_icon.png");
    }

    public void shutdown() {
        this.discsLine = null;
        this.voiceServer = null;
    }

    public PlayingVoiceSource startStaticSource(ServerLevel level, BlockPos pos, CustomDiscData disc) {
        PlasmoVoiceServer server = voiceServer;
        ServerSourceLine line = discsLine;
        if (server == null || line == null) {
            throw new IllegalStateException("Plasmo Voice is not initialized yet");
        }

        McServerWorld pvWorld = findWorld(server, level).orElseThrow(() -> new IllegalStateException("Could not resolve Plasmo world for " + level.dimension().location()));
        Vec3 projected = SablePositionCompat.projectJukeboxCenter(level, pos);
        boolean projectedOut = projected.distanceToSqr(Vec3.atCenterOf(pos)) > 0.0001D;
        LazoDiscs.LOGGER.info("Preparing LazoDisc Plasmo source: mcDimension={}, mcLevelClass={}, pvWorld={}, blockPos={}, projectedPos={}{}",
                level.dimension().location(), level.getClass().getName(), pvWorld.getName(), pos.toShortString(),
                String.format(Locale.ROOT, "%.2f, %.2f, %.2f", projected.x, projected.y, projected.z),
                projectedOut ? " (Sable/sub-level projected)" : "");
        ServerPos3d pvPos = new ServerPos3d(pvWorld, projected.x, projected.y, projected.z);
        ServerStaticSource source = line.createStaticSource(pvPos, false);
        ArrayAudioFrameProvider provider = new ArrayAudioFrameProvider(server, false);
        AudioSender sender = source.createAudioSender(provider, (short) Math.max(1, Math.min(Short.MAX_VALUE, disc.range())));

        AtomicBoolean stopped = new AtomicBoolean(false);
        AtomicBoolean senderStarted = new AtomicBoolean(false);

        Runnable cleanup = () -> {
            try {
                provider.close();
            } catch (Exception ignored) {
            }
            try {
                source.remove();
            } catch (Exception ignored) {
            }
        };

        AtomicReference<AutoCloseable> loaderRef = new AtomicReference<>();
        Runnable onSamplesReady = () -> { };

        java.util.function.Consumer<short[]> onReady = samples -> {
            if (stopped.get()) return;
            try {
                provider.addSamples(samples);
                AudioCache.put(disc, samples);
                if (stopped.get()) return;
                senderStarted.set(true);
                sender.start();
                LazoDiscs.LOGGER.info("LazoDisc audio sender started for '{}' at {}", disc.title(), pos.toShortString());
            } catch (Exception e) {
                LazoDiscs.LOGGER.warn("Failed to feed/start LazoDisc audio at {}: {}", pos.toShortString(), e.toString());
                if (stopped.compareAndSet(false, true)) cleanup.run();
            }
        };
        Runnable onFailure = () -> {
            if (stopped.compareAndSet(false, true)) cleanup.run();
        };

        Runnable startLoader;
        if (LavaPcmFeeder.shouldUse(disc.url())) {
            LavaPcmFeeder loader = new LavaPcmFeeder(disc.url(), disc.title(), disc.volume(), onReady, onFailure);
            loaderRef.set(loader);
            startLoader = loader::start;
        } else {
            HttpPcmFeeder loader = new HttpPcmFeeder(disc.url(), disc.volume(), onReady, onFailure);
            loaderRef.set(loader);
            startLoader = loader::start;
        }

        sender.onStop(() -> {
            if (!stopped.compareAndSet(false, true)) return;
            closeLoader(loaderRef.get());
            cleanup.run();
        });

        Optional<short[]> cachedSamples = AudioCache.get(disc);
        if (cachedSamples.isPresent()) {
            LazoDiscs.LOGGER.info("Starting LazoDisc '{}' from audio cache at {}", disc.title(), pos.toShortString());
            onReady.accept(cachedSamples.get());
        } else {
            startLoader.run();
        }

        return new PlayingVoiceSource() {
            @Override
            public void stop() {
                if (!stopped.compareAndSet(false, true)) return;
                closeLoader(loaderRef.get());
                if (senderStarted.get()) {
                    try {
                        sender.stop();
                    } catch (Exception ignored) {
                    }
                }
                cleanup.run();
            }

            @Override
            public void updatePosition(ServerLevel updateLevel, Vec3 projectedPosition) {
                if (stopped.get()) return;
                try {
                    McServerWorld updateWorld = findWorld(server, updateLevel).orElse(pvWorld);
                    source.setPosition(new ServerPos3d(updateWorld, projectedPosition.x, projectedPosition.y, projectedPosition.z));
                } catch (Exception e) {
                    LazoDiscs.LOGGER.debug("Failed to update LazoDisc source position at {}: {}", pos.toShortString(), e.toString());
                }
            }
        };
    }

    private void closeLoader(AutoCloseable loader) {
        if (loader == null) return;
        try {
            loader.close();
        } catch (Exception ignored) {
        }
    }

    private Optional<McServerWorld> findWorld(PlasmoVoiceServer server, ServerLevel level) {
        String full = level.dimension().location().toString().toLowerCase(Locale.ROOT);
        String path = level.dimension().location().getPath().toLowerCase(Locale.ROOT);
        return server.getMinecraftServer().getWorlds().stream()
                .filter(world -> {
                    String name = world.getName().toLowerCase(Locale.ROOT);
                    return name.equals(full) || name.equals(path) || name.endsWith("/" + path) || name.endsWith(":" + path);
                })
                .findFirst()
                .or(() -> server.getMinecraftServer().getWorlds().stream().findFirst());
    }
}
