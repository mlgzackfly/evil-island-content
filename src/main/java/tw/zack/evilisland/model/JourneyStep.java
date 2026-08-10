package tw.zack.evilisland.model;

public enum JourneyStep {
    AWAKEN_QI("炁息初醒", "前往新城聚炁鏡庭，完成炁息測定與不可逆的炁訣定型。"),
    CLAIM_WEAPON("兵器認主", "前往新城東門向撼山報到，從軍械頁領取一把制式兵器。"),
    COMPLETE_PATROL("第一次出勤", "再次與撼山互動，完成一份單人巡防；NPC 會補足隊伍位置。"),
    REACH_CAMP("走出城門", "前往東境補給路營地，直接與營地管事互動。"),
    START_EXPEDITION("編組遠征", "從營地的深入遠征公告選路、整備，再以單人無跡或雙人編組出發。"),
    REVIEW_WITHDRAWAL("學會止損", "遠征中潛行使用指令牌，查看目前撤離能保留的成果，再選擇繼續。"),
    COMPLETE_EXPEDITION("帶回結果", "完成遠征並回到撤離信標；失利時主動撤退也比拖到全滅更好。"),
    MAINLINE("加入新城遠路", "個人整備已完成；依本週主線選擇巡防、建設、交涉或遠征。" );

    private final String display;
    private final String objective;

    JourneyStep(String display, String objective) {
        this.display = display;
        this.objective = objective;
    }

    public String display() { return display; }
    public String objective() { return objective; }
}
