package net.csdn.bootstrap.lifecycle;

import net.csdn.bootstrap.ApplicationContext;
import net.csdn.bootstrap.Bootstrap;
import net.csdn.bootstrap.WebEnhancementMetadata;
import net.csdn.common.enhancer.EnhancementContext;
import net.csdn.common.enhancer.EnhancementDiagnostics;
import net.csdn.common.enhancer.StartupPhaseTrace;
import net.csdn.common.logging.Loggers;
import net.csdn.common.settings.Settings;
import net.csdn.jpa.JPA;
import net.csdn.jpa.type.DBInfo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.net.URLClassLoader;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Measures one real application start or the calls that follow it.
 * Not a JUnit test. Diagnostics stay off except for {@code --group smoke},
 * which is a functional check and is not the startup baseline.
 */
public final class FrameworkPhaseBenchmark {
    private static final String ORM = "net.csdn.bootstrap.lifecycle.web.db.orm";
    private static final String MONGO = "net.csdn.bootstrap.lifecycle.web.db.mongo";
    private static final String BOTH = "net.csdn.bootstrap.lifecycle.web.db.both";
    private static final String CONTROLLERS = BOTH;
    private static final String RECORD = ORM + ".WebRecord";
    private static final String CONTROLLER = BOTH + ".BothController";
    private static final String MARKER = "sf-phase-measure-marker";
    /** Caller token for the integrated smoke. Not the Bootstrap default {@code app-1}. */
    private static final String CALLER_REVISION = "config-20260924";

    private FrameworkPhaseBenchmark() {
    }

    public static void main(String[] args) throws Exception {
        String group = "cold";
        File out = new File("framework-phases.json");
        File logDir = new File("target/phase-logs");
        int warmup = 0;
        int iterations = 1;
        for (int i = 0; i < args.length; i++) {
            if ("--group".equals(args[i])) {
                group = args[++i];
            } else if ("--out".equals(args[i])) {
                out = new File(args[++i]);
            } else if ("--log-dir".equals(args[i])) {
                logDir = new File(args[++i]);
            } else if ("--warmup".equals(args[i])) {
                warmup = Integer.parseInt(args[++i]);
            } else if ("--iterations".equals(args[i])) {
                iterations = Integer.parseInt(args[++i]);
            } else {
                throw new IllegalArgumentException("unknown argument");
            }
        }
        if (warmup < 0 || iterations < 1) {
            throw new IllegalArgumentException("warmup>=0 and iterations>=1");
        }
        Map<String, String> env = loadEnv();
        if (!"sf_compat".equals(env.get("SF_COMPAT_MYSQL_DATABASE"))
                || !"sf_compat".equals(env.get("SF_COMPAT_MONGO_DATABASE"))) {
            throw new IllegalStateException("refusing a database other than sf_compat");
        }
        if (out.getParentFile() != null) {
            out.getParentFile().mkdirs();
        }
        logDir.mkdirs();
        String json;
        if ("smoke".equals(group)) {
            json = smoke(env, logDir);
        } else if ("hot".equals(group)) {
            json = hot(env, logDir, warmup, iterations);
        } else if ("repeated".equals(group)) {
            json = repeated(env, logDir, "repeated-fresh-loader",
                    "Same JVM, new application loader each sample. Framework classes stay in the parent loader. This is not a cold start. MySQL 8.0.46 and MongoDB 4.4.29 were already running on loopback. OS page cache is not claimed cold.",
                    warmup, iterations);
        } else if ("cold".equals(group)) {
            if (warmup != 0 || iterations != 1) {
                throw new IllegalStateException("cold is one uncached start in this JVM");
            }
            json = repeated(env, logDir, "cold",
                    "First application start in this JVM. Framework classes are loaded for the first time here, but the OS page cache and the already-running MySQL 8.0.46 / MongoDB 4.4.29 loopback processes are not a cold disk or database cache.",
                    0, 1);
        } else {
            throw new IllegalArgumentException("unknown group");
        }
        if (json.contains(env.get("SF_COMPAT_MYSQL_PASSWORD"))
                || json.contains(env.get("SF_COMPAT_MONGO_PASSWORD"))
                || json.contains("jdbc:")
                || json.contains("password=")) {
            throw new IllegalStateException("report would have contained a secret or JDBC URL");
        }
        write(out, json);
    }

