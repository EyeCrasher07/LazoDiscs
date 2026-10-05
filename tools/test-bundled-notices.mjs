import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { spawnSync } from 'node:child_process';
import {
  cpSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync,
} from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

// Run the actual Shadow transformer and final-archive verification in isolated
// Java-only fixtures. Neither case edits source ports or their build outputs.
const root = dirname(dirname(fileURLToPath(import.meta.url)));
const temporary = mkdtempSync(join(tmpdir(), 'lazo-notices-test-'));
const wrapper = join(root, 'fabric', '1.21.1', process.platform === 'win32' ? 'gradlew.bat' : 'gradlew');
const jar = process.env.JAVA_HOME ? join(process.env.JAVA_HOME, 'bin', 'jar') : 'jar';
const script = readFileSync(join(root, 'gradle', 'third-party-notices.gradle'), 'utf8');

function execute(command, args, options = {}) {
  const result = spawnSync(command, args, {
    encoding: 'utf8', timeout: 180_000, maxBuffer: 8 * 1024 * 1024, ...options,
  });
  if (result.error) throw result.error;
  return { ...result, output: (result.stdout ?? '') + (result.stderr ?? '') };
}

function fixture(name, packagingScript) {
  const directory = join(temporary, name);
  const project = join(directory, 'fabric', '1.21.1');
  mkdirSync(project, { recursive: true });
  mkdirSync(join(directory, 'gradle'), { recursive: true });
  cpSync(join(root, 'third-party'), join(directory, 'third-party'), { recursive: true });
  writeFileSync(join(directory, 'gradle', 'third-party-notices.gradle'), packagingScript);
  writeFileSync(join(project, 'settings.gradle'), "rootProject.name = 'notice-fixture'\n");
  writeFileSync(join(project, 'build.gradle'), `
plugins {
    id 'java'
    id 'com.gradleup.shadow' version '8.3.11'
}
configurations { shade }
dependencies { shade files('first.jar', 'second.jar') }
shadowJar {
    configurations = [project.configurations.shade]
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
apply from: '../../gradle/third-party-notices.gradle'
`);
  for (const dependency of ['first', 'second']) {
    const contents = join(directory, dependency);
    mkdirSync(join(contents, 'META-INF'), { recursive: true });
    for (const filename of ['LICENSE', 'NOTICE', 'LICENSE.txt', 'NOTICE.txt', 'Unique-LICENSE']) {
      const entry = filename === 'Unique-LICENSE' ? dependency + '-' + filename : filename;
      writeFileSync(join(contents, 'META-INF', entry), `Complete ${dependency} ${filename} text.\n`);
    }
    const result = execute(jar, ['--create', '--no-manifest', '--file',
      join(project, dependency + '.jar'), '-C', contents, '.']);
    assert.equal(result.status, 0, result.output);
  }
  const arguments_ = ['--project-dir', project, 'check', '--no-daemon', '--console=plain', '--max-workers=2'];
  return process.platform === 'win32' ? execute('cmd.exe', ['/d', '/c', wrapper, ...arguments_]) :
    execute(wrapper, arguments_);
}

try {
  const provenance = JSON.parse(readFileSync(join(root, 'third-party', 'PROVENANCE.json'), 'utf8'));
  for (const entry of provenance.files) {
    const hash = createHash('sha256').update(readFileSync(join(root, 'third-party', entry.file))).digest('hex');
    assert.equal(hash, entry.sha256, 'Changed supplemental text: ' + entry.file);
  }
  const positive = fixture('preserves-all-notices', script);
  assert.equal(positive.status, 0, positive.output);
  assert.match(positive.output, /Verified 16 complete upstream legal resources/);
  console.log('PASS: actual Shadow merges all four colliding paths and preserves unique/supplemental files.');

  const originalExclusion = script
    .replace(/    filesMatching\(mergedLegalPaths\) \{\n        duplicatesStrategy = DuplicatesStrategy.INCLUDE\n    \}\n/, '')
    .replace(/    mergedLegalPaths.each \{ append it \}\n/, '');
  assert.notEqual(originalExclusion, script);
  const negative = fixture('rejects-original-exclusion', originalExclusion);
  assert.notEqual(negative.status, 0, 'Original duplicate exclusion unexpectedly passed the gate');
  assert.match(negative.output, /Missing or incomplete META-INF\/(?:LICENSE|NOTICE)/);
  console.log('PASS: the archive gate rejects the original EXCLUDE-only packaging regression.');
  console.log('Passed 4 provenance checks and 2 real Shadow packaging regression cases.');
} finally {
  rmSync(temporary, { recursive: true, force: true });
}
