package net.lazyz.worldareareset;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsoleSerializationTest {

    @Test
    void keepsHexSeverityColorsInConsoleSectionCodes() {
        String serialized = WorldAreaResetPlugin.serializeConsole(
                Component.text("warning").color(TextColor.fromHexString("#FFB7D5")));

        assertTrue(serialized.startsWith("\u00a7d"),
                "console output must use a visible warning color instead of quantizing it to white");
    }

    @Test
    void rebuildsGradientNotificationsWithBannerLabelAndValueColors() {
        String message = "<gradient:#FFB7D5:#D7C7FF:#B9E7FF:#D7C7FF:#FFB7D5><bold>"
                + "Restoration queued > Countdown started; automatic timer reset to 3 hours"
                + "</bold></gradient>";

        String markup = WorldAreaResetPlugin.consoleBodyMarkup(message, "<#D7C7FF>", false);

        assertTrue(markup.contains("<#E62028><bold>Restoration queued > </bold>"),
                "notification labels should use the banner title color");
        assertTrue(markup.contains("<#B9E7FF><bold>Countdown started; automatic timer reset to 3 hours</bold>"),
                "notification values should use the banner value color");
        assertTrue(!markup.contains("<gradient:"),
                "player-facing gradients must not leak into console markup");
    }

    @Test
    void colorsMultilineLabelsAndValuesWithoutPlayerGradient() {
        String message = "<gradient:#FFB7D5:#D7C7FF:#B9E7FF:#D7C7FF:#FFB7D5>"
                + "--------- ✧ ---------</gradient>\n"
                + "<gradient:#FFB7D5:#D7C7FF:#B9E7FF:#D7C7FF:#FFB7D5>"
                + "Terrain restoration warning</gradient>\n"
                + "<gradient:#FFB7D5:#D7C7FF:#B9E7FF:#D7C7FF:#FFB7D5>World > arena_nether</gradient>";

        String markup = WorldAreaResetPlugin.consoleBodyMarkup(message, "<#FFB7D5>", true);

        assertTrue(markup.contains("<#D7C7FF><bold>--------- ✧ ---------</bold>"),
                "dividers should use the banner separator color");
        assertTrue(markup.contains("<#D7C7FF><bold>World > </bold><#B9E7FF><bold>arena_nether</bold>"),
                "multiline label/value rows should use distinct banner colors");
        assertTrue(!markup.contains("#FFB7D5"),
                "the player warning color must not be reused in console markup");
    }

    @Test
    void keepsConsoleHelpDividersDecorativeWhilePrefixingContent() {
        String markup = WorldAreaResetPlugin.consoleBodyMarkup(
                "--------- ✧ ---------\nPlugin name: WorldAreaReset", "<#D7C7FF>", false);
        String prefixed = InGameTextFormatter.prefixContentLines("<#8A2387>[WorldAreaReset]</#8A2387> ", markup);
        String[] lines = prefixed.split("\\R", -1);

        assertTrue(!lines[0].contains("[WorldAreaReset]"),
                "console help dividers should remain decorative and prefix-free");
        assertTrue(lines[1].contains("[WorldAreaReset]"),
                "console help content should use the plugin prefix");
    }
}