    private static String repeated(
            Map<String, String> env,
            File logDir,
            String group,
            String cacheNote,
            int warmup,
            int iterations) throws Exception {
        List<String> samples = new ArrayList<String>();
        for (int i = 0; i < warmup; i++) {
            Measured ignored = measureStart(env, logDir, "warm-" + i);
            // The start method has returned, so its ApplicationContext and loader locals are gone.
            ignored.attachAfter(memoryAfterMeasureReturned());
            ignored.release();
        }
        for (int i = 0; i < iterations; i++) {
            Measured measured = measureStart(env, logDir, "sample-" + i);
            samples.add(measured.json(memoryAfterMeasureReturned()));
            measured.release();
        }
        requireMarker(logDir, warmup + iterations);
        return "{\"label\":\"new-full-runtime-baseline\",\"historicalJdk17Startup\":false,"
                + "\"group\":" + quote(group) + ",\"diagnostics\":\"off\",\"observer\":false,"
                + runtimeJson()
                + ",\"cacheNote\":" + quote(cacheNote) + ","
                + "\"fixture\":{\"servicePackages\":\"\",\"utilPackages\":\"\",\"note\":\"Service and util packages are empty, so scan.service and scan.util are absent. This is not a large-application scan measurement.\"},"
                + "\"warmup\":" + warmup + ",\"iterations\":" + iterations + ","
                + "\"jdbcNote\":\"getTablesCalls and getColumnsCalls count DatabaseMetaData API calls inside DBInfo.refresh, not network round trips.\","
                + "\"exclusiveNote\":\"Sum of non-nested phase samples plus the JDBC metadata elapsed time. rule.* samples are included once each. The first entity-mapping sample is the whole tree; later entity-mapping samples are no-ops and are not a second copy of that tree. There is no extra enhance total.\","
                + "\"samples\":[" + join(samples) + "]}";
    }

    private static String hot(Map<String, String> env, File logDir, int warmup, int iterations) throws Exception {
        DatabaseFixtureSupport.dropCollection(env);
        DatabaseFixtureSupport.createTable(env);
        URLClassLoader loader = DatabaseFixtureSupport.isolated(null);
        ApplicationContext context = null;
        try {
            Started started = startBoth(env, loader, logDir, "hot");
            context = started.context;
            for (int i = 0; i < warmup; i++) {
                callBoth(context.httpPort(), "hot-warm-" + i, "hot");
            }
            List<String> calls = new ArrayList<String>();
            List<Long> nanos = new ArrayList<Long>();
            for (int i = 0; i < iterations; i++) {
                long startedAt = System.nanoTime();
                DatabaseFixtureSupport.HttpResult result = callBoth(context.httpPort(), "hot-" + i, "hot");
                long elapsed = System.nanoTime() - startedAt;
                nanos.add(Long.valueOf(elapsed));
                calls.add("{\"callNanos\":" + elapsed + ",\"status\":" + result.status + ",\"ok\":true}");
            }
            emitMarker();
            requireMarker(logDir, 1);
            return "{\"label\":\"hot-business-calls\",\"notStartup\":true,\"group\":\"hot-call\","
                    + runtimeJson()
                    + ",\"cacheNote\":\"One context. The first timed call is discarded. MySQL 8.0.46 and MongoDB 4.4.29 were already running on loopback.\","
                    + "\"route\":\"/db/both\",\"routeNote\":\"/db/orm and /db/mongo are the same precompiled fixture started with their own controller package. This context cannot also scan those packages as controllers, because the model and document scans already read them.\","
                    + "\"warmup\":" + warmup + ",\"iterations\":" + iterations + ","
                    + "\"startupWallNanos\":" + started.wallNanos + ","
                    + "\"calls\":[" + join(calls) + "],"
                    + distribution("callNanos", nanos) + "}";
        } finally {
            if (context != null) {
                DatabaseFixtureSupport.closeWhenIdle(context);
            }
            loader.close();
            DatabaseFixtureSupport.dropTable(env);
            DatabaseFixtureSupport.dropCollection(env);
        }
    }

