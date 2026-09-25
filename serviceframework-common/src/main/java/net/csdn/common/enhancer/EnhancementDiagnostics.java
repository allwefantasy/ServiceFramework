package net.csdn.common.enhancer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Optional per-class record of rule execution and definition.
 * <p>
 * Disabled diagnostics do not hash, inspect methods, read class bytes, or write.
 * {@link #flush()} writes only data already collected. Generated source and class
 * files are written only when the matching emit flag is set and diagnostics are
 * enabled. A failed flush leaves the in-memory events intact and can be retried;
 * a later success overwrites the same files instead of appending.
 * <p>
 * Metadata is an explicit version token plus an optional SHA-256 schema digest.
 * There is no settings, map, or raw-configuration argument. Failure text is
 * redacted before it is stored. Database passwords and connection strings are
 * not report fields.
 */
public final class EnhancementDiagnostics {

    private static final Pattern VERSION_TOKEN = Pattern.compile("[A-Za-z0-9._+-]{1,64}");
    private static final Pattern SCHEMA_DIGEST = Pattern.compile("[0-9a-fA-F]{64}");
    private static final Pattern JDBC = Pattern.compile("(?i)jdbc:[^\\s]+");
    private static final Pattern SECRET_ASSIGN = Pattern.compile(
            "(?i)(password|passwd|secret|credential|api[-_]?key)\\s*[=:]\\s*\\S+");

    private final boolean enabled;
    private final File outputDirectory;
    private final Map<String, String> originalHashes = new LinkedHashMap<String, String>();
    private final Map<String, byte[]> originalBytes = new LinkedHashMap<String, byte[]>();
    private final Map<String, byte[]> enhancedBytes = new LinkedHashMap<String, byte[]>();
    private final List<Event> events = new ArrayList<Event>();
    private final List<SourceNote> sources = new ArrayList<SourceNote>();
    private boolean emitGeneratedSource;
    private boolean emitClassFiles;
    private int hashComputations;
    private int bytecodeReads;
    private int methodInspections;
    private int diskWrites;
    private boolean flushed;
    private String targetLoaderId;
    private String jdkVersion;
    private int java8Target;
    private String configVersion;
    private String schemaDigest;

    private EnhancementDiagnostics(boolean enabled, File outputDirectory) {
        this.enabled = enabled;
        this.outputDirectory = outputDirectory;
    }

    public static EnhancementDiagnostics disabled() {
        return new EnhancementDiagnostics(false, null);
    }

    public static EnhancementDiagnostics enabled(File outputDirectory) {
        return new EnhancementDiagnostics(true, outputDirectory);
    }

    /**
     * @param outputDirectory destination used by {@link #flush()} only when {@code enabled}
     *                        is true. When disabled, the directory is never created or written
     *                        and class bytes are not hashed.
     */
    public static EnhancementDiagnostics create(boolean enabled, File outputDirectory) {
        return new EnhancementDiagnostics(enabled, outputDirectory);
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean flushed() {
        return flushed;
    }

    public boolean emitGeneratedSource() {
        return emitGeneratedSource;
    }

    public void setEmitGeneratedSource(boolean emitGeneratedSource) {
        this.emitGeneratedSource = emitGeneratedSource;
    }

    public boolean emitClassFiles() {
        return emitClassFiles;
    }

    /**
     * When true, and diagnostics are enabled, flush writes the original and
     * enhanced class files captured after this flag was turned on. Default is false.
     * Disabled diagnostics never write class files even if this is set.
     */
    public void setEmitClassFiles(boolean emitClassFiles) {
        this.emitClassFiles = emitClassFiles;
    }

    public int hashComputations() {
        return hashComputations;
    }

    public int bytecodeReads() {
        return bytecodeReads;
    }

    public int methodInspections() {
        return methodInspections;
    }

    public int diskWrites() {
        return diskWrites;
    }

    /**
     * Records the target loader identity. The loader object itself is not retained.
     * Disabled diagnostics ignore this call.
     */
    public void bindTarget(ClassLoader loader) {
        if (!enabled || targetLoaderId != null) {
            return;
        }
        this.targetLoaderId = loaderId(loader);
        ensureEnvironment();
    }

    /**
     * Explicit non-secret metadata. {@code configVersion} is a short token such as
     * {@code orm-2}. {@code schemaDigest} is a 64-character SHA-256 hex digest, or
     * null when the caller has no schema. Raw settings, JDBC URLs and secret
     * assignments are rejected and are not stored. Disabled diagnostics ignore
     * the call and do not validate, because they store nothing.
     */
    public void noteSafeMetadata(String configVersion, String schemaDigest) {
        if (!enabled) {
            return;
        }
        if (configVersion == null || !VERSION_TOKEN.matcher(configVersion).matches() || secretToken(configVersion)) {
            throw metadataFailure("configVersion must be an explicit version token, not raw configuration");
        }
        if (schemaDigest != null && !SCHEMA_DIGEST.matcher(schemaDigest).matches()) {
            throw metadataFailure("schemaDigest must be a 64-character SHA-256 hex digest, not a schema dump");
        }
        this.configVersion = configVersion;
        this.schemaDigest = schemaDigest == null ? null : schemaDigest.toLowerCase();
    }

    public String configVersion() {
        return configVersion;
    }

    public String schemaDigest() {
        return schemaDigest;
    }

    public String originalHash(String className) {
        return originalHashes.get(className);
    }

    public boolean hasOriginal(String className) {
        return originalHashes.containsKey(className);
    }

    public List<Event> events() {
        return Collections.unmodifiableList(new ArrayList<Event>(events));
    }

    public List<Event> eventsFor(String className) {
        List<Event> found = new ArrayList<Event>();
        for (int i = 0; i < events.size(); i++) {
            Event event = events.get(i);
            if (same(className, event.className)) {
                found.add(event);
            }
        }
        return Collections.unmodifiableList(found);
    }

    /**
     * Last recorded change for this method, including a removal. Null when this
     * signature was not added, rewritten or removed while diagnostics were enabled.
     */
    public MethodOrigin originOf(String className, String signature) {
        MethodOrigin found = null;
        for (int i = 0; i < events.size(); i++) {
            Event event = events.get(i);
            if (!same(className, event.className)) {
                continue;
            }
            for (int j = 0; j < event.methodChanges.size(); j++) {
                MethodChange change = event.methodChanges.get(j);
                if (same(signature, change.signature())) {
                    found = new MethodOrigin(change, event.phase);
                }
            }
        }
        return found;
    }

    public List<MethodChange> methodHistory(String className, String signature) {
        List<MethodChange> found = new ArrayList<MethodChange>();
        for (int i = 0; i < events.size(); i++) {
            Event event = events.get(i);
            if (!same(className, event.className)) {
                continue;
            }
            for (int j = 0; j < event.methodChanges.size(); j++) {
                MethodChange change = event.methodChanges.get(j);
                if (same(signature, change.signature())) {
                    found.add(change);
                }
            }
        }
        return Collections.unmodifiableList(found);
    }

    void noteBytecodeRead() {
        if (!enabled) {
            return;
        }
        bytecodeReads++;
    }

    void noteMethodInspection() {
        if (!enabled) {
            return;
        }
        methodInspections++;
    }

    public void noteOriginal(String className, byte[] bytecode) {
        if (!enabled || bytecode == null || className == null || originalHashes.containsKey(className)) {
            return;
        }
        originalHashes.put(className, sha256Counted(bytecode));
        if (emitClassFiles) {
            originalBytes.put(className, copy(bytecode));
        }
    }

    public void recordApply(String className, String ruleId, int version, int methodDelta, long elapsedNanos) {
        recordApply(className, ruleId, version, methodDelta, elapsedNanos, "matched", Collections.<MethodChange>emptyList());
    }

    public void recordApply(
            String className,
            String ruleId,
            int version,
            int methodDelta,
            long elapsedNanos,
            String reason,
            List<MethodChange> methodChanges) {
        if (!enabled) {
            return;
        }
        addEvent(className, ruleId, version, "apply", targetLoaderId, methodDelta, null, elapsedNanos, null, reason, methodChanges);
    }

    public void recordDefine(String className, ClassLoader loader, byte[] resultBytecode, long elapsedNanos) {
        if (!enabled) {
            return;
        }
        String result = resultBytecode == null ? null : sha256Counted(resultBytecode);
        if (emitClassFiles && resultBytecode != null && className != null) {
            enhancedBytes.put(className, copy(resultBytecode));
        }
        addEvent(className, null, 0, "define", loaderId(loader), 0, result, elapsedNanos, null, null, Collections.<MethodChange>emptyList());
    }

    /**
     * Notes that a rule did not match a class. No hash or bytecode work happens
     * here; disabled diagnostics keep this a no-op.
     */
    public void recordSkip(String className, String ruleId) {
        recordSkip(className, ruleId, 0, "not matched");
    }

    public void recordSkip(String className, String ruleId, int version, String reason) {
        if (!enabled) {
            return;
        }
        addEvent(className, ruleId, version, "skip", targetLoaderId, 0, null, 0L, null, reason, Collections.<MethodChange>emptyList());
    }

    public void recordFailure(String className, String ruleId, String phase, Throwable failure, long elapsedNanos) {
        recordFailure(className, ruleId, 0, phase, failure, elapsedNanos);
    }

    public void recordFailure(
            String className,
            String ruleId,
            int version,
            String phase,
            Throwable failure,
            long elapsedNanos) {
        if (!enabled) {
            return;
        }
        addEvent(className, ruleId, version, phase, targetLoaderId, 0, null, elapsedNanos, failureText(failure), null,
                Collections.<MethodChange>emptyList());
    }

    public void emitSource(String className, String source) {
        if (!enabled || !emitGeneratedSource || source == null) {
            return;
        }
        sources.add(new SourceNote(className, source));
    }

    /**
     * Writes the current events. A previous successful flush is not repeated.
     * A failed write leaves {@link #flushed()} false so the same events can be
     * written again; destinations are replaced, not appended.
     */
    public void flush() {
        if (!enabled || flushed) {
            return;
        }
        if (outputDirectory == null) {
            flushed = true;
            return;
        }
        if (outputDirectory.exists() && !outputDirectory.isDirectory()) {
            throw writeFailure("cannot write diagnostics", null);
        }
        if (!outputDirectory.exists() && !outputDirectory.mkdirs()) {
            throw writeFailure("cannot create " + outputDirectory.getPath(), null);
        }
        try {
            writeString(new File(outputDirectory, "report.txt"), reportText());
            if (emitGeneratedSource && !sources.isEmpty()) {
                File sourceDir = new File(outputDirectory, "sources");
                ensureDirectory(sourceDir);
                for (int i = 0; i < sources.size(); i++) {
                    SourceNote note = sources.get(i);
                    File file = new File(sourceDir, sanitize(note.className) + "-" + i + ".txt");
                    writeString(file, note.source);
                }
            }
            if (emitClassFiles) {
                writeClasses(new File(outputDirectory, "original"), originalBytes);
                writeClasses(new File(outputDirectory, "enhanced"), enhancedBytes);
            }
        } catch (IOException e) {
            throw writeFailure("cannot write diagnostics", e);
        }
        flushed = true;
    }

    private void addEvent(
            String className,
            String ruleId,
            int version,
            String phase,
            String loader,
            int methodDelta,
            String resultSha256,
            long elapsedNanos,
            String failure,
            String reason,
            List<MethodChange> methodChanges) {
        ensureEnvironment();
        List<MethodChange> changes = methodChanges == null
                ? Collections.<MethodChange>emptyList()
                : Collections.unmodifiableList(new ArrayList<MethodChange>(methodChanges));
        events.add(new Event(
                className,
                ruleId,
                version,
                phase,
                loader,
                methodDelta,
                originalHashes.get(className),
                resultSha256,
                elapsedNanos,
                redact(failure),
                redact(reason),
                jdkVersion,
                java8Target,
                configVersion,
                schemaDigest,
                changes));
    }

    private void ensureEnvironment() {
        if (jdkVersion == null) {
            jdkVersion = System.getProperty("java.specification.version", "");
        }
        if (java8Target == 0) {
            java8Target = ClassDefiner.JAVA8_MAJOR;
        }
    }

    private String reportText() {
        StringBuilder builder = new StringBuilder();
        builder.append("# enhancement-diagnostics v1\n");
        for (int i = 0; i < events.size(); i++) {
            builder.append(events.get(i).line());
            builder.append('\n');
        }
        return builder.toString();
    }

    private void writeClasses(File directory, Map<String, byte[]> files) throws IOException {
        if (files.isEmpty()) {
            return;
        }
        ensureDirectory(directory);
        for (Map.Entry<String, byte[]> entry : files.entrySet()) {
            writeBytes(new File(directory, sanitize(entry.getKey()) + ".class"), entry.getValue());
        }
    }

    private static void ensureDirectory(File directory) throws IOException {
        if (directory.exists() && !directory.isDirectory()) {
            throw new IOException("cannot create " + directory.getPath());
        }
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("cannot create " + directory.getPath());
        }
    }

    private void writeString(File dest, String content) throws IOException {
        byte[] bytes;
        try {
            bytes = content.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IOException(e);
        }
        writeBytes(dest, bytes);
    }

    private void writeBytes(File dest, byte[] content) throws IOException {
        File parent = dest.getParentFile();
        if (parent != null) {
            ensureDirectory(parent);
        }
        File tmp = new File(parent, dest.getName() + ".tmp");
        IOException failure = null;
        FileOutputStream output = new FileOutputStream(tmp);
        try {
            output.write(content);
            output.flush();
        } catch (IOException e) {
            failure = e;
        }
        try {
            output.close();
        } catch (IOException closeError) {
            if (failure == null) {
                failure = closeError;
            } else {
                failure.addSuppressed(closeError);
            }
        }
        if (failure != null) {
            tmp.delete();
            throw failure;
        }
        if (dest.exists() && !dest.delete()) {
            tmp.delete();
            throw new IOException("cannot replace " + dest.getPath());
        }
        if (!tmp.renameTo(dest)) {
            tmp.delete();
            throw new IOException("cannot rename " + tmp.getPath());
        }
        diskWrites++;
    }

    private String sha256Counted(byte[] bytecode) {
        hashComputations++;
        return sha256Hex(bytecode);
    }

    static String sha256Hex(byte[] bytecode) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytecode);
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (int i = 0; i < hash.length; i++) {
                int value = hash[i] & 0xff;
                if (value < 16) {
                    builder.append('0');
                }
                builder.append(Integer.toHexString(value));
            }
            return builder.toString();
        } catch (Exception e) {
            throw new EnhancementFailure(
                    EnhancementFailure.Category.ENHANCEMENT,
                    null,
                    null,
                    "diagnostics",
                    "SHA-256 is unavailable",
                    e);
        }
    }

    static String loaderId(ClassLoader loader) {
        if (loader == null) {
            return "bootstrap";
        }
        return loader.getClass().getName() + "@" + System.identityHashCode(loader);
    }

    private static EnhancementFailure metadataFailure(String detail) {
        return new EnhancementFailure(
                EnhancementFailure.Category.CONFIGURATION,
                null,
                null,
                "diagnostics",
                detail,
                null);
    }

    private static EnhancementFailure writeFailure(String detail, Exception cause) {
        return new EnhancementFailure(
                EnhancementFailure.Category.CONFIGURATION,
                null,
                null,
                "diagnostics",
                detail,
                cause);
    }

    private static boolean secretToken(String token) {
        String lower = token.toLowerCase();
        return lower.contains("password")
                || lower.contains("passwd")
                || lower.contains("secret")
                || lower.contains("credential")
                || lower.contains("jdbc");
    }

    private static String redact(String value) {
        if (value == null) {
            return null;
        }
        String text = JDBC.matcher(value).replaceAll("[redacted]");
        return SECRET_ASSIGN.matcher(text).replaceAll("$1=[redacted]");
    }

    private static String failureText(Throwable failure) {
        if (failure == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        builder.append(failure.getMessage() == null ? failure.getClass().getName() : failure.getMessage());
        Throwable cause = failure.getCause();
        if (cause != null && cause.getMessage() != null && cause.getMessage().length() > 0) {
            builder.append(" cause=").append(cause.getMessage());
        }
        return builder.toString();
    }

    private static byte[] copy(byte[] bytecode) {
        byte[] clone = new byte[bytecode.length];
        System.arraycopy(bytecode, 0, clone, 0, bytecode.length);
        return clone;
    }

    private static boolean same(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private static String sanitize(String className) {
        String value = className == null ? "unknown" : className;
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    static String token(String value) {
        if (value == null || value.length() == 0) {
            return "-";
        }
        StringBuilder builder = new StringBuilder(value.length());
        int index = 0;
        while (index < value.length()) {
            int point = value.codePointAt(index);
            index += Character.charCount(point);
            if (point < 128 && isTokenChar((char) point)) {
                builder.append((char) point);
                continue;
            }
            byte[] bytes;
            try {
                bytes = new String(Character.toChars(point)).getBytes("UTF-8");
            } catch (UnsupportedEncodingException e) {
                bytes = new byte[] {'?'};
            }
            for (int i = 0; i < bytes.length; i++) {
                int unsigned = bytes[i] & 0xff;
                builder.append('%');
                if (unsigned < 16) {
                    builder.append('0');
                }
                builder.append(Integer.toHexString(unsigned));
            }
        }
        return builder.toString();
    }

    private static boolean isTokenChar(char c) {
        return (c >= 'a' && c <= 'z')
                || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9')
                || c == '.' || c == '_' || c == '/' || c == ':' || c == '@'
                || c == '+' || c == '-' || c == '[' || c == ']' || c == ',' || c == '|'
                || c == '(' || c == ')' || c == ';';
    }

    private static final class SourceNote {
        private final String className;
        private final String source;

        private SourceNote(String className, String source) {
            this.className = className;
            this.source = source;
        }
    }

    public static final class MethodChange {
        public static final String ADDED = "added";
        public static final String REWRITTEN = "rewritten";
        public static final String REMOVED = "removed";

        private final String className;
        private final String change;
        private final String signature;
        private final String ruleId;
        private final int ruleVersion;

        public MethodChange(String className, String change, String signature, String ruleId, int ruleVersion) {
            this.className = className;
            this.change = change;
            this.signature = signature;
            this.ruleId = ruleId;
            this.ruleVersion = ruleVersion;
        }

        public String className() {
            return className;
        }

        public String change() {
            return change;
        }

        public String signature() {
            return signature;
        }

        public String ruleId() {
            return ruleId;
        }

        public int ruleVersion() {
            return ruleVersion;
        }

        String token() {
            return change + "," + signature + "," + ruleId + "," + ruleVersion;
        }
    }

    public static final class MethodOrigin {
        private final String className;
        private final String change;
        private final String signature;
        private final String ruleId;
        private final int ruleVersion;
        private final String phase;

        MethodOrigin(MethodChange change, String phase) {
            this.className = change.className();
            this.change = change.change();
            this.signature = change.signature();
            this.ruleId = change.ruleId();
            this.ruleVersion = change.ruleVersion();
            this.phase = phase;
        }

        public String className() {
            return className;
        }

        public String change() {
            return change;
        }

        public String signature() {
            return signature;
        }

        public String ruleId() {
            return ruleId;
        }

        public int ruleVersion() {
            return ruleVersion;
        }

        public String phase() {
            return phase;
        }
    }

    public static final class Event {
        private final String className;
        private final String ruleId;
        private final int ruleVersion;
        private final String phase;
        private final String loader;
        private final int methodDelta;
        private final String originalSha256;
        private final String resultSha256;
        private final long elapsedNanos;
        private final String failure;
        private final String reason;
        private final String jdk;
        private final int java8Target;
        private final String configVersion;
        private final String schemaDigest;
        private final List<MethodChange> methodChanges;

        Event(
                String className,
                String ruleId,
                int ruleVersion,
                String phase,
                String loader,
                int methodDelta,
                String originalSha256,
                String resultSha256,
                long elapsedNanos,
                String failure,
                String reason,
                String jdk,
                int java8Target,
                String configVersion,
                String schemaDigest,
                List<MethodChange> methodChanges) {
            this.className = className;
            this.ruleId = ruleId;
            this.ruleVersion = ruleVersion;
            this.phase = phase;
            this.loader = loader;
            this.methodDelta = methodDelta;
            this.originalSha256 = originalSha256;
            this.resultSha256 = resultSha256;
            this.elapsedNanos = elapsedNanos;
            this.failure = failure;
            this.reason = reason;
            this.jdk = jdk;
            this.java8Target = java8Target;
            this.configVersion = configVersion;
            this.schemaDigest = schemaDigest;
            this.methodChanges = methodChanges;
        }

        public String className() {
            return className;
        }

        public String ruleId() {
            return ruleId;
        }

        public int ruleVersion() {
            return ruleVersion;
        }

        public String phase() {
            return phase;
        }

        public String loader() {
            return loader;
        }

        public int methodDelta() {
            return methodDelta;
        }

        public String originalSha256() {
            return originalSha256;
        }

        public String resultSha256() {
            return resultSha256;
        }

        public long elapsedNanos() {
            return elapsedNanos;
        }

        public String failure() {
            return failure;
        }

        public String reason() {
            return reason;
        }

        public String jdk() {
            return jdk;
        }

        public int java8Target() {
            return java8Target;
        }

        public String configVersion() {
            return configVersion;
        }

        public String schemaDigest() {
            return schemaDigest;
        }

        public List<MethodChange> methodChanges() {
            return methodChanges;
        }

        String line() {
            return "class=" + dash(className)
                    + " rule=" + dash(ruleId)
                    + " version=" + ruleVersion
                    + " phase=" + dash(phase)
                    + " loader=" + dash(loader)
                    + " methodDelta=" + methodDelta
                    + " originalSha256=" + dash(originalSha256)
                    + " resultSha256=" + dash(resultSha256)
                    + " elapsedNanos=" + elapsedNanos
                    + " failure=" + token(failure)
                    + " reason=" + token(reason)
                    + " jdk=" + dash(jdk)
                    + " java8Target=" + java8Target
                    + " configVersion=" + dash(configVersion)
                    + " schemaDigest=" + dash(schemaDigest)
                    + " methods=" + methodsToken();
        }

        private String methodsToken() {
            if (methodChanges == null || methodChanges.isEmpty()) {
                return "-";
            }
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < methodChanges.size(); i++) {
                if (i > 0) {
                    builder.append('|');
                }
                builder.append(methodChanges.get(i).token());
            }
            return token(builder.toString());
        }

        private static String dash(String value) {
            return value == null ? "-" : value;
        }
    }
}
