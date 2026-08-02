package tw.zack.evilisland.model;

import java.util.Locale;

public enum Formula {
    BAO("bao", "爆訣", "勁急威猛"),
    QING("qing", "輕訣", "飄忽迅捷"),
    ROU("rou", "柔訣", "卸勁化力"),
    NING("ning", "凝訣", "堅凝厚實");

    private final String id;
    private final String display;
    private final String description;

    Formula(String id, String display, String description) {
        this.id = id;
        this.display = display;
        this.description = description;
    }

    public String id() {
        return id;
    }

    public String display() {
        return display;
    }

    public String description() {
        return description;
    }

    public static Formula parse(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        for (Formula formula : values()) {
            if (formula.id.equals(normalized)) {
                return formula;
            }
        }
        return null;
    }
}
