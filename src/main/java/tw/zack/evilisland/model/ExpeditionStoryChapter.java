package tw.zack.evilisland.model;

import java.util.Arrays;
import java.util.List;

public enum ExpeditionStoryChapter {
    EAST_1(ExplorationSite.EASTERN_ROUTE, 1, "失聯名冊",
            "新城外送出的築城車隊沒有依期回營。終戰後道路突然空了下來，舊路標已無法代表現在的邊界。",
            List.of("車轍沒有朝舊戰區深入，而是在新城外圍來回試探。",
                    "散落的是築城材料，不像只為掠奪糧食而來。",
                    "同一塊路標被不同隊伍反覆改寫，沒有一方真正掌握這條路。"),
            List.of("封存失聯名冊", "重立外圍路標"),
            "暫停最危險的岔路，把有限人力集中到可守住的補給線。",
            "重新標出不指向軍營的共用路徑，讓誤入者有機會退出。",
            "新城收回外圍隊伍，補給線縮短，但每一段都有人負責。",
            "新的路標避開軍事據點，第一條可被不同旅隊讀懂的外路出現。"),
    EAST_2(ExplorationSite.EASTERN_ROUTE, 2, "兩份路引",
            "失聯事件留下兩套互相矛盾的路引。一套要求所有旅隊繞行，另一套卻通向尚未完成的交換點。",
            List.of("封閉標記旁仍有人留下乾淨水袋，顯示道路並未真正斷絕。",
                    "犬戎足跡只追到路界便折返，更像巡獵而非攻城前鋒。",
                    "人類斥候也曾越過自己畫下的界線，雙方都在試探新規矩。"),
            List.of("核對軍用路引", "標定中立歇腳點"),
            "保留單一軍用路引，其餘標記全部撤除。",
            "把交換點移出補給主線，留下能被遠方旅隊辨認的歇腳處。",
            "東境路網改由軍團統一編號，誤入補給線的機會明顯降低。",
            "補給線外多了一處不存放軍資的歇腳點，陌生足跡開始在界外停留。"),
    EAST_3(ExplorationSite.EASTERN_ROUTE, 3, "新城外環",
            "三條外路終於能連成新城外環。最後的問題不是能不能通行，而是新城準備用何種態度面對遠方。",
            List.of("外環能避開居民區，也能在危急時迅速切斷。",
                    "不同族群留下的記號已能並列，不必互相覆蓋。",
                    "最遠的路標沒有指向任何城門，只寫明水源與危險地帶。"),
            List.of("啟用外環警戒", "啟用遠路指標"),
            "把外環定為防線，只有登記隊伍能接近新城。",
            "把外環定為遠路起點，保留不進城也能交換消息的空間。",
            "新城取得一圈可收可放的警戒外環，拓路先以居民安全為界。",
            "五方旅路有了不必進入新城的共同起點，遠方消息開始穩定回流。"),