    private static String smoke(Map<String, String> env, File logDir) throws Exception {
        DatabaseFixtureSupport.dropCollection(env);
        DatabaseFixtureSupport.createTable(env);
        File reportDir = new File(logDir, "diagnostics");
        deleteTree(reportDir);
        reportDir.mkdirs();
        URLClassLoader loader = DatabaseFixtureSupport.isolated(null);
        ApplicationContext context = null;
        EnhancementDiagnostics diagnostics = EnhancementDiagnostics.enabled(reportDir);
        diagnostics.setEmitClassFiles(true);
        diagnostics.noteSafeMetadata(CALLER_REVISION, null);
        try {
            Settings settings = settings(env, "smoke", logDir);
            context = Bootstrap.configureSystem(
                    settings,
                    DatabaseFixtureSupport.marker(loader, BOTH + ".ServiceFrameworkPackageAnchor"),
                    diagnostics);
            DatabaseFixtureSupport.HttpResult result = callBoth(context.httpPort(), "smoke", "smoke");
            if (result.status != 200) {
                throw new IllegalStateException("smoke HTTP failed: " + result.status);
            }
            emitMarker();
            String beforeSnapshot;
            String afterSnapshot;
            String beforeDigest;
            String afterDigest;
            int computationsBeforeExplicit;
            EnhancementContext.Scope scope = context.activate();
            try {
                DBInfo info = JPA.dbInfo();
                computationsBeforeExplicit = info.schemaDigestComputations();
                beforeSnapshot = info.schemaSnapshot();
                beforeDigest = info.schemaDigest();
                alterColumn(env);
                info.refresh();
                afterSnapshot = info.schemaSnapshot();
                afterDigest = info.schemaDigest();
            } finally {
                scope.close();
            }
            if (computationsBeforeExplicit != 1) {
                throw new IllegalStateException("schema digest was not hashed exactly once during configure");
            }
            if (beforeSnapshot.contains("extra_note") || !afterSnapshot.contains("extra_note")
                    || beforeDigest.equals(afterDigest)) {
                throw new IllegalStateException("refreshed schema digest did not change after ALTER");
            }
            if (beforeSnapshot.contains("jdbc:") || afterSnapshot.contains("password=")
                    || beforeSnapshot.contains(env.get("SF_COMPAT_MYSQL_PASSWORD"))) {
                throw new IllegalStateException("schema snapshot contained a secret");
            }
            assertRevision(diagnostics);
            DatabaseFixtureSupport.closeWhenIdle(context);
            context = null;
            assertDumps(diagnostics, reportDir);
            requireMarker(logDir, 1);
            String report = readFile(new File(reportDir, "report.txt"));
            if (report.contains(env.get("SF_COMPAT_MYSQL_PASSWORD")) || report.contains("jdbc:")
                    || report.contains("password=")) {
                throw new IllegalStateException("diagnostics report contained a secret");
            }
            if (!report.contains("configVersion=" + CALLER_REVISION)
                    || report.contains("configVersion=" + WebEnhancementMetadata.APPLICATION_REVISION)
                    || report.contains("configVersion=" + WebEnhancementMetadata.VERSION)
                    || report.contains("configVersion=orm-1")
                    || report.contains("configVersion=mongo-1")) {
                throw new IllegalStateException("diagnostics report did not keep the caller revision");
            }
            return "{\"label\":\"diagnostics-smoke-not-baseline\",\"group\":\"smoke\","
                    + "\"callerApplicationRevision\":\"" + CALLER_REVISION + "\","
                    + "\"bootstrapDefaultWhenCallerOmitsRevision\":\"" + WebEnhancementMetadata.APPLICATION_REVISION + "\","
                    + "\"moduleFormatTokens\":{\"web\":\"" + WebEnhancementMetadata.VERSION + "\",\"orm\":\"orm-1\",\"mongo\":\"mongo-1\"},"
                    + "\"eventsKeptCallerRevision\":true,"
                    + "\"schemaDigestComputationsBeforeExplicitCall\":" + computationsBeforeExplicit + ","
                    + "\"digestBeforeAlter\":\"" + beforeDigest + "\",\"digestAfterAlter\":\"" + afterDigest + "\","
                    + "\"extraNoteSeenOnlyAfterRefresh\":true,\"classDumps\":true,\"sourceDumps\":false,"
                    + "\"sourceReconstruction\":false,\"logMarker\":true," + runtimeJson() + "}";
        } finally {
            if (context != null) {
                DatabaseFixtureSupport.closeWhenIdle(context);
            }
            loader.close();
            try {
                DatabaseFixtureSupport.dropTable(env);
            } catch (Exception ignored) {
                // cleanup must not hide the primary failure
            }
            try {
                DatabaseFixtureSupport.dropCollection(env);
            } catch (Exception ignored) {
                // cleanup must not hide the primary failure
            }
        }
    }

