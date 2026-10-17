#!/usr/bin/env node

const fs = require('node:fs');
const path = require('node:path');
const sharp = require('sharp');

const docsDirectory = path.resolve(__dirname, '..');
const outputDirectory = __dirname;
const schemaDirectory = path.resolve(__dirname, '../..');

const services = [
  {
    key: 'identity',
    title: 'Identity DB',
    database: 'identity_db',
    file: 'services/identity-service/src/main/resources/db/migration/V1__identity.sql',
    color: '#DCEBFF',
    accent: '#245EA8',
    order: [
      'users', 'platform_roles', 'platform_permissions', 'devices', 'sessions',
      'user_platform_roles', 'platform_role_permissions', 'contact_verification_tokens',
      'password_reset_tokens', 'outbox_events', 'inbox_messages'
    ]
  },
  {
    key: 'restaurant',
    title: 'Restaurant DB',
    database: 'restaurant_db',
    file: 'services/restaurant-service/src/main/resources/db/migration/V1__restaurant.sql',
    color: '#FFE4DE',
    accent: '#A43D2C',
    order: ['restaurants', 'halls', 'dining_tables', 'table_status_history', 'outbox_events', 'inbox_messages']
  },
  {
    key: 'workforce',
    title: 'Workforce DB',
    database: 'workforce_db',
    file: 'services/workforce-service/src/main/resources/db/migration/V1__workforce.sql',
    color: '#DDF5F3',
    accent: '#176B65',
    order: [
      'restaurant_roles', 'permissions', 'restaurant_members', 'restaurant_role_permissions',
      'member_permission_overrides', 'member_permission_history', 'invitations',
      'outbox_events', 'inbox_messages'
    ]
  },
  {
    key: 'operations',
    title: 'Operations DB',
    database: 'operations_db',
    file: 'services/operations-service/src/main/resources/db/migration/V1__operations.sql',
    color: '#E9E1FA',
    accent: '#6543A5',
    order: [
      'shifts', 'shift_participants', 'task_templates', 'tasks', 'task_history',
      'shift_task_resolutions', 'shift_reports', 'favorite_task_templates',
      'outbox_events', 'inbox_messages'
    ]
  },
  {
    key: 'billing',
    title: 'Billing DB',
    database: 'billing_db',
    file: 'services/billing-service/src/main/resources/db/migration/V1__billing.sql',
    color: '#FFF0CF',
    accent: '#8D6210',
    order: ['tariff_plans', 'subscriptions', 'subscription_events', 'outbox_events', 'inbox_messages']
  },
  {
    key: 'notification',
    title: 'Notification DB',
    database: 'notification_db',
    file: 'services/notification-service/src/main/resources/db/migration/V1__notification.sql',
    color: '#DDF1FF',
    accent: '#23658C',
    order: ['notifications', 'push_registrations', 'delivery_attempts', 'outbox_events', 'inbox_messages']
  },
  {
    key: 'audit',
    title: 'Audit DB',
    database: 'audit_db',
    file: 'services/audit-service/src/main/resources/db/migration/V1__audit.sql',
    color: '#FADDE8',
    accent: '#8D3156',
    order: ['activity_logs', 'audit_logs', 'system_events', 'inbox_messages']
  },
  {
    key: 'reporting',
    title: 'Reporting DB',
    database: 'reporting_db',
    file: 'services/reporting-service/src/main/resources/db/migration/V1__reporting.sql',
    color: '#DFF2DA',
    accent: '#397A2C',
    order: ['restaurant_daily_metrics', 'employee_daily_metrics', 'table_daily_metrics', 'inbox_messages']
  }
];

const logicalColumnsByService = {
  identity: new Set(),
  restaurant: new Set(['director_user_id', 'changed_by_membership_id']),
  workforce: new Set([
    'restaurant_id', 'user_id', 'changed_by_user_id', 'accepted_by_user_id'
  ]),
  operations: new Set([
    'restaurant_id', 'membership_id', 'responsible_membership_id',
    'created_by_membership_id', 'started_by_membership_id', 'closed_by_membership_id',
    'added_by_membership_id', 'assigned_to_membership_id', 'actor_membership_id',
    'old_assignee_membership_id', 'new_assignee_membership_id',
    'decided_by_membership_id', 'generated_by_membership_id'
  ]),
  billing: new Set([
    'restaurant_id', 'activated_by_membership_id', 'actor_user_id', 'created_by_user_id'
  ]),
  notification: new Set(['user_id', 'restaurant_id', 'device_id']),
  audit: new Set([
    'user_id', 'membership_id', 'restaurant_id', 'actor_user_id',
    'actor_membership_id', 'subject_user_id', 'entity_id'
  ]),
  reporting: new Set(['restaurant_id', 'membership_id', 'table_id'])
};

