package net.csdn.jpa;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.LoaderClassPath;
import net.csdn.common.enhancer.EnhancementContext;
import net.csdn.common.enhancer.EnhancementDiagnostics;
import net.csdn.common.enhancer.EnhancementFailure;
import net.csdn.common.enhancer.EnhancementRuleIds;
import net.csdn.common.settings.ImmutableSettings;
import net.csdn.common.settings.Settings;
import net.csdn.jpa.model.JPABase;
import net.csdn.jpa.type.DBInfo;
import org.junit.After;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Live diagnostics for ORM enhancement. A skip when MySQL is not requested is not acceptance.
 */
public class OrmDiagnosticsLiveTest {

    private static final String PHOTO = "net.csdn.jpa.ormdiag.DiagPhoto";
    private static final String ASSET = "net.csdn.jpa.ormdiag.DiagAsset";
    private static final String ALBUM = "net.csdn.jpa.ormdiag.DiagAlbum";
    private static final String FAIL_LOCKED = "net.csdn.jpa.ormfail.FailLocked";
    private static final String FAIL_BASE = "net.csdn.jpa.ormfail.FailBase";
    private static final String EARLIER_DIGEST = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private Map<String, String> env;
    private EnhancementContext owned;

    @After
    public void cleanup() {
        try {
            JPA.shutdown();
        } catch (RuntimeException ignored) {
            // The assertions above are the result that matters.
        }
        if (owned != null && !owned.isClosed()) {
            owned.close();
        }
        owned = null;
        if (env != null) {
            try {
                dropTables();
            } catch (Exception ignored) {
                // Drop is best-effort after the assertion.
            }
            env = null;
        }
    }

    @Test
    public void nullDiagnosticsAreRejected() {
        Settings settings = disabledSettings();
        try {
            new JPA.CSDNORMConfiguration("development", settings, JPA.class).enhancementDiagnostics(null);
            fail("null diagnostics were accepted");
        } catch (EnhancementFailure failure) {
            assertEquals(EnhancementFailure.Category.CONFIGURATION, failure.getCategory());
            assertTrue(failure.getMessage().contains("diagnostics are required"));
        }
    }

    @Test
    public void suppliedContextAndOptInMustBeTheSameDiagnostics() throws Exception {
        EnhancementDiagnostics diagnostics = EnhancementDiagnostics.enabled(folder.newFolder("disagree"));
        EnhancementContext context = EnhancementContext.open(JPA.class.getClassLoader());
        try {
            Settings settings = disabledSettings();
            JPA.CSDNORMConfiguration configuration = new JPA.CSDNORMConfiguration("development", settings, JPA.class)
                    .enhancementDiagnostics(diagnostics);
            try {
                JPA.configure(configuration, context);
                fail("a different diagnostics instance was accepted");
            } catch (EnhancementFailure failure) {
                assertEquals(EnhancementFailure.Category.CONFIGURATION, failure.getCategory());
                assertTrue(failure.getMessage().contains("disagree"));
            }
            assertFalse(context.isClosed());
            assertNull(JPA.defaultContext());
            assertEquals(0, diagnostics.hashComputations());
        } finally {
            context.close();
        }
    }

    @Test
    public void disabledAndEnabledConfigureHaveTheSameCrudAndRealOrigins() throws Exception {
        requireMysql();
        createPhotoTable();
        File classes = compileFixtures();
        String disabledOutcome = runDisabled(classes);
        String enabledOutcome = runEnabled(classes);
        assertEquals(disabledOutcome, enabledOutcome);
    }

