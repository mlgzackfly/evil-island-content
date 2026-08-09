# 《噩盡島》Minecraft 外掛重寫構想

本文件是已核對設定的實作基線，不改寫小說正史；細部數值仍需經遊玩測試調整。原作查證資料保留於本機私密環境，不納入版本控制。

## 一、遊戲年代與玩家身分

- 時間點採第二部尾聲後，東大陸首座新城開始建設的時期。
- 小說已發生事件維持不變：首代屍靈王是沈洛年、第二代是劉巧雯，李翰是其下線旱魃；終戰已結束。
- 玩家是參與新城拓荒、城防與外交的普通人類，不扮演沈洛年、十聖或既有人物。
- 沈洛年、十聖、龍族與仙獸只作歷史人物、導師、勢力代表或罕見事件 NPC。
- 新城期能同時使用歲安軍團、各種引仙兵種、魔法、光靈、山眠教、毛族科技與各妖族政治，又不必強迫玩家重演小說主線。

## 二、核心遊戲循環

1. 玩家在歲安前哨或東大陸新城接取巡邏、採集、護送、偵察、外交與城防任務。
2. 離開息壤安全區後，依道息濃度管理炁息，辨認遭遇妖族的身分與立場。
3. 以談判、退讓、交易、潛行或戰鬥解決事件；妖族不統一視為敵怪。
4. 對真正敵對妖物進行擊殺與煉化，取得妖質、材料、情報或勢力聲望。
5. 返回安全區修煉炁訣、進行易質、製作城防、研究咒術，或支援新城公共工程。
6. 全服共同面對犬戎圍城、禺彊空襲、鑿齒攻城、應龍索財、屍靈警訊等不同世界事件。

循環重點是「離城冒險，帶資源與情報回城，讓角色和城市一起成長」，不是反覆刷同一種妖潮。

## 三、角色成長模型

角色資料拆成獨立維度，不再使用舊版六選一職業。

| 維度 | 選擇／狀態 | 遊戲作用 |
|---|---|---|
| 炁息傾向 | 系統測得的發散、內聚 | 決定感應、外放、護體與近戰效率；不可選擇或修改 |
| 炁訣 | 爆、輕、柔、凝及相鄰混合 | 存想完成後永久定型，不是可切換的技能配置 |
| 轉仙 | 未轉仙、易質階段、引仙、罕見換靈 | 決定身體上限、環境依賴與特殊能力 |
| 傳承 | 道武、魔法、光靈、山眠、縛妖等 | 提供知識、術式、任務和組織關係 |
| 裝備職責 | 武器、城防、醫療、偵察、空戰等 | 由前述能力組合形成，不另設固定職業 |

### 初期角色流程

- 新角色先完成軍團體檢與炁息測定，由系統永久記錄先天的發散或內聚傾向。
- 玩家在炁息尚未定型時選擇純訣或相鄰雙修及比例；確認定型後不可重配，後續成長來自技法與運用熟練。
- 易質是普通玩家主成長線，需妖質容量、煉化材料、成功率與低道息副作用。
- 引仙由軍團兵種或長任務取得，與特定妖體不可逆綁定。
- 換靈不做日常抽獎，只保留給伺服器級劇情與唯一性角色事件。

## 四、第一版必做系統

### 道息環境

- 每個區域有基礎道息值，受世界、地形、事件及建築影響。
- 息壤磚形成排息場；聚炁息壤鏡形成局部聚息點。
- 炁息補充、仙妖強度、火藥有效性與易質者狀態都讀取同一套道息場。
- 以區塊快取與增量更新計算，不每 tick 掃描全部方塊。

### 炁息與四訣

- 炁息是戰鬥資源，道息是環境；兩者不可合成一條魔力值。
- 爆訣提供瞬間輸出與加速但耗炁、露出破綻；輕訣提高移動與出手；柔訣承接化力；凝訣強化正面攻防。
- 相鄰混合以比例配置，例如爆輕、輕柔、柔凝，不允許原著不存在的跨階混合。
- 技能效果走自訂傷害與速度計算，不用長時間藥水效果冒充完整系統。

### 妖質與易質

- 妖物死亡後留下可煉化來源，不直接掉落固定名稱「妖質瓶」。
- 煉化產生可追蹤來源、純度與容量的妖質資料。
- 易質具有身體容量、失敗、排斥、仙化進度及低道息衰弱。
- 完全仙化是長期目標與世界選擇，不只是滿級後永久加傷害。

### 勢力與事件

- 玩家至少有歲安軍團、人類新城、犬戎、毛族、納金族、虯龍等聲望。
- 每個事件有目的、交涉條件、撤退條件和戰鬥 AI，沒有通用「妖潮」事件。
- 第一批事件先做鑿齒／刑天地面攻城、犬戎巡獵與禺彊空襲，驗證三種完全不同的戰法。

### 輕疾

- 提供登記名、私訊、群組、翻譯、任務通知和遠距情報。
- 玩家必須持有輕疾泥偶或位於可用終端附近；進階操作消耗炁息或妖炁。
- 介面可使用 Adventure 聊天元件，但世界觀中仍是輕疾分身，不是手機選單。

## 五、技術架構

正式版拆除單一 `EvilIslandPlugin.java`，採服務、領域物件和事件監聽器分層。

```text
tw.zack.evilisland
├── EvilIslandPlugin
├── bootstrap/       設定載入、服務組裝、資料遷移
├── profile/         玩家資料、傾向、炁訣、仙化進度
├── dao/             道息場、息壤、聚炁鏡、區塊快取
├── qi/              炁息、四訣、消耗、恢復與戰鬥計算
├── yaozhi/          妖質來源、煉化、易質與失敗結果
├── faction/         聲望、契約、領地與外交條件
├── encounter/       世界事件排程與勝敗狀態機
├── living/          區域危機導演、跨輪事件記憶與城市通報
├── species/         犬戎、鑿齒、刑天、禺彊等獨立行為
├── qingji/          輕疾通訊與名稱登記
├── item/            PDC 道具識別、配方與能力
├── command/         玩家及管理指令
├── persistence/     SQLite repository、schema migration
└── api/             供任務或其他外掛使用的公開事件
```

