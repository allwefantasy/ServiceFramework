package net.csdn.modules.http;

import net.csdn.ServiceFramwork;
import net.csdn.annotation.AroundFilter;
import net.csdn.annotation.BeforeFilter;
import net.csdn.common.collect.Tuple;
import net.csdn.common.logging.CSLogger;
import net.csdn.common.logging.Loggers;
import net.csdn.common.path.PathTrie;
import net.csdn.exception.ArgumentErrorException;
import net.csdn.exception.ExceptionHandler;
import net.csdn.exception.RecordNotFoundException;
import net.csdn.filter.FilterHelper;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static net.csdn.common.logging.support.MessageFormat.format;

/**
 * BlogInfo: william
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

        enhanceApplicationController(applicationController, request, restResponse);

        //check beforeFilter
        boolean containsAroundFilter = filter(handlerKey, applicationController);

        //invoke real
        if (!containsAroundFilter)
            handlerKey.v2().invoke(applicationController);

    }

    private void enhanceApplicationController(ApplicationController applicationController, final RestRequest request, RestResponse restResponse) throws Exception {
        Field field = ApplicationController.class.getDeclaredField("request");
        field.setAccessible(true);
        field.set(applicationController, request);
        field = ApplicationController.class.getDeclaredField("restResponse");
        field.setAccessible(true);
        field.set(applicationController, restResponse);
    }

    private boolean filter(Tuple<Class<ApplicationController>, Method> handlerKey, ApplicationController applicationController) throws Exception {
        //check beforeFilter
        Field[] fields = handlerKey.v1().getDeclaredFields();
        boolean containsAroundFilter = false;
        for (Field temp : fields) {
            if (temp.isAnnotationPresent(BeforeFilter.class)) {
                temp.setAccessible(true);
                Map beforeFilter = (Map) temp.get(null);

                String beforeMethod = temp.getName().substring(1);
                boolean shouldInvoke = false;
                if (beforeFilter.containsKey(FilterHelper.BeforeFilter.only)) {
                    List<String> list = (List) beforeFilter.get(FilterHelper.BeforeFilter.only);
                    shouldInvoke = list.contains(handlerKey.v2().getName());
                } else if(beforeFilter.containsKey(FilterHelper.BeforeFilter.except)) {
                    List<String> list = (List) beforeFilter.get(FilterHelper.BeforeFilter.except);
                    shouldInvoke = !list.contains(handlerKey.v2().getName());
                }else{
                    shouldInvoke = true;
                }

                if (shouldInvoke) {
                    Method beforeMethodFilter = handlerKey.v1().getDeclaredMethod(beforeMethod);
                    beforeMethodFilter.setAccessible(true);
                    beforeMethodFilter.invoke(applicationController);
                }

            }
            if (temp.isAnnotationPresent(AroundFilter.class)) {
                temp.setAccessible(true);
                Map beforeFilter = (Map) temp.get(null);

                String beforeMethod = temp.getName().substring(1);
                boolean shouldInvoke = false;
                if (beforeFilter.containsKey(FilterHelper.AroundFilter.only)) {
                    List<String> list = (List) beforeFilter.get(FilterHelper.AroundFilter.only);
                    shouldInvoke = list.contains(handlerKey.v2().getName());
                } else if (beforeFilter.containsKey(FilterHelper.AroundFilter.except)) {
                    List<String> list = (List) beforeFilter.get(FilterHelper.AroundFilter.except);
                    shouldInvoke = !list.contains(handlerKey.v2().getName());
                } else {
                    shouldInvoke = true;
                }
                if (shouldInvoke) {
                    Method aroundMethodFilter = handlerKey.v1().getDeclaredMethod(beforeMethod, Action.class);
                    aroundMethodFilter.setAccessible(true);
                    aroundMethodFilter.invoke(applicationController, new Action(handlerKey.v2(), applicationController));
                }
                containsAroundFilter = shouldInvoke;
            }
        }
        return containsAroundFilter;
    }

    public class Action {
        private Method method;
        private ApplicationController applicationController;

        public Action(Method method, ApplicationController applicationController) {
            this.method = method;
            this.applicationController = applicationController;
        }

        public void invoke() {
            try {
                method.invoke(applicationController);
            } catch (Exception e) {
                try {
                    ExceptionHandler.renderHandle(e);
                } catch (Exception e1) {
                    try {
                        throw e1;
                    } catch (Exception e2) {

                    }
                }
            }
        }
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