function escapeXml(value) {
  return String(value)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&apos;');
}

function splitTopLevel(body) {
  const items = [];
  let current = '';
  let depth = 0;
  let quoted = false;

  for (let index = 0; index < body.length; index += 1) {
    const character = body[index];
    if (character === "'" && body[index - 1] !== '\\') {
      quoted = !quoted;
    }
    if (!quoted) {
      if (character === '(') depth += 1;
      if (character === ')') depth -= 1;
      if (character === ',' && depth === 0) {
        items.push(current.trim());
        current = '';
        continue;
      }
    }
    current += character;
  }
  if (current.trim()) items.push(current.trim());
  return items;
}

function findTableBlocks(sql) {
  const blocks = [];
  const matcher = /CREATE TABLE\s+([a-z_][a-z0-9_]*)\s*\(/gi;
  let match;

  while ((match = matcher.exec(sql)) !== null) {
    const tableName = match[1];
    const bodyStart = matcher.lastIndex;
    let depth = 1;
    let quoted = false;
    let index = bodyStart;

    for (; index < sql.length && depth > 0; index += 1) {
      const character = sql[index];
      if (character === "'" && sql[index - 1] !== '\\') quoted = !quoted;
      if (!quoted) {
        if (character === '(') depth += 1;
        if (character === ')') depth -= 1;
      }
    }
    blocks.push({ name: tableName, body: sql.slice(bodyStart, index - 1) });
    matcher.lastIndex = index;
  }
  return blocks;
}

function parseSchema(sql, serviceKey) {
  const tables = [];
  const relationships = [];

  for (const block of findTableBlocks(sql)) {
    const items = splitTopLevel(block.body);
    const columns = [];
    const primaryKeys = new Set();
    const localForeignKeys = new Set();

    for (const item of items) {
      const normalized = item.replace(/\s+/g, ' ').trim();
      const tablePrimaryKey = normalized.match(/^(?:CONSTRAINT\s+\S+\s+)?PRIMARY KEY\s*\(([^)]+)\)/i);
      if (tablePrimaryKey) {
        tablePrimaryKey[1].split(',').map(value => value.trim()).forEach(value => primaryKeys.add(value));
        continue;
      }

      const tableForeignKey = normalized.match(
        /^(?:CONSTRAINT\s+\S+\s+)?FOREIGN KEY\s*\(([^)]+)\)\s+REFERENCES\s+([a-z_][a-z0-9_]*)\s*\(([^)]+)\)/i
      );
      if (tableForeignKey) {
        const sourceColumns = tableForeignKey[1].split(',').map(value => value.trim());
        sourceColumns.forEach(value => localForeignKeys.add(value));
        relationships.push({ child: block.name, parent: tableForeignKey[2], columns: sourceColumns });
        continue;
      }

      if (/^(?:CONSTRAINT\s+|UNIQUE\s*\(|CHECK\s*\()/i.test(normalized)) continue;
      const columnMatch = normalized.match(/^([a-z_][a-z0-9_]*)\s+(.+)$/i);
      if (!columnMatch) continue;

      const type = columnMatch[2]
        .split(/\s+(?=NOT NULL|PRIMARY KEY|DEFAULT|REFERENCES|CONSTRAINT|CHECK|UNIQUE\b)/i)[0]
        .trim()
        .toUpperCase();

      const column = {
        name: columnMatch[1],
        type,
        required: /\bNOT NULL\b/i.test(normalized),
        primary: /\bPRIMARY KEY\b/i.test(normalized),
        foreign: /\bREFERENCES\b/i.test(normalized),
        logical: false
      };
      columns.push(column);
      if (column.primary) primaryKeys.add(column.name);

      const inlineReference = normalized.match(/\bREFERENCES\s+([a-z_][a-z0-9_]*)\s*\(([^)]+)\)/i);
      if (inlineReference) {
        localForeignKeys.add(column.name);
        relationships.push({ child: block.name, parent: inlineReference[1], columns: [column.name] });
      }
    }

    for (const column of columns) {
      column.primary = primaryKeys.has(column.name);
      column.foreign = localForeignKeys.has(column.name);
      column.logical = logicalColumnsByService[serviceKey].has(column.name);
    }
    tables.push({ name: block.name, columns });
  }

  return { tables, relationships };
}

