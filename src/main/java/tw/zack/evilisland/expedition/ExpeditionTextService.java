package tw.zack.evilisland.expedition;

import tw.zack.evilisland.model.ExpeditionOperation;
import tw.zack.evilisland.model.ExpeditionPhase;
import tw.zack.evilisland.model.ExpeditionRoute;
import tw.zack.evilisland.model.ExplorationSite;

public final class ExpeditionTextService {
    public String stageInstruction(ExpeditionScenario scenario, ExpeditionPhase phase, ExpeditionRoute route,
                                   boolean solo, int enemiesRemaining) {
        return switch (phase) {
            case APPROACH -> "沿" + scenario.routeDisplay(route) + "確認路標，並處理兩段途中狀況。";
            case INVESTIGATING -> scenario.investigationInstruction();
            case OBJECTIVE -> solo
                    ? "兩處目標必須同步；操作一處後，以指令牌命令無跡執行另一處。"
                    : "兩名隊員分頭就位，各自操作一處目標。";
            case ESCALATION -> scenario.combatRequired()
                    ? "同步行動驚動敵軍，清除 " + enemiesRemaining + " 個威脅。"
                    : "遵守邊界默契，不與毛族交戰。";
            case EXTRACTION -> scenario.extractionInstruction();
            default -> phase.display();
        };
    }

    public String progress(ExpeditionScenario scenario, ExpeditionPhase phase, ExpeditionOperation operation,
                           ExplorationSite site, int approachMask, int eventMask, int validClues,
                           int requiredClues, int objectiveMask, int enemiesRemaining, long deadline, long now) {
        return switch (phase) {
            case APPROACH -> Integer.bitCount(approachMask) + "/2 路標｜"
                    + Integer.bitCount(eventMask) + "/2 途中狀況";
            case INVESTIGATING -> validClues + "/" + requiredClues + " 情報";
            case OBJECTIVE -> Integer.bitCount(objectiveMask) + "/2 同步目標";
            case ESCALATION -> enemiesRemaining + " 個威脅";
            case EXTRACTION -> scenario.timedExtraction(operation) && deadline > 0L
                    ? (site == ExplorationSite.DRAGON_COAST ? "潮路剩餘 " : "傷員可支撐 ")
                    + Math.max(0L, (deadline - now) / 1_000L) + " 秒" : "返回撤離信標";
            default -> phase.display();
        };
    }
}
