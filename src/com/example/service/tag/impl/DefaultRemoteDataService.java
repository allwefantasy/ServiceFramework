package com.example.service.tag.impl;


import com.example.service.tag.RemoteDataService;
import com.google.inject.Inject;
import net.csdn.common.path.Url;
import net.csdn.exception.ArgumentErrorException;
import net.csdn.modules.transport.HttpTransportService;
import net.sf.json.JSONArray;

import java.util.HashMap;

public class DefaultRemoteDataService implements RemoteDataService {

    @Inject
    private HttpTransportService httpTransportService;


    public JSONArray findByIds(String typeName, String fields, String ids) {
        String host = "";
        String port = "";
        HashMap<String, String> hm = new HashMap<String, String>();
        hm.put("ids", ids);
        hm.put("fields", fields);
        HttpTransportService.SResponse post = httpTransportService.post(new Url("http://" + host + ":" + port + "/store/" + getTypeName(typeName) + "/list/ids"), hm);
        if (post.getStatus() != 200) {
            throw new ArgumentErrorException("查询具体文章失败");
        }
        String content = post.getContent();
        return JSONArray.fromObject(content);
    }


    private String getTypeName(String typeName) {
        if ("BlogTag".equals(typeName)) return "Blog";
        if ("NewsTag".equals(typeName)) return "News";
        return null;
    }
}
