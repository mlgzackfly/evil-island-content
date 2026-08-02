import type { SaveData } from "./types";

const SAVE_KEY = "evil-island-new-city-v1";

const DEFAULT_SAVE: SaveData = {
  path: null,
  maxHealth: 100,
  maxQi: 100,
  essence: 0,
  transformations: 0,
  kills: 0,
  eliteDefeated: false,
};

export function loadSave(): SaveData {
  try {
    const raw = localStorage.getItem(SAVE_KEY);
    if (!raw) return { ...DEFAULT_SAVE };
    return { ...DEFAULT_SAVE, ...JSON.parse(raw) } as SaveData;
  } catch {
    return { ...DEFAULT_SAVE };
  }
}

export function writeSave(data: SaveData): void {
  localStorage.setItem(SAVE_KEY, JSON.stringify(data));
}
