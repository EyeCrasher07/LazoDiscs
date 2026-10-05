import { mkdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { spawn } from 'node:child_process';
import { root, release, variants } from './project-utils.mjs';

const arguments_ = process.argv.slice(2);
if (arguments_.some(arg => !/^--(?:loader|minecraft)=[^=]+$/.test(arg)) ||
    new Set(arguments_.map(arg => arg.split('=')[0])).size !== arguments_.length) {
  console.error('Usage: node tools/build-all.mjs [--loader=fabric|neoforge] [--minecraft=1.21.1]');
  process.exit(2);
}
const loader = arguments_.find(arg => arg.startsWith('--loader='))?.slice('--loader='.length);
const minecraft = arguments_.find(arg => arg.startsWith('--minecraft='))?.slice('--minecraft='.length);
if ((loader && !release.loaders.includes(loader)) ||
    (minecraft && !release.minecraft_versions.includes(minecraft))) {
  throw new Error('Unknown loader or Minecraft version');
}
const selected = variants().filter(v =>
  (!loader || v.loader === loader) && (!minecraft || v.minecraft === minecraft));
const results = [];
for (const variant of selected) {
  console.log(`Building ${release.mod_id} ${variant.relative}`);
  const started = Date.now();
  const status = await new Promise((resolve, reject) => {
    const args = ['clean', 'build', '--no-daemon', '--console=plain', '--max-workers=2'];
    const child = process.platform === 'win32'
      ? spawn('cmd.exe', ['/d', '/c', 'gradlew.bat', ...args], { cwd: variant.directory, stdio: 'inherit' })
      : spawn('./gradlew', args, { cwd: variant.directory, stdio: 'inherit' });
    child.once('error', reject);
    child.once('exit', code => resolve(code ?? 1));
  });
  results.push({ project: variant.relative, version: variant.version, status,
    seconds: Math.round((Date.now() - started) / 1000) });
}
const output = join(root, 'dist');
mkdirSync(output, { recursive: true });
const suffix = [loader, minecraft].filter(Boolean).join('-') || 'all';
writeFileSync(join(output, `build-results-${suffix}.json`), JSON.stringify(results, null, 2) + '\n');
const failed = results.filter(result => result.status !== 0);
console.log(`Build result: ${results.length - failed.length}/${results.length} passed.`);
process.exitCode = failed.length ? 1 : 0;
