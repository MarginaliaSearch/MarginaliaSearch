package nu.marginalia.codec.processed;

import blue.strategic.parquet.Hydrator;


public class ProcessedDomainDataDomainNameHydrator implements Hydrator<String, String> {

    @Override
    public String start() {
        return "";
    }

    @Override
    public String add(String target, String heading, Object value) {
        if ("domain".equals(heading)) {
            return (String) value;
        }
        return target;
    }

    @Override
    public String finish(String target) {
        return target;
    }

}
