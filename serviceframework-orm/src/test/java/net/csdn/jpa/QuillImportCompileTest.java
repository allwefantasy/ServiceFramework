package net.csdn.jpa;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * {@code QuillDB.ctx} is a method, so {@code import QuillDB.ctx._} is not a stable path.
 * The supported form is {@code val ctx = QuillDB.ctx} and then {@code import ctx._}.
 */
public class QuillImportCompileTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void stableCtxImportCompiles() throws Exception {
        File scalaLibrary = codeSource(Class.forName("scala.Option"));
        File scalaReflect = codeSource(Class.forName("scala.reflect.api.Mirror"));
        File scalaCompiler = codeSource(Class.forName("scala.tools.nsc.Main"));
        assertTrue(scalaCompiler.isFile());
        File source = folder.newFile("StableImport.scala");
        Files.write(source.toPath(), sourceText().getBytes(StandardCharsets.UTF_8));
        File out = folder.newFolder("classes");
        String compilerPath = scalaCompiler.getAbsolutePath() + File.pathSeparator + scalaLibrary.getAbsolutePath()
                + File.pathSeparator + scalaReflect.getAbsolutePath();
        List<String> command = new ArrayList<String>();
        command.add(new File(System.getProperty("java.home"), "bin/java").getAbsolutePath());
        command.add("-cp");
        command.add(compilerPath);
        command.add("scala.tools.nsc.Main");
        command.add("-classpath");
        command.add(System.getProperty("java.class.path"));
        command.add("-d");
        command.add(out.getAbsolutePath());
        command.add("-release");
        command.add("8");
        command.add(source.getAbsolutePath());
        File log = folder.newFile("scalac.log");
        int exit = run(command, log);
        if (exit != 0) {
            command.remove("-release");
            command.remove("8");
            command.add(command.size() - 1, "-target:8");
            log = folder.newFile("scalac-target.log");
            exit = run(command, log);
        }
        String output = new String(Files.readAllBytes(log.toPath()), StandardCharsets.UTF_8);
        assertEquals(output, 0, exit);
        assertTrue(new File(out, "net/csdn/jpa/quillcheck/StableImport.class").isFile());
    }

    private static String sourceText() {
        return "package net.csdn.jpa.quillcheck\n"
                + "case class Probe(id: Int)\n"
                + "object StableImport {\n"
                + "  val ctx = net.csdn.jpa.QuillDB.ctx\n"
                + "  import ctx._\n"
                + "  val listed = quote { query[Probe].map(_.id) }\n"
                + "}\n";
    }

    private static int run(List<String> command, File log) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        byte[] output = readAll(process.getInputStream());
        int exit = process.waitFor();
        Files.write(log.toPath(), output);
        return exit;
    }

    private static byte[] readAll(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = input.read(buffer)) >= 0) {
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static File codeSource(Class<?> type) {
        URL url = type.getProtectionDomain().getCodeSource().getLocation();
        if (url == null) {
            throw new IllegalStateException("no code source for " + type.getName());
        }
        return new File(url.getPath());
    }
}
