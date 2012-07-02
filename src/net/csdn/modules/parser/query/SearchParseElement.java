package net.csdn.modules.parser.query;

import net.csdn.context.SearchContext;
import net.sf.json.JSONObject;

/**
 * User: william
 * Date: 11-9-7
 * Time: 上午10:53
 */
public interface SearchParseElement {
    void parse(JSONObject obj, SearchContext context);
}



