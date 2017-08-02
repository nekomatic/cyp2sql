package query_translation.sql.conversion_types;

import intermediate_rep.DecodedQuery;
import query_translation.sql.utilities_sql.WithSQL;

public class With_Cypher extends AbstractConversion {
    /**
     * Method for converting Cypher queries containing the WITH keyword.
     * The method is currently a framework for how to translate one type of WITH query:
     * <p>
     * MATCH (a:Global)-[m]->(b:Local) WITH a, COUNT(m) AS Glo_Loc_Count
     * WHERE Glo_Loc_Count >= 2 RETURN a.node_id, Glo_Loc_Count ORDER BY Glo_Loc_Count DESC;
     *
     * @param cypher Original Cypher input containing the WITH keyword.
     * @return SQL string equivalent of the original Cypher input.
     */
    @Override
    public String convertQuery(String cypher) {
        int posOfWhere = cypher.toLowerCase().indexOf("where");
        int posOfOrderBy = cypher.toLowerCase().indexOf("order by");

        // from the tokens in the original Cypher query, decide on the most appropriate method for translation.
        if (posOfOrderBy == -1) return withWhere(cypher);
        else if (posOfWhere == -1) return withOB(cypher);
        else return "";
    }

    private String withOB(String cypher) {
        int posOfReturn = cypher.toLowerCase().indexOf("return");
        String firstWith = cypher.toLowerCase().replace("with", "return");
        firstWith = firstWith.substring(0, posOfReturn + 1) + ";";
        DecodedQuery dQ = convertCypherToSQL(firstWith);

        String withTemp = null;
        if (dQ != null) {
            withTemp = WithSQL.genTemp(dQ.getSqlEquiv());
        }

        String indexName = dQ.getMc().getNodes().get(0).getId();
        String finalPart = "match (" + indexName + ") " +
                cypher.toLowerCase().substring(posOfReturn, cypher.length());
        DecodedQuery decQFinal = convertCypherToSQL(finalPart);
        String sqlSelect = WithSQL.createSelectOB(decQFinal);
        return withTemp + " " + sqlSelect;
    }

    private String withWhere(String cypher) {
        String changeLine = cypher.toLowerCase().replace("with", "return");
        String[] withParts = changeLine.toLowerCase().split(" where ");
        DecodedQuery dQ = convertCypherToSQL(withParts[0] + ";");

        String withTemp = null;
        if (dQ != null) {
            withTemp = WithSQL.genTemp(dQ.getSqlEquiv());
        }

        String sqlSelect = WithSQL.createSelectWhere(withParts[1].trim(), dQ);
        return withTemp + " " + sqlSelect;
    }
}