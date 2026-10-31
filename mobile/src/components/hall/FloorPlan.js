import { useEffect, useMemo, useRef, useState } from "react";
import { PanResponder, Pressable, StyleSheet, Text, View } from "react-native";
import Svg, { Circle, G, Line, Rect, Text as SvgText } from "react-native-svg";
import { GRID, INTERIOR, tableSize } from "../../domain/layout";
import { colors, statusTint, type as font } from "../../theme";

const clamp = (n, low, high) => Math.max(low, Math.min(high, n));
const distance = (touches) => Math.hypot(touches[0].pageX - touches[1].pageX, touches[0].pageY - touches[1].pageY);

// A single responder keeps drag, pan and two-finger zoom in the same coordinate space.
export function FloorPlan({ draft, selectedId, onSelect, onMove, onDragStart, viewportRef, grid = false, editable = false, miniature = false }) {
  const [width, setWidth] = useState(340);
  const height = miniature ? 110 : 390;
  const planWidth = Number.isFinite(draft.width) && draft.width >= 200 ? draft.width : 1000;
  const planHeight = Number.isFinite(draft.height) && draft.height >= 200 ? draft.height : 800;
  const fit = Math.min(width / planWidth, height / planHeight) * 0.92;
  const [camera, setCamera] = useState({ zoom: 1, x: 0, y: 0 });
  const [guide, setGuide] = useState(null);
  const [origin, setOrigin] = useState(null);
  const gesture = useRef(null);
  const handlersRef = useRef(null);
  const scale = fit * camera.zoom;
  const ox = (width - planWidth * scale) / 2 + camera.x;
  const oy = (height - planHeight * scale) / 2 + camera.y;
  useEffect(() => {
    if (viewportRef) viewportRef.current = { x: (width / 2 - ox) / scale, y: (height / 2 - oy) / scale };
  }, [viewportRef, width, height, ox, oy, scale]);
  const tables = draft.tables.filter((t) => !t.deleted);
  const objects = draft.objects || [];
  function hit(x, y) {
    return [...objects, ...tables].reverse().find((item) => {
      const size = item.type
        ? { width: item.rotation === 90 ? item.height : item.width, height: item.rotation === 90 ? item.width : item.height }
        : tableSize(item);
      return x >= item.positionX && x <= item.positionX + size.width && y >= item.positionY && y <= item.positionY + size.height;
    });
  }
  function zoom(next) {
    setCamera((c) => ({ ...c, zoom: clamp(next, 1, 4) }));
  }
  const handlers = {
    onStartShouldSetPanResponder: () => !miniature,
    onMoveShouldSetPanResponder: () => !miniature,
    onPanResponderGrant: (event) => {
      const { locationX, locationY, touches } = event.nativeEvent;
      const item = hit((locationX - ox) / scale, (locationY - oy) / scale);
      gesture.current = { camera, item, scale, pinch: touches.length > 1 ? distance(touches) : null, moved: false };
    },
    onPanResponderMove: (event, movement) => {
      const start = gesture.current;
      if (!start) return;
      const touches = event.nativeEvent.touches;
      if (touches.length > 1) {
        start.pinch ??= distance(touches);
        start.moved = true;
        setCamera({ ...start.camera, zoom: clamp(start.camera.zoom * distance(touches) / start.pinch, 1, 4) });
        return;
      }
      if (start.pinch) return;
      if (Math.abs(movement.dx) + Math.abs(movement.dy) < 5) return;
      if (!start.moved && editable && start.item) { onDragStart?.(); setOrigin(start.item); }
      start.moved = true;
      if (editable && start.item) {
        const item = start.item;
        const size = item.type ? { width: item.rotation === 90 ? item.height : item.width, height: item.rotation === 90 ? item.width : item.height } : tableSize(item);
        let x = item.positionX + movement.dx / start.scale;
        let y = item.positionY + movement.dy / start.scale;
        if (grid) { x = Math.round(x / GRID) * GRID; y = Math.round(y / GRID) * GRID; }
        const aligned = tables.find((t) => t.id !== item.id && !item.type && Math.abs(t.positionX - x) < 8);
        if (aligned) x = aligned.positionX;
        setGuide(aligned ? x : null);
        onMove?.(item.id, { positionX: Math.round(clamp(x, 0, planWidth - size.width)), positionY: Math.round(clamp(y, 0, planHeight - size.height)) });
      } else {
        setCamera({ ...start.camera, x: start.camera.x + movement.dx, y: start.camera.y + movement.dy });
      }
    },
    onPanResponderRelease: () => {
      if (gesture.current && !gesture.current.moved) onSelect?.(gesture.current.item?.id ?? null);
      gesture.current = null;
      setGuide(null);
      setOrigin(null);
    },
    onPanResponderTerminate: () => { gesture.current = null; setGuide(null); setOrigin(null); },
    onPanResponderTerminationRequest: () => false,
  };
  useEffect(() => { handlersRef.current = handlers; });
  // Keep one responder throughout a drag so React renders do not reset dx/dy.
  // PanResponder invokes the ref-backed delegates only in response to gestures.
  const responder = useMemo(() => PanResponder.create(Object.fromEntries([
    "onStartShouldSetPanResponder", "onMoveShouldSetPanResponder", "onPanResponderGrant",
    "onPanResponderMove", "onPanResponderRelease", "onPanResponderTerminate",
    "onPanResponderTerminationRequest",
    // eslint-disable-next-line react-hooks/refs -- These delegates run on gestures, never during creation.
  ].map((name) => [name, (...args) => handlersRef.current?.[name](...args)]))), []);
  const detail = !miniature && scale >= 0.65;
  return (
    <View>
      <View style={[styles.floor, { height }]} onLayout={(e) => setWidth(e.nativeEvent.layout.width)} {...(!miniature ? responder.panHandlers : {})}>
        <Svg width="100%" height={height} pointerEvents="none">
          <G transform={`translate(${ox}, ${oy}) scale(${scale})`}>
            <Rect width={planWidth} height={planHeight} fill="#F3EAD9" stroke="#B8AD99" strokeWidth={2} />
            {grid ? Array.from({ length: Math.ceil(Math.min(planWidth, 5000) / GRID) }, (_, i) => <Line key={`x${i}`} x1={i * GRID} x2={i * GRID} y1={0} y2={planHeight} stroke="#DDD2BE" />) : null}
            {grid ? Array.from({ length: Math.ceil(Math.min(planHeight, 5000) / GRID) }, (_, i) => <Line key={`y${i}`} y1={i * GRID} y2={i * GRID} x1={0} x2={planWidth} stroke="#DDD2BE" />) : null}
            {objects.map((item) => {
              const w = item.rotation === 90 ? item.height : item.width;
              const h = item.rotation === 90 ? item.width : item.height;
              return <G key={item.id} transform={`translate(${item.positionX},${item.positionY})`}>
                <Rect width={w} height={h} rx={item.type === "PLANT" ? Math.min(w, h) / 2 : 3} fill={item.type === "PLANT" ? colors.greenSoft : item.type === "WALL" ? colors.ink : item.type === "WINDOW" ? "#CCE7F6" : "#D9D2C4"} stroke={selectedId === item.id ? colors.danger : colors.ink} strokeWidth={selectedId === item.id ? 4 : 2} />
                {!miniature && h >= 20 ? <SvgText x={w / 2} y={h / 2 + 5} textAnchor="middle" fontFamily={font.bold} fontSize={12} fill={item.type === "WALL" ? colors.white : colors.ink}>{INTERIOR.find(([type]) => type === item.type)?.[1]}</SvgText> : null}
              </G>;
            })}
            {tables.map((table) => {
              const size = tableSize(table);
              const tint = table.active === false || table.foreign ? "#D4CBBE" : statusTint[table.status] || colors.accent;
              return <G key={table.id} transform={`translate(${table.positionX},${table.positionY})`}>
                {detail && !table.foreign ? Array.from({ length: Math.min(12, table.seats || 0) }, (_, i) => {
                  const angle = (i / table.seats) * Math.PI * 2;
                  return <Circle key={i} cx={size.width / 2 + (size.width / 2 + 7) * Math.cos(angle)} cy={size.height / 2 + (size.height / 2 + 7) * Math.sin(angle)} r={5} fill={colors.white} stroke={colors.ink} strokeWidth={2} />;
                }) : null}
                <Rect width={size.width} height={size.height} rx={table.shape === "ROUND" ? 32 : 7} fill={tint} stroke={selectedId === table.id ? colors.danger : colors.ink} strokeWidth={selectedId === table.id ? 5 : 2} strokeDasharray={table.active === false ? "6 4" : undefined} />
                <SvgText x={size.width / 2} y={size.height / 2 + 7} textAnchor="middle" fontFamily={font.bold} fontSize={24} fontWeight="bold" fill={colors.ink}>{table.number}</SvgText>
              </G>;
            })}
            {origin && !origin.type ? <Rect x={origin.positionX} y={origin.positionY} width={tableSize(origin).width} height={tableSize(origin).height} rx={7} fill="none" stroke={colors.ink} strokeDasharray="8 6" strokeWidth={2} /> : null}
            {guide !== null ? <Line x1={guide} x2={guide} y1={0} y2={planHeight} stroke={colors.taskSoft} strokeWidth={4} /> : null}
          </G>
        </Svg>
      </View>
      {!miniature ? <View style={styles.controls}>
        <Text style={styles.hint}>Двигайте план · два пальца — масштаб</Text>
        <Pressable accessibilityRole="button" accessibilityLabel="Уменьшить план" onPress={() => zoom(camera.zoom - 0.5)} style={styles.zoom}><Text>−</Text></Pressable>
        <Pressable accessibilityRole="button" accessibilityLabel="Показать весь план" onPress={() => setCamera({ zoom: 1, x: 0, y: 0 })} style={styles.zoom}><Text>{Math.round(camera.zoom * 100)}%</Text></Pressable>
        <Pressable accessibilityRole="button" accessibilityLabel="Приблизить план" onPress={() => zoom(camera.zoom + 0.5)} style={styles.zoom}><Text>+</Text></Pressable>
      </View> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  floor: { backgroundColor: "#E8DECB", overflow: "hidden", borderWidth: 2, borderColor: colors.ink, borderRadius: 10, touchAction: "none" },
  controls: { flexDirection: "row", alignItems: "center", gap: 5, marginTop: 6 },
  hint: { flex: 1, fontFamily: font.regular, fontSize: 11, color: colors.mute },
  zoom: { minWidth: 40, minHeight: 44, alignItems: "center", justifyContent: "center", borderWidth: 1, borderColor: colors.ink, borderRadius: 7, backgroundColor: colors.white },
});
