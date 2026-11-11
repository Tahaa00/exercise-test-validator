package com.example.arraymax;

/** Demo for trying the class by hand. No test calls it, like the demo main of many exercises. */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        if (args.length > 0) {
            System.out.println("Arguments are ignored.");
        }
        int[] values = {3, 9, 1, 4};
        System.out.println("Largest value: " + ArrayMax.max(values));
    }
}
