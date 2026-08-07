package tw.zack.evilisland.model;

import org.bukkit.Material;

import java.util.Locale;
import java.util.Set;

public enum CityRoute {
    FORTRESS("fortress", "固城路線", Material.SHIELD,
            "強化外牆、防空與守城調度", Set.of(CityProject.WALLS, CityProject.AIR_DEFENSE)),
    EXPEDITION("expedition", "遠征路線", Material.RECOVERY_COMPASS,
            "強化輕疾站、工坊與遠距部署", Set.of(CityProject.SCOUT_POST, CityProject.WORKSHOP)),
    QI_CIVIC("qi_civic", "聚炁民生路線", Material.LODESTONE,
            "強化聚炁、NPC 恢復與異族接待", Set.of(CityProject.QI_MIRROR));

    private final String id;
    private final String display;
    private final Material icon;
    private final String summary;
    private final Set<CityProject> preferredProjects;

    CityRoute(String id, String display, Material icon, String summary, Set<CityProject> preferredProjects) {
        this.id = id;
        this.display = display;
        this.icon = icon;
        this.summary = summary;
        this.preferredProjects = Set.copyOf(preferredProjects);
    }

    public String id() { return id; }
    public String display() { return display; }
    public Material icon() { return icon; }
    public String summary() { return summary; }
    public boolean prefers(CityProject project) { return preferredProjects.contains(project); }

    public static CityRoute parse(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.toLowerCase(Locale.ROOT);
        for (CityRoute route : values()) {
            if (route.id.equals(normalized)) return route;
        }
        return null;
    }
}
