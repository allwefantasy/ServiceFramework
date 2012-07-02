package net.csdn.modules.http;

import net.csdn.CsdnSearchException;
import net.csdn.bootstrap.Bootstrap;
import net.csdn.common.collect.Tuple;
import net.csdn.common.logging.CSLogger;
import net.csdn.common.logging.Loggers;
import net.csdn.common.path.PathTrie;
import net.csdn.exception.ArgumentErrorException;
import net.csdn.exception.RecordNotFoundException;
import org.apache.commons.beanutils.BeanUtils;

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

    private final PathTrie<Tuple<Class<BaseRestHandler>, Method>> getHandlers = new PathTrie<Tuple<Class<BaseRestHandler>, Method>>();
    private final PathTrie<Tuple<Class<BaseRestHandler>, Method>> postHandlers = new PathTrie<Tuple<Class<BaseRestHandler>, Method>>();
    private final PathTrie<Tuple<Class<BaseRestHandler>, Method>> putHandlers = new PathTrie<Tuple<Class<BaseRestHandler>, Method>>();
    private final PathTrie<Tuple<Class<BaseRestHandler>, Method>> deleteHandlers = new PathTrie<Tuple<Class<BaseRestHandler>, Method>>();
    private final PathTrie<Tuple<Class<BaseRestHandler>, Method>> headHandlers = new PathTrie<Tuple<Class<BaseRestHandler>, Method>>();
    private final PathTrie<Tuple<Class<BaseRestHandler>, Method>> optionsHandlers = new PathTrie<Tuple<Class<BaseRestHandler>, Method>>();

    public void registerHandler(RestRequest.Method method, String path, Tuple<Class<BaseRestHandler>, Method> handler) {
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
        final Tuple<Class<BaseRestHandler>, Method> handlerKey = getHandler(request);
        if (handlerKey == null) {
            throw new RecordNotFoundException(format("你请求的URL地址[{}]不存在", request.rawPath().toString()));
        }
        BaseRestHandler baseRestHandler = Bootstrap.injector.getInstance(handlerKey.v1());

        Field field = BaseRestHandler.class.getDeclaredField("request");
        field.setAccessible(true);
        field.set(baseRestHandler, request);
        field = BaseRestHandler.class.getDeclaredField("restResponse");
        field.setAccessible(true);
        field.set(baseRestHandler, restResponse);
        handlerKey.v2().invoke(baseRestHandler);

    }

    private Tuple<Class<BaseRestHandler>, Method> getHandler(RestRequest request) {
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
        // we use rawPath since we don't want to decode it while processing the path resolution
        // so we can handle things like:
        // my_index/my_type/http%3A%2F%2Fwww.google.com
        return request.rawPath();
    }


}
