package net.csdn.common.scan.component;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;


public class FileIterator implements StreamIterator {
    private ArrayList files;
    private int index = 0;

    public FileIterator(File file, Filter filter) {
        files = new ArrayList();
        try {
            create(files, file, filter);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected static void create(List list, File dir, Filter filter) throws Exception {
        create(list, dir, filter, dir.getCanonicalPath());
    }

    protected static void create(List list, File dir, Filter filter, String prefix) throws Exception {
        File[] files = dir.listFiles();
        for (int i = 0; i < files.length; i++) {
            if (files[i].isDirectory()) {
                create(list, files[i], filter, prefix);
            } else {
                String path = files[i].getCanonicalPath();
                String relativePath = path.substring(prefix.length() + 1);
                if (File.separatorChar == '\\')
                    relativePath = relativePath.replace('\\', '/');
                if (filter == null || filter.accepts(relativePath)) {
                    list.add(files[i]);
                }
            }
        }
    }

    public InputStream next() {
        if (index >= files.size()) return null;
        File fp = (File) files.get(index++);
        try {
            return new FileInputStream(fp);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public void close() {
    }
}
