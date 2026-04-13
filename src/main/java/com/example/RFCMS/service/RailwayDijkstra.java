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
            String src = doc.getString("source");
            String dest = doc.getString("destination");
            int dist = doc.getInteger("distance");

            graph.putIfAbsent(src, new ArrayList<>());
            graph.putIfAbsent(dest, new ArrayList<>()); // ✅ IMPORTANT

            graph.get(src).add(new Edge(dest, dist));   //A → [(B, 5), (C, 10)]
        }

        return graph;
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

        Map<String, String> parent = new HashMap<>();
        Map<String, Integer> dist = dijkstra(graph, start, parent);

        if (!dist.containsKey(end) || dist.get(end) == Integer.MAX_VALUE) {
            return new ArrayList<>(List.of(start, end));
        }
        return getPath(parent, end);
    }

    public int shortestDistanceKm(String start, String end) {
        Map<String, List<Edge>> graph = buildGraph();
        Map<String, Integer> dist = dijkstra(graph, start, new HashMap<>());
        return dist.getOrDefault(end, Integer.MAX_VALUE);
    }

    /**
     * Backward-compatible CLI-style method.
     */
    public void run(String start, String end) {
        List<String> path = shortestPath(start, end);
        System.out.println("Path: " + String.join(" -> ", path));
    }
}