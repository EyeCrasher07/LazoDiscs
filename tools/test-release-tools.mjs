import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { spawnSync } from 'node:child_process';
import {
  copyFileSync, existsSync, mkdirSync, mkdtempSync, readFileSync, readdirSync,
  rmSync, unlinkSync, writeFileSync,
} from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

// Exercise the real release tools in disposable repositories, never the working tree.
const sourceRoot = dirname(dirname(fileURLToPath(import.meta.url)));
const temporary = mkdtempSync(join(tmpdir(), 'lazo-release-tools-'));
const jarTool = process.env.JAVA_HOME ? join(process.env.JAVA_HOME, 'bin', 'jar') : 'jar';
const loaders = ['fabric', 'neoforge'];
const minecraftVersions = ['1.21.1', '1.21.2'];
let fixtureCount = 0;
let passed = 0;

function execute(command, args, options = {}) {
  const result = spawnSync(command, args, { encoding: 'utf8', timeout: 60_000, ...options });
  if (result.error) throw result.error;
  return { ...result, output: (result.stdout ?? '') + (result.stderr ?? '') };
}

function write(path, content) {
  mkdirSync(dirname(path), { recursive: true });
  writeFileSync(path, content);
}

function fixture(modId = 'lazoboombox') {
  const root = join(temporary, 'fixture-' + (++fixtureCount));
  const version = modId === 'lazoboombox' ? '0.1.2' : '1.0.5';
  const dependencies = {
    plasmovoice: '>=2.1.8',
    ...(modId === 'lazoboombox' ? { lazodiscs: '>=1.0.5' } : {}),
  };
  const release = {
    mod_id: modId, version, loaders, minecraft_versions: minecraftVersions,
    plasmo_voice_targets: { fabric: ['1.21.1'], neoforge: ['1.21.1'] },
    dependencies,
  };
  mkdirSync(join(root, 'tools'), { recursive: true });
  for (const filename of ['project-utils.mjs', 'prepare-release.mjs']) {
    copyFileSync(join(sourceRoot, 'tools', filename), join(root, 'tools', filename));
  }
  write(join(root, 'release.json'), JSON.stringify(release));
  write(join(root, 'release-notes', version + '.md'), '# Fixture release\n\nNot a real release.\n');
  const noticeFile = 'Fixture-upstream-LICENSE';
  const noticeText = 'Fixture legal resource, not actual licensing terms.\n';
  if (modId === 'lazodiscs') write(join(root, 'third-party', 'PROVENANCE.json'), JSON.stringify({
    files: [{ file: noticeFile, sha256: createHash('sha256').update(noticeText).digest('hex') }],
  }));

  const filename = (loader, minecraft) => `${modId}-${loader}-${version}+${minecraft}.jar`;
  const jarPath = (loader, minecraft) =>
    join(root, loader, minecraft, 'build', 'libs', filename(loader, minecraft));
  const output = (selection) => join(root, 'dist', version, selection);
  function writeJar(loader, minecraft, options = {}) {
    const contents = join(root, 'jar-contents', loader, minecraft);
    // This directory is owned by this fixture, not a project source directory.
    rmSync(contents, { recursive: true, force: true });
    mkdirSync(contents, { recursive: true });
    const metadataVersion = options.version ?? version + '+' + minecraft;
    const identity = options.modId ?? modId;
    const pvMinimum = options.pvMinimum === undefined ? '2.1.8' : options.pvMinimum;
    const discsMinimum = options.discsMinimum === undefined ? '1.0.5' : options.discsMinimum;
    if (options.license !== false) write(join(contents, 'LICENSE'), 'Fixture license only.\n');
    if (modId === 'lazodiscs') {
      for (const name of ['LICENSE', 'LICENSE.txt', 'NOTICE', 'NOTICE.txt']) {
        const entry = 'META-INF/' + name;
        if (entry !== options.omitNotice) write(join(contents, entry), noticeText);
      }
      const entry = 'META-INF/third-party/' + noticeFile;
      if (entry !== options.omitNotice) write(join(contents, entry), options.alterNotice ? 'Incomplete fixture\n' : noticeText);
    }
    if (loader === 'fabric') {
      const depends = { minecraft: options.minecraft ?? minecraft, java: '>=21' };
      if (pvMinimum !== null) depends.plasmovoice = '>=' + pvMinimum;
      if (modId === 'lazoboombox' && discsMinimum !== null) depends.lazodiscs = '>=' + discsMinimum;
      write(join(contents, 'fabric.mod.json'), JSON.stringify({
        schemaVersion: 1, id: identity, version: metadataVersion, depends,
      }));
    } else {
      const next = Number(minecraft.split('.')[2]) + 1;
      const blocks = [
        'modLoader="javafml"\nloaderVersion="[4,)"\nlicense="GPL-3.0-only"',
        `[[mods]]\nmodId="${identity}"\nversion="${metadataVersion}"\ndisplayName="Fixture"`,
        `[[dependencies.${modId}]]\nmodId="minecraft"\ntype="required"\nversionRange="${options.minecraft ?? `[${minecraft},1.21.${next})`}"`,
      ];
      for (const [dependency, minimum] of [
        ['plasmovoice', pvMinimum],
        ...(modId === 'lazoboombox' ? [['lazodiscs', discsMinimum]] : []),
      ]) {
        if (minimum !== null) blocks.push(
          `[[dependencies.${modId}]]\nmodId="${dependency}"\ntype="${options.dependencyType ?? 'required'}"\nversionRange="[${minimum},)"`,
        );
      }
      write(join(contents, 'META-INF', 'neoforge.mods.toml'), blocks.join('\n\n'));
    }
    if (modId === 'lazoboombox' && options.loot !== false) {
      write(join(contents, 'data', modId, 'loot_table', 'blocks', 'boombox.json'), '{"pools":[]}');
    }
    const audioEntries = [
      'com/sedmelluq/discord/lavaplayer/player/AudioPlayer.class',
      'dev/lavalink/youtube/YoutubeAudioSourceManager.class',
      'com/eyecrasher/lazodiscs/shadow/com/fasterxml/jackson/databind/ObjectMapper.class',
      'com/eyecrasher/lazodiscs/shadow/org/apache/http/impl/client/CloseableHttpClient.class',
    ];
    for (const entry of [
      ...(modId === 'lazodiscs' ? audioEntries.filter(entry => entry !== options.omitAudio) : []),
      ...(options.extraEntries ?? []),
    ]) write(join(contents, entry), Buffer.from([0]));
    const destination = jarPath(loader, minecraft);
    mkdirSync(dirname(destination), { recursive: true });
    const result = execute(jarTool, ['--create', '--file', destination, '-C', contents, '.']);
    assert.equal(result.status, 0, 'Could not create fixture JAR: ' + result.output);
    return destination;
  }
  for (const loader of loaders) {
    for (const minecraft of minecraftVersions) writeJar(loader, minecraft);
  }
  return {
    root, release, filename, jarPath, writeJar, output,
    run: (...args) => execute(process.execPath,
      [join(root, 'tools', 'prepare-release.mjs'), ...args], { cwd: root }),
  };
}

