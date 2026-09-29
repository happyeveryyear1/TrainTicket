import { readFileSync, writeFileSync } from 'node:fs';

const raw = JSON.parse(readFileSync(new URL('./call-graph.raw.json', import.meta.url), 'utf8'));
const calls = raw.calls;

const MQ = [
  ['ts-preserve-service', 'rabbitmq'], ['ts-preserve-other-service', 'rabbitmq'], ['ts-food-service', 'rabbitmq'],
  ['rabbitmq', 'ts-notification-service'], ['rabbitmq', 'ts-delivery-service'],
];
const allEdges = [];
for (const [u, deps] of Object.entries(calls)) for (const v of Object.keys(deps)) allEdges.push([u, v, 'call']);
for (const v of ['ts-order-service', 'ts-order-other-service']) allEdges.push(['ts-voucher-service', v, 'call']);
for (const [u, v] of MQ) allEdges.push([u, v, 'mq']);

const NODE_W = 190;
const NODE_H = 34;
const PITCH = 46;
const TOP = 92;

function buildBipartite(name, title, callers) {
  const set = new Set(callers);
  const edges = allEdges.filter(([u]) => set.has(u));
  const targets = [...new Set(edges.map((e) => e[1]))].filter((t) => !set.has(t)).sort();
  const overlap = [...new Set(edges.map((e) => e[1]))].filter((t) => set.has(t));
  const left = [...callers].sort();
  const rows = Math.max(left.length, targets.length);
  const viewH = Math.max(240, TOP + (rows - 1) * PITCH + NODE_H + 40);
  const viewW = Math.max(940, Math.round(viewH * 2.5));
  const LX = 48;
  const RX = viewW - NODE_W - 48;

  const comps = [];
  const leftStart = left.length >= targets.length ? TOP : TOP + Math.round((targets.length - left.length) * PITCH / 2);
  const rightStart = targets.length >= left.length ? TOP : TOP + Math.round((left.length - targets.length) * PITCH / 2);
  let y = leftStart;
  for (const id of left) { comps.push({ id, type: 'backend', label: id, pos: [LX, y], size: [NODE_W, NODE_H] }); y += PITCH; }
  y = rightStart;
  for (const id of targets) { comps.push({ id, type: id === 'rabbitmq' ? 'messagebus' : 'backend', label: id, pos: [RX, y], size: [NODE_W, NODE_H], ...(id === 'rabbitmq' ? { brand: 'rabbitmq' } : {}) }); y += PITCH; }

  const conns = edges.map(([u, v, k], i) => ({ id: `e${i}`, from: u, to: v, fromSide: 'right', toSide: 'left', ...(k === 'mq' ? { variant: 'dashed' } : {}) }));
  const spec = {
    schema_version: 1, diagram_type: 'architecture',
    meta: { title: `${title}（左=调用方 · 右=被调方 · 实线=同步 · 虚线=异步）`, locale: 'zh-CN', quality_profile: 'standard', viewBox: [viewW, viewH] },
    components: comps,
    connections: conns,
  };
  const file = `train-ticket-calls-${name}.architecture.json`;
  writeFileSync(new URL('./' + file, import.meta.url), JSON.stringify(spec, null, 2));
  console.log(`${name}: callers=${left.length} targets=${targets.length} edges=${conns.length} overlap=[${overlap.join(',')}] viewBox=${viewW}x${viewH}`);
  return file;
}

const groups = {
  'booking-preserve': ['ts-preserve-service', 'ts-preserve-other-service'],
  'after-sales': ['ts-rebook-service', 'ts-cancel-service', 'ts-voucher-service'],
  admin: ['ts-admin-basic-info-service', 'ts-admin-order-service', 'ts-admin-route-service', 'ts-admin-travel-service', 'ts-admin-user-service'],
  'domain-core': ['ts-travel-service', 'ts-travel2-service', 'ts-wait-order-service'],
  dining: ['ts-food-service', 'ts-food-delivery-service', 'ts-consign-service'],
  'seat-basic': ['ts-seat-service', 'ts-basic-service'],
  'payment-security': ['ts-inside-payment-service', 'ts-security-service', 'ts-execute-service'],
  planning: ['ts-travel-plan-service'],
  'route-planning': ['ts-route-plan-service'],
  'order-store': ['ts-order-service', 'ts-order-other-service'],
  'identity-user': ['ts-user-service'],
  'identity-auth': ['ts-auth-service'],
};

for (const [k, v] of Object.entries(groups)) buildBipartite(k, `Train Ticket 服务调用关系 · ${k}`, v);
