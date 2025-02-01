package com.maxwai.nclientv3.utility;

import android.util.Log;

import java.util.Arrays;
import java.util.function.BiConsumer;

public class LogUtility {

    public static final String LOGTAG = "NCLIENTLOG";

    public static void d(Object... message) {
        common(Log::d, message);
    }

    public static void d(Object message, Throwable throwable) {
        common(Log::d, message, throwable);
    }

    public static void i(Object... message) {
        common(Log::i, message);
    }

    public static void i(Object message, Throwable throwable) {
        common(Log::i, message, throwable);
    }

    public static void w(Object... message) {
        common(Log::w, message);
    }

    public static void w(Object message, Throwable throwable) {
        common(Log::w, message, throwable);
    }

    public static void e(Object... message) {
        common(Log::e, message);
    }

    public static void e(Object message, Throwable throwable) {
        common(Log::e, message, throwable);
    }

    public static void wtf(Object... message) {
        common(Log::wtf, message);
    }

    public static void wtf(Object message, Throwable throwable) {
        common(Log::wtf, message, throwable);
    }

    private static void common(BiConsumer<String, String> logCall, Object... message) {
        if (message == null) return;
        if (message.length == 1) logCall.accept(LogUtility.LOGTAG, "" + message[0]);
        else logCall.accept(LogUtility.LOGTAG, Arrays.toString(message));
    }

    private static void common(TriConsumer logCall, Object message, Throwable throwable) {
        if (message == null) message = "";
        logCall.accept(LogUtility.LOGTAG, message.toString(), throwable);
    }

    @FunctionalInterface
    private interface TriConsumer {
        void accept(String tag, String msg, Throwable tr);
    }
}
