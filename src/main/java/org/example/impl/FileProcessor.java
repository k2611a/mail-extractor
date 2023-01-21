package org.example.impl;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.mail.BodyPart;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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

    public void processEmlFile(File empInputFile) throws IOException, MessagingException {
        initialCleanup();
        log.debug("Starting mail extraction from email : " + empInputFile + " to : " + outputPath);
        try (InputStream inputStream = bufferedStreamFactory.readFile(empInputFile)) {
            processEmlInputStream(
                    empInputFile.getName(),
                    inputStream,
                    new ExtractionPath()
            );
        }

        log.debug("Processing finished");
    }

    public void processZipFile(File zipInputFile) throws IOException {
        initialCleanup();
        log.debug("Starting mail extraction from archive : " + zipInputFile + " to : " + outputPath);
        try (InputStream inputStream = bufferedStreamFactory.readFile(zipInputFile)) {
            processZipInputStream(
                    zipInputFile.getName(),
                    inputStream,
                    new ExtractionPath()
            );
        }
        log.debug("Processing finished");
    }

    private void initialCleanup() throws IOException {
        Files.createDirectories(outputPath);
        FileUtils.cleanDirectory(outputPath.toFile());
    }

    private void processZipInputStream(String fileName, InputStream inputStream, ExtractionPath extractionPath) throws IOException {
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
                    if (isZipFilename(zipEntryName)) {
                        log.debug("Processing zip file : " + zipEntryName);
                        processZipInputStream(zipEntryName, zipInputStream, extractionPath);
                    }
                    if (isEmlFilename(zipEntryName)) {
                        log.debug("Processing eml file : " + zipEntryName);
                        processEmlInputStream(zipEntryName, zipInputStream, extractionPath);
                    }
                } catch (IOException e) {
                    log.error("Exception while reading zip file", e);
                } catch (MessagingException e) {
                    log.error("Exception while reading zip file", e);
                }
            }
        }
    }


    private void processEmlInputStream(String fileName, InputStream inputStream, ExtractionPath extractionPath) throws MessagingException, IOException {
        try (
                Closeable noop = extractionPath.pushEml(fileName);
        ) {
            Properties props = new Properties();
            Session mailSession = Session.getDefaultInstance(props, null);
            MimeMessage message = new MimeMessage(
                    mailSession,
                    inputStream
            );

            if (message.isMimeType("multipart/*") && message.getContent() instanceof Multipart multipart) {
                // process multipart message
                log.debug("Multipart in mail, part count : " + multipart.getCount());

                List<BodyPart> messageBodyPartsLeft = processMessageBody(extractionPath, multipart);

                if (!messageBodyPartsLeft.isEmpty()) {
                    // reconstruct the message without processed inner messages
                    MimeMultipart multiPartLeftovers = MessageUtils.toMimeMultipart(messageBodyPartsLeft);
                    message.setContent(multiPartLeftovers);
                    writeOutputEml(message);
                } else {
                    log.debug("Message contains only nested content, skipping output");
                }
            } else if (message.isMimeType("text/plain") && message.getContent() instanceof String) {
                // do nothing, text content stays as it is
                writeOutputEml(message);
            } else if (message.isMimeType("text/rfc822") && message.getContent() instanceof String) {
                // go deeper into message and its attachments
                try (InputStream nestedInputStream = message.getInputStream()) {
                    processEmlInputStream(message.getFileName(), nestedInputStream, extractionPath);
                }
            } else {
                log.warn("Content type unknown : " + message.getContentType());
                writeOutputEml(message);
            }
        }
    }

    private List<BodyPart> processMessageBody(ExtractionPath extractionPath, Multipart multipart) throws MessagingException, IOException {
        List<BodyPart> messageBodyParts = MessageUtils.toBodyParts(multipart);
        List<BodyPart> messageBodyPartsLeft = new ArrayList<>(multipart.getCount());
        for (BodyPart bodyPart : messageBodyParts) {
            if (!processEmlBodyPart(bodyPart, extractionPath)) {
                messageBodyPartsLeft.add(bodyPart);
            }
        }
        return messageBodyPartsLeft;
    }

    private void writeOutputEml(MimeMessage message) throws IOException, MessagingException {
        String outputFileName = outputFileNameGenerator.generateNewOutputFileName();
        File emlOutputFile = new File(outputPath.toFile(), outputFileName);
        log.info("WRITING : " + emlOutputFile.getAbsolutePath());
        try (OutputStream emlOutputStream = bufferedStreamFactory.writeFile(emlOutputFile)) {
            message.writeTo(emlOutputStream);
        }
    }

    private boolean processEmlBodyPart(BodyPart bodyPart, ExtractionPath extractionPath) throws MessagingException,
            IOException {
        log.debug("Body part encountered, type : " + bodyPart.getContentType());
        if (MessageUtils.isZip(bodyPart)) {
            try (InputStream inputStream = bodyPart.getInputStream()) {
                processZipInputStream(bodyPart.getFileName(), inputStream, extractionPath);
            }
            return true;
        }
        if (MessageUtils.isMessage(bodyPart)) {
            try (InputStream inputStream = bodyPart.getInputStream()) {
                processEmlInputStream(bodyPart.getFileName(), inputStream, extractionPath);
            }
            return true;
        }
        if (MessageUtils.isText(bodyPart)) {
            if (MessageUtils.isStringBodyEmpty(bodyPart)) {
                return true;
            }
            return false;
        }
        // we do not know the policy regarding different from zip body parts, assume they are to be saved
        return false;
    }


    private boolean isEmlFilename(String zipEntryName) {
        return zipEntryName.toLowerCase().endsWith(".eml");
    }

    private boolean isZipFilename(String zipEntryName) {
        return zipEntryName.toLowerCase().endsWith(".zip");
    }
}
