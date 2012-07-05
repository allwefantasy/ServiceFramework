package net.csdn.modules.scan;

import javassist.ClassPool;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;

/**
 * BlogInfo: WilliamZhu
 * Date: 12-6-4
 * Time: 下午3:11
 */
public interface ScanService {
    URL packagePath(String packageName);

    List<InputStream> scanArchives(URL... urls) throws IOException;

    List<InputStream> scanArchives(String packageName) throws IOException;

    Class scanClass(InputStream bits, LoadClassEnhanceCallBack loadClassEnhanceCallBack) throws IOException;

    List<Class> scanArchives(String packageName, LoadClassEnhanceCallBack loadClassEnhanceCallBack) throws IOException;

    List<Class> scanClass(List<InputStream> inputStreams, LoadClassEnhanceCallBack loadClassEnhanceCallBack) throws IOException;

    public List<String> classNames(String packageName);

    public interface LoadClassEnhanceCallBack {
        public Class loaded(ClassPool classPool,DataInputStream classFile);
    }
}
