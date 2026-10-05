import { createHash } from 'node:crypto';
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';
import { root, variants, walk } from './project-utils.mjs';

const mode = process.argv[2] ?? '--check';
if (!['--check', '--write'].includes(mode) || process.argv.length > 3) {
  console.error('Usage: node tools/format-java.mjs [--check|--write]');
  process.exit(2);
}

const version = '1.37.0';
const expectedSha256 = '834b2a0c38cb774953322a84b5ca3f2f40dd3156650b3cd44d3b744345962f7a';
const cache = join(root, '.cache', 'tools');
const jar = join(cache, `google-java-format-${version}.jar`);
mkdirSync(cache, { recursive: true });

if (!existsSync(jar)) {
  const url = `https://github.com/google/google-java-format/releases/download/v${version}/google-java-format-${version}-all-deps.jar`;
  const response = await fetch(url, { signal: AbortSignal.timeout(60_000) });
  if (!response.ok) throw new Error(`Formatter download failed: HTTP ${response.status}`);
  const bytes = Buffer.from(await response.arrayBuffer());
  if (createHash('sha256').update(bytes).digest('hex') !== expectedSha256) {
    throw new Error('Formatter download SHA-256 mismatch');
  }
  writeFileSync(jar, bytes);
}
if (createHash('sha256').update(readFileSync(jar)).digest('hex') !== expectedSha256) {
  throw new Error('Cached formatter SHA-256 mismatch');
}

const java = process.env.JAVA_HOME
  ? join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java')
  : 'java';
const files = variants().flatMap(v => walk(join(v.directory, 'src', 'main', 'java')))
  .filter(path => path.endsWith('.java')).sort();
let failed = false;
for (let offset = 0; offset < files.length; offset += 64) {
  const batch = files.slice(offset, offset + 64);
  const fingerprint = () => createHash('sha256')
    .update(batch.map(path => createHash('sha256').update(readFileSync(path)).digest('hex')).join(''))
    .digest('hex');
  // AOSP long-string wrapping can need a second pass to settle indentation.
  // Stop only at a fixed point so --write leaves a tree that passes --check.
  for (let pass = 0; pass < (mode === '--write' ? 4 : 1); pass++) {
    const before = mode === '--write' ? fingerprint() : undefined;
    const args = ['-jar', jar, '--aosp',
      ...(mode === '--write' ? ['--replace'] : ['--dry-run', '--set-exit-if-changed']),
      ...batch];
    const result = spawnSync(java, args, { stdio: 'inherit' });
    if (result.error) throw result.error;
    if (result.status !== 0) {
      failed = true;
      break;
    }
    if (mode === '--check' || fingerprint() === before) break;
    if (pass === 3) {
      console.error('Formatter did not reach a stable result after four passes.');
      failed = true;
    }
  }
}
console.log(`${mode === '--write' ? 'Formatted' : 'Checked'} ${files.length} Java files (AOSP, Java 21).`);
process.exitCode = failed ? 1 : 0;
