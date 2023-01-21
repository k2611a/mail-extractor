package org.example.impl;

public class OutputFileNameGenerator {
    private int outputFileCounter = 0;

    public String generateNewOutputFileName() {
        outputFileCounter++;
        return "test" + outputFileCounter + ".eml";
    }
}
