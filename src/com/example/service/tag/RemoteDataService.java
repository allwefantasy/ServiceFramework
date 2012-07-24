package com.example.service.tag;

import com.example.service.tag.impl.DefaultRemoteDataService;
import net.csdn.annotation.Service;
import net.sf.json.JSONArray;

@Service(implementedBy = DefaultRemoteDataService.class)
public interface RemoteDataService {
    public JSONArray findByIds(String typeName, String fields, String ids);
}

