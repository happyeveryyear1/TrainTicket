import { readdirSync, readFileSync, existsSync, writeFileSync } from 'node:fs';
import { join, extname } from 'node:path';

const ROOT = 'C:/train-ticket-master';
const serviceDirs = readdirSync(ROOT)
  .filter((n) => n.startsWith('ts-'))
  .filter((n) => existsSync(join(ROOT, n, 'Dockerfile')))
  .sort();

const CODE_EXT = new Set(['.java', '.js', '.ts', '.py', '.go', '.kt']);
const SKIP = new Set(['target', 'node_modules', '.git', 'dist', 'build', '.idea']);

function walk(dir, out = []) {
  let entries;
  try { entries = readdirSync(dir, { withFileTypes: true }); } catch { return out; }
  for (const e of entries) {
    if (SKIP.has(e.name)) continue;
    const p = join(dir, e.name);
    if (e.isDirectory()) walk(p, out);
    else if (CODE_EXT.has(extname(e.name))) out.push(p);
  }
  return out;
}

const getUrlRe = /getServiceUrl\(\s*"([^"]+)"\s*\)/g;
const svcToken = /\bts-[a-z0-9-]+-service\b/g;
const endpointRe = /@(Get|Post|Put|Delete|Patch)Mapping\s*\(([^)]*)\)/g;
const classReqRe = /@RequestMapping\s*\(([^)]*)\)/g;
const pathRe = /["'](\/api\/v1\/[^"']+)["']/g;

const calls = {};
const mentions = {};
const endpoints = {};
const urlPaths = {};
const evidence = {};

for (const svc of serviceDirs) {
  const files = walk(join(ROOT, svc));
  const c = {}; const m = {}; const eps = new Set(); const paths = new Set();
  for (const f of files) {
    let txt; try { txt = readFileSync(f, 'utf8'); } catch { continue; }
    let mm;
    while ((mm = getUrlRe.exec(txt))) {
      const dep = mm[1];
      if (dep === svc) continue;
      c[dep] = (c[dep] || 0) + 1;
      const rel = f.replace(ROOT + '/', '');
      const key = `${svc}->${dep}`;
      (evidence[key] ||= []).length < 3 && evidence[key].push(rel);
    }
    while ((mm = svcToken.exec(txt))) {
      const dep = mm[0];
      if (dep === svc) continue;
      m[dep] = (m[dep] || 0) + 1;
    }
    while ((mm = endpointRe.exec(txt))) {
      const q = mm[2].match(/["']([^"']+)["']/);
      if (q) eps.add(`${mm[1].toUpperCase()} ${q[1]}`);
    }
    while ((mm = classReqRe.exec(txt))) {
      const q = mm[1].match(/["']([^"']+)["']/);
      if (q) eps.add(`@ ${q[1]}`);
    }
    while ((mm = pathRe.exec(txt))) paths.add(mm[1]);
  }
  calls[svc] = Object.fromEntries(Object.entries(c).sort((a, b) => b[1] - a[1]));
  mentions[svc] = Object.fromEntries(Object.entries(m).sort((a, b) => b[1] - a[1]));
  endpoints[svc] = [...eps].sort();
  urlPaths[svc] = [...paths].sort();
}

const out = { root: ROOT, serviceCount: serviceDirs.length, calls, mentions, endpoints, urlPaths, evidence };
writeFileSync(new URL('./call-graph.raw.json', import.meta.url), JSON.stringify(out, null, 2));

const withCalls = serviceDirs.filter((s) => Object.keys(calls[s]).length);
console.log('services:', serviceDirs.length, '| services with outbound calls:', withCalls.length);
console.log('--- true outbound calls (getServiceUrl) ---');
for (const s of serviceDirs) {
  const d = calls[s];
  if (!Object.keys(d).length) continue;
  console.log(`${s.padEnd(32)} -> ${Object.entries(d).map(([k, v]) => `${k}(${v})`).join(', ')}`);
}
console.log('--- call-compatible mention-only services (no getServiceUrl) ---');
for (const s of serviceDirs) {
  if (Object.keys(calls[s]).length) continue;
  const keys = Object.keys(mentions[s]);
  if (keys.length) console.log(`${s.padEnd(32)} mentions: ${keys.join(', ')}`);
}
