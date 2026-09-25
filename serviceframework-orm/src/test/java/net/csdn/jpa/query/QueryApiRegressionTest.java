package net.csdn.jpa.query;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.JUnitCore;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;

/**
 * JUnit4 regression for the companion query API. Discovered by Surefire as
 * {@code *Test}. Every dependency (query classes, javax.persistence API, Scala
 * jars) is resolved from the runtime classpath through each class's protection
 * domain, so nothing depends on a user directory layout. All compilation
 * output lands in a unique {@link TemporaryFolder} that is deleted afterwards.
 *
 * <p>javac is invoked with {@code --release 8} on JDK 9+ and
 * {@code -source 8 -target 8} on JDK 8. The Scala section runs only when the
 * scala-compiler classpath of this JVM actually contains the jars; the version
 * exercised is logged, because the supported version is the one the build
 * resolves, not a hardcoded one.
 *
 * <p>It does not start MySQL or Hibernate and does not claim a database test
 * passed. The production EntityManager path
 * {@code JPA.getJPAConfig().getJPAContext().em()} is checked in bytecode only;
 * executions use the explicit-EntityManager overload.
 */
public class QueryApiRegressionTest {

    private static final String MALICIOUS = "a' OR 1=1 --\n\"/*";

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private File mainClasses;
    private File jpaApi;
    private File work;
    private PrintWriter log;

    public static void main(String[] args) {
        JUnitCore.main(QueryApiRegressionTest.class.getName());
    }

    @Before
    public void setUp() throws Exception {
        mainClasses = codeSource(ServiceFrameworkQueryProcessor.class);
        jpaApi = codeSource(EntityManager.class);
        work = temp.newFolder("cases");
        log = new PrintWriter(new FileOutputStream(new File(temp.getRoot(), "self-test.log")), true);
        logLine("java.specification.version=" + System.getProperty("java.specification.version"));
        logLine("mainClasses=" + mainClasses.getAbsolutePath());
        logLine("jpaApi=" + jpaApi.getAbsolutePath());
    }

    @After
    public void tearDown() {
        if (log != null) {
            log.flush();
            log.close();
        }
    }

    @Test
    public void sharedContractValidation() {
        section("shared contract");
        QueryFieldView illegal = new SimpleField("status", "java.lang.String", false, false, false,
                "excluded by the dynamic finder field filter (static)");
        Map<String, QueryFieldView> fields = new LinkedHashMap<String, QueryFieldView>();
        fields.put("status", illegal);
        try {
            QuerySchemas.compile(new MapModel("demo.StaticModel", fields),
                    Collections.singletonList(new QueryMethodSpec("findByStatus",
                            Collections.singletonList("status"), Collections.<String>emptyList())));
            Assert.fail("static field was accepted");
        } catch (QueryDeclarationException ex) {
            Assert.assertTrue(ex.getMessage(), ex.getMessage().contains("demo.StaticModel"));
            Assert.assertTrue(ex.getMessage(), ex.getMessage().contains("findByStatus"));
            Assert.assertTrue(ex.getMessage(), ex.getMessage().contains("status"));
            Assert.assertTrue(ex.getMessage(), ex.getMessage().contains("static"));
        }
        QueryPredicate status = new QueryPredicate("status", "java.lang.String", false, QueryOperator.EQ, "p0");
        QueryPredicate tenant = new QueryPredicate("tenantId", "java.lang.Long", false, QueryOperator.EQ, "p1");
        QueryDescriptor descriptor = new QueryDescriptor("demo.Order", "findByStatusAndTenant",
                Arrays.asList(status, tenant),
                Collections.singletonList(new QuerySort("id", QueryDirection.ASC)));
        String jpql = QueryJpql.render("sales.OrderEntity", descriptor, new boolean[]{true, false});
        Assert.assertEquals(
                "SELECT e FROM sales.OrderEntity e WHERE e.status IS NULL AND e.tenantId = :p1 ORDER BY e.id ASC",
                jpql);
        Assert.assertTrue(jpql, jpql.indexOf(MALICIOUS) < 0);
        try {
            QueryJpql.render("sales.OrderEntity; drop", descriptor, new boolean[]{true, false});
            Assert.fail("unsafe entity name was rendered");
        } catch (QueryDeclarationException ex) {
            Assert.assertTrue(ex.getMessage(), ex.getMessage().indexOf("SELECT e FROM") < 0);
        }
    }

    @Test
    public void processorIsNotServiceRegistered() throws Exception {
        section("processor registration");
        if (mainClasses.isFile()) {
            JarFile jar = new JarFile(mainClasses);
            try {
                Assert.assertNull("processor service entry must not exist in " + mainClasses,
                        jar.getEntry("META-INF/services/javax.annotation.processing.Processor"));
            } finally {
                jar.close();
            }
        } else {
            Assert.assertFalse("processor service file must not exist",
                    new File(mainClasses, "META-INF/services/javax.annotation.processing.Processor").exists());
        }
        File userDir = new File(System.getProperty("user.dir"));
        File[] candidates = new File[]{
                new File(userDir, "src/main/resources/META-INF/services/javax.annotation.processing.Processor"),
                new File(userDir, "src/main/java/META-INF/services/javax.annotation.processing.Processor"),
                new File(userDir, "serviceframework-orm/src/main/resources/META-INF/services/javax.annotation.processing.Processor"),
                new File(userDir, "serviceframework-orm/src/main/java/META-INF/services/javax.annotation.processing.Processor")
        };
        for (int i = 0; i < candidates.length; i++) {
            Assert.assertFalse("service file must not exist: " + candidates[i], candidates[i].exists());
        }
    }

    @Test
    public void processorDoesNotClassForNameModels() throws Exception {
        section("processor does not Class.forName");
        File sourceRoot = querySourceDir();
        Assume.assumeTrue("query source tree not found next to " + System.getProperty("user.dir"),
                sourceRoot != null);
        File[] files = sourceRoot.listFiles();
        Assert.assertNotNull("query source root missing", files);
        for (int i = 0; i < files.length; i++) {
            if (!files[i].getName().endsWith(".java")) {
                continue;
            }
            String text = read(files[i]);
            Assert.assertFalse(files[i].getName() + " calls Class.forName",
                    text.indexOf("Class.forName(") >= 0);
        }
    }

