package com.example.RFCMS.service;

import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class RailwayDijkstra {

    static class Edge {
        String dest;
        int weight;

        Edge(String d, int w) {
            dest = d;
            weight = w;
        }
    }

    @Autowired
    private MongoTemplate mongoTemplate;

    private volatile Map<String, List<Edge>> cachedAdjacency;
    private volatile Instant graphLoadedAt = Instant.EPOCH;
    private static final Duration GRAPH_CACHE_TTL = Duration.ofMinutes(2);

    // -----------------------------
    // BUILD GRAPH
    // -----------------------------
    public Map<String, List<Edge>> buildGraph() {   //Station → List of neighbors
        Instant now = Instant.now();
        Map<String, List<Edge>> g = cachedAdjacency;
        if (g != null && now.isBefore(graphLoadedAt.plus(GRAPH_CACHE_TTL))) {
            return g;
        }
        synchronized (this) {
            now = Instant.now();
            g = cachedAdjacency;
            if (g != null && now.isBefore(graphLoadedAt.plus(GRAPH_CACHE_TTL))) {
                return g;
            }
            g = buildGraphFromDb();
            cachedAdjacency = g;
            graphLoadedAt = now;
            return g;
        }
    }

    private Map<String, List<Edge>> buildGraphFromDb() {
        Map<String, List<Edge>> graph = new HashMap<>();

        List<Document> edges = mongoTemplate.findAll(Document.class, "graph");

        for (Document doc : edges) {
            // Match WagonStatusEtaUpdaterService: graph rows may use source/destination or src/dest.
            String src = readGraphStation(doc, "source", "src");
            String dest = readGraphStation(doc, "destination", "dest");
            Object distVal = doc.get("distance");
            if (distVal == null) {
                distVal = doc.get("distance_km");
            }
            if (src == null || dest == null || distVal == null) {
                continue;
            }
            int dist;
            if (distVal instanceof Number n) {
                dist = (int) Math.round(n.doubleValue());
            } else {
                try {
                    dist = (int) Math.round(Double.parseDouble(distVal.toString().trim()));
                } catch (NumberFormatException ex) {
                    continue;
                }
            }
            if (dist < 0) {
                continue;
            }

            graph.putIfAbsent(src, new ArrayList<>());
            graph.putIfAbsent(dest, new ArrayList<>());

            // Treat segments as bidirectional (same km both ways).
            graph.get(src).add(new Edge(dest, dist));
            graph.get(dest).add(new Edge(src, dist));
        }

        return graph;
    }

    private static String readGraphStation(Document doc, String... fieldNames) {
        for (String key : fieldNames) {
            Object v = doc.get(key);
            if (v == null) {
                continue;
            }
            String s = v.toString().trim();
            if (!s.isEmpty()) {
                return s;
            }
        }
        return null;
    }

    /** Map user/API station name to the vertex key used in the graph (case-insensitive). */
    private static String canonicalVertex(Map<String, List<Edge>> graph, String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String t = name.trim();
        if (graph.containsKey(t)) {
            return t;
        }
        for (String k : graph.keySet()) {
            if (k != null && k.equalsIgnoreCase(t)) {
                return k;
            }
        }
        return null;
    }

    /** Call after bulk edits to `graph` collection if you need immediate fresh paths. */
    public void invalidateGraphCache() {
        synchronized (this) {
            cachedAdjacency = null;
            graphLoadedAt = Instant.EPOCH;
        }
    }

    // -----------------------------
    // DIJKSTRA
    // -----------------------------
    public Map<String, Integer> dijkstra(Map<String, List<Edge>> graph,
            String start,
            Map<String, String> parent) {

        PriorityQueue<Map.Entry<String, Integer>> pq = new PriorityQueue<>(Map.Entry.comparingByValue());

        Map<String, Integer> dist = new HashMap<>();

        dist.put(start, 0);
        pq.add(new AbstractMap.SimpleEntry<>(start, 0));

        while (!pq.isEmpty()) {
            String curr = pq.poll().getKey();

            for (Edge edge : graph.getOrDefault(curr, new ArrayList<>())) {
                int newDist = dist.get(curr) + edge.weight;

                if (newDist < dist.getOrDefault(edge.dest, Integer.MAX_VALUE)) {
                    dist.put(edge.dest, newDist);
                    parent.put(edge.dest, curr);
                    pq.add(new AbstractMap.SimpleEntry<>(edge.dest, newDist));
                }
            }
        }

        return dist;
    }

    // -----------------------------
    // GET PATH
    // -----------------------------
    public List<String> getPath(Map<String, String> parent, String end) {
        List<String> path = new ArrayList<>();

        while (end != null) {
            path.add(end);
            end = parent.get(end);
        }

        Collections.reverse(path);
        return path;
    }

    // -----------------------------
    // MAIN LOGIC METHOD
    // -----------------------------
    public List<String> shortestPath(String start, String end) {
        Map<String, List<Edge>> graph = buildGraph();

        String s = canonicalVertex(graph, start);
        String e = canonicalVertex(graph, end);
        if (s == null || e == null) {
            String a = start != null ? start.trim() : "";
            String b = end != null ? end.trim() : "";
            return new ArrayList<>(List.of(a, b));
        }

        Map<String, String> parent = new HashMap<>();
        Map<String, Integer> dist = dijkstra(graph, s, parent);

        if (!dist.containsKey(e) || dist.get(e) == Integer.MAX_VALUE) {
            return new ArrayList<>(List.of(s, e));
        }
        return getPath(parent, e);
    }

    public int shortestDistanceKm(String start, String end) {
        Map<String, List<Edge>> graph = buildGraph();
        String s = canonicalVertex(graph, start);
        String t = canonicalVertex(graph, end);
        if (s == null || t == null) {
            return Integer.MAX_VALUE;
        }
        Map<String, Integer> dist = dijkstra(graph, s, new HashMap<>());
        return dist.getOrDefault(t, Integer.MAX_VALUE);
    }

    /**
     * Backward-compatible CLI-style method.
     */
    public void run(String start, String end) {
        List<String> path = shortestPath(start, end);
        System.out.println("Path: " + String.join(" -> ", path));
    }
}