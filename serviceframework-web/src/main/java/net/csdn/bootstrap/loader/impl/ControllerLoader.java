package net.csdn.bootstrap.loader.impl;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import javassist.CtClass;
import javassist.bytecode.ClassFile;
import net.csdn.annotation.rest.At;
import net.csdn.annotation.rest.ErrorAction;
import net.csdn.annotation.rest.NoAction;
import net.csdn.bootstrap.ApplicationContext;
import net.csdn.bootstrap.ClassFiles;
import net.csdn.bootstrap.WebEnhancementMetadata;
import net.csdn.bootstrap.loader.Loader;
import net.csdn.common.collections.WowCollections;
import net.csdn.common.enhancer.EnhancementContext;
import net.csdn.common.enhancer.EnhancementDiagnostics;
import net.csdn.common.enhancer.EnhancementFailure;
import net.csdn.common.enhancer.EnhancementPlan;
import net.csdn.common.enhancer.EnhancementRules;
import net.csdn.common.enhancer.StartupPhaseTrace;
import net.csdn.common.logging.CSLogger;
import net.csdn.common.logging.Loggers;
import net.csdn.common.scan.ScanService;
import net.csdn.common.settings.Settings;
import net.csdn.filter.ControllerFilterRule;
import net.csdn.modules.controller.API;
import net.csdn.modules.http.ApplicationController;
import net.csdn.modules.http.RestController;
import net.csdn.modules.http.RestRequest;
import net.csdn.modules.http.support.ControllerFilterPlan;

