import Phaser from "phaser";
import { writeSave } from "./save";
import type { Formula, HudState, QiPath, SaveData, TouchState } from "./types";
import type { GameUI } from "./ui";

const WORLD_WIDTH = 2400;
const WORLD_HEIGHT = 1400;
const CITY_EDGE = 650;
const PLAYER_START = { x: 330, y: 700 };

interface FormulaStats {
  cost: number;
  damage: number;
  range: number;
  cooldown: number;
  color: number;
}

const FORMULAS: Record<Formula, FormulaStats> = {
  bao: { cost: 24, damage: 38, range: 150, cooldown: 820, color: 0xc94f39 },
  qing: { cost: 9, damage: 17, range: 108, cooldown: 260, color: 0x77b7a4 },
  rou: { cost: 14, damage: 20, range: 118, cooldown: 470, color: 0x5c89a4 },
  ning: { cost: 20, damage: 30, range: 132, cooldown: 720, color: 0xd6aa58 },
};

interface EnemyStats {
  kind: "zaochi" | "xingtian";
  hp: number;
  maxHp: number;
  damage: number;
  speed: number;
  lastStrike: number;
}

export class PatrolScene extends Phaser.Scene {
  private player!: Phaser.Physics.Arcade.Sprite;
  private guard!: Phaser.Physics.Arcade.Sprite;
  private enemies!: Phaser.Physics.Arcade.Group;
  private remains!: Phaser.Physics.Arcade.Group;
  private keys!: Record<string, Phaser.Input.Keyboard.Key>;
  private health = 100;
  private qi = 100;
  private carriedRemains = 0;
  private formula: Formula = "bao";
  private lastAttack = 0;
  private lastDash = 0;
  private guardUntil = 0;
  private shieldUntil = 0;
  private lastFacing = new Phaser.Math.Vector2(1, 0);
  private currentDao = 12;
  private currentRegion = "息壤城區";
  private gameStarted = false;
  private dead = false;
  private nextHudUpdate = 0;
  private guardAttackAt = 0;
  private worldBars!: Phaser.GameObjects.Graphics;
  private mirror!: Phaser.GameObjects.Arc;
  private elite: Phaser.Physics.Arcade.Sprite | null = null;

  constructor(
    private readonly ui: GameUI,
    private readonly save: SaveData,
    private readonly touch: TouchState,
  ) {
    super("patrol");
  }

  preload(): void {
    this.load.svg("player", "/assets/player.svg", { width: 48, height: 48 });
    this.load.svg("heshan", "/assets/heshan.svg", { width: 56, height: 56 });
    this.load.svg("zaochi", "/assets/zaochi.svg", { width: 52, height: 52 });
    this.load.svg("xingtian", "/assets/xingtian.svg", { width: 72, height: 72 });
  }

  create(): void {
    this.physics.world.setBounds(0, 0, WORLD_WIDTH, WORLD_HEIGHT);
    this.cameras.main.setBounds(0, 0, WORLD_WIDTH, WORLD_HEIGHT);
    this.drawWorld();
    this.createGeneratedTextures();

    this.enemies = this.physics.add.group();
    this.remains = this.physics.add.group();
    this.player = this.physics.add.sprite(PLAYER_START.x, PLAYER_START.y, "player").setDepth(8);
    this.player.setCircle(15, 9, 10).setCollideWorldBounds(true);
    this.guard = this.physics.add.sprite(585, 700, "heshan").setDepth(7).setImmovable(true);
    this.guard.setData("name", "撼山隊員");

    this.createWorldObjects();
    this.worldBars = this.add.graphics().setDepth(9);
    this.spawnInitialEnemies();
    this.physics.add.overlap(this.player, this.enemies, (_player, enemy) => {
      this.handleEnemyContact(enemy as Phaser.Physics.Arcade.Sprite);
    });
    this.physics.add.overlap(this.player, this.remains, (_player, remain) => {
      this.collectRemain(remain as Phaser.Physics.Arcade.Sprite);
    });

    const keyboard = this.input.keyboard;
    if (!keyboard) throw new Error("Keyboard input unavailable");
    this.keys = keyboard.addKeys("W,A,S,D,J,K,F,SPACE,ONE,TWO,THREE,FOUR,Q,E") as Record<string, Phaser.Input.Keyboard.Key>;
    this.cameras.main.startFollow(this.player, true, 0.09, 0.09);
    this.cameras.main.setZoom(1.05);

    this.health = this.save.maxHealth;
    this.qi = this.save.maxQi;
    this.gameStarted = this.save.path !== null;
    if (this.save.transformations > 0 && !this.save.eliteDefeated) this.spawnElite();
    this.ui.log("東門外偵測到鑿齒活動", "軍情");
    this.ui.log("撼山小隊已在城門外接應");
    this.pushHud();
  }