    @Test
    public void ownedFinalConflictClosesContextAndWritesFailureReport() throws Exception {
        requireMysql();
        createFailureTable();
        File classes = compileFixtures();
        File reports = reportDir("failure");
        URLClassLoader loader = new URLClassLoader(new URL[]{classes.toURI().toURL()}, JPA.class.getClassLoader());
        EnhancementDiagnostics diagnostics = EnhancementDiagnostics.enabled(reports);
        String expectedDigest = digestNow("net.csdn.jpa.ormfail");
        try {
            Class<?> marker = Class.forName("net.csdn.jpa.ormfail.ServiceFrameworkPackageAnchor", false, loader);
            JPA.CSDNORMConfiguration configuration = new JPA.CSDNORMConfiguration(
                    "development", mysqlSettings("net.csdn.jpa.ormfail"), marker)
                    .enhancementDiagnostics(diagnostics);
            try {
                JPA.configure(configuration);
                fail("final inherited getter was copied");
            } catch (EnhancementFailure failure) {
                assertEquals(EnhancementFailure.Category.CONFLICT, failure.getCategory());
                assertEquals("enhance", failure.getPhase());
                assertEquals("bean-accessor", failure.getRuleId());
                assertEquals(FAIL_LOCKED, failure.getClassName());
                assertTrue(failure.getMessage().contains("final"));
                assertTrue(failure.getMessage().contains("getNote"));
                assertNoSecrets(failure.getMessage());
            }
            assertNull(JPA.defaultContext());
            assertFalse(JPA.isConfigured());
            assertTrue(diagnostics.flushed());
            Class<?> raw = Class.forName(FAIL_LOCKED, false, loader);
            for (java.lang.reflect.Method method : raw.getDeclaredMethods()) {
                if ("getId".equals(method.getName()) || "count".equals(method.getName()) || "findById".equals(method.getName())) {
                    fail("enhanced model was defined: " + method);
                }
            }
            EnhancementDiagnostics.Event reported = null;
            for (int i = 0; i < diagnostics.events().size(); i++) {
                EnhancementDiagnostics.Event event = diagnostics.events().get(i);
                if (event.failure() != null && event.failure().contains("final") && event.failure().contains(FAIL_LOCKED)) {
                    reported = event;
                    break;
                }
            }
            assertNotNull(reported);
            assertEquals(FAIL_BASE, reported.className());
            assertEquals(EnhancementRuleIds.ENTITY_MAPPING, reported.ruleId());
            assertEquals(1, reported.ruleVersion());
            assertEquals("apply", reported.phase());
            assertTrue(reported.failure().contains("phase=enhance"));
            assertTrue(reported.failure().contains("rule=bean-accessor"));
            assertTrue(reported.failure().contains("class=" + FAIL_LOCKED));
            assertEquals(DBInfo.DIAGNOSTICS_VERSION, reported.configVersion());
            assertEquals(expectedDigest, reported.schemaDigest());
            assertNoSecrets(reported.failure());
            String report = readUtf8(new File(reports, "report.txt"));
            assertTrue(report.startsWith("# enhancement-diagnostics v1\n"));
            assertTrue(report.contains("class=" + FAIL_BASE));
            assertTrue(report.contains("rule=" + EnhancementRuleIds.ENTITY_MAPPING));
            assertTrue(report.contains("phase=apply"));
            assertTrue(report.contains("phase%3denhance"));
            assertTrue(report.contains("rule%3dbean-accessor"));
            assertTrue(report.contains(FAIL_LOCKED));
            assertTrue(report.contains("configVersion=" + DBInfo.DIAGNOSTICS_VERSION));
            assertTrue(report.contains("schemaDigest=" + expectedDigest));
            assertNoSecrets(report);
            assertFalse(new File(reports, "sources").isDirectory());
            assertFalse(new File(reports, "original").isDirectory());
            assertFalse(new File(reports, "enhanced").isDirectory());
            assertEquals(expectedDigest, digestNow("net.csdn.jpa.ormfail"));
        } finally {
            loader.close();
        }
    }