import java.io.DataInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Enhances concrete controllers with {@code controller-filter}, defines them
 * through the application {@link net.csdn.common.enhancer.ClassDefiner}, then
 * registers routes. Classes that are not controllers are left to
 * {@link Class#forName(String, boolean, ClassLoader)} by the service and util
 * loaders. This loader does not call {@code toClass}.
 */
public class ControllerLoader implements Loader {

    private static final String CONTROLLER = "net.csdn.modules.http.ApplicationController";
    private final CSLogger logger = Loggers.getLogger(ControllerLoader.class);

    @Override
    public void load(Settings settings) throws Exception {
        final ApplicationContext application = ApplicationContext.require();
        Map<String, byte[]> bytes = new LinkedHashMap<String, byte[]>();
        Map<String, String> supers = new HashMap<String, String>();
        for (String item : WowCollections.split2(settings.get("application.controller"), ",")) {
            readPackage(application, item, bytes, supers);
        }
        List<String> names = controllerNames(bytes, supers, application.targetLoader());
        EnhancementRules rules = new EnhancementRules();
        rules.register(new ControllerFilterRule());
        for (int i = 0; i < application.controllerRules().size(); i++) {
            rules.register(application.controllerRules().get(i));
        }
        EnhancementPlan plan = rules.compile();
        EnhancementContext enhancement = application.enhancementContext();
        List<CtClass> types = new ArrayList<CtClass>();
        StartupPhaseTrace.Frame made = StartupPhaseTrace.open(enhancement, "controller.makeClass");
        try {
            for (int i = 0; i < names.size(); i++) {
                String name = names.get(i);
                CtClass type = enhancement.classPool().makeClass(new DataInputStream(
                        new java.io.ByteArrayInputStream(bytes.get(name))));
                enhancement.track(type);
                if (Modifier.isAbstract(type.getModifiers())) {
                    continue;
                }
                types.add(type);
            }
        } finally {
            made.close();
        }
        List<String> enhancedNames = new ArrayList<String>();
        for (int i = 0; i < types.size(); i++) {
            enhancedNames.add(types.get(i).getName());
        }
        MetadataBoundary boundary = MetadataBoundary.note(enhancement, enhancedNames);
        try {
            for (int i = 0; i < types.size(); i++) {
                plan.apply(types.get(i), enhancement);
            }
            List<Class<?>> defined = new ArrayList<Class<?>>();
            for (int i = 0; i < types.size(); i++) {
                Class<?> loaded = enhancement.define(types.get(i));
                application.markClassesDefined();
                defined.add(loaded);
                application.addController(loaded);
                logger.info("controller load :    " + loaded.getName());
            }
            bindControllers(application, enhancement, settings, defined);
        } finally {
            boundary.restore(enhancement);
        }
    }

    private void bindControllers(
            ApplicationContext application,
            EnhancementContext enhancement,
            Settings settings,
            List<Class<?>> defined) {
        List<Class<?>> bound = new ArrayList<Class<?>>();
        Set<String> seen = new HashSet<String>();
        for (int i = 0; i < defined.size(); i++) {
            addBound(bound, seen, defined.get(i));
        }
        for (String item : WowCollections.split2(settings.get("application.controller.default",
                "net.csdn.api.controller.SystemInfoController,net.csdn.api.controller.APIDescController"), ",")) {
            addBound(bound, seen, loadExisting(application.targetLoader(), item));
        }
        for (String item : WowCollections.split2(settings.get("application.controllerNames"), ",")) {
            addBound(bound, seen, loadExisting(application.targetLoader(), item));
        }
        try {
            application.replaceFilters(ControllerFilterPlan.compile(bound));
        } catch (EnhancementFailure failure) {
            MetadataBoundary.record(enhancement, failure);
            throw failure;
        }
        for (int i = 0; i < bound.size(); i++) {
            application.controllerModules().add(bindAction(bound.get(i)));
        }
    }

    /**
     * While controller rules run, events keep a revision the caller already
     * stored. {@code web-1} is written only when that revision is absent, and
     * it is the controller class-name format, not an application revision.
     * The digest is always the sorted controller class names. A previous
     * revision and digest are put back afterwards so a later event does not
     * keep this digest. Disabled diagnostics skip the class-name digest.
     */
    private static final class MetadataBoundary {
        private final boolean noted;
        private final String previousVersion;
        private final String previousDigest;

        private MetadataBoundary(boolean noted, String previousVersion, String previousDigest) {
            this.noted = noted;
            this.previousVersion = previousVersion;
            this.previousDigest = previousDigest;
        }

        private static MetadataBoundary note(EnhancementContext context, List<String> classNames) {
            EnhancementDiagnostics diagnostics = context.diagnostics();
            if (diagnostics == null || !diagnostics.enabled()) {
                return new MetadataBoundary(false, null, null);
            }
            String previousVersion = diagnostics.configVersion();
            String previousDigest = diagnostics.schemaDigest();
            String revision = previousVersion != null ? previousVersion : WebEnhancementMetadata.VERSION;
            diagnostics.noteSafeMetadata(revision, WebEnhancementMetadata.controllerDigest(classNames));
            return new MetadataBoundary(true, previousVersion, previousDigest);
        }

        private void restore(EnhancementContext context) {
            if (!noted || previousVersion == null || context == null || context.isClosed()) {
                return;
            }
            EnhancementDiagnostics diagnostics = context.diagnostics();
            if (diagnostics == null || !diagnostics.enabled()) {
                return;
            }
            diagnostics.noteSafeMetadata(previousVersion, previousDigest);
        }

        private static void record(EnhancementContext context, EnhancementFailure failure) {
            if (context == null || context.isClosed() || failure == null) {
                return;
            }
            EnhancementDiagnostics diagnostics = context.diagnostics();
            if (diagnostics == null || !diagnostics.enabled()) {
                return;
            }
            diagnostics.recordFailure(
                    failure.getClassName(),
                    failure.getRuleId(),
                    1,
                    failure.getPhase(),
                    failure,
                    0L);
        }
    }

    private static void addBound(List<Class<?>> bound, Set<String> seen, Class<?> type) {
        if (type == null || !seen.add(type.getName() + "@" + System.identityHashCode(type.getClassLoader()))) {
            return;
        }
        if (Modifier.isAbstract(type.getModifiers())) {
            return;
        }
        if (!ApplicationController.class.isAssignableFrom(type) || type == ApplicationController.class) {
            return;
        }
        bound.add(type);
    }

    private static Class<?> loadExisting(ClassLoader loader, String name) {
        if (name == null || name.trim().length() == 0) {
            return null;
        }
        try {
            return Class.forName(name.trim(), false, loader);
        } catch (ClassNotFoundException e) {
            throw new EnhancementFailure(
                    EnhancementFailure.Category.CONFIGURATION,
                    name,
                    "controller-filter",
                    "scan",
                    "controller class was not found",
                    e);
        }
    }

    private void readPackage(
            final ApplicationContext application,
            String packageName,
            final Map<String, byte[]> bytes,
            final Map<String, String> supers) throws IOException {
        if (packageName == null || packageName.trim().length() == 0) {
            return;
        }
        StartupPhaseTrace.Frame scan = StartupPhaseTrace.open(application.enhancementContext(), "scan.controller");
        try {
        application.scanService().scanArchives(packageName.trim(), new ScanService.LoadClassEnhanceCallBack() {
            @Override
            public Class loaded(DataInputStream classFile) {
                try {
                    byte[] data = ClassFiles.read(classFile);
                    ClassFile parsed = ClassFiles.parse(data);
                    if (bytes.containsKey(parsed.getName())) {
                        return null;
                    }
                    bytes.put(parsed.getName(), data);
                    supers.put(parsed.getName(), parsed.getSuperclass());
                    return null;
                } catch (IOException e) {
                    throw new EnhancementFailure(
                            EnhancementFailure.Category.SCAN,
                            packageName,
                            "controller-filter",
                            "scan",
                            "controller class bytes were not read",
                            e);
                }
            }
        });
        } finally {
            scan.close();
        }
    }

    private static List<String> controllerNames(
            Map<String, byte[]> bytes,
            Map<String, String> supers,
            ClassLoader loader) {
        List<String> matched = new ArrayList<String>();
        for (String name : bytes.keySet()) {
            if (isController(name, supers, loader, new HashSet<String>())) {
                matched.add(name);
            }
        }
        List<String> ordered = new ArrayList<String>();
        Set<String> placed = new HashSet<String>();
        for (int i = 0; i < matched.size(); i++) {
            place(matched.get(i), matched, supers, ordered, placed, new HashSet<String>());
        }
        return ordered;
    }

    private static void place(
            String name,
            List<String> matched,
            Map<String, String> supers,
            List<String> ordered,
            Set<String> placed,
            Set<String> stack) {
        if (!placed.add(name)) {
            return;
        }
        if (!stack.add(name)) {
            throw new EnhancementFailure(
                    EnhancementFailure.Category.CONFLICT,
                    name,
                    "controller-filter",
                    "scan",
                    "controller inheritance cycle",
                    null);
        }
        String parent = supers.get(name);
        if (parent != null && matched.contains(parent)) {
            place(parent, matched, supers, ordered, placed, stack);
        }
        ordered.add(name);
    }

    private static boolean isController(
            String name,
            Map<String, String> supers,
            ClassLoader loader,
            Set<String> stack) {
        if (CONTROLLER.equals(name) || !stack.add(name)) {
            return false;
        }
        String parent = supers.get(name);
        if (parent == null) {
            return false;
        }
        if (CONTROLLER.equals(parent)) {
            return true;
        }
        if (supers.containsKey(parent)) {
            return isController(parent, supers, loader, stack);
        }
        try {
            Class<?> type = Class.forName(parent, false, loader);
            return ApplicationController.class.isAssignableFrom(type);
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static Module bindAction(final Class clzz) {
        return new AbstractModule() {
            @Override
            protected void configure() {
                try {
                    ApplicationContext application = ApplicationContext.require();
                    Method[] methods = clzz.getDeclaredMethods();
                    boolean bound = false;
                    for (int i = 0; i < methods.length; i++) {
                        Method method = methods[i];
                        if (Modifier.isPrivate(method.getModifiers())) {
                            continue;
                        }
                        RestController restController = application.injector().getInstance(RestController.class);
                        API api = application.injector().getInstance(API.class);
                        NoAction noAction = method.getAnnotation(NoAction.class);
                        if (noAction != null) {
                            if (restController.defaultHandlerKey() != null) {
                                throw new EnhancementFailure(
                                        EnhancementFailure.Category.CONFIGURATION,
                                        clzz.getName(),
                                        "controller-filter",
                                        "route",
                                        "only one default action can be defined; current is " + method.getName(),
                                        null);
                            }
                            restController.setDefaultHandlerKey(
                                    new net.csdn.common.collect.Tuple<Class<ApplicationController>, Method>(clzz, method));
                        }
                        ErrorAction errorAction = method.getAnnotation(ErrorAction.class);
                        if (errorAction != null) {
                            if (restController.errorHandlerKey() != null) {
                                throw new EnhancementFailure(
                                        EnhancementFailure.Category.CONFIGURATION,
                                        clzz.getName(),
                                        "controller-filter",
                                        "route",
                                        "only one error action can be defined; current is " + method.getName(),
                                        null);
                            }
                            restController.setErrorHandlerKey(
                                    new net.csdn.common.collect.Tuple<Class<ApplicationController>, Method>(clzz, method));
                        }
                        At at = method.getAnnotation(At.class);
                        if (at == null) {
                            continue;
                        }
                        String url = at.path()[0];
                        RestRequest.Method[] httpMethods = at.types();
                        for (int h = 0; h < httpMethods.length; h++) {
                            net.csdn.common.collect.Tuple<Class<ApplicationController>, Method> tuple =
                                    new net.csdn.common.collect.Tuple<Class<ApplicationController>, Method>(clzz, method);
                            restController.registerHandler(httpMethods[h], url, tuple);
                            api.addPath(tuple.v2());
                        }
                        if (!bound) {
                            bind(clzz);
                            bound = true;
                        }
                    }
                } catch (EnhancementFailure failure) {
                    throw failure;
                } catch (RuntimeException e) {
                    throw new EnhancementFailure(
                            EnhancementFailure.Category.CONFIGURATION,
                            clzz.getName(),
                            "controller-filter",
                            "route",
                            e.getMessage() == null ? e.getClass().getName() : e.getMessage(),
                            e);
                }
            }
        };
    }
}