function orderedTables(service, tables) {
  const rank = new Map(service.order.map((name, index) => [name, index]));
  return [...tables].sort((left, right) =>
    (rank.get(left.name) ?? Number.MAX_SAFE_INTEGER) - (rank.get(right.name) ?? Number.MAX_SAFE_INTEGER)
  );
}

function tableHeight(table) {
  return 42 + table.columns.length * 24 + 12;
}

function createServiceSvg(service, schema) {
  const tables = orderedTables(service, schema.tables);
  const columnsCount = tables.length >= 8 ? 3 : 2;
  const boxWidth = 500;
  const horizontalGap = 90;
  const verticalGap = 80;
  const pagePadding = 48;
  const headerHeight = 104;
  const positions = new Map();
  const columnHeights = Array(columnsCount).fill(headerHeight);

  for (const table of tables) {
    let selectedColumn = 0;
    for (let column = 1; column < columnsCount; column += 1) {
      if (columnHeights[column] < columnHeights[selectedColumn]) selectedColumn = column;
    }
    const x = pagePadding + selectedColumn * (boxWidth + horizontalGap);
    const y = columnHeights[selectedColumn];
    const height = tableHeight(table);
    positions.set(table.name, { x, y, width: boxWidth, height });
    columnHeights[selectedColumn] += height + verticalGap;
  }

  const width = pagePadding * 2 + columnsCount * boxWidth + (columnsCount - 1) * horizontalGap;
  const height = Math.max(...columnHeights) - verticalGap + pagePadding + 42;
  const pieces = [];

  pieces.push(`<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}">`);
  pieces.push(`<rect width="100%" height="100%" fill="#F8FAFC"/>`);
  pieces.push(`<defs><marker id="arrow" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto"><path d="M0,0 L8,4 L0,8 Z" fill="#64748B"/></marker></defs>`);
  pieces.push(`<text x="${pagePadding}" y="42" font-family="Arial, Helvetica, sans-serif" font-size="28" font-weight="700" fill="#0F172A">${escapeXml(service.title)}</text>`);
  pieces.push(`<text x="${pagePadding}" y="70" font-family="Arial, Helvetica, sans-serif" font-size="15" fill="#475569">${escapeXml(service.database)} · ${tables.length} tables · generated from ${escapeXml(path.basename(service.file))}</text>`);
  pieces.push(`<text x="${width - pagePadding}" y="42" text-anchor="end" font-family="Arial, Helvetica, sans-serif" font-size="13" fill="#475569">PK primary key · FK PostgreSQL foreign key · LK logical cross-service UUID</text>`);

  for (const relationship of schema.relationships) {
    const child = positions.get(relationship.child);
    const parent = positions.get(relationship.parent);
    if (!child || !parent) continue;

    const childCenter = child.x + child.width / 2;
    const parentCenter = parent.x + parent.width / 2;
    const leftToRight = childCenter < parentCenter;
    const startX = leftToRight ? child.x + child.width : child.x;
    const endX = leftToRight ? parent.x : parent.x + parent.width;
    const startY = child.y + 28;
    const endY = parent.y + 28;
    const midX = (startX + endX) / 2;
    const pathData = Math.abs(startX - endX) < 5
      ? `M ${startX} ${startY} C ${startX + 32} ${startY}, ${endX + 32} ${endY}, ${endX} ${endY}`
      : `M ${startX} ${startY} H ${midX} V ${endY} H ${endX}`;
    pieces.push(`<path d="${pathData}" fill="none" stroke="#94A3B8" stroke-width="2" marker-end="url(#arrow)"/>`);
  }

  for (const table of tables) {
    const position = positions.get(table.name);
    pieces.push(`<g>`);
    pieces.push(`<rect x="${position.x}" y="${position.y}" width="${position.width}" height="${position.height}" rx="8" fill="#FFFFFF" stroke="#CBD5E1" stroke-width="1.5"/>`);
    pieces.push(`<path d="M ${position.x + 8} ${position.y} H ${position.x + position.width - 8} Q ${position.x + position.width} ${position.y} ${position.x + position.width} ${position.y + 8} V ${position.y + 40} H ${position.x} V ${position.y + 8} Q ${position.x} ${position.y} ${position.x + 8} ${position.y}" fill="${service.color}"/>`);
    pieces.push(`<text x="${position.x + 16}" y="${position.y + 27}" font-family="Arial, Helvetica, sans-serif" font-size="17" font-weight="700" fill="${service.accent}">${escapeXml(table.name)}</text>`);

    table.columns.forEach((column, index) => {
      const y = position.y + 62 + index * 24;
      const markers = [];
      if (column.primary) markers.push('PK');
      if (column.foreign) markers.push('FK');
      if (column.logical) markers.push('LK');
      const markerText = markers.join('·');
      pieces.push(`<text x="${position.x + 16}" y="${y}" font-family="Menlo, Consolas, monospace" font-size="13" fill="#0F172A">${escapeXml(column.name)}</text>`);
      pieces.push(`<text x="${position.x + 330}" y="${y}" font-family="Menlo, Consolas, monospace" font-size="12" fill="#475569">${escapeXml(column.type)}${column.required || column.primary ? '' : '?'}</text>`);
      if (markerText) pieces.push(`<text x="${position.x + position.width - 16}" y="${y}" text-anchor="end" font-family="Arial, Helvetica, sans-serif" font-size="11" font-weight="700" fill="${column.logical ? '#B45309' : service.accent}">${markerText}</text>`);
    });
    pieces.push(`</g>`);
  }

  pieces.push(`<text x="${pagePadding}" y="${height - 18}" font-family="Arial, Helvetica, sans-serif" font-size="12" fill="#64748B">Nullable columns are marked with ?. Solid arrows are physical foreign keys inside this database.</text>`);
  pieces.push('</svg>');
  return { svg: pieces.join('\n'), width, height, tables };
}

