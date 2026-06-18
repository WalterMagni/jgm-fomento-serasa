"use client";

import { useEffect, useMemo } from "react";
import { MapContainer, TileLayer, CircleMarker, Tooltip, Polyline, useMap } from "react-leaflet";
import "leaflet/dist/leaflet.css";

function FitBounds({ points }: { points: [number, number][] }) {
  const map = useMap();
  useEffect(() => {
    if (points.length === 1) {
      map.setView(points[0], 8);
    } else if (points.length > 1) {
      map.fitBounds(points, { padding: [50, 50], maxZoom: 9 });
    }
  }, [map, points]);
  return null;
}

export type GeoPoint = {
  label: string;
  city?: string | null;
  lat?: number | null;
  lng?: number | null;
  color: string;
};

type Props = {
  points: GeoPoint[];
};

function isValid(point: GeoPoint): point is GeoPoint & { lat: number; lng: number } {
  return typeof point.lat === "number" && typeof point.lng === "number";
}

export default function PaymentPlaceMap({ points }: Props) {
  const valid = useMemo(() => points.filter(isValid), [points]);

  const center = useMemo<[number, number]>(() => {
    if (valid.length === 0) return [-14.235, -51.925]; // centro do Brasil
    const lat = valid.reduce((sum, p) => sum + p.lat, 0) / valid.length;
    const lng = valid.reduce((sum, p) => sum + p.lng, 0) / valid.length;
    return [lat, lng];
  }, [valid]);

  // Agrupa pontos que caem na mesma coordenada (mesma cidade) para empilhar as cores.
  const groups = useMemo(() => {
    const map = new Map<string, { center: [number, number]; members: GeoPoint[] }>();
    for (const p of valid) {
      const key = `${p.lat.toFixed(4)},${p.lng.toFixed(4)}`;
      const g = map.get(key);
      if (g) g.members.push(p);
      else map.set(key, { center: [p.lat, p.lng], members: [p] });
    }
    return Array.from(map.values());
  }, [valid]);

  if (valid.length === 0) {
    return (
      <div className="flex h-full min-h-[260px] items-center justify-center rounded-lg border border-dashed border-border-light bg-gray-50 text-xs text-gray-500 dark:border-border-dark dark:bg-white/5">
        Sem coordenadas para exibir no mapa.
      </div>
    );
  }

  return (
    <MapContainer
      center={center}
      zoom={valid.length === 1 ? 9 : 6}
      scrollWheelZoom={false}
      className="h-full min-h-[260px] w-full rounded-lg"
      style={{ zIndex: 0 }}
    >
      <TileLayer
        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
        url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
      />
      <FitBounds points={groups.map((g) => g.center)} />
      {groups.length >= 2 ? (
        <Polyline
          positions={groups.map((g) => g.center)}
          pathOptions={{ color: "#612035", weight: 2, dashArray: "6 6", opacity: 0.6 }}
        />
      ) : null}
      {groups.map((group) =>
        // Pontos na mesma coordenada (mesma cidade) viram anéis concêntricos de cada cor.
        group.members.map((p, i) => (
          <CircleMarker
            key={p.label}
            center={group.center}
            radius={11 - i * 3.5}
            pathOptions={{ color: "#fff", weight: 1.5, fillColor: p.color, fillOpacity: 1 }}
          >
            {i === 0 ? (
              <Tooltip direction="top" offset={[0, -10]} opacity={1}>
                {group.members.map((m) => (
                  <span key={m.label} className="block">
                    <span className="text-xs font-bold" style={{ color: m.color }}>● </span>
                    <span className="text-xs font-bold">{m.label}</span>
                    {m.city ? <span className="text-[11px]"> · {m.city}</span> : null}
                  </span>
                ))}
              </Tooltip>
            ) : null}
          </CircleMarker>
        )),
      )}
    </MapContainer>
  );
}