    private static void assertRevision(EnhancementDiagnostics diagnostics) {
        if (!CALLER_REVISION.equals(diagnostics.configVersion())
                || diagnostics.schemaDigest() != null) {
            throw new IllegalStateException("caller revision was not restored after the modules");
        }
        boolean orm = false;
        boolean mongo = false;
        boolean controller = false;
        String ormDigest = null;
        String mongoDigest = null;
        String controllerDigest = WebEnhancementMetadata.controllerDigest(Collections.singletonList(CONTROLLER));
        List<EnhancementDiagnostics.Event> events = diagnostics.events();
        for (int i = 0; i < events.size(); i++) {
            EnhancementDiagnostics.Event event = events.get(i);
            if (!CALLER_REVISION.equals(event.configVersion())) {
                throw new IllegalStateException("event did not keep the caller revision");
            }
            if ("entity-mapping".equals(event.ruleId()) && event.schemaDigest() != null) {
                orm = true;
                ormDigest = event.schemaDigest();
            }
            if ("mongo-document".equals(event.ruleId()) && event.schemaDigest() != null) {
                mongo = true;
                mongoDigest = event.schemaDigest();
            }
            if ("controller-filter".equals(event.ruleId()) && CONTROLLER.equals(event.className())
                    && controllerDigest.equals(event.schemaDigest())) {
                controller = true;
            }
        }
        if (!orm || !mongo || !controller || ormDigest.equals(mongoDigest)) {
            throw new IllegalStateException("module schema digests were missing or identical");
        }
    }

    private static void assertDumps(EnhancementDiagnostics diagnostics, File reportDir) throws Exception {
        File sources = new File(reportDir, "sources");
        if (sources.exists()) {
            throw new IllegalStateException("source dump was written without emitSource");
        }
        File original = new File(reportDir, "original/" + RECORD + ".class");
        File enhanced = new File(reportDir, "enhanced/" + RECORD + ".class");
        if (!original.isFile() || !enhanced.isFile()) {
            throw new IllegalStateException("class dumps were not written");
        }
        byte[] before = readBytes(original);
        byte[] after = readBytes(enhanced);
        if (before.length == 0 || after.length == 0 || java.util.Arrays.equals(before, after)) {
            throw new IllegalStateException("enhanced class dump did not differ from the original");
        }
        String hash = sha256(before);
        if (!hash.equals(diagnostics.originalHash(RECORD))) {
            throw new IllegalStateException("original class dump does not match the pre-mutation hash");
        }
    }

