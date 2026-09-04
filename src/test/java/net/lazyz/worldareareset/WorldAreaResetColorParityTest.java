package net.lazyz.worldareareset;

import org.bukkit.ChatColor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldAreaResetColorParityTest {
    @Test
    void convertsMiniMessageAndLegacyFormatting() {
        String output = WorldAreaResetPlugin.color(
                "<#8A2387><bold>[WorldAreaReset]</bold></#8A2387> Ready &#FF0000&lnow");
        assertFalse(output.contains("<bold>"));
        assertFalse(output.contains("&#FF0000"));
        assertTrue(ChatColor.stripColor(output).contains("[WorldAreaReset] Ready now"));
    }

    @Test
    void removesUnmatchedFormattingTags() {
        String output = WorldAreaResetPlugin.color(
                "<color:#8A2387><bold>Message <bold>text</gradient>");
        assertFalse(output.contains("<color:"));
        assertFalse(output.contains("<bold>"));
        assertFalse(output.contains("</gradient>"));
        assertTrue(ChatColor.stripColor(output).contains("Message text"), output);
    }

    @Test
    void compactsSemanticGuiColors() {
        String blue = WorldAreaResetPlugin.colorForGui("<#00D2FF>Blue GUI lore");
        String success = WorldAreaResetPlugin.colorForGui("<#55FF55>Save");
        String uncolored = WorldAreaResetPlugin.colorForGui("Uncolored GUI button");
        assertTrue(blue.startsWith("§x§0§0§D§2§F§F"), blue);
        assertTrue(success.startsWith("§x§5§5§F§F§5§5"), success);
        assertTrue(uncolored.startsWith("§x§F§F§B§7§D§5"), uncolored);
        assertEquals("Blue GUI lore", ChatColor.stripColor(blue));
    }

    @Test
    void keepsHelpDescriptionsLightPurpleAndRoleHeadingsYellow() throws Exception {
        var method = WorldAreaResetPlugin.class.getDeclaredMethod("colorSingleTone", String.class, String.class);
        method.setAccessible(true);
        String description = (String) method.invoke(null,
                "<#FF5555><bold>  /war cleanup - Run cleanup</bold>", "help_menu_admin");
        String heading = (String) method.invoke(null,
                "<#FFFF55><bold>- Administrator commands</bold>", "help_menu_admin");
        assertTrue(description.startsWith("§x§D§7§C§7§F§F"), description);
        assertTrue(heading.startsWith("§x§F§F§F§F§5§5"), heading);
    }

    @Test
    void keepsFixedPrefixPaletteWhileApplyingBodySeverity() throws Exception {
        var method = WorldAreaResetPlugin.class.getDeclaredMethod("colorSingleTone", String.class, String.class);
        method.setAccessible(true);
        String output = (String) method.invoke(null,
                "<#FF5555><bold>[WorldAreaReset]</bold> <#555555><bold>»</bold> <#B9E7FF>Operation failed",
                "no_permission");
        String stripped = ChatColor.stripColor(output);
        assertTrue(stripped.startsWith("[WorldAreaReset] » Operation failed"), stripped);
        assertTrue(output.startsWith("§5§l[§c§lWorldAreaReset"), output);
        assertTrue(output.contains("§x§F§F§5§5§5§5"), output);
    }
}
