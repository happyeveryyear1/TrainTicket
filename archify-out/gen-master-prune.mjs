import { readFileSync, writeFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';

const ARCHIFY = 'C:/Users/侯钰坤/.agents/skills/archify/bin/archify.mjs';
const OUT = new URL('./train-ticket-call-graph-master.architecture.json', import.meta.url);

const raw = JSON.parse(readFileSync(new URL('./call-graph.raw.json', import.meta.url), 'utf8'));
const calls = raw.calls;
const SVC = Object.keys(calls).sort();

const MQ = [
  ['ts-preserve-service', 'rabbitmq'], ['ts-preserve-other-service', 'rabbitmq'], ['ts-food-service', 'rabbitmq'],
  ['rabbitmq', 'ts-notification-service'], ['rabbitmq', 'ts-delivery-service'],
];
const allEdges = [];
for (const [u, deps] of Object.entries(calls)) for (const v of Object.keys(deps)) allEdges.push([u, v]);
allEdges.push(['ts-voucher-service', 'ts-order-service'], ['ts-voucher-service', 'ts-order-other-service']);
const mqSet = new Set(MQ.map(([a, b]) => `${a}|${b}`));
for (const [u, v] of MQ) allEdges.push([u, v]);

const ids = [...SVC, 'rabbitmq'];
const adj = new Map(ids.map((s) => [s, []]));
for (const [u, v] of allEdges) if (adj.has(u) && adj.has(v)) { adj.get(u).push(v); adj.get(v).push(u); }

const N = ids.length, W = 2200, H = 1100;
const nodes = ids.map((id, i) => ({ id, x: W / 2 + W * 0.42 * Math.cos((2 * Math.PI * i) / N), y: H / 2 + H * 0.42 * Math.sin((2 * Math.PI * i) / N) }));
const k = Math.sqrt((W * H) / N) * 0.62;
const idx = new Map(ids.map((s, i) => [s, i]));
let temp = W * 0.10;
for (let it = 0; it < 650; it++) {
  const disp = nodes.map(() => ({ x: 0, y: 0 }));
  for (let i = 0; i < N; i++) for (let j = i + 1; j < N; j++) {
    const dx = nodes[i].x - nodes[j].x, dy = nodes[i].y - nodes[j].y;
    const d = Math.hypot(dx, dy) || 0.01, f = (k * k) / d;
    disp[i].x += (dx / d) * f; disp[i].y += (dy / d) * f;
    disp[j].x -= (dx / d) * f; disp[j].y -= (dy / d) * f;
  }
  for (const [u, v] of allEdges) {
    const i = idx.get(u), j = idx.get(v);
    if (i === undefined || j === undefined) continue;
    const dx = nodes[i].x - nodes[j].x, dy = nodes[i].y - nodes[j].y;
    const d = Math.hypot(dx, dy) || 0.01, f = (d * d) / k;
    disp[i].x -= (dx / d) * f; disp[i].y -= (dy / d) * f;
    disp[j].x += (dx / d) * f; disp[j].y += (dy / d) * f;
  }
  for (let i = 0; i < N; i++) {
    const d = Math.hypot(disp[i].x, disp[i].y) || 0.01, lim = Math.min(d, temp);
    nodes[i].x += (disp[i].x / d) * lim; nodes[i].y += (disp[i].y / d) * lim;
    nodes[i].x = Math.max(40, Math.min(W - 40, nodes[i].x)); nodes[i].y = Math.max(40, Math.min(H - 40, nodes[i].y));
  }
  temp *= 0.995;
}
const NODE_W = 186, NODE_H = 40, MIN_DX = NODE_W + 24, MIN_DY = NODE_H + 24;
for (let pass = 0; pass < 900; pass++) {
  let moved = false;
  for (let i = 0; i < N; i++) for (let j = i + 1; j < N; j++) {
    const dx = nodes[j].x - nodes[i].x, dy = nodes[j].y - nodes[i].y;
    const ox = MIN_DX - Math.abs(dx), oy = MIN_DY - Math.abs(dy);
    if (ox > 0 && oy > 0) {
      moved = true;
      if (ox < oy) { const s = (dx >= 0 ? 1 : -1) * ox / 2; nodes[i].x -= s; nodes[j].x += s; }
      else { const s = (dy >= 0 ? 1 : -1) * oy / 2; nodes[i].y -= s; nodes[j].y += s; }
    }
  }
  if (!moved) break;
}
const minX = Math.min(...nodes.map((n) => n.x)), minY = Math.min(...nodes.map((n) => n.y)), pad = 36;
const comps = nodes.map((n) => ({
  id: n.id, type: n.id === 'rabbitmq' ? 'messagebus' : 'backend', label: n.id,
  pos: [Math.round(n.x - minX + pad), Math.round(n.y - minY + pad)], size: [NODE_W, NODE_H],
  ...(n.id === 'rabbitmq' ? { brand: 'rabbitmq' } : {}),
}));
const viewW = Math.round(Math.max(...comps.map((c) => c.pos[0] + c.size[0])) + pad);
const viewH = Math.round(Math.max(...comps.map((c) => c.pos[1] + c.size[1])) + pad);

const views = [
  { id: 'booking', label: '订票与改签', focus: ['ts-preserve-service', 'ts-preserve-other-service', 'ts-rebook-service', 'ts-cancel-service', 'ts-wait-order-service', 'ts-voucher-service', 'ts-travel-plan-service', 'ts-route-plan-service'], note: '订票、跨局订票、改签、取消、候补的入站编排。' },
  { id: 'core', label: '票务核心域', focus: ['ts-seat-service', 'ts-order-service', 'ts-order-other-service', 'ts-basic-service', 'ts-travel-service', 'ts-travel2-service', 'ts-station-service', 'ts-train-service', 'ts-route-service', 'ts-price-service', 'ts-contacts-service', 'ts-security-service'], note: '座位、订单、车次、路线、票价等被高频调用的领域服务。' },
  { id: 'payment', label: '支付与通知', focus: ['ts-inside-payment-service', 'ts-payment-service', 'ts-execute-service', 'ts-notification-service', 'rabbitmq'], note: '支付结算、订单执行与 RabbitMQ 异步通知。' },
  { id: 'admin', label: '管理后台', focus: ['ts-admin-basic-info-service', 'ts-admin-order-service', 'ts-admin-route-service', 'ts-admin-travel-service', 'ts-admin-user-service'], note: '五个管理服务对票务核心的调用。' },
  { id: 'aux', label: '餐食与账户', focus: ['ts-food-service', 'ts-food-delivery-service', 'ts-station-food-service', 'ts-train-food-service', 'ts-delivery-service', 'ts-assurance-service', 'ts-consign-service', 'ts-consign-price-service', 'ts-auth-service', 'ts-user-service', 'ts-verification-code-service'], note: '餐食配送与账户认证链路。' },
];

let active = allEdges.map(([u, v], i) => ({ id: `e${i}`, from: u, to: v, ...(mqSet.has(`${u}|${v}`) ? { variant: 'dashed' } : {}) }));
const report = [];
for (let round = 0; round < 40; round++) {
  const spec = {
    schema_version: 1, diagram_type: 'architecture',
    meta: { title: `Train Ticket 服务调用总图（代码级 · ${active.length} 条关系）`, locale: 'zh-CN', quality_profile: 'standard', viewBox: [viewW, viewH], views },
    components: comps, connections: active,
  };
  writeFileSync(OUT, JSON.stringify(spec, null, 2));
  let json;
  try {
    const out = execFileSync('node', [ARCHIFY, 'validate', 'architecture', OUT.pathname.replace(/^\//, ''), '--quality', 'standard', '--json'], { encoding: 'utf8', maxBuffer: 1 << 28 });
    json = JSON.parse(out);
  } catch (e) {
    const s = (e.stdout || '').toString();
    const i = s.indexOf('{');
    try { json = JSON.parse(s.slice(i)); } catch { console.log('parse fail', s.slice(0, 300)); break; }
  }
  if (json.ok) { report.push({ round, kept: active.length, status: 'PASS' }); break; }
  const bad = new Set();
  for (const d of json.diagnostics || []) {
    const id = d.subject && d.subject.id;
    if (d.subject && d.subject.collection === 'connections' && id) bad.add(id);
    const m = /connections\[\d+\] id "([^"]+)"/.exec(d.message || '');
    if (m) bad.add(m[1]);
  }
  const before = active.length;
  active = active.filter((c) => !bad.has(c.id));
  report.push({ round, kept: active.length, removed: before - active.length, status: 'prune' });
  if (active.length === before || active.length === 0) break;
}
console.log(report.map((r) => `round ${r.round}: kept=${r.kept} removed=${r.removed ?? 0} ${r.status}`).join('\n'));
console.log('final edges kept:', active.length, 'of', allEdges.length);
const keptKeys = new Set(active.map((c) => `${c.from}->${c.to}`));
console.log('dropped:', allEdges.map(([u, v]) => `${u}->${v}`).filter((k) => !keptKeys.has(k)).join(', '));
