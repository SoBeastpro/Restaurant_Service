import assert from "node:assert/strict";
import { changeCount, layoutIssues, layoutPayload, makeDraft, nextNumber, normalizeHallLayout } from "../src/domain/layout.js";
import { visibleTables } from "../src/domain/view.js";

const hall = { id: "h1", name: "Основной", layout: { width: 1000, height: 800, objects: [] } };
const tables = [
  { id: "t1", hallId: "h1", number: 1, seats: 7, shape: "ROUND", positionX: 40, positionY: 40 },
  { id: "t2", hallId: "h1", number: 2, seats: 4, shape: "RECTANGLE", positionX: 200, positionY: 40 },
];
const original = makeDraft(hall, tables);
assert.deepEqual(layoutIssues(original), []);
assert.equal(changeCount(original, original), 0);
const draft = structuredClone(original);
draft.tables[0].positionX = 60;
draft.objects.push({ id: "bar", type: "BAR", positionX: 400, positionY: 200, width: 140, height: 40, rotation: 0 });
assert.equal(changeCount(original, draft), 2);
const payload = layoutPayload(draft);
assert.equal(payload.tables[0].seatsCount, 7);
assert.equal(payload.tables[0].positionX, 60);
assert.equal(payload.layout.items[0].type, "BAR");
assert.equal(payload.layout.canvasWidth, 1000);
assert.equal(original.tables[0].positionX, 40, "Editing must not mutate the server snapshot");
const roundTrip = makeDraft({ ...hall, layout: normalizeHallLayout(payload.layout) }, payload.tables.map((t) => ({ ...t, hallId: hall.id, seats: t.seatsCount, rotation: t.rotationDegrees, active: t.enabled })));
assert.deepEqual(layoutPayload(roundTrip), payload);
draft.tables[1].positionX = 60;
assert.ok(layoutIssues(draft).some((i) => i.message.includes("пересекаются")));
draft.tables[1].number = "1";
assert.ok(layoutIssues(draft).some((i) => i.message.includes("уже занят")));
draft.tables[0].seats = 13;
assert.ok(layoutIssues(draft).some((i) => i.message.includes("1 до 12")));
draft.tables[0].positionX = NaN;
assert.ok(layoutIssues(draft).some((i) => i.message.includes("границы")));
draft.tables[1].deleted = true;
draft.tables[1].assigneeId = "waiter";
assert.ok(layoutIssues(draft, true).some((i) => i.message.includes("закреплён")));
assert.throws(() => layoutPayload(draft), /Удаление/);
assert.throws(() => layoutPayload({ ...original, tables: [...original.tables, { id: "new", isNew: true }] }), /создать новые/);
assert.equal(nextNumber([{ number: "01" }, { number: "2" }, { number: "3", deleted: true }]), 3);
const visibility = {
  halls: [{ id: "visible", venueId: "v" }, { id: "hidden", venueId: "v", status: "HIDDEN" }],
  tables: [
    { id: "a", hallId: "visible", number: 1 },
    { id: "b", hallId: "visible", number: 2, active: false },
    { id: "c", hallId: "hidden", number: 3 },
  ],
};
assert.deepEqual(visibleTables(visibility, { venueId: "v", role: "RUNNER" }).map((t) => t.id), ["a"]);
assert.equal(visibleTables(visibility, { venueId: "v", role: "MANAGER" }).length, 3);
console.log("Layout checks passed: persistence payload, immutability, overlaps, duplicate numbers, seats, bounds, shift protection.");
