package com.branch.v2.read.util;

public class StringUtil {


    public static boolean isEmpty(String str) {

        if (str == null || str.length() == 1) {
            return true;
        }

        return false;
    }

}
