package net.csdn.common.scan;

import tech.mlsql.common.utils.reflect.ClassPath;

import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
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
    public List<InputStream> scanArchives(String packageName) throws IOException {
        return scanArchives(packageName);
    }

    @Override
    public List<Class> scanArchives(String packageName, LoadClassEnhanceCallBack loadClassEnhanceCallBack) throws IOException {
        Iterator<ClassPath.ClassInfo> classinfos = ClassPath.from(loader.getClassLoader()).getTopLevelClassesRecursive(packageName).iterator();
        List<URL> urlList = new ArrayList<URL>();
        while (classinfos.hasNext()) {
            ClassPath.ClassInfo info = classinfos.next();
            urlList.add(info.url());
        }
        URL[] res = new URL[urlList.size()];
        return scanClass(scanArchives(urlList.toArray(res)), loadClassEnhanceCallBack);
    }

    @Override
    public List<InputStream> scanArchives(URL... urls) throws IOException {


        List<InputStream> streamList = new ArrayList<InputStream>();
        for (URL url : urls) {
            String protocol = url.getProtocol();
            if (protocol == "file") {
                streamList.add(new FileInputStream(new File(url.getFile())));
            } else if (protocol == "jar") {
                streamList.add(loader.getResourceAsStream(url.getFile().split("\\.jar!")[1]));
            } else {
                throw new RuntimeException(url.toString() + " is not supported");
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