  update(time: number, delta: number): void {
    if (!this.player || this.dead || !this.gameStarted) return;
    this.updateEnvironment();
    this.updatePlayer(time, delta);
    this.updateEnemies(time, delta);
    this.updateGuard(time);
    this.drawWorldBars();

    if (time > this.nextHudUpdate) {
      this.pushHud();
      this.nextHudUpdate = time + 80;
    }
  }

  choosePath(path: QiPath): void {
    if (this.save.path) return;
    this.save.path = path;
    if (path === "outward") {
      this.save.maxHealth = 100;
      this.save.maxQi = 120;
      this.ui.log("測定為發散型，外炁感應範圍提升", "測定");
    } else {
      this.save.maxHealth = 120;
      this.save.maxQi = 110;
      this.ui.log("測定為內聚型，護體與容量提升", "測定");
    }
    this.health = this.save.maxHealth;
    this.qi = this.save.maxQi;
    this.gameStarted = true;
    this.ui.finishOrientation();
    this.persist();
    this.pushHud();
  }

  selectFormula(formula: Formula): void {
    if (!this.gameStarted || this.dead || formula === this.formula) return;
    this.formula = formula;
    this.ui.log(`存想切換為${this.formulaName(formula)}訣`);
    this.pushHud();
  }

  respawn(): void {
    if (!this.dead) return;
    this.dead = false;
    this.carriedRemains = 0;
    this.health = this.save.maxHealth;
    this.qi = this.save.maxQi;
    this.player.enableBody(true, PLAYER_START.x, PLAYER_START.y, true, true);
    this.player.setAlpha(1);
    this.ui.hideDeath();
    this.ui.log("已由撼山隊送返東門", "軍團");
    this.pushHud();
  }

  private drawWorld(): void {
    const terrain = this.add.graphics();
    terrain.fillStyle(0x1d2923).fillRect(0, 0, WORLD_WIDTH, WORLD_HEIGHT);
    terrain.fillStyle(0x28362c).fillRect(CITY_EDGE, 0, 760, WORLD_HEIGHT);
    terrain.fillStyle(0x273027).fillRect(1410, 0, WORLD_WIDTH - 1410, WORLD_HEIGHT);

    const random = new Phaser.Math.RandomDataGenerator(["new-city-east-53"]);
    for (let i = 0; i < 170; i += 1) {
      const x = random.between(680, WORLD_WIDTH - 30);
      const y = random.between(30, WORLD_HEIGHT - 30);
      const wild = x > 1400;
      terrain.fillStyle(wild ? 0x384234 : 0x34453a, random.realInRange(0.35, 0.7));
      terrain.fillCircle(x, y, random.between(7, 24));
    }

    terrain.fillStyle(0x343c35).fillRect(0, 610, CITY_EDGE, 180);
    terrain.fillStyle(0x73705e).fillRect(0, 675, CITY_EDGE + 120, 48);
    terrain.fillStyle(0x4b4e43).fillRect(0, 683, CITY_EDGE + 120, 32);
    terrain.lineStyle(3, 0xa39b7d, 0.7).lineBetween(0, 683, CITY_EDGE + 120, 683);
    terrain.lineStyle(3, 0x242d27, 0.9).lineBetween(0, 715, CITY_EDGE + 120, 715);

    terrain.fillStyle(0x8c8062).fillRect(0, 0, CITY_EDGE - 20, 42);
    terrain.fillRect(0, WORLD_HEIGHT - 42, CITY_EDGE - 20, 42);
    terrain.fillRect(605, 0, 38, 595);
    terrain.fillRect(605, 805, 38, WORLD_HEIGHT - 805);
    terrain.fillStyle(0x5c5747).fillRect(605, 595, 38, 90);
    terrain.fillRect(605, 715, 38, 90);

    terrain.lineStyle(2, 0x73aa97, 0.45);
    for (let x = 90; x < CITY_EDGE; x += 105) terrain.strokeCircle(x, 700, 30);
    terrain.lineStyle(2, 0xd1ad62, 0.22).strokeCircle(1760, 720, 220);

    this.add.text(78, 104, "東大陸首座新城", {
      fontFamily: "serif", fontSize: "26px", color: "#d7d0ba",
    }).setDepth(1);
    this.add.text(80, 139, "息壤排息區", {
      fontFamily: "sans-serif", fontSize: "12px", color: "#8d978e",
    }).setDepth(1);
    this.add.text(720, 585, "東門巡防線", {
      fontFamily: "serif", fontSize: "18px", color: "#c6c4b3",
    }).setDepth(1);
    this.add.text(1480, 545, "高道息荒原", {
      fontFamily: "serif", fontSize: "22px", color: "#d6b769",
    }).setDepth(1);
  }

