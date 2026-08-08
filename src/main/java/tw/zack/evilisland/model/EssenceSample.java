package tw.zack.evilisland.model;

public record EssenceSample(String source, int purity, int amount) {
    public EssenceSample {
        source = source == null || source.isBlank() ? "unknown" : source;
        purity = Math.max(1, purity);
        amount = Math.max(0, amount);
    }
}
