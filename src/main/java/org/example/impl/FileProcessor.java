package org.example.impl;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.mail.BodyPart;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.FileType;

class FileProcessor {

    private static final Logger log = LogManager.getLogger(FileProcessor.class);

    private final Path outputPath;

    private final OutputFileNameGenerator outputFileNameGenerator;

    private final BufferedStreamFactory bufferedStreamFactory;

    public FileProcessor(
            Path outputPath,
            int bufferSize,
            long maximumOutputSize
    ) {
        this.outputFileNameGenerator = new OutputFileNameGenerator();
        this.bufferedStreamFactory = new BufferedStreamFactory(bufferSize, maximumOutputSize);
        this.outputPath = outputPath;
    }

    public void process(
            File inputFile,
            ArrayDeque<FileType> fileTypePath
    ) throws IOException, MessagingException {

        log.debug("Starting mail extraction from : " + inputFile + " to : " + outputPath);
        log.debug("File format is : " + fileTypePath);

        initialCleanup();

        final String fileName = inputFile.getName();
        final ExtractionPath extractionPath = new ExtractionPath();

        processInputStreamBasedOnType(inputFile, fileTypePath, fileName, extractionPath);

        log.debug("Processing finished");
    }

    private void processInputStreamBasedOnType(
            File inputFile,
            ArrayDeque<FileType> fileTypePath,
            String fileName,
            ExtractionPath extractionPath
    ) throws IOException, MessagingException {
        try (InputStream inputStream = bufferedStreamFactory.readFile(inputFile)) {
            processInputStreamBasedOnType(
                    inputStream,
                    fileTypePath,
                    fileName,
                    extractionPath
            );
        }
    }

    private void processInputStreamBasedOnType(
            InputStream inputStream,
            ArrayDeque<FileType> fileTypePath,
            String fileName,
            ExtractionPath extractionPath
    ) throws IOException, MessagingException {
        FileType currentFileType = fileTypePath.pollFirst();
        try {
            switch (currentFileType) {
                case ZIP -> processZipInputStream(fileName, inputStream, extractionPath, fileTypePath);
                case EML -> processEmlInputStream(fileName, inputStream, extractionPath, fileTypePath);
                default -> throw new IllegalArgumentException("Unsupported file type : " + currentFileType);
            }
        } finally {
            fileTypePath.addFirst(currentFileType);
        }

    }

    private void initialCleanup() throws IOException {
        Files.createDirectories(outputPath);
        log.info("Created directory : " + outputPath);
        FileUtils.cleanDirectory(outputPath.toFile());
    }

    private void processZipInputStream(
            String fileName,
            InputStream inputStream,
            ExtractionPath extractionPath,
            ArrayDeque<FileType> fileTypePath
    ) throws IOException {
        assert (fileTypePath.size() > 0);
        log.debug("processZipInputStream. fileTypePath : " + fileTypePath);
        try (
                Closeable noop = extractionPath.pushZip(fileName);
        ) {
            ZipInputStream zipInputStream = new ZipInputStream(inputStream);
            ZipEntry zipEntry = null;
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                try {
                    if (zipEntry.isDirectory()) {
                        // do not process nested directories
                        log.debug("Skipping nested directory : " + zipEntry.getName());
                        return;
                    }

                    String zipEntryName = zipEntry.getName();

                    processInputStreamBasedOnType(
                            zipInputStream,
                            fileTypePath,
                            zipEntryName,
                            extractionPath
                    );

                } catch (IOException e) {
                    log.error("Exception while reading zip file", e);
                } catch (MessagingException e) {
                    log.error("Exception while reading zip file", e);
                }
            }
        }
    }

    private void processEmlInputStream(String fileName, InputStream inputStream, ExtractionPath extractionPath, ArrayDeque<FileType> fileTypePath) throws MessagingException, IOException {
        log.debug("processEmlInputStream. fileTypePath : " + fileTypePath);
        try (
                Closeable noop = extractionPath.pushEml(fileName);
        ) {

            if (fileTypePath.isEmpty()) {
                // last level of extraction, write email to output
                writeOutputEml(inputStream);
            } else {
                Properties props = new Properties();
                Session mailSession = Session.getDefaultInstance(props, null);
                MimeMessage message = new MimeMessage(
                        mailSession,
                        inputStream
                );
                processMessageBodyForAttachments(
                        message,
                        extractionPath,
                        fileTypePath
                );
            }


        }
    }

    private void processMessageBodyForAttachments(
            MimeMessage message,
            ExtractionPath extractionPath,
            ArrayDeque<FileType> fileTypePath
    ) throws MessagingException, IOException {
        assert (fileTypePath.size() > 0);
        if (message.isMimeType("multipart/*") && message.getContent() instanceof Multipart multipart) {
            // process multipart message
            log.debug("Multipart in mail, part count : " + multipart.getCount());
            List<BodyPart> messageBodyParts = MessageUtils.toBodyParts(multipart);
            FileType currentFileType = fileTypePath.pollFirst();

            try {
                switch (currentFileType) {
                    case ZIP -> {
                        for (BodyPart bodyPart : messageBodyParts) {
                            if (MessageUtils.isZip(bodyPart)) {
                                try (InputStream inputStream = bodyPart.getInputStream()) {
                                    processZipInputStream(
                                            bodyPart.getFileName(),
                                            inputStream,
                                            extractionPath,
                                            fileTypePath
                                    );
                                }
                            }
                        }
                    }
                    case EML -> {
                        for (BodyPart bodyPart : messageBodyParts) {
                            if (MessageUtils.isMessage(bodyPart)) {
                                try (InputStream inputStream = bodyPart.getInputStream()) {
                                    processEmlInputStream(
                                            bodyPart.getFileName(),
                                            inputStream,
                                            extractionPath,
                                            fileTypePath
                                    );
                                }
                            }
                        }
                    }
                    default -> throw new IllegalArgumentException("Unsupported file type : " + currentFileType);
                }
            } finally {
                fileTypePath.addFirst(currentFileType);
            }


        } else if (message.isMimeType("text/plain") && message.getContent() instanceof String) {
            // do nothing, text content is not attachment
        } else if (message.isMimeType("text/rfc822") && message.getContent() instanceof String) {
            // email is not attachment
        } else {
            log.warn("Content type unknown : " + message.getContentType());
        }

    }

    private void writeOutputEml(InputStream inputStream) throws IOException {
        String outputFileName = outputFileNameGenerator.generateNewOutputFileName();
        File emlOutputFile = new File(outputPath.toFile(), outputFileName);
        log.info("WRITING : " + emlOutputFile.getAbsolutePath());
        try (OutputStream out = bufferedStreamFactory.writeFile(emlOutputFile)) {
            IOUtils.copy(inputStream, out);
            IOUtils.closeQuietly(out);
        }

    }


}
