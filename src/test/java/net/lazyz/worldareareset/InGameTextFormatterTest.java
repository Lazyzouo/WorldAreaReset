package net.lazyz.worldareareset;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InGameTextFormatterTest {

    @Test
    void doesNotPrefixMiniMessageDividerLines() {
        String message = "<gradient>━━━━━━ ✧ ━━━━━━</gradient>\n<color:#B9E7FF>正文</color>";

        assertEquals("<gradient>━━━━━━ ✧ ━━━━━━</gradient>\n<color:#8A2387>[W]</color> <color:#B9E7FF>正文</color>",
                InGameTextFormatter.prefixContentLines("<color:#8A2387>[W]</color> ", message));
    }

    @Test
    void doesNotPrefixKitloaderLengthAsciiDividerLines() {
        String message = "<gradient>---------------- ✧ ----------------</gradient>\n<color:#B9E7FF>正文</color>";

        assertEquals("<gradient>---------------- ✧ ----------------</gradient>\n<color:#8A2387>[W]</color> <color:#B9E7FF>正文</color>",
                InGameTextFormatter.prefixContentLines("<color:#8A2387>[W]</color> ", message));
    }

    @Test
    void keepsLineBreaksAndStripsIndentationAfterMiniMessageTags() {
        String message = "  <bold>标题</bold>\n    <color:#B9E7FF>内容</color>";

        assertEquals("<bold>标题</bold>\n<color:#B9E7FF>内容</color>", InGameTextFormatter.leftAlign(message));
    }

    @Test
    void preservesSectionColorCodesWhileRemovingIndentation() {
        assertEquals("§c§l警告\n&b内容", InGameTextFormatter.leftAlign("  §c§l警告\n    &b内容"));
    }

    @Test
    void keepsSectionHexSequencesTogetherWhenForcingBold() {
        String legacyHex = "§x§F§F§B§7§D§5";
        assertEquals("&l" + legacyHex + "&l文本", InGameTextFormatter.forceBold(legacyHex + "文本"));
    }
}
