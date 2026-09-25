package net.csdn.common.scan;

import javassist.CtClass;
import javassist.CtMethod;
import net.csdn.common.enhancer.EnhancementContext;
import net.csdn.common.enhancer.EnhancementDiagnostics;
import net.csdn.common.enhancer.EnhancementPlan;
import net.csdn.common.enhancer.EnhancementRule;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Rerunnable measurement for the public enhancement path. It is not a JUnit test.
 * Logical open-stream counts are not operating-system file descriptors, and the
 * elapsed numbers are not a claim about framework startup time.
 */
public final class EnhancementCostBenchmark {

    private EnhancementCostBenchmark() {
    }

    public static void main(String[] args) throws Exception {
        File out = new File("enhancement-costs");
        int classes = 24;
        int warmup = 3;
        int iterations = 11;
        for (int i = 0; i < args.length; i++) {
            if ("--out".equals(args[i])) {
                out = new File(args[++i]);
            } else if ("--classes".equals(args[i])) {
                classes = Integer.parseInt(args[++i]);
            } else if ("--warmup".equals(args[i])) {
                warmup = Integer.parseInt(args[++i]);
            } else if ("--iterations".equals(args[i])) {
                iterations = Integer.parseInt(args[++i]);
            } else {
                throw new IllegalArgumentException("unknown argument " + args[i]);
            }
        }
        if (classes < 2 || warmup < 0 || iterations < 1) {
            throw new IllegalArgumentException("classes>=2, warmup>=0, iterations>=1");
        }
        out.mkdirs();
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("javac is required");
        }
        Fixture fixture = buildFixture(compiler, classes);
        ScanResult baseline = measureScan(fixture, true, warmup, iterations);
        ScanResult callback = measureScan(fixture, false, warmup, iterations);
        if (baseline.peak != fixture.classCount || callback.peak != 1 || callback.visited != fixture.classCount) {
            throw new IllegalStateException("unexpected stream peaks baseline=" + baseline.peak
                    + " callback=" + callback.peak + " visited=" + callback.visited
                    + " classes=" + fixture.classCount);
        }
        ArchiveCheck archives = checkArchives(compiler);
        DiagResult disabled = measureDiagnostics(false, warmup, iterations, null);
        File enabledDir = new File(out, "diagnostics-enabled");
        DiagResult enabled = measureDiagnostics(true, warmup, iterations, enabledDir);
        if (disabled.hashComputations != 0 || disabled.bytecodeReads != 0
                || disabled.methodInspections != 0 || disabled.diskWrites != 0) {
            throw new IllegalStateException("disabled diagnostics did work: " + disabled);
        }
        if (enabled.hashComputations <= 0 || enabled.bytecodeReads <= 0 || enabled.methodInspections <= 0) {
            throw new IllegalStateException("enabled diagnostics recorded no work: " + enabled);
        }
        String json = render(fixture, baseline, callback, archives, disabled, enabled, warmup, iterations);
        write(new File(out, "enhancement-costs.json"), json);
        write(new File(out, "enhancement-costs.txt"), renderText(fixture, baseline, callback, archives, disabled, enabled, warmup, iterations));
        System.out.println(new File(out, "enhancement-costs.json").getAbsolutePath());
    }

    private static ScanResult measureScan(Fixture fixture, boolean openAll, int warmup, int iterations) throws Exception {
        for (int i = 0; i < warmup; i++) {
            timeScan(fixture, openAll);
        }
        GcSnapshot before = GcSnapshot.take();
        long[] samples = new long[iterations];
        ScanResult last = null;
        for (int i = 0; i < iterations; i++) {
            long started = System.nanoTime();
            last = timeScan(fixture, openAll);
            samples[i] = System.nanoTime() - started;
        }
        last.samples = samples;
        last.gc = GcSnapshot.take().minus(before);
        return last;
    }

    private static ScanResult timeScan(Fixture fixture, boolean openAll) throws Exception {
        DefaultScanService service = new DefaultScanService();
        service.setLoader(fixture.anchor);
        service.resetStreamAccounting();
        final int[] visited = new int[1];
        ScanService.LoadClassEnhanceCallBack callback = new ScanService.LoadClassEnhanceCallBack() {
            @Override
            public Class loaded(java.io.DataInputStream classFile) {
                visited[0]++;
                byte[] buffer = new byte[256];
                try {
                    while (classFile.read(buffer) >= 0) {
                        // Touch the bytes so both paths do the same read work.
                    }
                } catch (java.io.IOException e) {
                    throw new IllegalStateException(e);
                }
                return null;
            }
        };
        if (openAll) {
            List<InputStream> streams = service.scanArchives(fixture.packageName);
            try {
                service.scanClass(streams, callback);
            } finally {
                for (int i = 0; i < streams.size(); i++) {
                    try {
                        streams.get(i).close();
                    } catch (java.io.IOException ignored) {
                        // scanClass already closes each stream.
                    }
                }
            }
        } else {
            service.scanArchives(fixture.packageName, callback);
        }
        ScanResult result = new ScanResult();
        result.peak = service.peakOpenStreams();
        result.visited = visited[0];
        result.openAll = openAll;
        return result;
    }

    private static DiagResult measureDiagnostics(boolean enabled, int warmup, int iterations, File output) throws Exception {
        for (int i = 0; i < warmup; i++) {
            oneDiagnosticPass(enabled, output, false);
        }
        GcSnapshot before = GcSnapshot.take();
        long[] samples = new long[iterations];
        EnhancementDiagnostics last = null;
        for (int i = 0; i < iterations; i++) {
            long started = System.nanoTime();
            last = oneDiagnosticPass(enabled, output, false);
            samples[i] = System.nanoTime() - started;
        }
        EnhancementDiagnostics emitted = oneDiagnosticPass(enabled, output, true);
        DiagResult result = new DiagResult();
        result.enabled = enabled;
        result.samples = samples;
        result.gc = GcSnapshot.take().minus(before);
        result.hashComputations = last.hashComputations();
        result.bytecodeReads = last.bytecodeReads();
        result.methodInspections = last.methodInspections();
        result.diskWrites = emitted.diskWrites();
        result.events = emitted.events().size();
        return result;
    }

    private static EnhancementDiagnostics oneDiagnosticPass(boolean enabled, File output, boolean emit) throws Exception {
        File dir = output;
        if (emit && dir != null) {
            dir = new File(output, "emit");
        }
        EnhancementDiagnostics diagnostics = enabled
                ? EnhancementDiagnostics.enabled(dir)
                : EnhancementDiagnostics.create(false, dir);
        if (enabled) {
            diagnostics.noteSafeMetadata("bench-1", shaToken());
        }
        if (emit) {
            diagnostics.setEmitGeneratedSource(true);
            diagnostics.setEmitClassFiles(true);
        }
        EnhancementContext context = EnhancementContext.open(EnhancementCostBenchmark.class.getClassLoader(), diagnostics);
        try {
            CtClass type = context.makeClass("net.csdn.common.scan.BenchType" + System.nanoTime());
            EnhancementPlan.compile(Collections.singletonList(new EnhancementRule() {
                @Override
                public String id() {
                    return "bench";
                }

                @Override
                public int version() {
                    return 1;
                }

                @Override
                public boolean matches(CtClass matched, EnhancementContext owner) {
                    return true;
                }

                @Override
                public void apply(CtClass matched, EnhancementContext owner) {
                    try {
                        matched.addMethod(CtMethod.make("public void marked() {}", matched));
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                }
            })).apply(type, context);
            if (emit) {
                diagnostics.emitSource(type.getName(), "generated marked()");
            }
        } finally {
            context.close();
        }
        return diagnostics;
    }

    private static ArchiveCheck checkArchives(JavaCompiler compiler) throws Exception {
        File root = new File(System.getProperty("java.io.tmpdir"), "sf bench space " + System.nanoTime());
        File source = new File(root, "src/benchpkg/BenchAnchor.java");
        write(source, "package benchpkg; public class BenchAnchor {}");
        if (compiler.run(null, null, null, "-d", root.getPath(), source.getPath()) != 0) {
            throw new IllegalStateException("fixture javac failed");
        }
        writeBytes(new File(root, "benchpkg/A.class"), new byte[] {'A', 'A'});
        File jar = new File(root, "sample classes.jar");
        java.util.jar.JarOutputStream jarOutput = new java.util.jar.JarOutputStream(new FileOutputStream(jar));
        try {
            put(jarOutput, "benchpkg/B.class", new byte[] {'B'});
            put(jarOutput, "benchpkg/A.class", new byte[] {'A'});
            put(jarOutput, "benchpkg/note.txt", new byte[] {'n'});
        } finally {
            jarOutput.close();
        }
        DefaultScanService service = new DefaultScanService();
        URL directory = new File(root, "benchpkg").toURI().toURL();
        URL jarUrl = jar.toURI().toURL();
        List<InputStream> dirStreams = service.scanArchives(directory);
        List<InputStream> jarStreams = service.scanArchives(jarUrl);
        ArchiveCheck check = new ArchiveCheck();
        check.directoryStreams = dirStreams.size();
        check.jarStreams = jarStreams.size();
        check.spaced = directory.toString().contains("%20") || jarUrl.toString().contains("%20");
        close(dirStreams);
        close(jarStreams);
        return check;
    }

    private static Fixture buildFixture(JavaCompiler compiler, int classes) throws Exception {
        File root = new File(System.getProperty("java.io.tmpdir"), "sf-bench-fixture-" + System.nanoTime());
        File sourceDir = new File(root, "src/benchpkg");
        sourceDir.mkdirs();
        List<String> paths = new ArrayList<String>();
        for (int i = 0; i < classes; i++) {
            File source = new File(sourceDir, "Bench" + i + ".java");
            write(source, "package benchpkg; public class Bench" + i + " { public int id() { return " + i + "; } }");
            paths.add(source.getPath());
        }
        File anchor = new File(sourceDir, "BenchAnchor.java");
        write(anchor, "package benchpkg; public class BenchAnchor {}");
        paths.add(anchor.getPath());
        List<String> javac = new ArrayList<String>();
        javac.add("-d");
        javac.add(root.getPath());
        javac.addAll(paths);
        if (compiler.run(null, null, null, javac.toArray(new String[javac.size()])) != 0) {
            throw new IllegalStateException("fixture javac failed");
        }
        URLClassLoader loader = new URLClassLoader(new URL[] {root.toURI().toURL()}, EnhancementCostBenchmark.class.getClassLoader());
        Fixture fixture = new Fixture();
        fixture.anchor = loader.loadClass("benchpkg.BenchAnchor");
        fixture.packageName = "benchpkg";
        fixture.classCount = classes + 1;
        fixture.loader = loader;
        return fixture;
    }

    private static String render(
            Fixture fixture,
            ScanResult baseline,
            ScanResult callback,
            ArchiveCheck archives,
            DiagResult disabled,
            DiagResult enabled,
            int warmup,
            int iterations) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        field(json, "jdkVersion", System.getProperty("java.version"), true);
        field(json, "jdkSpec", System.getProperty("java.specification.version"), true);
        field(json, "java8Target", "52", false);
        field(json, "fixtureClasses", String.valueOf(fixture.classCount), false);
        field(json, "warmup", String.valueOf(warmup), false);
        field(json, "iterations", String.valueOf(iterations), false);
        json.append("  \"logicalOpenStreamsAreNotFileDescriptors\": true,\n");
        json.append("  \"startupSpeedupClaimed\": false,\n");
        json.append("  \"baselineOpenAllThenCallback\": ");
        scanJson(json, baseline);
        json.append(",\n  \"callbackOneAtATime\": ");
        scanJson(json, callback);
        json.append(",\n  \"archiveSpotCheck\": {");
        json.append("\"directoryLogicalStreams\": ").append(archives.directoryStreams);
        json.append(", \"jarLogicalStreams\": ").append(archives.jarStreams);
        json.append(", \"pathContainedEncodedSpace\": ").append(archives.spaced);
        json.append("},\n  \"diagnosticsDisabled\": ");
        diagJson(json, disabled);
        json.append(",\n  \"diagnosticsEnabled\": ");
        diagJson(json, enabled);
        json.append("\n}\n");
        return json.toString();
    }

    private static void scanJson(StringBuilder json, ScanResult result) {
        json.append("{\"peakLogicalStreams\": ").append(result.peak);
        json.append(", \"visited\": ").append(result.visited);
        json.append(", \"elapsedNanos\": ").append(stats(result.samples));
        json.append(", \"gc\": ").append(result.gc.json());
        json.append("}");
    }

    private static void diagJson(StringBuilder json, DiagResult result) {
        json.append("{\"enabled\": ").append(result.enabled);
        json.append(", \"hashComputations\": ").append(result.hashComputations);
        json.append(", \"bytecodeReads\": ").append(result.bytecodeReads);
        json.append(", \"methodInspections\": ").append(result.methodInspections);
        json.append(", \"diskWrites\": ").append(result.diskWrites);
        json.append(", \"events\": ").append(result.events);
        json.append(", \"elapsedNanos\": ").append(stats(result.samples));
        json.append(", \"gc\": ").append(result.gc.json());
        json.append("}");
    }

    private static String stats(long[] samples) {
        long[] copy = Arrays.copyOf(samples, samples.length);
        Arrays.sort(copy);
        long sum = 0;
        for (int i = 0; i < copy.length; i++) {
            sum += copy[i];
        }
        return "{\"min\": " + copy[0]
                + ", \"p50\": " + percentile(copy, 0.50)
                + ", \"p95\": " + percentile(copy, 0.95)
                + ", \"max\": " + copy[copy.length - 1]
                + ", \"mean\": " + (sum / copy.length)
                + "}";
    }

    private static long percentile(long[] sorted, double fraction) {
        int index = (int) Math.ceil(fraction * sorted.length) - 1;
        if (index < 0) {
            index = 0;
        }
        if (index >= sorted.length) {
            index = sorted.length - 1;
        }
        return sorted[index];
    }

    private static String renderText(
            Fixture fixture,
            ScanResult baseline,
            ScanResult callback,
            ArchiveCheck archives,
            DiagResult disabled,
            DiagResult enabled,
            int warmup,
            int iterations) {
        return "JDK " + System.getProperty("java.version")
                + " spec " + System.getProperty("java.specification.version")
                + "\nfixtureClasses=" + fixture.classCount
                + " warmup=" + warmup
                + " iterations=" + iterations
                + "\nbaseline peakLogicalStreams=" + baseline.peak
                + " elapsed=" + stats(baseline.samples)
                + " gc=" + baseline.gc.json()
                + "\ncallback peakLogicalStreams=" + callback.peak
                + " visited=" + callback.visited
                + " elapsed=" + stats(callback.samples)
                + " gc=" + callback.gc.json()
                + "\narchive directoryStreams=" + archives.directoryStreams
                + " jarStreams=" + archives.jarStreams
                + " encodedSpace=" + archives.spaced
                + "\ndisabled hashes=" + disabled.hashComputations
                + " bytecodeReads=" + disabled.bytecodeReads
                + " methodInspections=" + disabled.methodInspections
                + " diskWrites=" + disabled.diskWrites
                + " elapsed=" + stats(disabled.samples)
                + "\nenabled hashes=" + enabled.hashComputations
                + " bytecodeReads=" + enabled.bytecodeReads
                + " methodInspections=" + enabled.methodInspections
                + " diskWrites=" + enabled.diskWrites
                + " events=" + enabled.events
                + " elapsed=" + stats(enabled.samples)
                + "\nlogical streams are not file descriptors; elapsed distributions are not a startup speedup claim.\n";
    }

    private static void field(StringBuilder json, String name, String value, boolean string) {
        json.append("  \"").append(name).append("\": ");
        if (string) {
            json.append("\"").append(value).append("\"");
        } else {
            json.append(value);
        }
        json.append(",\n");
    }

    private static String shaToken() {
        return "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    }

    private static void put(java.util.jar.JarOutputStream output, String name, byte[] bytes) throws java.io.IOException {
        output.putNextEntry(new java.util.jar.JarEntry(name));
        output.write(bytes);
        output.closeEntry();
    }

    private static void write(File file, String text) throws java.io.IOException {
        file.getParentFile().mkdirs();
        OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
        try {
            writer.write(text);
        } finally {
            writer.close();
        }
    }

    private static void writeBytes(File file, byte[] bytes) throws java.io.IOException {
        file.getParentFile().mkdirs();
        FileOutputStream output = new FileOutputStream(file);
        try {
            output.write(bytes);
        } finally {
            output.close();
        }
    }

    private static void close(List<InputStream> streams) {
        for (int i = 0; i < streams.size(); i++) {
            try {
                streams.get(i).close();
            } catch (java.io.IOException ignored) {
                // Spot check only needs the counts.
            }
        }
    }

    private static final class Fixture {
        Class<?> anchor;
        String packageName;
        int classCount;
        URLClassLoader loader;
    }

    private static final class ScanResult {
        boolean openAll;
        int peak;
        int visited;
        long[] samples;
        GcDelta gc;
    }

    private static final class DiagResult {
        boolean enabled;
        int hashComputations;
        int bytecodeReads;
        int methodInspections;
        int diskWrites;
        int events;
        long[] samples;
        GcDelta gc;

        @Override
        public String toString() {
            return "hashes=" + hashComputations + " reads=" + bytecodeReads
                    + " methods=" + methodInspections + " disk=" + diskWrites;
        }
    }

    private static final class ArchiveCheck {
        int directoryStreams;
        int jarStreams;
        boolean spaced;
    }

    private static final class GcSnapshot {
        final long count;
        final long timeMs;
        final long usedBytes;

        GcSnapshot(long count, long timeMs, long usedBytes) {
            this.count = count;
            this.timeMs = timeMs;
            this.usedBytes = usedBytes;
        }

        static GcSnapshot take() {
            long count = 0;
            long time = 0;
            for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
                if (bean.getCollectionCount() > 0) {
                    count += bean.getCollectionCount();
                }
                if (bean.getCollectionTime() > 0) {
                    time += bean.getCollectionTime();
                }
            }
            Runtime runtime = Runtime.getRuntime();
            return new GcSnapshot(count, time, runtime.totalMemory() - runtime.freeMemory());
        }

        GcDelta minus(GcSnapshot before) {
            return new GcDelta(count - before.count, timeMs - before.timeMs, usedBytes - before.usedBytes);
        }
    }

    private static final class GcDelta {
        final long collections;
        final long timeMs;
        final long usedBytesDelta;

        GcDelta(long collections, long timeMs, long usedBytesDelta) {
            this.collections = collections;
            this.timeMs = timeMs;
            this.usedBytesDelta = usedBytesDelta;
        }

        String json() {
            return "{\"collections\": " + collections
                    + ", \"timeMs\": " + timeMs
                    + ", \"usedBytesDelta\": " + usedBytesDelta
                    + "}";
        }
    }
}
