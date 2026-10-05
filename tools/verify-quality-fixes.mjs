#!/usr/bin/env node
// Dependency-free checks of the real methods, compiled with small Minecraft/API doubles.
// Run: JAVA_HOME=/path/to/jdk21 node tools/verify-quality-fixes.mjs
// These cover permissions, config, bounded loading, command completion, and playback lifecycle.
// They do not replace Gradle compilation or an in-game audio/integration test.
import assert from 'node:assert/strict';
import { mkdtempSync, readFileSync, readdirSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const projects = ['fabric', 'neoforge'].flatMap(loader =>
  readdirSync(join(root, loader)).filter(version => /^1\.21\.\d+$/.test(version))
    .map(version => join(root, loader, version, 'src/main/java/com/eyecrasher/lazodiscs')));
assert.equal(projects.length, 22);

function javaFiles(directory, relative = '') {
  return readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    const path = join(relative, entry.name);
    if (entry.isDirectory()) return javaFiles(join(directory, entry.name), path);
    return entry.name.endsWith('.java') ? [path] : [];
  }).sort();
}
const classInventory = javaFiles(projects[0]);
assert.equal(classInventory.length, 30);
for (const project of projects) assert.deepEqual(javaFiles(project), classInventory, `${project}: incomplete Java class inventory`);
const compact = source => source.replace(/\s+/g, '');

function method(file, signature) {
  const source = readFileSync(file, 'utf8');
  const start = source.indexOf(signature);
  assert.ok(start >= 0, `${file}: missing ${signature}`);
  const indentation = source.slice(source.lastIndexOf('\n', start) + 1, start);
  const closing = `\n${indentation}}`;
  const end = source.indexOf(closing, start);
  assert.ok(end >= 0, `${file}: missing method end`);
  return source.slice(start, end + closing.length);
}

function sharedMethod(relative, signature) {
  const reference = method(join(projects[0], relative), signature);
  for (const project of projects) {
    assert.equal(method(join(project, relative), signature), reference, `${project}: divergent ${signature}`);
  }
  return reference;
}

const finishBurn = sharedMethod('command/LazoDiscsCommands.java', 'private static void finishBurn(');
const validateUrl = sharedMethod('data/DiscDataUtil.java', 'public static String validateUrl(');
const clampRange = sharedMethod('data/DiscDataUtil.java', 'public static int clampRange(');
const openStream = sharedMethod('voice/LavaPcmFeeder.java', 'public static StreamingPlayback openStream(');
const resolveTrack = sharedMethod('voice/LavaPcmFeeder.java', 'public static ResolvedTrack resolveTrack(');
const finishAt = sharedMethod('server/JukeboxPlaybackManager.java', 'private void finishAt(');
const onPlasmoFinished = sharedMethod('compat/sophisticatedbackpacks/LazoDiscsDiscHandler.java', 'private static void onPlasmoFinished(');
const notifyLoadFailure = sharedMethod('voice/PlasmoVoiceBridge.java', 'private void notifyLoadFailure(');
const provide20ms = sharedMethod('voice/PlasmoVoiceBridge.java', 'public AudioFrameResult provide20ms(');
const isProbablySubLevel = sharedMethod('compat/SablePositionCompat.java', 'public static boolean isProbablySubLevel(');
const resolveFromSpotifyPage = sharedMethod('voice/SpotifyTitleResolver.java', 'private static Optional<SpotifyMetadata> resolveFromSpotifyPage(');
const resolveFromOEmbed = sharedMethod('voice/SpotifyTitleResolver.java', 'private static Optional<SpotifyMetadata> resolveFromOEmbed(');
const configuredLanguage = sharedMethod('text/LazoDiscsText.java', 'private static String configuredLanguage(');
const onAddonShutdown = sharedMethod('voice/LazoDiscsVoiceAddon.java', 'public void onAddonShutdown(');
const executorSource = readFileSync(join(projects[0], 'voice/AudioLoadExecutor.java'), 'utf8');
for (const project of projects) {
  assert.equal(readFileSync(join(project, 'voice/AudioLoadExecutor.java'), 'utf8'), executorSource);
  const bridgeSource = readFileSync(join(project, 'voice/PlasmoVoiceBridge.java'), 'utf8');
  assert.equal(bridgeSource.match(/LazoDiscsText\.audioLoadBusy\(\)/g)?.length, 3);
  const commands = readFileSync(join(project, 'command/LazoDiscsCommands.java'), 'utf8');
  assert.equal(commands.match(/LazoDiscsText\.audioLoadBusy\(\)/g)?.length, 2);
  assert.equal(commands.match(/catch \(InterruptedException interrupted\)/g)?.length, 2);
}
const executorClass = executorSource.slice(executorSource.indexOf('public final class AudioLoadExecutor'))
  .replaceAll('AudioLoadExecutor', 'RealAudioLoadExecutor').replace('public final class', 'static final class');
