package net.lazyz.worldareareset;

final class InGameTextFormatter {

    private static final char DIVIDER_STAR = '✦';
    private static final int CHAT_SPACE_WIDTH = 4;

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

    static String centerOnDividerStar(String text) {
        String[] lines = text.split("\\R", -1);
        double targetCenter = -1;

        for (String line : lines) {
            int starIndex = line.indexOf(DIVIDER_STAR);
            if (starIndex >= 0) {
                targetCenter = visiblePixelWidth(line.substring(0, starIndex))
                        + renderedGlyphWidth(DIVIDER_STAR) / 2.0;
                break;
            }
        }

        if (targetCenter < 0) {
            return text;
        }

        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            if (line.indexOf(DIVIDER_STAR) >= 0 || stripLegacyCodes(line).isBlank()) {
                continue;
            }

            String content = trimVisibleLine(line);
            int contentWidth = visiblePixelWidth(content);
            int leadingSpaces = Math.max(0,
                    (int) Math.round((targetCenter - contentWidth / 2.0) / CHAT_SPACE_WIDTH));
            lines[index] = " ".repeat(leadingSpaces) + content;
        }

        return String.join("\n", lines);
    }

    private static String trimVisibleLine(String line) {
        String trimmed = line.strip();
        int codeEnd = 0;
        int codeLength;

        while ((codeLength = legacyCodeLength(trimmed, codeEnd)) > 0) {
            codeEnd += codeLength;
        }

        int contentStart = codeEnd;
        while (contentStart < trimmed.length() && Character.isWhitespace(trimmed.charAt(contentStart))) {
            contentStart++;
        }

        return trimmed.substring(0, codeEnd) + trimmed.substring(contentStart);
    }

    private static String stripLegacyCodes(String text) {
        StringBuilder visible = new StringBuilder(text.length());
        for (int index = 0; index < text.length();) {
            int codeLength = legacyCodeLength(text, index);
            if (codeLength > 0) {
                index += codeLength;
                continue;
            }

            int codePoint = text.codePointAt(index);
            visible.appendCodePoint(codePoint);
            index += Character.charCount(codePoint);
        }
        return visible.toString();
    }

    private static int visiblePixelWidth(String text) {
        int width = 0;
        for (int index = 0; index < text.length();) {
            int codeLength = legacyCodeLength(text, index);
            if (codeLength > 0) {
                index += codeLength;
                continue;
            }

            int codePoint = text.codePointAt(index);
            width += renderedGlyphWidth(codePoint);
            index += Character.charCount(codePoint);
        }
        return width;
    }

    private static int renderedGlyphWidth(int codePoint) {
        int coreWidth;
        if (codePoint > 127) {
            coreWidth = 8;
        } else {
            coreWidth = switch ((char) codePoint) {
                case ' ' -> 3;
                case '!', '\'', ',', '.', ':', ';', 'i', 'l', '|' -> 1;
                case '`' -> 2;
                case '"', '(', ')', '*', '[', ']', '{', '}', 'I', 't' -> 3;
                case '<', '>', 'f', 'k' -> 4;
                case '@', '~' -> 6;
                default -> 5;
            };
        }

        int spacing = 1;
        int boldExtra = codePoint == ' ' ? 0 : 1;
        return coreWidth + spacing + boldExtra;
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