  private createGeneratedTextures(): void {
    const graphics = this.make.graphics({ x: 0, y: 0 });
    graphics.fillStyle(0xd3a953).fillCircle(9, 9, 7);
    graphics.lineStyle(2, 0xf2dc9b).strokeCircle(9, 9, 7);
    graphics.generateTexture("remain", 18, 18);
    graphics.clear();
    graphics.fillStyle(0x6c9f8d, 0.18).fillCircle(28, 28, 27);
    graphics.lineStyle(3, 0x72c1a9, 0.8).strokeCircle(28, 28, 24);
    graphics.generateTexture("mirror-aura", 56, 56);
    graphics.destroy();
  }

  private createWorldObjects(): void {
    this.mirror = this.add.circle(420, 520, 68, 0x58a78f, 0.11).setStrokeStyle(3, 0x6dc0a7, 0.7).setDepth(2);
    this.tweens.add({ targets: this.mirror, scale: 1.12, alpha: 0.55, duration: 1700, yoyo: true, repeat: -1 });
    this.add.image(420, 520, "mirror-aura").setScale(1.4).setDepth(3);
    this.add.text(372, 438, "聚炁鏡", { fontFamily: "serif", fontSize: "16px", color: "#9bd0bf" }).setDepth(4);

    const base = this.add.rectangle(348, 698, 74, 54, 0x544b3e).setStrokeStyle(2, 0xb19b70);
    const basin = this.add.ellipse(348, 682, 54, 22, 0x202b25).setStrokeStyle(2, 0xd2a852);
    const label = this.add.text(310, 744, "煉化臺", { fontFamily: "serif", fontSize: "14px", color: "#c8b98d" });
    this.add.container(0, 0, [base, basin, label]).setDepth(3);

    this.add.text(548, 635, "撼山", { fontFamily: "sans-serif", fontSize: "11px", color: "#acc4af" }).setDepth(9);
  }

  private spawnInitialEnemies(): void {
    const positions = [
      [865, 560], [940, 820], [1110, 650], [1280, 910],
      [1510, 690], [1660, 460], [1900, 850], [2140, 620],
    ];
    positions.forEach(([x, y]) => this.spawnEnemy("zaochi", x, y));
  }

  private spawnEnemy(kind: EnemyStats["kind"], x: number, y: number): Phaser.Physics.Arcade.Sprite {
    const sprite = this.enemies.create(x, y, kind) as Phaser.Physics.Arcade.Sprite;
    const elite = kind === "xingtian";
    const stats: EnemyStats = {
      kind,
      hp: elite ? 340 : 72,
      maxHp: elite ? 340 : 72,
      damage: elite ? 24 : 11,
      speed: elite ? 74 : 64,
      lastStrike: 0,
    };
    sprite.setData("stats", stats).setDepth(elite ? 7 : 6).setCollideWorldBounds(true);
    sprite.setCircle(elite ? 25 : 18, elite ? 11 : 8, elite ? 18 : 12);
    if (elite) {
      sprite.setScale(1.15);
      this.add.text(x - 44, y - 66, "刑天統領", { fontFamily: "serif", fontSize: "16px", color: "#e3b45d" }).setName("elite-label").setDepth(9);
    }
    this.tweens.add({ targets: sprite, y: y + (elite ? 7 : 4), duration: elite ? 950 : 1250, yoyo: true, repeat: -1 });
    return sprite;
  }

