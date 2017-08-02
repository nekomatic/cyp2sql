package database;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import production.C2SMain;
import schema_conversion.SchemaConvert;

import java.io.*;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Class containing all methods required to store results of schema conversion from Neo4j to
 * a relational backend database.
 */
public class InsertSchema {
    private static List<String> fieldsForMetaFile = new ArrayList<>();

    /**
     * Executing the various schema parts one by one to the relational backend.
     *
     * @param database Name of the database to store the new schema on.
     */
    public static void executeSchemaChange(String database) {
        RelDBDriver.createConnection(database);

        String createAdditionalNodeTables = insertEachLabel();
        String createAdditionalEdgesTables = insertEachRelType();

        String sqlInsertNodes = insertNodes();
        String sqlInsertEdges = insertEdges();

        try {
            RelDBDriver.createInsert(createAdditionalNodeTables);
            RelDBDriver.createInsert(createAdditionalEdgesTables);
            RelDBDriver.createInsert(sqlInsertNodes);
            RelDBDriver.createInsert(sqlInsertEdges);
            RelDBDriver.createInsert(DBConstants.QUERY_MAPPING);
            RelDBDriver.createInsert(DBConstants.ADJLIST_FROM);
            RelDBDriver.createInsert(DBConstants.ADJLIST_TO);
            RelDBDriver.createInsert(DBConstants.FOR_EACH_FUNC);
            RelDBDriver.createInsert(DBConstants.CYPHER_ITERATE);
            RelDBDriver.createInsert(DBConstants.UNIQUE_ARR_FUNC);
            RelDBDriver.createInsert(DBConstants.AUTO_SEQ_QUERY);
        } catch (SQLException e) {
            e.printStackTrace();
        }

        addFieldsToMetaFile();
        RelDBDriver.closeConnection();
    }

