package com.eyecrasher.lazodiscs.voice;

import com.eyecrasher.lazodiscs.LazoDiscs;
import com.eyecrasher.lazodiscs.compat.SablePositionCompat;
import com.eyecrasher.lazodiscs.config.LazoDiscsConfig;
import com.eyecrasher.lazodiscs.data.CustomDiscData;
import com.eyecrasher.lazodiscs.text.LazoDiscsText;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackState;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import su.plo.slib.api.server.position.ServerPos3d;
import su.plo.slib.api.server.world.McServerWorld;
import su.plo.voice.api.server.PlasmoVoiceServer;
import su.plo.voice.api.server.audio.line.ServerSourceLine;
import su.plo.voice.api.server.audio.provider.AudioFrameProvider;
import su.plo.voice.api.server.audio.provider.AudioFrameResult;
import su.plo.voice.api.server.audio.source.AudioSender;
import su.plo.voice.api.server.audio.source.ServerStaticSource;

import java.io.InputStream;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Future;
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

        String sourceLineName = LazoDiscsText.sourceLineName();

        try (InputStream icon = getIconResource()) {
            if (icon != null) {
                var builder = voiceServer.getSourceLineManager().createBuilder(
                        addon,
                        "discs",
                        sourceLineName,
                        icon,
                        50
                );

                builder.setDefaultVolume(
                        LazoDiscsConfig.SOURCE_LINE_DEFAULT_VOLUME.get().floatValue()
                );

                this.discsLine = builder.build();
            } else {
                var builder = voiceServer.getSourceLineManager().createBuilder(
                        addon,
                        "discs",
                        sourceLineName,
                        "lazodiscs:textures/icons/discs.png",
                        50
                );

                builder.setDefaultVolume(
                        LazoDiscsConfig.SOURCE_LINE_DEFAULT_VOLUME.get().floatValue()
                );

                this.discsLine = builder.build();
            }
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Could not register LazoDiscs Plasmo Voice source line",
                    e
            );
        }

        LazoDiscs.LOGGER.info(
                "Registered Plasmo Voice source line 'discs' for LazoDiscs volume control"
        );
    }

    private InputStream getIconResource() {
        InputStream icon = getClass()
                .getClassLoader()
                .getResourceAsStream("assets/lazodiscs/icon.png");

        if (icon != null) {
            return icon;
        }

        return getClass()
                .getClassLoader()
                .getResourceAsStream("lazodiscs_icon.png");
    }

    public void shutdown() {
        this.discsLine = null;
        this.voiceServer = null;
    }

    public PlayingVoiceSource startStaticSource(
            ServerLevel level,
            BlockPos pos,
            CustomDiscData disc,
            Runnable onFinished
    ) {
        return startStreamingSource(level, pos, disc, onFinished);
    }

    private PlayingVoiceSource startStreamingSource(
            ServerLevel level,
            BlockPos pos,
            CustomDiscData disc,
            Runnable onFinished
    ) {
        PlasmoVoiceServer server = voiceServer;
        ServerSourceLine line = discsLine;

        if (server == null || line == null) {
            throw new IllegalStateException(
                    "Plasmo Voice is not initialized yet"
            );
        }

        // Resolve the Plasmo Voice world that corresponds to this ServerLevel.
        // findWorld() matches McServerWorld.getName() against the exact
        // ResourceLocation string of the level's dimension key, so it works for
        // minecraft:overworld, minecraft:the_nether, minecraft:the_end and any
        // custom dimension (e.g. superflatdimension:overworld) without a fallback.
        Optional<McServerWorld> worldResult = findWorld(server, level);

        if (worldResult.isEmpty()) {
            String dimId = dimensionId(level);
            LazoDiscs.LOGGER.warn(
                    "Could not resolve Plasmo Voice world for Minecraft dimension '{}'." +
                    " Available Plasmo worlds: {}",
                    dimId,
                    server.getMinecraftServer()
                            .getWorlds()
                            .stream()
                            .map(McServerWorld::getName)
                            .toList()
            );

            throw new IllegalStateException(
                    "Could not resolve Plasmo Voice world for Minecraft dimension "
                            + dimId
            );
        }

        McServerWorld pvWorld = worldResult.get();

        Vec3 projected = SablePositionCompat.projectJukeboxCenter(level, pos);

        boolean projectedOut =
                projected.distanceToSqr(Vec3.atCenterOf(pos)) > 0.0001D;

        LazoDiscs.LOGGER.info(
                "Preparing streaming LazoDisc Plasmo source:" +
                " mcDimension={}, pvWorld={}, blockPos={}, projectedPos={}{}",
                dimensionId(level),
                pvWorld.getName(),
                pos.toShortString(),
                String.format(
                        Locale.ROOT,
                        "%.2f, %.2f, %.2f",
                        projected.x,
                        projected.y,
                        projected.z
                ),
                projectedOut
                        ? " (Sable/sub-level projected)"
                        : ""
        );

        ServerPos3d pvPos = new ServerPos3d(
                pvWorld,
                projected.x,
                projected.y,
                projected.z
        );

        AtomicBoolean stopped = new AtomicBoolean(false);
        AtomicBoolean manualStop = new AtomicBoolean(false);
        AtomicBoolean finishedNotified = new AtomicBoolean(false);

        AtomicReference<LavaPcmFeeder.StreamingPlayback> playbackRef =
                new AtomicReference<>();

        AtomicReference<ServerStaticSource> sourceRef =
                new AtomicReference<>();

        AtomicReference<AudioSender> senderRef =
                new AtomicReference<>();

        AtomicReference<Future<?>> taskRef =
                new AtomicReference<>();

        Runnable cleanup = () -> {
            LavaPcmFeeder.StreamingPlayback playback =
                    playbackRef.getAndSet(null);

            if (playback != null) {
                try {
                    playback.close();
                } catch (Exception ignored) {
                }
            }

            ServerStaticSource source =
                    sourceRef.getAndSet(null);

            if (source != null) {
                try {
                    source.remove();
                } catch (Exception ignored) {
                }
            }
        };

        Runnable notifyFinished = () -> {
            if (onFinished == null) {
                return;
            }

            if (!finishedNotified.compareAndSet(false, true)) {
                return;
            }

            level.getServer().execute(onFinished);
        };

        Future<?> task = AudioLoadExecutor.submit(() -> {
            try {
                LavaPcmFeeder.StreamingPlayback playback =
                        LavaPcmFeeder.openStream(
                                disc.url(),
                                disc.title(),
                                disc.volume()
                        );

                if (stopped.get()) {
                    playback.close();
                    return;
                }

                playbackRef.set(playback);

                ServerStaticSource source =
                        line.createStaticSource(
                                pvPos,
                                false
                        );

                source.setName(disc.title());

                sourceRef.set(source);

                AudioFrameProvider provider =
                        new StreamingAudioFrameProvider(
                                server,
                                playback,
                                stopped
                        );

                AudioSender sender =
                        source.createAudioSender(
                                provider,
                                (short) Math.max(
                                        1,
                                        Math.min(
                                                Short.MAX_VALUE,
                                                disc.range()
                                        )
                                )
                        );

                senderRef.set(sender);

                sender.onStop(() -> {
                    boolean wasManual = manualStop.get();

                    stopped.set(true);

                    cleanup.run();

                    if (!wasManual) {
                        notifyFinished.run();
                    }
                });

                if (stopped.get()) {
                    cleanup.run();
                    return;
                }

                sender.start();

                LazoDiscs.LOGGER.info(
                        "Streaming LazoDisc audio sender started for '{}' at {}",
                        disc.title(),
                        pos.toShortString()
                );

            } catch (Throwable t) {

                if (!stopped.get()) {
                    LazoDiscs.LOGGER.warn(
                            "Failed to start streaming LazoDisc audio at {}: {}",
                            pos.toShortString(),
                            t.toString()
                    );

                    notifyLoadFailure(
                            level,
                            pos,
                            disc,
                            messageOf(t)
                    );
                }

                stopped.set(true);

                cleanup.run();

                if (!manualStop.get()) {
                    notifyFinished.run();
                }
            }
        });

        taskRef.set(task);

        return new PlayingVoiceSource() {

            @Override
            public void stop() {
                if (!stopped.compareAndSet(false, true)) {
                    return;
                }

                manualStop.set(true);

                Future<?> t = taskRef.getAndSet(null);

                if (t != null) {
                    t.cancel(true);
                }

                AudioSender sender = senderRef.getAndSet(null);

                if (sender != null) {
                    try {
                        sender.stop();
                    } catch (Exception ignored) {
                    }
                }

                cleanup.run();
            }

            @Override
            public void updatePosition(
                    ServerLevel updateLevel,
                    Vec3 projectedPosition
            ) {
                if (stopped.get()) {
                    return;
                }

                ServerStaticSource source = sourceRef.get();

                if (source == null) {
                    return;
                }

                try {
                    // IMPORTANT: always resolve the world fresh from the provided
                    // updateLevel — never fall back to pvWorld (the original level's
                    // world).  If the jukebox somehow teleported to another dimension
                    // we must track that; and if it hasn't, we still want the exact
                    // McServerWorld that matches updateLevel's dimension key, not the
                    // one for the overworld that pvWorld might represent.
                    McServerWorld updateWorld =
                            findWorld(server, updateLevel)
                                    .orElseThrow(() -> new IllegalStateException(
                                            "Could not resolve Plasmo Voice world for" +
                                            " updated dimension: " + dimensionId(updateLevel)
                                    ));

                    source.setPosition(
                            new ServerPos3d(
                                    updateWorld,
                                    projectedPosition.x,
                                    projectedPosition.y,
                                    projectedPosition.z
                            )
                    );

                } catch (Exception e) {
                    LazoDiscs.LOGGER.debug(
                            "Failed to update streaming LazoDisc source position at {}: {}",
                            pos.toShortString(),
                            e.toString()
                    );
                }
            }
        };
    }

    private void notifyLoadFailure(
            ServerLevel level,
            BlockPos pos,
            CustomDiscData disc,
            String reason
    ) {
        double range = Math.max(
                32.0D,
                Math.min(
                        256.0D,
                        disc.range()
                )
        );

        double rangeSqr = range * range;

        Vec3 center = Vec3.atCenterOf(pos);

        for (ServerPlayer player : level.players()) {
            if (player.position().distanceToSqr(center) <= rangeSqr) {
                player.sendSystemMessage(
                        LazoDiscsText
                                .audioLoadFailed(
                                        disc.title(),
                                        reason
                                )
                                .withStyle(ChatFormatting.RED)
                );
            }
        }
    }

    private static String messageOf(Throwable t) {
        if (t == null) {
            return LazoDiscsText.unknown();
        }

        String message = t.getMessage();
        Throwable cause = t.getCause();

        if ((message == null || message.isBlank()) && cause != null) {
            return messageOf(cause);
        }

        if (cause != null
                && message != null
                && message.equals(cause.toString())) {
            return messageOf(cause);
        }

        return message == null || message.isBlank()
                ? t.getClass().getSimpleName()
                : message;
    }

    // -------------------------------------------------------------------------
    // World resolution
    // -------------------------------------------------------------------------

    /**
     * Returns the {@link McServerWorld} that Plasmo Voice has registered for the
     * given {@link ServerLevel}.
     *
     * <h3>How mc-slib sets McServerWorld.getName()</h3>
     * <p>In {@code ModServerWorld.kt} (mc-slib, the library backing Plasmo Voice on
     * Fabric/NeoForge) the name is initialised as:
     * <pre>
     *   override val name: String = level.dimension().location().toString()
     *   // or .value().toString() in MC 1.21.11+ where location() was renamed to value()
     * </pre>
     * Either way, {@code getName()} returns the full {@code "namespace:path"} string
     * of the dimension's ResourceLocation — e.g. {@code "minecraft:overworld"},
     * {@code "minecraft:the_nether"}, {@code "superflatdimension:overworld"}, etc.
     *
     * <h3>Why the old code was broken</h3>
     * <p>The old {@code findWorld()} extracted only the <em>path</em> part
     * ({@code "overworld"}) from the Minecraft dimension key and tried to match it
     * against {@code pvWorld.getName()}.  For the vanilla overworld both sides
     * happen to produce {@code "overworld"}, so the match succeeded.  But for
     * {@code superflatdimension:overworld} the path is still {@code "overworld"},
     * which matched {@code minecraft:overworld} — the wrong world.  The source was
     * then created in the vanilla overworld even though the jukebox was placed in a
     * custom dimension, making the audio inaudible there.
     *
     * <h3>The fix — version-agnostic approach</h3>
     * <p>We call {@code level.dimension().toString()}, which returns the stable
     * {@code ResourceKey} string present in every Minecraft version:
     * <pre>
     *   "ResourceKey[minecraft:dimension / minecraft:overworld]"
     *   "ResourceKey[minecraft:dimension / superflatdimension:overworld]"
     * </pre>
     * We then parse out the {@code "namespace:path"} portion after the {@code " / "}
     * separator.  This avoids importing {@code ResourceLocation} or calling
     * {@code .location()} / {@code .value()} directly — both of which changed
     * between MC 1.21.10 and 1.21.11 (Mojang renamed the method and moved the class).
     * The resulting string exactly matches what mc-slib stores as {@code getName()}.
     */
    private Optional<McServerWorld> findWorld(
            PlasmoVoiceServer server,
            ServerLevel level
    ) {
        String exactKey = dimensionId(level); // "namespace:path", e.g. "minecraft:overworld"

        return server.getMinecraftServer()
                .getWorlds()
                .stream()
                .filter(world -> world.getName().equalsIgnoreCase(exactKey))
                .findFirst();
    }

    // -------------------------------------------------------------------------
    // Helpers used only for log messages and world lookup
    // -------------------------------------------------------------------------

    /**
     * Returns the canonical {@code "namespace:path"} dimension identifier for the
     * given level, e.g. {@code "minecraft:overworld"} or
     * {@code "superflatdimension:overworld"}.
     *
     * <p>Uses only {@code level.dimension().toString()} — a method available on
     * {@code java.lang.Object} — so it compiles against every Minecraft version
     * without any version-specific imports or method calls.
     *
     * <p>{@code ResourceKey.toString()} always returns:
     * <pre>
     *   "ResourceKey[minecraft:dimension / minecraft:overworld]"
     * </pre>
     * We extract the substring after the last {@code " / "} and before {@code "]"}.
     */
    private static String dimensionId(ServerLevel level) {
        return parseDimensionKey(level.dimension().toString());
    }

    /**
     * Parses the {@code "namespace:path"} out of a {@code ResourceKey.toString()}
     * value such as {@code "ResourceKey[minecraft:dimension / minecraft:overworld]"}.
     * Falls back to the raw string if the expected format is not found.
     */
    private static String parseDimensionKey(String resourceKeyString) {
        if (resourceKeyString == null || resourceKeyString.isBlank()) {
            return "unknown";
        }

        String result = resourceKeyString.trim();

        // Format: "ResourceKey[minecraft:dimension / minecraft:overworld]"
        if (result.startsWith("ResourceKey[")
                && result.contains(" / ")
                && result.endsWith("]")) {

            int separatorIdx = result.lastIndexOf(" / ");
            if (separatorIdx >= 0) {
                return result.substring(
                        separatorIdx + 3,
                        result.length() - 1
                ).toLowerCase(Locale.ROOT);
            }
        }

        // Fallback: already a plain "namespace:path" string
        return result.toLowerCase(Locale.ROOT);
    }

    // -------------------------------------------------------------------------
    // Audio frame provider
    // -------------------------------------------------------------------------

    private static final class StreamingAudioFrameProvider
            implements AudioFrameProvider {

        private final PlasmoVoiceServer server;
        private final LavaPcmFeeder.StreamingPlayback playback;
        private final AtomicBoolean stopped;

        private StreamingAudioFrameProvider(
                PlasmoVoiceServer server,
                LavaPcmFeeder.StreamingPlayback playback,
                AtomicBoolean stopped
        ) {
            this.server = server;
            this.playback = playback;
            this.stopped = stopped;
        }

        @Override
        public AudioFrameResult provide20ms() {
            if (stopped.get()) {
                return AudioFrameResult.Finished.INSTANCE;
            }

            try {
                AudioTrackState state =
                        playback.track().getState();

                if (state == AudioTrackState.FINISHED
                        || (
                        state == AudioTrackState.INACTIVE
                                && playback.track().getPosition() > 0L
                )) {
                    return AudioFrameResult.Finished.INSTANCE;
                }

                AudioFrame frame =
                        playback.player().provide();

                byte[] encrypted =
                        frame == null
                                ? null
                                : server
                                .getDefaultEncryption()
                                .encrypt(frame.getData());

                return new AudioFrameResult.Provided(encrypted);

            } catch (Throwable t) {
                stopped.set(true);

                LazoDiscs.LOGGER.warn(
                        "Failed to provide LazoDisc streaming frame: {}",
                        t.toString()
                );

                return AudioFrameResult.Finished.INSTANCE;
            }
        }
    }
}