### 資料原則

- SQLite 保存玩家、NPC 輪值、勢力、契約、城防與事件狀態；伺服器重啟後必須完整恢復。
- Bukkit PDC 只保存物品識別與必要實例資料，不把玩家完整進度塞進 PDC。
- YAML 保存物種、技能、物品與事件定義；啟動時做 schema 驗證，錯誤設定直接指出檔案與欄位。
- 主執行緒只碰 Bukkit API；資料庫與大型計算非同步執行，結果再切回主執行緒套用。
- 所有週期任務由單一 scheduler registry 管理，停服時可完整取消。

### 建議資料表

```text
player_profile(uuid, name, tendency, primary_formula, secondary_formula,
               qi, qi_capacity, transformation_stage, updated_at)
player_reputation(uuid, faction_id, value)
player_technique(uuid, technique_id, mastery)
world_region(world, chunk_x, chunk_z, base_dao, structure_modifier)
faction_contract(id, faction_a, faction_b, terms_json, expires_at)
encounter_instance(id, type, state, world, anchor, payload_json)
npc_roster(role, fatigue, injured_until, updated_at)
campaign_state(cycle, week, day, defense, supply, intelligence, morale,
               weekly_resolved, weekly_strategy, fortify_points, provision_points, recon_points)
development_state(cycle, last_ending, updated_at)
development_resource(resource, amount)
city_project(project, level)
faction_relation(faction, reputation)
exploration_site(site, discovered_cycle)
event_chain(chain, progress)
player_weapon_mastery(player_uuid, weapon, mastery, technique)
cycle_history(cycle, ending, summary, completed_at)
city_route(cycle, route, chosen_at)
construction_plot(project, world, x, y, z, rotation, level, status)
construction_block(project, world, x, y, z, original_data, placed_data)
faction_contract(cycle, faction, progress, resolution, state, updated_at)
faction_stock(faction, week, remaining)
player_faction_credit(player_uuid, faction, week, amount, updated_at)
mission_telemetry(id, mission_type, players, started_at, completed_at, result, failure_reason, payload)
player_activity(player_uuid, last_seen, last_catchup_cycle)
acceptance_run(id, state, world, center, checks, summary, started_at, updated_at)
acceptance_block(run_id, world, x, y, z, original_data, placed_data)
player_growth(player_uuid, rejection, updated_at)
player_essence_source(player_uuid, source, amount, purity_points, updated_at)
player_inheritance(player_uuid, inheritance, progress, completed, attuned, updated_at)
city_project_condition(project, condition, updated_at)
living_event(id, type, state, approach, cycle, week, day, started_epoch_day,
             expires_epoch_day, participants, created_at, resolved_at, updated_at)
crisis_scene(event_id, type, state, world, x, y, z, updated_at)
crisis_scene_block(event_id, world, x, y, z, original_data, active_data,
                   resolved_data, expired_data, placed_data)
supply_route(event_id, state, dispatcher, receiver, departed_at, arrives_at, updated_at)
resident_intel(event_id, resident, reporter, collected_at)
schema_version(version, applied_at)
```

## 六、指令與介面

- `/evil status`：查看傾向、炁訣比例、炁息、易質與所在地道息。
- 兵器研習、公共工程、探索情報、連續事件與勢力交涉均由撼山巡防公告進入。
- 輕疾名稱、私訊與翻譯仍屬後續內容，不先增加玩家指令。
- `/evil admin region|encounter|profile`：管理區域、事件與測試資料。

常用戰鬥能力以按鍵組合、物品姿態和互動觸發；指令主要用於查詢與管理，不讓玩家在戰鬥中輸入長指令施法。

## 七、開發切分

### 里程碑 1：可玩的生存核心

- 建立新專案骨架、SQLite migration 與玩家 profile。
- 完成區域道息、息壤磚、聚炁鏡、炁息消耗／恢復。
- 完成發散／內聚與爆、輕、柔、凝單訣。
- 做一個鑿齒／刑天攻城事件及死亡、重登、重啟恢復測試。

### 里程碑 2：成長與城市

- 妖質煉化、易質階段、失敗與低道息副作用。
- 歲安軍團任務、聲望、城防工程與輕疾通訊。
- 加入相鄰雙訣和第一批原著道具。

### 里程碑 3：多勢力世界

- 犬戎、禺彊、毛族、納金族與虯龍的獨立事件和外交。
- 引仙兵種：撼山、揚武、無跡、鬥天。
- 伺服器級契約、領地與新城建設進度。

### 里程碑 4：高階傳承

- 應龍魔法、圓足光靈、山眠祖靈與羅宗／昌宗縛妖術。
- 罕見換靈、天仙級 NPC、屍靈警訊與大型終局事件。
- 資源包、音效與更完整的客製模型最後加入，不阻塞核心規則驗證。

## 八、第一個垂直切片

第一個可交付版本只做一段完整流程：玩家在新城報到，由系統測定先天炁息傾向，選擇並永久定型純訣或相鄰雙修路線，向撼山 NPC 接受巡防，遭遇一小隊鑿齒，在 NPC 協助下戰鬥，煉化一份妖質，返回息壤城後因排息感到炁息下降，最後在聚炁鏡旁完成第一次易質。

這段流程能同時驗證環境、戰鬥、成長、NPC、資料保存和原著語意。完成後再擴物種與傳承，風險最低。