  private spawnElite(): void {
    if (this.elite || this.save.eliteDefeated) return;
    this.elite = this.spawnEnemy("xingtian", 1780, 720);
    this.ui.log("刑天統領自荒原逼近東門", "警報");
    this.cameras.main.flash(350, 120, 42, 30, false);
  }

  private updateEnvironment(): void {
    const x = this.player.x;
    const nearMirror = Phaser.Math.Distance.Between(this.player.x, this.player.y, 420, 520) < 105;
    if (nearMirror) {
      this.currentDao = 67;
      this.currentRegion = "聚炁鏡場";
    } else if (x < CITY_EDGE) {
      this.currentDao = 12;
      this.currentRegion = "息壤城區";
    } else if (x < 1410) {
      this.currentDao = 43;
      this.currentRegion = "東門緩衝帶";
    } else {
      this.currentDao = 82;
      this.currentRegion = "高道息荒原";
    }
  }

  private updatePlayer(time: number, delta: number): void {
    const cursors = this.input.keyboard?.createCursorKeys();
    const direction = new Phaser.Math.Vector2(
      (this.keys.D.isDown || cursors?.right.isDown || this.touch.right ? 1 : 0) -
      (this.keys.A.isDown || cursors?.left.isDown || this.touch.left ? 1 : 0),
      (this.keys.S.isDown || cursors?.down.isDown || this.touch.down ? 1 : 0) -
      (this.keys.W.isDown || cursors?.up.isDown || this.touch.up ? 1 : 0),
    );
    if (direction.lengthSq() > 0) {
      direction.normalize();
      this.lastFacing.copy(direction);
    }

    const pathModifier = this.save.path === "inward" ? 0.96 : 1;
    const formulaSpeed = this.formula === "qing" ? 1.28 : this.formula === "ning" ? 0.8 : 1;
    this.player.setVelocity(direction.x * 175 * pathModifier * formulaSpeed, direction.y * 175 * pathModifier * formulaSpeed);

    const regenPerSecond = 1.3 + this.currentDao * 0.072;
    this.qi = Math.min(this.save.maxQi, this.qi + regenPerSecond * delta / 1000);

    if (Phaser.Input.Keyboard.JustDown(this.keys.ONE)) this.selectFormula("bao");
    if (Phaser.Input.Keyboard.JustDown(this.keys.TWO)) this.selectFormula("qing");
    if (Phaser.Input.Keyboard.JustDown(this.keys.THREE)) this.selectFormula("rou");
    if (Phaser.Input.Keyboard.JustDown(this.keys.FOUR)) this.selectFormula("ning");
    if (Phaser.Input.Keyboard.JustDown(this.keys.Q)) this.cycleFormula(-1);
    if (Phaser.Input.Keyboard.JustDown(this.keys.E)) this.cycleFormula(1);

    const attackPressed = Phaser.Input.Keyboard.JustDown(this.keys.J) || Phaser.Input.Keyboard.JustDown(this.keys.SPACE) || this.touch.attack;
    const dashPressed = Phaser.Input.Keyboard.JustDown(this.keys.K) || this.touch.dash;
    const interactPressed = Phaser.Input.Keyboard.JustDown(this.keys.F) || this.touch.interact;
    if (attackPressed) this.attack(time);
    if (dashPressed) this.dash(time);
    if (interactPressed) this.interact();
    this.touch.attack = false;
    this.touch.dash = false;
    this.touch.interact = false;

    if (this.player.x < CITY_EDGE && this.save.transformations > 0 && this.currentDao < 20) {
      this.health = Math.max(1, this.health - 0.8 * delta / 1000);
    }
  }

