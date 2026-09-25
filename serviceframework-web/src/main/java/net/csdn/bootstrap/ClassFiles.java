package net.csdn.bootstrap;

import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.ClassFile;
import javassist.bytecode.annotation.Annotation;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Reads class-file names and runtime annotations without defining the class.
 */
public final class ClassFiles {

    private ClassFiles() {
    }

    public static byte[] read(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = input.read(buffer)) >= 0) {
            if (read > 0) {
                output.write(buffer, 0, read);
            }
        }
        return output.toByteArray();
    }

    public static ClassFile parse(byte[] bytes) throws IOException {
        DataInputStream input = new DataInputStream(new BufferedInputStream(new ByteArrayInputStream(bytes)));
        try {
            return new ClassFile(input);
        } finally {
            input.close();
        }
    }

    public static boolean hasAnnotation(ClassFile file, String annotation) {
        return hasAnnotation(file.getAttribute(AnnotationsAttribute.visibleTag), annotation);
    }

    public static boolean hasAnnotation(Object attribute, String annotation) {
        if (!(attribute instanceof AnnotationsAttribute)) {
            return false;
        }
        Annotation[] annotations = ((AnnotationsAttribute) attribute).getAnnotations();
        String slashed = annotation.replace('.', '/');
        for (int i = 0; i < annotations.length; i++) {
            String type = annotations[i].getTypeName();
            if (annotation.equals(type) || slashed.equals(type)) {
                return true;
            }
        }
        return false;
    }
}
