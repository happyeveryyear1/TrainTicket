import { writeFileSync } from 'node:fs';

const NODE_W = 170, NODE_H = 38, PITCH_X = 196, PITCH_Y = 76;
const COL = { c0: 40, c1: 236, c2: 432, c3: 628, c4: 824, c5: 1020, c6: 1216 };
const ROW = { A: 104, B: 180, C1: 256, C2: 332, C3: 408, D: 496, E1: 572, E2: 648 };

const D = {
  passengers: ['external', '乘客 / 浏览器', 'Web 客户端'],
  ui: ['frontend', 'ts-ui-dashboard', 'Nginx :8080'],
  gateway: ['backend', 'ts-gateway-service', 'Gateway :18888'],
  insidePayment: ['backend', 'ts-inside-payment-service', '支付结算'],
  payment: ['backend', 'ts-payment-service', '支付网关'],
  user: ['backend', 'ts-user-service', '用户账户'],
  nacos: ['cloud', 'Nacos', '注册 · 配置 :8848'],
  auth: ['security', 'ts-auth-service', '登录 · 签发 JWT'],
  verification: ['backend', 'ts-verification-code-service', '验证码'],
  security: ['backend', 'ts-security-service', '安全 / 风控'],
  stationFood: ['backend', 'ts-station-food-service', '车站餐食'],
  preserveOther: ['backend', 'ts-preserve-other-service', '跨局订票'],
  basic: ['backend', 'ts-basic-service', '车次信息聚合'],
  orderOther: ['backend', 'ts-order-other-service', '跨局订单'],
  travel: ['backend', 'ts-travel-service', '高铁车次'],
  travel2: ['backend', 'ts-travel2-service', '普通车次'],
  route: ['backend', 'ts-route-service', '路线'],
  contacts: ['backend', 'ts-contacts-service', '联系人'],
  seat: ['backend', 'ts-seat-service', '座位分配'],
  preserve: ['backend', 'ts-preserve-service', '订票编排'],
  order: ['backend', 'ts-order-service', '订单'],
  station: ['backend', 'ts-station-service', '车站'],
  travelPlan: ['backend', 'ts-travel-plan-service', '行程规划'],
  routePlan: ['backend', 'ts-route-plan-service', '路线规划'],
  rabbitmq: ['messagebus', 'RabbitMQ', 'email / food_delivery'],
  notification: ['backend', 'ts-notification-service', '通知消费'],
  mongo: ['database', 'MongoDB', '各服务独立库'],
  food: ['backend', 'ts-food-service', '餐饮'],
  train: ['backend', 'ts-train-service', '列车'],
  adminBasic: ['backend', 'ts-admin-basic-info', '基础信息管理'],
  adminOrder: ['backend', 'ts-admin-order-service', '订单管理'],
  price: ['backend', 'ts-price-service', '票价'],
  adminRoute: ['backend', 'ts-admin-route-service', '路线管理'],
  adminTravel: ['backend', 'ts-admin-travel-service', '车次管理'],
  adminUser: ['backend', 'ts-admin-user-service', '用户管理'],
  execute: ['backend', 'ts-execute-service', '订单执行'],
  cancel: ['backend', 'ts-cancel-service', '取消'],
  foodDelivery: ['backend', 'ts-food-delivery-service', '餐食配送'],
  rebook: ['backend', 'ts-rebook-service', '改签'],
  delivery: ['backend', 'ts-delivery-service', '配送'],
  consign: ['backend', 'ts-consign-service', '托运'],
  consignPrice: ['backend', 'ts-consign-price-service', '托运价格'],
  ticketOffice: ['backend', 'ts-ticket-office-service', '售票点'],
  news: ['backend', 'ts-news-service', '资讯'],
  config: ['backend', 'ts-config-service', '配置'],
  waitOrder: ['backend', 'ts-wait-order-service', '候补订单'],
  avatar: ['backend', 'ts-avatar-service', '头像'],
  voucher: ['backend', 'ts-voucher-service', '代金券'],
  assurance: ['backend', 'ts-assurance-service', '保险'],
  trainFood: ['backend', 'ts-train-food-service', '列车餐食'],
};

const place = [
  ['passengers', 'c0', 'A'], ['ui', 'c1', 'A'], ['gateway', 'c2', 'A'], ['insidePayment', 'c4', 'A'], ['payment', 'c5', 'A'],
  ['user', 'c0', 'B'], ['nacos', 'c1', 'B'], ['auth', 'c3', 'B'], ['verification', 'c4', 'B'], ['security', 'c5', 'B'], ['stationFood', 'c6', 'B'],
  ['preserveOther', 'c0', 'C1'], ['basic', 'c1', 'C1'], ['orderOther', 'c3', 'C1'], ['travel', 'c4', 'C1'], ['travel2', 'c5', 'C1'], ['route', 'c6', 'C1'],
  ['contacts', 'c0', 'C2'], ['seat', 'c1', 'C2'], ['preserve', 'c2', 'C2'], ['order', 'c3', 'C2'], ['station', 'c4', 'C2'], ['travelPlan', 'c5', 'C2'], ['routePlan', 'c6', 'C2'],
  ['rabbitmq', 'c2', 'C3'], ['notification', 'c3', 'C3'], ['mongo', 'c4', 'C3'], ['food', 'c5', 'C3'], ['train', 'c6', 'C3'],
  ['adminBasic', 'c0', 'D'], ['adminOrder', 'c1', 'D'], ['price', 'c2', 'D'], ['adminRoute', 'c3', 'D'], ['adminTravel', 'c4', 'D'], ['adminUser', 'c5', 'D'], ['execute', 'c6', 'D'],
  ['cancel', 'c0', 'E1'], ['foodDelivery', 'c1', 'E1'], ['rebook', 'c2', 'E1'], ['delivery', 'c3', 'E1'], ['consign', 'c4', 'E1'], ['consignPrice', 'c5', 'E1'], ['ticketOffice', 'c6', 'E1'],
  ['news', 'c0', 'E2'], ['config', 'c1', 'E2'], ['waitOrder', 'c2', 'E2'], ['avatar', 'c3', 'E2'], ['voucher', 'c4', 'E2'], ['assurance', 'c5', 'E2'], ['trainFood', 'c6', 'E2'],
];

