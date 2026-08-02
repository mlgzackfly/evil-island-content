package tw.zack.evilisland.model;

import java.util.UUID;

public final class WorldEventStateTest {
    private WorldEventStateTest() {
    }

    public static void main(String[] args) {
        assert WorldEventState.PREPARING.canTransitionTo(WorldEventState.ACTIVE);
        assert WorldEventState.PREPARING.canTransitionTo(WorldEventState.FAILED);
        assert !WorldEventState.PREPARING.canTransitionTo(WorldEventState.SUCCEEDED);
        assert WorldEventState.ACTIVE.canTransitionTo(WorldEventState.RETREAT);
        assert WorldEventState.RETREAT.canTransitionTo(WorldEventState.ACTIVE);
        assert WorldEventState.SUCCEEDED.canTransitionTo(WorldEventState.COOLDOWN);
        assert WorldEventState.COOLDOWN.canTransitionTo(WorldEventState.PREPARING);
        assert WorldEventState.ACTIVE.running();
        assert !WorldEventState.FAILED.running();

        WorldEventSnapshot preparing = new WorldEventSnapshot(UUID.randomUUID(), "test",
                WorldEventState.PREPARING, UUID.randomUUID(), 1, 2, 3, "{}", 10L);
        WorldEventSnapshot active = preparing.withState(WorldEventState.ACTIVE, 20L);
        assert active.state() == WorldEventState.ACTIVE;
        assert active.updatedAt() == 20L;
        boolean rejected = false;
        try {
            preparing.withState(WorldEventState.SUCCEEDED, 20L);
        } catch (IllegalStateException expected) {
            rejected = true;
        }
        assert rejected;
        System.out.println("WorldEventStateTest passed");
    }
}