    private String runDisabled(File classes) throws Exception {
        URLClassLoader loader = new URLClassLoader(new URL[]{classes.toURI().toURL()}, JPA.class.getClassLoader());
        try {
            Class<?> marker = Class.forName("net.csdn.jpa.ormdiag.ServiceFrameworkPackageAnchor", false, loader);
            JPA.configure(new JPA.CSDNORMConfiguration("development", mysqlSettings("net.csdn.jpa.ormdiag"), marker));
            Class<?> photo = Class.forName(PHOTO, true, loader);
            String outcome = exercise(photo);
            EnhancementDiagnostics diagnostics = JPA.defaultContext().diagnostics();
            assertFalse(diagnostics.enabled());
            assertEquals(0, diagnostics.hashComputations());
            assertEquals(0, diagnostics.bytecodeReads());
            assertEquals(0, diagnostics.methodInspections());
            assertEquals(0, diagnostics.diskWrites());
            assertEquals(0, JPA.dbInfo().schemaDigestComputations());
            String explicit = JPA.dbInfo().schemaDigest();
            assertEquals(1, JPA.dbInfo().schemaDigestComputations());
            assertEquals(explicit, JPA.dbInfo().schemaDigest());
            assertEquals(2, JPA.dbInfo().schemaDigestComputations());
            assertEquals(64, explicit.length());
            assertFalse(JPA.dbInfo().schemaSnapshot().contains(env.get("SF_COMPAT_MYSQL_PASSWORD")));
            JPA.shutdown();
            return outcome;
        } finally {
            loader.close();
        }
    }

