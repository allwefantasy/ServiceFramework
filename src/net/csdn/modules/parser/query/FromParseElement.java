package net.csdn.modules.parser.query;

import net.csdn.context.SearchContext;
import net.sf.json.JSONObject;

import static net.csdn.modules.http.JSONObjectUtils.getInt;

/**
 * User: william
 * Date: 11-9-7
 * Time: 上午11:17
 */
public class FromParseElement implements SearchParseElement {
    @Override
    public void parse(JSONObject obj, SearchContext context) {
        context.from(getInt(obj, SearchContext.QUERY_FIRST_KEY_WORD.FROM, 0));
    }


}
