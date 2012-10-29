package net.csdn.common.scan;

import com.mysql.jdbc.StringUtils;
import net.csdn.common.scan.component.ClasspathUrlFinder;
import net.csdn.common.scan.component.Filter;
import net.csdn.common.scan.component.IteratorFactory;
import net.csdn.common.scan.component.StreamIterator;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * BlogInfo: WilliamZhu
 * Date: 12-6-4
 * Time: 下午2:04
 */
public class DefaultScanService implements ScanService {


    private Class loader = DefaultScanService.class;

    public Class getLoader() {
        return loader;
    }

    public void setLoader(Class loader) {
        this.loader = loader;
    }

    @Override
    public URL packagePath(String packageName) {
        URL class_file_base_url = ClasspathUrlFinder.findClassBase(loader);
        try {
            return new URL("file:" + class_file_base_url.getPath() + packageName.replaceAll("\\.", "/") + "/");
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public List<String> classNames(String packageName) {
        return classNames(packageName, DefaultScanService.class);
    }

    @Override
    public List<String> classNames(String packageName, Class baseClass) {
        URL class_file_base_url = ClasspathUrlFinder.findClassBase(baseClass);
        String packageNameWithDot = packageName.replace(".", File.separator);
        File packageDir = new File(class_file_base_url.getPath() + packageNameWithDot);
        List<String> classes = new ArrayList<String>();
        List<File> files = new ArrayList<File>();
        iterateDir(packageDir, files);
        for (File f : files) {
            String path = f.getPath();
            int pos = StringUtils.indexOfIgnoreCase(path, packageNameWithDot);
            if (pos == -1) pos = 0;
            path = path.substring(pos, path.length() - 6).replace(File.separator, ".");
            classes.add(path);
        }
        return classes;
    }

    private void iterateDir(File file, List<File> files) {
        if (file.isDirectory()) {
            for (File f : file.listFiles()) {
                iterateDir(f, files);
            }
        } else {
            files.add(file);
        }

    }

    @Override
    public List<InputStream> scanArchives(String packageName) throws IOException {
        return scanArchives(packagePath(packageName));
    }

    @Override
    public List<Class> scanArchives(String packageName, LoadClassEnhanceCallBack loadClassEnhanceCallBack) throws IOException {
        return scanClass(scanArchives(packagePath(packageName)), loadClassEnhanceCallBack);
    }

    @Override
    public List<InputStream> scanArchives(URL... urls) throws IOException {
        List<InputStream> streamList = new ArrayList<InputStream>();
        for (URL url : urls) {
            Filter filter = new Filter() {
                public boolean accepts(String filename) {
                    if (filename.endsWith(".class")) {
                        return true;

                    }
                    return false;
                }
            };

            try {
                StreamIterator it = IteratorFactory.create(url, filter);

                InputStream stream;
                while ((stream = it.next()) != null) streamList.add(stream);
            } catch (IOException e) {
                throw e;
            }
        }
        return streamList;

    }


    @Override
    public Class scanClass(InputStream bits, LoadClassEnhanceCallBack loadClassEnhanceCallBack) throws IOException {
        DataInputStream dstream = new DataInputStream(new BufferedInputStream(bits));

        try {
            try {
                return loadClassEnhanceCallBack.loaded(dstream);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        } finally {
            dstream.close();
            bits.close();
        }
    }

    @Override
    public List<Class> scanClass(List<InputStream> inputStreams, LoadClassEnhanceCallBack loadClassEnhanceCallBack) throws IOException {
        List<Class> classList = new ArrayList<Class>();
        for (InputStream inputStream : inputStreams) {
            classList.add(scanClass(inputStream, loadClassEnhanceCallBack));
        }
        return classList;

    }

}
