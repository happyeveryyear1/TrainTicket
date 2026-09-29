import { readFileSync, writeFileSync } from 'node:fs';

const raw = JSON.parse(readFileSync(new URL('./call-graph.raw.json', import.meta.url), 'utf8'));
const calls = raw.calls;

const MQ = [
  ['ts-preserve-service', 'rabbitmq'], ['ts-preserve-other-service', 'rabbitmq'], ['ts-food-service', 'rabbitmq'],
  ['rabbitmq', 'ts-notification-service'], ['rabbitmq', 'ts-delivery-service'],
];
const edges = [];
for (const [u, deps] of Object.entries(calls)) for (const [v, n] of Object.entries(deps)) edges.push([u, v, n, 'call']);
for (const v of ['ts-order-service', 'ts-order-other-service']) edges.push(['ts-voucher-service', v, 1, 'call']);
for (const [u, v] of MQ) edges.push([u, v, 1, 'mq']);

const callers = [...new Set(edges.map((e) => e[0]))].sort();
const callees = [...new Set(edges.map((e) => e[1]))].sort();
const key = (u, v) => `${u}|${v}`;
const map = new Map(edges.map((e) => [key(e[0], e[1]), e]));

const short = (s) => s.replace(/^ts-/, '').replace(/-service$/, '');
const esc = (s) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

let head = '<tr><th class="corner">调用方 \\ 被调方</th>' + callees.map((c) => `<th class="rot"><span>${esc(short(c))}</span></th>`).join('') + '</tr>';
let body = '';
for (const u of callers) {
  let row = `<th class="rowh" title="${esc(u)}">${esc(short(u))}</th>`;
  for (const v of callees) {
    const e = map.get(key(u, v));
    if (!e) { row += '<td></td>'; continue; }
    const cls = e[3] === 'mq' ? 'mq' : 'hit';
    const label = e[3] === 'mq' ? 'MQ' : String(e[2]);
    row += `<td class="${cls}">${label}</td>`;
  }
  body += `<tr>${row}</tr>`;
}

const list = callers.map((u) => {
  const outs = edges.filter((e) => e[0] === u);
  const items = outs.map((e) => `${esc(e[1])}${e[3] === 'mq' ? ' (MQ)' : ` ×${e[2]}`}`).join('、');
  return `<li><b>${esc(u)}</b> → ${items}</li>`;
}).join('');

const inDeg = new Map();
for (const e of edges) inDeg.set(e[1], (inDeg.get(e[1]) || 0) + 1);
const hubs = [...inDeg.entries()].sort((a, b) => b[1] - a[1]).slice(0, 10)
  .map(([s, d]) => `<li>${esc(s)} ← ${d} 个调用方</li>`).join('');

const html = `<!doctype html>
<html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Train Ticket 服务调用矩阵（代码级）</title>
<style>
:root{--bg:#0f172a;--panel:#111c33;--line:#22304d;--txt:#dbe6f6;--muted:#8fa3c2;--hit:#1d4ed8;--hit2:#2563eb;--mq:#7c3aed;--accent:#22d3ee}
*{box-sizing:border-box}
body{margin:0;background:linear-gradient(180deg,#0b1220,#0f172a);color:var(--txt);font:14px/1.5 "Segoe UI",system-ui,"Microsoft YaHei",sans-serif}
header{padding:22px 26px 10px}
h1{margin:0 0 6px;font-size:20px}
h1 small{color:var(--muted);font-weight:400;font-size:13px;margin-left:8px}
.meta{color:var(--muted);font-size:13px}
.wrap{padding:8px 26px 40px}
.panel{background:var(--panel);border:1px solid var(--line);border-radius:12px;padding:14px 16px;margin:16px 0}
.panel h2{margin:0 0 10px;font-size:15px;color:var(--accent)}
.scroll{overflow:auto;max-height:70vh;border:1px solid var(--line);border-radius:10px}
table{border-collapse:separate;border-spacing:0;font-size:12px}
th,td{border-right:1px solid var(--line);border-bottom:1px solid var(--line);padding:3px 6px;text-align:center;white-space:nowrap}
thead th{position:sticky;top:0;background:#16233d;z-index:2}
.corner{position:sticky;left:0;top:0;z-index:3;background:#16233d;text-align:left;min-width:180px}
.rowh{position:sticky;left:0;background:#16233d;text-align:left;z-index:1;font-weight:500}
th.rot{height:150px;vertical-align:bottom}
th.rot span{writing-mode:vertical-rl;transform:rotate(180deg);display:inline-block}
td.hit{background:linear-gradient(180deg,var(--hit2),var(--hit));color:#fff;font-weight:600;border-radius:3px}
td.mq{background:var(--mq);color:#fff;font-weight:600;border-radius:3px}
ul{margin:6px 0 0;padding-left:20px}
li{margin:2px 0}
.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(320px,1fr));gap:16px}
code{background:#0b1220;border:1px solid var(--line);border-radius:5px;padding:1px 5px}
.legend span{display:inline-block;margin-right:16px}
.sw{display:inline-block;width:12px;height:12px;border-radius:3px;vertical-align:middle;margin-right:6px}
</style></head><body>
<header>
<h1>Train Ticket 服务调用矩阵 <small>代码级提取</small></h1>
<div class="meta">46 个可运行服务 · ${callers.length} 个调用方 · ${callees.length} 个被调方 · ${edges.filter((e) => e[3] === 'call').length} 条同步调用 · ${edges.filter((e) => e[3] === 'mq').length} 条异步消息</div>
</header>
<div class="wrap">
<div class="panel legend">
<span><i class="sw" style="background:#2563eb"></i>同步 HTTP 调用（数字=调用点数量）</span>
<span><i class="sw" style="background:#7c3aed"></i>RabbitMQ 异步消息</span>
</div>
<div class="panel">
<h2>调用方 × 被调方矩阵</h2>
<div class="scroll"><table><thead>${head}</thead><tbody>${body}</tbody></table></div>
</div>
<div class="grid">
<div class="panel"><h2>逐服务出边（调用方 → 被调方）</h2><ul>${list}</ul></div>
<div class="panel"><h2>被调方入度 Top 10</h2><ul>${hubs}</ul><p class="meta">入度 = 有多少个服务直接调用它。</p></div>
</div>
<div class="panel"><h2>提取方法</h2>
<ul>
<li>扫描每个服务 <code>src</code> 下的 <code>.java/.py/.js/.ts/.go</code>，提取 <code>getServiceUrl("ts-…")</code> 调用点与 <code>/api/v1/…</code> 目标。</li>
<li>Python 服务（如 <code>ts-voucher-service</code>）按直连 URL（如 <code>http://ts-order-service:12031</code>）计入。</li>
<li>异步边按 RabbitMQ 队列名匹配：<code>email</code>（preserve/preserve-other → notification）、<code>food_delivery</code>（food → delivery）。</li>
<li><code>ts-gateway-service</code> 的路由写在 <code>application.yml</code> 而非代码，未计入本矩阵；<code>ts-avatar</code>、<code>ts-news</code>、<code>ts-ticket-office</code> 未发现跨服务调用。</li>
</ul></div>
</div></body></html>`;

writeFileSync(new URL('./train-ticket-call-matrix.html', import.meta.url), html);
console.log('matrix written:', callers.length, 'x', callees.length, 'edges', edges.length);
