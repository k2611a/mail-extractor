package org.example.impl;

import java.util.ArrayList;
import java.util.List;

import javax.mail.BodyPart;
import javax.mail.MessagingException;
import javax.mail.Multipart;

class MessageUtils {

    private MessageUtils() {
    }

    static List<BodyPart> toBodyParts(Multipart multipart) throws MessagingException {
        List<BodyPart> result = new ArrayList<>();
        for (int i = 0; i < multipart.getCount(); i++) {
            result.add(multipart.getBodyPart(i));
        }
        return result;
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
