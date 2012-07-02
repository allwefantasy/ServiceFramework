package net.csdn.modules.parser.query;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import net.csdn.context.SearchContext;
import net.csdn.modules.gateway.GatewayData;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.lucene.index.Engine;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;

import java.util.List;

import static net.csdn.modules.http.JSONObjectUtils.getString;

/**
 * User: william
 * Date: 11-9-26
 * Time: 下午2:57
 */
public class SortParseElement implements SearchParseElement {

    private Engine.Mapper mapper;

    @Inject
    private GatewayData gatewayData;

    @Override
    public void parse(JSONObject obj, SearchContext context) {
        // { "post_date" : {"order" : "asc"} },
        //context.from(getInt(obj,SearchContext.QUERY_FIRST_KEY_WORD.FROM,-1));
        mapper = gatewayData.mapper(context.name(), context.types()[0]);
        innerParse(obj, context);


    }

    private void innerParse(JSONObject obj, SearchContext context) {

        if (!obj.containsKey(SearchContext.QUERY_FIRST_KEY_WORD.SORT)) return;
        Object sort = obj.get(SearchContext.QUERY_FIRST_KEY_WORD.SORT);

        if (sort instanceof JSONObject) {

            JSONObject sortObject = (JSONObject) sort;
            JSONArray array = new JSONArray();
            for (Object _key : sortObject.keySet()) {
                String key = (String) _key;
                String order = getString(sortObject, key);
                JSONObject object = new JSONObject();
                object.put(key, order);
                array.add(object);
            }
            sort = array;
        }

        if (sort instanceof JSONArray) {
            List<SortField> sortFields = Lists.newArrayList();
            JSONArray array = (JSONArray) sort;
            for (int i = 0; i < array.size(); i++) {
                JSONObject sortObject = array.getJSONObject(i);
                for (Object _key : sortObject.keySet()) {
                    String key = (String) _key;
                    String order = getString(sortObject, key);
                    boolean reverse = order.equals("desc") ? true : false;
                    String dataType = mapper._properties(key).type();
                    sortFields.add(new SortField(key, dataType(dataType), reverse));
                }
            }
            if (sortFields.size() == 0) {
                context.sort(null);
                return;
            }
            SortField[] sortFieldsArrays = new SortField[sortFields.size()];
            context.sort(new Sort(sortFields.toArray(sortFieldsArrays)));
        }
    }

    private int dataType(String dataType) {

        if (dataType.equals("string")) {
            return SortField.STRING;
        } else if (dataType.equals("integer")) {
            return SortField.INT;
        } else if (dataType.equals("double")) {
            return SortField.DOUBLE;
        } else if (dataType.equals("long")) {
            return SortField.LONG;
        } else if (dataType.equals("float")) {
            return SortField.FLOAT;
        } else if (dataType.equals("short")) {
            return SortField.SHORT;
        }
        return SortField.STRING;
    }
}
