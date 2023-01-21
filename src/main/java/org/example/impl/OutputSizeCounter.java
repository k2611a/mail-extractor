package org.example.impl;

class OutputSizeCounter {
    private long sizeUsed = 0;
    private final long maxSize;

    public OutputSizeCounter(long maxSize) {
        this.maxSize = maxSize;
    }

    void ensureSize(int size) {
        this.sizeUsed+=size;
        if (sizeUsed >= maxSize) {
            throw new IllegalStateException("Total output size exceeded : " + maxSize);
        }

    }

}

