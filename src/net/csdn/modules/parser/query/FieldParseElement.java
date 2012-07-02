package net.csdn.modules.parser.query;

import com.google.common.collect.Lists;
import net.csdn.context.SearchContext;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

/**
 * User: WilliamZhu
 * Date: 12-6-13
 * Time: 下午4:30
 */
public class FieldParseElement implements SearchParseElement {
    @Override
    public void parse(JSONObject obj, SearchContext context) {
        if (!obj.containsKey(SearchContext.QUERY_FIRST_KEY_WORD.Fields)) return;
        JSONArray fields = obj.getJSONArray(SearchContext.QUERY_FIRST_KEY_WORD.Fields);
        context.fields(Lists.newArrayList(fields));
    }
}
