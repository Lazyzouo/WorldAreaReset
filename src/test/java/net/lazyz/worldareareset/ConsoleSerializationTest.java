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

        assertTrue(serialized.startsWith("\u00a7x\u00a7f\u00a7f\u00a7b\u00a77\u00a7d\u00a75"),
                "console output must keep the warning RGB color instead of quantizing it to white");
    }
}
