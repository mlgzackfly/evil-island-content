export type Formula = "bao" | "qing" | "rou" | "ning";
export type QiPath = "outward" | "inward";

export interface SaveData {
  path: QiPath | null;
  maxHealth: number;
  maxQi: number;
  essence: number;
  transformations: number;
  kills: number;
  eliteDefeated: boolean;
}

export interface HudState {
  health: number;
  maxHealth: number;
  qi: number;
  maxQi: number;
  dao: number;
  region: string;
  remains: number;
  essence: number;
  transformations: number;
  formula: Formula;
  objectiveTitle: string;
  objectiveProgress: string;
  prompt: string | null;
}

export interface TouchState {
  up: boolean;
  down: boolean;
  left: boolean;
  right: boolean;
  attack: boolean;
  dash: boolean;
  interact: boolean;
}
