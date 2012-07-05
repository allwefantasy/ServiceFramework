package net.csdn.common.collections;

import java.io.*;

/**
 * BlogInfo: william
 * Date: 12-4-17
 * Time: 下午1:23
 */
public class WowSerializable {

    public static void saveFile(String fileName, Object content) {

        try {
            FileOutputStream fileOutputStream = new FileOutputStream(new File(fileName));
            ObjectOutputStream outputStream = new ObjectOutputStream(fileOutputStream);
            outputStream.writeObject(content);
            outputStream.flush();
            outputStream.close();
            fileOutputStream.flush();
            fileOutputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static Object loadFile(String fileName) {
        Object obj = null;
        try {
            FileInputStream fileInputStream = new FileInputStream(new File(fileName));
            ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream);
            obj = objectInputStream.readObject();
            objectInputStream.close();
            fileInputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return obj;
    }
}
