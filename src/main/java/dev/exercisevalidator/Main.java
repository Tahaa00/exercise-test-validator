package dev.exercisevalidator;

/**
 * Entry point. Step 1 only ships the sample exercises; the checks arrive in step 2.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        if (args.length != 2 || !"validate".equals(args[0])) {
            System.err.println("Usage: validate <exercise-folder>");
            System.exit(2);
        }
        System.out.println("Checks are not implemented yet (build step 2). Exercise: " + args[1]);
    }
}
