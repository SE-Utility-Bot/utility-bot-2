package io.github.placereporter99.utilitybot;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ArbitraryCodeExecutor {
    private final int timeout;
    public ArbitraryCodeExecutor(int timeout) {
        this.timeout = timeout;
    }

    private static String indentLinesByFourSpaces(String text) {
        return String.join("\n", text.lines().map("    "::concat).toList());
    }

    public String executeUntrustedCode(String code) {
        File sourceFile = new File("UntrustedCode.java");
        try (FileWriter writer = new FileWriter(sourceFile)) {
            writer.write(code);
        } catch (IOException e) {
            return "    " + e.getMessage() + "\n" + indentLinesByFourSpaces(Arrays.stream(e.getStackTrace()).map(StackTraceElement::toString).collect(Collectors.joining("\n")));
        }
        try {
            // 1. Compile and catch compiler errors in memory
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            int compileResult = compiler.run(null, outputStream, outputStream, sourceFile.getPath());

            String temp = outputStream.toString(StandardCharsets.UTF_8);

            String compileLogs = "    Compile logs (" + (compileResult == 0 ? "success" : "error") + ") :\n\n" + (temp.isEmpty() ? "<no logs>" : indentLinesByFourSpaces(temp));

            if (compileResult != 0) {
                return compileLogs;
            }

            // 2. Execute process with isolated environment
            ProcessBuilder pb = new ProcessBuilder(
                    "java",
                    "-Xmx32m",                      // Limit user data/heap to 32MB
                    "-Xss256k",                     // Shrink thread stack sizes
                    "-XX:MaxMetaspaceSize=24m",     // Cap class definition memory
                    "-XX:ReservedCodeCacheSize=16m",// Stop the JIT compiler from reserving huge RAM blocks
                    "-XX:+UseSerialGC",             // Use the most memory-efficient garbage collector
                    "UntrustedCode"
            );
            pb.environment().clear();
            pb.redirectErrorStream(true);

            Process process = pb.start();
            boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);

            String logs;
            String finalLogs;
            if (!finished) {
                process.destroyForcibly();
                logs = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                finalLogs = "    Process timed out after %d seconds, may still have logs:\n\n".formatted(timeout) + (logs.isEmpty() ? "<no logs>" : indentLinesByFourSpaces(logs));
            } else {
                logs = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                finalLogs = logs.isEmpty() ? "    Program has run:\n\n<no logs>" : "    Program has run:\n\n" + indentLinesByFourSpaces(logs);
            }

            return compileLogs + "\n++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++\n" + finalLogs;
        } catch (Exception e) {
            return "    Issue with writing/compiling code and/or threads:\n\n    " + e.getMessage() + "\n" + indentLinesByFourSpaces(Arrays.stream(e.getStackTrace()).map(StackTraceElement::toString).collect(Collectors.joining("\n")));
        } finally {
            // Ensure disk cleanup always runs
            try {
                Files.deleteIfExists(sourceFile.toPath());
                Files.deleteIfExists(new File("UntrustedCode.class").toPath());
            } catch (IOException e) {
                System.err.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                System.err.println("WARNING: Failed to delete generated code files. This may bloat memory.");
                e.printStackTrace();
                System.err.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            }
        }
    }
}
