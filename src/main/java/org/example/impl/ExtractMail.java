package org.example.impl;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.FileType;

import picocli.CommandLine;

@CommandLine.Command(
        name = "extract",
        mixinStandardHelpOptions = true,
        description = "Extract all the emails from the provided file"
)
public class ExtractMail implements Callable<Integer> {

    private static final Logger log = LogManager.getLogger(ExtractMail.class);

    @CommandLine.Parameters(index = "0", description = "The file whose content to extract.")
    private File inputFile;

    @CommandLine.Option(names = {"-f", "--filetype"}, description = "Type of the file to extract", required = true)
    private FileType fileType;

    @CommandLine.Option(names = {"-o", "--output"}, description = "Output path to extract files to", required = false)
    private Path outputPath = Paths.get("./output");

    @CommandLine.Option(names = {"-b", "--buffer"}, description = "Size of the buffers allocated when reading/writing files", required = false)
    private int bufferSize = 8192;

    @CommandLine.Option(names = {"-l", "--limit"}, description = "Maximum number of bytes to write", required = false)
    private long maximumOutputSizeBytes = FileUtils.ONE_GB;




    @Override
    public Integer call() throws Exception {
        if (!inputFile.exists()) {
            log.error("File not exists : " + inputFile);
            return -1;
        }

        FileProcessor fileProcessor = new FileProcessor(
                outputPath,
                bufferSize,
                maximumOutputSizeBytes
        );

        switch (fileType) {
            case ZIP -> fileProcessor.processZipFile(inputFile);
            case EML -> fileProcessor.processEmlFile(inputFile);
            default -> throw new IllegalArgumentException("Unsupported file type : " + fileType);
        }

        return 0;
    }


}
