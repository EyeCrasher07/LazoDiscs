import { readFileSync, readdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

export const root = dirname(dirname(fileURLToPath(import.meta.url)));
export const release = JSON.parse(readFileSync(join(root, 'release.json'), 'utf8'));

export function variants() {
  return release.loaders.flatMap(loader =>
    release.minecraft_versions.map(minecraft => ({
      loader,
      minecraft,
      relative: `${loader}/${minecraft}`,
      directory: join(root, loader, minecraft),
      version: `${release.version}+${minecraft}`,
      filename: `${release.mod_id}-${loader}-${release.version}+${minecraft}.jar`,
    })),
  );
}

export function walk(directory) {
  return readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    const path = join(directory, entry.name);
    return entry.isDirectory() ? walk(path) : [path];
  });
}

export function read(path) {
  return readFileSync(path, 'utf8');
}

export function readJson(path) {
  return JSON.parse(read(path));
}
