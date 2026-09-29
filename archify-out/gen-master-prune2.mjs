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

const bands = [
  { label: '入口与身份', members: ['ts-gateway-service', 'ts-ui-dashboard', 'ts-auth-service', 'ts-user-service', 'ts-verification-code-service', 'ts-security-service'] },
  { label: '订票与出行（核心）', members: ['ts-preserve-service', 'ts-preserve-other-service', 'ts-order-service', 'ts-order-other-service', 'ts-seat-service', 'ts-basic-service', 'ts-travel-service', 'ts-travel2-service', 'ts-travel-plan-service', 'ts-route-plan-service', 'ts-route-service', 'ts-price-service', 'ts-train-service', 'ts-station-service', 'ts-contacts-service'] },
  { label: '支付与订单生命周期', members: ['ts-inside-payment-service', 'ts-payment-service', 'ts-execute-service', 'ts-cancel-service', 'ts-rebook-service', 'ts-wait-order-service'] },
  { label: '辅助与通知', members: ['ts-assurance-service', 'ts-food-service', 'ts-station-food-service', 'ts-train-food-service', 'ts-food-delivery-service', 'ts-delivery-service', 'ts-consign-service', 'ts-consign-price-service', 'ts-ticket-office-service', 'ts-news-service', 'ts-config-service', 'ts-avatar-service', 'ts-voucher-service', 'ts-notification-service'] },
  { label: '管理后台', members: ['ts-admin-basic-info-service', 'ts-admin-order-service', 'ts-admin-route-service', 'ts-admin-travel-service', 'ts-admin-user-service'] },
  { label: '平台', members: ['rabbitmq'] },
];

const NODE_W = 186, NODE_H = 40, PITCH_X = 216, PITCH_Y = 78, WRAP = 8, LEFT = 44, TOP = 100, BAND_GAP = 32;
const comps = [];
let y0 = TOP;
for (const b of bands) {
  let y = y0;
  for (let i = 0; i < b.members.length; i += WRAP) {
    const row = b.members.slice(i, i + WRAP);
    row.forEach((id, c) => comps.push({
      id, type: id === 'rabbitmq' ? 'messagebus' : 'backend', label: id,
      pos: [LEFT + c * PITCH_X, y], size: [NODE_W, NODE_H], ...(id === 'rabbitmq' ? { brand: 'rabbitmq' } : {}),
    }));
    y += PITCH_Y;
  }
  y0 = y - PITCH_Y + NODE_H + BAND_GAP;
}
const viewW = LEFT + WRAP * PITCH_X + 20;
const viewH = y0 - BAND_GAP + 40;

const views = [
  { id: 'booking', label: '订票与改签', focus: ['ts-preserve-service', 'ts-preserve-other-service', 'ts-rebook-service', 'ts-cancel-service', 'ts-wait-order-service', 'ts-voucher-service', 'ts-travel-plan-service', 'ts-route-plan-service'], note: '订票、跨局订票、改签、取消、候补的入站编排。' },
  { id: 'core', label: '票务核心域', focus: ['ts-seat-service', 'ts-order-service', 'ts-order-other-service', 'ts-basic-service', 'ts-travel-service', 'ts-travel2-service', 'ts-station-service', 'ts-train-service', 'ts-route-service', 'ts-price-service', 'ts-contacts-service', 'ts-security-service'], note: '座位、订单、车次、路线、票价等被高频调用的领域服务。' },
  { id: 'payment', label: '支付与通知', focus: ['ts-inside-payment-service', 'ts-payment-service', 'ts-execute-service', 'ts-notification-service', 'rabbitmq'], note: '支付结算、订单执行与 RabbitMQ 异步通知。' },
  { id: 'admin', label: '管理后台', focus: ['ts-admin-basic-info-service', 'ts-admin-order-service', 'ts-admin-route-service', 'ts-admin-travel-service', 'ts-admin-user-service'], note: '五个管理服务对票务核心的调用。' },
  { id: 'aux', label: '餐食与账户', focus: ['ts-food-service', 'ts-food-delivery-service', 'ts-station-food-service', 'ts-train-food-service', 'ts-delivery-service', 'ts-auth-service', 'ts-user-service', 'ts-verification-code-service', 'ts-consign-service'], note: '餐食配送与账户认证链路。' },
];

let active = allEdges.map(([u, v], i) => ({ id: `e${i}`, from: u, to: v, ...(mqSet.has(`${u}|${v}`) ? { variant: 'dashed' } : {}) }));
const allList = allEdges.map(([u, v]) => `${u}->${v}`);
const log = [];
for (let round = 0; round < 60; round++) {
  const spec = {
    schema_version: 1, diagram_type: 'architecture',
    meta: { title: `Train Ticket 服务调用总览 · 章节导航（可渲染子集 ${active.length}/97 条）`, locale: 'zh-CN', quality_profile: 'standard', viewBox: [viewW, viewH], views },
    components: comps, connections: active,
  };
  writeFileSync(OUT, JSON.stringify(spec, null, 2));
  let json;
  try {
    const out = execFileSync('node', [ARCHIFY, 'validate', 'architecture', OUT.pathname.replace(/^\//, ''), '--quality', 'standard', '--json'], { encoding: 'utf8', maxBuffer: 1 << 28 });
    json = JSON.parse(out);
  } catch (e) {
    const s = (e.stdout || '').toString(); const i = s.indexOf('{');
    json = JSON.parse(s.slice(i));
  }
  if (json.ok) { log.push(`round ${round}: PASS kept=${active.length}`); break; }
  const bad = new Set();
  for (const d of json.diagnostics || []) {
    if (d.subject && d.subject.collection === 'connections' && d.subject.id) bad.add(d.subject.id);
    const m = /connections\[\d+\] id "([^"]+)"/.exec(d.message || '');
    if (m) bad.add(m[1]);
  }
  const before = active.length;
  active = active.filter((c) => !bad.has(c.id));
  log.push(`round ${round}: pruned ${before - active.length}, kept=${active.length}`);
  if (active.length === before) { log.push('no progress; stopping'); break; }
}
console.log(log.join('\n'));
const kept = new Set(active.map((c) => `${c.from}->${c.to}`));
console.log('KEPT:', active.length, '/', allEdges.length);
console.log('kept list:', [...kept].join(', '));
