package tw.zack.evilisland.expedition;

import tw.zack.evilisland.model.ExpeditionOperation;
import tw.zack.evilisland.model.ExpeditionPhase;
import tw.zack.evilisland.model.ExplorationSite;

final class RongxuApproachScenario extends AbstractExpeditionScenario {
    RongxuApproachScenario() { super(ExplorationSite.RONGXU_APPROACH); }

    @Override public boolean combatRequired() { return false; }
    @Override public ExpeditionPhase phaseAfterObjective() { return ExpeditionPhase.EXTRACTION; }
    @Override public int enemyCount(ExpeditionOperation operation, tw.zack.evilisland.model.ExpeditionRoute route,
                                    int participants, int alert,
                                    tw.zack.evilisland.model.ExpeditionStoryChoice direction) { return 0; }
    @Override public String investigationInstruction() {
        return "辨識毛族邊界布記、方向牌與使者留言；錯把防衛標記當敵意會破壞默契。";
    }
    @Override public String extractionInstruction() {
        return "會合記號已完成，不進入地下領域，也不生成清剿戰；循外緣原路返回。";
    }
}
