package tw.zack.evilisland;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import tw.zack.evilisland.model.ExpeditionStoryChapter;
import tw.zack.evilisland.model.ExpeditionStoryChoice;
import tw.zack.evilisland.model.ExpeditionStoryDecisionSnapshot;
import tw.zack.evilisland.model.ExpeditionStoryProgressSnapshot;
import tw.zack.evilisland.model.ExpeditionStoryResolution;
import tw.zack.evilisland.model.ExpeditionStoryRules;
import tw.zack.evilisland.model.ExplorationSite;
import tw.zack.evilisland.persistence.ExpeditionRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ExpeditionNarrativeService {
    private final ExpeditionRepository repository;
    private final ExpeditionStoryWorldService worldScenes;

    public ExpeditionNarrativeService(ExpeditionRepository repository, ExpeditionStoryWorldService worldScenes) {
        this.repository = repository;
        this.worldScenes = worldScenes;
    }

    public void load() {
        worldScenes.reconcile(repository.storyProgress());
    }

    public ExpeditionStoryProgressSnapshot progress(ExplorationSite site) {
        return repository.storyProgress(site);
    }

    public ExpeditionStoryChapter chapter(ExplorationSite site, int chapter) {
        return ExpeditionStoryChapter.forSite(site, chapter);
    }

    public List<String> boardLore(Player player, ExplorationSite site) {
        ExpeditionStoryProgressSnapshot progress = progress(site);
        ExpeditionStoryChapter chapter = chapter(site, progress.chapter());
        List<String> lore = new ArrayList<>();
        lore.add(progress.completed() ? "區域故事已完成｜後續巡查" : "第 " + chapter.chapter() + " 章｜"
                + chapter.title());
        lore.addAll(wrap(chapter.briefingAfter(progress.lastChoice())));
        if (progress.direction() != null) lore.add("目前方向：" + progress.direction().display());
        repository.lastStoryDecision(player.getUniqueId(), site).ifPresent(decision ->
                lore.add("管事記得你上次主張「" + decision.choice().display() + "」。"));
        lore.add("每區每週最多推進一章；重玩不會改寫章節。");
        return lore;
    }

    public ExpeditionStoryResolution decide(UUID expeditionId, ExplorationSite site, int chapter,
                                             ExpeditionStoryChoice choice, UUID leader, UUID partner,
                                             int cycle, int week, long now) {
        ExpeditionStoryResolution resolution = repository.recordStoryDecision(
                new ExpeditionStoryDecisionSnapshot(expeditionId, site, chapter, choice, leader, partner,
                        cycle, week, now));
        if (resolution.recorded()) worldScenes.show(resolution.progress());
        return resolution;
    }

    public boolean allCompleted() {
        return ExpeditionStoryRules.allCompleted(repository.storyProgress());
    }

    public Material approachMarker(ExpeditionStoryChoice direction, int index) {
        if (direction == ExpeditionStoryChoice.SECURE) return index == 0 ? Material.SHIELD : Material.REDSTONE_TORCH;
        if (direction == ExpeditionStoryChoice.CONNECT) return index == 0 ? Material.OAK_SIGN : Material.LANTERN;
        return index == 0 ? Material.OAK_SIGN : Material.REDSTONE_TORCH;
    }

    public String approachMarkerName(ExpeditionStoryChoice direction, int index) {
        if (direction == ExpeditionStoryChoice.SECURE) return index == 0 ? "前章留下的封存標記" : "內側警戒標";
        if (direction == ExpeditionStoryChoice.CONNECT) return index == 0 ? "前章留下的通路標記" : "外側回訊燈";
        return index == 0 ? "前隊留下的路標" : "被折斷的警戒標記";
    }

    public List<String> wrap(String text) {
        List<String> lines = new ArrayList<>();
        int width = 24;
        for (int start = 0; start < text.length(); start += width) {
            lines.add(text.substring(start, Math.min(text.length(), start + width)));
        }
        return lines;
    }
}
