import { createHash } from 'node:crypto';
import { copyFileSync, existsSync, mkdirSync, mkdtempSync, readFileSync, renameSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { execFileSync } from 'node:child_process';
import { root, release, variants } from './project-utils.mjs';

const args = process.argv.slice(2);
if (args.some(arg => arg !== '--all-targets' && !/^--(?:input|loader|minecraft)=.+$/.test(arg)) ||
    new Set(args.map(arg => arg.split('=')[0])).size !== args.length) {
  console.error('Usage: node tools/prepare-release.mjs [--all-targets] [--input=directory] [--loader=fabric|neoforge] [--minecraft=1.21.1]');
  process.exit(2);
}
const allTargets = args.includes('--all-targets');
const option = name => args.find(arg => arg.startsWith('--' + name + '='))?.slice(name.length + 3);
const input = option('input');
const loader = option('loader');
const minecraft = option('minecraft');
if ((loader && !release.loaders.includes(loader)) ||
    (minecraft && !release.minecraft_versions.includes(minecraft))) {
  throw new Error('Unknown loader or Minecraft version');
}
const selected = variants().filter(v =>
  (!loader || v.loader === loader) && (!minecraft || v.minecraft === minecraft) &&
  (allTargets || release.plasmo_voice_targets[v.loader].includes(v.minecraft)));
if (!selected.length) throw new Error('No publishable targets selected');
const parent = join(root, 'dist', release.version);
const mode = allTargets ? 'all-targets' : 'publish-candidates';
const selection = [mode, loader, minecraft].filter(Boolean).join('-');
const output = join(parent, selection);
const notes = join(root, 'release-notes', release.version + '.md');
const changelog = readFileSync(notes, 'utf8');
const artifacts = [];
const thirdPartyProvenance = release.mod_id === 'lazodiscs'
  ? JSON.parse(readFileSync(join(root, 'third-party', 'PROVENANCE.json'), 'utf8'))
  : undefined;

function jarText(jar, entry) {
  return execFileSync('unzip', ['-p', jar, entry], { encoding: 'utf8', maxBuffer: 8 * 1024 * 1024 });
}

for (const variant of selected) {
  const jar = input ? join(resolve(input), variant.filename) :
    join(variant.directory, 'build', 'libs', variant.filename);
  if (!existsSync(jar)) throw new Error(`Missing release JAR: ${jar}; build this target first.`);
  const entries = execFileSync('unzip', ['-Z1', jar], {
    encoding: 'utf8', maxBuffer: 16 * 1024 * 1024,
  }).trim().split(/\r?\n/);
  const forbidden = [
    /^net\/minecraft\/.*\.class$/, /^su\/plo\/voice\/.*\.class$/,
    /^org\/slf4j\/.*\.class$/, /^com\/google\/(?:common|gson)\/.*\.class$/,
    /^com\/fasterxml\/jackson\/.*\.class$/, /^org\/apache\/http\/.*\.class$/,
  ];
  if (release.mod_id === 'lazoboombox') {
    forbidden.push(/^com\/eyecrasher\/lazodiscs\/.*\.class$/,
      /^com\/sedmelluq\/.*\.class$/, /^dev\/lavalink\/.*\.class$/);
  }
  const duplicateClasses = entries.filter((entry, index) =>
    entry.endsWith('.class') && entries.indexOf(entry) !== index);
  const bad = entries.filter(entry => forbidden.some(pattern => pattern.test(entry)));
  if (bad.length || duplicateClasses.length) {
    throw new Error(`Unexpected bundled classes in ${variant.filename}: ${[...bad, ...duplicateClasses].slice(0, 10).join(', ')}`);
  }
  if (!entries.includes('LICENSE')) throw new Error(`Missing LICENSE in ${variant.filename}`);
  if (variant.loader === 'fabric') {
    const metadata = JSON.parse(jarText(jar, 'fabric.mod.json'));
    if (metadata.id !== release.mod_id || metadata.version !== variant.version ||
        metadata.depends.minecraft !== variant.minecraft) {
      throw new Error(`Incorrect Fabric metadata in ${variant.filename}`);
    }
    if (metadata.depends.plasmovoice !== release.dependencies.plasmovoice ||
        (release.mod_id === 'lazoboombox' &&
         metadata.depends.lazodiscs !== release.dependencies.lazodiscs)) {
      throw new Error(`Incorrect runtime dependencies in ${variant.filename}`);
    }
  } else {
    const metadata = jarText(jar, 'META-INF/neoforge.mods.toml');
    const identity = metadata.split('[[mods]]')[1]?.split('[[')[0] ?? '';
    if (!new RegExp('modId\\s*=\\s*"' + release.mod_id + '"').test(identity)) {
      throw new Error(`Incorrect NeoForge mod ID in ${variant.filename}`);
    }
    if (!metadata.includes(`version="${variant.version}"`) &&
        !metadata.includes(`version = "${variant.version}"`)) {
      throw new Error(`Incorrect NeoForge version in ${variant.filename}`);
    }
    if (!metadata.includes(`[${variant.minecraft},1.21.${Number(variant.minecraft.split('.')[2]) + 1})`)) {
      throw new Error(`Incorrect NeoForge Minecraft bounds in ${variant.filename}`);
    }
    for (const [dependency, minimum] of Object.entries({
      plasmovoice: '2.1.8',
      ...(release.mod_id === 'lazoboombox' ? { lazodiscs: '1.0.5' } : {}),
    })) {
      const block = metadata.split(`[[dependencies.${release.mod_id}]]`).slice(1)
        .map(text => text.split('[[')[0])
        .find(text => new RegExp('modId\\s*=\\s*"' + dependency + '"').test(text));
      if (!block || !new RegExp('type\\s*=\\s*"required"').test(block) ||
          !block.includes(`[${minimum},)`)) {
        throw new Error(`Incorrect ${dependency} dependency in ${variant.filename}`);
      }
    }
  }
  if (release.mod_id === 'lazodiscs') {
    for (const entry of [
      'com/sedmelluq/discord/lavaplayer/player/AudioPlayer.class',
      'dev/lavalink/youtube/YoutubeAudioSourceManager.class',
      'com/eyecrasher/lazodiscs/shadow/com/fasterxml/jackson/databind/ObjectMapper.class',
      'com/eyecrasher/lazodiscs/shadow/org/apache/http/impl/client/CloseableHttpClient.class',
    ]) {
      if (!entries.includes(entry)) throw new Error(`Missing audio dependency ${entry}`);
    }
    for (const entry of ['META-INF/LICENSE', 'META-INF/LICENSE.txt', 'META-INF/NOTICE', 'META-INF/NOTICE.txt']) {
      if (!entries.includes(entry)) throw new Error(`Missing bundled legal resource ${entry}`);
    }
    for (const notice of thirdPartyProvenance.files) {
      const entry = 'META-INF/third-party/' + notice.file;
      if (!entries.includes(entry)) throw new Error(`Missing bundled legal resource ${entry}`);
      const sha256 = createHash('sha256').update(jarText(jar, entry), 'utf8').digest('hex');
      if (sha256 !== notice.sha256) throw new Error(`Incomplete bundled legal resource ${entry}`);
    }
  } else if (!entries.includes('data/lazoboombox/loot_table/blocks/boombox.json')) {
    throw new Error(`Missing Boombox loot table in ${variant.filename}`);
  }
  artifacts.push({
    file: variant.filename, minecraft: variant.minecraft, loader: variant.loader,
    version: variant.version,
    sha256: createHash('sha256').update(readFileSync(jar)).digest('hex'),
    plasmo_voice_build_available: release.plasmo_voice_targets[variant.loader].includes(variant.minecraft),
    in_game_verified: false,
    source: jar,
  });
}
// Stage a complete selection; never mix a new manifest with old or partial artifacts.
mkdirSync(parent, { recursive: true });
const staging = mkdtempSync(join(parent, '.stage-'));
for (const artifact of artifacts) copyFileSync(artifact.source, join(staging, artifact.file));
writeFileSync(join(staging, 'CHANGELOG.md'), changelog);
writeFileSync(join(staging, 'SHA256SUMS'), artifacts.map(a => `${a.sha256}  ${a.file}`).join('\n') + '\n');
writeFileSync(join(staging, 'manifest.json'), JSON.stringify({
  mod_id: release.mod_id, version: release.version, dependencies: release.dependencies,
  warning: 'Build/packaging checked. Complete the in-game release checklist before publishing.',
  artifacts: artifacts.map(({ source, ...artifact }) => artifact),
}, null, 2) + '\n');
let backup;
if (existsSync(output)) {
  const backups = join(parent, '.previous');
  mkdirSync(backups, { recursive: true });
  backup = join(backups, selection + '-' + Date.now());
  renameSync(output, backup);
}
try {
  renameSync(staging, output);
} catch (error) {
  if (backup) renameSync(backup, output);
  throw error;
}
if (backup) console.log(`Previous generated bundle preserved in ${backup}`);
console.log(`Prepared ${artifacts.length} verified JARs in ${output}. Nothing has been published.`);
