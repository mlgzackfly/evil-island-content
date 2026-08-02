package tw.zack.evilisland.model;

public final class FormulaPathTest {
    private FormulaPathTest() {
    }

    public static void main(String[] args) {
        if (FormulaPath.canonicalPaths().size() != 13) {
            throw new AssertionError("Expected 13 canonical formula paths");
        }
        if (!FormulaPath.pure(Formula.BAO).display().equals("爆訣")) {
            throw new AssertionError("Pure formula display is incorrect");
        }
        if (!FormulaPath.mixed(Formula.QING, Formula.ROU, 70).display().equals("輕七柔三")) {
            throw new AssertionError("Mixed formula display is incorrect");
        }
        if (!FormulaPath.mixed(Formula.ROU, Formula.NING, 50).display().equals("柔凝均修")) {
            throw new AssertionError("Equal mixed formula display is incorrect");
        }
        if (FormulaPath.mixed(Formula.QING, Formula.ROU, 70).enginePath().secondaryPercent() != 30) {
            throw new AssertionError("Engine path weight is incorrect");
        }
        expectInvalid(() -> FormulaPath.mixed(Formula.BAO, Formula.ROU, 50));
        expectInvalid(() -> FormulaPath.mixed(Formula.QING, Formula.ROU, 60));
        System.out.println("FormulaPathTest passed");
    }

    private static void expectInvalid(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected invalid formula path to fail");
        } catch (IllegalArgumentException expected) {
            // Expected validation failure.
        }
    }
}
