import { readFileSync, writeFileSync } from 'node:fs';

const raw = JSON.parse(readFileSync(new URL('./call-graph.raw.json', import.meta.url), 'utf8'));
const calls = raw.calls;

const SVC = Object.keys(calls);
const edges = [];
for (const [u, deps] of Object.entries(calls)) for (const v of Object.keys(deps)) edges.push([u, v, 'call']);
for (const v of ['ts-order-service', 'ts-order-other-service']) edges.push(['ts-voucher-service', v, 'call']);
for (const [u, v] of [['ts-preserve-service', 'rabbitmq'], ['ts-preserve-other-service', 'rabbitmq'], ['ts-food-service', 'rabbitmq']]) edges.push([u, v, 'mq']);
for (const [u, v] of [['rabbitmq', 'ts-notification-service'], ['rabbitmq', 'ts-delivery-service']]) edges.push([u, v, 'mq']);

const callers = [...new Set(edges.map((e) => e[0]))].sort();
const callees = [...new Set(edges.map((e) => e[1]))].sort();

const NODE_W = 194;
const NODE_H = 30;
const PITCH_Y = 34;
const LEFT_X = 60;
const RIGHT_X = 760;
const TOP = 96;

const comps = [];
function col(list, x) {
  const ids = [];
  let y = TOP;
  for (const id of list) {
    comps.push({ id, type: id === 'rabbitmq' ? 'messagebus' : 'backend', label: id, pos: [x, y], size: [NODE_W, NODE_H], ...(id === 'rabbitmq' ? { brand: 'rabbitmq' } : {}) });
    ids.push(id);
    y += PITCH_Y;
  }
  return { ids, bottom: y - PITCH_Y + NODE_H };
}
const L = col(callers, LEFT_X);
const R = col(callees, RIGHT_X);

const connections = [];
let i = 0;
for (const [u, v, kind] of edges) {
  connections.push({ id: `e${i++}`, from: u, to: v, fromSide: 'right', toSide: 'left', ...(kind === 'mq' ? { variant: 'dashed' } : {}) });
}

const viewW = RIGHT_X + NODE_W + 40;
const viewH = Math.max(L.bottom, R.bottom) + 60;

const spec = {
  schema_version: 1,
  diagram_type: 'architecture',
  meta: {
    title: 'Train Ticket 服务调用关系图（调用方 → 被调方）',
    locale: 'zh-CN',
    quality_profile: 'standard',
    viewBox: [viewW, viewH],
  },
  components: comps,
  boundaries: [
    { kind: 'region', label: '调用方（caller）', wraps: L.ids, pad: 20 },
    { kind: 'region', label: '被调方（callee）', wraps: R.ids, pad: 20 },
  ],
  connections,
  cards: [
    { dot: 'cyan', title: '读法', items: ['左列为调用方，右列为被调方；同一服务可同时出现在两侧', '实线 = 同步 HTTP 调用（RestTemplate + Nacos）；虚线 = RabbitMQ 异步'] },
  ],
};

writeFileSync(new URL('./train-ticket-call-graph.architecture.json', import.meta.url), JSON.stringify(spec, null, 2));
console.log('callers:', callers.length, 'callees:', callees.length, 'edges:', connections.length, 'viewBox:', viewW + 'x' + viewH);
console.log('callees:', callees.join(', '));
