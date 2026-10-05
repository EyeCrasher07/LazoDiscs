#!/usr/bin/env node
// Compile each real NeoForge event class against minimal, version-correct API doubles.
// This exercises startup scheduling, not a Minecraft/PV audio integration test.
import assert from 'node:assert/strict';
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { spawnSync } from 'node:child_process';
import { variants, root } from './project-utils.mjs';

if (process.argv.length !== 2) throw new Error('Usage: node tools/test-jukebox-resync.mjs');
const temp = mkdtempSync(join(tmpdir(), 'lazodiscs-jukebox-resync-'));
const java = name => process.env.JAVA_HOME ? join(process.env.JAVA_HOME, 'bin', name) : name;
const relativeSource = 'src/main/java/com/eyecrasher/lazodiscs/event/JukeboxEvents.java';
const projects = variants().filter(project => project.loader === 'neoforge');
assert.equal(projects.length, 11);
const sources = {
  'net/minecraft/world/level/LevelAccessor.java': `package net.minecraft.world.level; public interface LevelAccessor {}`,
  'net/minecraft/core/BlockPos.java': `package net.minecraft.core; public record BlockPos(int x,int y,int z) {}`,
  'net/minecraft/world/level/ChunkPos.java': `package net.minecraft.world.level;
    public final class ChunkPos {public final int x,z; public ChunkPos(int x,int z){this.x=x;this.z=z;}
      @Override public boolean equals(Object other){return other instanceof ChunkPos pos&&pos.x==x&&pos.z==z;}
      @Override public int hashCode(){return 31*x+z;}}`,
  'net/minecraft/server/MinecraftServer.java': `package net.minecraft.server;
    public class MinecraftServer {private final Thread main=Thread.currentThread();
      public final java.util.List<net.minecraft.server.level.ServerLevel> levels=new java.util.ArrayList<>();
      public Iterable<net.minecraft.server.level.ServerLevel> getAllLevels(){return levels;}
      public void assertMain(){if(Thread.currentThread()!=main)throw new AssertionError("off-thread level interaction");}}`,
  'net/minecraft/server/level/ServerLevel.java': `package net.minecraft.server.level;
    public class ServerLevel implements net.minecraft.world.level.LevelAccessor {
      private final net.minecraft.server.MinecraftServer server; public final ServerChunkCache chunks;
      public ServerLevel(net.minecraft.server.MinecraftServer server){this.server=server;chunks=new ServerChunkCache(server);server.levels.add(this);}
      public net.minecraft.server.MinecraftServer getServer(){return server;}
      public ServerChunkCache getChunkSource(){server.assertMain();return chunks;}
      public net.minecraft.world.level.chunk.LevelChunk getChunk(int x,int z){throw new AssertionError("forced chunk load");}
      public boolean hasChunkAt(net.minecraft.core.BlockPos pos){throw new AssertionError("unexpected level query");}}`,
  'net/minecraft/server/level/ServerChunkCache.java': `package net.minecraft.server.level;
    public class ServerChunkCache {private final net.minecraft.server.MinecraftServer server;public int queries;
      public final java.util.Map<net.minecraft.world.level.ChunkPos,net.minecraft.world.level.chunk.LevelChunk> full=new java.util.HashMap<>();
      public ServerChunkCache(net.minecraft.server.MinecraftServer server){this.server=server;}
      public net.minecraft.world.level.chunk.LevelChunk getChunkNow(int x,int z){server.assertMain();queries++;return full.get(new net.minecraft.world.level.ChunkPos(x,z));}
      public net.minecraft.world.level.chunk.LevelChunk getChunk(int x,int z){throw new AssertionError("forced chunk load");}}`,
  'net/minecraft/world/level/chunk/ChunkAccess.java': `package net.minecraft.world.level.chunk;
    public class ChunkAccess {private final net.minecraft.world.level.LevelAccessor level;private final net.minecraft.world.level.ChunkPos pos;
      public ChunkAccess(net.minecraft.world.level.LevelAccessor level,net.minecraft.world.level.ChunkPos pos){this.level=level;this.pos=pos;}
      public net.minecraft.world.level.LevelAccessor getLevel(){return level;}public net.minecraft.world.level.ChunkPos getPos(){return pos;}}`,
  'net/minecraft/world/level/chunk/LevelChunk.java': `package net.minecraft.world.level.chunk;
    public class LevelChunk extends ChunkAccess {public int scans;
      public final java.util.Map<net.minecraft.core.BlockPos,net.minecraft.world.level.block.entity.BlockEntity> entities=new java.util.LinkedHashMap<>();
      public LevelChunk(net.minecraft.world.level.LevelAccessor level,net.minecraft.world.level.ChunkPos pos){super(level,pos);}
      public java.util.Map<net.minecraft.core.BlockPos,net.minecraft.world.level.block.entity.BlockEntity> getBlockEntities(){
        if(getLevel() instanceof net.minecraft.server.level.ServerLevel level)level.getServer().assertMain();scans++;return entities;}}`,
  'net/minecraft/world/level/block/entity/BlockEntity.java': `package net.minecraft.world.level.block.entity;
    public class BlockEntity {private final net.minecraft.core.BlockPos pos;public BlockEntity(net.minecraft.core.BlockPos pos){this.pos=pos;}
      public net.minecraft.core.BlockPos getBlockPos(){return pos;}}`,
  'net/minecraft/world/level/block/entity/JukeboxBlockEntity.java': `package net.minecraft.world.level.block.entity;
    public class JukeboxBlockEntity extends BlockEntity {public net.minecraft.world.item.ItemStack item;
      public JukeboxBlockEntity(net.minecraft.core.BlockPos pos,net.minecraft.world.item.ItemStack item){super(pos);this.item=item;}
      public net.minecraft.world.item.ItemStack getTheItem(){return item;}}`,
  'net/minecraft/world/item/ItemStack.java': `package net.minecraft.world.item;
    public class ItemStack {public final com.eyecrasher.lazodiscs.data.CustomDiscData disc;
      public ItemStack(com.eyecrasher.lazodiscs.data.CustomDiscData disc){this.disc=disc;}}`,
  'net/minecraft/world/level/block/Blocks.java': `package net.minecraft.world.level.block; public class Blocks {public static final Object JUKEBOX=new Object();}`,
  'net/minecraft/world/level/block/state/BlockState.java': `package net.minecraft.world.level.block.state;
    public class BlockState {private final Object block;public BlockState(Object block){this.block=block;}public boolean is(Object candidate){return block==candidate;}}`,
  'net/neoforged/bus/api/SubscribeEvent.java': `package net.neoforged.bus.api;
    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface SubscribeEvent {}`,
  'net/neoforged/neoforge/event/level/BlockEvent.java': `package net.neoforged.neoforge.event.level;
    public class BlockEvent {public static class BreakEvent {private final net.minecraft.world.level.LevelAccessor level;
      private final net.minecraft.core.BlockPos pos;private final net.minecraft.world.level.block.state.BlockState state;
      public BreakEvent(net.minecraft.world.level.LevelAccessor level,net.minecraft.core.BlockPos pos,Object block){this.level=level;this.pos=pos;state=new net.minecraft.world.level.block.state.BlockState(block);}
      public net.minecraft.world.level.LevelAccessor getLevel(){return level;}public net.minecraft.core.BlockPos getPos(){return pos;}
      public net.minecraft.world.level.block.state.BlockState getState(){return state;}}}`,
  'net/neoforged/neoforge/event/tick/ServerTickEvent.java': `package net.neoforged.neoforge.event.tick;
    public class ServerTickEvent {public static class Post {private final net.minecraft.server.MinecraftServer server;
      public Post(net.minecraft.server.MinecraftServer server){this.server=server;}public net.minecraft.server.MinecraftServer getServer(){return server;}}}`,
  'com/eyecrasher/lazodiscs/data/CustomDiscData.java': `package com.eyecrasher.lazodiscs.data; public record CustomDiscData(String id) {}`,
  'com/eyecrasher/lazodiscs/data/DiscDataUtil.java': `package com.eyecrasher.lazodiscs.data;
    public class DiscDataUtil {public static java.util.Optional<CustomDiscData> read(net.minecraft.world.item.ItemStack item){return java.util.Optional.ofNullable(item.disc);}}`,
  'com/eyecrasher/lazodiscs/voice/PlasmoVoiceBridge.java': `package com.eyecrasher.lazodiscs.voice;
    public class PlasmoVoiceBridge {public static final PlasmoVoiceBridge INSTANCE=new PlasmoVoiceBridge();public boolean ready;
      public boolean isInitialized(){return ready;}}`,
  'com/eyecrasher/lazodiscs/LazoDiscs.java': `package com.eyecrasher.lazodiscs;
    public class LazoDiscs {public static final com.eyecrasher.lazodiscs.server.JukeboxPlaybackManager MANAGER=new com.eyecrasher.lazodiscs.server.JukeboxPlaybackManager();
      public static com.eyecrasher.lazodiscs.server.JukeboxPlaybackManager playback(){return MANAGER;}}`,
  'com/eyecrasher/lazodiscs/server/JukeboxPlaybackManager.java': `package com.eyecrasher.lazodiscs.server;
    public class JukeboxPlaybackManager {
      public record Key(net.minecraft.server.level.ServerLevel level,net.minecraft.core.BlockPos pos){}
      public final java.util.Map<Key,com.eyecrasher.lazodiscs.data.CustomDiscData> active=new java.util.HashMap<>();
      public int starts,startCalls,rewinds,ticks,stopChunks,stopAll,stopAt;
      public void start(net.minecraft.server.level.ServerLevel level,net.minecraft.core.BlockPos pos,com.eyecrasher.lazodiscs.data.CustomDiscData disc,String reason){
        level.getServer().assertMain();if(!com.eyecrasher.lazodiscs.voice.PlasmoVoiceBridge.INSTANCE.ready)throw new AssertionError("started before Plasmo ready");
        startCalls++;var key=new Key(level,pos);if(disc.equals(active.get(key)))return;active.put(key,disc);starts++;}
      public void resyncFromBlockEntity(net.minecraft.server.level.ServerLevel level,net.minecraft.core.BlockPos pos,String reason){rewinds++;}
      public void tickLevel(net.minecraft.server.level.ServerLevel level){level.getServer().assertMain();ticks++;}
      public void stopChunk(net.minecraft.server.level.ServerLevel level,net.minecraft.world.level.ChunkPos pos,String reason){stopChunks++;}
      public void stopAll(String reason){stopAll++;active.clear();}
      public void stopAt(net.minecraft.server.level.ServerLevel level,net.minecraft.core.BlockPos pos,String reason){stopAt++;}
      public void reset(){active.clear();starts=startCalls=rewinds=ticks=stopChunks=stopAll=stopAt=0;}}`,
  'com/eyecrasher/lazodiscs/compat/sophisticatedbackpacks/LazoDiscsDiscHandler.java': `package com.eyecrasher.lazodiscs.compat.sophisticatedbackpacks;
    public class LazoDiscsDiscHandler {public static int ticks,stops;
      public static void onServerTick(net.minecraft.server.MinecraftServer server){server.assertMain();ticks++;}
      public static void stopAll(String reason){stops++;}}`,
  'JukeboxResyncRegression.java': `
    import com.eyecrasher.lazodiscs.event.JukeboxEvents;
    import com.eyecrasher.lazodiscs.LazoDiscs;
    import com.eyecrasher.lazodiscs.data.CustomDiscData;
    import com.eyecrasher.lazodiscs.compat.sophisticatedbackpacks.LazoDiscsDiscHandler;
    import com.eyecrasher.lazodiscs.voice.PlasmoVoiceBridge;
    import net.minecraft.server.MinecraftServer;
    import net.minecraft.server.level.ServerLevel;
    import net.minecraft.core.BlockPos;
    import net.minecraft.world.level.ChunkPos;
    import net.minecraft.world.level.chunk.*;
    import net.minecraft.world.level.block.entity.*;
    import net.minecraft.world.item.ItemStack;
    import net.neoforged.neoforge.event.level.ChunkEvent;
    import net.neoforged.neoforge.event.server.*;
    import net.neoforged.neoforge.event.tick.ServerTickEvent;
    public class JukeboxResyncRegression {
      static int assertions;static boolean legacy;
      static void check(boolean ok,String message){assertions++;if(!ok)throw new AssertionError(message);}
      static void optional(String name,Class<?> type,Object event)throws Exception {
        try{JukeboxEvents.class.getMethod(name,type).invoke(null,event);}catch(NoSuchMethodException ignored){}}
      static void about(MinecraftServer server)throws Exception{optional("onServerAboutToStart",ServerAboutToStartEvent.class,new ServerAboutToStartEvent(server));}
      static void load(ChunkAccess chunk)throws Exception {
        var event=ChunkEvent.Load.class.getConstructor(legacy?ChunkAccess.class:LevelChunk.class,boolean.class).newInstance(chunk,false);
        optional("onChunkLoad",ChunkEvent.Load.class,event);}
      static void unload(LevelChunk chunk)throws Exception {
        var event=ChunkEvent.Unload.class.getConstructor(legacy?ChunkAccess.class:LevelChunk.class).newInstance(chunk);
        JukeboxEvents.onChunkUnload((ChunkEvent.Unload)event);}
      static void tick(MinecraftServer server){JukeboxEvents.onServerTick(new ServerTickEvent.Post(server));}
      static int pending()throws Exception{var field=JukeboxEvents.class.getDeclaredField("pendingResyncs");field.setAccessible(true);return ((java.util.Set<?>)field.get(null)).size();}
      static LevelChunk chunk(ServerLevel level,int x,CustomDiscData disc){var chunk=new LevelChunk(level,new ChunkPos(x,0));
        chunk.entities.put(new BlockPos(x*16,64,0),new JukeboxBlockEntity(new BlockPos(x*16,64,0),new ItemStack(disc)));return chunk;}
      static void reset(MinecraftServer server)throws Exception{about(server);LazoDiscs.MANAGER.reset();PlasmoVoiceBridge.INSTANCE.ready=false;LazoDiscsDiscHandler.ticks=LazoDiscsDiscHandler.stops=0;}
      public static void main(String[]args)throws Exception {
        legacy=args[0].equals("legacy");var server=new MinecraftServer();var level=new ServerLevel(server);
        var disc=new CustomDiscData("saved");var saved=chunk(level,0,disc);var manager=LazoDiscs.MANAGER;
        reset(server);
        if(args.length>1){load(saved);tick(server);level.chunks.full.put(saved.getPos(),saved);PlasmoVoiceBridge.INSTANCE.ready=true;tick(server);
          check(manager.starts==1,"saved jukebox was not recovered after Plasmo initialized");return;}
        saved.entities.put(new BlockPos(1,64,0),new JukeboxBlockEntity(new BlockPos(1,64,0),new ItemStack(null)));
        saved.entities.put(new BlockPos(2,64,0),new BlockEntity(new BlockPos(2,64,0)));
        var failure=new java.util.concurrent.atomic.AtomicReference<Throwable>();var workers=new java.util.ArrayList<Thread>();
        for(int i=0;i<4;i++){var worker=new Thread(()->{try{for(int j=0;j<32;j++)load(saved);}catch(Throwable error){failure.set(error);}});workers.add(worker);worker.start();}
        for(var worker:workers)worker.join();check(failure.get()==null,"load touched level off-thread: "+failure.get());
        check(pending()==1,"duplicate chunk loads were not coalesced");check(level.chunks.queries==0&&saved.scans==0,"load scanned or resolved chunk");
        tick(server);check(manager.starts==0&&pending()==1,"startup request lost before Plasmo ready");
        check(level.chunks.queries==0,"queried chunks while Plasmo not ready");check(manager.ticks==1&&LazoDiscsDiscHandler.ticks==1,"normal tick handlers skipped");
        PlasmoVoiceBridge.INSTANCE.ready=true;tick(server);check(pending()==1&&manager.starts==0,"request lost before FULL promotion");
        check(saved.scans==0,"scanned chunk before FULL promotion");level.chunks.full.put(saved.getPos(),saved);tick(server);
        check(manager.starts==1&&manager.startCalls==1,"custom disc was not recovered exactly once");
        check(saved.scans==1&&pending()==0,"resolved chunk request was not drained");check(manager.active.size()==1,"vanilla/non-jukebox source started");
        int queries=level.chunks.queries;tick(server);check(level.chunks.queries==queries&&saved.scans==1,"normal ticks scan loaded world");
        load(saved);load(saved);tick(server);check(manager.starts==1&&manager.rewinds==0,"active identical source rewound");
        check(manager.startCalls==2,"duplicate load requests were scanned more than once");
        var other=new ServerLevel(server);var second=chunk(other,0,new CustomDiscData("dimension"));other.chunks.full.put(second.getPos(),second);
        load(saved);load(second);check(pending()==2,"same coordinates in different levels collapsed");tick(server);
        check(manager.starts==2&&manager.active.size()==2,"dimension-specific saved source missing");
        reset(server);var unloaded=chunk(level,1,disc);load(unloaded);check(pending()==1,"unload fixture was not enqueued");unload(unloaded);
        check(pending()==0&&manager.stopChunks==1,"chunk unload retained request or skipped playback stop");
        level.chunks.full.put(unloaded.getPos(),unloaded);PlasmoVoiceBridge.INSTANCE.ready=true;tick(server);
        check(manager.starts==0&&unloaded.scans==0,"unloaded startup jukebox was restarted");
        reset(server);var removed=chunk(level,2,disc);load(removed);((JukeboxBlockEntity)removed.entities.values().iterator().next()).item=new ItemStack(null);
        level.chunks.full.put(removed.getPos(),removed);PlasmoVoiceBridge.INSTANCE.ready=true;tick(server);
        check(manager.starts==0&&pending()==0,"removed custom disc was revived from stale load snapshot");
        reset(server);load(saved);JukeboxEvents.onServerStopping(new ServerStoppingEvent(server));
        check(pending()==0&&manager.stopAll==1&&LazoDiscsDiscHandler.stops==1,"server stop retained requests or sources");
        load(saved);check(pending()==0,"late old-world load accepted after stopping");
        var reopened=new MinecraftServer();var freshLevel=new ServerLevel(reopened);var fresh=chunk(freshLevel,0,disc);
        reset(reopened);load(saved);check(pending()==0,"old server load accepted after reopening");load(fresh);
        check(pending()==1,"new integrated server did not accept loads");freshLevel.chunks.full.put(fresh.getPos(),fresh);PlasmoVoiceBridge.INSTANCE.ready=true;tick(reopened);
        check(manager.starts==1&&manager.active.keySet().iterator().next().level()==freshLevel,"reopened server did not recover its own jukebox");
        var client=new net.minecraft.world.level.LevelAccessor(){};load(new LevelChunk(client,new ChunkPos(0,0)));
        check(pending()==0,"client chunk queued server playback");
        if(legacy){load(new ChunkAccess(freshLevel,new ChunkPos(3,0)));check(pending()==0,"protochunk queued playback");}
        int stops=manager.stopAt;
        JukeboxEvents.onBlockBreak(new net.neoforged.neoforge.event.level.BlockEvent.BreakEvent(freshLevel,new BlockPos(0,64,0),new Object()));
        check(manager.stopAt==stops,"non-jukebox break stopped playback");
        JukeboxEvents.onBlockBreak(new net.neoforged.neoforge.event.level.BlockEvent.BreakEvent(client,new BlockPos(0,64,0),net.minecraft.world.level.block.Blocks.JUKEBOX));
        check(manager.stopAt==stops,"client break stopped playback");
        JukeboxEvents.onBlockBreak(new net.neoforged.neoforge.event.level.BlockEvent.BreakEvent(freshLevel,new BlockPos(0,64,0),net.minecraft.world.level.block.Blocks.JUKEBOX));
        check(manager.stopAt==stops+1,"server jukebox break did not stop playback");
        check(manager.ticks==1&&LazoDiscsDiscHandler.ticks==1,"normal reopened-server tick handlers skipped");
        check(manager.rewinds==0,"item-change resync bypassed start deduplication");
        System.out.println("PASS: deferred jukebox startup, pre-FULL retry, concurrency, unload, reopening, filters, active-source dedup ("+assertions+" assertions)");
      }
    }`,
};
for (const event of ['ServerAboutToStartEvent', 'ServerStoppingEvent']) {
  sources[`net/neoforged/neoforge/event/server/${event}.java`] = `package net.neoforged.neoforge.event.server;
    public class ${event} {private final net.minecraft.server.MinecraftServer server;
      public ${event}(net.minecraft.server.MinecraftServer server){this.server=server;}public net.minecraft.server.MinecraftServer getServer(){return server;}}`;
}

