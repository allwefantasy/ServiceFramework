package net.csdn.modules.http.support;

import net.csdn.annotation.filter.AfterFilter;
import net.csdn.annotation.filter.AroundFilter;
import net.csdn.annotation.filter.BeforeFilter;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Compatibility view of {@link ControllerFilterPlan}. Order is the plan's
 * declaration order, including after filters. Request handling uses the
 * catalog stored on the application context instead of calling this per request.
 */
public class FilterHelper2 {

    public static Map<Method, Map<Class, List<Method>>> create(Class clzz) {
        ControllerFilterPlan plan = ControllerFilterPlan.compile(Collections.<Class<?>>singletonList(clzz));
        Map<Method, FilterChain> chains = plan.chains(clzz);
        Map<Method, Map<Class, List<Method>>> result = new LinkedHashMap<Method, Map<Class, List<Method>>>();
        for (Map.Entry<Method, FilterChain> entry : chains.entrySet()) {
            Map<Class, List<Method>> filters = new LinkedHashMap<Class, List<Method>>();
            filters.put(BeforeFilter.class, new ArrayList<Method>(entry.getValue().before()));
            filters.put(AroundFilter.class, new ArrayList<Method>(entry.getValue().around()));
            filters.put(AfterFilter.class, new ArrayList<Method>(entry.getValue().after()));
            result.put(entry.getKey(), filters);
        }
        return result;
    }
}
