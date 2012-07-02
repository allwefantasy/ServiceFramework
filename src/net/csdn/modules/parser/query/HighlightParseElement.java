package net.csdn.modules.parser.query;

import net.csdn.context.SearchContext;
import net.sf.json.JSONObject;

import static net.csdn.modules.http.JSONObjectUtils.*;

/**
 * User: WilliamZhu
 * Date: 12-6-9
 * Time: 上午10:13
 */
public class HighlightParseElement implements SearchParseElement {
    @Override
    public void parse(JSONObject obj, SearchContext context) {
        context.highlight(getJSONObject(obj, SearchContext.QUERY_FIRST_KEY_WORD.Highlight));
    }
}