    /**
     * All of the fields and relationships gathered during the Schema translation are stored in
     * meta files (to be used when outputting the results of the queries from both Postgres and
     * Neo4j).
     */
    private static void addFieldsToMetaFile() {
        FileOutputStream fos;
        try {
            fos = new FileOutputStream(C2SMain.props.getWspace() + "/meta_nodeProps.txt");

            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(fos));
            for (String s : fieldsForMetaFile) {
                bw.write(s);
                bw.newLine();
            }
            bw.close();
            fos.close();

            fos = new FileOutputStream(C2SMain.props.getWspace() + "/meta_rels.txt");

            bw = new BufferedWriter(new OutputStreamWriter(fos));
            for (String s : SchemaConvert.relTypes) {
                bw.write(s);
                bw.newLine();
            }
            bw.close();
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * If there is a label which is applied to a node only ever on its own in isolation, then store this as a
     * relation to remove unnecessary NULLs which slow execution of SQL down.
     *
     * @return SQL to execute.
     */
    private static String insertEachLabel() {
        StringBuilder sb = new StringBuilder();
        FileOutputStream fos_labelProps;
        FileOutputStream fos_labelNames;

        try {
            fos_labelProps = new FileOutputStream(C2SMain.props.getWspace() + "/meta_labelProps.txt");
            fos_labelNames = new FileOutputStream(C2SMain.props.getWspace() + "/meta_labelNames.txt");

            BufferedWriter bw_labelProps = new BufferedWriter(new OutputStreamWriter(fos_labelProps));
            BufferedWriter bw_labelNames = new BufferedWriter(new OutputStreamWriter(fos_labelNames));

            for (String label : SchemaConvert.labelMappings.keySet()) {
                String tableLabel = label.replace(", ", "_");
                sb.append("CREATE TABLE ").append(tableLabel).append("(");
                sb.append(SchemaConvert.labelMappings.get(label));
                sb.append("); ");

                bw_labelProps.write("*" + tableLabel + "*");
                bw_labelProps.newLine();

                for (String y : SchemaConvert.labelMappings.get(label).replace(" TEXT[]", "")
                        .replace(" BIGINT", "")
                        .replace(" INT", "")
                        .replace(" TEXT", "")
                        .split(", ")) {
                    bw_labelProps.write(y);
                    bw_labelProps.newLine();
                }

                bw_labelNames.write(tableLabel);
                bw_labelNames.newLine();
            }

            bw_labelProps.close();
            bw_labelNames.close();
            fos_labelProps.close();
            fos_labelNames.close();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }

        return sb.toString();
    }

    /**
     * Create each relationship table in the relational schema. The tables in the relational schema
     * that correspond to each edge type of Neo4j will be prefixed with the two-letter pair of 'e$'.
     *
     * @return SQL string for insertion of each relationship type.
     */
    private static String insertEachRelType() {
        StringBuilder sb = new StringBuilder();

        for (String rel : SchemaConvert.relTypes) {
            // specific relationship types will be stored in the following
            // format, with the name of the relation being e${type of relationship}
            String relTableName = "e$" + rel;

            sb.append("CREATE TABLE ").append(relTableName).append("(");

            for (String x : SchemaConvert.edgesRelLabels) {
                sb.append(x).append(", ");
            }
            sb.setLength(sb.length() - 2);
            sb.append("); ");
        }

        return sb.toString();
    }

    /**
     * For each 'label' relation, insert the appropriate values.
     *
     * @param sb    Original SQL that will be appended to.
     * @param label Name of relation.
     * @param o     JSON object containing data to store.
     * @return New SQL statement with correct INSERT INTO statement.
     */
    private static StringBuilder insertDataForLabels(StringBuilder sb, String label, JsonObject o) {
        String tableLabel = label.replace(", ", "_");
        sb.append("INSERT INTO ").append(tableLabel).append("(");

        for (String prop : SchemaConvert.labelMappings.get(label).split(", ")) {
            sb.append(prop
                    .replace(" TEXT[]", "")
                    .replace(" BIGINT", "")
                    .replace(" TEXT", "")
                    .replace(" INT", ""))
                    .append(", ");
        }

        sb.setLength(sb.length() - 2);
        sb.append(") VALUES(");

        for (String z : SchemaConvert.labelMappings.get(label).split(", ")) {
            sb.append(getInsertString(z, o));
        }

        sb.setLength(sb.length() - 2);
        sb.append("); ");

        return sb;
    }

    /**
     * Insert all nodes into relational database.
     *
     * @return SQL to execute.
     */
    private static String insertNodes() {
        StringBuilder sb = new StringBuilder();

        sb.append("CREATE TABLE nodes(");
        for (String x : SchemaConvert.nodeRelLabels) {
            if (x.startsWith("mono_time")) x = "mono_time BIGINT";
            sb.append(x).append(", ");
        }
        sb.setLength(sb.length() - 2);
        sb.append("); ");

        sb = insertTableDataNodes(sb);
        return sb.toString();
    }

    /**
     * Data/properties of the nodes to store.
     *
     * @param sb Original SQL to append data to.
     * @return New SQL
     */
    private static StringBuilder insertTableDataNodes(StringBuilder sb) {
        StringBuilder sbLabels = new StringBuilder();
        sb.append("INSERT INTO nodes (");

        for (String y : SchemaConvert.nodeRelLabels) {
            if (y.startsWith("mono_time")) y = "mono_time BIGINT";
            sb.append(y.split(" ")[0]).append(", ");
            fieldsForMetaFile.add(y.split(" ")[0]);
        }
        sb.setLength(sb.length() - 2);
        sb.append(")");

        sb.append(" VALUES ");

        try {
            FileInputStream fis = new FileInputStream(SchemaConvert.nodesFile);
            BufferedReader br = new BufferedReader(new InputStreamReader(fis));
            String line;
            JsonParser parser = new JsonParser();

            while ((line = br.readLine()) != null) {
                JsonObject o = (JsonObject) parser.parse(line);
                String label = o.get("label").getAsString();

                sbLabels = insertDataForLabels(sbLabels, label, o);
                sb.append("(");
                for (String z : SchemaConvert.nodeRelLabels) {
                    sb.append(getInsertString(z, o));
                }
                sb.setLength(sb.length() - 2);
                sb.append("), ");
            }
            br.close();
            fis.close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            File f = new File(SchemaConvert.nodesFile);
            f.delete();
        }

        sb.setLength(sb.length() - 2);
        sb.append("; ");
        sb.append(sbLabels.toString()).append(";");

        return sb;
    }

    /**
     * Insert relationships into SQL.
     *
     * @return SQL to execute.
     */
    private static String insertEdges() {
        StringBuilder sb = new StringBuilder();

        sb.append("CREATE TABLE edges(");
        for (String x : SchemaConvert.edgesRelLabels) {
            sb.append(x).append(", ");
        }
        sb.setLength(sb.length() - 2);
        sb.append("); ");

        sb = insertTableDataEdges(sb);
        return sb.toString();
    }


    /**
     * Insert properties of the relationships to SQL.
     *
     * @param sb Original SQL to append data to.
     * @return New SQL with data inserted into it.
     */
    private static StringBuilder insertTableDataEdges(StringBuilder sb) {
        sb.append("INSERT INTO edges (");
        StringBuilder sbTypes = new StringBuilder();

        String columns = "";

        for (String y : SchemaConvert.edgesRelLabels) {
            columns = columns + y.split(" ")[0] + ", ";
        }
        columns = columns.substring(0, columns.length() - 2);
        columns = columns + ")";
        sb.append(columns);

        sb.append(" VALUES ");

        try {
            FileInputStream fis = new FileInputStream(SchemaConvert.edgesFile);
            BufferedReader br = new BufferedReader(new InputStreamReader(fis));
            String line;
            JsonParser parser = new JsonParser();

            while ((line = br.readLine()) != null) {
                JsonObject o = (JsonObject) parser.parse(line);
                sb.append("(");
                String values = "";
                for (String z : SchemaConvert.edgesRelLabels) {
                    String v = getInsertString(z, o);
                    values = values + v;
                    sb.append(v);
                }
                values = values.substring(0, values.length() - 2);
                sbTypes = addType(sbTypes, columns, o, values);
                sb.setLength(sb.length() - 2);
                sb.append("), ");
            }
            br.close();
            fis.close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            File f = new File(SchemaConvert.edgesFile);
            f.delete();
        }

        sb.setLength(sb.length() - 2);
        sb.append(";").append(" ").append(sbTypes.toString());

        return sb;
    }

    private static StringBuilder addType(StringBuilder sbTypes, String columns, JsonObject o, String values) {
        sbTypes.append("INSERT INTO ");
        sbTypes.append("e$").append(o.get("type").getAsString()).append("(").append(columns);
        sbTypes.append(" VALUES (");
        sbTypes.append(values);
        sbTypes.append(");");
        return sbTypes;
    }

    private static String getInsertString(String inputField, JsonObject obj) {
        String temp;

        //OPUS hack
        if (inputField.startsWith("mono_time")) inputField = "mono_time BIGINT";

        try {
            if (inputField.endsWith("BIGINT")) {
                long value = obj.get(inputField.split(" ")[0]).getAsLong();
                temp = value + ", ";
            } else if (inputField.endsWith("INT") && !inputField.contains("BIGINT")) {
                int value = obj.get(inputField.split(" ")[0]).getAsInt();
                temp = value + ", ";
            } else if (inputField.endsWith("[]")) {
                // is text with list property
                JsonArray value = obj.get(inputField.split(" ")[0]).getAsJsonArray();
                temp = "ARRAY" + value.toString().replace("\"", "'") + ", ";
            } else {
                // is just text
                String value = obj.get(inputField.split(" ")[0]).getAsString();
                temp = "'" + value + "', ";
            }
        } catch (NumberFormatException | NullPointerException nfe) {
            temp = "null, ";
        }
        return temp;
    }
}