    private String runEnabled(File classes) throws Exception {
        try (Connection connection = open(); Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM " + DBInfo.quoteIdentifier("sf_orm_diag_photo"));
            statement.execute("DELETE FROM " + DBInfo.quoteIdentifier("sf_orm_diag_album"));
        }
        File reports = reportDir("normal");
        URLClassLoader loader = new URLClassLoader(new URL[]{classes.toURI().toURL()}, JPA.class.getClassLoader());
        EnhancementDiagnostics diagnostics = EnhancementDiagnostics.enabled(reports);
        diagnostics.noteSafeMetadata("app-rev-1", EARLIER_DIGEST);
        diagnostics.recordApply(
                "earlier.Module",
                "mongo-document",
                1,
                0,
                1L,
                "boundary",
                Collections.<EnhancementDiagnostics.MethodChange>emptyList());
        EnhancementContext context = EnhancementContext.open(loader, diagnostics);
        owned = context;
        try {
            Class<?> marker = Class.forName("net.csdn.jpa.ormdiag.ServiceFrameworkPackageAnchor", false, loader);
            JPA.configure(new JPA.CSDNORMConfiguration("development", mysqlSettings("net.csdn.jpa.ormdiag"), marker), context);
            assertNull(JPA.defaultContext());
            assertFalse(context.isClosed());
            String outcome;
            String schemaDigest;
            try (EnhancementContext.Scope ignored = context.activate()) {
                assertEquals(1, JPA.dbInfo().schemaDigestComputations());
                schemaDigest = JPA.dbInfo().schemaDigest();
                assertEquals(2, JPA.dbInfo().schemaDigestComputations());
                assertTrue(tableBlock(JPA.dbInfo().schemaSnapshot(), "sf_orm_diag_photo").contains("column album_id INT"));
                Class<?> photo = Class.forName(PHOTO, true, loader);
                outcome = exercise(photo);
            }
            assertEquals("app-rev-1", diagnostics.configVersion());
            assertEquals(EARLIER_DIGEST, diagnostics.schemaDigest());
            diagnostics.recordApply(
                    "later.Controller",
                    "controller-filter",
                    1,
                    0,
                    1L,
                    "boundary",
                    Collections.<EnhancementDiagnostics.MethodChange>emptyList());
            assertOriginal(diagnostics, classes, ASSET);
            assertOriginal(diagnostics, classes, PHOTO);
            assertOriginal(diagnostics, classes, ALBUM);
            assertFalse(diagnostics.originalHash(ASSET).equals(diagnostics.originalHash(PHOTO)));

            Set<String> assetChanges = changedSignatures(diagnostics, ASSET);
            Set<String> photoChanges = changedSignatures(diagnostics, PHOTO);
            Set<String> albumChanges = changedSignatures(diagnostics, ALBUM);
            assertFalse(assetChanges.contains("getRootTag()Ljava/lang/String;"));
            assertFalse(assetChanges.contains("setRootTag(Ljava/lang/String;)V"));
            assertFalse(assetChanges.contains("setRootTag(Ljava/lang/String;Ljava/lang/String;)V"));
            assertFalse(photoChanges.contains("getName()Ljava/lang/String;"));
            assertFalse(photoChanges.contains("setName(Ljava/lang/String;)V"));
            assertFalse(photoChanges.contains("setName(Ljava/lang/String;Ljava/lang/String;)V"));
            assertFalse(photoChanges.contains("getAlbumId()Ljava/lang/Integer;"));
            assertTrue(albumChanges.contains("getPhotos()Ljava/util/List;"));
            assertTrue(photoChanges.contains("getId()Ljava/lang/Integer;"));

            EnhancementDiagnostics.MethodOrigin photos = origin(diagnostics, ALBUM, "photos()Lnet/csdn/jpa/association/Association;");
            assertEquals(EnhancementDiagnostics.MethodChange.ADDED, photos.change());
            assertEquals(EnhancementRuleIds.ASSOCIATION, photos.ruleId());
            assertEquals(1, photos.ruleVersion());
            EnhancementDiagnostics.MethodOrigin albumLink = origin(
                    diagnostics, PHOTO, "album(Lnet/csdn/jpa/ormdiag/DiagAlbum;)Lnet/csdn/jpa/ormdiag/DiagPhoto;");
            assertEquals(EnhancementDiagnostics.MethodChange.ADDED, albumLink.change());
            assertEquals(EnhancementRuleIds.ASSOCIATION, albumLink.ruleId());
            EnhancementDiagnostics.MethodOrigin finder = origin(
                    diagnostics, PHOTO, "findById(Ljava/lang/Object;)Lnet/csdn/jpa/model/JPABase;");
            assertEquals(EnhancementDiagnostics.MethodChange.ADDED, finder.change());
            assertEquals(EnhancementRuleIds.ORM_QUERY, finder.ruleId());
            EnhancementDiagnostics.MethodOrigin idGetter = origin(diagnostics, PHOTO, "getId()Ljava/lang/Integer;");
            assertEquals(EnhancementDiagnostics.MethodChange.ADDED, idGetter.change());
            assertEquals(EnhancementRuleIds.ENTITY_MAPPING, idGetter.ruleId());
            assertNull(diagnostics.originOf(ASSET, "findById(Ljava/lang/Object;)Lnet/csdn/jpa/model/JPABase;"));
            assertNull(diagnostics.originOf(ASSET, "getRootTag()Ljava/lang/String;"));

            boolean sawTreeReason = false;
            boolean sawAlreadyMapped = false;
            boolean sawLeafSkip = false;
            boolean sawOrmEvent = false;
            for (int i = 0; i < diagnostics.events().size(); i++) {
                EnhancementDiagnostics.Event event = diagnostics.events().get(i);
                if ("earlier.Module".equals(event.className()) || "later.Controller".equals(event.className())) {
                    assertEquals("app-rev-1", event.configVersion());
                    assertEquals(EARLIER_DIGEST, event.schemaDigest());
                    continue;
                }
                assertEquals("app-rev-1", event.configVersion());
                assertEquals(schemaDigest, event.schemaDigest());
                sawOrmEvent = true;
                if (ASSET.equals(event.className()) && "skip".equals(event.phase())
                        && EnhancementRuleIds.ORM_QUERY.equals(event.ruleId())) {
                    assertEquals("query methods are added only on leaf models", event.reason());
                    sawLeafSkip = true;
                }
                if (PHOTO.equals(event.className()) && EnhancementRuleIds.ENTITY_MAPPING.equals(event.ruleId())
                        && "apply".equals(event.phase())) {
                    if ("maps the model tree from this class".equals(event.reason()) && !event.methodChanges().isEmpty()) {
                        sawTreeReason = true;
                    }
                    if ("entity mapping already includes this class".equals(event.reason())) {
                        assertTrue(event.methodChanges().isEmpty());
                        sawAlreadyMapped = true;
                    }
                }
            }
            assertTrue(sawOrmEvent);
            assertTrue(sawTreeReason);
            assertTrue(sawAlreadyMapped);
            assertTrue(sawLeafSkip);
            context.close();
            owned = null;
            assertTrue(diagnostics.flushed());
            String report = readUtf8(new File(reports, "report.txt"));
            assertTrue(report.startsWith("# enhancement-diagnostics v1\n"));
            assertTrue(report.contains("class=" + PHOTO));
            assertTrue(report.contains("rule=" + EnhancementRuleIds.ORM_QUERY));
            assertTrue(report.contains("added,findById(Ljava/lang/Object;)Lnet/csdn/jpa/model/JPABase;," + EnhancementRuleIds.ORM_QUERY + ",1"));
            assertTrue(report.contains("added,album(Lnet/csdn/jpa/ormdiag/DiagAlbum;)Lnet/csdn/jpa/ormdiag/DiagPhoto;," + EnhancementRuleIds.ASSOCIATION + ",1"));
            assertTrue(report.contains("query%20methods%20are%20added%20only%20on%20leaf%20models"));
            assertTrue(report.contains("configVersion=app-rev-1"));
            assertFalse(report.contains("configVersion=" + DBInfo.DIAGNOSTICS_VERSION));
            assertTrue(report.contains("schemaDigest=" + schemaDigest));
            assertTrue(report.contains("schemaDigest=" + EARLIER_DIGEST));
            assertNoSecrets(report);
            assertFalse(report.contains("getRootTag()Ljava/lang/String;"));
            assertFalse(new File(reports, "sources").isDirectory());
            assertFalse(new File(reports, "original").isDirectory());
            return outcome;
        } finally {
            if (owned != null && !owned.isClosed()) {
                owned.close();
                owned = null;
            }
            loader.close();
        }
    }

