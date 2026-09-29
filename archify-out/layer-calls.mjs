import { readFileSync } from 'node:fs';

const raw = JSON.parse(readFileSync(new URL('./call-graph.raw.json', import.meta.url), 'utf8'));
const calls = raw.calls;

const SERVICES = Object.keys(calls).concat(['ts-voucher-service']).filter((v, i, a) => a.indexOf(v) === i).sort();

const edges = [];
for (const [u, deps] of Object.entries(calls)) {
  for (const v of Object.keys(deps)) edges.push([u, v]);
}
edges.push(['ts-voucher-service', 'ts-order-service'], ['ts-voucher-service', 'ts-order-other-service']);

const adj = new Map(SERVICES.map((s) => [s, []]));
for (const [u, v] of edges) if (adj.has(u) && adj.has(v)) adj.get(u).push(v);

const layer = new Map();
const state = new Map();
function depth(n) {
  if (layer.has(n)) return layer.get(n);
  if (state.get(n) === 'visiting') return 0;
  state.set(n, 'visiting');
  let d = 0;
  for (const m of adj.get(n)) d = Math.max(d, depth(m) + 1);
  state.set(n, 'done');
  layer.set(n, d);
  return d;
}
for (const s of SERVICES) depth(s);

const maxLayer = Math.max(...[...layer.values()]);
const byLayer = [];
for (let i = 0; i <= maxLayer; i++) byLayer.push([]);
for (const s of SERVICES) byLayer[layer.get(s)].push(s);

console.log('services:', SERVICES.length, 'edges:', edges.length, 'layers:', maxLayer + 1);
byLayer.forEach((arr, i) => console.log(`L${i} (${arr.length}):`, arr.join(', ')));

const inDeg = new Map(SERVICES.map((s) => [s, 0]));
const outDeg = new Map(SERVICES.map((s) => [s, 0]));
for (const [u, v] of edges) { outDeg.set(u, outDeg.get(u) + 1); inDeg.set(v, inDeg.get(v) + 1); }
console.log('--- hubs by in-degree ---');
[...inDeg.entries()].sort((a, b) => b[1] - a[1]).slice(0, 12).forEach(([s, d]) => console.log(`${s.padEnd(32)} in=${d} out=${outDeg.get(s)}`));
