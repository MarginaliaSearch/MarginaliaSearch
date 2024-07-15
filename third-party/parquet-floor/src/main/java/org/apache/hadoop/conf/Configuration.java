package org.apache.hadoop.conf;

public class Configuration {
    public Configuration(boolean x) {}

    public boolean getBoolean(String x, boolean y) {
        return y;
    }

    public void setBoolean(String x, boolean y) {
    }

    public int getInt(String x, int y) {
        return y;
    }

    public String get(String x) {
        return null;
    }
}
