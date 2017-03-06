package com.mycompany;

/**
 * Hello world!
 */
public class App {
    public String getText() {
        return "hello";
    }

    public int getResult(boolean even) {
        if (even) {
            return 2;
        } else {
            return 1;
        }
    }

    public boolean isOk() {
        return false;
    }

    public static class Util {
        public static int calc(int a, int b) {
            return a + b;
        }
    }
}
