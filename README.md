# AE2 Crafting ETA

A Minecraft 1.7.10 addon for the GT New Horizons fork of Applied Energistics 2 that estimates how long a crafting job will take.

Built against the versions in GTNH 2.9.0-beta-3 (AE2 `rv3-beta-1050-GTNH`).

## What it shows

Everything on AE2's crafting screens sits behind a blue "(i)" in the title row. Hover it to see:

- **Crafting plan screen** (the "(i)" is next to the search box): "Estimated time: 4m 12s", updated when you pick a different CPU.
- **Crafting status screen** (the "(i)" is next to AE2's elapsed time):
  - "Time remaining: about 3m 5s", counting down each second, or paused while another job holds the machines.
  - **Stuck:** each waiting item and why, for example "Waiting for Tin Dust to be made first", "Macerator is busy with another job: making 64 Tin Plate on Main", or "Macerator has shut down: no power". Needs the mod on the server.
  - **Machines:** what the GregTech machines making each item are doing, for example "Macerator, 45%, 3.2s left" or "idle". Needs the mod on the server.
- **ME terminal tooltips:** "Last crafted 64 in 2m 10s (about 2.0s each)" for items that have been the target of a finished job.

If some steps of a plan have never run, the estimate says how many, e.g. "(3 of 12 steps not measured yet)".

Times are counted in game ticks, so they assume the server runs at 20 TPS. When the server lags, crafts take longer than shown. Both (i) tooltips carry a reminder of this.

## How it works

AE2 patterns carry no duration, so the mod learns one by watching each crafting CPU:

1. When the CPU pushes a batch to an interface or Molecular Assembler, the mod notes the server tick.
2. When that batch's output comes back into the CPU, the gap is the step's **latency**. Only batches pushed while nothing else of the same kind was in flight are used, so queueing doesn't inflate it.
3. When the job finishes, the time between the first and last output of each item gives its **throughput** with all its machines busy.

Both are kept per item as a moving average and saved with the world, so they follow upgrades and new machines.

The estimate treats each pattern as a step: it can start once each ingredient step has delivered its first batch, and takes `latency + (batches - 1) × batch size / throughput`. The job time is the latest step's finish, or the CPU's push limit (co-processors + 1 batches per tick) if that is slower.

Steps that have never run fall back to:

- Crafting patterns: the Molecular Assembler speed (10 ticks per craft with no acceleration cards, 2 with 5).
- Processing patterns: the recipe time of a GregTech machine seen running it (remembered until the server restarts), otherwise `unknownBatchSeconds` from the config (default 5 seconds).

## Client-only use

The mod works best installed on both the server and the client, but a client can use it on a server that doesn't have it. The client notices because the server never sends its timings at login, and then it learns on its own:

- **Timings** come from the crafting status screen. AE2 sends the client each item's scheduled and in-progress counts while that screen is open, and the mod times batches from those changes. It only learns while you're watching a CPU.
- **Plan screen estimates** use the crafting tree AE2 already sends for its tree view, so they work the same way as on a modded server, except that the number of machines per pattern isn't known.
- **Running CPU estimates** are rougher: the client sees counts but not which pattern feeds which, so the estimate is the slowest single item rather than the whole chain.
- **"Last crafted" tooltips** only update for crafts you chose to follow, because that's when AE2 tells the client the job's time.

Client-learned timings are saved in `.minecraft/ae2craftingeta/<server address>.dat`.

## Known limits

- Machines shared between several patterns are treated as if each pattern had its own.
- Throughput measured while a step was starved of inputs reads lower than the machine could manage, so estimates lean long.
- Machine status follows an ME Interface to the GregTech machine next to it, through a smart input hatch or bus (crafting input hatches included) to its multiblock controller, and one level into a subnet (its interfaces and export buses). A plain input bus has no link to its controller, so a multiblock fed only through one isn't read. Buffer chests show how full they are.
- Tooltips only appear in AE2's own terminals, not in Wireless Crafting Terminal.

## Building

```
./gradlew build
```

The jar is written to `build/libs/`. Dependencies come from the GTNH Maven, which the GitHub Actions build can reach.

## Releasing

Push a tag and GitHub Actions builds the jar and attaches it to a GitHub release with generated notes:

```
git tag 1.0.0
git push origin 1.0.0
```

The tag becomes the mod version. A tag ending in `-snapshot` is marked as a pre-release. Nothing is uploaded to CurseForge, Modrinth or Maven.