const permissionClasses = [false, true].map(modern => {
  const group = projects.filter(project => project.includes('/1.21.11/') === modern);
  const reference = readFileSync(join(group[0], 'server/LazoDiscsPermissions.java'), 'utf8');
  for (const project of group) assert.equal(readFileSync(join(project, 'server/LazoDiscsPermissions.java'), 'utf8'), reference);
  return reference.slice(reference.indexOf('public final class LazoDiscsPermissions'))
    .replaceAll('LazoDiscsPermissions', modern ? 'ModernPermissions' : 'LegacyPermissions')
    .replace('public final class', 'static final class');
}).join('\n');
const fabricProjects = projects.filter(project => project.includes('/fabric/'));
const fabricConfig = readFileSync(join(fabricProjects[0], 'config/LazoDiscsConfig.java'), 'utf8');
const fabricConfigClasses = ['public abstract static class Value', 'public static final class DoubleValue'].map(signature => {
  const reference = blockAt(fabricConfig, fabricConfig.indexOf(signature));
  for (const project of fabricProjects) {
    const source = readFileSync(join(project, 'config/LazoDiscsConfig.java'), 'utf8');
    assert.equal(blockAt(source, source.indexOf(signature)), reference);
  }
  return reference;
}).join('\n');
const stripConfigComment = method(join(fabricProjects[0], 'config/LazoDiscsConfig.java'), 'private static String stripComment(');
const neoProjects = projects.filter(project => project.includes('/neoforge/'));
const tooltipMethod = method(join(neoProjects[0], 'event/LazoDiscsClientEvents.java'), 'public static void onItemTooltip(');
for (const project of neoProjects) {
  const source = readFileSync(join(project, 'event/LazoDiscsClientEvents.java'), 'utf8');
  assert.match(source, /@EventBusSubscriber\(modid\s*=\s*LazoDiscs.MOD_ID,\s*value\s*=\s*Dist.CLIENT\)/);
  assert.equal(method(join(project, 'event/LazoDiscsClientEvents.java'), 'public static void onItemTooltip('), tooltipMethod);
}
const resyncLoadedJukeboxes = method(join(fabricProjects[0], 'event/JukeboxEvents.java'), 'private static void resyncLoadedJukeboxes(');
for (const project of fabricProjects) {
  assert.equal(method(join(project, 'event/JukeboxEvents.java'), 'private static void resyncLoadedJukeboxes('), resyncLoadedJukeboxes);
  const events = readFileSync(join(project, 'event/JukeboxEvents.java'), 'utf8');
  assert.ok(events.includes('ServerChunkEvents.CHUNK_LOAD.register'));
  assert.ok(events.includes('chunk.getBlockEntities().values()'));
  assert.ok(events.includes('pendingResyncs.clear();'));
  assert.ok(compact(events).includes('pendingResyncs.entrySet().removeIf'));
}
const bridge = readFileSync(join(projects[0], 'voice/PlasmoVoiceBridge.java'), 'utf8');
for (const project of projects) {
  assert.equal(readFileSync(join(project, 'voice/PlasmoVoiceBridge.java'), 'utf8'), bridge);
}
function blockAt(source, start) {
  const opening = source.indexOf('{', start);
  let depth = 0;
  for (let index = opening; index < source.length; index++) {
    if (source[index] === '{') depth++;
    if (source[index] === '}' && --depth === 0) return source.slice(start, index + 1);
  }
  throw new Error('unclosed Java block');
}
const cleanups = [...bridge.matchAll(/Runnable\s+cleanup\s*=\s*\(\)\s*->\s*\{/g)].map(match => blockAt(bridge, match.index));
const stops = [...bridge.matchAll(/public void stop\(\) \{/g)].map(match => blockAt(bridge, match.index));
const publications = [...bridge.matchAll(/playbackRef\.set\(playback\);/g)].map(match =>
  blockAt(bridge, bridge.lastIndexOf('synchronized (lifecycleLock)', match.index)));
const rejectionCallbacks = [...bridge.matchAll(/LazoDiscsText\.audioLoadBusy\(\)/g)].map(match =>
  blockAt(bridge, bridge.lastIndexOf('() -> {', match.index)));
assert.equal(cleanups.length, 3); assert.equal(stops.length, 3); assert.equal(publications.length, 3); assert.equal(rejectionCallbacks.length, 3);
const pipelines = cleanups.map((cleanup, index) => `
    static final class Pipeline${index} extends BasePipeline {
        ${cleanup};
        public void publish(LavaPcmFeeder.StreamingPlayback playback) { ${publications[index]} }
        public void reject() { Runnable onRejected = ${rejectionCallbacks[index]}; onRejected.run(); }
        public void clean() { cleanup.run(); }
        ${stops[index]}
    }
`).join('\n');
for (const project of projects) {
  const commands = readFileSync(join(project, 'command/LazoDiscsCommands.java'), 'utf8');
  assert.ok(commands.includes('ItemStack originalStack = stack;'));
  assert.ok(commands.includes('ItemStack originalSnapshot = stack.copy();'));
  assert.ok(compact(commands).includes('finishBurn(player,originalStack,originalSnapshot,url,title)'));
  const manager = readFileSync(join(project, 'server/JukeboxPlaybackManager.java'), 'utf8');
  assert.ok(compact(manager).includes('()->finishAt(level,pos,sourceRef.get(),"track-ended")'));
  assert.ok(manager.includes('sourceRef.set(source);'));
  const compat = readFileSync(join(project, 'compat/sophisticatedbackpacks/LazoDiscsDiscHandler.java'), 'utf8');
  assert.equal(compact(compat).match(/\(\)->onPlasmoFinished\(storageUuid,active\)/g)?.length, 4);
}

const javaSource = `
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.*;

public final class QualityFixesTest {
    static int checks;
    static void check(boolean condition, String scenario) {
        if (!condition) throw new AssertionError(scenario);
        checks++;
    }
    interface CheckedAction { void run() throws Exception; }
    static void denied(CheckedAction action, String scenario) throws Exception {
        try { action.run(); } catch (IllegalArgumentException expected) { checks++; return; }
        throw new AssertionError(scenario);
    }
    record Message(String text) { Message withStyle(ChatFormatting format) { return this; } }
    enum ChatFormatting { RED }
    static final class Value<T> { T value; Value(T value) { this.value = value; } T get() { return value; } }
    static final class LazoDiscsConfig {
        static final Value<Boolean> ALLOW_HTTP = new Value<>(false), ALLOW_HTTPS = new Value<>(true);
        static final Value<List<String>> ALLOWED_DOMAINS = new Value<>(List.of());
        static final Value<Integer> DEFAULT_RANGE = new Value<>(32), MAX_RANGE = new Value<>(128);
        static final Value<Double> DEFAULT_VOLUME = new Value<>(1.0);
        static final Value<String> LANGUAGE = new Value<>("en_us");
        static final Value<Integer> MAX_CONCURRENT_AUDIO_LOADS = new Value<>(1);
        static final Value<Boolean> REQUIRE_PERMISSION_FOR_BURN_COMMAND = new Value<>(true);
        static final Value<Boolean> REQUIRE_PERMISSION_FOR_ERASE_COMMAND = new Value<>(true);
        static final Value<Boolean> REQUIRE_PERMISSION_FOR_SEARCH_COMMAND = new Value<>(true);
        static final Value<Boolean> REQUIRE_PERMISSION_FOR_PLAY = new Value<>(true);
        static final Value<Integer> BURN_PERMISSION_LEVEL = new Value<>(2), ERASE_PERMISSION_LEVEL = new Value<>(2);
        static final Value<Integer> SEARCH_PERMISSION_LEVEL = new Value<>(2), PLAY_PERMISSION_LEVEL = new Value<>(2);
    }
    static final class PermissionLevel {
        final int id; PermissionLevel(int id) { this.id = id; } static PermissionLevel byId(int id) { return new PermissionLevel(id); }
    }
    interface Permission { record HasCommandLevel(PermissionLevel level) implements Permission {} }
    record PermissionSet(int level) { boolean hasPermission(Permission permission) { return level >= ((Permission.HasCommandLevel) permission).level().id; } }
    static final class CommandSourceStack {
        final int level; CommandSourceStack(int level) { this.level = level; }
        boolean hasPermission(int required) { return level >= required; } PermissionSet permissions() { return new PermissionSet(level); }
    }
    static final class Player {
        final int level; Player(int level) { this.level = level; }
        boolean hasPermissions(int required) { return level >= required; } PermissionSet permissions() { return new PermissionSet(level); }
    }
    ${permissionClasses}
    ${executorClass}
    static final class FabricConfig {
        ${fabricConfigClasses}
        ${stripConfigComment}
    }
    static final class LanguageSelection {
        static final String DEFAULT_LANGUAGE = "en_us";
        ${configuredLanguage}
    }
    static final class AddonLifecycle { ${onAddonShutdown} }
    static final class LazoDiscsDiscHandler { static int stops; static void stopAll(String reason) { stops++; } }
    static final class SableGuard {
        static boolean loaded; static boolean isSableLoaded() { return loaded; }
        ${isProbablySubLevel}
    }
    static final class MockConnection extends HttpURLConnection {
        static MockConnection latest; static int nextStatus = 200; static boolean failRead, failStatus;
        int disconnects; boolean streamClosed;
        MockConnection(URL url) { super(url); latest = this; }
        public void connect() {} public boolean usingProxy() { return false; } public void disconnect() { disconnects++; }
        public int getResponseCode() throws IOException { if (failStatus) throw new IOException("status failure"); return nextStatus; }
        public InputStream getInputStream() throws IOException {
            if (failRead) throw new IOException("read failure");
            return new ByteArrayInputStream("metadata".getBytes(StandardCharsets.UTF_8)) {
                public void close() throws IOException { streamClosed = true; super.close(); }
            };
        }
    }
    static final class MetadataHttp {
        record SpotifyMetadata(String title, List<String> artists, Long durationMs) {}
        record SplitTitle(String title, List<String> artists) {}
        static boolean failParse;
        static Optional<SpotifyMetadata> parseMetadataFromHtml(String html) {
            if (failParse) throw new IllegalArgumentException("parse failure");
            return Optional.of(new SpotifyMetadata(html, List.of(), null));
        }
        static Optional<String> extractJsonString(String json, String field) {
            if (failParse) throw new IllegalArgumentException("parse failure"); return Optional.of("Track");
        }
        static String stripSpotifyDecorations(String title) { return title; }
        static SplitTitle splitTitleAndArtist(String title) { return new SplitTitle(title, List.of("Artist")); }
        ${resolveFromSpotifyPage}
        ${resolveFromOEmbed}
    }
    static final class StringUtil { static boolean isNullOrEmpty(String value) { return value == null || value.isEmpty(); } }
    static final class SpotifyTitleResolver {
        static Optional<String> validateSingleTrack(String value) { return Optional.empty(); }
        static String canonicalize(String value) {
            return value.startsWith("spotify:track:") ? "https://open.spotify.com/track/" + value.substring(14) : value;
        }
        static boolean looksLikeSpotify(String value) { return value.startsWith("spotify:") || value.contains("open.spotify.com/"); }
    }
    static final class LazoDiscsText {
        static String urlEmpty() { return "empty"; } static String urlInvalid() { return "invalid"; }
        static String httpDisabled() { return "http disabled"; } static String httpsDisabled() { return "https disabled"; }
        static String unsupportedScheme() { return "unsupported scheme"; } static String domainNotAllowed() { return "domain disabled"; }
        static Message holdDisc() { return new Message("hold disc"); } static Message noPermission() { return new Message("denied"); }
        static Message invalidUrl(String reason) { return new Message(reason); } static Message burned(String title) { return new Message(title); }
        static Message audioLoadFailed(String title, String reason) { return new Message(reason); }
        static String audioLoadBusy() { return "busy"; }
    }
    static final class Logger { void info(String format, Object... args) {} void debug(String format, Object... args) {} void warn(String format, Object... args) {} }
    static final class LazoDiscs {
        static final Logger LOGGER = new Logger(); static final PlaybackManager PLAYBACK = new PlaybackManager();
        static PlaybackManager playback() { return PLAYBACK; }
    }
    static final class PlaybackManager {
        int resyncs, stops; void resyncFromBlockEntity(ServerLevel level, BlockPos pos, String reason) { resyncs++; }
        void stopAll(String reason) { stops++; }
    }
    static final class PlasmoVoiceBridge {
        static final PlasmoVoiceBridge INSTANCE = new PlasmoVoiceBridge(); boolean ready;
        boolean isInitialized() { return ready; }
        void shutdown() { ready = false; }
    }
    static final Map<SourceKey, ServerLevel> pendingResyncs = new HashMap<>();
    ${resyncLoadedJukeboxes}
    record CustomDiscData(String url, String title, int range, float volume, UUID id) {}
    static final class ItemStack {
        String metadata = "original"; boolean musicDisc = true, customDisc = true; int writes;
        ItemStack copy() { ItemStack copy = new ItemStack(); copy.metadata = metadata; copy.musicDisc = musicDisc; return copy; }
        static boolean matches(ItemStack a, ItemStack b) { return a.musicDisc == b.musicDisc && a.metadata.equals(b.metadata); }
    }
    static final class DiscDataUtil {
        ${validateUrl}
        ${clampRange}
        static boolean isMusicDisc(ItemStack stack) { return stack.musicDisc; }
        static boolean hasCustomDisc(ItemStack stack) { return stack.customDisc; }
        static void write(ItemStack stack, CustomDiscData data) { stack.writes++; stack.metadata = data.title(); }
    }
    static final class LazoDiscsPermissions { static boolean canBurn(ServerPlayer player) { return player.permitted; } }
    static final class ServerPlayer {
        ItemStack held = new ItemStack(); boolean removed, permitted = true; final List<Message> messages = new ArrayList<>();
        boolean isRemoved() { return removed; } ItemStack getMainHandItem() { return held; }
        ServerPlayer createCommandSourceStack() { return this; } void sendSystemMessage(Message message) { messages.add(message); }
        Vec3 position() { return new Vec3(); }
    }
    ${finishBurn}
    record SourceKey(String dimension, BlockPos pos) {}
    static final class BlockPos {
        final int x, z; BlockPos() { this(0, 0); } BlockPos(int x, int z) { this.x = x; this.z = z; }
        int getX() { return x; } int getZ() { return z; }
        static BlockPos containing(Object position) { return new BlockPos(); }
        BlockPos immutable() { return this; } String toShortString() { return "0,0,0"; }
    }
    record ItemTooltipEvent(ItemStack stack, List<String> tooltip) {
        ItemStack getItemStack() { return stack; } List<String> getToolTip() { return tooltip; }
    }
    static final class ClientTooltip { ${tooltipMethod} }
    static final class Vec3 { static Vec3 atCenterOf(BlockPos pos) { return new Vec3(); } double distanceToSqr(Vec3 other) { return 0; } }
    static final class Server {
        final Queue<Runnable> tasks = new ArrayDeque<>(); boolean inTask;
        void execute(Runnable task) { tasks.add(task); }
        void drain() { inTask = true; try { while (!tasks.isEmpty()) tasks.remove().run(); } finally { inTask = false; } }
    }
    static final class ServerLevel {
        final Server server = new Server(); final List<ServerPlayer> nearby = new ArrayList<>(); boolean loaded = true;
        String dimension() { return "test:world"; } Server getServer() { return server; }
        boolean hasChunkAt(BlockPos pos) { return loaded; }
        List<ServerPlayer> players() { if (!server.inTask) throw new AssertionError("players read outside server task"); return nearby; }
    }
    static final class PlayingVoiceSource { int stops; void stop() { stops++; } }
    record ActiveJukeboxSource(CustomDiscData disc, PlayingVoiceSource source) {}
    static final class Manager {
        final Map<SourceKey, ActiveJukeboxSource> active = new HashMap<>();
        ${finishAt}
    }
    static final class ActiveLazoSource {
        final ServerLevel level; final Vec3 lastPosition = new Vec3(); final Runnable onFinished;
        final AtomicReference<PlayingVoiceSource> sourceRef = new AtomicReference<>();
        ActiveLazoSource(ServerLevel level, Runnable onFinished, PlayingVoiceSource source) {
            this.level = level; this.onFinished = onFinished; sourceRef.set(source);
        }
    }
    static final Map<UUID, ActiveLazoSource> ACTIVE = new HashMap<>();
    static Runnable stopReentry;
    static void invokeStopPlayingDisc(ServerLevel level, Vec3 pos, UUID id) { if (stopReentry != null) stopReentry.run(); }
    ${onPlasmoFinished}
    static final class VoiceBridge { ${notifyLoadFailure} }
    static final class AudioTrackInfo { String title = "title", author = "author", uri = "https://example.com/music", identifier = "id"; long length = 1000; }
    enum AudioTrackState { FINISHED, INACTIVE, PLAYING }
    static final class AudioTrack {
        AudioTrackState state = AudioTrackState.PLAYING; long position;
        AudioTrackInfo getInfo() { return new AudioTrackInfo(); } AudioTrackState getState() { return state; } long getPosition() { return position; }
    }
    record AudioFrame(byte[] data) { byte[] getData() { return data; } }
    static final class AudioPlayer {
        final Queue<AudioFrame> frames = new ArrayDeque<>();
        void setVolume(int volume) {} void playTrack(AudioTrack track) {} AudioFrame provide() { return frames.poll(); }
    }
    static final class AudioPlayerManager { AudioPlayer createPlayer() { return new AudioPlayer(); } }
    record ResolveRequest(String identifier, Object metadata) {}
    record StreamingPlayback(AudioPlayer player, AudioTrack track) {}
    record ResolvedTrack(String title, String author, String url, long lengthMs) {}
    static final class LavaPcmFeeder {
        static final class StreamingPlayback {
            int closes; final AudioPlayer player = new AudioPlayer(); final AudioTrack track = new AudioTrack();
            AudioPlayer player() { return player; } AudioTrack track() { return track; } void close() { closes++; }
        }
    }
    interface AudioFrameProvider { AudioFrameResult provide20ms(); }
    static class AudioFrameResult {
        static final class Finished extends AudioFrameResult { static final Finished INSTANCE = new Finished(); }
        static final class Provided extends AudioFrameResult { final byte[] frame; Provided(byte[] frame) { this.frame = frame; } }
    }
    static final class Encryption { byte[] encrypt(byte[] frame) { return frame; } }
    static final class PlasmoVoiceServer { Encryption getDefaultEncryption() { return new Encryption(); } }
    static final class StreamingAudioFrameProvider implements AudioFrameProvider {
        final PlasmoVoiceServer server; final LavaPcmFeeder.StreamingPlayback playback; final AtomicBoolean stopped;
        StreamingAudioFrameProvider(PlasmoVoiceServer server, LavaPcmFeeder.StreamingPlayback playback, AtomicBoolean stopped) {
            this.server = server; this.playback = playback; this.stopped = stopped;
        }
        ${provide20ms}
    }
    static final class AudioSender {
        boolean started, stopped; Runnable callback;
        void onStop(Runnable callback) { this.callback = callback; }
        void start() { if (stopped) throw new AssertionError("sender started after stop"); started = true; }
        void stop() { stopped = true; }
    }
    static class ServerProximitySource<T> {
        int removals; final AudioSender sender = new AudioSender();
        void setName(String name) {} void remove() { removals++; }
        AudioSender createAudioSender(AudioFrameProvider provider, short range) { return sender; }
    }
    static class ServerStaticSource extends ServerProximitySource<Object> {}
    static final class ServerEntitySource extends ServerStaticSource {}
    static final class Line {
        CountDownLatch creationEntered, continueCreation; ServerStaticSource created;
        void awaitCreation() {
            if (creationEntered == null) return;
            creationEntered.countDown();
            try { if (!continueCreation.await(5, TimeUnit.SECONDS)) throw new AssertionError("creation barrier timeout"); }
            catch (InterruptedException exception) { throw new AssertionError(exception); }
        }
        ServerEntitySource createEntitySource(Object entity, boolean stereo) { awaitCreation(); var source = new ServerEntitySource(); created = source; return source; }
        ServerStaticSource createStaticSource(Object pos, boolean stereo) { awaitCreation(); var source = new ServerStaticSource(); created = source; return source; }
    }
    static abstract class BasePipeline {
        final Object lifecycleLock = new Object(), finalMcEntity = new Object(), pvPos = new Object();
        final AtomicBoolean stopped = new AtomicBoolean(), manualStop = new AtomicBoolean();
        final AtomicReference<LavaPcmFeeder.StreamingPlayback> playbackRef = new AtomicReference<>();
        final AtomicReference<ServerStaticSource> sourceRef = new AtomicReference<>();
        final AtomicReference<AudioSender> senderRef = new AtomicReference<>(); final AtomicReference<Future<?>> taskRef = new AtomicReference<>();
        final PlasmoVoiceServer server = new PlasmoVoiceServer(); final Line line = new Line();
        final ServerLevel level = new ServerLevel(); final Vec3 initialPos = new Vec3(); final BlockPos pos = new BlockPos();
        int finishedNotifications; final Runnable notifyFinished = () -> finishedNotifications++;
        final CustomDiscData disc = new CustomDiscData("https://example.com/music", "title", 32, 1, UUID.randomUUID());
        void notifyLoadFailure(ServerLevel level, BlockPos pos, CustomDiscData disc, String reason) { new VoiceBridge().notifyLoadFailure(level, pos, disc, reason); }
        abstract void publish(LavaPcmFeeder.StreamingPlayback playback); abstract void stop(); abstract void clean(); abstract void reject();
    }
    ${pipelines}
    static BasePipeline pipeline(int index) { return switch (index) { case 0 -> new Pipeline0(); case 1 -> new Pipeline1(); default -> new Pipeline2(); }; }

    static void verifyPipeline(int index) throws Exception {
        BasePipeline overloaded = pipeline(index); overloaded.reject();
        check(overloaded.stopped.get() && overloaded.finishedNotifications == 1 && overloaded.level.server.tasks.size() == 1,
                "busy request releases playback lifecycle and schedules a message, pipeline " + index);
        BasePipeline cancelled = pipeline(index); var cancelledPlayback = new LavaPcmFeeder.StreamingPlayback();
        cancelled.stop(); cancelled.publish(cancelledPlayback);
        check(cancelledPlayback.closes == 1 && cancelled.line.created == null, "cancel before resource publication, pipeline " + index);
        BasePipeline failedProvider = pipeline(index); var failedPlayback = new LavaPcmFeeder.StreamingPlayback();
        failedProvider.publish(failedPlayback); failedProvider.stopped.set(true); failedProvider.stop();
        check(failedPlayback.closes == 1 && failedProvider.line.created.removals == 1 && failedProvider.line.created.sender.stopped,
                "stop still cleans after provider set stopped, pipeline " + index);
        BasePipeline racing = pipeline(index); var racingPlayback = new LavaPcmFeeder.StreamingPlayback();
        racing.line.creationEntered = new CountDownLatch(1); racing.line.continueCreation = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread loader = new Thread(() -> { try { racing.publish(racingPlayback); } catch (Throwable t) { failure.set(t); } });
        loader.start(); check(racing.line.creationEntered.await(5, TimeUnit.SECONDS), "loader entered source creation, pipeline " + index);
        Thread stopper = new Thread(racing::stop); stopper.start();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (stopper.getState() != Thread.State.BLOCKED && stopper.isAlive() && System.nanoTime() < deadline) Thread.sleep(1);
        check(stopper.getState() == Thread.State.BLOCKED, "stop serializes with source/sender publication, pipeline " + index);
        racing.line.continueCreation.countDown(); loader.join(5000); stopper.join(5000);
        check(!loader.isAlive() && !stopper.isAlive() && failure.get() == null, "publication and stop complete, pipeline " + index);
        check(racingPlayback.closes == 1 && racing.line.created.removals == 1 && racing.line.created.sender.stopped,
                "concurrent stop leaves no source or playback, pipeline " + index);
    }
    static final class Feeder {
        static final AudioPlayerManager PLAYER_MANAGER = new AudioPlayerManager(); static int loads;
        static ResolveRequest resolveIdentifier(String raw, String title) { return new ResolveRequest(raw, null); }
        static AudioTrack loadTrack(AudioPlayerManager manager, String id, Object metadata) { loads++; return new AudioTrack(); }
        static void validateStreamingTrackLength(AudioTrack track) {} static String nullToUnknown(String value) { return value; }
        ${openStream}
        ${resolveTrack}
    }

    static void verifyPermissions() {
        for (int required = 0; required <= 4; required++) {
            LazoDiscsConfig.BURN_PERMISSION_LEVEL.value = required;
            LazoDiscsConfig.ERASE_PERMISSION_LEVEL.value = required;
            LazoDiscsConfig.SEARCH_PERMISSION_LEVEL.value = required;
            LazoDiscsConfig.PLAY_PERMISSION_LEVEL.value = required;
            for (int actual = 0; actual <= 4; actual++) {
                var source = new CommandSourceStack(actual); var player = new Player(actual);
                boolean expected = actual >= required;
                check(LegacyPermissions.canBurn(source) == expected && ModernPermissions.canBurn(source) == expected, "burn uses operator level");
                check(LegacyPermissions.canErase(source) == expected && ModernPermissions.canErase(source) == expected, "erase uses operator level");
                check(LegacyPermissions.canSearch(source) == expected && ModernPermissions.canSearch(source) == expected, "search uses operator level");
                check(LegacyPermissions.canPlay(player) == expected && ModernPermissions.canPlay(player) == expected, "play uses operator level");
            }
        }
        check(!LegacyPermissions.canBurn(null) && !ModernPermissions.canBurn(null), "unknown command source fails closed");
        check(!LegacyPermissions.canPlay(null) && !ModernPermissions.canPlay(null), "unknown player fails closed");
        LazoDiscsConfig.REQUIRE_PERMISSION_FOR_BURN_COMMAND.value = false;
        LazoDiscsConfig.REQUIRE_PERMISSION_FOR_ERASE_COMMAND.value = false;
        LazoDiscsConfig.REQUIRE_PERMISSION_FOR_SEARCH_COMMAND.value = false;
        LazoDiscsConfig.REQUIRE_PERMISSION_FOR_PLAY.value = false;
        check(LegacyPermissions.canBurn(null) && ModernPermissions.canBurn(null), "disabled burn gate stays disabled");
        check(LegacyPermissions.canErase(new CommandSourceStack(0)) && ModernPermissions.canErase(new CommandSourceStack(0)), "disabled erase gate stays disabled");
        check(LegacyPermissions.canSearch(new CommandSourceStack(0)) && ModernPermissions.canSearch(new CommandSourceStack(0)), "disabled search gate stays disabled");
        check(LegacyPermissions.canPlay(null) && ModernPermissions.canPlay(null), "disabled playback gate stays disabled");
    }

    static void verifyLoaderPool() throws Exception {
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger executions = new AtomicInteger();
        Future<?> running = RealAudioLoadExecutor.submit(() -> {
            started.countDown();
            try { release.await(); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        });
        check(started.await(5, TimeUnit.SECONDS), "loader worker starts");
        List<Future<?>> queued = new ArrayList<>();
        for (int i = 0; i < 16; i++) queued.add(RealAudioLoadExecutor.submit(executions::incrementAndGet));
        int[] rejections = {0}; Thread caller = Thread.currentThread();
        Future<?> rejected = RealAudioLoadExecutor.submit(executions::incrementAndGet, () -> {
            check(Thread.currentThread() == caller, "busy callback runs immediately on submitting thread"); rejections[0]++;
        });
        check(rejected.isDone() && rejections[0] == 1 && executions.get() == 0, "bounded queue reports rejection without executing network work inline");
        try { rejected.get(); throw new AssertionError("rejection future succeeded"); }
        catch (ExecutionException expected) { check(expected.getCause() instanceof RejectedExecutionException, "busy request has an exceptional completed future"); }
        RealAudioLoadExecutor.shutdownNow(); running.get(5, TimeUnit.SECONDS);
        check(queued.stream().allMatch(Future::isCancelled) && executions.get() == 0, "shutdown cancels every queued future");
        RealAudioLoadExecutor.submit(executions::incrementAndGet).get(5, TimeUnit.SECONDS);
        check(executions.get() == 1, "executor restarts after integrated-server shutdown");
        CountDownLatch oldStarted = new CountDownLatch(1), oldRelease = new CountDownLatch(1);
        Future<?> oldRunning = RealAudioLoadExecutor.submit(() -> {
            oldStarted.countDown();
            try { oldRelease.await(); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        });
        check(oldStarted.await(5, TimeUnit.SECONDS), "worker waits before resize");
        Future<?> accepted = RealAudioLoadExecutor.submit(executions::incrementAndGet);
        LazoDiscsConfig.MAX_CONCURRENT_AUDIO_LOADS.value = 2;
        RealAudioLoadExecutor.submit(executions::incrementAndGet).get(5, TimeUnit.SECONDS);
        accepted.get(5, TimeUnit.SECONDS);
        check(!oldRunning.isDone() && executions.get() == 3, "resize preserves accepted tasks and the running worker");
        LazoDiscsConfig.MAX_CONCURRENT_AUDIO_LOADS.value = 1;
        Future<?> afterShrink = RealAudioLoadExecutor.submit(executions::incrementAndGet);
        oldRelease.countDown(); oldRunning.get(5, TimeUnit.SECONDS); afterShrink.get(5, TimeUnit.SECONDS);
        check(executions.get() == 4, "downsize also preserves work");
        RealAudioLoadExecutor.shutdownNow();
    }

    static void verifyConfigAndHttp() {
        var volume = new FabricConfig.DoubleValue("volume", 1.0, 0.0, 4.0);
        for (String raw : List.of("NaN", "Infinity", "-Infinity", "not-a-number")) {
            volume.read(raw); check(volume.get() == 1.0, "invalid/non-finite config retains finite default: " + raw);
        }
        volume.read("2.5 # comment"); check(volume.get() == 2.5, "finite config and inline comments still parse");
        volume.read("8"); check(volume.get() == 4.0, "finite volume remains range-clamped");
        LazoDiscsConfig.LANGUAGE.value = " RU-ru ";
        check(LanguageSelection.configuredLanguage().equals("ru_ru"), "language identifiers normalize consistently");
        LazoDiscsConfig.LANGUAGE.value = "custom_locale2";
        check(LanguageSelection.configuredLanguage().equals("custom_locale2"), "custom language identifiers remain supported");
        for (String unsafe : List.of("../outside", "/tmp/outside", "a/b", "a\\\\b", ".", "..")) {
            LazoDiscsConfig.LANGUAGE.value = unsafe;
            check(LanguageSelection.configuredLanguage().equals("en_us"), "language selection cannot leave the language directory");
        }
        LazoDiscsConfig.LANGUAGE.value = "en_us";
        var distant = new BlockPos(20_000_000, -20_000_000);
        check(!SableGuard.isProbablySubLevel(distant), "distant vanilla jukebox is not a Sable plot without Sable");
        SableGuard.loaded = true;
        check(SableGuard.isProbablySubLevel(distant) && !SableGuard.isProbablySubLevel(new BlockPos()), "Sable plot heuristic remains gated and functional");

        URL.setURLStreamHandlerFactory(protocol -> protocol.equals("https") ? new URLStreamHandler() {
            protected URLConnection openConnection(URL url) { return new MockConnection(url); }
        } : null);
        for (boolean page : List.of(true, false)) {
            MockConnection.nextStatus = 404;
            check((page ? MetadataHttp.resolveFromSpotifyPage("https://open.spotify.com/track/123") : MetadataHttp.resolveFromOEmbed("https://open.spotify.com/track/123")).isEmpty()
                    && MockConnection.latest.disconnects == 1, "metadata HTTP error is disconnected");
            MockConnection.nextStatus = 200; MockConnection.failRead = true;
            check((page ? MetadataHttp.resolveFromSpotifyPage("https://open.spotify.com/track/123") : MetadataHttp.resolveFromOEmbed("https://open.spotify.com/track/123")).isEmpty()
                    && MockConnection.latest.disconnects == 1, "metadata read exception is disconnected");
            MockConnection.failRead = false; MetadataHttp.failParse = true;
            check((page ? MetadataHttp.resolveFromSpotifyPage("https://open.spotify.com/track/123") : MetadataHttp.resolveFromOEmbed("https://open.spotify.com/track/123")).isEmpty()
                    && MockConnection.latest.disconnects == 1 && MockConnection.latest.streamClosed, "metadata parse exception closes stream and connection");
            MetadataHttp.failParse = false;
            check((page ? MetadataHttp.resolveFromSpotifyPage("https://open.spotify.com/track/123") : MetadataHttp.resolveFromOEmbed("https://open.spotify.com/track/123")).isPresent()
                    && MockConnection.latest.disconnects == 1 && MockConnection.latest.streamClosed, "successful metadata closes stream and connection");
        }
        ItemStack custom = new ItemStack(); List<String> tooltip = new ArrayList<>(List.of("Custom title", "Vanilla song", "Lore"));
        ClientTooltip.onItemTooltip(new ItemTooltipEvent(custom, tooltip));
        check(tooltip.equals(List.of("Custom title")), "registered NeoForge tooltip retains custom title");
        custom.customDisc = false; tooltip = new ArrayList<>(List.of("Disc", "Song"));
        ClientTooltip.onItemTooltip(new ItemTooltipEvent(custom, tooltip));
        check(tooltip.equals(List.of("Disc", "Song")), "vanilla tooltip is not changed");
    }

    public static void main(String[] args) throws Exception {
        verifyPermissions(); verifyLoaderPool(); verifyConfigAndHttp();
        String valid = "https://example.com/music";
        ServerPlayer player = new ServerPlayer(); ItemStack original = player.held, snapshot = original.copy();
        player.held = original.copy();
        finishBurn(player, original, snapshot, valid, "wrong");
        check(player.held.writes == 0 && original.writes == 0, "swapping identical discs cannot burn replacement");
        player.held = original; original.metadata = "erased or changed";
        finishBurn(player, original, snapshot, valid, "wrong");
        check(original.writes == 0, "edited target cannot be overwritten by pending burn");
        snapshot = original.copy(); player.permitted = false;
        finishBurn(player, original, snapshot, valid, "wrong");
        check(original.writes == 0, "revoked burn permission is rechecked");
        player.permitted = true; player.removed = true;
        finishBurn(player, original, snapshot, valid, "wrong");
        check(original.writes == 0, "disconnected player is ignored");
        player.removed = false;
        finishBurn(player, original, snapshot, "http://example.com/music", "wrong");
        check(original.writes == 0, "current URL policy is rechecked before writing item");
        finishBurn(player, original, snapshot, valid, "first");
        finishBurn(player, original, snapshot, valid, "stale second completion");
        check(original.writes == 1 && original.metadata.equals("first"), "stale simultaneous completion cannot overwrite a successful burn");

        denied(() -> DiscDataUtil.validateUrl("file:///tmp/music.mp3"), "local file is rejected");
        denied(() -> DiscDataUtil.validateUrl("http://example.com/music"), "HTTP is disabled by default");
        denied(() -> DiscDataUtil.validateUrl("https:///music"), "missing URL host is rejected");
        check(DiscDataUtil.validateUrl(valid).equals(valid), "valid HTTPS URL is accepted");
        LazoDiscsConfig.ALLOWED_DOMAINS.value = List.of("example.com");
        denied(() -> DiscDataUtil.validateUrl("https://example.com.attacker.test/music"), "allowlist suffix deception is rejected");
        check(DiscDataUtil.validateUrl("https://cdn.example.com/music").contains("cdn.example.com"), "allowed subdomain is accepted");
        denied(() -> DiscDataUtil.validateUrl("spotify:track:123"), "Spotify URI respects domain allowlist");
        LazoDiscsConfig.ALLOWED_DOMAINS.value = List.of(); LazoDiscsConfig.ALLOW_HTTPS.value = false;
        denied(() -> DiscDataUtil.validateUrl("spotify:track:123"), "Spotify URI respects disabled HTTPS");
        LazoDiscsConfig.ALLOW_HTTPS.value = true;
        check(DiscDataUtil.validateUrl("spotify:track:123").equals("https://open.spotify.com/track/123"), "Spotify URI still canonicalizes");
        denied(() -> Feeder.openStream("file:///tmp/music.mp3", "title", 1), "stored file URL cannot reach audio loader");
        denied(() -> Feeder.openStream("http://example.com/music", "title", 1), "stored HTTP URL cannot reach audio loader");
        LazoDiscsConfig.ALLOWED_DOMAINS.value = List.of("example.com");
        denied(() -> Feeder.openStream("https://attacker.test/music", "title", 1), "stored forbidden domain cannot reach audio loader");
        denied(() -> Feeder.resolveTrack("https://attacker.test/music", "title"), "public resolveTrack enforces policy");
        check(Feeder.loads == 0, "invalid URL failures happen before loading audio");
        Feeder.openStream(valid, "title", 1); Feeder.resolveTrack(valid, "title");
        check(Feeder.loads == 2, "permitted playback and track resolution still load");

        ServerLevel level = new ServerLevel(); BlockPos pos = new BlockPos(); SourceKey key = new SourceKey(level.dimension(), pos);
        CustomDiscData disc = new CustomDiscData(valid, "same disc", 32, 1, UUID.randomUUID());
        PlayingVoiceSource oldSource = new PlayingVoiceSource(), newSource = new PlayingVoiceSource();
        Manager manager = new Manager(); manager.active.put(key, new ActiveJukeboxSource(disc, newSource));
        manager.finishAt(level, pos, oldSource, "old callback");
        check(manager.active.get(key).source() == newSource, "delayed same-disc callback preserves restarted jukebox");
        manager.finishAt(level, pos, newSource, "current callback");
        check(manager.active.isEmpty(), "current jukebox callback removes its own source");

        UUID storage = UUID.randomUUID(); int[] callbacks = {0};
        ActiveLazoSource old = new ActiveLazoSource(level, () -> callbacks[0]++, oldSource);
        ActiveLazoSource current = new ActiveLazoSource(level, () -> callbacks[0]++, newSource);
        ACTIVE.put(storage, current); onPlasmoFinished(storage, old);
        check(ACTIVE.get(storage) == current && newSource.stops == 0 && callbacks[0] == 0, "old backpack callback preserves replacement");
        stopReentry = () -> onPlasmoFinished(storage, current);
        onPlasmoFinished(storage, current); onPlasmoFinished(storage, current);
        check(ACTIVE.isEmpty() && newSource.stops == 1 && callbacks[0] == 1, "reentrant/repeated backpack callbacks close exactly once");
        check(current.sourceRef.get() == null, "backpack completion releases its source reference");

        ServerPlayer nearby = new ServerPlayer(); level.nearby.add(nearby);
        new VoiceBridge().notifyLoadFailure(level, pos, disc, "failed load");
        check(nearby.messages.isEmpty() && level.server.tasks.size() == 1, "loader failure schedules server-thread player access");
        level.server.drain();
        check(nearby.messages.size() == 1, "scheduled loader failure still reaches nearby player");

        var bufferedPlayback = new LavaPcmFeeder.StreamingPlayback(); bufferedPlayback.track.state = AudioTrackState.FINISHED;
        bufferedPlayback.player.frames.add(new AudioFrame(new byte[] {1, 2}));
        var provider = new StreamingAudioFrameProvider(new PlasmoVoiceServer(), bufferedPlayback, new AtomicBoolean());
        check(provider.provide20ms() instanceof AudioFrameResult.Provided frame && Arrays.equals(frame.frame, new byte[] {1, 2}),
                "finished decoder still delivers final buffered frame");
        check(provider.provide20ms() == AudioFrameResult.Finished.INSTANCE, "drained finished track terminates");
        bufferedPlayback.track.state = AudioTrackState.INACTIVE;
        check(provider.provide20ms() instanceof AudioFrameResult.Provided, "initial inactive track waits for first frame");
        for (int index = 0; index < 3; index++) verifyPipeline(index);

        pendingResyncs.put(key, level); resyncLoadedJukeboxes();
        check(pendingResyncs.size() == 1 && LazoDiscs.PLAYBACK.resyncs == 0, "chunk startup resync waits for voice initialization");
        PlasmoVoiceBridge.INSTANCE.ready = true; resyncLoadedJukeboxes();
        check(pendingResyncs.isEmpty() && LazoDiscs.PLAYBACK.resyncs == 1, "loaded custom jukebox resumes once voice is ready");
        level.loaded = false; pendingResyncs.put(key, level); resyncLoadedJukeboxes();
        check(pendingResyncs.isEmpty() && LazoDiscs.PLAYBACK.resyncs == 1, "unloaded chunks are discarded without loading them");
        new AddonLifecycle().onAddonShutdown();
        check(!PlasmoVoiceBridge.INSTANCE.ready && LazoDiscs.PLAYBACK.stops == 1 && LazoDiscsDiscHandler.stops == 1,
                "addon shutdown stops jukebox and backpack sources together");
        System.out.println("Passed " + checks + " behavior checks; shared production methods verified in all 22 variants.");
    }
}
`;

// Compile each distinct insertion implementation, not a hand-copied approximation.
const insertionVariants = new Map();
for (const project of projects) {
  const implementation = method(join(project, 'mixin/JukeboxPlayableMixin.java'), 'private static void lazodiscs$tryInsertIntoJukebox(')
    .replaceAll('ItemInteractionResult', 'InteractionResult');
  const fingerprint = implementation.replace(/\s+/g, ' ');
  if (!insertionVariants.has(fingerprint)) insertionVariants.set(fingerprint, implementation);
}
const insertionImplementations = [...insertionVariants.values()];
const insertionSource = `
import java.util.*;
public final class InsertionFixesTest {
    static int checks;
    static void check(boolean condition, String scenario) {
        if (!condition) throw new AssertionError(scenario); checks++;
    }
    enum InteractionResult { FAIL, SUCCESS }
    static final class CallbackInfoReturnable<T> { T result; void setReturnValue(T result) { this.result = result; } }
    record CustomDiscData(String title) {}
    static final class DiscDataUtil {
        static Optional<CustomDiscData> read(ItemStack stack) { return stack.custom ? Optional.of(new CustomDiscData("Custom title")) : Optional.empty(); }
    }
    static final class ItemStack {
        int count = 1; boolean custom = true;
        ItemStack consumeAndReturn(int consumed, Player player) { count -= consumed; return new ItemStack(); }
    }
    static final class BlockPos {}
    static final class Blocks { static final Object JUKEBOX = new Object(); }
    static final class JukeboxBlock { static final Object HAS_RECORD = new Object(); }
    static final class BlockState {
        boolean occupied; boolean is(Object block) { return true; } boolean getValue(Object property) { return occupied; }
    }
    static class Level {
        Object blockEntity; boolean client; final BlockState state = new BlockState();
        BlockState getBlockState(BlockPos pos) { return state; } Object getBlockEntity(BlockPos pos) { return blockEntity; }
        boolean isClientSide() { return client; } void gameEvent(Object event, BlockPos pos, Object context) {}
    }
    static final class ServerLevel extends Level {}
    static class JukeboxBlockEntity { ItemStack item; void setTheItem(ItemStack stack) { item = stack; } }
    interface LazoDiscJukeboxAccess { void lazodiscs$setLazoDiscItem(ItemStack stack); }
    static final class CustomJukebox extends JukeboxBlockEntity implements LazoDiscJukeboxAccess {
        public void lazodiscs$setLazoDiscItem(ItemStack stack) { item = stack; }
    }
    static final class Player {
        boolean permitted = true; int stats;
        void displayClientMessage(Object message, boolean overlay) {} void awardStat(Object stat) { stats++; }
    }
    static final class LazoDiscsPermissions { static boolean canPlay(Player player) { return player.permitted; } }
    static final class LazoDiscsServerBootstrap { static boolean isLoaded() { return true; } }
    static final class Stats { static final Object PLAY_RECORD = new Object(); }
    static final class Component { static String literal(String text) { return text; } }
    static final class LazoDiscsText {
        static String plasmoVoiceRequired() { return "dependency"; } static String playNoPermission() { return "denied"; }
        static String nowPlaying(String title) { return title; }
    }
    static final class GameEvent {
        static final Object BLOCK_CHANGE = new Object();
        static final class Context { static Object of(Player player, BlockState state) { return new Object(); } }
    }
    static final class Playback {
        int starts; void onJukeboxItemChanged(ServerLevel level, BlockPos pos, ItemStack record, String reason) { starts++; }
    }
    static final class LazoDiscs { static final Playback playback = new Playback(); static Playback playback() { return playback; } }
    ${insertionImplementations.map((implementation, index) => `static final class Version${index} { ${implementation} }`).join('\n')}
    interface Insertion { void apply(Level level, BlockPos pos, ItemStack stack, Player player, CallbackInfoReturnable<InteractionResult> cir); }
    public static void main(String[] args) {
        Insertion[] implementations = { ${insertionImplementations.map((_, index) => `Version${index}::lazodiscs$tryInsertIntoJukebox`).join(', ')} };
        for (Insertion insertion : implementations) {
            var level = new ServerLevel(); var stack = new ItemStack(); var player = new Player(); var pos = new BlockPos();
            var result = new CallbackInfoReturnable<InteractionResult>();
            insertion.apply(level, pos, stack, player, result);
            check(stack.count == 1 && player.stats == 0 && result.result == InteractionResult.FAIL, "missing block entity cannot consume a disc");
            level.blockEntity = new CustomJukebox(); result = new CallbackInfoReturnable<>();
            player.permitted = false; insertion.apply(level, pos, stack, player, result);
            check(stack.count == 1 && result.result == InteractionResult.FAIL, "denied insertion cannot consume a disc");
            player.permitted = true; result = new CallbackInfoReturnable<>(); int before = LazoDiscs.playback.starts;
            insertion.apply(level, pos, stack, player, result);
            check(stack.count == 0 && ((CustomJukebox) level.blockEntity).item != null && player.stats == 1
                    && LazoDiscs.playback.starts == before + 1 && result.result == InteractionResult.SUCCESS, "valid insertion consumes and plays exactly one disc");
            stack = new ItemStack(); level.blockEntity = new JukeboxBlockEntity(); result = new CallbackInfoReturnable<>();
            insertion.apply(level, pos, stack, player, result);
            check(result.result == InteractionResult.FAIL ? stack.count == 1 : ((JukeboxBlockEntity) level.blockEntity).item != null && stack.count == 0,
                    "missing access mixin either rejects safely or preserves the legacy vanilla fallback");
            stack = new ItemStack(); stack.custom = false; result = new CallbackInfoReturnable<>();
            insertion.apply(level, pos, stack, player, result);
            check(stack.count == 1 && result.result == null, "vanilla discs stay on the vanilla path");
        }
        System.out.println("Passed " + checks + " insertion checks across " + implementations.length + " distinct production implementations.");
    }
}
`;

const temporary = mkdtempSync(join(tmpdir(), 'lazodiscs-quality-'));
try {
  console.log(`Source inventory verified: ${projects.length * classInventory.length} Java files, ${classInventory.length} class families, ${projects.length} variants.`);
  const sourceFile = join(temporary, 'QualityFixesTest.java');
  writeFileSync(sourceFile, javaSource);
  const insertionFile = join(temporary, 'InsertionFixesTest.java');
  writeFileSync(insertionFile, insertionSource);
  const binary = name => process.env.JAVA_HOME ? join(process.env.JAVA_HOME, 'bin', name) : name;
  for (const [command, args] of [[binary('javac'), ['--release', '21', sourceFile, insertionFile]],
    [binary('java'), ['-cp', temporary, 'QualityFixesTest']], [binary('java'), ['-cp', temporary, 'InsertionFixesTest']]]) {
    const result = spawnSync(command, args, { encoding: 'utf8' });
    if (result.stdout) process.stdout.write(result.stdout);
    if (result.stderr) process.stderr.write(result.stderr);
    if (result.error) throw result.error;
    assert.equal(result.status, 0, `${command} failed`);
  }
} finally {
  rmSync(temporary, { recursive: true, force: true });
}
