import type { Formula, HudState, QiPath, SaveData, TouchState } from "./types";

interface UIHooks {
  choosePath: (path: QiPath) => void;
  selectFormula: (formula: Formula) => void;
  respawn: () => void;
}

function required<T extends Element>(selector: string): T {
  const element = document.querySelector<T>(selector);
  if (!element) throw new Error(`Missing UI element: ${selector}`);
  return element;
}

export class GameUI {
  readonly touch: TouchState = {
    up: false,
    down: false,
    left: false,
    right: false,
    attack: false,
    dash: false,
    interact: false,
  };

  private hooks: UIHooks | null = null;
  private readonly healthFill = required<HTMLElement>("#health-fill");
  private readonly qiFill = required<HTMLElement>("#qi-fill");
  private readonly healthValue = required<HTMLOutputElement>("#health-value");
  private readonly qiValue = required<HTMLOutputElement>("#qi-value");
  private readonly daoValue = required<HTMLElement>("#dao-value");
  private readonly daoPip = required<HTMLElement>("#dao-pip");
  private readonly regionName = required<HTMLElement>("#region-name");
  private readonly remainsValue = required<HTMLElement>("#remains-value");
  private readonly essenceValue = required<HTMLElement>("#essence-value");
  private readonly transformValue = required<HTMLElement>("#transform-value");
  private readonly objectiveTitle = required<HTMLElement>("#objective-title");
  private readonly objectiveProgress = required<HTMLElement>("#objective-progress");
  private readonly prompt = required<HTMLElement>("#context-prompt");
  private readonly promptLabel = required<HTMLElement>("#context-prompt span");
  private readonly feed = required<HTMLElement>("#event-feed");
  private readonly orientationModal = required<HTMLElement>("#orientation-modal");
  private readonly deathModal = required<HTMLElement>("#death-modal");
  private readonly saveState = required<HTMLElement>("#save-state");
  private feedLines: string[] = [];
  private lastFormula: Formula = "bao";

  constructor(save: SaveData) {
    this.orientationModal.hidden = save.path !== null;
    this.bindButtons();
  }

  setHooks(hooks: UIHooks): void {
    this.hooks = hooks;
  }

  update(state: HudState): void {
    const healthPercent = Math.max(0, (state.health / state.maxHealth) * 100);
    const qiPercent = Math.max(0, (state.qi / state.maxQi) * 100);
    this.healthFill.style.width = `${healthPercent}%`;
    this.qiFill.style.width = `${qiPercent}%`;
    this.healthValue.value = `${Math.ceil(state.health)} / ${state.maxHealth}`;
    this.qiValue.value = `${Math.floor(state.qi)} / ${state.maxQi}`;
    this.daoValue.textContent = String(Math.round(state.dao));
    this.daoPip.style.background = state.dao > 70 ? "#d8aa55" : state.dao > 30 ? "#4aa38d" : "#77877f";
    this.daoPip.style.boxShadow = `0 0 12px ${state.dao > 70 ? "#d8aa55" : "#4aa38d"}`;
    this.regionName.textContent = state.region;
    this.remainsValue.textContent = String(state.remains);
    this.essenceValue.textContent = String(state.essence);
    this.transformValue.textContent = state.transformations > 0 ? `一階 ${state.transformations * 18}%` : "未進行";
    this.objectiveTitle.textContent = state.objectiveTitle;
    this.objectiveProgress.textContent = state.objectiveProgress;
    this.prompt.hidden = state.prompt === null;
    if (state.prompt) this.promptLabel.textContent = state.prompt;
    if (state.formula !== this.lastFormula) this.setFormula(state.formula);
  }

  log(message: string, emphasis = ""): void {
    const safeMessage = message.replace(/[&<>"']/g, (char) => ({
      "&": "&amp;",
      "<": "&lt;",
      ">": "&gt;",
      "\"": "&quot;",
      "'": "&#039;",
    })[char] ?? char);
    const safeEmphasis = emphasis.replace(/[&<>"']/g, "");
    this.feedLines.unshift(safeEmphasis ? `<p><b>${safeEmphasis}</b> ${safeMessage}</p>` : `<p>${safeMessage}</p>`);
    this.feedLines = this.feedLines.slice(0, 4);
    this.feed.innerHTML = this.feedLines.join("");
  }

  saved(): void {
    this.saveState.textContent = "紀錄已保存";
    this.saveState.style.color = "#73b9a6";
    window.setTimeout(() => {
      this.saveState.style.color = "";
    }, 900);
  }

  finishOrientation(): void {
    this.orientationModal.hidden = true;
  }

  showDeath(): void {
    this.deathModal.hidden = false;
  }

  hideDeath(): void {
    this.deathModal.hidden = true;
  }

  private setFormula(formula: Formula): void {
    this.lastFormula = formula;
    document.querySelectorAll<HTMLElement>("[data-formula]").forEach((button) => {
      button.classList.toggle("is-active", button.dataset.formula === formula);
    });
  }

  private bindButtons(): void {
    document.querySelectorAll<HTMLButtonElement>("[data-path]").forEach((button) => {
      button.addEventListener("click", () => this.hooks?.choosePath(button.dataset.path as QiPath));
    });

    document.querySelectorAll<HTMLButtonElement>("[data-formula]").forEach((button) => {
      button.addEventListener("click", () => this.hooks?.selectFormula(button.dataset.formula as Formula));
    });

    required<HTMLButtonElement>("#respawn-button").addEventListener("click", () => this.hooks?.respawn());

    document.querySelectorAll<HTMLButtonElement>("[data-control]").forEach((button) => {
      const control = button.dataset.control as keyof TouchState;
      const activate = (event: Event): void => {
        event.preventDefault();
        this.touch[control] = true;
      };
      const release = (event: Event): void => {
        event.preventDefault();
        this.touch[control] = false;
      };
      button.addEventListener("pointerdown", activate);
      button.addEventListener("pointerup", release);
      button.addEventListener("pointercancel", release);
      button.addEventListener("pointerleave", release);
    });
  }
}