function createOverviewSvg(parsedServices) {
  const width = 1900;
  const height = 1280;
  const cardWidth = 410;
  const positions = {
    identity: { x: 70, y: 120 },
    restaurant: { x: 745, y: 100 },
    workforce: { x: 745, y: 490 },
    operations: { x: 1420, y: 330 },
    billing: { x: 70, y: 830 },
    notification: { x: 70, y: 470 },
    audit: { x: 745, y: 900 },
    reporting: { x: 1420, y: 830 }
  };
  const logicalLinks = [
    ['identity', 'restaurant'],
    ['identity', 'workforce'],
    ['identity', 'notification'],
    ['identity', 'billing'],
    ['restaurant', 'workforce'],
    ['restaurant', 'operations'],
    ['restaurant', 'billing'],
    ['workforce', 'operations'],
    ['workforce', 'billing'],
    ['restaurant', 'reporting'],
    ['workforce', 'reporting']
  ];
  const pieces = [];
  pieces.push(`<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}">`);
  pieces.push(`<rect width="100%" height="100%" fill="#F8FAFC"/>`);
  pieces.push(`<defs><marker id="logical-arrow" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto"><path d="M0,0 L8,4 L0,8 Z" fill="#B45309"/></marker></defs>`);
  pieces.push(`<text x="70" y="54" font-family="Arial, Helvetica, sans-serif" font-size="30" font-weight="700" fill="#0F172A">Restaurant platform · modular database ERD</text>`);
  pieces.push(`<text x="70" y="82" font-family="Arial, Helvetica, sans-serif" font-size="15" fill="#475569">8 physical PostgreSQL databases · 54 tables · dashed arrows are logical UUID references, not PostgreSQL foreign keys</text>`);

  for (const [sourceKey, targetKey] of logicalLinks) {
    const source = positions[sourceKey];
    const target = positions[targetKey];
    const sourceHeight = 74 + parsedServices.find(item => item.service.key === sourceKey).schema.tables.length * 21;
    const targetHeight = 74 + parsedServices.find(item => item.service.key === targetKey).schema.tables.length * 21;
    const sourceCenter = { x: source.x + cardWidth / 2, y: source.y + sourceHeight / 2 };
    const targetCenter = { x: target.x + cardWidth / 2, y: target.y + targetHeight / 2 };
    const startX = sourceCenter.x < targetCenter.x ? source.x + cardWidth : source.x;
    const endX = sourceCenter.x < targetCenter.x ? target.x : target.x + cardWidth;
    const startY = sourceCenter.y;
    const endY = targetCenter.y;
    const midX = (startX + endX) / 2;
    pieces.push(`<path d="M ${startX} ${startY} H ${midX} V ${endY} H ${endX}" fill="none" stroke="#D97706" stroke-width="1.6" stroke-dasharray="7 6" marker-end="url(#logical-arrow)" opacity="0.75"/>`);
  }

  for (const item of parsedServices) {
    const service = item.service;
    const tableNames = orderedTables(service, item.schema.tables).map(table => table.name);
    const position = positions[service.key];
    const cardHeight = 74 + tableNames.length * 21;
    pieces.push(`<rect x="${position.x}" y="${position.y}" width="${cardWidth}" height="${cardHeight}" rx="10" fill="#FFFFFF" stroke="#CBD5E1" stroke-width="1.5"/>`);
    pieces.push(`<path d="M ${position.x + 10} ${position.y} H ${position.x + cardWidth - 10} Q ${position.x + cardWidth} ${position.y} ${position.x + cardWidth} ${position.y + 10} V ${position.y + 50} H ${position.x} V ${position.y + 10} Q ${position.x} ${position.y} ${position.x + 10} ${position.y}" fill="${service.color}"/>`);
    pieces.push(`<text x="${position.x + 16}" y="${position.y + 31}" font-family="Arial, Helvetica, sans-serif" font-size="18" font-weight="700" fill="${service.accent}">${escapeXml(service.database)}</text>`);
    pieces.push(`<text x="${position.x + cardWidth - 16}" y="${position.y + 31}" text-anchor="end" font-family="Arial, Helvetica, sans-serif" font-size="12" fill="${service.accent}">${tableNames.length} tables</text>`);
    tableNames.forEach((tableName, index) => {
      pieces.push(`<text x="${position.x + 16}" y="${position.y + 71 + index * 21}" font-family="Menlo, Consolas, monospace" font-size="13" fill="#334155">${escapeXml(tableName)}</text>`);
    });
  }

  pieces.push(`<text x="70" y="${height - 26}" font-family="Arial, Helvetica, sans-serif" font-size="12" fill="#64748B">Audit and reporting consume events and retain denormalized identifiers; their domain tables intentionally have no cross-database foreign keys.</text>`);
  pieces.push('</svg>');
  return pieces.join('\n');
}

