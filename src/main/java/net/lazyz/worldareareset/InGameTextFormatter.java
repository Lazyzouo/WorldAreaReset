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
                if (code == '#' || code == 'x' || isColorOrReset(code)) {
                    result.append("&l");
                }
                index += codeLength;
                continue;
            }

            if (text.charAt(index) == '<') {
                int tagEnd = text.indexOf('>', index + 1);
                if (tagEnd >= 0) {
                    String tag = text.substring(index, tagEnd + 1);
                    result.append(tag);
                    if (isMiniMessageResetOrColor(tag)) {
                        result.append("&l");
                    }
                    index = tagEnd + 1;
                    continue;
                }
            }

            int codePoint = text.codePointAt(index);
            result.appendCodePoint(codePoint);
            index += Character.charCount(codePoint);
        }

        return result.toString();
    }

    static String prefixContentLines(String prefix, String text) {
        String[] lines = text.split("\\R", -1);
        StringBuilder result = new StringBuilder(text.length() + prefix.length() * lines.length);

        for (int index = 0; index < lines.length; index++) {
            if (index > 0) {
                result.append('\n');
            }
            if (!isDividerLine(lines[index])) {
                result.append(prefix);
            }
            result.append(lines[index]);
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

        while (index < line.length() && line.charAt(index) == '<') {
            int tagEnd = line.indexOf('>', index + 1);
            if (tagEnd < 0) {
                break;
            }
            formattingCodes.append(line, index, tagEnd + 1);
            index = tagEnd + 1;
            while (index < line.length() && Character.isWhitespace(line.charAt(index))) {
                index++;
            }
        }

        return formattingCodes.append(line, index, line.length()).toString();
    }

    private static boolean isDividerLine(String line) {
        boolean hasDividerCharacter = false;

        for (int index = 0; index < line.length();) {
            int codeLength = legacyCodeLength(line, index);
            if (codeLength > 0) {
                index += codeLength;
                continue;
            }

            if (line.charAt(index) == '<') {
                int tagEnd = line.indexOf('>', index + 1);
                if (tagEnd >= 0) {
                    index = tagEnd + 1;
                    continue;
                }
            }

            int codePoint = line.codePointAt(index);
            if (!Character.isWhitespace(codePoint)) {
                if (!isDividerCharacter(codePoint)) {
                    return false;
                }
                hasDividerCharacter = true;
            }
            index += Character.charCount(codePoint);
        }

        return hasDividerCharacter;
    }

    private static boolean isDividerCharacter(int codePoint) {
        return switch (codePoint) {
            case '-', '_', '=', '*', 0x2014, 0x2500, 0x2501, 0x2550, 0x2726, 0x2727 -> true;
            default -> false;
        };
    }

    private static int legacyCodeLength(String text, int index) {
        if (index + 1 >= text.length()
                || (text.charAt(index) != '&' && text.charAt(index) != '§')) {
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

        if (code == 'x' && index + 13 < text.length()) {
            char markerPrefix = text.charAt(index);
            for (int offset = 0; offset < 6; offset++) {
                int marker = index + 2 + offset * 2;
                if (text.charAt(marker) != markerPrefix
                        || Character.digit(text.charAt(marker + 1), 16) < 0) {
                    return 0;
                }
            }
            return 14;
        }

        return 0;
    }

    private static boolean isLegacyCode(char code) {
        return isColorOrReset(code) || (code >= 'k' && code <= 'o');
    }

    private static boolean isColorOrReset(char code) {
        return (code >= '0' && code <= '9') || (code >= 'a' && code <= 'f') || code == 'r';
    }

    private static boolean isMiniMessageResetOrColor(String tag) {
        String normalized = tag.toLowerCase(java.util.Locale.ROOT);
        return normalized.equals("<reset>")
                || normalized.startsWith("<color:")
                || normalized.startsWith("<#")
                || normalized.startsWith("<gradient:")
                || normalized.equals("<gradient>")
                || normalized.matches("<(black|dark_blue|dark_green|dark_aqua|dark_red|dark_purple|gold|gray|dark_gray|blue|green|aqua|red|light_purple|yellow|white)>");
    }
}
