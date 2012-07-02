package net.csdn.modules.scan;

import javassist.ClassPool;
import javassist.CtClass;
import net.csdn.bootstrap.Bootstrap;
import net.csdn.modules.scan.component.ClasspathUrlFinder;
import net.csdn.modules.scan.component.Filter;
import net.csdn.modules.scan.component.IteratorFactory;
import net.csdn.modules.scan.component.StreamIterator;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * User: WilliamZhu
 * Date: 12-6-4
 * Time: 下午2:04
 */
public class DefaultScanService implements ScanService {

    @Override
    public URL packagePath(String packageName) {
        URL class_file_base_url = ClasspathUrlFinder.findClassBase(DefaultScanService.class);
        try {
            return new URL("file:" + class_file_base_url.getPath() + packageName.replaceAll("\\.", "/") + "/");
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public List<String> classNames(String packageName) {
        URL class_file_base_url = ClasspathUrlFinder.findClassBase(DefaultScanService.class);
        File packageDir = new File(class_file_base_url.getPath() + packageName.replaceAll("\\.", "/"));
        List<String> classes = new ArrayList<String>();
        List<File> files = new ArrayList<File>();
        iterateDir(packageDir, files);
        for (File f : files) {
            String path = f.getPath();
            classes.add(path.substring(class_file_base_url.getPath().length(), path.length() - 6).replaceAll(File.separator, "."));
        }
        String obj = "";
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
        ClassPool cp = Bootstrap.classPool;
        try {
            try {
                return loadClassEnhanceCallBack.loaded(cp, dstream);
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