    @Test
    public void generatesCompanionsAndSamePhaseCallers() throws Exception {
        section("positive javac: models and callers in one run");
        CompileResult result = compilePositive();

        File generated = new File(result.sourceOut, "net/csdn/jpa/query/fixture/OrderEntityQueries.java");
        String source = read(generated);
        logLine("--- OrderEntityQueries.java ---");
        logLine(source);
        Assert.assertTrue(source,
                source.contains("findByStatusAndTenant(java.lang.String p0, java.lang.Long p1, int offset, int limit)"));
        Assert.assertTrue(source,
                source.contains("findByStatusAndTenant(javax.persistence.EntityManager entityManager, java.lang.String p0, java.lang.Long p1, int offset, int limit)"));
        Assert.assertTrue(source,
                source.contains("findByWarehouse(java.lang.String p0, int offset, int limit)"));
        Assert.assertTrue(source, source.indexOf("Integer status") < 0);
        Assert.assertTrue(source, source.indexOf(MALICIOUS) < 0);
        Assert.assertTrue("anchor missing",
                new File(result.sourceOut, "net/csdn/jpa/query/fixture/ServiceFrameworkPackageAnchor.java").isFile());
        Assert.assertEquals("anchor count", 1, countFilesNamed(result.sourceOut, "ServiceFrameworkPackageAnchor.java"));
        Assert.assertTrue(result.diagnostics,
                result.diagnostics.contains("generated net.csdn.jpa.query.fixture.OrderEntityQueries"));
        Assert.assertTrue(result.diagnostics,
                result.diagnostics.contains("generated net.csdn.jpa.query.fixture.NoteEntityQueries"));

        File classFile = new File(result.classOut, "net/csdn/jpa/query/fixture/OrderEntityQueries.class");
        int major = classMajor(classFile);
        logLine("OrderEntityQueries major=" + major);
        Assert.assertEquals("class major", 52, major);
        String javap = javap(result.classOut, "net.csdn.jpa.query.fixture.OrderEntityQueries");
        logLine("--- javap OrderEntityQueries ---");
        logLine(javap);
        Assert.assertTrue("default overload signature missing",
                javap.contains("(Ljava/lang/String;Ljava/lang/Long;II)Ljava/util/List;"));
        Assert.assertTrue("EntityManager overload signature missing",
                javap.contains("(Ljavax/persistence/EntityManager;Ljava/lang/String;Ljava/lang/Long;II)Ljava/util/List;"));
        Assert.assertTrue(javap, javap.contains("GeneratedQueryExecutor"));
        Assert.assertTrue("malicious constant in bytecode", javap.indexOf(MALICIOUS) < 0);
        String entityJavap = javap(result.classOut, "net.csdn.jpa.query.fixture.OrderEntity");
        Assert.assertTrue(entityJavap, entityJavap.contains("RuntimeVisibleAnnotations"));
        Assert.assertTrue(entityJavap, entityJavap.contains("findByStatusAndTenant"));
        Assert.assertTrue("same-phase same-package caller missing",
                new File(result.classOut, "net/csdn/jpa/query/fixture/SamePackageCaller.class").isFile());
        Assert.assertTrue("same-phase cross-package caller missing",
                new File(result.classOut, "net/csdn/jpa/query/fixture/client/OrderQueryCaller.class").isFile());

        String bridge = javap(mainClasses, "net.csdn.jpa.query.JpaEntityManagers");
        logLine("--- javap JpaEntityManagers ---");
        logLine(bridge);
        Assert.assertTrue(bridge, bridge.contains("getJPAConfig"));
        Assert.assertTrue(bridge, bridge.contains("getJPAContext"));
        Assert.assertTrue(bridge, bridge.contains("em"));

        try {
            GeneratedQueryExecutor.class.getDeclaredField("entityManagerOverride");
            Assert.fail("global test EntityManager field still exists");
        } catch (NoSuchFieldException expected) {
            // removed on purpose: no shared EntityManager state
        }
        try {
            GeneratedQueryExecutor.class.getDeclaredMethod("overrideEntityManager", EntityManager.class);
            Assert.fail("global test EntityManager hook still exists");
        } catch (NoSuchMethodException expected) {
            // removed on purpose
        }
    }

    @Test
    public void twoPhaseCallersCompileAgainstCompanionClasses() throws Exception {
        section("two-phase javac callers");
        File modelInput = new File(work, "phase1/input");
        copyFixture(modelInput, "OrderEntityBase.java");
        copyFixture(modelInput, "OrderEntity.java");
        CompileResult models = compile("phase1", modelInput, true);
        Assert.assertTrue("phase-1 compile failed\n" + models.diagnostics, models.success);

        File callerInput = new File(work, "phase2/input");
        write(new File(callerInput, "net/csdn/jpa/query/fixture/TwoPhaseSamePackageCaller.java"),
                twoPhaseSamePackageCallerSource());
        write(new File(callerInput, "net/csdn/jpa/query/fixture/client/TwoPhaseCaller.java"),
                twoPhaseCallerSource());
        CompileResult callers = compile("phase2", callerInput, false, models.classOut);
        Assert.assertTrue("two-phase callers failed\n" + callers.diagnostics, callers.success);
        Assert.assertTrue(new File(callers.classOut,
                "net/csdn/jpa/query/fixture/TwoPhaseSamePackageCaller.class").isFile());
        Assert.assertTrue(new File(callers.classOut,
                "net/csdn/jpa/query/fixture/client/TwoPhaseCaller.class").isFile());
    }

