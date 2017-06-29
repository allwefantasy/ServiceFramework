package net.csdn.common.scan.component;

import java.io.InputStream;


public interface StreamIterator {
    InputStream next();

    void close();
}
