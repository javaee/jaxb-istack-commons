package com.sun.istack.soimp;

/**
 * @author Kohsuke Kawaguchi
 */
public interface Listener {
    void info(String line);

    public static final Listener CONSOLE = new Listener() {
        public void info(String line) {
            System.out.println(line);
        }
    };
}
