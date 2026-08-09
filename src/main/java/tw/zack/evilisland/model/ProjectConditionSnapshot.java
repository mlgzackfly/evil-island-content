package tw.zack.evilisland.model;

public record ProjectConditionSnapshot(
        CityProject project,
        int condition,
        long updatedAt
) {
    public ProjectConditionSnapshot {
        condition = Math.max(0, Math.min(100, condition));
    }
}
