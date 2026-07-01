# CC Synaxis Connector

NeoForge 1.21.1 compatibility layer for CC: Tweaked and Synaxis.

## Build dependencies

Most dependencies are resolved from Maven by Gradle. The local `libs/` directory is only for dependencies that are not currently resolved from a public Maven in this project:

- `synaxis-1.3.4.jar`
- `sable-schematic-api-0.2.6.jar`

`libs/*.jar` is ignored by Git, so dependency jars should not be committed to GitHub.

The `cc_synaxis_bridge` block exposes a CC peripheral, a dynamic Synaxis Cimulink endpoint, and a Synaxis PlantPort provider fallback. Each bridge is a named, single-direction value slot.

Right-click the block with an empty hand to open its LDLib/Synaxis-style UI. The UI can create bridges, remove bridges, choose `real` or `boolean`, choose the transfer direction, and inspect the current bridge list.

## Peripheral methods

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

## Copyright and compatibility notice

CC: Tweaked, Synaxis, Sable, and related names, APIs, trademarks, assets, and original works belong to their respective owners.

This project is an independently created, AI-assisted compatibility mod. Its purpose is to provide interoperability between CC: Tweaked and Synaxis. It does not intentionally copy or redistribute third-party assets or source code, and it has no intent to infringe any rights.

本项目为 AI 辅助开发的兼容性模组，目的是实现 CC: Tweaked 与 Synaxis 之间的互通。CC: Tweaked、Synaxis、Sable 以及相关名称、API、商标、素材和原始作品的版权归各自权利方所有。本项目无故意侵权行为；如权利方认为存在问题，请联系维护者处理。
