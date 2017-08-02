package database;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import schema_conversion.SchemaConvert;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

public class KeyValueTest {
    private static Map<Integer, String> mapNodes;
    private static Map<String, String> mapEdges;
    private static Map<String, List<Integer>> mapLabels;
    private static Map<Integer, List<Integer>> mapOut;
    private static Map<Integer, List<Integer>> mapIn;
    private static JsonParser parser = new JsonParser();


    public static void executeSchemaChange() {
        Config cfg = new Config();
        HazelcastInstance instance = Hazelcast.newHazelcastInstance(cfg);

        mapNodes = instance.getMap("nodes");
        mapEdges = instance.getMap("edges");
        mapLabels = instance.getMap("labels");
        mapOut = instance.getMap("out");
        mapIn = instance.getMap("in");

        nodesMap();
        edgesMap();

        exampleQueries();
    }

    private static void exampleQueries() {
        //match(n) where n.node_id = 492 return n.sys_time;
        //answer should be 1112012297
        //iterating through all keys until found...
        long startNano = System.nanoTime();
        Map<Integer, String> results = new HashMap<>();
        for (Map.Entry<Integer, String> entry : mapNodes.entrySet()) {
            JsonObject o2 = (JsonObject) parser.parse(entry.getValue());
            if (o2.get("node_id").getAsInt() == 492) results.put(entry.getKey(), o2.get("sys_time").getAsString());
        }
        long endNano = System.nanoTime();
        System.out.println(results + " -- time: " + ((endNano - startNano) / 1000000.0) + "ms.");

        //match(n:Meta) return count(n);
        //answer should be 163
        System.out.println(mapLabels.get("meta").size());

        // match (a {node_id:236})-->(b) return b;
        // answer should be [162, 232]
        System.out.println(mapOut.get(235));

        // match (a {node_id:835})<--(b) return b;
        // answer should be [871]
        System.out.println(mapIn.get(834));

        //MATCH (a:Global)-->(b:Local) RETURN count(b);
        //answer should be 234
        startNano = System.nanoTime();
        List<Integer> globalNodes = mapLabels.get("global");
        List<Integer> localNodes = mapLabels.get("local");
        int count = 0;
        for (Map.Entry<String, String> entry : mapEdges.entrySet()) {
            int idL = Integer.parseInt(entry.getKey().split(",")[0].substring(1));
            if (globalNodes.contains(idL)) {
                int idR = Integer.parseInt(entry.getKey().split(",")[1].substring(0,
                        entry.getKey().split(",")[1].length() - 1));
                if (localNodes.contains(idR)) count++;
            }
        }
        endNano = System.nanoTime();
        System.out.println(count + " -- time: " + ((endNano - startNano) / 1000000.0) + "ms.");

        //MATCH (a:Global)-->(b:Local) RETURN count(b);
        //answer should be 234
        startNano = System.nanoTime();
        count = 0;
        for (int x : globalNodes) {
            for (int y : localNodes) {
                if (mapOut.get(x).contains(y)) count++;
            }
        }
        endNano = System.nanoTime();
        System.out.println(count + " -- time: " + ((endNano - startNano) / 1000000.0) + "ms.");

        //startNano = System.nanoTime();
        globalNodes = mapLabels.get("global");
        localNodes = mapLabels.get("local");
        List<Integer> processNodes = mapLabels.get("process");
        List<Integer> currentRes = new LinkedList<>();
        List<Integer> bRes;
        Set<Integer> temp = new HashSet<>();
        for (int a : globalNodes) {
            List<Integer> list = mapOut.get(a);
            currentRes.addAll(list);
        }
        for (int b : currentRes) {
            if (!localNodes.contains(b)) temp.add(b);
        }
        currentRes.removeAll(temp);
        System.out.println(currentRes.size());
        bRes = currentRes;
        currentRes = new LinkedList<>();
        temp = new HashSet<>();
        for (int b2 : bRes) {
            List<Integer> list = mapOut.get(b2);
            if (list != null) currentRes.addAll(list);
        }
        for (int c : currentRes) {
            if (!processNodes.contains(c)) temp.add(c);
        }
        currentRes.removeAll(temp);
        System.out.println(currentRes.size());
        endNano = System.nanoTime();
        //System.out.println(currentRes.size());
        System.out.println(currentRes.size() + " -- time: " + ((endNano - startNano) / 1000000.0) + "ms.");
    }

    private static void edgesMap() {
        FileInputStream fis;
        BufferedReader br = null;

        try {
            fis = new FileInputStream(SchemaConvert.edgesFile);
            br = new BufferedReader(new InputStreamReader(fis));
            String line;

            while ((line = br.readLine()) != null) {
                JsonObject o = (JsonObject) parser.parse(line);
                int idl = o.get("idL").getAsInt();
                int idr = o.get("idR").getAsInt();
                o.remove("idL");
                o.remove("idR");
                mapEdges.put("{" + idl + "," + idr + "}", o.toString());
                addToMap(idl, idr, mapOut);
                addToMap(idr, idl, mapIn);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException ex) {
                    // ignore ... any significant errors should already have been caught...
                }
            }
            //File f = new File(SchemaConvert.edgesFile);
            //f.delete();
        }
    }

    private static void nodesMap() {
        FileInputStream fis;
        BufferedReader br = null;

        try {
            fis = new FileInputStream(SchemaConvert.nodesFile);
            br = new BufferedReader(new InputStreamReader(fis));
            String line;

            while ((line = br.readLine()) != null) {
                JsonObject o = (JsonObject) parser.parse(line);
                int id = o.get("id").getAsInt();
                String label = o.get("label").getAsString();
                o.remove("id");
                mapNodes.put(id, o.toString());
                addToMap(label, id, mapLabels);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException ex) {
                    // ignore ... any significant errors should already have been caught...
                }
            }
            //File f = new File(SchemaConvert.nodesFile);
            //f.delete();
        }
    }

    private static <S, T> void addToMap(S key, T value, Map<S, List<T>> map) {
        List<T> there = map.computeIfAbsent(key, k -> new ArrayList<>());
        there.add(value);
        map.put(key, there);
    }
}