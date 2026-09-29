import { writeFileSync } from 'node:fs';

const nodeH = 56;
const gapX = 16;
const gapY = 16;
const pitchY = nodeH + gapY;
const left = 40;
const top = 110;
const bandGap = 36;

function w(label) { return label.length > 24 ? 194 : 166; }
const extraGap = { passengers: 64, ui: 64, gateway: 64 };

const domains = [
  {
    id: 'edge', label: '入口与身份', rows: 8,
    nodes: [
      ['passengers', 'external', '乘客', '浏览器 / Web 客户端'],
      ['ui', 'frontend', 'ts-ui-dashboard', 'Nginx :8080 静态前端'],
      ['gateway', 'backend', 'ts-gateway-service', 'Spring Cloud Gateway :18888'],
      ['auth', 'security', 'ts-auth-service', '登录 · 签发 JWT'],
      ['user', 'backend', 'ts-user-service', '用户账户'],
      ['verification', 'backend', 'ts-verification-code-service', '验证码'],
      ['security-svc', 'backend', 'ts-security-service', '安全 / 风控'],
    ],
  },
  {
    id: 'booking', label: '订票与出行（核心）', rows: 8,
    nodes: [
      ['preserve', 'backend', 'ts-preserve-service', '订票编排'],
      ['seat', 'backend', 'ts-seat-service', '座位分配'],
      ['order', 'backend', 'ts-order-service', '订单'],
      ['basic', 'backend', 'ts-basic-service', '车次信息聚合'],
      ['travel', 'backend', 'ts-travel-service', '高铁车次'],
      ['travel2', 'backend', 'ts-travel2-service', '普通车次'],
      ['preserve-other', 'backend', 'ts-preserve-other-service', '跨局订票'],
      ['order-other', 'backend', 'ts-order-other-service', '跨局订单'],
      ['travel-plan', 'backend', 'ts-travel-plan-service', '行程规划'],
      ['route-plan', 'backend', 'ts-route-plan-service', '路线规划'],
      ['route', 'backend', 'ts-route-service', '路线'],
      ['price', 'backend', 'ts-price-service', '票价'],
      ['train', 'backend', 'ts-train-service', '列车'],
      ['station', 'backend', 'ts-station-service', '车站'],
      ['contacts', 'backend', 'ts-contacts-service', '联系人'],
    ],
  },
  {
    id: 'payment', label: '支付与订单生命周期', rows: 6,
    nodes: [
      ['inside-payment', 'backend', 'ts-inside-payment-service', '支付结算'],
      ['payment', 'backend', 'ts-payment-service', '支付网关'],
      ['execute', 'backend', 'ts-execute-service', '订单执行'],
      ['cancel', 'backend', 'ts-cancel-service', '取消'],
      ['rebook', 'backend', 'ts-rebook-service', '改签'],
      ['wait-order', 'backend', 'ts-wait-order-service', '候补订单'],
    ],
  },
  {
    id: 'ancillary', label: '辅助服务与通知', rows: 7,
    nodes: [
      ['assurance', 'backend', 'ts-assurance-service', '保险'],
      ['food', 'backend', 'ts-food-service', '餐饮'],
      ['station-food', 'backend', 'ts-station-food-service', '车站餐食'],
      ['train-food', 'backend', 'ts-train-food-service', '列车餐食'],
      ['food-delivery', 'backend', 'ts-food-delivery-service', '餐食配送'],
      ['delivery', 'backend', 'ts-delivery-service', '配送'],
      ['consign', 'backend', 'ts-consign-service', '托运'],
      ['consign-price', 'backend', 'ts-consign-price-service', '托运价格'],
      ['ticket-office', 'backend', 'ts-ticket-office-service', '售票点'],
      ['news', 'backend', 'ts-news-service', '资讯'],
      ['config', 'backend', 'ts-config-service', '配置'],
      ['avatar', 'backend', 'ts-avatar-service', '头像'],
      ['voucher', 'backend', 'ts-voucher-service', '代金券'],
      ['notification', 'backend', 'ts-notification-service', '通知（队列消费）'],
    ],
  },
  {
    id: 'admin', label: '管理后台', rows: 5,
    nodes: [
      ['admin-basic-info', 'backend', 'ts-admin-basic-info-service', '基础信息管理'],
      ['admin-order', 'backend', 'ts-admin-order-service', '订单管理'],
      ['admin-route', 'backend', 'ts-admin-route-service', '路线管理'],
      ['admin-travel', 'backend', 'ts-admin-travel-service', '车次管理'],
      ['admin-user', 'backend', 'ts-admin-user-service', '用户管理'],
    ],
  },
  {
    id: 'platform', label: '平台与数据（外部依赖）', rows: 7,
    nodes: [
      ['nacos', 'cloud', 'Nacos', '注册 / 配置 :8848'],
      ['rabbitmq', 'messagebus', 'RabbitMQ', '消息队列'],
      ['mongo', 'database', 'MongoDB', '主数据存储'],
      ['mysql', 'database', 'MySQL', 'voucher 等'],
      ['redis', 'database', 'Redis', '缓存'],
    ],
  },
];