    private String exercise(Class<?> photoType) throws Exception {
        Object photo = photoType.getDeclaredConstructor().newInstance();
        photoType.getMethod("setName", String.class).invoke(photo, "  pic ");
        assertEquals("pic", photoType.getMethod("getName").invoke(photo));
        photoType.getMethod("setName", String.class, String.class).invoke(photo, "n", "s");
        assertEquals("n:s", photoType.getMethod("getName").invoke(photo));
        photoType.getMethod("setRootTag", String.class).invoke(photo, "  root ");
        assertEquals("root", photoType.getMethod("getRootTag").invoke(photo));
        photoType.getMethod("setRootTag", String.class, String.class).invoke(photo, "p", "q");
        assertEquals("pq", photoType.getMethod("getRootTag").invoke(photo));
        assertNotNull(photoType.getMethod("album").invoke(photo));
        assertTrue(((JPABase) photo).save());
        Object id = photoType.getMethod("getId").invoke(photo);
        JPA.getJPAConfig().getJPAContext().closeTx(false);
        Object loaded = photoType.getMethod("findById", Object.class).invoke(null, id);
        assertEquals("n:s", photoType.getMethod("getName").invoke(loaded));
        assertEquals("pq", photoType.getMethod("getRootTag").invoke(loaded));
        long count = ((Long) photoType.getMethod("count").invoke(null)).longValue();
        Object rolled = photoType.getDeclaredConstructor().newInstance();
        photoType.getMethod("setName", String.class).invoke(rolled, "gone");
        photoType.getMethod("setRootTag", String.class).invoke(rolled, "temp");
        assertTrue(((JPABase) rolled).save());
        Object rolledId = photoType.getMethod("getId").invoke(rolled);
        JPA.getJPAConfig().getJPAContext().closeTx(true);
        assertNull(photoType.getMethod("findById", Object.class).invoke(null, rolledId));
        long after = ((Long) photoType.getMethod("count").invoke(null)).longValue();
        return "n:s|pq|" + count + "|" + after + "|missing";
    }

