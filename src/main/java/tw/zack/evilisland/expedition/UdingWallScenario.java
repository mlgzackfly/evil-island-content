package tw.zack.evilisland.expedition;

import tw.zack.evilisland.model.ExplorationSite;

final class UdingWallScenario extends AbstractExpeditionScenario {
    UdingWallScenario() { super(ExplorationSite.UDING_WALL); }

    @Override public String investigationInstruction() {
        return "依高差完成風向、崖釘與遠端觀測校正；三點讀數共同決定安全線。";
    }
    @Override public String extractionInstruction() {
        return "犬戎巡獵已被引開，沿完成校正的崖壁傳訊線撤回。";
    }
}
