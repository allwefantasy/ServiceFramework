package net.csdn.common.scan;

import net.csdn.common.enhancer.EnhancementFailure;
import org.junit.Test;
import tech.mlsql.common.utils.reflect.ClassPath;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DefaultScanServiceTest {

    @Test
    public void scanArchivesWithoutCallbackReadsClasspathClassesInResourceOrder() throws Exception {
        DefaultScanService service = new DefaultScanService();
        service.setLoader(DefaultScanService.class);
        String packageName = "net.csdn.common.enhancer.fixture";
        List<ClassPath.ClassInfo> expected = new ArrayList<ClassPath.ClassInfo>(
                ClassPath.from(DefaultScanService.class.getClassLoader()).getTopLevelClassesRecursive(packageName));
        Collections.sort(expected, new Comparator<ClassPath.ClassInfo>() {
            @Override
            public int compare(ClassPath.ClassInfo left, ClassPath.ClassInfo right) {
                return left.getResourceName().compareTo(right.getResourceName());
            }
        });
        List<ClassPath.ClassInfo> classes = new ArrayList<ClassPath.ClassInfo>();
        for (int i = 0; i < expected.size(); i++) {
            if (expected.get(i).getResourceName().endsWith(".class")) {
                classes.add(expected.get(i));
            }
        }
        List<InputStream> streams = service.scanArchives(packageName);
        try {
            assertTrue(streams.size() > 0);
            assertEquals(classes.size(), streams.size());
            for (int i = 0; i < classes.size(); i++) {
                assertArrayEquals(read(classes.get(i).url().openStream()), read(streams.get(i)));
            }
        } finally {
            for (int i = 0; i < streams.size(); i++) {
                streams.get(i).close();
            }
        }
    }

    @Test
    public void callbackNullIsOmittedAndFailureKeepsContext() throws Exception {
        DefaultScanService service = new DefaultScanService();
        service.setLoader(DefaultScanService.class);
        List<Class> matches = service.scanArchives("net.csdn.common.enhancer.fixture", new ScanService.LoadClassEnhanceCallBack() {
            private int seen;

            @Override
            public Class loaded(java.io.DataInputStream classFile) {
                seen++;
                return seen % 2 == 0 ? Object.class : null;
            }
        });
        assertFalse(matches.contains(null));
        assertTrue(matches.size() > 0);
        try {
            service.scanArchives("net.csdn.common.enhancer.fixture", new ScanService.LoadClassEnhanceCallBack() {
                @Override
                public Class loaded(java.io.DataInputStream classFile) {
                    throw new IllegalStateException("scan-boom");
                }
            });
            fail("callback");
        } catch (EnhancementFailure failure) {
            assertEquals(EnhancementFailure.Category.SCAN, failure.getCategory());
            assertEquals("scan", failure.getPhase());
            assertTrue(failure.getClassName().endsWith(".class"));
            assertTrue(failure.getCause() instanceof IllegalStateException);
            assertEquals("scan-boom", failure.getCause().getMessage());
        }
    }

    @Test
    public void scanClassClosesEveryStreamWhenCallbackFailsAndSkipsNull() throws Exception {
        DefaultScanService service = new DefaultScanService();
        SpyStream first = new SpyStream(new byte[]{1});
        SpyStream second = new SpyStream(new byte[]{2});
        SpyStream third = new SpyStream(new byte[]{3});
        try {
            service.scanClass(Arrays.<InputStream>asList(first, second, third), new ScanService.LoadClassEnhanceCallBack() {
                @Override
                public Class loaded(java.io.DataInputStream classFile) {
                    throw new IllegalStateException("stop");
                }
            });
            fail("close");
        } catch (EnhancementFailure failure) {
            assertTrue(failure.getCause() instanceof IllegalStateException);
        }
        assertTrue(first.closed && second.closed && third.closed);

        SpyStream alpha = new SpyStream(new byte[]{4});
        SpyStream beta = new SpyStream(new byte[]{5});
        List<Class> found = service.scanClass(Arrays.<InputStream>asList(alpha, beta), new ScanService.LoadClassEnhanceCallBack() {
            private int seen;

            @Override
            public Class loaded(java.io.DataInputStream classFile) {
                seen++;
                return seen == 1 ? null : String.class;
            }
        });
        assertEquals(Collections.singletonList(String.class), found);
        assertTrue(alpha.closed && beta.closed);
    }

    @Test
    public void directoryJarAndEncodedSpacePathsSkipNonClasses() throws Exception {
        File root = new File(System.getProperty("java.io.tmpdir"), "sf scan " + System.nanoTime());
        File classes = new File(root, "scanpkg");
        File source = new File(root, "src/scanpkg/LoaderHook.java");
        source.getParentFile().mkdirs();
        classes.mkdirs();
        write(source, "package scanpkg; public class LoaderHook { public static int id() { return 7; } }");
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertTrue(compiler != null);
        assertEquals(0, compiler.run(null, null, null, "-d", root.getPath(), source.getPath()));
        writeBytes(new File(classes, "A.class"), new byte[]{'A', 'A', 'A'});
        writeBytes(new File(classes, "B.class"), new byte[]{'B', 'B', 'B'});
        writeBytes(new File(classes, "note.txt"), new byte[]{'n'});
        writeBytes(new File(classes, "nested.jar"), new byte[]{'j'});
        new File(classes, "sub").mkdirs();
        writeBytes(new File(classes, "sub/C.class"), new byte[]{'C', 'C', 'C'});

        URLClassLoader loader = new URLClassLoader(new URL[]{root.toURI().toURL()}, null);
        try {
            Class<?> anchor = loader.loadClass("scanpkg.LoaderHook");
            assertEquals(7, ((Integer) anchor.getMethod("id").invoke(null)).intValue());
            DefaultScanService service = new DefaultScanService();
            service.setLoader(anchor);
            List<InputStream> streams = service.scanArchives("scanpkg");
            try {
                assertEquals(4, streams.size());
                assertArrayEquals(new byte[]{'A', 'A', 'A'}, read(streams.get(0)));
                assertArrayEquals(new byte[]{'B', 'B', 'B'}, read(streams.get(1)));
                assertTrue(read(streams.get(2)).length > 4);
                assertArrayEquals(new byte[]{'C', 'C', 'C'}, read(streams.get(3)));
            } finally {
                closeAll(streams);
            }
        } finally {
            loader.close();
        }

        DefaultScanService files = new DefaultScanService();
        URL directory = classes.toURI().toURL();
        assertTrue(directory.toString().contains("%20") || directory.getPath().contains("%20"));
        List<InputStream> dirStreams = files.scanArchives(directory);
        try {
            assertEquals(4, dirStreams.size());
            assertArrayEquals(new byte[]{'A', 'A', 'A'}, read(dirStreams.get(0)));
            assertArrayEquals(new byte[]{'B', 'B', 'B'}, read(dirStreams.get(1)));
            assertArrayEquals(new byte[]{'C', 'C', 'C'}, read(dirStreams.get(3)));
        } finally {
            closeAll(dirStreams);
        }

        URL classUrl = new File(classes, "A.class").toURI().toURL();
        assertTrue(classUrl.toString().contains("%20") || classUrl.getPath().contains("%20"));
        List<InputStream> one = files.scanArchives(classUrl);
        try {
            assertEquals(1, one.size());
            assertArrayEquals(new byte[]{'A', 'A', 'A'}, read(one.get(0)));
        } finally {
            closeAll(one);
        }

        File jar = new File(root, "sample classes.jar");
        JarOutputStream jarOutput = new JarOutputStream(new FileOutputStream(jar));
        try {
            put(jarOutput, "scanpkg/B.class", new byte[]{'B', 'B', 'B'});
            put(jarOutput, "scanpkg/A.class", new byte[]{'A', 'A', 'A'});
            put(jarOutput, "scanpkg/note.txt", new byte[]{'n'});
            put(jarOutput, "scanpkg/nested.jar", new byte[]{'j'});
            put(jarOutput, "scanpkg/sub/C.class", new byte[]{'C', 'C', 'C'});
        } finally {
            jarOutput.close();
        }
        URL jarUrl = jar.toURI().toURL();
        assertTrue(jarUrl.toString().contains("%20") || jarUrl.getPath().contains("%20"));
        List<InputStream> jarStreams = files.scanArchives(jarUrl);
        try {
            assertEquals(3, jarStreams.size());
            assertArrayEquals(new byte[]{'A', 'A', 'A'}, read(jarStreams.get(0)));
            assertArrayEquals(new byte[]{'B', 'B', 'B'}, read(jarStreams.get(1)));
            assertArrayEquals(new byte[]{'C', 'C', 'C'}, read(jarStreams.get(2)));
        } finally {
            closeAll(jarStreams);
        }
    }

    @Test
    public void callbackKeepsOneLogicalStreamOpenWhileTheListApiKeepsThemAll() throws Exception {
        DefaultScanService service = new DefaultScanService();
        service.setLoader(DefaultScanService.class);
        String packageName = "net.csdn.common.enhancer.fixture";
        service.resetStreamAccounting();
        List<InputStream> all = service.scanArchives(packageName);
        try {
            assertTrue(all.size() > 1);
            assertEquals(all.size(), service.peakOpenStreams());
            assertEquals(all.size(), service.currentOpenStreams());
            for (int i = 0; i < all.size(); i++) {
                assertTrue(all.get(i).read() >= -1);
            }
        } finally {
            closeAll(all);
        }

        service.resetStreamAccounting();
        final int[] seen = new int[1];
        final int[] highestDuringCallback = new int[1];
        List<Class> matches = service.scanArchives(packageName, new ScanService.LoadClassEnhanceCallBack() {
            @Override
            public Class loaded(java.io.DataInputStream classFile) {
                seen[0]++;
                int open = service.currentOpenStreams();
                if (open > highestDuringCallback[0]) {
                    highestDuringCallback[0] = open;
                }
                return null;
            }
        });
        assertEquals(0, matches.size());
        assertEquals(all.size(), seen[0]);
        assertEquals(1, highestDuringCallback[0]);
        assertEquals(1, service.peakOpenStreams());
        assertEquals(0, service.currentOpenStreams());
        assertTrue(service.peakOpenStreams() < all.size());
    }

    @Test
    public void callbackFailureClosesOnlyTheResourceItOpened() throws Exception {
        AccountingScan service = new AccountingScan();
        service.setLoader(DefaultScanService.class);
        service.resetStreamAccounting();
        try {
            service.scanArchives("net.csdn.common.enhancer.fixture", new ScanService.LoadClassEnhanceCallBack() {
                @Override
                public Class loaded(java.io.DataInputStream classFile) {
                    throw new IllegalStateException("scan-boom");
                }
            });
            fail("callback");
        } catch (EnhancementFailure failure) {
            assertEquals("scan-boom", failure.getCause().getMessage());
            assertTrue(failure.getClassName().endsWith(".class"));
        }
        assertEquals(1, service.opened.size());
        assertTrue(service.opened.get(0).closed);
        assertEquals(1, service.peakOpenStreams());
        assertEquals(0, service.currentOpenStreams());
    }

    @Test
    public void callbackReadsSpacedDirectoryClassesOneAtATime() throws Exception {
        File root = new File(System.getProperty("java.io.tmpdir"), "sf scan cb " + System.nanoTime());
        File classes = new File(root, "scanpkg");
        File source = new File(root, "src/scanpkg/LoaderHook.java");
        classes.mkdirs();
        write(source, "package scanpkg; public class LoaderHook { public static int id() { return 7; } }");
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertTrue(compiler != null);
        assertEquals(0, compiler.run(null, null, null, "-d", root.getPath(), source.getPath()));
        writeBytes(new File(classes, "A.class"), new byte[]{'A'});
        writeBytes(new File(classes, "B.class"), new byte[]{'B'});
        writeBytes(new File(classes, "note.txt"), new byte[]{'n'});
        writeBytes(new File(classes, "nested.jar"), new byte[]{'j'});
        new File(classes, "sub").mkdirs();
        writeBytes(new File(classes, "sub/C.class"), new byte[]{'C'});
        URL directory = root.toURI().toURL();
        assertTrue(directory.toString().contains("%20") || directory.getPath().contains("%20"));
        URLClassLoader loader = new URLClassLoader(new URL[]{directory}, null);
        try {
            Class<?> anchor = loader.loadClass("scanpkg.LoaderHook");
            AccountingScan service = new AccountingScan();
            service.setLoader(anchor);
            service.resetStreamAccounting();
            final List<byte[]> payloads = new ArrayList<byte[]>();
            List<Class> matches = service.scanArchives("scanpkg", new ScanService.LoadClassEnhanceCallBack() {
                @Override
                public Class loaded(java.io.DataInputStream classFile) {
                    try {
                        payloads.add(read(classFile));
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                    assertEquals(1, service.currentOpenStreams());
                    return null;
                }
            });
            assertEquals(0, matches.size());
            assertEquals(4, payloads.size());
            assertArrayEquals(new byte[]{'A'}, payloads.get(0));
            assertArrayEquals(new byte[]{'B'}, payloads.get(1));
            assertTrue(payloads.get(2).length > 4);
            assertArrayEquals(new byte[]{'C'}, payloads.get(3));
            assertEquals(1, service.peakOpenStreams());
            assertEquals(0, service.currentOpenStreams());
            assertEquals(4, service.opened.size());
            for (int i = 0; i < service.opened.size(); i++) {
                assertTrue(service.opened.get(i).closed);
            }
        } finally {
            loader.close();
        }
    }

    @Test
    public void partialArchiveOpenClosesStreamsAlreadyOpened() throws Exception {
        File root = new File(System.getProperty("java.io.tmpdir"), "sf-scan-close-" + System.nanoTime());
        root.mkdirs();
        File good = new File(root, "Good.class");
        writeBytes(good, new byte[]{1, 2, 3});
        SpyScan service = new SpyScan();
        try {
            service.scanArchives(good.toURI().toURL(), new File(root, "missing.class").toURI().toURL());
            fail("missing");
        } catch (EnhancementFailure failure) {
            assertEquals(EnhancementFailure.Category.SCAN, failure.getCategory());
            assertTrue(failure.getCause() instanceof java.io.FileNotFoundException);
        }
        assertEquals(1, service.opened.size());
        assertTrue(service.opened.get(0).closed);
    }

    private static void put(JarOutputStream output, String name, byte[] bytes) throws IOException {
        output.putNextEntry(new JarEntry(name));
        output.write(bytes);
        output.closeEntry();
    }

    private static void write(File file, String text) throws IOException {
        file.getParentFile().mkdirs();
        OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
        try {
            writer.write(text);
        } finally {
            writer.close();
        }
    }

    private static void writeBytes(File file, byte[] bytes) throws IOException {
        file.getParentFile().mkdirs();
        FileOutputStream output = new FileOutputStream(file);
        try {
            output.write(bytes);
        } finally {
            output.close();
        }
    }

    private static byte[] read(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[256];
        int count;
        while ((count = input.read(buffer)) >= 0) {
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static void closeAll(List<InputStream> streams) throws IOException {
        for (int i = 0; i < streams.size(); i++) {
            streams.get(i).close();
        }
    }

    public static class AccountingScan extends DefaultScanService {
        final List<SpyStream> opened = new ArrayList<SpyStream>();

        @Override
        InputStream openClassInfo(ClassPath.ClassInfo info) throws IOException {
            SpyStream stream = new SpyStream(super.openClassInfo(info));
            opened.add(stream);
            return stream;
        }
    }

    public static class SpyScan extends DefaultScanService {
        final List<SpyStream> opened = new ArrayList<SpyStream>();

        @Override
        InputStream openFile(File file) throws IOException {
            SpyStream stream = new SpyStream(super.openFile(file));
            opened.add(stream);
            return stream;
        }
    }

    public static class SpyStream extends FilterInputStream {
        boolean closed;

        SpyStream(byte[] data) {
            super(new ByteArrayInputStream(data));
        }

        SpyStream(InputStream input) {
            super(input);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }
}