function success(result) {
  assert.equal(result.status, 0, result.output);
}

function rejection(f, options, pattern) {
  f.writeJar('fabric', '1.21.1', options);
  const result = f.run('--all-targets', '--loader=fabric', '--minecraft=1.21.1');
  assert.notEqual(result.status, 0, 'Invalid artifact was accepted');
  assert.match(result.output, pattern);
  assert.equal(existsSync(join(f.root, 'dist')), false, 'Failure left a partial release directory');
}

function bundle(f, selection, expectedFiles) {
  const directory = f.output(selection);
  const manifest = JSON.parse(readFileSync(join(directory, 'manifest.json'), 'utf8'));
  assert.equal(manifest.mod_id, f.release.mod_id);
  assert.equal(manifest.version, f.release.version);
  assert.deepEqual(manifest.dependencies, f.release.dependencies);
  assert.deepEqual(manifest.artifacts.map(a => a.file).sort(), [...expectedFiles].sort());
  assert.deepEqual(readdirSync(directory).filter(name => name.endsWith('.jar')).sort(),
    [...expectedFiles].sort(), 'Bundle contains stale or unlisted JARs');
  const checksums = new Map(readFileSync(join(directory, 'SHA256SUMS'), 'utf8').trim()
    .split('\n').map(line => {
      const [hash, name] = line.split('  ');
      return [name, hash];
    }));
  assert.equal(checksums.size, expectedFiles.length);
  for (const artifact of manifest.artifacts) {
    const hash = createHash('sha256').update(readFileSync(join(directory, artifact.file))).digest('hex');
    assert.equal(artifact.sha256, hash);
    assert.equal(checksums.get(artifact.file), hash);
    assert.equal(artifact.in_game_verified, false);
    assert.equal(artifact.plasmo_voice_build_available, artifact.minecraft === '1.21.1');
    assert.equal('source' in artifact, false, 'Manifest leaked local source paths');
  }
  assert.match(readFileSync(join(directory, 'CHANGELOG.md'), 'utf8'), /Fixture release/);
  return directory;
}

function test(name, body) {
  body();
  passed++;
  console.log('PASS ' + name);
}