  private attack(time: number): void {
    const stats = FORMULAS[this.formula];
    if (time - this.lastAttack < stats.cooldown || this.qi < stats.cost) return;
    this.lastAttack = time;
    this.qi -= stats.cost;
    const rangeBonus = this.save.path === "outward" ? 1.18 : 1;
    const range = stats.range * rangeBonus;
    const centerX = this.player.x + this.lastFacing.x * range * 0.42;
    const centerY = this.player.y + this.lastFacing.y * range * 0.42;
    const effect = this.add.circle(centerX, centerY, range * 0.48, stats.color, 0.22)
      .setStrokeStyle(3, stats.color, 0.85).setDepth(5);
    this.tweens.add({ targets: effect, scale: 1.5, alpha: 0, duration: 220, onComplete: () => effect.destroy() });

    if (this.formula === "rou") this.guardUntil = time + 720;
    if (this.formula === "ning") this.shieldUntil = time + 1050;

    const targets = this.enemies.getChildren() as Phaser.Physics.Arcade.Sprite[];
    for (const enemy of targets) {
      if (!enemy.active) continue;
      const toEnemy = new Phaser.Math.Vector2(enemy.x - this.player.x, enemy.y - this.player.y);
      const distance = toEnemy.length();
      const forward = distance === 0 ? 1 : toEnemy.normalize().dot(this.lastFacing);
      const fullSweep = this.formula === "bao" || this.formula === "rou";
      if (distance <= range && (fullSweep || forward > -0.05)) {
        const pathDamage = this.save.path === "inward" ? 1.08 : 1;
        this.damageEnemy(enemy, Math.round(stats.damage * pathDamage), this.lastFacing, this.formula === "bao" ? 180 : 90);
      }
    }
    this.cameras.main.shake(this.formula === "bao" ? 80 : 45, this.formula === "bao" ? 0.004 : 0.002);
  }

  private dash(time: number): void {
    const cooldown = this.formula === "qing" ? 520 : 1100;
    const cost = this.formula === "qing" ? 8 : 14;
    if (time - this.lastDash < cooldown || this.qi < cost) return;
    this.lastDash = time;
    this.qi -= cost;
    const distance = this.formula === "qing" ? 145 : 92;
    this.player.x = Phaser.Math.Clamp(this.player.x + this.lastFacing.x * distance, 20, WORLD_WIDTH - 20);
    this.player.y = Phaser.Math.Clamp(this.player.y + this.lastFacing.y * distance, 20, WORLD_HEIGHT - 20);
    this.player.setAlpha(0.5);
    this.time.delayedCall(90, () => this.player.setAlpha(1));
  }

  private interact(): void {
    const atRefinery = Phaser.Math.Distance.Between(this.player.x, this.player.y, 348, 698) < 92;
    if (!atRefinery) return;
    if (this.carriedRemains > 0) {
      const amount = this.carriedRemains;
      this.carriedRemains = 0;
      this.save.essence += amount;
      this.ui.log(`煉化 ${amount} 份妖物遺骸，取得等量妖質`, "煉化");
      this.persist();
      this.pushHud();
      return;
    }
    if (this.save.transformations === 0 && this.save.essence >= 3) {
      this.performFirstTransformation();
      return;
    }
    this.ui.log(this.save.transformations > 0 ? "目前易質容量已滿" : "至少需要三份妖質");
  }

  private performFirstTransformation(): void {
    this.save.essence -= 3;
    this.save.transformations = 1;
    this.save.maxHealth += 18;
    this.save.maxQi += 22;
    this.health = this.save.maxHealth;
    this.qi = this.save.maxQi;
    this.ui.log("首次易質完成；低道息區將產生衰弱", "易質");
    this.persist();
    this.spawnElite();
    this.pushHud();
  }

  private updateEnemies(time: number, delta: number): void {
    const targets = this.enemies.getChildren() as Phaser.Physics.Arcade.Sprite[];
    for (const enemy of targets) {
      if (!enemy.active) continue;
      const stats = enemy.getData("stats") as EnemyStats;
      const distance = Phaser.Math.Distance.Between(enemy.x, enemy.y, this.player.x, this.player.y);
      const canPursue = this.player.x > CITY_EDGE - 35 && distance < (stats.kind === "xingtian" ? 620 : 360);
      if (canPursue) {
        const direction = new Phaser.Math.Vector2(this.player.x - enemy.x, this.player.y - enemy.y).normalize();
        const daoStrength = 0.76 + this.daoAt(enemy.x) / 170;
        enemy.setVelocity(direction.x * stats.speed * daoStrength, direction.y * stats.speed * daoStrength);
        enemy.setFlipX(direction.x < 0);
      } else {
        enemy.setVelocity(0, 0);
        enemy.rotation += Math.sin(time / 800 + enemy.x) * 0.00025 * delta;
      }
    }
  }

