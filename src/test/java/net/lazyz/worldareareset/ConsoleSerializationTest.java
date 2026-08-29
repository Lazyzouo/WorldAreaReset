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
}
