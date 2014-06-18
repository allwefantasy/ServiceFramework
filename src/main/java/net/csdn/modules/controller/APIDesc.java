package net.csdn.modules.controller;

import net.sf.json.JSONObject;

import java.util.List;

/**
 * 6/15/14 WilliamZhu(allwefantasy@gmail.com)
 */
public class APIDesc {
    String path;
    String desc;
    long qps;
    List<ParamDesc> paramDesces;
    List<ResponseStatus> responseStatuses;

    //for json

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    public long getQps() {
        return qps;
    }

    public void setQps(long qps) {
        this.qps = qps;
    }

    public List<ParamDesc> getParamDesces() {
        return paramDesces;
    }

    public void setParamDesces(List<ParamDesc> paramDesces) {
        this.paramDesces = paramDesces;
    }

    public List<ResponseStatus> getResponseStatuses() {
        return responseStatuses;
    }

    public void setResponseStatuses(List<ResponseStatus> responseStatuses) {
        this.responseStatuses = responseStatuses;
    }

    public static void main(String[] args) {
        JSONObject.fromObject(new APIDesc());
    }
}
