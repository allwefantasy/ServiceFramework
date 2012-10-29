package net.csdn.common.env;

import net.csdn.common.Classes;
import net.csdn.common.exception.FailedToResolveConfigException;
import net.csdn.common.io.Streams;
import net.csdn.common.settings.Settings;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;

import static net.csdn.common.Strings.cleanPath;
import static net.csdn.common.settings.ImmutableSettings.Builder.EMPTY_SETTINGS;

/**
 * BlogInfo: william
 * Date: 11-9-1
 * Time: 下午3:51
 */
public class Environment {

    private final File homeFile;

    private final File workFile;

    private final File workWithClusterFile;

    private final File dataFile;

    private final File dataWithClusterFile;

    private final File configFile;

    private final File pluginsFile;

    private final File logsFile;

    private final File dictionariesFile;

    private final File gatewayFile;

    private final File fingerprintFile;


    public Environment() {
        this(EMPTY_SETTINGS);
    }

    public Environment(Settings settings) {
        if (settings.get("path.home") != null) {
            homeFile = new File(cleanPath(settings.get("path.home")));
        } else {
            homeFile = new File(System.getProperty("user.dir"));
        }

        if (settings.get("path.conf") != null) {
            configFile = new File(cleanPath(settings.get("path.conf")));
        } else {
            configFile = new File(homeFile, "config");
        }

        if (settings.get("path.plugins") != null) {
            pluginsFile = new File(cleanPath(settings.get("path.plugins")));
        } else {
            pluginsFile = new File(homeFile, "plugins");
        }

        if (settings.get("path.work") != null) {
            workFile = new File(cleanPath(settings.get("path.work")));
        } else {
            workFile = new File(homeFile, "work");
        }
        workWithClusterFile = new File(workFile, settings.get("cluster.name", "csdnsearch"));

        if (settings.get("path.data") != null) {
            dataFile = new File(cleanPath(settings.get("path.data")));
        } else {
            dataFile = new File(homeFile, "data");
        }
        dataWithClusterFile = new File(dataFile, settings.get("cluster.name", "csdnsearch"));

        if (settings.get("path.logs") != null) {
            logsFile = new File(cleanPath(settings.get("path.logs")));
        } else {
            logsFile = new File(homeFile, "logs");
        }

        if (settings.get("path.dictionaries") != null) {
            dictionariesFile = new File(cleanPath(settings.get("path.dictionaries")));
        } else {
            dictionariesFile = new File(homeFile, "dictionaries");
        }
        if (settings.get("path.gateway") != null) {
            gatewayFile = new File(cleanPath(settings.get("path.gateway")));
        } else {
            gatewayFile = new File(homeFile, "gateway");
        }
        if (settings.get("path.fingerprintdic") != null) {
            fingerprintFile = new File(cleanPath(settings.get("path.fingerprintdic")));
        } else {
            fingerprintFile = new File(homeFile, "fingerprintdic");
        }
    }

    /**
     * The home of the installation.
     */
    public File homeFile() {
        return homeFile;
    }

    /**
     * The home of dictionaries
     */
    public File dictionariesFile() {
        return dictionariesFile;
    }

    /**
     * The work location.
     */
    public File workFile() {
        return workFile;
    }

    /**
     * The work location with the cluster name as a sub directory.
     */
    public File workWithClusterFile() {
        return workWithClusterFile;
    }

    /**
     * The data location.
     */
    public File dataFile() {
        return dataFile;
    }

    /**
     * The data location with the cluster name as a sub directory.
     */
    public File dataWithClusterFile() {
        return dataWithClusterFile;
    }

    /**
     * The config location.
     */
    public File configFile() {
        return configFile;
    }

    public File pluginsFile() {
        return pluginsFile;
    }

    public File logsFile() {
        return logsFile;
    }

    public File gateway() {
        return gatewayFile;
    }

    public File fingerprintFile() {
        return fingerprintFile;
    }

    public String resolveConfigAndLoadToString(String path) throws FailedToResolveConfigException, IOException {
        return Streams.copyToString(new InputStreamReader(resolveConfig(path).openStream(), "UTF-8"));
    }

    public URL resolveConfig(String path) throws FailedToResolveConfigException {
        // first, try it as a path on the file system
        File f1 = new File(path);
        if (f1.exists()) {
            try {
                return f1.toURI().toURL();
            } catch (MalformedURLException e) {
                throw new FailedToResolveConfigException("Failed to resolve path [" + f1 + "]", e);
            }
        }
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        // next, try it relative to the config location
        File f2 = new File(configFile, path);
        if (f2.exists()) {
            try {
                return f2.toURI().toURL();
            } catch (MalformedURLException e) {
                throw new FailedToResolveConfigException("Failed to resolve path [" + f2 + "]", e);
            }
        }
        // try and load it from the classpath directly
        URL resource = Classes.getDefaultClassLoader().getResource(path);
        if (resource != null) {
            return resource;
        }
        // try and load it from the classpath with config/ prefix
        if (!path.startsWith("config/")) {
            resource = Classes.getDefaultClassLoader().getResource("config/" + path);
            if (resource != null) {
                return resource;
            }
        }
        throw new FailedToResolveConfigException("Failed to resolve config path [" + path + "], tried file path [" + f1 + "], path file [" + f2 + "], and classpath");
    }
}
