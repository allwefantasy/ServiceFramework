package net.csdn.modules.highlight;

import com.google.inject.Inject;
import net.csdn.modules.analyzer.AnalyzerService;
import net.csdn.modules.analyzer.mmseg4j.analysis.MMSegAnalyzer;
import net.csdn.modules.persist.mongodb.MongoClient;
import net.csdn.modules.persist.mongodb.MongoService;
import net.csdn.modules.transport.data.SearchHit;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.apache.lucene.analysis.WhitespaceAnalyzer;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.highlight.*;
import org.apache.lucene.util.Version;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * User: WilliamZhu
 * Date: 12-6-9
 * Time: 上午9:33
 */
public class HighlightService {

    @Inject
    private AnalyzerService analyzerService;
    @Inject
    private MongoService mongoService;

    private static String DATAS = "datas";
    private static String KEYWORDS = "keywords";
    private static String CONTENT_LENGTH = "content_length";
    private static final String PRE_TAG = "pre_tag";

    private static final String POST_TAG = "post_tag";
    private static String TYPE = "type";
    private static int MAGIC_LENGTH = 250;

    //{"datas":[{},{},{}],"keywords":{"title":keyawords,"body":keywrords},"content_length":100,"pre_tag":"","post_tag":""}
    //{"datas":[id1,id2,id3],"type":"blogs","keywords":{"title":keyawords,"body":keywrords},"content_length":100,"pre_tag":"","post_tag":""}
    //根据type确定是否取数据库
//    public JSONObject highlight(JSONObject object) {
//        if (object.containsKey(TYPE)) {
//
//            return fetchThenHighlight(object);
//        }
//        return onlyHighlight(null, object);
//    }

    public JSONObject highlight(Query query, JSONObject highlightObj, List<SearchHit> searchHitList) {
        highlightObj.put(DATAS, searchHitList);
        return onlyHighlight(query, highlightObj, searchHitList);
    }

    private JSONObject onlyHighlight(Query query, JSONObject object, List<SearchHit> searchHitList) {

        JSONObject keys = object.getJSONObject(KEYWORDS);
        int length = MAGIC_LENGTH;
        if (object.containsKey(CONTENT_LENGTH)) {
            length = object.getInt(CONTENT_LENGTH);
        }

        SimpleHTMLFormatter simpleHTMLFormatter = new SimpleHTMLFormatter(getOption(object, PRE_TAG, "<font color=\"red\">"),
                getOption(object, POST_TAG, "</font>"));

        for (int i = 0; i < searchHitList.size(); i++) {
            SearchHit data = searchHitList.get(i);
            for (Object key : keys.keySet()) {
                String field = (String) key;
                if (!data.getObject().containsKey(field)) {
                    continue;
                }
                String content = data.getObject().get(field);
                String queryStr = keys.getString(field);
                content = innerHighlight(query, simpleHTMLFormatter, field, content, queryStr, length);
                data.getObject().put(field, content);
            }
        }
        return object;
    }

//    private JSONObject fetchThenHighlight(JSONObject object) {
//        JSONArray ids = object.getJSONArray(DATAS);
//        String type = object.getString(TYPE);
//        JSONArray datas = fetchDBs(ids, type);
//        object.put(DATAS, datas);
//        return onlyHighlight(null, object);
//    }

    private JSONArray fetchDBs(JSONArray array, String type) {
        JSONArray newArray = new JSONArray();
        for (int i = 0; i < array.size(); i++) {
            int id = array.getInt(i);
            Map result = mongoService.fetchOne(type, id);
            if (result == null || result.isEmpty()) {
                JSONObject jsonObjectTemp = new JSONObject();
                //jsonObjectTemp.put("id",id);
                newArray.add(jsonObjectTemp);
            } else {
                newArray.add(JSONObject.fromObject(result));
            }

        }
        return newArray;
    }

    private String innerHighlight(Query _query, SimpleHTMLFormatter simpleHTMLFormatter, String field, String content, String queryStr, int length) {
        //object
        try {
            Query query = _query;
            if (query == null) {
                QueryParser parser = new QueryParser(Version.LUCENE_32, field, new WhitespaceAnalyzer(Version.LUCENE_32));
                parser.setDefaultOperator(QueryParser.Operator.AND);
                query = parser.parse(((MMSegAnalyzer) analyzerService.defaultAnalyzer()).toWhiteSpaceString(queryStr));
            }

            Highlighter highlighter = new Highlighter(simpleHTMLFormatter, new QueryScorer(query));
            highlighter.setTextFragmenter(new SimpleFragmenter(length));
            return fragment(highlighter.getBestFragment(analyzerService.defaultAnalyzer(), field, content), StringUtils.substring(content, 0, length));
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InvalidTokenOffsetsException e) {
            e.printStackTrace();
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return StringUtils.substring(content, 0, length);
    }

    private String fragment(String content, String defaultContent) {
        if (content == null || content.isEmpty()) return defaultContent;
        return content;
    }

    private static <T> T getOption(JSONObject input, String key, T default_val) {
        T value = (T) input.get(key);
        if (value == null)
            return default_val;
        return value;
    }

}