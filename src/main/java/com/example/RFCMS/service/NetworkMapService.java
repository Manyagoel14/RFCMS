package com.example.RFCMS.service;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class NetworkMapService {

    private final MongoTemplate mongoTemplate;
    private final RailwayDijkstra railwayDijkstra;
    private static final Duration CACHE_TTL = Duration.ofSeconds(30);
    private volatile Map<String, LatLng> cachedCoords = Map.of();
    private volatile Instant coordsLoadedAt = Instant.EPOCH;
    private volatile List<Document> cachedGraphEdges = List.of();
    private volatile Instant graphLoadedAt = Instant.EPOCH;

    public NetworkMapService(MongoTemplate mongoTemplate, RailwayDijkstra railwayDijkstra) {
        this.mongoTemplate = mongoTemplate;
        this.railwayDijkstra = railwayDijkstra;
    }

    public String buildNetworkHtml() {
        Map<String, LatLng> coords = loadStationCoords();
        List<Document> edges = loadGraphEdges();

        // Collect stations referenced by graph too (so they show even if isolated)
        Set<String> stations = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        stations.addAll(coords.keySet());
        for (Document e : edges) {
            stations.add(asString(e.get("source")));
            stations.add(asString(e.get("destination")));
        }
        stations.removeIf(s -> s == null || s.isBlank());

        // Map center
        LatLng center = average(coords.values()).orElse(new LatLng(20.5937, 78.9629)); // India centroid-ish fallback

        // Build JS data
        String stationsJs = stations.stream()
                .map(s -> {
                    LatLng ll = coords.get(s);
                    if (ll == null) return null;
                    return "{name:" + jsStr(s) + ", lat:" + ll.lat + ", lng:" + ll.lng + "}";
                })
                .filter(Objects::nonNull)
                .reduce("[", (acc, cur) -> acc.equals("[") ? "[" + cur : acc + "," + cur, (a, b) -> a + b) + "]";

        String edgesJs = edges.stream()
                .map(e -> {
                    String src = asString(e.get("source"));
                    String dst = asString(e.get("destination"));
                    Integer dist = asInt(e.get("distance"));
                    if (src == null || dst == null) return null;
                    LatLng a = coords.get(src);
                    LatLng b = coords.get(dst);
                    if (a == null || b == null) return null; // skip if no coordinates
                    return "{src:" + jsStr(src) + ", dst:" + jsStr(dst) + ", dist:" + (dist == null ? "null" : dist) +
                            ", path:[[" + a.lat + "," + a.lng + "],[" + b.lat + "," + b.lng + "]]}";
                })
                .filter(Objects::nonNull)
                .reduce("[", (acc, cur) -> acc.equals("[") ? "[" + cur : acc + "," + cur, (a, b) -> a + b) + "]";

        return """
                <!doctype html>
                <html>
                  <head>
                    <meta charset="utf-8" />
                    <meta name="viewport" content="width=device-width, initial-scale=1" />
                    <title>RFCMS Railway Network</title>
                    <link
                      rel="stylesheet"
                      href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"
                      integrity="sha256-p4NxAoJBhIIN+hmNHrzRCf9tD/miZyoHS5obTRR9BMY="
                      crossorigin=""
                    />
                    <style>
                      html, body { height: 100%%; margin: 0; }
                      #map { height: 100%%; width: 100%%; }
                      .legend {
                        position: absolute;
                        top: 12px;
                        left: 12px;
                        background: rgba(255,255,255,0.95);
                        padding: 10px 12px;
                        border-radius: 8px;
                        box-shadow: 0 2px 10px rgba(0,0,0,0.15);
                        font-family: system-ui, -apple-system, Segoe UI, Roboto, Arial, sans-serif;
                        font-size: 13px;
                        max-width: 340px;
                      }
                      .legend code { background: #f4f4f4; padding: 1px 4px; border-radius: 4px; }
                    </style>
                  </head>
                  <body>
                    <div id="map"></div>
                    <div class="legend">
                      <div><strong>RFCMS network</strong></div>
                      <div>Stations plotted from <code>lat_long</code></div>
                      <div>Edges drawn from <code>graph</code> (only when both endpoints have coords)</div>
                    </div>
                    <script
                      src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"
                      integrity="sha256-20nQCchB9co0qIjJZRGuk2/Z9VM+kNiyxNV1lvTlZBo="
                      crossorigin=""
                    ></script>
                    <script>
                      const map = L.map('map', { preferCanvas: true }).setView([%f, %f], 5);
                      L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                        maxZoom: 18,
                        attribution: '&copy; OpenStreetMap contributors'
                      }).addTo(map);

                      const stations = %s;
                      const edges = %s;

                      const bounds = L.latLngBounds([]);
                      for (const s of stations) {
                        const m = L.circleMarker([s.lat, s.lng], {
                          radius: 6,
                          color: '#0b4',
                          weight: 2,
                          fillColor: '#1f7',
                          fillOpacity: 0.9
                        }).addTo(map);
                        m.bindTooltip(s.name, { direction: 'top', sticky: true });
                        bounds.extend([s.lat, s.lng]);
                      }

                      for (const e of edges) {
                        const line = L.polyline(e.path, { color: '#2a6', weight: 3, opacity: 0.8 }).addTo(map);
                        const label = e.dist != null ? `${e.src} → ${e.dst} (${e.dist} km)` : `${e.src} → ${e.dst}`;
                        line.bindTooltip(label, { sticky: true });
                      }

                      if (bounds.isValid()) {
                        map.fitBounds(bounds.pad(0.2));
                      }
                    </script>
                  </body>
                </html>
                """.formatted(center.lat, center.lng, stationsJs, edgesJs);
    }

    public String buildTrackingHtml(String cn, String status, String currentStation) {
        Map<String, LatLng> coords = loadStationCoords();
        List<Document> edges = loadGraphEdges();

        // Collect stations referenced by graph too
        Set<String> stations = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        stations.addAll(coords.keySet());
        for (Document e : edges) {
            stations.add(asString(e.get("source")));
            stations.add(asString(e.get("destination")));
        }
        stations.removeIf(s -> s == null || s.isBlank());

        LatLng center = average(coords.values()).orElse(new LatLng(20.5937, 78.9629));

        String stationsJs = stations.stream()
                .map(s -> {
                    LatLng ll = coords.get(s);
                    if (ll == null) return null;
                    return "{name:" + jsStr(s) + ", lat:" + ll.lat + ", lng:" + ll.lng + "}";
                })
                .filter(Objects::nonNull)
                .reduce("[", (acc, cur) -> acc.equals("[") ? "[" + cur : acc + "," + cur, (a, b) -> a + b) + "]";

        String edgesJs = edges.stream()
                .map(e -> {
                    String src = asString(e.get("source"));
                    String dst = asString(e.get("destination"));
                    Integer dist = asInt(e.get("distance"));
                    if (src == null || dst == null) return null;
                    LatLng a = coords.get(src);
                    LatLng b = coords.get(dst);
                    if (a == null || b == null) return null;
                    return "{src:" + jsStr(src) + ", dst:" + jsStr(dst) + ", dist:" + (dist == null ? "null" : dist) +
                            ", path:[[" + a.lat + "," + a.lng + "],[" + b.lat + "," + b.lng + "]]}";
                })
                .filter(Objects::nonNull)
                .reduce("[", (acc, cur) -> acc.equals("[") ? "[" + cur : acc + "," + cur, (a, b) -> a + b) + "]";

        LatLng trainPos = currentStation != null ? coords.get(currentStation) : null;
        String trainJs = trainPos == null
                ? "null"
                : "{cn:" + jsStr(cn) + ", status:" + jsStr(status == null ? "UNKNOWN" : status) +
                ", station:" + jsStr(currentStation) + ", lat:" + trainPos.lat + ", lng:" + trainPos.lng + "}";

        return """
                <!doctype html>
                <html>
                  <head>
                    <meta charset="utf-8" />
                    <meta name="viewport" content="width=device-width, initial-scale=1" />
                    <title>RFCMS Tracking Map</title>
                    <link
                      rel="stylesheet"
                      href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"
                      integrity="sha256-p4NxAoJBhIIN+hmNHrzRCf9tD/miZyoHS5obTRR9BMY="
                      crossorigin=""
                    />
                    <style>
                      html, body { height: 100%%; margin: 0; }
                      #map { height: 100%%; width: 100%%; }
                      .legend {
                        position: absolute;
                        top: 12px;
                        left: 12px;
                        background: rgba(255,255,255,0.95);
                        padding: 10px 12px;
                        border-radius: 8px;
                        box-shadow: 0 2px 10px rgba(0,0,0,0.15);
                        font-family: system-ui, -apple-system, Segoe UI, Roboto, Arial, sans-serif;
                        font-size: 13px;
                        max-width: 360px;
                      }
                      .legend code { background: #f4f4f4; padding: 1px 4px; border-radius: 4px; }
                      .train-icon {
                        font-size: 40px;
                        line-height: 40px;
                        width: 40px;
                        height: 40px;
                        text-align: center;
                        filter: drop-shadow(0 2px 2px rgba(0,0,0,0.25));
                      }
                    </style>
                  </head>
                  <body>
                    <div id="map"></div>
                    <div class="legend">
                      <div><strong>Tracking</strong></div>
                      <div>CN: <code>%s</code></div>
                      <div>Status: <code>%s</code></div>
                      <div>Current station: <code>%s</code></div>
                    </div>
                    <script
                      src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"
                      integrity="sha256-20nQCchB9co0qIjJZRGuk2/Z9VM+kNiyxNV1lvTlZBo="
                      crossorigin=""
                    ></script>
                    <script>
                      const map = L.map('map', { preferCanvas: true }).setView([%f, %f], 5);
                      L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                        maxZoom: 18,
                        attribution: '&copy; OpenStreetMap contributors'
                      }).addTo(map);

                      const stations = %s;
                      const edges = %s;
                      const train = %s;

                      const bounds = L.latLngBounds([]);
                      for (const s of stations) {
                        const m = L.circleMarker([s.lat, s.lng], {
                          radius: 5,
                          color: '#0b4',
                          weight: 2,
                          fillColor: '#1f7',
                          fillOpacity: 0.85
                        }).addTo(map);
                        m.bindTooltip(s.name, { direction: 'top', sticky: true });
                        bounds.extend([s.lat, s.lng]);
                      }

                      for (const e of edges) {
                        const line = L.polyline(e.path, { color: '#2a6', weight: 3, opacity: 0.55 }).addTo(map);
                        const label = e.dist != null ? `${e.src} → ${e.dst} (${e.dist} km)` : `${e.src} → ${e.dst}`;
                        line.bindTooltip(label, { sticky: true });
                      }

                      if (train) {
                        const icon = L.divIcon({
                          className: 'train-icon',
                          html: '🚆',
                          iconSize: [40, 40],
                          iconAnchor: [20, 20]
                        });
                        const tm = L.marker([train.lat, train.lng], { icon }).addTo(map);
                        tm.bindPopup(`<b>CN:</b> ${train.cn}<br/><b>Status:</b> ${train.status}<br/><b>Station:</b> ${train.station}`);
                        bounds.extend([train.lat, train.lng]);
                      }

                      if (bounds.isValid()) {
                        map.fitBounds(bounds.pad(0.2));
                      }
                    </script>
                  </body>
                </html>
                """.formatted(
                cn,
                status == null ? "UNKNOWN" : status,
                currentStation == null ? "UNKNOWN" : currentStation,
                center.lat, center.lng,
                stationsJs, edgesJs, trainJs
        );
    }

    public String buildTrackingHtmlDynamic(String cn,
                                          String status,
                                          List<String> routeStations,
                                          String wagonId,
                                          Double speedKmph,
                                          String departureIso) {
        Map<String, LatLng> coords = loadStationCoords();
        List<Document> edges = loadGraphEdges();

        // Stations for background
        Set<String> stations = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        stations.addAll(coords.keySet());
        for (Document e : edges) {
            stations.add(asString(e.get("source")));
            stations.add(asString(e.get("destination")));
        }
        stations.removeIf(s -> s == null || s.isBlank());

        LatLng center = average(coords.values()).orElse(new LatLng(20.5937, 78.9629));

        String stationsJs = stations.stream()
                .map(s -> {
                    LatLng ll = coords.get(s);
                    if (ll == null) return null;
                    return "{name:" + jsStr(s) + ", lat:" + ll.lat + ", lng:" + ll.lng + "}";
                })
                .filter(Objects::nonNull)
                .reduce("[", (acc, cur) -> acc.equals("[") ? "[" + cur : acc + "," + cur, (a, b) -> a + b) + "]";

        // Build route polyline points (skip stations without coords)
        String routePointsJs = routeStations.stream()
                .map(s -> {
                    LatLng ll = coords.get(s);
                    if (ll == null) return null;
                    return "{name:" + jsStr(s) + ", lat:" + ll.lat + ", lng:" + ll.lng + "}";
                })
                .filter(Objects::nonNull)
                .reduce("[", (acc, cur) -> acc.equals("[") ? "[" + cur : acc + "," + cur, (a, b) -> a + b) + "]";

        // Segment distances from graph:
        // prefer direct edge distance, else shortest distance (still graph-based),
        // else haversine(lat/long) so animation always works.
        String segDistJs = buildSegmentDistancesJs(routeStations, edges, coords);

        String meta = "{cn:" + jsStr(cn) +
                ", wagonId:" + jsStr(wagonId == null ? "" : wagonId) +
                ", status:" + jsStr(status == null ? "UNKNOWN" : status) +
                ", speedKmph:" + (speedKmph == null ? "null" : speedKmph) +
                ", departure:" + (departureIso == null ? "null" : jsStr(departureIso)) +
                "}";

        return """
                <!doctype html>
                <html>
                  <head>
                    <meta charset="utf-8" />
                    <meta name="viewport" content="width=device-width, initial-scale=1" />
                    <title>RFCMS Tracking Map</title>
                    <link
                      rel="stylesheet"
                      href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"
                      integrity="sha256-p4NxAoJBhIIN+hmNHrzRCf9tD/miZyoHS5obTRR9BMY="
                      crossorigin=""
                    />
                    <style>
                      html, body { height: 100%%; margin: 0; }
                      #map { height: 100%%; width: 100%%; }
                      .legend {
                        position: absolute;
                        top: 12px;
                        left: 12px;
                        background: rgba(255,255,255,0.95);
                        padding: 10px 12px;
                        border-radius: 8px;
                        box-shadow: 0 2px 10px rgba(0,0,0,0.15);
                        font-family: system-ui, -apple-system, Segoe UI, Roboto, Arial, sans-serif;
                        font-size: 13px;
                        max-width: 380px;
                      }
                      .legend code { background: #f4f4f4; padding: 1px 4px; border-radius: 4px; }
                      .train-icon {
                        font-size: 44px;
                        line-height: 44px;
                        width: 44px;
                        height: 44px;
                        text-align: center;
                        filter: drop-shadow(0 2px 2px rgba(0,0,0,0.25));
                      }
                    </style>
                  </head>
                  <body>
                    <div id="map"></div>
                    <div class="legend">
                      <div><strong>Tracking</strong></div>
                      <div>CN: <code id="cn"></code></div>
                      <div>Wagon: <code id="wagon"></code></div>
                      <div>Status: <code id="status"></code></div>
                      <div>Current station: <code id="station"></code></div>
                      <div>Speed: <code id="speed"></code></div>
                      <div>Progress: <code id="progress"></code></div>
                      <div>Travel: <code id="travel"></code></div>
                    </div>
                    <script
                      src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"
                      integrity="sha256-20nQCchB9co0qIjJZRGuk2/Z9VM+kNiyxNV1lvTlZBo="
                      crossorigin=""
                    ></script>
                    <script>
                      const map = L.map('map', { preferCanvas: true }).setView([%f, %f], 5);
                      L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                        maxZoom: 18,
                        attribution: '&copy; OpenStreetMap contributors'
                      }).addTo(map);

                      const stations = %s;
                      const routePts = %s; // [{name,lat,lng},...]
                      const segKm = %s;    // [km between i->i+1], null if unknown
                      const meta = %s;

                      document.getElementById('cn').textContent = meta.cn;
                      document.getElementById('wagon').textContent = meta.wagonId || '—';
                      document.getElementById('status').textContent = meta.status || 'UNKNOWN';
                      document.getElementById('speed').textContent = meta.speedKmph != null ? (meta.speedKmph + ' km/h') : '—';

                      const bounds = L.latLngBounds([]);
                      for (const s of stations) {
                        const m = L.circleMarker([s.lat, s.lng], {
                          radius: 4,
                          color: '#0b4',
                          weight: 2,
                          fillColor: '#1f7',
                          fillOpacity: 0.75
                        }).addTo(map);
                        bounds.extend([s.lat, s.lng]);
                      }

                      // draw route polyline if we have it
                      let routeLine = null;
                      if (routePts.length >= 2) {
                        const latlngs = routePts.map(p => [p.lat, p.lng]);
                        routeLine = L.polyline(latlngs, { color: '#1565c0', weight: 5, opacity: 0.75 }).addTo(map);
                        routeLine.bindTooltip('Route', { sticky: true });
                        for (const ll of latlngs) bounds.extend(ll);
                      } else if (routePts.length === 1) {
                        bounds.extend([routePts[0].lat, routePts[0].lng]);
                      }

                      const icon = L.divIcon({
                        className: 'train-icon',
                        html: '🚆',
                        iconSize: [44, 44],
                        iconAnchor: [22, 22]
                      });

                      // initial position: first route point (or center)
                      const start = routePts[0] || {lat:%f, lng:%f, name:'UNKNOWN'};
                      let trainMarker = L.marker([start.lat, start.lng], { icon }).addTo(map);

                      function clamp01(x){ return Math.max(0, Math.min(1, x)); }
                      function lerp(a,b,t){ return a + (b-a)*t; }

                      function segmentCumDistances() {
                        // returns cumulative distances [0, d0, d0+d1, ...]
                        const cum = [0];
                        for (let i=0;i<segKm.length;i++) {
                          const prev = cum[cum.length-1];
                          const dk = (segKm[i] == null ? 0 : segKm[i]);
                          cum.push(prev + dk);
                        }
                        return cum;
                      }

                      // Move only on the first leg (src -> station2)
                      const legPts = routePts.length >= 2 ? [routePts[0], routePts[1]] : routePts;
                      const legKm = (segKm && segKm.length > 0 && segKm[0] != null) ? segKm[0] : 0;
                      const totalKm = legKm || 0;
                      document.getElementById('travel').textContent = totalKm > 0 ? ('0 / ' + totalKm.toFixed(1) + ' km') : '—';

                      function computePositionKm(travelKm) {
                        if (legPts.length === 0) return null;
                        if (legPts.length === 1 || totalKm === 0) {
                          return { lat: legPts[0].lat, lng: legPts[0].lng, station: legPts[0].name };
                        }
                        const km = Math.max(0, Math.min(travelKm, totalKm));
                        const a = legPts[0];
                        const b = legPts[1];
                        const t = clamp01(km / totalKm);
                        return {
                          lat: lerp(a.lat, b.lat, t),
                          lng: lerp(a.lng, b.lng, t),
                          station: t >= 1 ? b.name : a.name
                        };
                      }

                      function update() {
                        // If we don't have speed/departure, keep train at first station
                        if (!meta.departure || meta.speedKmph == null || legPts.length === 0) {
                          document.getElementById('station').textContent = legPts[0]?.name || 'UNKNOWN';
                          document.getElementById('progress').textContent = '0%%';
                          document.getElementById('travel').textContent = totalKm > 0 ? ('0 / ' + totalKm.toFixed(1) + ' km') : '—';
                          if (!meta.departure) {
                            document.getElementById('status').textContent = (meta.status || 'UNKNOWN') + ' (waiting for actual_departure_time)';
                          }
                          return;
                        }
                        // Parse departure time deterministically:
                        // if it's an ISO string without timezone, treat as UTC to avoid local-time shifts.
                        let depStr = meta.departure;
                        if (typeof depStr === 'string' && !depStr.endsWith('Z') && !depStr.includes('+')) {
                          depStr = depStr + 'Z';
                        }
                        const depMs = Date.parse(depStr);
                        if (Number.isNaN(depMs)) {
                          document.getElementById('station').textContent = routePts[0]?.name || 'UNKNOWN';
                          document.getElementById('progress').textContent = '0%%';
                          document.getElementById('travel').textContent = totalKm > 0 ? ('0 / ' + totalKm.toFixed(1) + ' km') : '—';
                          return;
                        }
                        const nowMs = Date.now();
                        const elapsedH = Math.max(0, (nowMs - depMs) / (1000*60*60));
                        const travelKm = meta.speedKmph * elapsedH;
                        const pos = computePositionKm(travelKm);
                        if (!pos) return;
                        trainMarker.setLatLng([pos.lat, pos.lng]);
                        document.getElementById('station').textContent = pos.station || 'UNKNOWN';
                        if (totalKm > 0) {
                          const pct = Math.floor(Math.min(1, travelKm / totalKm) * 100);
                          document.getElementById('progress').textContent = pct + '%%';
                          document.getElementById('travel').textContent = Math.min(travelKm, totalKm).toFixed(1) + ' / ' + totalKm.toFixed(1) + ' km';
                        } else {
                          document.getElementById('progress').textContent = '—';
                          document.getElementById('travel').textContent = '—';
                        }
                      }

                      // Fit map once
                      if (bounds.isValid()) map.fitBounds(bounds.pad(0.2));

                      // Use requestAnimationFrame for smooth, reliable animation.
                      let lastUpdate = 0;
                      function tick(ts) {
                        if (ts - lastUpdate >= 200) {
                          update();
                          lastUpdate = ts;
                        }
                        requestAnimationFrame(tick);
                      }
                      update();
                      requestAnimationFrame(tick);
                    </script>
                  </body>
                </html>
                """.formatted(
                center.lat, center.lng,
                stationsJs,
                routePointsJs,
                segDistJs,
                meta,
                center.lat, center.lng
        );
    }

    private String buildSegmentDistancesJs(List<String> routeStations, List<Document> edges, Map<String, LatLng> coords) {
        // Build a lookup map for direct edges
        Map<String, Integer> dist = new HashMap<>();
        for (Document e : edges) {
            String s = asString(e.get("source"));
            String d = asString(e.get("destination"));
            Integer km = asInt(e.get("distance"));
            if (s == null || d == null || km == null) continue;
            dist.put((s + "->" + d).toLowerCase(Locale.ROOT), km);
        }

        List<String> seg = new ArrayList<>();
        for (int i = 0; i < routeStations.size() - 1; i++) {
            String a = routeStations.get(i);
            String b = routeStations.get(i + 1);
            if (a == null || b == null) {
                seg.add("null");
                continue;
            }
            Integer km = dist.get((a + "->" + b).toLowerCase(Locale.ROOT));
            if (km == null) {
                int shortest = railwayDijkstra.shortestDistanceKm(a, b);
                km = (shortest == Integer.MAX_VALUE) ? null : shortest;
            }
            if (km == null) {
                LatLng la = coords.get(a);
                LatLng lb = coords.get(b);
                if (la != null && lb != null) {
                    km = Math.max(1, (int) Math.round(haversineKm(la.lat, la.lng, lb.lat, lb.lng)));
                }
            }
            seg.add(km == null ? "null" : km.toString());
        }
        return "[" + String.join(",", seg) + "]";
    }

    private static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private Map<String, LatLng> loadStationCoords() {
        Instant now = Instant.now();
        if (!cachedCoords.isEmpty() && now.isBefore(coordsLoadedAt.plus(CACHE_TTL))) {
            return cachedCoords;
        }
        List<Document> docs = mongoTemplate.findAll(Document.class, "lat_long");
        Map<String, LatLng> out = new HashMap<>();
        for (Document d : docs) {
            String name = firstString(d, "station", "name", "city", "source");
            Double lat = firstDouble(d, "lat", "latitude");
            Double lng = firstDouble(d, "lng", "lon", "longitude");
            if (name == null || lat == null || lng == null) continue;
            out.put(name, new LatLng(lat, lng));
        }
        cachedCoords = out;
        coordsLoadedAt = now;
        return out;
    }

    private List<Document> loadGraphEdges() {
        Instant now = Instant.now();
        if (!cachedGraphEdges.isEmpty() && now.isBefore(graphLoadedAt.plus(CACHE_TTL))) {
            return cachedGraphEdges;
        }
        List<Document> edges = mongoTemplate.findAll(Document.class, "graph");
        cachedGraphEdges = edges;
        graphLoadedAt = now;
        return edges;
    }

    private static Optional<LatLng> average(Collection<LatLng> vals) {
        if (vals == null || vals.isEmpty()) return Optional.empty();
        double sumLat = 0, sumLng = 0;
        int n = 0;
        for (LatLng v : vals) {
            if (v == null) continue;
            sumLat += v.lat;
            sumLng += v.lng;
            n++;
        }
        if (n == 0) return Optional.empty();
        return Optional.of(new LatLng(sumLat / n, sumLng / n));
    }

    private static String firstString(Document d, String... keys) {
        for (String k : keys) {
            Object v = d.get(k);
            String s = asString(v);
            if (s != null && !s.isBlank()) return s;
        }
        return null;
    }

    private static Double firstDouble(Document d, String... keys) {
        for (String k : keys) {
            Object v = d.get(k);
            Double n = asDouble(v);
            if (n != null) return n;
        }
        return null;
    }

    private static String asString(Object v) {
        if (v == null) return null;
        String s = v.toString();
        return s;
    }

    private static Integer asInt(Object v) {
        if (v == null) return null;
        if (v instanceof Integer i) return i;
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(v.toString()); } catch (Exception e) { return null; }
    }

    private static Double asDouble(Object v) {
        if (v == null) return null;
        if (v instanceof Double d) return d;
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(v.toString()); } catch (Exception e) { return null; }
    }

    private static String jsStr(String s) {
        // very small JS string escape
        return "'" + s.replace("\\\\", "\\\\\\\\").replace("'", "\\\\'") + "'";
    }

    private static final class LatLng {
        final double lat;
        final double lng;
        LatLng(double lat, double lng) { this.lat = lat; this.lng = lng; }
    }
}