  private updateGuard(time: number): void {
    if (time < this.guardAttackAt) return;
    const enemies = this.enemies.getChildren() as Phaser.Physics.Arcade.Sprite[];
    const target = enemies.find((enemy) => enemy.active && Phaser.Math.Distance.Between(enemy.x, enemy.y, this.guard.x, this.guard.y) < 185);
    if (!target) return;
    this.guardAttackAt = time + 980;
    const direction = new Phaser.Math.Vector2(target.x - this.guard.x, target.y - this.guard.y).normalize();
    const effect = this.add.line(0, 0, this.guard.x, this.guard.y, target.x, target.y, 0xbdd8c4, 0.8).setOrigin(0).setLineWidth(3).setDepth(5);
    this.tweens.add({ targets: effect, alpha: 0, duration: 130, onComplete: () => effect.destroy() });
    this.damageEnemy(target, 14, direction, 45);
  }

  private handleEnemyContact(enemy: Phaser.Physics.Arcade.Sprite): void {
    if (this.dead || !enemy.active || this.player.x < CITY_EDGE - 25) return;
    const stats = enemy.getData("stats") as EnemyStats;
    const now = this.time.now;
    if (now - stats.lastStrike < (stats.kind === "xingtian" ? 760 : 1050)) return;
    stats.lastStrike = now;

    if (now < this.guardUntil) {
      const push = new Phaser.Math.Vector2(enemy.x - this.player.x, enemy.y - this.player.y).normalize();
      this.damageEnemy(enemy, 16, push, 135);
      this.ui.log("柔訣卸開來勢並反震敵手");
      return;
    }

    let damage = stats.damage;
    if (now < this.shieldUntil) damage *= 0.34;
    if (this.save.path === "inward") damage *= 0.88;
    this.health -= damage;
    this.player.setTintFill(0xd16a56);
    this.time.delayedCall(90, () => this.player.clearTint());
    this.cameras.main.shake(90, 0.006);
    if (this.health <= 0) this.handleDeath();
  }

  private damageEnemy(enemy: Phaser.Physics.Arcade.Sprite, amount: number, direction: Phaser.Math.Vector2, knockback: number): void {
    const stats = enemy.getData("stats") as EnemyStats;
    stats.hp -= amount;
    enemy.setTintFill(0xf0d8af);
    enemy.setVelocity(direction.x * knockback, direction.y * knockback);
    this.time.delayedCall(85, () => {
      if (enemy.active) enemy.clearTint();
    });
    const number = this.add.text(enemy.x, enemy.y - 30, String(amount), {
      fontFamily: "sans-serif", fontSize: "12px", color: "#f2d58d", stroke: "#161b17", strokeThickness: 3,
    }).setOrigin(0.5).setDepth(10);
    this.tweens.add({ targets: number, y: number.y - 22, alpha: 0, duration: 520, onComplete: () => number.destroy() });
    if (stats.hp <= 0) this.killEnemy(enemy, stats);
  }

  private killEnemy(enemy: Phaser.Physics.Arcade.Sprite, stats: EnemyStats): void {
    const x = enemy.x;
    const y = enemy.y;
    enemy.disableBody(true, true);
    if (stats.kind === "xingtian") {
      this.save.eliteDefeated = true;
      this.elite = null;
      this.ui.log("刑天統領已退倒，東境威脅解除", "戰果");
      const label = this.children.getByName("elite-label");
      label?.destroy();
      for (let i = 0; i < 3; i += 1) this.spawnRemain(x + i * 16 - 16, y + i * 8 - 8);
    } else {
      this.save.kills += 1;
      this.spawnRemain(x, y);
      this.ui.log("鑿齒倒下，留下可煉化遺骸");
      this.time.delayedCall(9000, () => {
        if (this.enemies.countActive(true) < 8) this.spawnEnemy("zaochi", Phaser.Math.Between(1000, 2200), Phaser.Math.Between(260, 1140));
      });
    }
    this.persist();
  }