    UDING_1(ExplorationSite.UDING_WALL, 1, "沉默風釘",
            "高原東壁的傳訊風釘接連失去回音。這裡是人類天然防區，也是犬戎巡獵會最先碰到的高界。",
            List.of("風釘並非被拔除，而是被轉向背風面，像有人刻意遮住訊號。",
                    "崖邊留有大型足跡，卻沒有向高原內側深入。",
                    "最舊的觀測痕仍可使用，只是人類已多年沒有維護。"),
            List.of("校正東壁風釘", "重啟高處觀測"),
            "將所有風釘轉為單向警報，不再向高原外回傳位置。",
            "保留一組公開風訊，讓巡獵者也能知道崖路何時封閉。",
            "東壁恢復安靜而可靠的警報線，任何越界都會先傳回營地。",
            "風釘開始同時回應內外兩側，崖路第一次有了共同避險訊號。"),
    UDING_2(ExplorationSite.UDING_WALL, 2, "越界足跡",
            "風訊恢復後，斥候發現人類與犬戎都曾越過彼此以為存在的界線。衝突來自兩張不同的高原地圖。",
            List.of("犬戎獵隊以氣味標界，人類繪圖卻只記岩脊，兩條界線並不重合。",
                    "一處短暫營火同時留有人類布條與犬戎骨飾，雙方曾在此避風。",
                    "真正危險的是崩裂岩層，卻被兩邊都誤當成對方設下的阻路。"),
            List.of("標定內側警戒線", "標定共用避風線"),
            "把人類活動收回可長期巡守的岩脊內，不再追逐每一道足跡。",
            "將天然崩裂帶標成共同禁入區，讓兩邊不必靠近也能讀懂。",
            "高原守軍放棄無法維持的遠界，警戒範圍縮小但更可信。",
            "崩裂帶成為第一條不屬於任何一方、卻被雙方遵守的高界。"),
    UDING_3(ExplorationSite.UDING_WALL, 3, "高原之眼",
            "東壁觀測已能覆蓋整段高界。最後要決定的是，它只是一雙監視外敵的眼睛，還是一座避免誤判的信號站。",
            List.of("三處觀測點能彼此驗證，單一假訊號不再足以調動守軍。",
                    "犬戎巡獵改走界外，仍會在暴風前留下低沉號聲。",
                    "新城的回訊已能越過高原，東壁不再是孤立前哨。"),
            List.of("完成高界警戒網", "完成跨界風訊站"),
            "讓三站只交換軍情，建立最快的高原預警。",
            "保留暴風與崩塌公開訊號，軍情另以內線傳遞。",
            "宇定東壁成為新城最可靠的高處警戒，守軍不再被單點假訊牽動。",
            "東壁同時傳遞危險與軍情，互不信任的巡獵者也能避開共同天災。"),

    RONGXU_1(ExplorationSite.RONGXU_APPROACH, 1, "被移動的界石",
            "絨須洞外的界石偏離了原位。毛族沒有派兵追擊，只關閉了幾條地下入口，等待人類先說明來意。",
            List.of("界石底部沒有挖掘痕，應是地表崩動後被旅隊誤搬。",
                    "毛族布記刻意面向外側，內容是在警告地下防衛區而非宣戰。",
                    "人類補給隊曾把界石當成路障移開，卻沒有人回報。"),
            List.of("復原外緣界石", "設立說明標記"),
            "把界石復原並讓人類隊伍退出所有不明洞口。",
            "在界石外加上人類可讀的說明，避免下一支隊伍再次誤搬。",
            "絨須外緣恢復原界，毛族重新開啟一條不通往核心區的洞路。",
            "雙方標記第一次並列在同一位置，旅隊知道何處應停、如何請求引導。"),
    RONGXU_2(ExplorationSite.RONGXU_APPROACH, 2, "失聯的回音",
            "一名負責傳話的毛族使者沒有抵達營地。洞外沒有戰鬥痕跡，只有被錯接到舊商旅線的回音管。",
            List.of("回音管仍在傳送訊息，只是出口被接到無人使用的舊路。",
                    "使者留下的短記反覆提醒不要攜帶火源靠近地下門。",
                    "舊商旅線上有人類糧包，顯示使者曾被巡隊照料而非扣留。"),
            List.of("封閉錯接回音管", "建立地表會合記號"),
            "停用所有未登記管線，改由營地逐一核對訊息。",
            "在安全地表設置不通往洞內的會合記號，讓使者不必暴露入口。",
            "錯誤回音被切斷，往後訊息較慢，但不會再把旅隊引向洞口。",
            "地表會合點開始運作，毛族不必開放地下防線也能與人類交換消息。"),
    RONGXU_3(ExplorationSite.RONGXU_APPROACH, 3, "洞外新約",
            "界線與傳話方式都已釐清。這不是替兩族締結永久盟約，而是一套能在互不干涉下持續工作的洞外規矩。",
            List.of("毛族願意修補地表防具，但拒絕讓陌生人進入地下工坊。",
                    "人類需要地下防衛情報，毛族更在意旅隊不要留下會引來強敵的痕跡。",
                    "雙方都接受先停步、再傳訊、最後由使者會合的次序。"),
            List.of("確認互不越界", "確認洞外協作"),
            "把互不進入核心領地寫成營地最高原則。",
            "在不開放地下技術的前提下，維持地表修補與危險通報。",
            "絨須洞外形成清楚的互不越界線，沉默不再立刻被視為敵意。",
            "洞外協作有了固定次序，有限互助不必以交出領地或技術為代價。"),

