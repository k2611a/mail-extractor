package org.example.impl;

import java.io.IOException;
import java.io.OutputStream;

public class LimitedStream extends OutputStream {

    private final OutputStream os;

    private final OutputSizeCounter outputSizeCounter;

    public LimitedStream(OutputStream os, OutputSizeCounter outputSizeCounter) {
        this.os = os;
        this.outputSizeCounter = outputSizeCounter;
    }

    public static OutputStream nullOutputStream() {
        return OutputStream.nullOutputStream();
    }

    @Override
    public void write(int b) throws IOException {
        outputSizeCounter.ensureSize(4);
        os.write(b);
    }

    @Override
    public void write(byte[] b) throws IOException {
        outputSizeCounter.ensureSize(b.length);
        os.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        outputSizeCounter.ensureSize(len);
        os.write(b, off, len);
    }


    @Override
    public void flush() throws IOException {
        os.flush();
    }

    @Override
    public void close() throws IOException {
        os.close();
    }
}
