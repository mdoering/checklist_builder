package de.doering.dwca.utils;

import com.google.common.base.Strings;

public class ParagraphBuilder {
    private StringBuilder sb = new StringBuilder();

    public void append(String para) {
        if (!Strings.isNullOrEmpty(para)) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append(para.trim());
        }
    }

    public String toString() {
        return sb.length() > 0 ? sb.toString() : null;
    }
}