    WEST_1(ExplorationSite.WESTERN_TRACE, 1, "無主遺跡",
            "西方荒野散落著終戰前後留下的營地與斷牆。每個人都說那裡藏有重要物資，卻沒有人能說清遺跡原本屬於誰。",
            List.of("第一份陶片記的是水源，不是軍隊位置。",
                    "斷牆內的箭痕來自不同方向，這裡曾被多支隊伍輪流使用。",
                    "殘圖刻意抹去聚落，只保留能避開高道息區的路。"),
            List.of("封存危險遺物", "保存荒野路圖"),
            "只帶回能確認用途的物件，其餘原地封存。",
            "優先帶回路圖與水源記錄，不宣稱遺跡歸新城所有。",
            "荒野搜索改以危險分級，不再為未知遺物反覆投入隊伍。",
            "第一張不標領地、只標生路的荒野圖回到營地，遠征不再全靠傳聞。"),
    WEST_2(ExplorationSite.WESTERN_TRACE, 2, "被分開的地圖",
            "上一批證據證明荒野路圖被刻意拆開保存。三份樣本只能帶回兩份，選擇本身會決定新城看見何種西方。",
            List.of("北片記錄可守的岩口，適合建立警戒站。",
                    "中片標出曾有不同旅隊交換食水的空地。",
                    "南片指向更遠遺跡，但沿途缺乏可長期維持的補給。"),
            List.of("拼合防衛路圖", "拼合旅隊路圖"),
            "優先拼出可守地形，不追逐最遠的遺跡。",
            "優先拼出水源與歇腳處，接受地圖仍有空白。",
            "西方警戒圖完成，遠征隊知道哪些地方能守、哪些必須放棄。",
            "旅隊路圖完成，地圖保留未知領地，不把每一處空白都視為無主土地。"),
    WEST_3(ExplorationSite.WESTERN_TRACE, 3, "遠路的去向",
            "荒野圖已足以支持固定遠征。最後兩份樣本分別能完成前哨線與旅路線，新城只能先公開其中一種。",
            List.of("前哨線距離短、易補給，但會讓遠方族群看見人類正在擴張。",
                    "旅路線避開主要遺跡，交換消息較慢，卻不必占用任何舊營地。",
                    "兩條路都能返回新城，差別只在新城想先帶出去什麼。"),
            List.of("完成西方前哨線", "完成西方旅路線"),
            "以可撤回的小型前哨維持搜索，不建立永久占領。",
            "公開水源與危險地帶，讓遠路先成為消息通道。",
            "西方搜索有了可撤回的前哨節點，擴張被限制在能負責的範圍內。",
            "荒野出現一條不宣示領地的旅路，新城開始收到比遺物更有價值的遠方消息。"),

