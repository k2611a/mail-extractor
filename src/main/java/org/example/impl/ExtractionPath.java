package org.example.impl;

import java.io.Closeable;
import java.util.ArrayDeque;
import java.util.Iterator;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class ExtractionPath {

    private static final Logger log = LogManager.getLogger(ExtractMail.class);

    private ArrayDeque<String> currentExtractionPath = new ArrayDeque<>();

    Closeable pushZip(String name) {
        currentExtractionPath.push("ZIP:" + name);
        log.info("PROCESSING: " + toSingleLine());
        return this::pop;
    }

    Closeable pushEml(String name) {
        currentExtractionPath.push("EML:" + name);
        log.info("PROCESSING: " + toSingleLine());
        return this::pop;
    }

    private String pop() {
        return currentExtractionPath.pop();
    }

    private String toSingleLine() {
        StringBuilder sb = new StringBuilder();
        Iterator<String> iterator = currentExtractionPath.descendingIterator();
        boolean first = true;
        while (iterator.hasNext()) {
            if (!first) {
                sb.append(" -> ");
            }
            first = false;
            sb.append(iterator.next());
        }
        return sb.toString();
    }
}
