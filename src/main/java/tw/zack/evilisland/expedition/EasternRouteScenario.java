package tw.zack.evilisland.expedition;

import tw.zack.evilisland.model.ExpeditionOperation;
import tw.zack.evilisland.model.ExplorationSite;

final class EasternRouteScenario extends AbstractExpeditionScenario {
    EasternRouteScenario() { super(ExplorationSite.EASTERN_ROUTE); }

    @Override public boolean timedExtraction(ExpeditionOperation operation) {
        return operation == ExpeditionOperation.CASUALTY_EVACUATION;
    }
    @Override public String investigationInstruction() {
        return "核對車隊、封鎖或傷員留下的三處痕跡，假跡會提高後續警戒。";
    }
    @Override public String extractionInstruction() {
        return "沿補給線返回撤離信標；傷員撤運必須在支撐時間內回營。";
    }
}
