package tw.zack.evilisland.model;

public enum WeeklyEvent {
    FRONTIER_GAPS(1, "外圍缺口", "決定是否調整人力、補給或斥候配置。", 6, 0, 0, 2),
    SUPPLY_SHORTAGE(2, "補給線吃緊", "決定東門下一週應優先保住哪一項能力。", 0, 8, 0, 2),
    MUSTER_SIGNS(3, "敵軍集結跡象", "選擇迎戰前的主要準備方向。", 3, 0, 6, 0),
    DECISIVE_ORDERS(4, "決戰部署", "確認本輪最終守勢，敵軍會依此改變首領編組。", 4, 4, 4, 4);

    private final int week;
    private final String display;
    private final String summary;
    private final int defensePenalty;
    private final int supplyPenalty;
    private final int intelligencePenalty;
    private final int moralePenalty;

    WeeklyEvent(int week, String display, String summary, int defensePenalty, int supplyPenalty,
                int intelligencePenalty, int moralePenalty) {
        this.week = week;
        this.display = display;
        this.summary = summary;
        this.defensePenalty = defensePenalty;
        this.supplyPenalty = supplyPenalty;
        this.intelligencePenalty = intelligencePenalty;
        this.moralePenalty = moralePenalty;
    }

    public String display() { return display; }
    public String summary() { return summary; }
    public int defensePenalty() { return defensePenalty; }
    public int supplyPenalty() { return supplyPenalty; }
    public int intelligencePenalty() { return intelligencePenalty; }
    public int moralePenalty() { return moralePenalty; }

    public static WeeklyEvent fromWeek(int week) {
        for (WeeklyEvent event : values()) if (event.week == week) return event;
        return FRONTIER_GAPS;
    }
}
