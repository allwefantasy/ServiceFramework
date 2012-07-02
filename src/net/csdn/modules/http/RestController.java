package net.csdn.modules.http;

import net.csdn.ServiceFramwork;
import net.csdn.common.collect.Tuple;
import net.csdn.common.logging.CSLogger;
import net.csdn.common.logging.Loggers;
import net.csdn.common.path.PathTrie;
import net.csdn.exception.ArgumentErrorException;
import net.csdn.exception.RecordNotFoundException;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static net.csdn.common.logging.support.MessageFormat.format;

/**
 * User: william
 * Date: 11-9-5
 * Time: 下午4:24
 */
public class RestController {

    private CSLogger logger = Loggers.getLogger(getClass());

    private final PathTrie<Tuple<Class<ApplicationController>, Method>> getHandlers = new PathTrie<Tuple<Class<ApplicationController>, Method>>();
    private final PathTrie<Tuple<Class<ApplicationController>, Method>> postHandlers = new PathTrie<Tuple<Class<ApplicationController>, Method>>();
    private final PathTrie<Tuple<Class<ApplicationController>, Method>> putHandlers = new PathTrie<Tuple<Class<ApplicationController>, Method>>();
    private final PathTrie<Tuple<Class<ApplicationController>, Method>> deleteHandlers = new PathTrie<Tuple<Class<ApplicationController>, Method>>();
    private final PathTrie<Tuple<Class<ApplicationController>, Method>> headHandlers = new PathTrie<Tuple<Class<ApplicationController>, Method>>();
    private final PathTrie<Tuple<Class<ApplicationController>, Method>> optionsHandlers = new PathTrie<Tuple<Class<ApplicationController>, Method>>();

    public void registerHandler(RestRequest.Method method, String path, Tuple<Class<ApplicationController>, Method> handler) {
        switch (method) {
            case GET:
                getHandlers.insert(path, handler);
                break;
            case DELETE:
                deleteHandlers.insert(path, handler);
                break;
            case POST:
                postHandlers.insert(path, handler);
                break;
            case PUT:
                putHandlers.insert(path, handler);
                break;
            case OPTIONS:
                optionsHandlers.insert(path, handler);
                break;
            case HEAD:
                headHandlers.insert(path, handler);
                break;
            default:
                throw new ArgumentErrorException("Can't handle [" + method + "] for path [" + path + "]");
        }
    }


    public void dispatchRequest(final RestRequest request, RestResponse restResponse) throws Exception {
        final Tuple<Class<ApplicationController>, Method> handlerKey = getHandler(request);
        if (handlerKey == null) {
            throw new RecordNotFoundException(format("你请求的URL地址[{}]不存在", request.rawPath().toString()));
        }
        ApplicationController applicationController = ServiceFramwork.injector.getInstance(handlerKey.v1());

        Field field = ApplicationController.class.getDeclaredField("request");
        field.setAccessible(true);
        field.set(applicationController, request);
        field = ApplicationController.class.getDeclaredField("restResponse");
        field.setAccessible(true);
        field.set(applicationController, restResponse);
        handlerKey.v2().invoke(applicationController);

    }

    private Tuple<Class<ApplicationController>, Method> getHandler(RestRequest request) {
        String path = getPath(request);
        RestRequest.Method method = request.method();
        if (method == RestRequest.Method.GET) {
            return getHandlers.retrieve(path, request.params());
        } else if (method == RestRequest.Method.POST) {
            return postHandlers.retrieve(path, request.params());
        } else if (method == RestRequest.Method.PUT) {
            return putHandlers.retrieve(path, request.params());
        } else if (method == RestRequest.Method.DELETE) {
            return deleteHandlers.retrieve(path, request.params());
        } else if (method == RestRequest.Method.HEAD) {
            return headHandlers.retrieve(path, request.params());
        } else if (method == RestRequest.Method.OPTIONS) {
            return optionsHandlers.retrieve(path, request.params());
        } else {
            return null;
        }
    }

    private String getPath(RestRequest request) {
        return request.rawPath();
    }


}
