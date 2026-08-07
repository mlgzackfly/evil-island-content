package tw.zack.evilisland.model;

import java.util.Arrays;

public enum CampaignStrategy {
    NONE("none", "尚未決定", "本週尚未形成共同方針。"),
    FORTIFY("fortify", "加固防線", "投入石材與人力加固東門，降低正面突破風險。"),
    PROVISION("provision", "維持補給", "優先維持糧秣與醫療，讓巡防隊保持出勤能力。"),
    RECON("recon", "先制偵察", "派出無跡斥候追查敵軍集結，提早掌握首領弱點。");

    private final String id;
    private final String display;
    private final String summary;

    CampaignStrategy(String id, String display, String summary) {
        this.id = id;
        this.display = display;
        this.summary = summary;
    }

    public String id() { return id; }
    public String display() { return display; }
    public String summary() { return summary; }

    public static CampaignStrategy parse(String id) {
        if (id == null) return NONE;
        return Arrays.stream(values()).filter(value -> value.id.equalsIgnoreCase(id)).findFirst().orElse(NONE);
    }
}
