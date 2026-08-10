package tw.zack.evilisland.expedition;

import org.bukkit.Material;
import tw.zack.evilisland.model.ExpeditionOperation;
import tw.zack.evilisland.model.ExpeditionPhase;
import tw.zack.evilisland.model.ExpeditionRoute;
import tw.zack.evilisland.model.ExpeditionStoryChoice;
import tw.zack.evilisland.model.ExplorationSite;
import tw.zack.evilisland.model.SpeciesType;

public interface ExpeditionScenario {
    ExplorationSite site();
    String boardTitle();
    String routeDisplay(ExpeditionRoute route);
    String routeDescription(ExpeditionRoute route);
    Material routeIcon(ExpeditionRoute route);
    ExpeditionOperation operation(long seed);
    int requiredClues(ExpeditionOperation operation, ExpeditionRoute route, ExpeditionStoryChoice direction);
    boolean combatRequired();
    boolean timedExtraction(ExpeditionOperation operation);
    int enemyCount(ExpeditionOperation operation, ExpeditionRoute route, int participants, int alert,
                   ExpeditionStoryChoice direction);
    SpeciesType enemy(int index);
    long syncWindowMillis(ExpeditionOperation operation, ExpeditionRoute route, ExpeditionStoryChoice direction);
    ExpeditionPhase phaseAfterObjective();
    Material clueMaterial(ExpeditionOperation operation, int index);
    String clueName(ExpeditionOperation operation, int index);
    Material objectiveMaterial(ExpeditionOperation operation, int index);
    String objectiveName(ExpeditionOperation operation, int index);
    String investigationInstruction();
    String extractionInstruction();

    default String directionTradeoff(ExpeditionStoryChoice direction) {
        if (direction == ExpeditionStoryChoice.SECURE) {
            return "前章主張使情報需求與接敵量降低，但同步窗口縮短 3 秒。";
        }
        if (direction == ExpeditionStoryChoice.CONNECT) {
            return "前章主張保留更多往來線索，同步窗口延長 3 秒，但會多暴露一個威脅。";
        }
        return "尚無前章方向；本次使用區域標準條件。";
    }
}
