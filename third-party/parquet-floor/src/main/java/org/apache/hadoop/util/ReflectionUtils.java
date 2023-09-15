package org.apache.hadoop.util;

import org.apache.hadoop.conf.Configurable;
import org.apache.hadoop.conf.Configuration;

public final class ReflectionUtils {

    private ReflectionUtils() { /* prevent instantitation */ }

    public static Object newInstance(Class<?> type, Configuration x) {
        try {
            Object o = type.newInstance();
            if (o instanceof  Configurable) {
                ((Configurable) o).setConf(x);
            }
            return o;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
