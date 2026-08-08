package tw.zack.evilisland.model;

import java.util.List;

public record ConstructionPreviewPlan(CityProject project, int level, String world, int centerX, int centerY,
                                      int centerZ, List<ConstructionPreviewBlock> blocks) {
    public ConstructionPreviewPlan {
        blocks = List.copyOf(blocks);
    }
}
