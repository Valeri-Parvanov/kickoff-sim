package com.kickoffsim.dto;

import java.util.Locale;

public enum RecapStoryKind {

    TITLE_DECIDED("🏆", RecapStoryFamily.NARRATIVE),
    TITLE_RACE("⚔️", RecapStoryFamily.NARRATIVE),
    TITLE_BATTLE("🏁", RecapStoryFamily.NARRATIVE),
    UPSET("😱", RecapStoryFamily.NARRATIVE),
    SURGE("🚀", RecapStoryFamily.NARRATIVE),
    COLLAPSE("📉", RecapStoryFamily.NARRATIVE),
    BREAKOUT("✨", RecapStoryFamily.NARRATIVE),
    COMEBACK("🔄", RecapStoryFamily.NARRATIVE),
    LATE_DRAMA("⏱️", RecapStoryFamily.NARRATIVE),
    SWINGS("🎢", RecapStoryFamily.NARRATIVE),
    HAT_TRICK("🎩", RecapStoryFamily.NARRATIVE),
    MVP("🌟", RecapStoryFamily.NARRATIVE),
    STREAK("🔥", RecapStoryFamily.NARRATIVE),
    BIG_WIN("💥", RecapStoryFamily.NARRATIVE),
    AWAY_WIN("✈️", RecapStoryFamily.NARRATIVE),
    GOAL_FEST("🎯", RecapStoryFamily.NARRATIVE),
    SECOND_PLACE("🥈", RecapStoryFamily.NARRATIVE),
    SCORER_RACE("👟", RecapStoryFamily.NARRATIVE),
    ATTACK_DEFENCE("🛡️", RecapStoryFamily.NARRATIVE),
    BOTTOM("🧱", RecapStoryFamily.NARRATIVE),
    STATS("📈", RecapStoryFamily.STATS),
    SQUAD("⭐", RecapStoryFamily.LIST),
    BENCH("🪑", RecapStoryFamily.LIST),
    RESULTS("⚽", RecapStoryFamily.LIST);

    private final String icon;

    private final RecapStoryFamily family;

    RecapStoryKind(String icon, RecapStoryFamily family) {
        this.icon = icon;
        this.family = family;
    }

    public String getIcon() {
        return icon;
    }

    public RecapStoryFamily getFamily() {
        return family;
    }

    public String getSlug() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
