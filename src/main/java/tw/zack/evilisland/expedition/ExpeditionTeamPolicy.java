package tw.zack.evilisland.expedition;

import tw.zack.evilisland.model.ExpeditionDirector;

public final class ExpeditionTeamPolicy {
    public String rejection(boolean enlisted, boolean hasWeapon, boolean inExpedition, boolean inPatrol) {
        if (!enlisted) return "必須先完成角色測定與炁訣定型。";
        if (!hasWeapon) return "必須攜帶已認主的兵器。";
        if (inExpedition) return "你已有一場未完成的遠征。";
        if (inPatrol) return "必須先完成目前巡防。";
        return null;
    }

    public boolean validLoadout(int kitMask, int participants) {
        return ExpeditionDirector.validLoadout(kitMask, participants);
    }
}