try {
  assert.equal(execute(jarTool, ['--version']).status, 0, 'JDK jar tool is required');
  assert.equal(execute('unzip', ['-v']).status, 0, 'unzip is required');

  test('publish candidates exclude Minecraft targets without an official PV build', () => {
    const f = fixture();
    success(f.run());
    bundle(f, 'publish-candidates', loaders.map(loader => f.filename(loader, '1.21.1')));
  });

  test('full then filtered bundles have separate, complete manifests and file sets', () => {
    const f = fixture();
    success(f.run('--all-targets'));
    const all = loaders.flatMap(loader => minecraftVersions.map(mc => f.filename(loader, mc)));
    bundle(f, 'all-targets', all);
    success(f.run('--all-targets', '--loader=fabric', '--minecraft=1.21.1'));
    bundle(f, 'all-targets-fabric-1.21.1', [f.filename('fabric', '1.21.1')]);
    bundle(f, 'all-targets', all);
  });

  test('repeated selection replaces stale artifacts and preserves its previous bundle', () => {
    const f = fixture();
    const args = ['--all-targets', '--loader=fabric', '--minecraft=1.21.1'];
    success(f.run(...args));
    const directory = f.output('all-targets-fabric-1.21.1');
    write(join(directory, 'obsolete.jar'), 'Stale generated artifact');
    write(join(directory, 'manifest.json'), '{"stale":true}');
    success(f.run(...args));
    bundle(f, 'all-targets-fabric-1.21.1', [f.filename('fabric', '1.21.1')]);
    const backups = join(f.root, 'dist', f.release.version, '.previous');
    const previous = readdirSync(backups);
    assert.equal(previous.length, 1);
    assert.equal(readFileSync(join(backups, previous[0], 'obsolete.jar'), 'utf8'),
      'Stale generated artifact');
  });

  test('downloaded CI JAR input is used instead of local build outputs', () => {
    const f = fixture();
    const input = join(f.root, 'ci=jars');
    mkdirSync(input);
    for (const loader of loaders) {
      for (const mc of minecraftVersions) {
        copyFileSync(f.jarPath(loader, mc), join(input, f.filename(loader, mc)));
      }
    }
    f.writeJar('fabric', '1.21.1', { modId: 'incorrect' });
    success(f.run('--input=' + input));
    bundle(f, 'publish-candidates', loaders.map(loader => f.filename(loader, '1.21.1')));
  });

  test('missing release notes do not create any partial bundle', () => {
    const f = fixture();
    unlinkSync(join(f.root, 'release-notes', f.release.version + '.md'));
    const result = f.run('--all-targets');
    assert.notEqual(result.status, 0);
    assert.match(result.output, /ENOENT/);
    assert.equal(existsSync(join(f.root, 'dist')), false);
  });

  test('a missing later artifact does not leave copies of earlier artifacts', () => {
    const f = fixture();
    unlinkSync(f.jarPath('neoforge', '1.21.2'));
    const result = f.run('--all-targets');
    assert.notEqual(result.status, 0);
    assert.match(result.output, /Missing release JAR/);
    assert.equal(existsSync(join(f.root, 'dist')), false);
  });

  test('a failed revalidation preserves the existing complete selection', () => {
    const f = fixture();
    const args = ['--all-targets', '--loader=fabric', '--minecraft=1.21.1'];
    success(f.run(...args));
    const directory = f.output('all-targets-fabric-1.21.1');
    const original = readFileSync(join(directory, 'manifest.json'), 'utf8');
    f.writeJar('fabric', '1.21.1', { version: '0.0.0' });
    assert.notEqual(f.run(...args).status, 0);
    assert.equal(readFileSync(join(directory, 'manifest.json'), 'utf8'), original);
    bundle(f, 'all-targets-fabric-1.21.1', [f.filename('fabric', '1.21.1')]);
  });

  for (const [name, options, pattern] of [
    ['version', { version: '0.0.0' }, /Incorrect Fabric metadata/],
    ['Minecraft target', { minecraft: '1.21.2' }, /Incorrect Fabric metadata/],
    ['mod ID', { modId: 'another_mod' }, /Incorrect Fabric metadata/],
    ['PV minimum', { pvMinimum: '2.1.7' }, /Incorrect runtime dependencies/],
    ['missing PV dependency', { pvMinimum: null }, /Incorrect runtime dependencies/],
    ['LazoDiscs minimum', { discsMinimum: '1.0.4' }, /Incorrect runtime dependencies/],
    ['missing LazoDiscs dependency', { discsMinimum: null }, /Incorrect runtime dependencies/],
    ['missing license', { license: false }, /Missing LICENSE/],
    ['missing loot table', { loot: false }, /Missing Boombox loot table/],
  ]) test('reject Fabric ' + name, () => rejection(fixture(), options, pattern));

  for (const [name, options, pattern] of [
    ['version', { version: '0.0.0' }, /Incorrect NeoForge version/],
    ['Minecraft bounds', { minecraft: '[1.21.2,1.21.3)' }, /Incorrect NeoForge Minecraft bounds/],
    ['mod ID', { modId: 'another_mod' }, /Incorrect NeoForge mod ID/],
    ['PV minimum', { pvMinimum: '2.1.7' }, /Incorrect plasmovoice dependency/],
    ['missing PV dependency', { pvMinimum: null }, /Incorrect plasmovoice dependency/],
    ['LazoDiscs minimum', { discsMinimum: '1.0.4' }, /Incorrect lazodiscs dependency/],
    ['missing LazoDiscs dependency', { discsMinimum: null }, /Incorrect lazodiscs dependency/],
    ['optional mandatory dependency', { dependencyType: 'optional' }, /Incorrect plasmovoice dependency/],
  ]) test('reject NeoForge ' + name, () => {
    const f = fixture();
    f.writeJar('neoforge', '1.21.1', options);
    const result = f.run('--all-targets', '--loader=neoforge', '--minecraft=1.21.1');
    assert.notEqual(result.status, 0);
    assert.match(result.output, pattern);
    assert.equal(existsSync(join(f.root, 'dist')), false);
  });

  for (const entry of [
    'net/minecraft/AccidentallyBundled.class',
    'su/plo/voice/AccidentallyBundled.class',
    'org/slf4j/AccidentallyBundled.class',
    'com/google/common/AccidentallyBundled.class',
    'com/google/gson/AccidentallyBundled.class',
    'com/fasterxml/jackson/AccidentallyBundled.class',
    'org/apache/http/AccidentallyBundled.class',
    'com/eyecrasher/lazodiscs/AccidentallyBundled.class',
    'com/sedmelluq/AccidentallyBundled.class',
    'dev/lavalink/AccidentallyBundled.class',
  ]) test('reject forbidden bundled class ' + entry, () =>
    rejection(fixture(), { extraEntries: [entry] }, /Unexpected bundled classes/));

  test('Discs accepts its required private audio libraries', () => {
    const f = fixture('lazodiscs');
    success(f.run());
    bundle(f, 'publish-candidates', loaders.map(loader => f.filename(loader, '1.21.1')));
  });

  for (const entry of [
    'com/sedmelluq/discord/lavaplayer/player/AudioPlayer.class',
    'dev/lavalink/youtube/YoutubeAudioSourceManager.class',
    'com/eyecrasher/lazodiscs/shadow/com/fasterxml/jackson/databind/ObjectMapper.class',
    'com/eyecrasher/lazodiscs/shadow/org/apache/http/impl/client/CloseableHttpClient.class',
  ]) test('reject missing Discs audio library ' + entry, () =>
    rejection(fixture('lazodiscs'), { omitAudio: entry }, /Missing audio dependency/));

  for (const entry of ['META-INF/LICENSE', 'META-INF/LICENSE.txt', 'META-INF/NOTICE',
    'META-INF/NOTICE.txt', 'META-INF/third-party/Fixture-upstream-LICENSE']) {
    test('reject missing Discs legal resource ' + entry, () =>
      rejection(fixture('lazodiscs'), { omitNotice: entry }, /Missing bundled legal resource/));
  }
  test('reject incomplete upstream legal resource', () =>
    rejection(fixture('lazodiscs'), { alterNotice: true }, /Incomplete bundled legal resource/));

  test('unsupported PV target cannot be selected without --all-targets', () => {
    const f = fixture();
    const result = f.run('--minecraft=1.21.2');
    assert.notEqual(result.status, 0);
    assert.match(result.output, /No publishable targets selected/);
    assert.equal(existsSync(join(f.root, 'dist')), false);
  });

  test('invalid, empty, repeated, and unknown arguments are rejected', () => {
    const f = fixture();
    for (const args of [
      ['--unknown'],
      ['--input='],
      ['--loader='],
      ['--minecraft='],
      ['--all-targets', '--all-targets'],
      ['--loader=fabric', '--loader=neoforge'],
      ['--loader=fabric=oops'],
      ['--minecraft=1.21.1=oops'],
      ['--loader=forge'],
      ['--minecraft=1.20.1'],
    ]) {
      const result = f.run(...args);
      assert.notEqual(result.status, 0, 'Unexpected success: ' + args.join(' '));
      assert.match(result.output, /Usage:|Unknown loader or Minecraft version/);
    }
    assert.equal(existsSync(join(f.root, 'dist')), false);
  });

  console.log(`Release tooling regressions: ${passed} passed. Production files were not modified.`);
} finally {
  // Only delete the exact mkdtemp-owned test directory.
  rmSync(temporary, { recursive: true, force: true });
}
