import assert from 'node:assert/strict';
import { existsSync } from 'node:fs';
import { join, relative } from 'node:path';
import { root, release, variants, walk, read, readJson } from './project-utils.mjs';

// From the matching Mojang client JAR version.json, not guessed from MC versions.
const formats = {
  '1.21.1': [34, 48], '1.21.2': [42, 57], '1.21.3': [42, 57],
  '1.21.4': [46, 61], '1.21.5': [55, 71], '1.21.6': [63, 80],
  '1.21.7': [64, 81], '1.21.8': [64, 81],
  '1.21.9': [69, 88], '1.21.10': [69, 88], '1.21.11': [75, [94, 1]],
};
let javaFiles = 0;
let jsonFiles = 0;
let checks = 0;
function check(condition, message) {
  checks++;
  assert.ok(condition, message);
}
function ownAsset(resources, location, kind, extension) {
  if (typeof location !== 'string' || !location.startsWith(release.mod_id + ':')) return;
  const path = join(resources, 'assets', release.mod_id, kind, location.split(':')[1] + extension);
  check(existsSync(path), `Missing referenced asset: ${path}`);
}

for (const variant of variants()) {
  const resources = join(variant.directory, 'src', 'main', 'resources');
  const javaRoot = join(variant.directory, 'src', 'main', 'java');
  const sourceFiles = walk(javaRoot).filter(path => path.endsWith('.java'));
  javaFiles += sourceFiles.length;
  const build = read(join(variant.directory, 'build.gradle'));
  check(build.includes(`version = '${variant.version}'`), `Wrong build version: ${variant.relative}`);
  check(build.includes('dev.arbjerg:lavaplayer:2.2.7'), `LavaPlayer drift: ${variant.relative}`);
  check(!build.includes('resolutionStrategy.force'), `Global dependency force: ${variant.relative}`);
  if (release.mod_id === 'lazodiscs') {
    check(build.includes('com.fasterxml.jackson:jackson-bom:2.21.7'), `Jackson drift: ${variant.relative}`);
    check(build.includes('org.apache.httpcomponents:httpclient:4.5.14'), `HttpClient drift: ${variant.relative}`);
    check(build.includes('2be8e542d3f6f178e048dca565892684c2e40177-SNAPSHOT'), `YouTube pin drift: ${variant.relative}`);
    for (const dependency of ['org.mozilla:rhino:1.7.15.1', 'org.mozilla:rhino-engine:1.7.15.1', 'org.json:json:20260719']) {
      check(build.includes(`shadeRuntime '${dependency}'`), `Audio security pin drift: ${dependency} in ${variant.relative}`);
    }
    for (const group of ['org.mozilla', 'org.json']) {
      check(build.split(`exclude group: '${group}'`).length === 3,
        `Audio runtime library leaked into Minecraft's compile graph: ${group} in ${variant.relative}`);
    }
    check(build.includes("apply from: '../../gradle/third-party-notices.gradle'"),
      `Missing bundled legal-resource gate: ${variant.relative}`);
  } else {
    check(build.includes("compileOnly('dev.arbjerg:lavaplayer:2.2.7')"),
      `Boombox must not bundle LavaPlayer: ${variant.relative}`);
    check(build.includes("exclude 'com/eyecrasher/lazodiscs/**'"),
      `Boombox must exclude its compile-only stubs: ${variant.relative}`);
  }
  const wrapper = read(join(variant.directory, 'gradle', 'wrapper', 'gradle-wrapper.properties'));
  check(wrapper.includes('gradle-8.14.5-bin.zip'), `Wrapper drift: ${variant.relative}`);
  check(wrapper.includes('distributionSha256Sum=6f74b601422d6d6fc4e1f9a1ab6522f642c2fdcbc15ae33ebd30ba3d7198e854'),
    `Wrapper checksum missing: ${variant.relative}`);
  const properties = join(variant.directory, 'gradle.properties');
  check(!existsSync(properties) || !read(properties).includes('/tmp/'),
    `Machine-local JDK path: ${variant.relative}`);

  if (variant.loader === 'fabric') {
    const metadata = readJson(join(resources, 'fabric.mod.json'));
    check(metadata.id === release.mod_id && metadata.version === '${version}',
      `Wrong Fabric identity/version template: ${variant.relative}`);
    check(metadata.depends.minecraft === variant.minecraft, `Wrong MC target: ${variant.relative}`);
    check(metadata.depends.plasmovoice === '>=2.1.8', `PV minimum drift: ${variant.relative}`);
    if (release.mod_id === 'lazoboombox') {
      check(metadata.depends.lazodiscs === '>=1.0.5', `Discs minimum drift: ${variant.relative}`);
    }
    check(existsSync(join(resources, metadata.icon)), `Missing icon: ${variant.relative}`);
    for (const entry of Object.values(metadata.entrypoints).flat()) {
      const name = typeof entry === 'string' ? entry : entry.value;
      check(existsSync(join(javaRoot, name.replaceAll('.', '/') + '.java')),
        `Missing entrypoint ${name}`);
    }
    if (metadata.accessWidener) {
      check(metadata.accessWidener.endsWith('.accesswidener'), 'Misleading access-widener extension');
      check(read(join(resources, metadata.accessWidener)).startsWith('accessWidener '),
        `Invalid access widener: ${variant.relative}`);
    }
  } else {
    const metadata = read(join(resources, 'META-INF', 'neoforge.mods.toml'));
    const upper = '1.21.' + (Number(variant.minecraft.split('.')[2]) + 1);
    check(metadata.includes(`[${variant.minecraft},${upper})`), `Wrong MC upper bound: ${variant.relative}`);
    check(metadata.includes('[2.1.8,)'), `PV minimum drift: ${variant.relative}`);
    check(metadata.includes('${version}'), `Hard-coded NeoForge mod version: ${variant.relative}`);
    if (release.mod_id === 'lazoboombox') {
      check(metadata.includes('[1.0.5,)'), `Discs minimum drift: ${variant.relative}`);
    }
  }

  const pack = readJson(join(resources, 'pack.mcmeta')).pack;
  const [resource, data] = formats[variant.minecraft];
  if (Number(variant.minecraft.split('.')[2]) >= 9) {
    // A shared resource/data pack must retain legacy fields below both MC thresholds.
    assert.deepEqual([pack.pack_format, pack.min_format, pack.max_format, pack.supported_formats],
      [64, 64, data, { min_inclusive: 64, max_inclusive: Array.isArray(data) ? data[0] : data }],
      `Wrong modern resource/data formats: ${variant.relative}`);
  } else {
    assert.deepEqual([pack.pack_format, pack.supported_formats],
      [resource, { min_inclusive: resource, max_inclusive: data }],
      `Wrong resource/data formats: ${variant.relative}`);
  }
  checks++;

  for (const path of walk(resources).filter(path => path.endsWith('.json'))) {
    const value = readJson(path);
    const portablePath = path.replaceAll('\\', '/');
    jsonFiles++;
    if (path.endsWith('.mixins.json')) {
      for (const name of [...(value.mixins ?? []), ...(value.client ?? []), ...(value.server ?? [])]) {
        check(existsSync(join(javaRoot, value.package.replaceAll('.', '/'), name + '.java')),
          `Missing mixin class: ${name}`);
      }
    }
    if (portablePath.includes('/models/')) {
      ownAsset(resources, value.parent, 'models', '.json');
      for (const texture of Object.values(value.textures ?? {})) {
        ownAsset(resources, texture, 'textures', '.png');
      }
    }
    if (portablePath.includes('/items/')) ownAsset(resources, value.model?.model, 'models', '.json');
    if (portablePath.includes('/blockstates/')) {
      for (const entry of Object.values(value.variants ?? {}).flat()) {
        ownAsset(resources, entry.model, 'models', '.json');
      }
    }
  }
  const language = readJson(join(resources, 'assets', release.mod_id, 'lang', 'en_us.json'));
  for (const path of sourceFiles) {
    const source = read(path);
    for (const match of source.matchAll(/Component\.translatable\(\s*"([^"]+)"/g)) {
      if (match[1].startsWith(release.mod_id + '.')) {
        check(Object.hasOwn(language, match[1]), `Missing client translation ${match[1]} in ${variant.relative}`);
      }
    }
    if (path.endsWith('VoiceAddon.java') && path.includes(join(release.mod_id, 'voice'))) {
      check(source.includes(`"${variant.version}"`), `Plasmo addon version drift: ${relative(root, path)}`);
    }
  }
  if (release.mod_id === 'lazodiscs') {
    const translations = read(join(resources, 'lazodiscs_languages', 'en_us.toml'));
    check(translations.includes('audio.load_busy ='), `Missing loader busy message: ${variant.relative}`);
    const textClass = read(join(javaRoot, 'com/eyecrasher/lazodiscs/text/LazoDiscsText.java'));
    const keys = new Set([...translations.matchAll(/^([\w.]+)\s*=/gm)].map(match => match[1]));
    for (const match of textClass.matchAll(/(?:text|component)\(\s*"([^"]+)"/g)) {
      check(keys.has(match[1]), `Missing server translation ${match[1]} in ${variant.relative}`);
    }
  } else {
    const obsoleteRecipes = join(resources, 'data/lazoboombox/recipes');
    check(!existsSync(obsoleteRecipes) || walk(obsoleteRecipes).length === 0,
      'Obsolete plural recipe files');
    const recipe = readJson(join(resources, 'data/lazoboombox/recipe/boombox.json'));
    // Minecraft 1.21.2 changed recipe ingredients from item objects to IDs.
    const modern = Number(variant.minecraft.split('.')[2]) >= 2;
    check(Object.values(recipe.key).every(value => modern ? typeof value === 'string' : typeof value.item === 'string'),
      `Wrong recipe ingredient format: ${variant.relative}`);
    const loot = readJson(join(resources, 'data/lazoboombox/loot_table/blocks/boombox.json'));
    const operations = loot.pools[0].entries[0].functions.flatMap(fn => fn.ops ?? []);
    check(operations.some(op => op.source === 'disc' && op.target === 'lazoboombox_boombox.disc'),
      `Disc must survive ordinary block loot: ${variant.relative}`);
    check(operations.some(op => op.source === 'owner' && op.target === 'lazoboombox_boombox.owner'),
      `Owner must survive ordinary block loot: ${variant.relative}`);
  }
}
check(read(join(root, 'LICENSE')).includes('END OF TERMS AND CONDITIONS'), 'Missing full project license');
console.log(`Passed ${checks} project/resource checks across ${variants().length} targets; scanned ${javaFiles} Java and ${jsonFiles} JSON files.`);
