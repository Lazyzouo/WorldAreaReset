package net.lazyz.worldareareset;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AreaCleanupTaskTest {

    @Test
    void displaysCleanupIntervalUsingConfiguredUnit() {
        assertEquals("5 minutes", new AreaCleanupTask.Interval(5, "minutes").display(false));
        assertEquals("2 hours", new AreaCleanupTask.Interval(2, "hours").display(false));
        assertEquals("1 day", new AreaCleanupTask.Interval(1, "days").display(false));

        assertEquals("5 分钟", new AreaCleanupTask.Interval(5, "minutes").display(true));
        assertEquals("2 小时", new AreaCleanupTask.Interval(2, "hours").display(true));
        assertEquals("1 天", new AreaCleanupTask.Interval(1, "days").display(true));
    }
}