  private spawnRemain(x: number, y: number): void {
    const remain = this.remains.create(x, y, "remain") as Phaser.Physics.Arcade.Sprite;
    remain.setDepth(4).setCircle(8);
    this.tweens.add({ targets: remain, scale: 1.3, alpha: 0.65, duration: 650, yoyo: true, repeat: -1 });
  }

  private collectRemain(remain: Phaser.Physics.Arcade.Sprite): void {
    if (!remain.active) return;
    remain.disableBody(true, true);
    this.carriedRemains += 1;
    this.ui.log("取得一份妖物遺骸；需返城煉化", "取材");
    this.pushHud();
  }

  private handleDeath(): void {
    this.dead = true;
    this.health = 0;
    this.player.disableBody(true, false);
    this.player.setAlpha(0.25);
    this.ui.showDeath();
    this.pushHud();
  }

  private cycleFormula(offset: number): void {
    const list: Formula[] = ["bao", "qing", "rou", "ning"];
    const index = list.indexOf(this.formula);
    this.selectFormula(list[(index + offset + list.length) % list.length]);
  }

  private drawWorldBars(): void {
    this.worldBars.clear();
    const enemies = this.enemies.getChildren() as Phaser.Physics.Arcade.Sprite[];
    for (const enemy of enemies) {
      if (!enemy.active) continue;
      const stats = enemy.getData("stats") as EnemyStats;
      if (stats.hp >= stats.maxHp) continue;
      const width = stats.kind === "xingtian" ? 72 : 42;
      this.worldBars.fillStyle(0x141815, 0.85).fillRect(enemy.x - width / 2, enemy.y - 39, width, 5);
      this.worldBars.fillStyle(stats.kind === "xingtian" ? 0xd4a548 : 0xb74f3d).fillRect(enemy.x - width / 2, enemy.y - 39, width * Math.max(0, stats.hp / stats.maxHp), 5);
    }
  }

  private pushHud(): void {
    const atRefinery = this.player && Phaser.Math.Distance.Between(this.player.x, this.player.y, 348, 698) < 92;
    let prompt: string | null = null;
    if (atRefinery) {
      if (this.carriedRemains > 0) prompt = "煉化遺骸";
      else if (this.save.transformations === 0 && this.save.essence >= 3) prompt = "進行首次易質";
      else prompt = "查看煉化臺";
    }
    const objective = this.objective();
    const state: HudState = {
      health: this.health,
      maxHealth: this.save.maxHealth,
      qi: this.qi,
      maxQi: this.save.maxQi,
      dao: this.currentDao,
      region: this.currentRegion,
      remains: this.carriedRemains,
      essence: this.save.essence,
      transformations: this.save.transformations,
      formula: this.formula,
      objectiveTitle: objective.title,
      objectiveProgress: objective.progress,
      prompt,
    };
    this.ui.update(state);
  }

  private objective(): { title: string; progress: string } {
    if (this.save.transformations === 0) {
      const total = Math.min(3, this.carriedRemains + this.save.essence);
      if (this.carriedRemains > 0) return { title: "返城煉化鑿齒遺骸", progress: `${total} / 3　東門煉化臺` };
      if (this.save.essence >= 3) return { title: "在聚炁鏡旁完成易質", progress: "妖質容量充足　返回煉化臺" };
      return { title: "帶回鑿齒遺骸", progress: `${total} / 3　高道息區` };
    }
    if (!this.save.eliteDefeated) return { title: "阻止刑天統領逼近", progress: "高道息荒原　東北方" };
    return { title: "東境巡防完成", progress: "新城防線暫時穩定" };
  }

  private daoAt(x: number): number {
    if (x < CITY_EDGE) return 12;
    if (x < 1410) return 43;
    return 82;
  }

  private formulaName(formula: Formula): string {
    return ({ bao: "爆", qing: "輕", rou: "柔", ning: "凝" })[formula];
  }

  private persist(): void {
    writeSave(this.save);
    this.ui.saved();
  }
}
