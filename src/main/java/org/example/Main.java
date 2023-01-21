package org.example;

import org.example.impl.ExtractMail;

import picocli.CommandLine;

public class Main {
    public static void main(String[] args) {
        int exitCode = new CommandLine(new ExtractMail()).execute(args);
        System.exit(exitCode);
    }

}