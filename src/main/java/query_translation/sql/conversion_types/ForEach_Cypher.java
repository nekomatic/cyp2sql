package query_translation.sql.conversion_types;

import intermediate_rep.CypForEach;
import intermediate_rep.DecodedQuery;
import query_translation.sql.utilities_sql.ForEach;

public class ForEach_Cypher extends AbstractConversion {
    @Override
    public String convertQuery(String cypher) {
        String changeLine = cypher.toLowerCase().replace("with", "return");
        String[] feParts = changeLine.toLowerCase().split(" foreach ");
        DecodedQuery dQ = convertCypherToSQL(feParts[0].trim() + ";");
        CypForEach cypForEach = new CypForEach(feParts[1].trim());
        if (dQ != null) {
            dQ.setForEachC(cypForEach);
        }
        StringBuilder sql = new StringBuilder();
        ForEach fe = new ForEach();
        return fe.translate(sql, dQ).toString();
    }
}