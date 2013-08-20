package net.csdn.junit;

import net.csdn.ServiceFramwork;
import net.csdn.common.exception.RenderFinish;
import net.csdn.common.settings.Settings;
import net.csdn.modules.http.RestController;
import net.csdn.modules.http.RestRequest;
import net.csdn.modules.http.RestResponse;
import net.csdn.modules.mock.MockRestRequest;
import net.csdn.modules.mock.MockRestResponse;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.csdn.common.collections.WowCollections.map;

/**
 * 6/28/13 WilliamZhu(allwefantasy@gmail.com)
 */
public class BaseControllerTest extends IocTest {

    public RestResponse runAction(String path, Map params, RestRequest.Method method) throws Exception {
        RestResponse response = new MockRestResponse();

        Map newParams = new HashMap();
        for (Object key : params.keySet()) {
            newParams.put(key, params.get(key).toString());
        }

        RestController controller = injector.getInstance(RestController.class);
        try {
            controller.dispatchRequest(new MockRestRequest(path, newParams, method, null), response);
        } catch (Exception e) {
            catchRenderFinish(e);
        }
        return response;
    }

    boolean disableMysql = injector.getInstance(Settings.class).getAsBoolean(ServiceFramwork.mode + ".datasources.mysql.disable", false);

    private void catchRenderFinish(Exception e) throws Exception {
        if (e instanceof RenderFinish) {
            if (!disableMysql) {
                commitTransaction();
            }
        } else if (e instanceof InvocationTargetException) {
            if (((InvocationTargetException) e).getTargetException() instanceof RenderFinish) {
                if (!disableMysql) {
                    commitTransaction();
                }
            } else {
                throw e;
            }
        } else {
            throw e;
        }
    }

    public RestResponse runAction(String path, String rawParamsStr, RestRequest.Method method) throws Exception {
        RestResponse response = new MockRestResponse();
        RestController controller = injector.getInstance(RestController.class);
        try {
            controller.dispatchRequest(new MockRestRequest(path, map(), method, rawParamsStr), response);
        } catch (Exception e) {
            catchRenderFinish(e);
        }
        return response;
    }

    public Map<RestRequest.Method, RestResponse> each(List<RestRequest.Method> methods, String path, Map params) throws Exception {
        Map<RestRequest.Method, RestResponse> maps = map();
        for (RestRequest.Method method : methods) {
            maps.put(method, runAction(path, params, method));
        }
        return maps;
    }

    public Map<RestRequest.Method, RestResponse> each(List<RestRequest.Method> methods, String path, String rawParamStr) throws Exception {
        Map<RestRequest.Method, RestResponse> maps = map();
        for (RestRequest.Method method : methods) {
            maps.put(method, runAction(path, rawParamStr, method));
        }
        return maps;
    }

    public RestResponse get(String path, Map params) throws Exception {
        return runAction(path, params, RestRequest.Method.GET);
    }

    public RestResponse post(String path, Map params) throws Exception {
        return runAction(path, params, RestRequest.Method.POST);
    }

    public RestResponse delete(String path, Map params) throws Exception {
        return runAction(path, params, RestRequest.Method.DELETE);
    }

    public RestResponse put(String path, Map params) throws Exception {
        return runAction(path, params, RestRequest.Method.PUT);
    }

    public RestResponse get(String path, String rawParamsStr) throws Exception {
        return runAction(path, rawParamsStr, RestRequest.Method.GET);
    }

    public RestResponse post(String path, String rawParamsStr) throws Exception {
        return runAction(path, rawParamsStr, RestRequest.Method.POST);
    }

    public RestResponse delete(String path, String rawParamsStr) throws Exception {
        return runAction(path, rawParamsStr, RestRequest.Method.DELETE);
    }

    public RestResponse put(String path, String rawParamsStr) throws Exception {
        return runAction(path, rawParamsStr, RestRequest.Method.PUT);
    }
}
