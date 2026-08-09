package tw.zack.evilisland.model;

import java.util.Collection;

public final class ResidentIntelRules {
    private ResidentIntelRules() { }

    public static boolean truthful(LivingEventType type, ResidentRole resident) {
        return Math.floorMod(type.ordinal() + resident.ordinal(), ResidentRole.values().length) < 4;
    }

    public static boolean verified(LivingEventType type, Collection<IntelReportSnapshot> reports) {
        return verified(type, reports, 3);
    }

    public static boolean verified(LivingEventType type, Collection<IntelReportSnapshot> reports, int required) {
        int safeRequired = Math.max(2, Math.min(4, required));
        return reports != null && reports.stream().filter(report -> truthful(type, report.resident())).count()
                >= safeRequired;
    }

    public static int threatReduction(LivingEventType type, Collection<IntelReportSnapshot> reports) {
        return verified(type, reports) ? 1 : 0;
    }

    public static ExplorationSite claimedRegion(LivingEventType type, ResidentRole resident) {
        if (truthful(type, resident)) return type.region();
        ExplorationSite[] sites = ExplorationSite.values();
        int index = (type.region().ordinal() + resident.ordinal() + 1) % sites.length;
        if (sites[index] == type.region()) index = (index + 1) % sites.length;
        return sites[index];
    }
}
