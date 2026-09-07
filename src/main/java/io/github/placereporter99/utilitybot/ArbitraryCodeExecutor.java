package io.github.placereporter99.utilitybot;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ArbitraryCodeExecutor {
    private final int timeout;
    public ArbitraryCodeExecutor(int timeout) {
        this.timeout = timeout;
    }

    public String executeUntrustedCode(String code, String className, String[] args) {
        File sourceFile = new File(className + ".java");
        try (FileWriter writer = new FileWriter(sourceFile)) {
            writer.write(code);
        } catch (IOException e) {
            return Helpers.getFullMessage(e);
        }
        try {
            // 1. Compile and catch compiler errors in memory
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            int compileResult = compiler.run(null, outputStream, outputStream, sourceFile.getPath());

            String temp = outputStream.toString(StandardCharsets.UTF_8);

            String compileLogs = "Compile logs (" + (compileResult == 0 ? "success" : "error") + "):\n\n" + (temp.isEmpty() ? "<no logs>" : temp);

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
                    className
            );
            pb.command().addAll(List.of(args));
            pb.environment().clear();
            pb.redirectErrorStream(true);

            Process process = pb.start();
            boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);

            String logs;
            String finalLogs;
            if (!finished) {
                process.destroyForcibly();
                logs = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                finalLogs = "Process timed out after %d seconds, may still have logs:\n\n".formatted(timeout) + (logs.isEmpty() ? "<no logs>" : logs);
            } else {
                logs = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                finalLogs = logs.isEmpty() ? "Program has run:\n\n<no logs>" : "Program has run:\n\n" + logs;
            }

            return compileLogs + "\n---------------------------------------------------------------------------\n" + finalLogs;
        } catch (Exception e) {
            return "Issue with writing/compiling code and/or threads:\n\n" + Helpers.getFullMessage(e);
        } finally {
            // Ensure disk cleanup always runs
            try {
                Files.deleteIfExists(sourceFile.toPath());
                Files.deleteIfExists(new File(className + ".class").toPath());
            } catch (IOException e) {
                System.err.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                System.err.println("WARNING: Failed to delete generated code files. This may bloat memory.");
                e.printStackTrace();
                System.err.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            }
        }
    }

    public String executeUntrustedCode(String code, String[] args) {
        return executeUntrustedCode(code, "Main", args);
    }

    public String executeUntrustedCode(String code) {
        return executeUntrustedCode(code, new String[]{});
    }

    public String executeUntrustedCodeRaw(String code, String className, String[] args) {
        File sourceFile = new File(className + ".java");
        try (FileWriter writer = new FileWriter(sourceFile)) {
            writer.write(code);
        } catch (IOException e) {
            return Helpers.getFullMessage(e);
        }

        try {
            // 1. Compile and catch compiler errors in memory
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            int compileResult = compiler.run(null, outputStream, outputStream, sourceFile.getPath());

            String compileLogs = outputStream.toString(StandardCharsets.UTF_8);

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
                    className
            );
            pb.command().addAll(List.of(args));
            pb.environment().clear();
            pb.redirectErrorStream(true);

            Process process = pb.start();
            boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);

            var timedOut = false;
            if (!finished) {
                process.destroyForcibly();
                timedOut = true;
            }
            return (timedOut ? "Command timed out:\n\n" : "\n") + new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "Issue with writing/compiling code and/or threads:\n\n" + Helpers.getFullMessage(e);
        } finally {
            // Ensure disk cleanup always runs
            try {
                Files.deleteIfExists(sourceFile.toPath());
                Files.deleteIfExists(new File(className + ".class").toPath());
            } catch (IOException e) {
                System.err.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                System.err.println("WARNING: Failed to delete generated code files. This may bloat memory.");
                e.printStackTrace();
                System.err.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            }
        }
    }
}