const components = [];
const boundaries = [];
const map = new Map();
let y = top;
let maxRowRight = 0;

for (const d of domains) {
  const nodes = d.nodes;
  const rows = [];
  for (let i = 0; i < nodes.length; i += d.rows) rows.push(nodes.slice(i, i + d.rows));
  const ids = [];
  let rowY = y;
  for (const row of rows) {
    let x = left;
    for (const [id, type, label, sub] of row) {
      const width = w(label);
      const comp = { id, type, label, sublabel: sub, pos: [x, rowY], size: [width, nodeH] };
      if (id === 'rabbitmq') comp.brand = 'rabbitmq';
      if (id === 'mongo') comp.brand = 'mongodb';
      components.push(comp);
      map.set(id, { x, y: rowY, w: width, h: nodeH });
      ids.push(id);
      x += width + gapX + (extraGap[id] || 0);
    }
    maxRowRight = Math.max(maxRowRight, x - gapX);
    rowY += pitchY;
  }
  boundaries.push({ kind: 'region', label: d.label, wraps: ids, pad: 16 });
  y = rowY - pitchY + nodeH + bandGap;
}

const viewW = Math.round(maxRowRight + 40);
const viewH = Math.round(y - bandGap + 40);

const spec = {
  schema_version: 1,
  diagram_type: 'architecture',
  meta: {
    title: 'Train Ticket 全网服务地图（46 个可运行服务）',
    locale: 'zh-CN',
    quality_profile: 'standard',
    viewBox: [viewW, viewH],
  },
  components: components.map((c) => ({
    id: c.id, type: c.type, label: c.label, sublabel: c.sublabel,
    pos: c.pos, size: c.size, ...(c.brand ? { brand: c.brand } : {}),
  })),
  boundaries,
  connections: [
    { id: 'passengers-ui', from: 'passengers', to: 'ui', label: 'HTTPS :8080', variant: 'emphasis', fromSide: 'right', toSide: 'left' },
    { id: 'ui-gateway', from: 'ui', to: 'gateway', label: '代理 /api/v1', variant: 'emphasis', fromSide: 'right', toSide: 'left' },
    { id: 'gateway-auth', from: 'gateway', to: 'auth', label: '登录 · JWT', variant: 'security', fromSide: 'right', toSide: 'left' },
  ],
  cards: [
    {
      dot: 'cyan',
      title: '网关路由',
      items: [
        'ts-gateway-service 以 Path 断言把 /api/v1/** 转发到 38 个后端服务',
        'ts-ui-dashboard 经 Nginx 将 /api/v1 反向代理到网关，JWT / 角色在网关与服务内校验',
      ],
    },
    {
      dot: 'amber',
      title: '数据、平台与未走网关的服务',
      items: [
        '各服务使用独立 MongoDB / MySQL 库；Nacos 负责注册配置，RabbitMQ 承载通知',
        'delivery / food-delivery / wait-order / ticket-office / news / voucher 未注册网关路由',
      ],
    },
  ],
};

writeFileSync(new URL('./train-ticket-service-map.architecture.json', import.meta.url), JSON.stringify(spec, null, 2));
console.log('wrote', viewW, 'x', viewH, 'components', components.length);
