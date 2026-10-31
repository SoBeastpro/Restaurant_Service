import { useCallback, useEffect, useRef, useState } from "react";
import { Modal, Pressable, ScrollView, StyleSheet, Text, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { api } from "../../api/store";
import { SHAPES, INTERIOR, changeCount, layoutIssues, makeDraft, nextNumber, tableSize } from "../../domain/layout";
import { colors, type as font } from "../../theme";
import { Button, Field, Lead, Title } from "../../ui";
import { FloorPlan } from "./FloorPlan";

let localId = 0;
const newId = () => `draft-${Date.now()}-${++localId}`;

// Full-screen modal workflows keep the editor outside the bottom tab bar.
export function HallWorkspace({ data, initialHallId, onClose }) {
  const [hallId, setHallId] = useState(initialHallId ?? null);
  const [creating, setCreating] = useState(false);
  const [name, setName] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const cancelEditorRef = useRef(null);
  const hall = data.halls.find((h) => h.id === hallId);
  return <Modal visible animationType="slide" onRequestClose={() => { if (hall) cancelEditorRef.current?.(); else onClose(); }}>
    <SafeAreaView style={s.safe}>
      {hall ? <HallEditor key={hall.id} hall={hall} data={data} cancelEditorRef={cancelEditorRef} onClose={() => setHallId(null)} /> : <ScrollView contentContainerStyle={s.list}>
        <View style={s.header}><Title>Залы</Title><Action label="Закрыть" onPress={onClose} /></View>
        <Lead>План зала видят управляющий и сотрудники. Выберите зал, чтобы изменить расстановку.</Lead>
        {[...data.halls].sort((a, b) => (a.displayOrder || 0) - (b.displayOrder || 0)).map((item) => {
          const draft = makeDraft(item, data.allTables);
          return <Pressable key={item.id} accessibilityRole="button" accessibilityLabel={`Редактировать зал ${item.name}`} onPress={() => setHallId(item.id)} style={s.hall}>
            <View style={s.thumb}><FloorPlan draft={draft} miniature /></View>
            <View style={s.flex}><Text style={s.bold}>{item.name}</Text><Text style={s.meta}>{draft.tables.length} столов · {draft.tables.reduce((sum, t) => sum + t.seats, 0)} мест</Text>{item.status === "HIDDEN" ? <Text style={s.tag}>Скрыт</Text> : null}</View>
            <Text style={s.bold}>›</Text>
          </Pressable>;
        })}
        {!data.halls.length ? <Lead>Залов пока нет. Создайте первый.</Lead> : null}
        {error ? <Text accessibilityRole="alert" style={s.error}>{error}</Text> : null}
        {creating ? <>
          <Field label="Название нового зала" value={name} onChangeText={setName} maxLength={100} capitalize="sentences" />
          <Button disabled={busy} label={busy ? "Создаём…" : "Создать зал"} onPress={async () => {
            if (!name.trim()) { setError("Введите название зала."); return; }
            setBusy(true); setError("");
            try { const created = await api.createHall({ name: name.trim() }); setCreating(false); setName(""); setHallId(created.hallId); }
            catch (caught) { setError(caught.message); }
            finally { setBusy(false); }
          }} />
          <Button tone="ghost" disabled={busy} label="Отмена" onPress={() => setCreating(false)} />
        </> : <Button tone="ghost" label="+ Новый зал" onPress={() => setCreating(true)} />}
      </ScrollView>}
    </SafeAreaView>
  </Modal>;
}

function HallEditor({ hall, data, onClose, cancelEditorRef }) {
  const [original] = useState(() => makeDraft(hall, data.allTables));
  const [draft, setDraft] = useState(original);
  const [history, setHistory] = useState([]);
  const [selectedId, setSelected] = useState(null);
  const [sheet, setSheet] = useState(null);
  const [grid, setGrid] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [saved, setSaved] = useState(false);
  const [serverIssues, setServerIssues] = useState([]);
  const [discard, setDiscard] = useState(false);
  const [newShape, setNewShape] = useState("ROUND");
  const [newSeats, setNewSeats] = useState(4);
  const viewport = useRef(null);
  const selectedTable = draft.tables.find((t) => t.id === selectedId && !t.deleted);
  const selectedObject = draft.objects.find((o) => o.id === selectedId);
  const selected = selectedTable || selectedObject;
  const count = changeCount(original, draft);
  const issues = layoutIssues(draft, Boolean(data.shift));
  function checkpoint() { setHistory((rows) => [...rows.slice(-49), draft]); }
  function change(next) { checkpoint(); setDraft(next); setError(""); setServerIssues([]); }
  function patch(id, fields, record = true) {
    if (record) checkpoint();
    setDraft((current) => ({ ...current, tables: current.tables.map((t) => t.id === id ? { ...t, ...fields } : t), objects: current.objects.map((o) => o.id === id ? { ...o, ...fields } : o) }));
    setServerIssues([]);
  }
  function addTable(copy) {
    const id = newId();
    const table = { id, isNew: true, number: String(nextNumber(draft.tables)), seats: newSeats, shape: newShape, positionX: 0, positionY: 0, rotation: 0, active: true, ...copy, assigneeId: null, runnerId: null };
    table.id = id; table.isNew = true; table.number = String(nextNumber(draft.tables)); table.deleted = false;
    const center = viewport.current || { x: draft.width / 2, y: draft.height / 2 };
    table.positionX = Math.max(0, Math.min(draft.width - tableSize(table).width, Math.round(center.x - tableSize(table).width / 2)));
    table.positionY = Math.max(0, Math.min(draft.height - tableSize(table).height, Math.round(center.y - tableSize(table).height / 2)));
    if (copy) { table.positionX = Math.min(draft.width - tableSize(table).width, copy.positionX + 120); table.positionY = Math.min(draft.height - tableSize(table).height, copy.positionY + 80); }
    change({ ...draft, tables: [...draft.tables, table] }); setSelected(id); setSheet("properties");
  }
  async function save() {
    setError(""); setServerIssues([]);
    if (issues.length) { setSheet("issues"); return; }
    setBusy(true);
    try { await api.saveHallLayout({ hallId: hall.id, draft, onCreatedTable: (clientId, id) => {
      setDraft((current) => ({ ...current, tables: current.tables.map((t) => t.id === clientId ? { ...t, id, isNew: false } : t) }));
      setSelected((selected) => selected === clientId ? id : selected);
      setHistory([]);
    } }); onClose(); }
    catch (caught) { setSaved(Boolean(caught.saved)); setError(caught.message); setServerIssues(caught.violations || []); setSheet("issues"); }
    finally { setBusy(false); }
  }
  const cancel = useCallback(() => { if (busy) return; if (saved) onClose(); else if (count) setDiscard(true); else onClose(); }, [busy, saved, count, onClose]);
  useEffect(() => { cancelEditorRef.current = cancel; return () => { cancelEditorRef.current = null; }; }, [cancelEditorRef, cancel]);
  if (saved) return <View style={s.list}><Title>План сохранён</Title><Lead>{error}</Lead><Button label="Закрыть редактор" onPress={onClose} /></View>;
  return <View style={s.editor} pointerEvents={busy ? "none" : "auto"}>
    <View style={s.editHeader}>
      <Action label="Отмена" onPress={cancel} disabled={busy} />
      <Pressable accessibilityRole="button" accessibilityLabel="Свойства зала" onPress={() => setSheet("hall")} style={s.flex}><Text numberOfLines={1} style={[s.bold, s.center]}>{draft.name}</Text><Text style={[s.meta, s.center]}>{count ? `${count} изменений` : "Нет изменений"}</Text></Pressable>
      <Action label={busy ? "Сохраняем…" : saved ? "Сохранено" : "Сохранить"} accent disabled={busy || saved || !count} onPress={save} />
    </View>
    <Text style={s.mode}><Text style={s.bold}>Режим правки.</Text> Тяните столы, тап — свойства. План появится у сотрудников после сохранения.</Text>
    <ScrollView contentContainerStyle={s.canvasBody}>
      <FloorPlan draft={draft} viewportRef={viewport} selectedId={selectedId} editable={!busy && !saved} grid={grid} onDragStart={checkpoint} onMove={(id, fields) => patch(id, fields, false)} onSelect={(id) => { setSelected(id); if (id && !saved) setSheet("properties"); }} />
      {!hall.layout && draft.tables.length && draft.tables.every((t) => !t.positionX && !t.positionY) ? <Text style={s.meta}>План ещё не рисовали: столы находятся в нулевой точке. Расставьте их перед сохранением.</Text> : null}
      {error ? <Text accessibilityRole="alert" style={s.error}>{error}</Text> : null}
      {issues.length ? <Action label={`Проверка: ${issues.length} ошибок`} onPress={() => setSheet("issues")} /> : <Text style={s.meta}>План {draft.width} × {draft.height} · {draft.tables.filter((t) => !t.deleted).length} столов</Text>}
      <Text style={s.meta}>Выберите объект:</Text>
      <View style={s.wrap}>{[...draft.tables.filter((t) => !t.deleted), ...draft.objects].map((item) => <Action key={item.id} label={item.type ? INTERIOR.find(([type]) => type === item.type)?.[1] || item.type : `Стол ${item.number}`} accent={selectedId === item.id} onPress={() => { setSelected(item.id); setSheet("properties"); }} />)}</View>
    </ScrollView>
    <View style={s.tools}>
      <Action label="+ Добавить" accent disabled={busy || saved} onPress={() => setSheet("add")} />
      <Action label="Выровнять" disabled={busy || !selected} onPress={() => { if (selected) patch(selected.id, { positionX: Math.round(selected.positionX / 20) * 20, positionY: Math.round(selected.positionY / 20) * 20 }); }} />
      <Action label="↶ Назад" disabled={busy || !history.length} onPress={() => { setDraft(history[history.length - 1]); setHistory(history.slice(0, -1)); setServerIssues([]); setError(""); }} />
      <Action label="# Сетка" accent={grid} onPress={() => setGrid(!grid)} />
    </View>
    <Modal visible={Boolean(sheet)} transparent animationType="slide" onRequestClose={() => { if (!busy) setSheet(null); }}>
      <View style={s.scrim}><SafeAreaView style={s.sheet}>
        <View style={s.grab} />
        <View style={s.header}><Text style={s.sheetTitle}>{sheet === "add" ? "Добавить на план" : sheet === "hall" ? "Свойства зала" : sheet === "issues" ? "Проверка плана" : selectedTable ? `Стол ${selectedTable.number}` : "Предмет интерьера"}</Text><Action label="✕" disabled={busy} onPress={() => setSheet(null)} /></View>
        <ScrollView contentContainerStyle={s.sheetBody} keyboardShouldPersistTaps="handled">
          {sheet === "hall" ? <>
            <Text style={s.bold}>{draft.name}</Text>
            <View style={s.row}><NumberField label="Ширина плана" value={draft.width} onChange={(width) => change({ ...draft, width })} /><NumberField label="Высота плана" value={draft.height} onChange={(height) => change({ ...draft, height })} /></View>
            <Text style={s.meta}>Переименование и скрытие зала пока недоступны.</Text>
          </> : null}
          {sheet === "add" ? <>
            <Text style={s.bold}>Стол · следующий номер {nextNumber(draft.tables)}</Text>
            <Options choices={SHAPES} value={newShape} onChange={setNewShape} />
            <Seats value={newSeats} onChange={setNewSeats} />
            <Button label="Добавить стол" onPress={() => addTable()} />
            <Text style={s.bold}>Интерьер · не влияет на смену</Text>
            <View style={s.wrap}>{INTERIOR.map(([type, label]) => <Action key={type} label={`+ ${label}`} onPress={() => {
              const id = newId();
              change({ ...draft, objects: [...draft.objects, { id, type, positionX: Math.round(draft.width / 2), positionY: Math.round(draft.height / 2), width: ["COLUMN", "PLANT"].includes(type) ? 40 : 140, height: ["WALL", "WINDOW"].includes(type) ? 12 : 40, rotation: 0 }] }); setSelected(id); setSheet("properties");
            }} />)}</View>
          </> : null}
          {sheet === "properties" && selected ? <>
            {selectedTable ? <>
              <Field label="Номер" value={String(selected.number)} keyboard="number-pad" onChangeText={(number) => patch(selected.id, { number })} maxLength={20} />
              <Seats value={selected.seats} onChange={(seats) => patch(selected.id, { seats })} />
              <Options choices={SHAPES} value={selected.shape} onChange={(shape) => patch(selected.id, { shape })} />
            </> : <>
              <Text style={s.bold}>{INTERIOR.find(([type]) => type === selected.type)?.[1]}</Text>
              <View style={s.row}><NumberField label="Ширина" value={selected.width} onChange={(width) => patch(selected.id, { width })} /><NumberField label="Высота" value={selected.height} onChange={(height) => patch(selected.id, { height })} /></View>
            </>}
            <View style={s.row}><NumberField label="Координата X" value={selected.positionX} onChange={(positionX) => patch(selected.id, { positionX })} /><NumberField label="Координата Y" value={selected.positionY} onChange={(positionY) => patch(selected.id, { positionY })} /></View>
            <Text style={s.bold}>Поворот</Text><Options choices={[[0, "0°"], [90, "90°"]]} value={selected.rotation || 0} onChange={(rotation) => patch(selected.id, { rotation })} />
            {selectedTable && (selected.assigneeId || selected.runnerId) ? <Text style={s.note}>Закреплён в смене: {data.people.filter((p) => [selected.assigneeId, selected.runnerId].includes(p.id)).map((p) => p.fullName).join(", ") || "сотрудник"}. Удаление доступно после смены.</Text> : null}
            <View style={s.wrap}>
              {selectedTable ? <><Action label="Дублировать" onPress={() => addTable(selectedTable)} /><Action label={selected.active ? "Выключить" : "Включить"} onPress={() => patch(selected.id, { active: !selected.active })} /></> : null}
              <Action label="Удалить" disabled={Boolean(selectedTable && !selectedTable.isNew)} onPress={() => { change(selectedTable ? { ...draft, tables: draft.tables.map((t) => t.id === selected.id ? { ...t, deleted: true } : t) } : { ...draft, objects: draft.objects.filter((o) => o.id !== selected.id) }); setSelected(null); setSheet(null); }} />
            </View>
            {selectedTable && !selectedTable.isNew ? <Text style={s.meta}>Существующий стол можно выключить. Удаление пока недоступно.</Text> : null}
          </> : null}
          {sheet === "issues" ? <>
            {error ? <Text accessibilityRole="alert" style={s.error}>{error}</Text> : null}
            {issues.map((issue, index) => <Pressable key={index} accessibilityRole="button" onPress={() => { setSelected(issue.id); setSheet(issue.id ? "properties" : "hall"); }} style={s.issue}><Text style={s.error}>! {issue.message}</Text></Pressable>)}
            {serverIssues.map((issue, index) => <Text key={index} style={s.error}>{issue.message || issue.reason || String(issue.field || "Проверьте план")}</Text>)}
            {!issues.length && !error ? <Lead>Ошибок не найдено.</Lead> : null}
            <Text style={s.meta}>{saved ? "Сохранено на сервере. Повторная отправка отключена." : "Сохранение не завершено. Ваши правки остаются в редакторе."}</Text>
          </> : null}
          <Button label="Готово" disabled={busy} onPress={() => setSheet(null)} />
        </ScrollView>
      </SafeAreaView></View>
    </Modal>
    <Modal visible={discard} transparent animationType="fade" onRequestClose={() => setDiscard(false)}><View style={s.scrim}><View style={s.confirm}><Title>Выйти без сохранения?</Title><Lead>Изменения плана будут потеряны.</Lead><Button label="Продолжить правку" onPress={() => setDiscard(false)} /><Button tone="ghost" label="Отменить изменения" onPress={onClose} /></View></View></Modal>
  </View>;
}

function NumberField({ label, value, onChange }) {
  return <View style={s.flex}><Field label={label} value={String(value)} keyboard="numeric" onChangeText={(text) => { const number = Number(text.replace(",", ".")); if (Number.isFinite(number)) onChange(number); }} maxLength={6} /></View>;
}
function Seats({ value, onChange }) {
  return <View style={s.row}><View style={s.flex}><Text style={s.bold}>Мест</Text><Text style={s.meta}>от 1 до 12</Text></View><Action label="−" disabled={value <= 1} onPress={() => onChange(value - 1)} /><Text style={s.bold}>{value}</Text><Action label="+" disabled={value >= 12} onPress={() => onChange(value + 1)} /></View>;
}
function Options({ choices, value, onChange }) {
  return <View style={s.wrap}>{choices.map(([key, label]) => <Action key={key} label={label} accent={key === value} onPress={() => onChange(key)} />)}</View>;
}
function Action({ label, onPress, accent, disabled }) {
  return <Pressable accessibilityRole="button" accessibilityLabel={label} accessibilityState={{ disabled: Boolean(disabled), selected: Boolean(accent) }} disabled={disabled} onPress={onPress} style={[s.action, accent && s.accent, disabled && s.disabled]}><Text style={s.actionText}>{label}</Text></Pressable>;
}
const s = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.paper },
  editor: { flex: 1, backgroundColor: colors.paper, width: "100%", maxWidth: 600, alignSelf: "center" },
  list: { padding: 20, gap: 16, maxWidth: 600, width: "100%", alignSelf: "center" },
  header: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", gap: 8 },
  editHeader: { flexDirection: "row", alignItems: "center", gap: 8, padding: 12, borderBottomWidth: 2, borderColor: colors.ink },
  flex: { flex: 1 }, center: { textAlign: "center" },
  bold: { fontFamily: font.bold, fontSize: 15, color: colors.ink },
  meta: { fontFamily: font.regular, fontSize: 12, lineHeight: 18, color: colors.mute },
  mode: { padding: 12, backgroundColor: "#F8EDB4", fontFamily: font.regular, fontSize: 13, lineHeight: 19 },
  hall: { flexDirection: "row", alignItems: "center", gap: 12, padding: 10, backgroundColor: colors.white, borderWidth: 2, borderColor: colors.ink, borderRadius: 10, boxShadow: "3px 3px 0 #141414" },
  thumb: { width: 95 }, tag: { fontFamily: font.bold, fontSize: 11, marginTop: 6, color: colors.mute },
  canvasBody: { padding: 12, gap: 12 },
  tools: { flexDirection: "row", flexWrap: "wrap", justifyContent: "space-between", gap: 6, padding: 10, borderTopWidth: 2, borderColor: colors.ink },
  action: { minHeight: 44, paddingHorizontal: 10, borderWidth: 2, borderColor: colors.ink, borderRadius: 8, backgroundColor: colors.white, alignItems: "center", justifyContent: "center" },
  actionText: { fontFamily: font.bold, fontSize: 12, color: colors.ink },
  accent: { backgroundColor: colors.accent }, disabled: { opacity: 0.4 },
  wrap: { flexDirection: "row", flexWrap: "wrap", gap: 8 }, row: { flexDirection: "row", alignItems: "center", gap: 12 },
  scrim: { flex: 1, justifyContent: "flex-end", backgroundColor: "rgba(20,20,20,0.4)" },
  sheet: { maxHeight: "90%", backgroundColor: colors.paper, borderTopLeftRadius: 22, borderTopRightRadius: 22, borderWidth: 2, borderColor: colors.ink, padding: 16, gap: 12, width: "100%", maxWidth: 600, alignSelf: "center" },
  sheetBody: { gap: 16, paddingBottom: 20 }, sheetTitle: { flex: 1, fontFamily: font.bold, fontSize: 23, color: colors.ink },
  grab: { width: 40, height: 5, borderRadius: 3, backgroundColor: "#B8AD99", alignSelf: "center" },
  issue: { padding: 14, borderWidth: 2, borderColor: colors.danger, borderRadius: 8, backgroundColor: "#FFF0ED" },
  note: { fontFamily: font.regular, fontSize: 13, lineHeight: 20, backgroundColor: "#F8EDB4", padding: 12, borderRadius: 8 },
  error: { fontFamily: font.semibold, color: colors.danger, fontSize: 14, lineHeight: 21 },
  confirm: { backgroundColor: colors.paper, padding: 24, gap: 16, borderRadius: 16 },
});
