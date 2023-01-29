package org.example;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ArrayUtils;
import org.example.impl.ExtractMail;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FunctionalTests {

    @Test
    public void providedExample() throws IOException {
        runTest(
                "provided-example",
                "archive.zip",
                Arrays.asList(FileType.ZIP, FileType.EML),
                false
        );
    }

    @Test
    public void providedExampleSizeLimitNotEnough() throws IOException {
        runTest(
                "provided-example",
                "archive.zip",
                Arrays.asList(FileType.ZIP, FileType.EML),
                true,
                "-l", "500"
        );
    }

    @Test
    public void providedExampleSizeLimitEnough() throws IOException {
        runTest(
                "provided-example",
                "archive.zip",
                Arrays.asList(FileType.ZIP, FileType.EML),
                false,
                "-l", "8000"
        );
    }

    @Test
    public void empty() throws IOException {
        runTest(
                "empty",
                "archive.zip",
                Arrays.asList(FileType.ZIP, FileType.EML),
                false
        );
    }

    @Test
    public void fileNotExistsArchive() throws IOException {
        runTest(
                "not-exists",
                "archive.zip",
                Arrays.asList(FileType.ZIP, FileType.EML),
                true
        );
    }

    @Test
    public void fileNotExistsEmail() throws IOException {
        runTest(
                "not-exists",
                "message.eml",
                Arrays.asList(FileType.EML, FileType.EML),
                true
        );
    }

    @Test
    public void nestedZip() throws IOException {
        runTest(
                "nested-zip",
                "archive.zip",
                Arrays.asList(FileType.ZIP, FileType.ZIP, FileType.EML, FileType.ZIP, FileType.EML, FileType.EML),
                false
        );
    }

    @Test
    public void emailWithMessages() throws IOException {
        runTest(
                "email-with-messages",
                "message.eml",
                Arrays.asList(FileType.EML, FileType.ZIP, FileType.EML),
                false
        );
    }

    @Test
    public void nestedDirectory() throws IOException {
        runTest(
                "nested-directory",
                "archive.zip",
                Arrays.asList(FileType.ZIP, FileType.EML),
                false
        );
    }

    private void runTest(
            String testcasePath,
            String filename,
            List<FileType> fileType,
            boolean expectFailure,
            String... additonalArgs
    ) throws IOException {
        String fullTestcasePath = "./src/test/resources/testcases/" + testcasePath;
        String fullFilename = fullTestcasePath + "/input/" + filename;
        String fullExpectedOutputPath = fullTestcasePath + "/output";

        String outputPath = "./build/tmp/testcases/" + testcasePath + "/output";

        String[] args = {
                fullFilename,
                "-f", toPath(fileType),
                "-o", outputPath
        };
        if (additonalArgs != null) {
            args = ArrayUtils.addAll(args, additonalArgs);
        }
        int resultCode = new CommandLine(new ExtractMail()).execute(args);

        if (expectFailure) {
            assertNotEquals(0, resultCode);
        } else {
            assertEquals(0, resultCode);
            assertEqualDirectContent(
                    fullExpectedOutputPath,
                    outputPath
            );
        }
    }

    private String toPath(List<FileType> fileType) {
        return fileType
                .stream()
                .map(Object::toString)
                .collect(Collectors.joining(","));

    }

    private void assertEqualDirectContent(String expected, String actual) throws IOException {
        File expectedDirectory = new File(expected);
        File actualDirectory = new File(actual);

        assertTrue(
                actualDirectory.isDirectory(),
                "actual directory is directory : " + actualDirectory.getAbsolutePath()
        );

        assertTrue(
                expectedDirectory.isDirectory(),
                "expected directory is directory : " + expectedDirectory.getAbsolutePath()
        );

        File[] expectedDirectoryFiles = expectedDirectory.listFiles();
        File[] actualDirectoryfiles = actualDirectory.listFiles();
        assertEquals(
                expectedDirectoryFiles.length,
                actualDirectoryfiles.length,
                "directories should contain same number of files"
        );


        Map<String, File> expectedFilesByName = new HashMap<>();
        for (File expectedFile : expectedDirectoryFiles) {
            expectedFilesByName.put(expectedFile.getName(), expectedFile);
        }

        for (File actualFile : actualDirectoryfiles) {
            if (expectedFilesByName.containsKey(actualFile.getName())) {
                File expectedFile = expectedFilesByName.get(actualFile.getName());
                assertThat(actualFile).hasSameTextualContentAs(expectedFile);
            } else {
                Assertions.fail("Actual file not found in expected files : " + actualFile.getName());
            }
        }
    }

}
