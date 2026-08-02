package tw.zack.evilisland.model;

import dev.zack.rpgengine.WeightedPath;

import java.util.ArrayList;
import java.util.List;

public record FormulaPath(Formula primary, Formula secondary, int primaryPercent) {
    public FormulaPath {
        if (secondary == null) {
            WeightedPath.pure(primary);
            primaryPercent = 100;
        } else {
            WeightedPath.mixed(primary, secondary, primaryPercent);
            if (Math.abs(primary.ordinal() - secondary.ordinal()) != 1) {
                throw new IllegalArgumentException("Only adjacent formulas can be cultivated together");
            }
            if (primaryPercent != 30 && primaryPercent != 50 && primaryPercent != 70) {
                throw new IllegalArgumentException("Mixed formula ratio must be 30, 50, or 70 percent");
            }
        }
    }

    public static FormulaPath pure(Formula formula) {
        return new FormulaPath(formula, null, 100);
    }

    public static FormulaPath mixed(Formula first, Formula second, int firstPercent) {
        return new FormulaPath(first, second, firstPercent);
    }

    public static List<FormulaPath> canonicalPaths() {
        List<FormulaPath> paths = new ArrayList<>();
        for (Formula formula : Formula.values()) {
            paths.add(pure(formula));
        }
        for (int index = 0; index < Formula.values().length - 1; index++) {
            Formula first = Formula.values()[index];
            Formula second = Formula.values()[index + 1];
            paths.add(mixed(first, second, 70));
            paths.add(mixed(first, second, 50));
            paths.add(mixed(first, second, 30));
        }
        return List.copyOf(paths);
    }

    public boolean isMixed() {
        return secondary != null;
    }

    public WeightedPath<Formula> enginePath() {
        return secondary == null
                ? WeightedPath.pure(primary)
                : WeightedPath.mixed(primary, secondary, primaryPercent);
    }

    public Formula dominant() {
        return primaryPercent >= 50 ? primary : secondary;
    }

    public String display() {
        if (!isMixed()) {
            return primary.display();
        }
        if (primaryPercent == 50) {
            return shortName(primary) + shortName(secondary) + "均修";
        }
        return shortName(primary) + chineseDigit(primaryPercent / 10)
                + shortName(secondary) + chineseDigit((100 - primaryPercent) / 10);
    }

    private static String shortName(Formula formula) {
        return formula.display().substring(0, 1);
    }

    private static String chineseDigit(int value) {
        return switch (value) {
            case 3 -> "三";
            case 5 -> "五";
            case 7 -> "七";
            default -> Integer.toString(value);
        };
    }
}
