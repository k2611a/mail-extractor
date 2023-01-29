package org.example.impl;

import java.util.ArrayList;
import java.util.List;

import javax.mail.BodyPart;
import javax.mail.MessagingException;
import javax.mail.Multipart;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class MessageUtils {

    private static final Logger log = LogManager.getLogger(ExtractMail.class);

    private MessageUtils() {
    }

    static List<BodyPart> toBodyParts(Multipart multipart) throws MessagingException {
        List<BodyPart> result = new ArrayList<>();
        for (int i = 0; i < multipart.getCount(); i++) {
            result.add(multipart.getBodyPart(i));
        }
        sortByExtractionPriority(result);
        return result;
    }

    private static void sortByExtractionPriority(List<BodyPart> result) {
        result.sort((o1, o2) -> {
            try {
                return Integer.compare(priority(o1), priority(o2));
            } catch (MessagingException e) {
                log.error("Exception while processing content type", e);
                throw new RuntimeException(e);
            }
        });
    }

    private static int priority(BodyPart bodyPart) throws MessagingException {
        // to ensure depth first traversal of nested email/archive structure
        if (bodyPart == null) {
            return -1;
        }
        if (isZip(bodyPart)) {
            return 1;
        }
        if (isMessage(bodyPart)) {
            return 2;
        }
        return 1000;
    }

    static boolean isMessage(BodyPart bodyPart) throws MessagingException {
        return bodyPart.isMimeType("message/rfc822");
    }

    static boolean isText(BodyPart bodyPart) throws MessagingException {
        return bodyPart.isMimeType("text/plain");
    }

    static boolean isZip(BodyPart bodyPart) throws MessagingException {
        return bodyPart.isMimeType("application/zip") || bodyPart.isMimeType("application/x-zip-compressed");
    }


}