    DRAGON_1(ExplorationSite.DRAGON_COAST, 1, "逆潮浮標",
            "龍宮海岸的浮標在退潮時反向發光。虯龍秩序仍在，但岸外禺彊與人類觀測隊並不共享同一套海天訊號。",
            List.of("浮標沒有損壞，而是被海流帶離原本的觀測線。",
                    "禺彊羽膜落在外海側，顯示牠們沿潮線巡查而非靠近龍宮內域。",
                    "人類記錄只寫潮高，漏掉了空中風向，才會把正常巡飛當成突襲。"),
            List.of("重設近岸潮標", "增設海天對照標"),
            "把觀測收回近岸，先確保人類能在潮路封閉前撤離。",
            "讓浮標同時顯示潮高與風向，減少海面與空中的誤判。",
            "近岸潮標恢復可靠，觀測隊不再為追逐外海訊號錯過撤離。",
            "海天對照標開始運作，禺彊活動仍具威脅，卻不再每次都被誤報為進攻。"),
    DRAGON_2(ExplorationSite.DRAGON_COAST, 2, "掠空影跡",
            "海天記錄顯示部分禺彊刻意逼近觀測線，另一些卻只在高空跟隨海流。兩種影跡不能用同一份警報處理。",
            List.of("低空羽痕伴隨抓痕，確實有巡飛者攻擊浮標。",
                    "高空影跡始終停在潮線外，只記錄往返船隊。",
                    "一枚破損警示片同時刻有海浪與翼形，舊觀測者早已嘗試分類。"),
            List.of("分級空襲警報", "建立非敵對風訊"),
            "只對低空越線發出戰鬥警報，避免守軍被高空影跡耗盡。",
            "保留一組不含軍情的風訊，讓高空巡飛不必靠近浮標辨認天候。",
            "海岸警報能分辨監視與攻擊，鬥天支援只在真正越線時出動。",
            "公開風訊減少了部分逼近，敵意沒有消失，但雙方不必為天候情報交戰。"),
    DRAGON_3(ExplorationSite.DRAGON_COAST, 3, "海天信號",
            "潮標與空警已能互相驗證。最後一座信號站將決定龍宮海岸只是封閉邊界，還是跨領域往返前的安全門檻。",
            List.of("龍宮方向的秩序標記始終沒有改變，混亂主要發生在外海過渡帶。",
                    "禺彊影跡開始避開分級警報線，仍有少數掠空者故意試探。",
                    "人類船隊已能依海天信號自行判斷是否返航，不必等待遠方命令。"),
            List.of("完成海岸封控站", "完成海天通報站"),
            "讓信號站優先服務撤離與封控，不承擔對外聯絡。",
            "把天候、潮線與非軍事警示公開，軍情仍留在內部。",
            "龍宮海岸形成清楚的封控門檻，任何隊伍都知道何時必須退回。",
            "海天通報站成為跨領域往返前的共同安全節點，秩序不必等同完全隔絕。" );

    private final ExplorationSite site;
    private final int chapter;
    private final String title;
    private final String briefing;
    private final List<String> discoveries;
    private final List<String> objectives;
    private final String securePrompt;
    private final String connectPrompt;
    private final String secureResult;
    private final String connectResult;

    ExpeditionStoryChapter(ExplorationSite site, int chapter, String title, String briefing,
                           List<String> discoveries, List<String> objectives, String securePrompt,
                           String connectPrompt, String secureResult, String connectResult) {
        this.site = site;
        this.chapter = chapter;
        this.title = title;
        this.briefing = briefing;
        this.discoveries = List.copyOf(discoveries);
        this.objectives = List.copyOf(objectives);
        this.securePrompt = securePrompt;
        this.connectPrompt = connectPrompt;
        this.secureResult = secureResult;
        this.connectResult = connectResult;
    }

    public ExplorationSite site() { return site; }
    public int chapter() { return chapter; }
    public String title() { return title; }
    public String briefing() { return briefing; }
    public String discovery(int index) { return discoveries.get(Math.floorMod(index, discoveries.size())); }
    public String objective(int index) { return objectives.get(Math.floorMod(index, objectives.size())); }
    public String prompt(ExpeditionStoryChoice choice) {
        return choice == ExpeditionStoryChoice.SECURE ? securePrompt : connectPrompt;
    }
    public String result(ExpeditionStoryChoice choice) {
        return choice == ExpeditionStoryChoice.SECURE ? secureResult : connectResult;
    }

    public String briefingAfter(ExpeditionStoryChoice previous) {
        if (previous == null || chapter == 1) return briefing;
        return briefing + (previous == ExpeditionStoryChoice.SECURE
                ? " 上週營地先選擇穩住界線，本章將檢驗這條防線是否足夠可信。"
                : " 上週營地選擇保留往來，本章將檢驗這條通路能否承受新的試探。");
    }

    public static ExpeditionStoryChapter forSite(ExplorationSite site, int chapter) {
        int normalized = Math.max(1, Math.min(3, chapter));
        return Arrays.stream(values()).filter(value -> value.site == site && value.chapter == normalized)
                .findFirst().orElseThrow();
    }
}
