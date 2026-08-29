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

    @Test
    void formatsRemainingTimeWithSecondsAndConfiguredMaximumUnit() {
        assertEquals("4 minutes 59 seconds", AreaCleanupTask.formatDuration(299, "minutes", false));
        assertEquals("2 hours 14 minutes 3 seconds", AreaCleanupTask.formatDuration(8_043, "hours", false));
        assertEquals("1 day 2 hours 14 minutes 3 seconds", AreaCleanupTask.formatDuration(94_443, "days", false));

        assertEquals("4分钟59秒", AreaCleanupTask.formatDuration(299, "minutes", true));
        assertEquals("2小时14分钟3秒", AreaCleanupTask.formatDuration(8_043, "hours", true));
        assertEquals("1天2小时14分钟3秒", AreaCleanupTask.formatDuration(94_443, "days", true));
    }
}