    private static void assertOriginal(EnhancementDiagnostics diagnostics, File classes, String binaryName) throws Exception {
        String untouched = untouchedHash(classes, binaryName);
        assertEquals(binaryName, untouched, diagnostics.originalHash(binaryName));
        boolean defined = false;
        List<EnhancementDiagnostics.Event> events = diagnostics.eventsFor(binaryName);
        for (int i = 0; i < events.size(); i++) {
            EnhancementDiagnostics.Event event = events.get(i);
            if ("define".equals(event.phase())) {
                defined = true;
                assertNotNull(event.resultSha256());
                assertFalse(untouched.equals(event.resultSha256()));
            }
        }
        assertTrue(defined);
    }

    private static String untouchedHash(File classes, String binaryName) throws Exception {
        ClassPool pool = new ClassPool(false);
        pool.appendClassPath(new LoaderClassPath(OrmDiagnosticsLiveTest.class.getClassLoader()));
        pool.insertClassPath(classes.getAbsolutePath());
        CtClass type = pool.get(binaryName);
        type.stopPruning(true);
        byte[] bytes = type.toBytecode();
        return sha256(bytes);
    }

    private static EnhancementDiagnostics.MethodOrigin origin(
            EnhancementDiagnostics diagnostics, String className, String signature) {
        EnhancementDiagnostics.MethodOrigin found = diagnostics.originOf(className, signature);
        assertNotNull(className + " " + signature, found);
        return found;
    }

    private static Set<String> changedSignatures(EnhancementDiagnostics diagnostics, String className) {
        Set<String> signatures = new LinkedHashSet<String>();
        List<EnhancementDiagnostics.Event> events = diagnostics.eventsFor(className);
        for (int i = 0; i < events.size(); i++) {
            List<EnhancementDiagnostics.MethodChange> changes = events.get(i).methodChanges();
            for (int j = 0; j < changes.size(); j++) {
                signatures.add(changes.get(j).signature());
            }
        }
        return signatures;
    }

    private String digestNow(String modelPackage) {
        EnhancementContext context = EnhancementContext.open(JPA.class.getClassLoader());
        try (EnhancementContext.Scope ignored = context.activate()) {
            Settings settings = mysqlSettings(modelPackage);
            JPA.CSDNORMConfiguration configuration = new JPA.CSDNORMConfiguration("development", settings, JPA.class);
            OrmSession.attach(context).bindConfiguration(configuration);
            DBInfo info = new DBInfo(settings);
            String digest = info.schemaDigest();
            assertEquals(1, info.schemaDigestComputations());
            assertFalse("schema snapshot contains a credential", info.schemaSnapshot().contains(env.get("SF_COMPAT_MYSQL_PASSWORD")));
            assertFalse(info.schemaSnapshot().contains(info.identity()));
            return digest;
        } finally {
            context.close();
        }
    }

    private void assertNoSecrets(String text) {
        assertFalse("diagnostics text contains a credential", text.contains(env.get("SF_COMPAT_MYSQL_PASSWORD")));
        assertFalse(text.contains("jdbc:"));
        assertFalse(text.contains("password="));
    }

    private static String tableBlock(String snapshot, String table) {
        String header = "\ntable " + table + "\n";
        int start = snapshot.indexOf(header);
        assertTrue(start >= 0);
        int next = snapshot.indexOf("\ntable ", start + header.length());
        return next < 0 ? snapshot.substring(start) : snapshot.substring(start, next);
    }

    private static boolean mysqlRequested() {
        if (Boolean.parseBoolean(System.getProperty("sf.orm.mysql", "false"))) {
            return true;
        }
        return "true".equalsIgnoreCase(System.getenv("SF_ORM_MYSQL"));
    }

