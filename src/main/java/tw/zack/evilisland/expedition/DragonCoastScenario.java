package tw.zack.evilisland.expedition;

import tw.zack.evilisland.model.ExpeditionOperation;
import tw.zack.evilisland.model.ExplorationSite;

final class DragonCoastScenario extends AbstractExpeditionScenario {
    DragonCoastScenario() { super(ExplorationSite.DRAGON_COAST); }

    @Override public boolean timedExtraction(ExpeditionOperation operation) { return true; }
    @Override public String investigationInstruction() {
        return "比對潮位晶屑、退潮水草與掠空殘膜，分辨海路變化和禺彊活動。";
    }
    @Override public String extractionInstruction() {
        return "潮路正在封閉；避開掠空視線，在倒數結束前返回海岸信標。";
    }
}
