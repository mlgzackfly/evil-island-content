package tw.zack.evilisland.model;

public enum CampaignMetric {
    DEFENSE("城防"),
    SUPPLY("供應"),
    INTELLIGENCE("情報"),
    MORALE("民心");

    private final String display;

    CampaignMetric(String display) {
        this.display = display;
    }

    public String display() {
        return display;
    }
}
