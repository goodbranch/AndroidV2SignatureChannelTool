package com.branchao.v2;

import com.branch.v2.JCommander;
import com.branch.v2.read.util.Log;

import java.util.Arrays;

public class Main {

    public static void main(String[] args) {
        // write your code here

        Log.log(Arrays.toString(args));

        if (args == null || args.length == 0) {

            return;
        }
//        Log.log(Arrays.toString(args));

        JCommander commander = new JCommander();
        commander.parseCommand(args);
        commander.doAction();

    }
}
