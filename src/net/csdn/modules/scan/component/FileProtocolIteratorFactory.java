package net.csdn.modules.scan.component;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;


public class FileProtocolIteratorFactory implements DirectoryIteratorFactory {

    public StreamIterator create(URL url, Filter filter) throws IOException {
        // See http://weblogs.java.net/blog/2007/04/25/how-convert-javaneturl-javaiofile
        File f;
        try {
            f = new File(url.toURI());
        } catch (URISyntaxException e) {
            f = new File(url.getPath());
        }

        if (f.isDirectory()) {
            return new FileIterator(f, filter);
        } else {
            return new JarIterator(url.openStream(), filter);
        }
    }
}