    private void requireMysql() {
        Assume.assumeTrue("sf.orm.mysql is not enabled; a skip is not acceptance", mysqlRequested());
        String path = System.getenv("SF_COMPAT_ENV_FILE");
        if (path == null || path.length() == 0) {
            fail("sf.orm.mysql is enabled but SF_COMPAT_ENV_FILE is missing");
        }
        env = readEnv(new File(path));
        for (String key : new String[]{
                "SF_COMPAT_MYSQL_HOST", "SF_COMPAT_MYSQL_PORT", "SF_COMPAT_MYSQL_USER",
                "SF_COMPAT_MYSQL_PASSWORD", "SF_COMPAT_MYSQL_DATABASE"}) {
            if (env.get(key) == null || env.get(key).length() == 0) {
                fail("compat env is missing " + key);
            }
        }
        if (!"sf_compat".equals(env.get("SF_COMPAT_MYSQL_DATABASE"))) {
            fail("refusing to use a database other than sf_compat");
        }
    }

    private Settings mysqlSettings(String modelPackage) {
        return ImmutableSettings.settingsBuilder()
                .put("development.datasources.mysql.disable", "false")
                .put("development.datasources.mysql.host", env.get("SF_COMPAT_MYSQL_HOST"))
                .put("development.datasources.mysql.port", env.get("SF_COMPAT_MYSQL_PORT"))
                .put("development.datasources.mysql.database", env.get("SF_COMPAT_MYSQL_DATABASE"))
                .put("development.datasources.mysql.username", env.get("SF_COMPAT_MYSQL_USER"))
                .put("development.datasources.mysql.password", env.get("SF_COMPAT_MYSQL_PASSWORD"))
                .put("development.datasources.mysql.show_sql", "false")
                .put("development.datasources.mysql.driver", "com.mysql.jdbc.Driver")
                .put("development.datasources.mysql.jdbc.connectTimeout", "5000")
                .put("application.model", modelPackage)
                .build();
    }

    private static Settings disabledSettings() {
        return ImmutableSettings.settingsBuilder()
                .put("development.datasources.mysql.disable", "true")
                .put("application.model", "net.csdn.jpa.ormdiag")
                .build();
    }

    private void createPhotoTable() throws Exception {
        dropTables();
        try (Connection connection = open(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE " + DBInfo.quoteIdentifier("sf_orm_diag_album")
                    + " (id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, title VARCHAR(64) NULL)"
                    + " ENGINE=InnoDB DEFAULT CHARSET=utf8");
            statement.execute("CREATE TABLE " + DBInfo.quoteIdentifier("sf_orm_diag_photo")
                    + " (id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, root_tag VARCHAR(64) NULL,"
                    + " name VARCHAR(64) NULL, album_id INT NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8");
        }
    }

    private void createFailureTable() throws Exception {
        dropTables();
        try (Connection connection = open(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE " + DBInfo.quoteIdentifier("sf_orm_fail_locked")
                    + " (id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, note VARCHAR(64) NULL)"
                    + " ENGINE=InnoDB DEFAULT CHARSET=utf8");
        }
    }

    private void dropTables() throws Exception {
        try (Connection connection = open(); Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS " + DBInfo.quoteIdentifier("sf_orm_diag_photo"));
            statement.execute("DROP TABLE IF EXISTS " + DBInfo.quoteIdentifier("sf_orm_diag_album"));
            statement.execute("DROP TABLE IF EXISTS " + DBInfo.quoteIdentifier("sf_orm_fail_locked"));
        }
    }

    private Connection open() throws Exception {
        Class.forName("com.mysql.jdbc.Driver");
        return DriverManager.getConnection(
                "jdbc:mysql://" + env.get("SF_COMPAT_MYSQL_HOST") + ":" + env.get("SF_COMPAT_MYSQL_PORT")
                        + "/" + env.get("SF_COMPAT_MYSQL_DATABASE")
                        + "?useUnicode=true&characterEncoding=utf8&connectTimeout=5000",
                env.get("SF_COMPAT_MYSQL_USER"),
                env.get("SF_COMPAT_MYSQL_PASSWORD"));
    }

