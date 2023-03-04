package nu.marginalia.memex.memex.model;

import lombok.Getter;

import java.util.stream.Collectors;

@Getter
public class MemexTaskTags {
    public final String tagsCondensed;

    private static final int TAG_START = '(';
    private static final int TAG_END = ')';

    public MemexTaskTags(String text) {
        tagsCondensed = getTags(text);
    }

    public boolean hasTag(int tag) {
        return tagsCondensed.indexOf(tag) >= 0;
    }

    @Override
    public String toString() {
        return tagsCondensed.chars().mapToObj(c -> '(' + Character.toString(c) + ')')
                .collect(Collectors.joining(" "));
    }

    private static String getTags(String task) {
        StringBuilder sb = new StringBuilder();
        for (int i = task.indexOf(TAG_START);
             i >= 0 && i+2 < task.length();
             i = task.indexOf(TAG_START, i+1))
        {
            if (task.charAt(i+2) == TAG_END) {
                sb.append(task.charAt(i+1));
            }
        }
        return sb.toString();
    }
}