const components = place.map(([id, c, r]) => {
  const [type, label, sublabel] = D[id];
  const comp = { id, type, label, sublabel, pos: [COL[c], ROW[r]], size: [id === 'verification' ? 188 : NODE_W, NODE_H] };
  if (id === 'rabbitmq') comp.brand = 'rabbitmq';
  if (id === 'mongo') comp.brand = 'mongodb';
  return comp;
});

const boundaries = [
  { kind: 'region', label: '公共互联网 · 不可信', wraps: ['passengers'], pad: 22 },
  { kind: 'region', label: 'Train Ticket 私有服务网络（46 个服务）', wraps: place.map(([id]) => id).filter((id) => id !== 'passengers'), pad: 28 },
  { kind: 'security-group', label: '网关信任边界 · JWT + 角色', wraps: ['gateway', 'auth'], pad: 18 },
  { kind: 'security-group', label: '异步与数据层 · 仅内网', wraps: ['rabbitmq', 'notification', 'mongo'], pad: 14 },
];

const connections = [
  { id: 'p-ui', from: 'passengers', to: 'ui', label: 'HTTPS :8080', variant: 'emphasis', fromSide: 'right', toSide: 'left', labelAt: [223, 158] },
  { id: 'ui-gw', from: 'ui', to: 'gateway', label: '反向代理 /api/v1', variant: 'emphasis', fromSide: 'right', toSide: 'left', labelAt: [471, 158] },
  { id: 'gw-preserve', from: 'gateway', to: 'preserve', label: '路由 lb://', variant: 'emphasis', fromSide: 'bottom', toSide: 'top', labelAt: [517, 240] },
  { id: 'preserve-basic', from: 'preserve', to: 'basic', label: '余票 · 票价', fromSide: 'top', toSide: 'bottom' },
  { id: 'preserve-seat', from: 'preserve', to: 'seat', label: '分配座位', variant: 'emphasis', fromSide: 'left', toSide: 'right', labelAt: [419, 381] },
  { id: 'preserve-order', from: 'preserve', to: 'order', label: '创建订单', variant: 'emphasis', fromSide: 'right', toSide: 'left', labelAt: [615, 381] },
  { id: 'order-mongo', from: 'order', to: 'mongo', label: '订单持久化', fromSide: 'bottom', toSide: 'top' },
  { id: 'gw-pay', from: 'gateway', to: 'insidePayment', label: '支付路由', fromSide: 'right', toSide: 'left' },
  { id: 'pay-gate', from: 'insidePayment', to: 'payment', label: '扣款', fromSide: 'right', toSide: 'left', labelAt: [1007, 158] },
  { id: 'gw-auth', from: 'gateway', to: 'auth', label: '登录 · JWT', variant: 'security', fromSide: 'right', toSide: 'left' },
  { id: 'preserve-mq', from: 'preserve', to: 'rabbitmq', label: '异步邮件事件', variant: 'dashed', fromSide: 'bottom', toSide: 'top', labelAt: [600, 381] },
  { id: 'mq-notify', from: 'rabbitmq', to: 'notification', label: '消费通知', variant: 'dashed', fromSide: 'right', toSide: 'left', labelAt: [615, 474] },
  { id: 'gw-nacos', from: 'gateway', to: 'nacos', label: '服务发现', labelAt: [385, 160] },
];

const cards = [
  {
    dot: 'cyan',
    title: '入口与信任边界',
    items: [
      '浏览器只访问 ts-ui-dashboard 的 :8080，/api/v1 由 Nginx 反向代理到网关',
      'ts-gateway-service :18888 是唯一入口，校验 JWT / 角色后按 Path 断言转发',
      '网关以内同处私有网络；公共互联网不可信；异步与数据层仅内网可访问',
    ],
  },
  {
    dot: 'amber',
    title: '主路径与外部依赖',
    items: [
      '主路径：订票（网关→preserve→车次/座位/订单→MongoDB）、支付、认证、异步通知、服务发现',
      '外部依赖：MongoDB / MySQL / Redis、RabbitMQ、Nacos、Jaeger/Zipkin + Prometheus/Grafana + EFK',
      '另有 92 条服务间调用（order/travel/security/admin 等）见 train-ticket-call-atlas.html 与调用矩阵',
    ],
  },
];

const spec = {
  schema_version: 1,
  diagram_type: 'architecture',
  meta: {
    title: 'Train Ticket 运行时架构（50 组件 · 多主路径 · 信任边界）',
    locale: 'zh-CN',
    quality_profile: 'standard',
    viewBox: [1436, 740],
  },
  components,
  boundaries,
  connections,
  cards,
};

writeFileSync(new URL('./train-ticket-runtime-v2.architecture.json', import.meta.url), JSON.stringify(spec, null, 2));
console.log('components:', components.length, 'connections:', connections.length, 'cards:', cards.length);