    @Test
    public void explicitEntityManagerExecutesAndIsolates() throws Exception {
        section("execute generated callers with explicit EntityManager");
        CompileResult positive = compilePositive();
        URLClassLoader loader = new URLClassLoader(new URL[]{positive.classOut.toURI().toURL()},
                getClass().getClassLoader());
        try {
            Recording recording = new Recording();
            recording.entityNames.put("net.csdn.jpa.query.fixture.OrderEntity", "sales.OrderEntity");
            recording.entityNames.put("net.csdn.jpa.query.fixture.HitCounter", "sales.HitCounter");
            recording.entityNames.put("net.csdn.jpa.query.fixture.ItemEntity", "sales.ItemEntity");

            Class<?> caller = loader.loadClass("net.csdn.jpa.query.fixture.client.OrderQueryCaller");
            Method find = caller.getMethod("find", EntityManager.class, String.class, Long.class, int.class, int.class);
            recording.reset();
            Object marker = new Object();
            recording.results.add(marker);
            Object returned = find.invoke(null, recording.entityManager, MALICIOUS, Long.valueOf(42L),
                    Integer.valueOf(20), Integer.valueOf(10));
            Assert.assertSame("caller did not return the query result list", recording.results, returned);
            Assert.assertEquals("createQuery calls", 1, recording.createQueryCalls);
            Assert.assertEquals(
                    "SELECT e FROM sales.OrderEntity e WHERE e.status = :p0 AND e.tenantId = :p1 ORDER BY e.id ASC",
                    recording.jpql);
            Assert.assertTrue(recording.jpql, recording.jpql.indexOf(MALICIOUS) < 0);
            Assert.assertNotNull(recording.resultClass);
            Assert.assertEquals("net.csdn.jpa.query.fixture.OrderEntity", recording.resultClass.getName());
            Assert.assertEquals("bounds " + recording.bounds, 2, recording.bounds.size());
            Assert.assertEquals("p0", recording.bounds.get(0).name);
            Assert.assertEquals(MALICIOUS, recording.bounds.get(0).value);
            Assert.assertEquals(String.class, recording.bounds.get(0).nameType);
            Assert.assertEquals("p1", recording.bounds.get(1).name);
            Assert.assertEquals(Long.valueOf(42L), recording.bounds.get(1).value);
            Assert.assertEquals(20, recording.first);
            Assert.assertEquals(10, recording.max);

            recording.reset();
            find.invoke(null, recording.entityManager, null, Long.valueOf(5L), Integer.valueOf(0), Integer.valueOf(1));
            Assert.assertEquals(
                    "SELECT e FROM sales.OrderEntity e WHERE e.status IS NULL AND e.tenantId = :p1 ORDER BY e.id ASC",
                    recording.jpql);
            Assert.assertEquals(1, recording.bounds.size());
            Assert.assertEquals("p1", recording.bounds.get(0).name);

            Method region = loader.loadClass("net.csdn.jpa.query.fixture.OrderEntityQueries")
                    .getMethod("findByRegion", EntityManager.class, String.class, int.class, int.class);
            recording.reset();
            region.invoke(null, recording.entityManager, "east", Integer.valueOf(1), Integer.valueOf(2));
            Assert.assertEquals(
                    "SELECT e FROM sales.OrderEntity e WHERE e.region = :p0 ORDER BY e.id DESC, e.status ASC",
                    recording.jpql);

            recording.reset();
            Class<?> orderType = loader.loadClass("net.csdn.jpa.query.fixture.OrderEntity");
            try {
                executeRaw(recording.entityManager, orderType, "findByStatusAndTenant",
                        new Object[]{"ok", Integer.valueOf(1)}, 0, 1);
                Assert.fail("Integer was accepted for Long tenantId");
            } catch (IllegalArgumentException ex) {
                Assert.assertTrue(ex.getMessage(), ex.getMessage().contains("tenantId"));
                Assert.assertTrue(ex.getMessage(), ex.getMessage().contains("java.lang.Long"));
                Assert.assertEquals("typed mismatch still created a query", 0, recording.createQueryCalls);
            }

            recording.reset();
            try {
                find.invoke(null, recording.entityManager, "ok", Long.valueOf(1L), Integer.valueOf(-1), Integer.valueOf(1));
                Assert.fail("negative offset accepted");
            } catch (InvocationTargetException ex) {
                Assert.assertTrue(String.valueOf(ex.getCause()),
                        String.valueOf(ex.getCause()).contains("offset"));
                Assert.assertEquals("bad offset created a query", 0, recording.createQueryCalls);
            }

            Class<?> hit = loader.loadClass("net.csdn.jpa.query.fixture.HitCounter");
            recording.reset();
            try {
                executeRaw(recording.entityManager, hit, "findByHits", new Object[]{null}, 0, 1);
                Assert.fail("primitive null accepted");
            } catch (IllegalArgumentException ex) {
                Assert.assertTrue(ex.getMessage(), ex.getMessage().contains("hits"));
                Assert.assertTrue(ex.getMessage(), ex.getMessage().contains("primitive"));
                Assert.assertEquals("primitive null created a query", 0, recording.createQueryCalls);
            }
            recording.reset();
            Object marker2 = new Object();
            recording.results.add(marker2);
            List<?> hits = executeRaw(recording.entityManager, hit, "findByHits", new Object[]{Integer.valueOf(3)}, 0, 2);
            Assert.assertSame("direct EntityManager overload did not return the double list", recording.results, hits);
            Assert.assertEquals("SELECT e FROM sales.HitCounter e WHERE e.hits = :p0", recording.jpql);
            Assert.assertEquals(1, recording.bounds.size());
            Assert.assertEquals(Integer.valueOf(3), recording.bounds.get(0).value);

            Class<?> color = loader.loadClass("net.csdn.jpa.query.fixture.Color");
            Object red = Enum.class.getMethod("valueOf", Class.class, String.class).invoke(null, color, "RED");
            Method byColor = loader.loadClass("net.csdn.jpa.query.fixture.ItemEntityQueries")
                    .getMethod("findByColor", EntityManager.class, color, int.class, int.class);
            recording.reset();
            byColor.invoke(null, recording.entityManager, red, Integer.valueOf(0), Integer.valueOf(1));
            Assert.assertEquals("SELECT e FROM sales.ItemEntity e WHERE e.color = :p0", recording.jpql);
            Assert.assertEquals(1, recording.bounds.size());
            Assert.assertSame(red, recording.bounds.get(0).value);

            Class<?> order = loader.loadClass("net.csdn.jpa.query.fixture.OrderEntity");
            QueryMetadata metadata = RuntimeQueryMetadata.read(order);
            Assert.assertEquals("net.csdn.jpa.query.fixture.OrderEntity", metadata.getModelName());
            QueryDescriptor method = metadata.require("findByStatusAndTenant");
            Assert.assertEquals(2, method.getPredicates().size());
            Assert.assertEquals("java.lang.String", method.getPredicates().get(0).getJavaType());
            Assert.assertEquals("java.lang.Long", method.getPredicates().get(1).getJavaType());
            Assert.assertSame(QueryOperator.EQ, method.getPredicates().get(0).getOperator());
            Assert.assertEquals(1, method.getSorts().size());
            Assert.assertSame(QueryDirection.ASC, method.getSorts().get(0).getDirection());
            try {
                RuntimeQueryMetadata.read(loader.loadClass("net.csdn.jpa.query.fixture.ServiceFrameworkPackageAnchor"));
                Assert.fail("anchor was accepted as query metadata");
            } catch (IllegalArgumentException ex) {
                Assert.assertTrue(ex.getMessage(), ex.getMessage().contains("ServiceFrameworkPackageAnchor"));
                Assert.assertTrue(ex.getMessage(), ex.getMessage().contains("ClassDefiner"));
            }
        } finally {
            loader.close();
        }
        concurrentEntityManagerIsolation(positive);
    }