    private static Measured measureStart(Map<String, String> env, File logDir, String token) throws Exception {
        DatabaseFixtureSupport.dropCollection(env);
        DatabaseFixtureSupport.createTable(env);
        URLClassLoader loader = DatabaseFixtureSupport.isolated(null);
        ApplicationContext context = null;
        try {
            Started started = startBoth(env, loader, logDir, token);
            context = started.context;
            long callStarted = System.nanoTime();
            DatabaseFixtureSupport.HttpResult result = callBoth(context.httpPort(), token, token);
            long callNanos = System.nanoTime() - callStarted;
            EnhancementDiagnostics diagnostics = context.enhancementContext().diagnostics();
            if (diagnostics.enabled() || diagnostics.hashComputations() != 0 || diagnostics.bytecodeReads() != 0
                    || diagnostics.methodInspections() != 0 || diagnostics.diskWrites() != 0) {
                throw new IllegalStateException("diagnostics did work while the baseline was off");
            }
            DBInfo.RefreshStats stats;
            int computations;
            String snapshot;
            EnhancementContext.Scope scope = context.activate();
            try {
                DBInfo info = JPA.dbInfo();
                stats = info.lastRefreshStats();
                computations = info.schemaDigestComputations();
                snapshot = info.schemaSnapshot();
            } finally {
                scope.close();
            }
            if (stats.getTablesCalls != 1 || stats.getColumnsCalls != 1 || computations != 0) {
                throw new IllegalStateException("metadata calls were not one getTables and one getColumns with no digest");
            }
            if (!snapshot.contains("table " + DatabaseFixtureSupport.TABLE)
                    || !snapshot.contains("column label ")
                    || snapshot.contains("jdbc:")
                    || snapshot.contains(env.get("SF_COMPAT_MYSQL_PASSWORD"))) {
                throw new IllegalStateException("schema snapshot did not match the fixture or contained a secret");
            }
            emitMarker();
            long wallNanos = started.wallNanos;
            StartupPhaseTrace trace = started.trace;
            started.context = null;
            started = null;
            long exclusive = trace.exclusiveNanos() + stats.elapsedNanos;
            Memory before = memory();
            ApplicationContext closing = context;
            context = null;
            URLClassLoader closingLoader = loader;
            loader = null;
            DatabaseFixtureSupport.closeWhenIdle(closing);
            closing = null;
            closingLoader.close();
            closingLoader = null;
            Measured measured = new Measured();
            measured.body = "{\"wallNanos\":" + wallNanos
                    + ",\"exclusiveNanos\":" + exclusive
                    + ",\"residualNanos\":" + (wallNanos - exclusive)
                    + ",\"businessCallNanos\":" + callNanos
                    + ",\"httpStatus\":" + result.status
                    + ",\"schemaDigestComputations\":0"
                    + ",\"jdbc\":{\"tables\":" + stats.tables
                    + ",\"getTablesCalls\":" + stats.getTablesCalls
                    + ",\"getColumnsCalls\":" + stats.getColumnsCalls
                    + ",\"elapsedNanos\":" + stats.elapsedNanos + "}"
                    + ",\"phases\":" + phasesJson(trace)
                    + ",\"memoryBeforeClose\":" + before.json()
                    + ",\"memoryAfterCloseAndGcHint\":@@AFTER@@"
                    + ",\"gcNote\":\"System.gc is a hint and runs only after measureStart has returned, so that method no longer retains the ApplicationContext or the child loader. memoryAfterCloseAndGcHint is heap, Metaspace and collector counters after close plus that hint. close does not unload classes already defined in the child loader, and these numbers are not an unloading measurement.\"}";
            return measured;
        } finally {
            if (context != null) {
                DatabaseFixtureSupport.closeWhenIdle(context);
            }
            if (loader != null) {
                loader.close();
            }
            try {
                DatabaseFixtureSupport.dropTable(env);
            } catch (Exception ignored) {
                // cleanup must not hide the primary failure
            }
            try {
                DatabaseFixtureSupport.dropCollection(env);
            } catch (Exception ignored) {
                // cleanup must not hide the primary failure
            }
        }
    }

    private static Started startBoth(Map<String, String> env, URLClassLoader loader, File logDir, String token)
            throws Exception {
        Settings settings = settings(env, token, logDir);
        Class<?> marker = DatabaseFixtureSupport.marker(loader, BOTH + ".ServiceFrameworkPackageAnchor");
        ApplicationContext context = ApplicationContext.open(marker);
        StartupPhaseTrace trace = new StartupPhaseTrace();
        context.enhancementContext().setAttribute(StartupPhaseTrace.ATTRIBUTE, trace);
        long startedAt = System.nanoTime();
        context.start(settings);
        long wall = System.nanoTime() - startedAt;
        Started started = new Started();
        started.context = context;
        started.trace = trace;
        started.wallNanos = wall;
        return started;
    }

    private static Settings settings(Map<String, String> env, String token, File logDir) {
        return DatabaseFixtureSupport.databaseSettings(env, token, CONTROLLERS, ORM, MONGO, true, true)
                .put("http.disable", "false")
                .put("http.port", "0")
                .put("path.logs", logDir.getAbsolutePath())
                .put("cluster.name", "sf-phase-measure")
                .build();
    }

