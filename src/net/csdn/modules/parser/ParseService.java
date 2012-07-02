package net.csdn.modules.parser;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import net.csdn.context.SearchContext;
import net.csdn.modules.parser.filter.FilterParseElement;
import net.csdn.modules.parser.query.*;
import net.sf.json.JSONObject;

import java.util.Map;

import static net.csdn.context.SearchContext.QUERY_FIRST_KEY_WORD.*;

/**
 * User: WilliamZhu
 * Date: 12-6-19
 * Time: 上午11:22
 */
public class ParseService {

    private ImmutableMap<String, SearchParseElement> parseElementsProcessor;

    @Inject
    public ParseService(QueryParseElement queryParseElement, FromParseElement fromParseElement, SizeParseElement sizeParseElement, SortParseElement sortParseElement, FilterParseElement filterParseElement, HighlightParseElement highlightParseElement, FieldParseElement fieldParseElement) {
        ImmutableMap.Builder<String, SearchParseElement> parseElements = ImmutableMap.builder();
        parseElements.put(FROM, fromParseElement).put(SIZE, sizeParseElement).put(Fields, fieldParseElement)
                .put(QUERY, queryParseElement).put(SORT, sortParseElement).put(FILTER, filterParseElement).put(Highlight, highlightParseElement);
        this.parseElementsProcessor = parseElements.build();
    }


    public void parse(SearchContext context) {
        JSONObject query = JSONObject.fromObject(context.originSource());
        for (Map.Entry<String, ? extends SearchParseElement> entry : parseElementsProcessor.entrySet()) {
            entry.getValue().parse(query, context);
        }
    }

}
