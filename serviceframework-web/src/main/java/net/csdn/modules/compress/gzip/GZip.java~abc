package net.csdn.modules.compress.gzip;


import java.io.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * BlogInfo: william
 * Date: 12-4-5
 * Time: 下午12:10
 */
public class GZip {
    public static String decodeWithGZip(byte[] bytes) {
        try {
            GZIPInputStream gzipInputStream = new GZIPInputStream(new ByteArrayInputStream(bytes));
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(gzipInputStream));
            StringWriter stringWriter = new StringWriter();
            String s = null;

            while ((s = bufferedReader.readLine()) != null) {
                stringWriter.write(s);
            }
            bufferedReader.close();
            String result = stringWriter.toString();
            stringWriter.close();
            return result;
        } catch (IOException e) {
            e.printStackTrace();

        }
        return new String(bytes);
    }

    public static byte[] encodeWithGZip(String str) {

        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            GZIPOutputStream gzipOutputStream = new GZIPOutputStream(out);
            gzipOutputStream.write(str.getBytes("utf-8"));
            gzipOutputStream.close();
            return out.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return str.getBytes();
    }
}
