package io.github.placereporter99.utilitybot;

import java.io.PrintWriter;
import java.io.StringWriter;

sealed abstract public class Helpers permits Helpers.Dummy {
    private static final class Dummy extends Helpers {}
    public static String indentLinesByFourSpaces(String text) {
        return String.join("\n", text.lines().map("    "::concat).toList());
    }

    static public class AssertionException extends Exception {
        public static void softAssert(boolean condition) throws AssertionException {
            if (!condition) {
                throw new AssertionException();
            }
        }
    }

    public static String getFullMessage(Throwable ex) {
        var sw = new StringWriter();
        var pw = new PrintWriter(sw, true);
        ex.printStackTrace(pw);
        return sw.getBuffer().toString();
    }
}