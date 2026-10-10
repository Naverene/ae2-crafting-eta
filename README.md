# AE2 Crafting ETA

A Minecraft 1.7.10 addon for the GT New Horizons fork of Applied Energistics 2 that estimates how long a crafting job will take.

Built against the versions in GTNH 2.9.0-beta-3 (AE2 `rv3-beta-1050-GTNH`).

## What it shows

- **Crafting plan screen:** "Estimated time: 4m 12s" above the window, updated when you pick a different CPU.
- **Crafting status screen:** "Time remaining: about 3m 5s" for a busy CPU.
- **ME terminal tooltips:** "Last crafted 64 in 2m 10s (about 2.0s each)" for items that have been the target of a finished job.

If some steps of a plan have never run, the estimate says how many, e.g. "(3 of 12 steps not measured yet)".

## How it works

AE2 patterns carry no duration, so the mod learns one by watching each crafting CPU:

1. When the CPU pushes a batch to an interface or Molecular Assembler, the mod notes the server tick.
2. When that batch's output comes back into the CPU, the gap is the step's **latency**. Only batches pushed while nothing else of the same kind was in flight are used, so queueing doesn't inflate it.
3. When the job finishes, the time between the first and last output of each item gives its **throughput** with all its machines busy.

Both are kept per item as a moving average and saved with the world, so they follow upgrades and new machines.

The estimate treats each pattern as a step: it can start once each ingredient step has delivered its first batch, and takes `latency + (batches - 1) × batch size / throughput`. The job time is the latest step's finish, or the CPU's push limit (co-processors + 1 batches per tick) if that is slower.

Steps that have never run fall back to:

- Crafting patterns: the Molecular Assembler speed (10 ticks per craft with no acceleration cards, 2 with 5).
- Processing patterns: `unknownBatchSeconds` from the config (default 5 seconds).

## Known limits

- Machines shared between several patterns are treated as if each pattern had its own.
- Throughput measured while a step was starved of inputs reads lower than the machine could manage, so estimates lean long.
- The remaining time on a running CPU only changes when batches are pushed or return, so it can sit still during a long machine step.
- Tooltips only appear in AE2's own terminals, not in Wireless Crafting Terminal.

## Building

```
./gradlew build
```

The jar is written to `build/libs/`. Dependencies come from the GTNH Maven, which the GitHub Actions build can reach.
