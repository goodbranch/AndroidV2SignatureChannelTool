package com.branch.v2.read.util;

public class Log {

    private static boolean debug = true;

    public static void log(String log) {
        if (!debug) {
            return;

        }
        System.out.println(log);
    }

}