async function writePng(baseName, svg) {
  const svgPath = path.join(outputDirectory, `${baseName}.svg`);
  const pngPath = path.join(outputDirectory, `${baseName}.png`);
  fs.writeFileSync(svgPath, svg);
  await sharp(Buffer.from(svg), { density: 160 }).png({ compressionLevel: 9 }).toFile(pngPath);
}

async function main() {
  fs.mkdirSync(outputDirectory, { recursive: true });
  const parsedServices = services.map(service => {
    const sql = fs.readFileSync(path.join(schemaDirectory, service.file), 'utf8');
    return { service, schema: parseSchema(sql, service.key) };
  });

  const totalTables = parsedServices.reduce((sum, item) => sum + item.schema.tables.length, 0);
  if (totalTables !== 54) {
    throw new Error(`Expected 54 tables, parsed ${totalTables}`);
  }

  await writePng('all-databases', createOverviewSvg(parsedServices));
  for (const item of parsedServices) {
    const rendered = createServiceSvg(item.service, item.schema);
    await writePng(`${item.service.key}-database`, rendered.svg);
  }

  console.log(`Generated overview and ${parsedServices.length} database diagrams from ${totalTables} tables.`);
  console.log(`Output: ${outputDirectory}`);
  console.log(`Documentation root: ${docsDirectory}`);
}

main().catch(error => {
  console.error(error);
  process.exitCode = 1;
});