    private File compileFixtures() throws Exception {
        List<File> sources = new ArrayList<File>();
        collectJava(resourceDir("ormdiag"), sources);
        collectJava(resourceDir("ormfail"), sources);
        File classes = folder.newFolder("classes");
        File generated = folder.newFolder("generated");
        List<String> command = new ArrayList<String>();
        command.add(javacBinary().getAbsolutePath());
        String spec = System.getProperty("java.specification.version");
        if ("1.8".equals(spec) || "8".equals(spec)) {
            command.add("-source");
            command.add("8");
            command.add("-target");
            command.add("8");
        } else {
            command.add("--release");
            command.add("8");
        }
        command.add("-encoding");
        command.add("UTF-8");
        command.add("-classpath");
        command.add(System.getProperty("java.class.path"));
        command.add("-processor");
        command.add("net.csdn.jpa.query.ServiceFrameworkQueryProcessor");
        command.add("-processorpath");
        command.add(System.getProperty("java.class.path"));
        command.add("-d");
        command.add(classes.getAbsolutePath());
        command.add("-s");
        command.add(generated.getAbsolutePath());
        for (int i = 0; i < sources.size(); i++) {
            command.add(sources.get(i).getAbsolutePath());
        }
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        byte[] output = readAll(process.getInputStream());
        int exit = process.waitFor();
        if (exit != 0) {
            fail("fixture compile failed: " + new String(output, StandardCharsets.UTF_8));
        }
        return classes;
    }

    private static File resourceDir(String name) throws Exception {
        URL url = OrmDiagnosticsLiveTest.class.getResource("/net/csdn/jpa/" + name + "/ServiceFrameworkPackageAnchor.java");
        if (url == null) {
            throw new IllegalStateException("fixture sources are not on the test classpath: " + name);
        }
        return new File(url.toURI());
    }

    private static void collectJava(File start, List<File> sources) {
        File directory = start.isDirectory() ? start : start.getParentFile();
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }
        for (int i = 0; i < files.length; i++) {
            if (files[i].getName().endsWith(".java")) {
                sources.add(files[i]);
            }
        }
    }

    private static File javacBinary() {
        String home = System.getProperty("java.home");
        File direct = new File(home, "bin/javac");
        if (direct.isFile()) {
            return direct;
        }
        File sibling = new File(home, "../bin/javac");
        if (sibling.isFile()) {
            return sibling;
        }
        throw new IllegalStateException("javac was not found under " + home);
    }

    private static File reportDir(String kind) {
        String spec = System.getProperty("java.specification.version", "unknown").replace('.', '_');
        File dir = new File("/tmp/sf-orm-diagnostics-report", kind + "-" + spec);
        if (!dir.getAbsolutePath().startsWith("/tmp/sf-orm-diagnostics-report/")) {
            throw new IllegalStateException("refusing to replace " + dir);
        }
        deleteQuietly(dir);
        return dir;
    }

    private static void deleteQuietly(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (int i = 0; i < children.length; i++) {
                    deleteQuietly(children[i]);
                }
            }
        }
        file.delete();
    }

    private static Map<String, String> readEnv(File file) {
        try {
            Map<String, String> values = new LinkedHashMap<String, String>();
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.length() == 0 || line.charAt(0) == '#') {
                    continue;
                }
                int eq = line.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                values.put(line.substring(0, eq), line.substring(eq + 1));
            }
            return values;
        } catch (Exception e) {
            throw new IllegalStateException("compat env file could not be read", e);
        }
    }

    private static byte[] readAll(InputStream input) throws Exception {
        byte[] buffer = new byte[4096];
        int read;
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        while ((read = input.read(buffer)) >= 0) {
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static String readUtf8(File file) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
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
}
