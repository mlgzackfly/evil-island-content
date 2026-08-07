package tw.zack.evilisland.model;

public record FactionContractSnapshot(int cycle, FactionContract contract, int progress,
                                      ContractResolution resolution, FactionContractState state, long updatedAt) {
    public FactionContractSnapshot {
        cycle = Math.max(1, cycle);
        progress = Math.max(0, Math.min(contract == null ? 0 : contract.stageCount(), progress));
        resolution = resolution == null ? ContractResolution.NONE : resolution;
        state = state == null ? FactionContractState.ACTIVE : state;
    }

    public static FactionContractSnapshot initial(int cycle, FactionContract contract, long now) {
        return new FactionContractSnapshot(cycle, contract, 0, ContractResolution.NONE,
                FactionContractState.ACTIVE, now);
    }
}
