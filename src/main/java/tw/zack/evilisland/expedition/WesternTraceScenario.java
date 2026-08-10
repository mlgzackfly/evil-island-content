package tw.zack.evilisland.expedition;

import tw.zack.evilisland.model.ExplorationSite;

final class WesternTraceScenario extends AbstractExpeditionScenario {
    WesternTraceScenario() { super(ExplorationSite.WESTERN_TRACE); }

    @Override public String investigationInstruction() {
        return "三份遺跡證據只能帶回足夠完成判讀的部分；選樣順序會改變成果價值。";
    }
    @Override public String extractionInstruction() {
        return "封好主樣本與攜行箱，在犬戎下一輪巡獵抵達前撤回。";
    }
}
