package net.lazyz.worldareareset;

final class InGameTextFormatter {

    private InGameTextFormatter() {
    }

    static String forceBold(String text) {
        StringBuilder result = new StringBuilder(text.length() + 16).append("&l");

        for (int index = 0; index < text.length();) {
            int codeLength = legacyCodeLength(text, index);
            if (codeLength > 0) {
                char code = Character.toLowerCase(text.charAt(index + 1));
                result.append(text, index, index + codeLength);
                if (code == '#' || isColorOrReset(code)) {
                    result.append("&l");
                }
                index += codeLength;
                continue;
            }

            int codePoint = text.codePointAt(index);
            result.appendCodePoint(codePoint);
            index += Character.charCount(codePoint);
        }

        return result.toString();
    }

    static String prefixEveryLine(String prefix, String text) {
        String[] lines = text.split("\\R", -1);
        StringBuilder result = new StringBuilder(text.length() + prefix.length() * lines.length);

        for (int index = 0; index < lines.length; index++) {
            if (index > 0) {
                result.append('\n');
            }
            result.append(prefix).append(lines[index]);
        }

        return result.toString();
    }

    static String leftAlign(String text) {
        String[] lines = text.split("\\R", -1);

        for (int index = 0; index < lines.length; index++) {
            lines[index] = leftAlignLine(lines[index]);
        }

        return String.join("\n", lines);
    }

    private static String leftAlignLine(String line) {
        int index = 0;
        while (index < line.length() && Character.isWhitespace(line.charAt(index))) {
            index++;
        }

        StringBuilder formattingCodes = new StringBuilder();
        int codeLength;
        while ((codeLength = legacyCodeLength(line, index)) > 0) {
            formattingCodes.append(line, index, index + codeLength);
            index += codeLength;
            while (index < line.length() && Character.isWhitespace(line.charAt(index))) {
                index++;
            }
        }

        return formattingCodes.append(line, index, line.length()).toString();
    }

    private static int legacyCodeLength(String text, int index) {
        if (index + 1 >= text.length() || text.charAt(index) != '&') {
            return 0;
        }

        char code = Character.toLowerCase(text.charAt(index + 1));
        if (isLegacyCode(code)) {
            return 2;
        }

        if (code == '#' && index + 7 < text.length()) {
            for (int offset = 2; offset < 8; offset++) {
                if (Character.digit(text.charAt(index + offset), 16) < 0) {
                    return 0;
                }
            }
            return 8;
        }

        return 0;
    }

    private static boolean isLegacyCode(char code) {
        return isColorOrReset(code) || (code >= 'k' && code <= 'o');
    }

    private static boolean isColorOrReset(char code) {
        return (code >= '0' && code <= '9') || (code >= 'a' && code <= 'f') || code == 'r';
    }
}
