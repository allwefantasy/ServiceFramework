package net.csdn.common.scan;

import net.csdn.common.enhancer.EnhancementFailure;
import tech.mlsql.common.utils.reflect.ClassPath;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * BlogInfo: WilliamZhu
 * Date: 12-6-4
 * Time: 下午2:04
 * <p>
 * Package scans use common-utils {@link ClassPath} resource names, then open those
 * resources. Directory and plain JAR URLs are expanded to {@code .class} entries in
 * path order. Nested JARs are not opened. A callback return of null is not a match.
 * Callback failures keep their cause and close every stream already opened.
 * <p>
 * {@link #scanArchives(String, LoadClassEnhanceCallBack)} opens one resource,
 * runs the callback, and closes that stream before opening the next. The list
 * APIs still return every stream to the caller, who owns closing them.
 * {@link #peakOpenStreams()} counts those logical {@link InputStream}s. It is
 * not a count of operating-system file descriptors.
 */
public class DefaultScanService implements ScanService {

    private Class loader = DefaultScanService.class;
    private int openStreamCount;
    private int peakOpenStreams;

    public Class getLoader() {
        return loader;
    }

    public void setLoader(Class loader) {
        this.loader = loader;
    }

    /**
     * Highest number of logical class streams this service had open at once
     * since the last {@link #resetStreamAccounting()}. Not a file-descriptor count.
     * List APIs leave the count at the number of streams they returned, because
     * the caller owns those streams. The package callback API returns to zero
     * between resources.
     */
    public int peakOpenStreams() {
        return peakOpenStreams;
    }

    public int currentOpenStreams() {
        return openStreamCount;
    }

    public void resetStreamAccounting() {
        openStreamCount = 0;
        peakOpenStreams = 0;
    }

    @Override
    public List<InputStream> scanArchives(String packageName) throws IOException {
        List<ClassPath.ClassInfo> infos = findClasses(packageName);
        return openClassInfos(infos);
    }

    @Override
    public List<Class> scanArchives(String packageName, LoadClassEnhanceCallBack loadClassEnhanceCallBack) throws IOException {
        if (loadClassEnhanceCallBack == null) {
            throw scanFailure(packageName, "callback is required", null);
        }
        List<ClassPath.ClassInfo> infos = findClasses(packageName);
        List<Class> classes = new ArrayList<Class>();
        for (int i = 0; i < infos.size(); i++) {
            ClassPath.ClassInfo info = infos.get(i);
            InputStream stream = openOne(info);
            noteStreamOpened();
            try {
                Class loaded = scanClass(stream, loadClassEnhanceCallBack);
                if (loaded != null) {
                    classes.add(loaded);
                }
            } catch (Throwable thrown) {
                if (thrown instanceof Error) {
                    throw (Error) thrown;
                }
                if (thrown instanceof EnhancementFailure) {
                    throw (EnhancementFailure) thrown;
                }
                throw scanFailure(info.getResourceName(), "callback failed", thrown);
            } finally {
                noteStreamClosed();
            }
        }
        return classes;
    }

    @Override
    public List<InputStream> scanArchives(URL... urls) throws IOException {
        List<InputStream> opened = new ArrayList<InputStream>();
        try {
            if (urls == null) {
                return opened;
            }
            for (int i = 0; i < urls.length; i++) {
                URL url = urls[i];
                if (url == null) {
                    throw scanFailure("url[" + i + "]", "null url", null);
                }
                openUrl(url, opened);
            }
            return opened;
        } catch (Throwable thrown) {
            closeQuietly(opened, 0);
            if (thrown instanceof Error) {
                throw (Error) thrown;
            }
            if (thrown instanceof EnhancementFailure) {
                throw (EnhancementFailure) thrown;
            }
            if (thrown instanceof IOException) {
                throw scanFailure(thrown.getMessage(), "failed while opening archives", thrown);
            }
            throw scanFailure(String.valueOf(thrown.getMessage()), "failed while opening archives", thrown);
        }
    }

    @Override
    public Class scanClass(InputStream bits, LoadClassEnhanceCallBack loadClassEnhanceCallBack) throws IOException {
        if (bits == null || loadClassEnhanceCallBack == null) {
            throw scanFailure(null, "stream and callback are required", null);
        }
        DataInputStream dstream = new DataInputStream(new BufferedInputStream(bits));
        Throwable failure = null;
        try {
            return loadClassEnhanceCallBack.loaded(dstream);
        } catch (Throwable thrown) {
            failure = thrown;
            if (thrown instanceof Error) {
                throw (Error) thrown;
            }
            if (thrown instanceof RuntimeException) {
                throw (RuntimeException) thrown;
            }
            throw scanFailure(null, "callback failed", thrown);
        } finally {
            try {
                dstream.close();
            } catch (IOException closeError) {
                if (failure == null) {
                    throw closeError;
                }
                failure.addSuppressed(closeError);
            }
        }
    }

    @Override
    public List<Class> scanClass(List<InputStream> inputStreams, LoadClassEnhanceCallBack loadClassEnhanceCallBack) throws IOException {
        if (inputStreams == null || loadClassEnhanceCallBack == null) {
            throw scanFailure(null, "streams and callback are required", null);
        }
        List<Class> classes = new ArrayList<Class>();
        for (int i = 0; i < inputStreams.size(); i++) {
            try {
                Class loaded = scanClass(inputStreams.get(i), loadClassEnhanceCallBack);
                if (loaded != null) {
                    classes.add(loaded);
                }
            } catch (Throwable thrown) {
                closeQuietly(inputStreams, i + 1);
                if (thrown instanceof Error) {
                    throw (Error) thrown;
                }
                if (thrown instanceof EnhancementFailure) {
                    throw (EnhancementFailure) thrown;
                }
                throw scanFailure("stream[" + i + "]", "callback failed", thrown);
            }
        }
        return classes;
    }

    InputStream openFile(File file) throws IOException {
        return new FileInputStream(file);
    }

    private List<ClassPath.ClassInfo> findClasses(String packageName) {
        if (packageName == null) {
            throw scanFailure(null, "packageName is required", null);
        }
        if (loader == null || loader.getClassLoader() == null) {
            throw scanFailure(packageName, "loader has no ClassLoader", null);
        }
        try {
            ClassPath classPath = ClassPath.from(loader.getClassLoader());
            List<ClassPath.ClassInfo> infos = new ArrayList<ClassPath.ClassInfo>(
                    classPath.getTopLevelClassesRecursive(packageName));
            Collections.sort(infos, new Comparator<ClassPath.ClassInfo>() {
                @Override
                public int compare(ClassPath.ClassInfo left, ClassPath.ClassInfo right) {
                    return left.getResourceName().compareTo(right.getResourceName());
                }
            });
            List<ClassPath.ClassInfo> classes = new ArrayList<ClassPath.ClassInfo>();
            for (int i = 0; i < infos.size(); i++) {
                String resource = infos.get(i).getResourceName();
                if (isClassResource(resource)) {
                    classes.add(infos.get(i));
                }
            }
            return classes;
        } catch (EnhancementFailure failure) {
            throw failure;
        } catch (Exception e) {
            throw scanFailure(packageName, "classpath scan failed", e);
        }
    }

    private List<InputStream> openClassInfos(List<ClassPath.ClassInfo> infos) {
        List<InputStream> opened = new ArrayList<InputStream>();
        try {
            for (int i = 0; i < infos.size(); i++) {
                opened.add(openClassInfo(infos.get(i)));
                noteStreamOpened();
            }
            return opened;
        } catch (Throwable thrown) {
            for (int i = opened.size() - 1; i >= 0; i--) {
                noteStreamClosed();
            }
            closeQuietly(opened, 0);
            if (thrown instanceof Error) {
                throw (Error) thrown;
            }
            if (thrown instanceof EnhancementFailure) {
                throw (EnhancementFailure) thrown;
            }
            throw scanFailure(null, "failed to open class resource", thrown);
        }
    }

    private InputStream openOne(ClassPath.ClassInfo info) {
        try {
            return openClassInfo(info);
        } catch (Throwable thrown) {
            if (thrown instanceof Error) {
                throw (Error) thrown;
            }
            if (thrown instanceof EnhancementFailure) {
                throw (EnhancementFailure) thrown;
            }
            throw scanFailure(info.getResourceName(), "failed to open class resource", thrown);
        }
    }

    /**
     * Opens one class resource. The caller owns the stream. Package scans use
     * the loader resource first, then the ClassPath URL.
     */
    InputStream openClassInfo(ClassPath.ClassInfo info) throws IOException {
        InputStream stream = loader.getClassLoader().getResourceAsStream(info.getResourceName());
        if (stream == null) {
            stream = info.url().openStream();
        }
        return stream;
    }

    private void noteStreamOpened() {
        openStreamCount++;
        if (openStreamCount > peakOpenStreams) {
            peakOpenStreams = openStreamCount;
        }
    }

    private void noteStreamClosed() {
        if (openStreamCount > 0) {
            openStreamCount--;
        }
    }

    private void openUrl(URL url, List<InputStream> opened) throws IOException {
        String protocol = url.getProtocol();
        if ("file".equals(protocol)) {
            File file = toFile(url);
            if (file.isDirectory()) {
                openDirectory(file, opened);
            } else if (isJarFile(file)) {
                openJar(file, opened);
            } else if (file.getName().endsWith(".class")) {
                opened.add(openFile(file));
            }
            return;
        }
        if ("jar".equals(protocol)) {
            if (!jarEntryIsClass(url)) {
                return;
            }
            opened.add(url.openStream());
            return;
        }
        throw scanFailure(url.toString(), "unsupported archive protocol " + protocol, null);
    }

    private void openDirectory(File root, List<InputStream> opened) throws IOException {
        List<File> files = new ArrayList<File>();
        collectClassFiles(root, new HashSet<String>(), files);
        Collections.sort(files, new Comparator<File>() {
            @Override
            public int compare(File left, File right) {
                return left.getAbsolutePath().compareTo(right.getAbsolutePath());
            }
        });
        for (int i = 0; i < files.size(); i++) {
            opened.add(openFile(files.get(i)));
        }
    }

    private void collectClassFiles(File directory, Set<String> seen, List<File> files) throws IOException {
        String canonical = directory.getCanonicalPath();
        if (!seen.add(canonical)) {
            return;
        }
        File[] children = directory.listFiles();
        if (children == null) {
            throw scanFailure(directory.getPath(), "cannot list directory", null);
        }
        for (int i = 0; i < children.length; i++) {
            File child = children[i];
            if (child.isDirectory()) {
                collectClassFiles(child, seen, files);
            } else if (isClassResource(child.getName()) && !isJarFile(child)) {
                files.add(child);
            }
        }
    }

    private void openJar(File file, List<InputStream> opened) throws IOException {
        JarFile jar = new JarFile(file);
        try {
            List<JarEntry> entries = new ArrayList<JarEntry>();
            Enumeration<JarEntry> enumeration = jar.entries();
            while (enumeration.hasMoreElements()) {
                JarEntry entry = enumeration.nextElement();
                if (!entry.isDirectory() && isClassResource(entry.getName()) && entry.getName().indexOf(".jar") < 0) {
                    entries.add(entry);
                }
            }
            Collections.sort(entries, new Comparator<JarEntry>() {
                @Override
                public int compare(JarEntry left, JarEntry right) {
                    return left.getName().compareTo(right.getName());
                }
            });
            for (int i = 0; i < entries.size(); i++) {
                InputStream entryStream = jar.getInputStream(entries.get(i));
                try {
                    opened.add(new ByteArrayInputStream(readAll(entryStream)));
                } finally {
                    entryStream.close();
                }
            }
        } finally {
            jar.close();
        }
    }

    private static boolean isClassResource(String name) {
        return name != null && name.endsWith(".class") && !name.endsWith("module-info.class");
    }

    private static boolean isJarFile(File file) {
        String name = file.getName();
        return name.endsWith(".jar") || name.endsWith(".zip");
    }

    private static boolean jarEntryIsClass(URL url) {
        String file = url.getFile();
        int bang = file.lastIndexOf('!');
        String entry = bang >= 0 ? file.substring(bang + 1) : file;
        if (entry.startsWith("/")) {
            entry = entry.substring(1);
        }
        return isClassResource(entry);
    }

    private static File toFile(URL url) {
        try {
            return new File(url.toURI());
        } catch (URISyntaxException e) {
            try {
                return new File(URLDecoder.decode(url.getPath(), "UTF-8"));
            } catch (IOException ex) {
                throw scanFailure(url.toString(), "cannot decode file url", ex);
            }
        }
    }

    private static byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) >= 0) {
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static void closeQuietly(List<InputStream> streams, int from) {
        for (int i = streams.size() - 1; i >= from; i--) {
            InputStream stream = streams.get(i);
            if (stream == null) {
                continue;
            }
            try {
                stream.close();
            } catch (IOException ignored) {
                // The original failure is the one callers need.
            }
        }
    }

    private static EnhancementFailure scanFailure(String className, String detail, Throwable cause) {
        return new EnhancementFailure(
                EnhancementFailure.Category.SCAN,
                className,
                null,
                "scan",
                detail,
                cause instanceof Exception ? (Exception) cause : (cause == null ? null : new RuntimeException(cause)));
    }
}