    private void concurrentEntityManagerIsolation(CompileResult positive) throws Exception {
        section("two concurrent EntityManagers stay isolated");
        URLClassLoader loader = new URLClassLoader(new URL[]{positive.classOut.toURI().toURL()},
                getClass().getClassLoader());
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            final Method find = loader.loadClass("net.csdn.jpa.query.fixture.OrderEntityQueries")
                    .getMethod("findByStatusAndTenant", EntityManager.class, String.class, Long.class,
                            int.class, int.class);
            for (int round = 0; round < 8; round++) {
                final Recording first = new Recording();
                final Recording second = new Recording();
                first.entityNames.put("net.csdn.jpa.query.fixture.OrderEntity", "sales.OrderEntity");
                second.entityNames.put("net.csdn.jpa.query.fixture.OrderEntity", "sales.OrderEntity");
                CyclicBarrier gate = new CyclicBarrier(2);
                first.gate = gate;
                second.gate = gate;
                first.results.add("first-result-" + round);
                second.results.add("second-result-" + round);
                Future<Object> callFirst = pool.submit(new Callable<Object>() {
                    public Object call() throws Exception {
                        return find.invoke(null, first.entityManager, "alpha", Long.valueOf(1L),
                                Integer.valueOf(0), Integer.valueOf(5));
                    }
                });
                Future<Object> callSecond = pool.submit(new Callable<Object>() {
                    public Object call() throws Exception {
                        return find.invoke(null, second.entityManager, "beta", Long.valueOf(2L),
                                Integer.valueOf(3), Integer.valueOf(7));
                    }
                });
                Object firstReturned = callFirst.get(60, TimeUnit.SECONDS);
                Object secondReturned = callSecond.get(60, TimeUnit.SECONDS);
                Assert.assertSame("first call returned another EntityManager's results", first.results, firstReturned);
                Assert.assertSame("second call returned another EntityManager's results", second.results, secondReturned);
                Assert.assertEquals("SELECT e FROM sales.OrderEntity e WHERE e.status = :p0 AND e.tenantId = :p1 ORDER BY e.id ASC",
                        first.jpql);
                Assert.assertEquals(first.jpql, second.jpql);
                Assert.assertEquals("alpha", first.bounds.get(0).value);
                Assert.assertEquals("beta", second.bounds.get(0).value);
                Assert.assertEquals(Long.valueOf(1L), first.bounds.get(1).value);
                Assert.assertEquals(Long.valueOf(2L), second.bounds.get(1).value);
                Assert.assertEquals(0, first.first);
                Assert.assertEquals(5, first.max);
                Assert.assertEquals(3, second.first);
                Assert.assertEquals(7, second.max);
                Assert.assertEquals(1, first.createQueryCalls);
                Assert.assertEquals(1, second.createQueryCalls);
            }
        } finally {
            pool.shutdownNow();
            loader.close();
        }
    }

    @Test
    public void fieldNamesCollidingWithGeneratedParametersCompile() throws Exception {
        section("fields named offset/limit/entityManager");
        File input = new File(work, "collision/input");
        write(new File(input, "net/csdn/jpa/query/fixture/CollisionEntity.java"), collisionSource());
        CompileResult result = compile("collision", input, true);
        Assert.assertTrue("collision compile failed\n" + result.diagnostics, result.success);
        String source = read(new File(result.sourceOut, "net/csdn/jpa/query/fixture/CollisionEntityQueries.java"));
        logLine(source);
        Assert.assertTrue(source,
                source.contains("findByOffset(java.lang.String p0, int offset, int limit)"));
        Assert.assertTrue(source,
                source.contains("findByLimitAndEntityManager(javax.persistence.EntityManager entityManager, java.lang.String p0, java.lang.String p1, int offset, int limit)"));

        URLClassLoader loader = new URLClassLoader(new URL[]{result.classOut.toURI().toURL()},
                getClass().getClassLoader());
        try {
            Recording recording = new Recording();
            recording.entityNames.put("net.csdn.jpa.query.fixture.CollisionEntity", "sales.CollisionEntity");
            Class<?> queries = loader.loadClass("net.csdn.jpa.query.fixture.CollisionEntityQueries");
            Method find = queries.getMethod("findByLimitAndEntityManager",
                    EntityManager.class, String.class, String.class, int.class, int.class);
            recording.reset();
            find.invoke(null, recording.entityManager, "cap", "own-em", Integer.valueOf(0), Integer.valueOf(2));
            Assert.assertEquals("SELECT e FROM sales.CollisionEntity e WHERE e.limit = :p0 AND e.entityManager = :p1",
                    recording.jpql);
            Assert.assertEquals("cap", recording.bounds.get(0).value);
            Assert.assertEquals("own-em", recording.bounds.get(1).value);
        } finally {
            loader.close();
        }
    }

    @Test
    public void scalaCallerCompilesAndRuns() throws Exception {
        section("scala caller");
        File scalaLibrary;
        File scalaReflect;
        File scalaCompiler;
        try {
            scalaLibrary = codeSource(Class.forName("scala.Option"));
            scalaReflect = codeSource(Class.forName("scala.reflect.api.Mirror"));
            scalaCompiler = codeSource(Class.forName("scala.tools.nsc.Main"));
        } catch (ClassNotFoundException missing) {
            Assume.assumeNoException("scala compiler jars are not on the test classpath", missing);
            return;
        }
        logLine("scalaLibrary=" + scalaLibrary.getAbsolutePath());
        logLine("scalaReflect=" + scalaReflect.getAbsolutePath());
        logLine("scalaCompiler=" + scalaCompiler.getAbsolutePath());
        Assert.assertTrue(scalaCompiler.isFile() && scalaLibrary.isFile() && scalaReflect.isFile());

        CompileResult positive = compilePositive();
        File scalaDir = new File(work, "scala");
        scalaDir.mkdirs();
        File source = new File(scalaDir, "StatusQueryApp.scala");
        write(source, scalaSource());
        File out = new File(scalaDir, "classes");
        out.mkdirs();
        String classpath = positive.classOut.getAbsolutePath() + File.pathSeparator + mainClasses.getAbsolutePath()
                + File.pathSeparator + jpaApi.getAbsolutePath() + File.pathSeparator + scalaLibrary.getAbsolutePath();
        String compilerPath = scalaCompiler.getAbsolutePath() + File.pathSeparator + scalaLibrary.getAbsolutePath()
                + File.pathSeparator + scalaReflect.getAbsolutePath();
        List<String> command = new ArrayList<String>();
        command.add(new File(System.getProperty("java.home"), "bin/java").getAbsolutePath());
        command.add("-cp");
        command.add(compilerPath);
        command.add("scala.tools.nsc.Main");
        command.add("-classpath");
        command.add(classpath);
        command.add("-d");
        command.add(out.getAbsolutePath());
        command.add("-release");
        command.add("8");
        command.add(source.getAbsolutePath());
        int exit = runProcess(command, new File(scalaDir, "scalac.log"));
        logLine("scalac exit=" + exit);
        if (exit != 0) {
            command.remove("-release");
            command.remove("8");
            command.add(command.size() - 1, "-target:8");
            exit = runProcess(command, new File(scalaDir, "scalac-target.log"));
            logLine("scalac -target:8 exit=" + exit);
        }
        Assert.assertEquals("scalac failed, see " + scalaDir, 0, exit);
        File appClass = new File(out, "net/csdn/jpa/query/fixture/StatusQueryApp.class");
        Assert.assertTrue(appClass.isFile());
        logLine("StatusQueryApp major=" + classMajor(appClass));
        Assert.assertEquals("scala caller class major", 52, classMajor(appClass));

        URLClassLoader fixture = new URLClassLoader(new URL[]{positive.classOut.toURI().toURL()},
                getClass().getClassLoader());
        URLClassLoader scalaLoader = new URLClassLoader(
                new URL[]{out.toURI().toURL(), scalaLibrary.toURI().toURL()}, fixture);
        try {
            Recording recording = new Recording();
            recording.entityNames.put("net.csdn.jpa.query.fixture.OrderEntity", "sales.OrderEntity");
            Class<?> app = scalaLoader.loadClass("net.csdn.jpa.query.fixture.StatusQueryApp");
            Method method = app.getMethod("find", EntityManager.class, String.class, Long.class, int.class, int.class);
            recording.reset();
            Object returned = method.invoke(null, recording.entityManager, "scala-user", Long.valueOf(9L),
                    Integer.valueOf(3), Integer.valueOf(4));
            Assert.assertSame("scala caller did not use the query double", recording.results, returned);
            Assert.assertEquals(
                    "SELECT e FROM sales.OrderEntity e WHERE e.status = :p0 AND e.tenantId = :p1 ORDER BY e.id ASC",
                    recording.jpql);
            Assert.assertEquals("scala-user", recording.bounds.get(0).value);
            Assert.assertEquals(3, recording.first);
            Assert.assertEquals(4, recording.max);
        } finally {
            scalaLoader.close();
            fixture.close();
        }
    }

    @Test
    public void invalidDeclarationsFailCompilation() throws Exception {
        section("negative javac");
        expectFail("missing", model("neg.missing", "MissingModel",
                "@net.csdn.jpa.query.QueryMethod(name = \"findByStatus\", fields = {\"missing\"})",
                "private String status;"), "neg.missing.MissingModel", "findByStatus", "missing");
        expectFail("duplicate-method", model("neg.dup", "DupModel",
                "@net.csdn.jpa.query.QueryMethod(name = \"findByStatus\", fields = {\"status\"})\n"
                        + "    @net.csdn.jpa.query.QueryMethod(name = \"findByStatus\", fields = {\"region\"})",
                "private String status; private String region;"), "neg.dup.DupModel", "findByStatus", "duplicate query method");
        expectFail("duplicate-field", model("neg.dupfield", "DupFieldModel",
                "@net.csdn.jpa.query.QueryMethod(name = \"findByStatus\", fields = {\"status\", \"status\"})",
                "private String status;"), "neg.dupfield.DupFieldModel", "findByStatus", "duplicate equality field");
        expectFail("too-many-methods", manyMethods(), "neg.count.CountModel", "-", "method count 9 exceeds 8");
        expectFail("too-many-fields", model("neg.fields", "FieldModel",
                "@net.csdn.jpa.query.QueryMethod(name = \"findByAll\", fields = {\"a\", \"b\", \"c\", \"d\", \"e\"})",
                "private String a; private String b; private String c; private String d; private String e;"),
                "neg.fields.FieldModel", "findByAll", "field count 5 exceeds 4");
        expectFail("too-many-orders", model("neg.order", "OrderCountModel",
                "@net.csdn.jpa.query.QueryMethod(name = \"findByStatus\", fields = {\"status\"}, orderBy = {\"a\", \"b\", \"c\", \"d\"})",
                "private String status; private String a; private String b; private String c; private String d;"),
                "neg.order.OrderCountModel", "findByStatus", "orderBy count 4 exceeds 3");
        expectFail("transient-shadow", transientShadow(), "neg.shadow.ChildStatus", "findByStatus", "transient");
        expectFail("association", association(), "neg.assoc.InvoiceModel", "findByAccount", "association");
        expectFail("reserved", model("neg.reserved", "ReservedModel",
                "@net.csdn.jpa.query.QueryMethod(name = \"findByOrder\", fields = {\"order\"})",
                "private String order;"), "neg.reserved.ReservedModel", "findByOrder", "reserved");
        expectFail("bad-order", model("neg.badorder", "BadOrderModel",
                "@net.csdn.jpa.query.QueryMethod(name = \"findByStatus\", fields = {\"status\"}, orderBy = {\"id;drop\"})",
                "private String status; private Long id;"), "neg.badorder.BadOrderModel", "findByStatus", "id;drop");
        expectFail("final-field", model("neg.fin", "FinalModel",
                "@net.csdn.jpa.query.QueryMethod(name = \"findByStatus\", fields = {\"status\"})",
                "public final String status = \"x\";"), "neg.fin.FinalModel", "findByStatus", "final");
        expectFail("inject", model("neg.inject", "InjectModel",
                "@net.csdn.jpa.query.QueryMethod(name = \"findByStatus\", fields = {\"status' OR '1'='1\"})",
                "private String status;"), "neg.inject.InjectModel", "findByStatus", "status' OR '1'='1");
        expectFail("oversize", oversize(), "net.csdn.jpa.query.fixture.oversize.BigModel", "-", "generated source length");
        expectFail("default-package", "import net.csdn.jpa.query.QueryMethod;\n"
                + "@QueryMethod(name = \"findByStatus\", fields = {\"status\"})\n"
                + "public class Unpackaged { private String status; }\n", "Unpackaged", "-", "named package");
    }

    @Test
    public void packageAnchorIsKeptAndConflictsFail() throws Exception {
        section("anchor");
        File keep = new File(work, "anchor-keep/input");
        write(new File(keep, "neg/anchorok/ServiceFrameworkPackageAnchor.java"),
                "package neg.anchorok;\npublic class ServiceFrameworkPackageAnchor {\n"
                        + "    public static String origin() { return \"user-anchor\"; }\n}\n");
        write(new File(keep, "neg/anchorok/KeptModel.java"), model("neg.anchorok", "KeptModel",
                "@net.csdn.jpa.query.QueryMethod(name = \"findByStatus\", fields = {\"status\"})",
                "private String status;"));
        CompileResult kept = compile("anchor-keep", keep, true);
        Assert.assertTrue(kept.diagnostics, kept.success);
        Assert.assertFalse("anchor source was overwritten",
                new File(kept.sourceOut, "neg/anchorok/ServiceFrameworkPackageAnchor.java").exists());
        String javap = javap(kept.classOut, "neg.anchorok.ServiceFrameworkPackageAnchor");
        Assert.assertTrue(javap, javap.contains("user-anchor"));
        Assert.assertTrue(kept.diagnostics, kept.diagnostics.contains("not overwriting"));
        Assert.assertTrue("companion missing beside user anchor",
                new File(kept.sourceOut, "neg/anchorok/KeptModelQueries.java").isFile());

        File conflict = new File(work, "anchor-interface/input");
        write(new File(conflict, "neg/anchorif/ServiceFrameworkPackageAnchor.java"),
                "package neg.anchorif;\npublic interface ServiceFrameworkPackageAnchor {}\n");
        write(new File(conflict, "neg/anchorif/AnchoredModel.java"), model("neg.anchorif", "AnchoredModel",
                "@net.csdn.jpa.query.QueryMethod(name = \"findByStatus\", fields = {\"status\"})",
                "private String status;"));
        CompileResult bad = compile("anchor-interface", conflict, true);
        Assert.assertFalse("interface anchor was accepted", bad.success);
        Assert.assertEquals("exit", 1, bad.exit);
        Assert.assertTrue(bad.diagnostics, bad.diagnostics.contains("ServiceFrameworkPackageAnchor conflict"));
        Assert.assertTrue(bad.diagnostics, bad.diagnostics.contains("INTERFACE"));
    }

    @Test
    public void runtimeRejectsDeclarationThatBypassedProcessor() throws Exception {
        section("runtime revalidation");
        File input = new File(work, "runtime-bad/input");
        write(new File(input, "neg/runtimebad/BadRuntimeModel.java"),
                "package neg.runtimebad;\n"
                        + "import net.csdn.jpa.query.GenerateQueries;\n"
                        + "import net.csdn.jpa.query.QueryMethod;\n"
                        + "@GenerateQueries({\n"
                        + "    @QueryMethod(name = \"findByMissing\", fields = {\"missing\"})\n"
                        + "})\n"
                        + "public class BadRuntimeModel { private String status; }\n");
        CompileResult compiled = compile("runtime-bad", input, false);
        Assert.assertTrue(compiled.diagnostics, compiled.success);
        URLClassLoader loader = new URLClassLoader(new URL[]{compiled.classOut.toURI().toURL()},
                getClass().getClassLoader());
        try {
            Class<?> model = loader.loadClass("neg.runtimebad.BadRuntimeModel");
            try {
                RuntimeQueryMetadata.read(model);
                Assert.fail("bad runtime annotation was accepted");
            } catch (QueryDeclarationException ex) {
                Assert.assertTrue(ex.getMessage(), ex.getMessage().contains("neg.runtimebad.BadRuntimeModel"));
                Assert.assertTrue(ex.getMessage(), ex.getMessage().contains("findByMissing"));
                Assert.assertTrue(ex.getMessage(), ex.getMessage().contains("missing"));
            }
            Recording recording = new Recording();
            try {
                executeRaw(recording.entityManager, model, "findByMissing", new Object[]{"x"}, 0, 1);
                Assert.fail("executor ran a bad declaration");
            } catch (QueryDeclarationException ex) {
                Assert.assertEquals("bad declaration created JPQL", 0, recording.createQueryCalls);
                Assert.assertTrue(ex.getMessage(), ex.getMessage().contains("missing"));
            }
        } finally {
            loader.close();
        }
    }

    private CompileResult compilePositive() throws Exception {
        File input = new File(work, "positive/input");
        copyFixture(input, "OrderEntityBase.java");
        copyFixture(input, "OrderEntity.java");
        write(new File(input, "net/csdn/jpa/query/fixture/NoteEntity.java"), noteSource());
        write(new File(input, "net/csdn/jpa/query/fixture/Color.java"), colorSource());
        write(new File(input, "net/csdn/jpa/query/fixture/ItemEntity.java"), itemSource());
        write(new File(input, "net/csdn/jpa/query/fixture/HitCounter.java"), hitSource());
        write(new File(input, "net/csdn/jpa/query/fixture/SamePackageCaller.java"), samePackageCallerSource());
        write(new File(input, "net/csdn/jpa/query/fixture/client/OrderQueryCaller.java"), callerSource());
        CompileResult result = compile("positive", input, true);
        Assert.assertTrue("positive compile failed\n" + result.diagnostics, result.success);
        return result;
    }

    private void expectFail(String name, String source, String model, String method, String snippet) throws Exception {
        File input = new File(work, name + "/input");
        String fileName = model.indexOf('.') < 0 ? model + ".java" : model.substring(model.lastIndexOf('.') + 1) + ".java";
        File file;
        if (model.indexOf('.') < 0) {
            file = new File(input, fileName);
        } else {
            String packageName = model.substring(0, model.lastIndexOf('.'));
            file = new File(input, packageName.replace('.', '/') + "/" + fileName);
        }
        write(file, source);
        CompileResult result = compile(name, input, true);
        logLine(name + " exit=" + result.exit);
        Assert.assertFalse(name + " compiled\n" + result.diagnostics, result.success);
        Assert.assertEquals(name + " exit", 1, result.exit);
        Assert.assertTrue(name + " model missing\n" + result.diagnostics, result.diagnostics.contains(model));
        Assert.assertTrue(name + " method missing\n" + result.diagnostics,
                "-".equals(method) || result.diagnostics.contains(method));
        Assert.assertTrue(name + " snippet missing\n" + result.diagnostics, result.diagnostics.contains(snippet));
        Assert.assertEquals(name + " wrote a companion despite errors", 0,
                countFilesNamed(result.sourceOut, fileName.replace(".java", "Queries.java")));
    }

    private CompileResult compile(String name, File input, boolean processor, File... extraClasspath) throws Exception {
        File sourceOut = new File(work, name + "/generated");
        File classOut = new File(work, name + "/classes");
        sourceOut.mkdirs();
        classOut.mkdirs();
        List<File> files = new ArrayList<File>();
        collectJava(input, files);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Assert.assertNotNull("system JavaCompiler is missing; run tests on a JDK", compiler);
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
        StandardJavaFileManager filesManager = compiler.getStandardFileManager(diagnostics, Locale.ENGLISH, StandardCharsets.UTF_8);
        StringBuilder classpath = new StringBuilder(mainClasses.getAbsolutePath())
                .append(File.pathSeparator).append(jpaApi.getAbsolutePath());
        for (int i = 0; i < extraClasspath.length; i++) {
            classpath.insert(0, extraClasspath[i].getAbsolutePath() + File.pathSeparator);
        }
        List<String> options = new ArrayList<String>();
        if (jdk8()) {
            options.add("-source");
            options.add("8");
            options.add("-target");
            options.add("8");
        } else {
            options.add("--release");
            options.add("8");
        }
        options.add("-encoding");
        options.add("UTF-8");
        options.add("-classpath");
        options.add(classpath.toString());
        options.add("-d");
        options.add(classOut.getAbsolutePath());
        options.add("-s");
        options.add(sourceOut.getAbsolutePath());
        if (processor) {
            options.add("-processor");
            options.add("net.csdn.jpa.query.ServiceFrameworkQueryProcessor");
            options.add("-processorpath");
            options.add(mainClasses.getAbsolutePath());
        } else {
            options.add("-proc:none");
        }
        StringBuilder command = new StringBuilder("javac");
        for (int i = 0; i < options.size(); i++) {
            command.append(' ').append(options.get(i));
        }
        for (int i = 0; i < files.size(); i++) {
            command.append(' ').append(files.get(i).getAbsolutePath());
        }
        logLine("COMMAND " + command);
        logLine("JavaCompiler=" + compiler.getClass().getName());
        StringWriter compilerOut = new StringWriter();
        Boolean ok;
        try {
            ok = compiler.getTask(compilerOut, filesManager, diagnostics, options, null,
                    filesManager.getJavaFileObjectsFromFiles(files)).call();
        } finally {
            filesManager.close();
        }
        CompileResult result = new CompileResult();
        result.success = Boolean.TRUE.equals(ok);
        result.exit = result.success ? 0 : 1;
        result.command = command.toString();
        result.diagnostics = diagnosticText(diagnostics) + compilerOut.toString();
        result.sourceOut = sourceOut;
        result.classOut = classOut;
        logLine("exit=" + result.exit);
        logLine(result.diagnostics);
        return result;
    }

    private static boolean jdk8() {
        return "1.8".equals(System.getProperty("java.specification.version"));
    }

    private static File codeSource(Class<?> type) throws Exception {
        java.security.CodeSource source = type.getProtectionDomain().getCodeSource();
        Assert.assertNotNull("no CodeSource for " + type.getName(), source);
        return new File(source.getLocation().toURI());
    }

    private static File querySourceDir() {
        String override = System.getProperty("net.csdn.jpa.query.source.dir");
        List<File> candidates = new ArrayList<File>();
        if (override != null) {
            candidates.add(new File(override));
        }
        File userDir = new File(System.getProperty("user.dir"));
        candidates.add(new File(userDir, "src/main/java/net/csdn/jpa/query"));
        candidates.add(new File(userDir, "serviceframework-orm/src/main/java/net/csdn/jpa/query"));
        for (int i = 0; i < candidates.size(); i++) {
            if (candidates.get(i).isDirectory()) {
                return candidates.get(i);
            }
        }
        return null;
    }

    private static String diagnosticText(DiagnosticCollector<JavaFileObject> diagnostics) {
        StringBuilder text = new StringBuilder();
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            text.append(diagnostic.getKind()).append(": ").append(diagnostic.getMessage(Locale.ENGLISH)).append('\n');
        }
        return text.toString();
    }

    private static int runProcess(List<String> command, File output) throws Exception {
        StringBuilder rendered = new StringBuilder();
        for (int i = 0; i < command.size(); i++) {
            if (i > 0) {
                rendered.append(' ');
            }
            rendered.append(command.get(i));
        }
        System.out.println("COMMAND " + rendered);
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        builder.redirectOutput(output);
        Process process = builder.start();
        if (!process.waitFor(120, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            return 124;
        }
        return process.exitValue();
    }

    private static File jdkTool(String name) {
        File home = new File(System.getProperty("java.home"));
        File direct = new File(home, "bin/" + name);
        if (direct.isFile()) {
            return direct;
        }
        File parent = home.getParentFile();
        if (parent != null) {
            File sibling = new File(parent, "bin/" + name);
            if (sibling.isFile()) {
                return sibling;
            }
        }
        throw new IllegalStateException(name + " was not found under " + home.getAbsolutePath());
    }

    private static String javap(File classpath, String className) throws Exception {
        File output = File.createTempFile("javap", ".log");
        try {
            List<String> command = new ArrayList<String>();
            command.add(jdkTool("javap").getAbsolutePath());
            command.add("-v");
            command.add("-p");
            command.add("-classpath");
            command.add(classpath.getAbsolutePath());
            command.add(className);
            int exit = runProcess(command, output);
            String text = read(output);
            if (exit != 0) {
                return "javap exit " + exit + "\n" + text;
            }
            return text;
        } finally {
            output.delete();
        }
    }

    private static int classMajor(File classFile) throws Exception {
        byte[] bytes = Files.readAllBytes(classFile.toPath());
        return ((bytes[6] & 0xff) << 8) | (bytes[7] & 0xff);
    }

    private void copyFixture(File input, String name) throws Exception {
        String resource = "net/csdn/jpa/query/fixtures/" + name;
        InputStream in = getClass().getClassLoader().getResourceAsStream(resource);
        Assert.assertNotNull("fixture resource missing from test classpath: " + resource, in);
        try {
            String text = new String(readFully(in), StandardCharsets.UTF_8);
            write(new File(input, "net/csdn/jpa/query/fixture/" + name), text);
        } finally {
            in.close();
        }
    }

    private static byte[] readFully(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        while ((read = in.read(chunk)) >= 0) {
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }

    private static void write(File file, String content) throws Exception {
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    private static String read(File file) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static void collectJava(File dir, List<File> files) {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (int i = 0; i < children.length; i++) {
            if (children[i].isDirectory()) {
                collectJava(children[i], files);
            } else if (children[i].getName().endsWith(".java")) {
                files.add(children[i]);
            }
        }
    }

    private static int countFilesNamed(File dir, String name) {
        int count = 0;
        File[] children = dir.listFiles();
        if (children == null) {
            return 0;
        }
        for (int i = 0; i < children.length; i++) {
            if (children[i].isDirectory()) {
                count += countFilesNamed(children[i], name);
            } else if (children[i].getName().equals(name)) {
                count++;
            }
        }
        return count;
    }

    private void section(String name) {
        logLine("=== " + name + " ===");
    }

    private void logLine(String line) {
        System.out.println(line);
        if (log != null) {
            log.println(line);
        }
    }

    private static String model(String packageName, String simpleName, String annotations, String fields) {
        return "package " + packageName + ";\n"
                + annotations + "\n"
                + "public class " + simpleName + " {\n"
                + "    " + fields + "\n"
                + "}\n";
    }

    private static String manyMethods() {
        StringBuilder source = new StringBuilder();
        source.append("package neg.count;\nimport net.csdn.jpa.query.GenerateQueries;\nimport net.csdn.jpa.query.QueryMethod;\n");
        source.append("@GenerateQueries({\n");
        for (int i = 0; i < 9; i++) {
            if (i > 0) {
                source.append(",\n");
            }
            source.append("    @QueryMethod(name = \"find").append(i).append("\", fields = {\"status\"})");
        }
        source.append("\n})\npublic class CountModel { private String status; }\n");
        return source.toString();
    }

    private static String oversize() {
        StringBuilder source = new StringBuilder();
        source.append("package net.csdn.jpa.query.fixture.oversize;\n");
        source.append("import net.csdn.jpa.query.GenerateQueries;\nimport net.csdn.jpa.query.QueryMethod;\n");
        source.append("@GenerateQueries({\n");
        for (int i = 0; i < 8; i++) {
            if (i > 0) {
                source.append(",\n");
            }
            source.append("    @QueryMethod(name = \"").append(repeat('m', 120)).append(i)
                    .append("\", fields = {\"a\", \"b\", \"c\", \"d\"})");
        }
        source.append("\n})\npublic class BigModel {\n");
        source.append("    private String a; private String b; private String c; private String d;\n}\n");
        return source.toString();
    }

    private static String transientShadow() {
        return "package neg.shadow;\n"
                + "import javax.persistence.Transient;\n"
                + "import net.csdn.jpa.query.QueryMethod;\n"
                + "class ParentStatus { String status; }\n"
                + "@QueryMethod(name = \"findByStatus\", fields = {\"status\"})\n"
                + "public class ChildStatus extends ParentStatus {\n"
                + "    @Transient private String status;\n"
                + "}\n";
    }

    private static String association() {
        return "package neg.assoc;\n"
                + "import javax.persistence.ManyToOne;\n"
                + "import net.csdn.jpa.query.QueryMethod;\n"
                + "class Account {}\n"
                + "@QueryMethod(name = \"findByAccount\", fields = {\"account\"})\n"
                + "public class InvoiceModel {\n"
                + "    @ManyToOne private Account account;\n"
                + "}\n";
    }

    private static String noteSource() {
        return "package net.csdn.jpa.query.fixture;\n"
                + "import net.csdn.jpa.query.QueryMethod;\n"
                + "@QueryMethod(name = \"findByLabel\", fields = {\"label\"})\n"
                + "public class NoteEntity { private String label; }\n";
    }

    private static String colorSource() {
        return "package net.csdn.jpa.query.fixture;\npublic enum Color { RED, BLUE }\n";
    }

    private static String itemSource() {
        return "package net.csdn.jpa.query.fixture;\n"
                + "import net.csdn.jpa.query.QueryMethod;\n"
                + "@QueryMethod(name = \"findByColor\", fields = {\"color\"})\n"
                + "public class ItemEntity { private Color color; }\n";
    }

    private static String hitSource() {
        return "package net.csdn.jpa.query.fixture;\n"
                + "import net.csdn.jpa.query.QueryMethod;\n"
                + "@QueryMethod(name = \"findByHits\", fields = {\"hits\"})\n"
                + "public class HitCounter { private int hits; }\n";
    }

    private static String collisionSource() {
        return "package net.csdn.jpa.query.fixture;\n"
                + "import net.csdn.jpa.query.GenerateQueries;\n"
                + "import net.csdn.jpa.query.QueryMethod;\n"
                + "@GenerateQueries({\n"
                + "    @QueryMethod(name = \"findByOffset\", fields = {\"offset\"}),\n"
                + "    @QueryMethod(name = \"findByLimitAndEntityManager\", fields = {\"limit\", \"entityManager\"})\n"
                + "})\n"
                + "public class CollisionEntity {\n"
                + "    private String offset;\n"
                + "    private String limit;\n"
                + "    private String entityManager;\n"
                + "}\n";
    }

    private static String samePackageCallerSource() {
        return "package net.csdn.jpa.query.fixture;\n"
                + "public class SamePackageCaller {\n"
                + "    public static java.util.List<OrderEntity> find(javax.persistence.EntityManager em, String status, Long tenantId, int offset, int limit) {\n"
                + "        return OrderEntityQueries.findByStatusAndTenant(em, status, tenantId, offset, limit);\n"
                + "    }\n"
                + "    public static java.util.List<OrderEntity> findDefault(String status, Long tenantId, int offset, int limit) {\n"
                + "        return OrderEntityQueries.findByStatusAndTenant(status, tenantId, offset, limit);\n"
                + "    }\n"
                + "}\n";
    }

    private static String callerSource() {
        return "package net.csdn.jpa.query.fixture.client;\n"
                + "import javax.persistence.EntityManager;\n"
                + "import net.csdn.jpa.query.fixture.OrderEntity;\n"
                + "import net.csdn.jpa.query.fixture.OrderEntityQueries;\n"
                + "public class OrderQueryCaller {\n"
                + "    public static java.util.List<OrderEntity> find(EntityManager em, String status, Long tenantId, int offset, int limit) {\n"
                + "        return OrderEntityQueries.findByStatusAndTenant(em, status, tenantId, offset, limit);\n"
                + "    }\n"
                + "    public static java.util.List<OrderEntity> findDefault(String status, Long tenantId, int offset, int limit) {\n"
                + "        return OrderEntityQueries.findByStatusAndTenant(status, tenantId, offset, limit);\n"
                + "    }\n"
                + "}\n";
    }

    private static String twoPhaseSamePackageCallerSource() {
        return "package net.csdn.jpa.query.fixture;\n"
                + "public class TwoPhaseSamePackageCaller {\n"
                + "    public static java.util.List<OrderEntity> find(javax.persistence.EntityManager em, String status, Long tenantId, int offset, int limit) {\n"
                + "        return OrderEntityQueries.findByStatusAndTenant(em, status, tenantId, offset, limit);\n"
                + "    }\n"
                + "}\n";
    }

    private static String twoPhaseCallerSource() {
        return "package net.csdn.jpa.query.fixture.client;\n"
                + "import javax.persistence.EntityManager;\n"
                + "import net.csdn.jpa.query.fixture.OrderEntity;\n"
                + "import net.csdn.jpa.query.fixture.OrderEntityQueries;\n"
                + "public class TwoPhaseCaller {\n"
                + "    public static java.util.List<OrderEntity> find(EntityManager em, String status, Long tenantId, int offset, int limit) {\n"
                + "        return OrderEntityQueries.findByStatusAndTenant(em, status, tenantId, offset, limit);\n"
                + "    }\n"
                + "}\n";
    }

    private static String scalaSource() {
        return "package net.csdn.jpa.query.fixture\n"
                + "object StatusQueryApp {\n"
                + "  def find(em: javax.persistence.EntityManager, status: String, tenantId: java.lang.Long, offset: Int, limit: Int): java.util.List[OrderEntity] = {\n"
                + "    OrderEntityQueries.findByStatusAndTenant(em, status, tenantId, offset, limit)\n"
                + "  }\n"
                + "}\n";
    }

    private static String repeat(char value, int count) {
        char[] data = new char[count];
        Arrays.fill(data, value);
        return new String(data);
    }

    private static final class CompileResult {
        private boolean success;
        private int exit;
        private String command;
        private String diagnostics;
        private File sourceOut;
        private File classOut;
    }

    private static final class SimpleField implements QueryFieldView {
        private final String name;
        private final String javaType;
        private final boolean primitive;
        private final boolean enumeration;
        private final boolean eligible;
        private final String reason;

        private SimpleField(String name, String javaType, boolean primitive, boolean enumeration, boolean eligible, String reason) {
            this.name = name;
            this.javaType = javaType;
            this.primitive = primitive;
            this.enumeration = enumeration;
            this.eligible = eligible;
            this.reason = reason;
        }

        public String getName() {
            return name;
        }

        public String getJavaType() {
            return javaType;
        }

        public boolean isPrimitive() {
            return primitive;
        }

        public boolean isEnum() {
            return enumeration;
        }

        public boolean isEligible() {
            return eligible;
        }

        public String getIneligibleReason() {
            return reason;
        }

        public boolean isSupportedScalar() {
            return enumeration || QuerySchemas.isSupportedScalarName(javaType);
        }
    }

    private static final class MapModel implements QueryModelView {
        private final String name;
        private final Map<String, QueryFieldView> fields;

        private MapModel(String name, Map<String, QueryFieldView> fields) {
            this.name = name;
            this.fields = fields;
        }

        public String getModelName() {
            return name;
        }

        public QueryFieldView findField(String fieldName) {
            return fields.get(fieldName);
        }
    }

    private static final class Bound {
        private final Class<?> nameType;
        private final String name;
        private final Object value;

        private Bound(Class<?> nameType, String name, Object value) {
            this.nameType = nameType;
            this.name = name;
            this.value = value;
        }

        public String toString() {
            return nameType.getSimpleName() + " " + name + "=" + value;
        }
    }

    private static final class Recording implements InvocationHandler {
        private final Map<String, String> entityNames = new LinkedHashMap<String, String>();
        private final List<Object> results = new ArrayList<Object>();
        private final List<Bound> bounds = new ArrayList<Bound>();
        private final EntityManager entityManager;
        private final TypedQuery<?> query;
        private volatile CyclicBarrier gate;
        private volatile String jpql;
        private volatile Class<?> resultClass;
        private volatile int first = Integer.MIN_VALUE;
        private volatile int max = Integer.MIN_VALUE;
        private volatile int createQueryCalls;

        private Recording() {
            this.entityManager = (EntityManager) Proxy.newProxyInstance(EntityManager.class.getClassLoader(),
                    new Class<?>[]{EntityManager.class}, this);
            this.query = (TypedQuery<?>) Proxy.newProxyInstance(TypedQuery.class.getClassLoader(),
                    new Class<?>[]{TypedQuery.class}, this);
        }

        private void reset() {
            jpql = null;
            resultClass = null;
            first = Integer.MIN_VALUE;
            max = Integer.MIN_VALUE;
            createQueryCalls = 0;
            bounds.clear();
            results.clear();
        }

        public Object invoke(Object proxy, Method method, Object[] args) {
            if (method.getDeclaringClass() == Object.class) {
                if ("toString".equals(method.getName())) {
                    return "QueryDouble";
                }
                if ("hashCode".equals(method.getName())) {
                    return Integer.valueOf(System.identityHashCode(proxy));
                }
                if ("equals".equals(method.getName())) {
                    return Boolean.valueOf(proxy == args[0]);
                }
            }
            String name = method.getName();
            if ("getMetamodel".equals(name)) {
                return Proxy.newProxyInstance(EntityManager.class.getClassLoader(),
                        new Class<?>[]{javax.persistence.metamodel.Metamodel.class}, this);
            }
            if ("entity".equals(name) && args != null && args.length == 1 && args[0] instanceof Class) {
                Class<?> model = (Class<?>) args[0];
                String entityName = entityNames.get(model.getName());
                if (entityName == null) {
                    throw new IllegalArgumentException("no entity name for " + model.getName());
                }
                final String fixedName = entityName;
                return Proxy.newProxyInstance(EntityManager.class.getClassLoader(),
                        new Class<?>[]{javax.persistence.metamodel.EntityType.class}, new InvocationHandler() {
                            public Object invoke(Object entityProxy, Method entityMethod, Object[] entityArgs) {
                                if ("getName".equals(entityMethod.getName())) {
                                    return fixedName;
                                }
                                if ("toString".equals(entityMethod.getName())) {
                                    return fixedName;
                                }
                                return defaultValue(entityMethod.getReturnType());
                            }
                        });
            }
            if ("createQuery".equals(name) && args != null && args.length == 2) {
                createQueryCalls++;
                jpql = String.valueOf(args[0]);
                resultClass = (Class<?>) args[1];
                awaitGate();
                return query;
            }
            if ("setParameter".equals(name) && args != null && args.length >= 2) {
                bounds.add(new Bound(method.getParameterTypes()[0], String.valueOf(args[0]), args[1]));
                return proxy;
            }
            if ("setFirstResult".equals(name)) {
                first = ((Integer) args[0]).intValue();
                return proxy;
            }
            if ("setMaxResults".equals(name)) {
                max = ((Integer) args[0]).intValue();
                return proxy;
            }
            if ("getResultList".equals(name)) {
                return results;
            }
            return defaultValue(method.getReturnType());
        }

        private void awaitGate() {
            CyclicBarrier current = gate;
            if (current == null) {
                return;
            }
            try {
                current.await(30, TimeUnit.SECONDS);
            } catch (Exception broken) {
                throw new IllegalStateException("concurrent EntityManager gate", broken);
            }
        }

        private static Object defaultValue(Class<?> type) {
            if (!type.isPrimitive()) {
                return null;
            }
            if (type == Boolean.TYPE) {
                return Boolean.FALSE;
            }
            if (type == Long.TYPE) {
                return Long.valueOf(0L);
            }
            if (type == Double.TYPE) {
                return Double.valueOf(0d);
            }
            if (type == Float.TYPE) {
                return Float.valueOf(0f);
            }
            return Integer.valueOf(0);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<?> executeRaw(EntityManager entityManager, Class<?> model, String methodName, Object[] values, int offset, int limit) {
        return GeneratedQueryExecutor.execute(entityManager, (Class) model, methodName, values, offset, limit);
    }
}
