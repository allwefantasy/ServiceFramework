package net.csdn.remotenotes;

import net.csdn.jpa.model.Model;
import net.csdn.modules.http.HttpServer;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.CodeSource;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Identity of the framework jars actually loaded by this process.
 * The publish script writes {@code META-INF/sf-framework-build.txt} into each
 * framework jar. An application-only publish leaves those jars in place.
 */
public final class FrameworkBuild {
    private FrameworkBuild() {
    }

    public static Map<String, Object> identity() {
        Map<String, Object> described = new LinkedHashMap<String, Object>();
        described.put("app", jarOf(AppVersion.class));
        described.put("orm", jarOf(Model.class));
        described.put("web", jarOf(HttpServer.class));
        return described;
    }

    public static String releaseId() {
        File file = new File("release-id");
        if (!file.isFile()) {
            return "";
        }
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(new java.io.FileInputStream(file), "UTF-8"));
            try {
                String line = reader.readLine();
                return line == null ? "" : line.trim();
            } finally {
                reader.close();
            }
        } catch (Exception ex) {
            return "";
        }
    }

    private static Map<String, String> jarOf(Class<?> type) {
        Map<String, String> described = new LinkedHashMap<String, String>();
        described.put("className", type.getName());
        File file = sourceFile(type);
        if (file == null) {
            described.put("file", "");
            described.put("sha256", "");
            described.put("buildId", "");
            return described;
        }
        described.put("file", file.getName());
        described.put("sha256", sha256(file));
        described.put("buildId", buildId(file));
        return described;
    }

    private static File sourceFile(Class<?> type) {
        CodeSource source = type.getProtectionDomain().getCodeSource();
        if (source == null) {
            return null;
        }
        URL location = source.getLocation();
        if (location == null) {
            return null;
        }
        try {
            return new File(location.toURI());
        } catch (URISyntaxException ex) {
            return new File(location.getPath());
        }
    }

    private static String buildId(File file) {
        if (!file.isFile()) {
            return "";
        }
        JarFile jar = null;
        try {
            jar = new JarFile(file);
            JarEntry entry = jar.getJarEntry("META-INF/sf-framework-build.txt");
            if (entry == null) {
                return "";
            }
            InputStream input = jar.getInputStream(entry);
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8"));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("buildId=")) {
                        return line.substring("buildId=".length()).trim();
                    }
                }
                return "";
            } finally {
                input.close();
            }
        } catch (Exception ex) {
            return "";
        } finally {
            if (jar != null) {
                try {
                    jar.close();
                } catch (Exception ignored) {
                    // The identity fields stay empty.
                }
            }
        }
    }

    private static String sha256(File file) {
        if (!file.isFile()) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            InputStream input = new java.io.FileInputStream(file);
            try {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read > 0) {
                        digest.update(buffer, 0, read);
                    }
                }
            } finally {
                input.close();
            }
            byte[] hash = digest.digest();
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (int i = 0; i < hash.length; i++) {
                hex.append(Character.forDigit((hash[i] >> 4) & 0xF, 16));
                hex.append(Character.forDigit(hash[i] & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception ex) {
            return "";
        }
    }
}