function chunkEvent(legacy) {
  const chunkType = legacy ? 'ChunkAccess' : 'LevelChunk';
  return `package net.neoforged.neoforge.event.level;
    import net.minecraft.world.level.chunk.*;
    public class ChunkEvent${legacy ? '' : '<T extends ChunkAccess>'} {
      private final ${legacy ? 'ChunkAccess' : 'T'} chunk;
      public ChunkEvent(${legacy ? 'ChunkAccess' : 'T'} chunk){this.chunk=chunk;}
      public ${legacy ? 'ChunkAccess' : 'T'} getChunk(){return chunk;}
      public net.minecraft.world.level.LevelAccessor getLevel(){return chunk.getLevel();}
      public static class Load extends ChunkEvent${legacy ? '' : '<LevelChunk>'} {public Load(${chunkType} chunk,boolean fresh){super(chunk);}}
      public static class Unload extends ChunkEvent${legacy ? '' : '<LevelChunk>'} {public Unload(${chunkType} chunk){super(chunk);}}}`;
}
function write(path, text) {
  mkdirSync(dirname(path), { recursive: true });
  writeFileSync(path, text);
  return path;
}
function run(command, args) {
  const result = spawnSync(command, args, { cwd: root, encoding: 'utf8', timeout: 30_000 });
  if (result.error) throw result.error;
  if (result.status !== 0) throw new Error(`${command} failed\n${result.stdout}${result.stderr}`);
  return result.stdout;
}
function compile(project, baseline = false) {
  const legacy = Number(project.minecraft.split('.').at(-1)) <= 3;
  const directory = join(temp, baseline ? 'baseline' : project.minecraft);
  const fakes = { ...sources, 'net/neoforged/neoforge/event/level/ChunkEvent.java': chunkEvent(legacy) };
  const files = Object.entries(fakes).map(([name, text]) => write(join(directory, 'src', name), text));
  const production = baseline
    ? run('git', ['show', `HEAD:${project.relative}/${relativeSource}`])
    : readFileSync(join(project.directory, relativeSource), 'utf8');
  files.push(write(join(directory, 'src/com/eyecrasher/lazodiscs/event/JukeboxEvents.java'), production));
  const output = join(directory, 'classes');
  mkdirSync(output, { recursive: true });
  run(java('javac'), ['--release', '21', '-d', output, ...files]);
  return { output, api: legacy ? 'legacy' : 'modern' };
}

try {
  for (const project of projects) {
    const { output, api } = compile(project);
    process.stdout.write(`${project.relative}: ${run(java('java'), ['-cp', output, 'JukeboxResyncRegression', api])}`);
  }
  const { output, api } = compile(projects[0], true);
  const negative = spawnSync(java('java'), ['-cp', output, 'JukeboxResyncRegression', api, 'baseline'], {
    cwd: root, encoding: 'utf8', timeout: 30_000,
  });
  assert.equal(negative.status, 1, 'Original NeoForge event class unexpectedly recovered saved playback');
  assert.match(negative.stderr, /saved jukebox was not recovered after Plasmo initialized/);
  console.log('PASS: original NeoForge event class fails the startup-recovery negative control.');
} finally {
  rmSync(temp, { recursive: true, force: true });
}
