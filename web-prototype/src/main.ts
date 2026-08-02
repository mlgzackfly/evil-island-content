import Phaser from "phaser";
import "./style.css";
import { PatrolScene } from "./game";
import { loadSave } from "./save";
import { GameUI } from "./ui";

const save = loadSave();
const ui = new GameUI(save);
const scene = new PatrolScene(ui, save, ui.touch);

new Phaser.Game({
  type: Phaser.AUTO,
  parent: "game-stage",
  width: 1280,
  height: 720,
  backgroundColor: "#101713",
  pixelArt: false,
  antialias: true,
  physics: {
    default: "arcade",
    arcade: {
      gravity: { x: 0, y: 0 },
      debug: false,
    },
  },
  scale: {
    mode: Phaser.Scale.RESIZE,
    autoCenter: Phaser.Scale.CENTER_BOTH,
  },
  render: {
    roundPixels: true,
  },
  scene,
});

ui.setHooks({
  choosePath: (path) => scene.choosePath(path),
  selectFormula: (formula) => scene.selectFormula(formula),
  respawn: () => scene.respawn(),
});
