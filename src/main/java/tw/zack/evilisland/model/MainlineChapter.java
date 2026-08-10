package tw.zack.evilisland.model;

public enum MainlineChapter {
    FOOTHOLD(1, "立足", "讓新城今天仍能運作",
            "完成一份今日契約，處理本週共同方針，先把城防與補給穩住。"),
    OPEN_ROADS(2, "探路", "把五處遠路變成可重返的路",
            "從遠征營地推進區域故事；不同路線、整備與撤退時機都會留下結果。"),
    CHOOSE_BOUNDARIES(3, "定界", "決定新城如何與各方共存",
            "處理危機、核實居民情報並回應異族使者；選擇會改變後續場景與任務壓力。"),
    HOLD_NEW_CITY(4, "守城", "承受前三週累積的後果",
            "修復設施、完成決戰整備並迎戰本輪刑天；結算後保留工程與輪次紀錄。" );

    private final int week;
    private final String display;
    private final String purpose;
    private final String objective;

    MainlineChapter(int week, String display, String purpose, String objective) {
        this.week = week;
        this.display = display;
        this.purpose = purpose;
        this.objective = objective;
    }

    public int week() { return week; }
    public String display() { return display; }
    public String purpose() { return purpose; }
    public String objective() { return objective; }

    public static MainlineChapter fromWeek(int week) {
        int normalized = Math.max(1, Math.min(4, week));
        return values()[normalized - 1];
    }
}
