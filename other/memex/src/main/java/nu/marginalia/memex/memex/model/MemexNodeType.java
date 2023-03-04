package nu.marginalia.memex.memex.model;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public enum MemexNodeType {
    DOCUMENT("text/gemini"),
    IMAGE("image/png"),
    DIRECTORY("other/directory"),
    TEXT("text/plain"),
    OTHER("application/binary");

    public String mime;
}
