package tw.zack.evilisland.model;

public enum MissionType {
    PATROL("巡防"),
    GATHER("採集"),
    SCOUT("偵察");

    private final String display;

    MissionType(String display) {
        this.display = display;
    }

    public String display() {
        return display;
    }
}