    private static DatabaseFixtureSupport.HttpResult callBoth(int port, String query, String applicationToken)
            throws Exception {
        DatabaseFixtureSupport.HttpResult result = DatabaseFixtureSupport.http(
                port, "/db/both?q=" + DatabaseFixtureSupport.url(query));
        String expected = query + ":" + query + ":" + applicationToken;
        if (result.status != 200 || !expected.equals(result.body)) {
            throw new IllegalStateException("both route failed: " + result.status);
        }
        return result;
    }

    private static void alterColumn(Map<String, String> env) throws Exception {
        java.sql.Connection connection = DatabaseFixtureSupport.openMysql(env);
        try {
            java.sql.Statement statement = connection.createStatement();
            try {
                statement.execute("ALTER TABLE `" + DatabaseFixtureSupport.TABLE + "` ADD COLUMN extra_note VARCHAR(32) NULL");
            } finally {
                statement.close();
            }
        } finally {
            connection.close();
        }
    }

    private static void emitMarker() {
        Loggers.getLogger(FrameworkPhaseBenchmark.class).info(MARKER);
        System.out.println(MARKER);
        System.out.flush();
        System.err.flush();
    }

    /**
     * Called only after {@link #measureStart} has returned.
     */
    private static Memory memoryAfterMeasureReturned() {
        System.gc();
        return memory();
    }

    private static void requireMarker(File logDir, int times) throws Exception {
        File log = new File(logDir, "sf-phase-measure.log");
        if (!log.isFile()) {
            throw new IllegalStateException("configured log file did not contain the marker");
        }
        String text = readFile(log);
        int seen = 0;
        int from = 0;
        while (from <= text.length()) {
            int at = text.indexOf(MARKER, from);
            if (at < 0) {
                break;
            }
            seen++;
            from = at + MARKER.length();
        }
        if (seen < times) {
            throw new IllegalStateException("configured log file did not contain the marker");
        }
    }

    private static String phasesJson(StartupPhaseTrace trace) {
        StringBuilder builder = new StringBuilder();
        builder.append('[');
        List<StartupPhaseTrace.Sample> samples = trace.samples();
        for (int i = 0; i < samples.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            StartupPhaseTrace.Sample sample = samples.get(i);
            builder.append("{\"name\":\"").append(sample.name).append("\",\"nanos\":")
                    .append(sample.elapsedNanos).append(",\"nested\":").append(sample.nested).append('}');
        }
        builder.append(']');
        return builder.toString();
    }

    private static String runtimeJson() {
        return "\"jvm\":{\"specification\":" + quote(System.getProperty("java.specification.version"))
                + ",\"version\":" + quote(System.getProperty("java.version"))
                + ",\"vendor\":" + quote(System.getProperty("java.vm.name"))
                + ",\"os\":" + quote(System.getProperty("os.name") + " " + System.getProperty("os.version")) + "}";
    }

    private static Memory memory() {
        Runtime runtime = Runtime.getRuntime();
        long metaspaceUsed = -1L;
        long metaspaceCommitted = -1L;
        List<MemoryPoolMXBean> pools = ManagementFactory.getMemoryPoolMXBeans();
        for (int i = 0; i < pools.size(); i++) {
            MemoryPoolMXBean pool = pools.get(i);
            if ("Metaspace".equals(pool.getName()) && pool.getUsage() != null) {
                MemoryUsage usage = pool.getUsage();
                metaspaceUsed = usage.getUsed();
                metaspaceCommitted = usage.getCommitted();
            }
        }
        long gcCount = 0L;
        long gcTime = 0L;
        List<GarbageCollectorMXBean> collectors = ManagementFactory.getGarbageCollectorMXBeans();
        for (int i = 0; i < collectors.size(); i++) {
            GarbageCollectorMXBean collector = collectors.get(i);
            if (collector.getCollectionCount() > 0) {
                gcCount += collector.getCollectionCount();
            }
            if (collector.getCollectionTime() > 0) {
                gcTime += collector.getCollectionTime();
            }
        }
        return new Memory(runtime.totalMemory() - runtime.freeMemory(), metaspaceUsed, metaspaceCommitted, gcCount, gcTime);
    }

