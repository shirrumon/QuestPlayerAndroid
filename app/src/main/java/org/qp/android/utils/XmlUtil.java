package org.qp.android.utils;

import androidx.annotation.NonNull;

import org.simpleframework.xml.core.Persister;

import java.io.ByteArrayOutputStream;

public final class XmlUtil {
    private static final Persister PERSISTER = new Persister();

    @NonNull
    public static String objectToXml(Object o) throws Exception {
        try (var byteArrayOutputStream = new ByteArrayOutputStream()) {
            PERSISTER.write(o, byteArrayOutputStream);
            return byteArrayOutputStream.toString();
        }
    }

    public static <T> T xmlToObject(String xml, Class<T> clazz) throws Exception {
        return PERSISTER.read(clazz, xml);
    }
}
