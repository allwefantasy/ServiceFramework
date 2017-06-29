package net.csdn.modules.http;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import java.util.Map;

import static net.csdn.common.collect.MapBuilder.newMapBuilder;

/**
 * BlogInfo: william
 * Date: 11-9-6
 * Time: 下午2:15
 */
public class ResultFormat {
    //    {
//  "took" : 3560,
//  "timed_out" : false,
//  "_shards" : {
//    "total" : 5,
//    "successful" : 5,
//    "failed" : 0
//  },
//  "hits" : {
//    "total" : 0,
//    "max_score" : null,
//    "hits" : [ ]
//  }
//}
    public static JSONObject emptyResult(int total, float max_score, JSONArray hits) {
        Map map = newMapBuilder().put("took", 0).put("timed_out", false)
                .put("_shards", newMapBuilder().put("total", 0).put("successful", 0).put("failed", 0).map())
                .put("hits", newMapBuilder().put("total", total).put("max_score", max_score).put("hits", hits).map()).map();
        return JSONObject.fromObject(map);
    }
}
