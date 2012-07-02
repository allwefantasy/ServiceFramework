package net.csdn.modules.parser;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import net.csdn.modules.parser.filter.FilterParseElement;
import net.csdn.modules.parser.query.*;

/**
 * User: WilliamZhu
 * Date: 12-6-4
 * Time: 下午7:13
 */
public class ParserModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(FilterParseElement.class);
        bind(FromParseElement.class);
        bind(QueryParseElement.class);
        bind(SizeParseElement.class);
        bind(SortParseElement.class);
        bind(HighlightParseElement.class);
        bind(FieldParseElement.class);
        bind(ParseService.class).in(Singleton.class);
    }


}
