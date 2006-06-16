package com.sun.istack.test;

import java.io.OutputStream;
import java.io.IOException;

/**
 * {@link OutputStream} splitter.
 *
 * @author Kohsuke Kawaguchi
 */
public final class ForkOutputStream extends OutputStream {
    final OutputStream out1,out2;

    public ForkOutputStream(OutputStream out1,OutputStream out2) {
        this.out1 = out1;
        this.out2 = out2;
    }

    public void write(int b) throws IOException {
        out1.write(b);
        out2.write(b);
    }

    public void close() throws IOException {
        out1.close();
        out2.close();
    }

    public void flush() throws IOException {
        out1.flush();
        out2.flush();
    }

    public void write(byte b[]) throws IOException {
        out1.write(b);
        out2.write(b);
    }

    public void write(byte b[], int off, int len) throws IOException {
        out1.write(b,off,len);
        out2.write(b,off,len);
    }
}
