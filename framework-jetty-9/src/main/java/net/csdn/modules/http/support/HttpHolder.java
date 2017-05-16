package net.csdn.modules.http.support;

import net.csdn.modules.http.RestRequest;
import net.csdn.modules.http.RestResponse;

/**
 * 10/31/13 WilliamZhu(allwefantasy@gmail.com)
 */
public class HttpHolder {
    private RestRequest restRequest;
    private RestResponse restResponse;

    public HttpHolder(RestRequest restRequest, RestResponse restResponse) {
        this.restRequest = restRequest;
        this.restResponse = restResponse;
    }

    public RestRequest restRequest() {
        return restRequest;
    }

    public RestResponse restResponse() {
        return restResponse;
    }

}
