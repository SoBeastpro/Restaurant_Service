export const SHAPES = [["ROUND", "Круглый"], ["SQUARE", "Квадратный"], ["RECTANGLE", "Длинный"]];
export const INTERIOR = [["BAR", "Бар"], ["WINDOW", "Окно"], ["ENTRANCE", "Вход"], ["WALL", "Стена"], ["COLUMN", "Колонна"], ["PLANT", "Растение"]];
export const GRID = 20;

export function normalizeHallLayout(layout) {
  if (!layout) return null;
  return {
    width: layout.canvasWidth ?? layout.width ?? 1200,
    height: layout.canvasHeight ?? layout.height ?? 800,
    objects: (layout.items ?? layout.objects ?? []).map((item, index) => ({ ...item, id: item.id ?? `interior-${index}`, rotation: item.rotation ?? 0 })),
  };
}

export function tableSize(table) {
  const width = table.shape === "RECTANGLE" ? 110 : 64;
  return table.rotation === 90 ? { width: 64, height: width } : { width, height: 64 };
}

export function nextNumber(tables) {
  const used = new Set(tables.filter((t) => !t.deleted).map((t) => Number(t.number)));
  let number = 1;
  while (used.has(number)) number += 1;
  return number;
}

export function makeDraft(hall, tables) {
  return {
    name: hall.name,
    status: hall.status || "ACTIVE",
    displayOrder: hall.displayOrder || 0,
    width: hall.layout?.width || 1000,
    height: hall.layout?.height || 800,
    objects: (hall.layout?.objects || []).map((o) => ({ ...o })),
    tables: tables.filter((t) => t.hallId === hall.id).map((t) => ({
      id: t.id, number: String(t.number), seats: t.seats, shape: t.shape || "RECTANGLE",
      positionX: Number(t.positionX) || 0, positionY: Number(t.positionY) || 0,
      rotation: t.rotation || 0, active: t.active !== false && t.status !== "INACTIVE",
      status: t.status, assigneeId: t.assigneeId, runnerId: t.runnerId,
      ...(t.foreign ? { foreign: true } : {}),
    })),
  };
}

export function changeCount(before, after) {
  let count = ["name", "status", "displayOrder", "width", "height"].filter((k) => before[k] !== after[k]).length;
  for (const key of ["tables", "objects"]) {
    const originals = new Map(before[key].map((row) => [row.id, JSON.stringify(row)]));
    const current = new Map(after[key].filter((row) => !row.deleted).map((row) => [row.id, JSON.stringify(row)]));
    for (const id of new Set([...originals.keys(), ...current.keys()])) {
      if (originals.get(id) !== current.get(id)) count += 1;
    }
  }
  return count;
}

export function layoutIssues(draft, shiftActive = false) {
  const issues = [];
  const add = (id, message) => issues.push({ id, message });
  if (!draft.name.trim()) add(null, "Введите название зала.");
  if (![draft.width, draft.height].every((n) => Number.isFinite(n) && n >= 200 && n <= 5000)) {
    add(null, "Размеры плана должны быть от 200 до 5000.");
  }
  const tables = draft.tables.filter((t) => !t.deleted);
  for (const table of draft.tables) {
    const assigned = shiftActive && (table.assigneeId || table.runnerId);
    if (assigned && (table.deleted || draft.status === "HIDDEN")) add(table.id, `Стол ${table.number} закреплён в смене. Удалить стол или скрыть зал можно после смены.`);
    if (table.deleted) continue;
    if (!/^\d+$/.test(String(table.number)) || Number(table.number) < 1) add(table.id, "Номер стола должен быть положительным целым числом.");
    if (tables.some((t) => t.id !== table.id && Number(t.number) === Number(table.number))) add(table.id, `Номер ${table.number} уже занят.`);
    if (!Number.isInteger(table.seats) || table.seats < 1 || table.seats > 12) add(table.id, `Стол ${table.number}: укажите от 1 до 12 мест.`);
    const size = tableSize(table);
    if (![table.positionX, table.positionY].every(Number.isFinite) || table.positionX < 0 || table.positionY < 0 || table.positionX + size.width > draft.width || table.positionY + size.height > draft.height) add(table.id, `Стол ${table.number} выходит за границы плана.`);
  }
  for (let i = 0; i < tables.length; i += 1) {
    const a = tables[i], sa = tableSize(a);
    for (const b of tables.slice(i + 1)) {
      const sb = tableSize(b);
      if (a.positionX < b.positionX + sb.width && a.positionX + sa.width > b.positionX && a.positionY < b.positionY + sb.height && a.positionY + sa.height > b.positionY) {
        add(a.id, `Столы ${a.number} и ${b.number} пересекаются.`);
      }
    }
  }
  for (const object of draft.objects) {
    if (!INTERIOR.some(([type]) => type === object.type)) add(object.id, "Неизвестный предмет интерьера.");
    if (![object.width, object.height].every((n) => Number.isFinite(n) && n >= 8)) add(object.id, "Укажите положительный размер предмета (от 8).");
    const w = object.rotation === 90 ? object.height : object.width;
    const h = object.rotation === 90 ? object.width : object.height;
    if (![object.positionX, object.positionY].every(Number.isFinite) || object.positionX < 0 || object.positionY < 0 || object.positionX + w > draft.width || object.positionY + h > draft.height) add(object.id, "Предмет выходит за границы плана.");
  }
  return issues;
}

export function layoutPayload(draft) {
  if (draft.tables.some((t) => t.isNew && !t.deleted)) throw new Error("Перед сохранением плана нужно создать новые столы.");
  if (draft.tables.some((t) => t.deleted && !t.isNew)) throw new Error("Удаление существующих столов пока не поддерживается сервером. Стол можно выключить.");
  return {
    layout: {
      canvasWidth: draft.width, canvasHeight: draft.height,
      items: draft.objects.map((item) => ({ type: item.type, positionX: item.positionX, positionY: item.positionY,
        width: item.rotation === 90 ? item.height : item.width, height: item.rotation === 90 ? item.width : item.height })),
    },
    tables: draft.tables.filter((t) => !t.deleted).map((t) => ({
      id: t.id,
      number: String(t.number), seatsCount: t.seats, shape: t.shape,
      positionX: t.positionX, positionY: t.positionY, rotationDegrees: t.rotation, enabled: t.active,
    })),
  };
}
