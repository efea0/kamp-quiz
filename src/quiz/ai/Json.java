package quiz.ai;

import java.util.ArrayList;
import java.util.List;

final class Json {

    private Json() {
    }


    static String escape(String text) {
        StringBuilder out = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"'  -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }



    static List<String> valuesOf(String json, String key) {
        List<String> found = new ArrayList<>();
        String needle = "\"" + key + "\"";
        int i = 0;

        while ((i = json.indexOf(needle, i)) >= 0) {
            int p = i + needle.length();


            while (p < json.length() && Character.isWhitespace(json.charAt(p))) p++;
            if (p >= json.length() || json.charAt(p) != ':') { i = p; continue; }
            p++;
            while (p < json.length() && Character.isWhitespace(json.charAt(p))) p++;
            if (p >= json.length() || json.charAt(p) != '"') { i = p; continue; }

            int[] son = new int[1];
            found.add(readString(json, p, son));
            i = son[0];
        }
        return found;
    }


    private static String readString(String json, int start, int[] endOut) {
        StringBuilder out = new StringBuilder();
        int i = start + 1;

        while (i < json.length()) {
            char c = json.charAt(i);

            if (c == '"') {
                i++;
                break;
            }
            if (c != '\\') {
                out.append(c);
                i++;
                continue;
            }


            i++;
            if (i >= json.length()) break;
            char e = json.charAt(i++);
            switch (e) {
                case 'n'  -> out.append('\n');
                case 'r'  -> out.append('\r');
                case 't'  -> out.append('\t');
                case 'b'  -> out.append('\b');
                case 'f'  -> out.append('\f');
                case '"'  -> out.append('"');
                case '\\' -> out.append('\\');
                case '/'  -> out.append('/');
                case 'u'  -> {
                    if (i + 4 <= json.length()) {
                        out.append((char) Integer.parseInt(json.substring(i, i + 4), 16));
                        i += 4;
                    }
                }
                default -> out.append(e);
            }
        }
        endOut[0] = i;
        return out.toString();
    }
}
