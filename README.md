# CC Synaxis Connector

NeoForge 1.21.1 compatibility layer for CC: Tweaked and Synaxis.

This mod provides three bridge-style blocks:

- `cc_synaxis_bridge`: named one-way signal slots between CC and Synaxis.
- `cc_synaxis_super_hub`: a board-style hub for exposing selected CC/Synaxis capabilities to the opposite side.
- `log_output_assistant`: a Synaxis input board that writes configurable CSV logs.

## Build Dependencies

Most dependencies are resolved from Maven by Gradle. The local `libs/` directory is only for dependencies that are not currently resolved from a public Maven in this project:

- `synaxis-1.3.4.jar`
- `sable-schematic-api-0.2.6.jar`

`libs/*.jar` is ignored by Git, so dependency jars should not be committed to GitHub.

## Dependency Boundary Notes

The main bridge logic uses CC: Tweaked's public API package and Synaxis' Cimulink/PlantPort integration-facing types first.

Compatibility fallback for CC:T generic peripherals is isolated in `CcTweakedInternals`. This keeps the non-public CC:T implementation calls easy to audit or remove if upstream APIs change.

Synaxis plant access goes through registered `PlantEndpointProvider` ports and `PlantPortProviders.tryCreate`. The previous direct fallback to Synaxis dynamic motor internals has been removed.

The current block models still parent to `synaxis:block/cimulink_bus/block` in order to match Synaxis' board appearance. Replacing those three block model JSON files with original `ccsconnector` models will remove the remaining model-parent dependency.

Third-party dependency jars, source code, and assets are not intentionally redistributed by this repository.

## Bridge Peripheral Methods

- `list()` / `schema()`
- `info(name)`
- `addBridge([name], type, direction)`
- `removeBridge(name)`
- `get(name)`
- `set(name, value)`
- `lastWriteTick(name)`
- `getSaveLastValue(name)`
- `setSaveLastValue(name, enabled)`
- `toggleSaveLastValue(name)`

Directions:

- `synaxis_to_cc`: Synaxis writes the Cimulink input port, CC reads with `get(name)`.
- `cc_to_synaxis`: CC writes with `set(name, value)`, Synaxis reads the Cimulink output port.

If `name` is omitted or nil in `addBridge`, the block allocates `bridge_1`, `bridge_2`, and so on.

## Super Hub

The `cc_synaxis_super_hub` block is a board-style hub.

Synaxis side:

- Scans adjacent CC peripherals for CC-style `get*`, `is*`, `has*`, and `set*` methods.
- The empty-hand UI has an available-peripheral selector. Selecting a peripheral adds a configurable row.
- Each row has a method selector. `get*` / `is*` / `has*` methods become Synaxis `out` ports.
- `set*` methods become Synaxis `in` ports. Synaxis input values are forwarded into the selected CC setter.
- `number` becomes `real`, `boolean` becomes `boolean`, and `string` remains CC-only unless Synaxis adds a text signal type.
- The same UI also shows Synaxis-side values from two separate Synaxis APIs:
  - `bus`: values discovered from the local Synaxis plant cluster.
  - `adjacent:*`: values discovered by actively scanning adjacent Synaxis `PlantPort` providers/endpoints.

CC side:

- Exposes one CC peripheral named `cc_synaxis_super_hub`.
- Uses the Synaxis bus-style cluster plant query to collect readable Synaxis plant outputs without per-port UI exposure.
- `list()` / `schema()` / `listSynaxis()` returns all readable Synaxis values.
- `get(name)` / `getSynaxis(name)` reads one Synaxis value.
- `getAll()` / `getAllSynaxis()` reads all Synaxis values as a name-value table.
- `listCcCandidates()` lists adjacent CC peripherals and their selectable methods.
- `listExposedCc()` lists the CC method rows configured in the Super Hub UI.
- `getCc(name)` / `getAllCc()` reads the configured CC method row values.

## License And Third-Party Notices

This repository is licensed under the MIT License for this project's original compatibility-layer code and assets only. See [LICENSE](LICENSE).

CC: Tweaked, Synaxis, Sable, and related names, APIs, trademarks, assets, source code, binaries, models, textures, documentation, and original works belong to their respective owners and remain under their own upstream terms. This project does not relicense them.

CC Synaxis Connector is an independently created, AI-assisted compatibility mod for interoperability between CC: Tweaked and Synaxis. It does not intentionally copy or redistribute third-party assets or source code and has no intent to infringe any rights.

See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for dependency and rights-holder notes.
