package org.example.impl;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class BufferedStreamFactory {

    private final int bufferSize;

    private final OutputSizeCounter outputSizeCounter;

    public BufferedStreamFactory(
            int bufferSize,
            long maximumOutputSize
    ) {
        this.bufferSize = bufferSize;
        this.outputSizeCounter = new OutputSizeCounter(maximumOutputSize);
    }

    public InputStream readFile(File file) throws FileNotFoundException {
        FileInputStream fileInputStream = new FileInputStream(file);
        return new BufferedInputStream(fileInputStream, bufferSize);
    }

    public OutputStream writeFile(File file) throws FileNotFoundException {
        FileOutputStream fileOutputStream = new FileOutputStream(file);
        BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(fileOutputStream);
        return wrapWithLimit(bufferedOutputStream);

    }

    private OutputStream wrapWithLimit(BufferedOutputStream bufferedOutputStream) {
        return new LimitedStream(bufferedOutputStream, outputSizeCounter);
    }

}