    private static Map<String, String> loadEnv() throws Exception {
        String path = System.getenv("SF_COMPAT_ENV_FILE");
        if (path == null || path.length() == 0) {
            throw new IllegalStateException("SF_COMPAT_WEB_DB requires the compat env file");
        }
        return DatabaseFixtureSupport.readEnv(new File(path));
    }

    private static void write(File file, String text) throws Exception {
        OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
        try {
            writer.write(text);
        } finally {
            writer.close();
        }
    }

    private static String readFile(File file) throws Exception {
        byte[] bytes = readBytes(file);
        return new String(bytes, "UTF-8");
    }

    private static byte[] readBytes(File file) throws Exception {
        java.io.FileInputStream input = new java.io.FileInputStream(file);
        try {
            byte[] bytes = new byte[(int) file.length()];
            int offset = 0;
            while (offset < bytes.length) {
                int read = input.read(bytes, offset, bytes.length - offset);
                if (read < 0) {
                    break;
                }
                offset += read;
            }
            return bytes;
        } finally {
            input.close();
        }
    }

    private static String sha256(byte[] bytes) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder builder = new StringBuilder(hash.length * 2);
        for (int i = 0; i < hash.length; i++) {
            int value = hash[i] & 0xff;
            if (value < 16) {
                builder.append('0');
            }
            builder.append(Integer.toHexString(value));
        }
        return builder.toString();
    }

    private static String quote(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String join(List<String> parts) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(parts.get(i));
        }
        return builder.toString();
    }

    private static String distribution(String name, List<Long> values) {
        List<Long> sorted = new ArrayList<Long>(values);
        Collections.sort(sorted);
        long sum = 0L;
        for (int i = 0; i < sorted.size(); i++) {
            sum += sorted.get(i).longValue();
        }
        return "\"" + name + "Distribution\":{\"n\":" + sorted.size()
                + ",\"min\":" + sorted.get(0)
                + ",\"p50\":" + percentile(sorted, 50)
                + ",\"p95\":" + percentile(sorted, 95)
                + ",\"max\":" + sorted.get(sorted.size() - 1)
                + ",\"mean\":" + (sum / sorted.size())
                + ",\"unit\":\"nanoseconds\",\"smallSample\":true}";
    }

    private static long percentile(List<Long> sorted, int percent) {
        int index = (int) Math.ceil(percent / 100.0 * sorted.size()) - 1;
        if (index < 0) {
            index = 0;
        }
        if (index >= sorted.size()) {
            index = sorted.size() - 1;
        }
        return sorted.get(index).longValue();
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (int i = 0; i < children.length; i++) {
                    deleteTree(children[i]);
                }
            }
        }
        file.delete();
    }

    private static final class Started {
        private ApplicationContext context;
        private StartupPhaseTrace trace;
        private long wallNanos;
    }

    private static final class Measured {
        private String body;

        private String json(Memory after) {
            return body.replace("@@AFTER@@", after.json());
        }

        private void attachAfter(Memory after) {
            body = json(after);
        }

        private void release() {
            body = null;
        }
    }

    private static final class Memory {
        private final long heapUsed;
        private final long metaspaceUsed;
        private final long metaspaceCommitted;
        private final long gcCount;
        private final long gcTimeMs;

        private Memory(long heapUsed, long metaspaceUsed, long metaspaceCommitted, long gcCount, long gcTimeMs) {
            this.heapUsed = heapUsed;
            this.metaspaceUsed = metaspaceUsed;
            this.metaspaceCommitted = metaspaceCommitted;
            this.gcCount = gcCount;
            this.gcTimeMs = gcTimeMs;
        }

        private String json() {
            return "{\"heapUsed\":" + heapUsed
                    + ",\"metaspaceUsed\":" + metaspaceUsed
                    + ",\"metaspaceCommitted\":" + metaspaceCommitted
                    + ",\"gcCount\":" + gcCount
                    + ",\"gcTimeMs\":" + gcTimeMs + "}";
        }
    }

}
