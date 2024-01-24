package nu.marginalia.control;

import com.github.jknack.handlebars.*;
import nu.marginalia.renderer.config.HandlebarsConfigurator;

import java.time.LocalDate;

public class ControlHandlebarsConfigurator implements HandlebarsConfigurator {
    @Override
    public void configure(Handlebars handlebars) {
        handlebars.registerHelper("readableUUID", new UUIDHelper());
        handlebars.registerHelper("shortTimestamp", new ShortTimestampHelper());
    }

}

class ShortTimestampHelper implements Helper<Object> {
    @Override
    public Object apply(Object context, Options options) {
        if (context == null) return "";
        String ts = context.toString();

        String retDateBase = ts.replace('T', ' ');

        // if another day, return date, hour and minute
        if (!ts.startsWith(LocalDate.now().toString())) {
            // return hour minute and seconds
            return retDateBase.substring(0, "YYYY-MM-DDTHH:MM".length());
        }
        else { // return date, hour and minute but not seconds or ms
            return retDateBase.substring("YYYY-MM-DDT".length(), "YYYY-MM-DDTHH:MM:SS".length());
        }
    }
}


/** Helper for rendering UUIDs in a more readable way */
class UUIDHelper implements Helper<Object> {
    @Override
    public Object apply(Object context, Options options) {
        if (context == null) return "";
        String instance = context.toString();
        if (instance.length() < 31) return "";

        instance = instance.replace("-", "");
        String color1 = "#" + instance.substring(0, 6);
        String color2 = "#" + instance.substring(6, 12);
        String color3 = "#" + instance.substring(12, 18);
        String color4 = "#" + instance.substring(18, 24);

        String shortName1 = instance.substring(0, 2);
        String shortName2 = instance.substring(2, 4);
        String shortName3 = instance.substring(4, 6);
        String shortName4 = instance.substring(6, 8);

        String ret = "<span title=\"%s\">".formatted(context.toString()) +
                "<span style=\"text-shadow: 0 0 0.2ch %s; font-family: monospace;\">%s</span>".formatted(color1, shortName1) +
                "<span style=\"text-shadow: 0 0 0.2ch %s; font-family: monospace;\">%s</span>".formatted(color2, shortName2) +
                "<span style=\"text-shadow: 0 0 0.2ch %s; font-family: monospace;\">%s</span>".formatted(color3, shortName3) +
                "<span style=\"text-shadow :0 0 0.2ch %s; font-family: monospace;\">%s</span>".formatted(color4, shortName4);
        return ret;
    }
}