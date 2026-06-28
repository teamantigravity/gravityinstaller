package com.example.installer;

import java.lang.reflect.Method;
import rikka.shizuku.Shizuku;

public class ShizukuRunner {
    public static java.lang.Process newProcess(String[] cmd, String[] env, String dir) {
        try {
            Method m = Shizuku.class.getDeclaredMethod("newProcess", String[].class, String[].class, String.class);
            m.setAccessible(true);
            return (java.lang.Process) m.invoke(null, cmd, env, dir);
        } catch (Exception e) {
            throw new RuntimeException("Failed to run Shizuku process", e);
        }
    }
}
