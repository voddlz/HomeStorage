# CLAUDE.md — Home Storage Minecraft Mod

## Project purpose

This project is a learning-focused Minecraft 26.3 Fabric mod written in Java.

The goal is to build a storage system based on two custom blocks:

1. **Infinity Home Chest**
    - The core of a storage network.
    - Placing one creates a `StorageHome`.
    - Only one Home Chest may exist within another home's configured radius.
    - The Home Chest does not physically contain the network's items.
    - It owns/anchors the virtual storage network.

2. **Infinity Access Chest**
    - An access point to a Home's virtual storage.
    - Multiple Access Chests can belong to the same Home.
    - Opening any Access Chest accesses the same inventory.
    - It supports both input and output, like a normal chest.

There is deliberately **no `/storage create` command**. A Home is created by placing an Infinity Home Chest.

---

## Core gameplay model

Conceptually:

    Infinity Home Chest
             |
             v
       StorageHome
             |
             v
      InfinityStorage
        /    |    \
       /     |     \
      v      v      v

Access Access Access
Chest Chest Chest

A Home defines the area in which Access Chests can connect.

Example:

    Home Chest at X=100, Y=64, Z=-200
    Radius = 32

Any valid Infinity Access Chest inside that Home belongs to its storage network.

Multiple Homes have independent storage:

    Home A -> InfinityStorage A
    Home B -> InfinityStorage B

---

## Storage model

The storage is **virtual**, not a giant collection of physical chest slots.

Do not design the system around copying all items into thousands of vanilla chest slots.

The conceptual model is:

    ItemStack identity -> quantity

For example:

    Stone  -> 5432
    Iron   -> 1284
    Diamond -> 250
    Oak Log -> 8000

The exact implementation must respect modern Minecraft item components and ItemStack equality. Do not assume that comparing only the item type is sufficient; item components/data can make two stacks different.

"Unlimited" means effectively unlimited for normal gameplay. Avoid choosing an arbitrary tiny slot count that defeats the purpose. If implementation details require a numeric limit, document it and make the limit intentionally very large.

---

## Learning-first rules

The user is learning Java and Minecraft modding.

**Do not dump a complete finished mod unless explicitly requested.**

Instead:

1. Build one feature at a time.
2. Explain the problem before presenting code.
3. Explain why each class, method, field, collection, event, and Minecraft API is needed.
4. Prefer small code changes that can be tested immediately.
5. Ask the user to implement reasonable portions themselves when useful.
6. Explain compiler errors rather than simply replacing the user's code.
7. Avoid unexplained magic numbers or large blocks of copied code.
8. When introducing a Java concept, explain it in the context of this project.
9. Preserve the existing architecture unless there is a clear reason to change it.
10. If a design needs to change, explain the trade-off before changing it.

The goal is that the user can eventually explain the project themselves.

---

## Development environment

Target:

- Minecraft: **26.3**
- Mod loader: **Fabric**
- Language: **Java**
- IDE/editor: **Visual Studio Code**
- JDK: **Java 25**
- Build system: **Gradle**
- Fabric Loom: use the version appropriate for Minecraft 26.3
- Fabric Loader: use the current stable version compatible with 26.3
- Fabric API: use a current compatible 26.3 version

Minecraft 26.1+ uses the current non-obfuscated development setup. Use the current Fabric Loom configuration and current Mojang mappings for 26.3. Do not blindly apply old Yarn-based tutorials to this project.

Always verify version-sensitive details against the current official Fabric documentation before changing build configuration.

---

## Project naming

Suggested:

- Mod name: `Home Storage`
- Mod ID: `homestorage`
- Java package: `com.leekwater.homestorage`

Avoid spaces and non-ASCII characters in project paths.

---

## Planned architecture

Do not create all classes at the beginning. Introduce them when their responsibility becomes necessary.

A likely final architecture is:

    com.leekwater.homestorage
    |
    +-- HomeStorageMod.java
    |
    +-- block/
    |     +-- ModBlocks.java
    |     +-- InfinityHomeChestBlock.java
    |     +-- InfinityAccessChestBlock.java
    |
    +-- storage/
    |     +-- StorageHome.java
    |     +-- StorageManager.java
    |     +-- InfinityStorage.java
    |     +-- StorageItemEntry.java
    |
    +-- blockentity/
    |     +-- InfinityHomeChestBlockEntity.java
    |     +-- InfinityAccessChestBlockEntity.java
    |
    +-- screen/
    |     +-- InfinityStorageScreenHandler.java
    |     +-- InfinityStorageScreen.java
    |
    +-- persistence/
    |     +-- StorageWorldData.java
    |
    +-- permissions/
    |     +-- StoragePermissions.java
    |
    +-- client/
    |     +-- HomeStorageClient.java

The exact names may change as the implementation develops.

---

## Responsibilities

### HomeStorageMod

Main common/server entry point.

Responsible for initializing and registering common systems.

It should not become a giant class containing all mod logic.

### StorageHome

Represents one Home.

Likely data:

- unique identifier
- owner UUID
- dimension
- Home Chest position
- radius
- storage/network identifier
- future member/permission data

It should answer questions such as:

- Is a position inside this Home?
- Is a given Access Chest valid for this Home?

### StorageManager

Responsible for finding and managing Homes.

Potential responsibilities:

- create Home when Home Chest is placed
- find Home by ID
- find Home containing a position
- prevent overlapping/invalid Home placement
- remove Home when its Home Chest is permanently destroyed
- provide access to Homes for other systems

It should not contain GUI code.

### InfinityStorage

Represents the actual virtual inventory.

Potential responsibilities:

- insert an ItemStack
- extract an ItemStack
- count an item
- list stored items
- test whether an item can be inserted
- persist storage contents

This is the most important domain object in the mod.

### InfinityHomeChestBlock

Custom block that creates/anchors a Home.

Rules:

- placing it attempts to create a Home
- placement fails if it conflicts with an existing Home
- breaking it must follow the chosen Home-destruction rules
- it should not directly implement the entire storage system

### InfinityAccessChestBlock

Custom block that accesses an existing Home.

Rules:

- it must be inside a valid Home
- it must know which Home/network it belongs to, either directly or by resolving its position
- right-click opens the Home's storage
- breaking it removes only the access point, not the storage

### ScreenHandler

Server-authoritative GUI/inventory interaction.

It must validate item movement server-side.

Never trust client-provided inventory changes.

### Screen

Client-side visual GUI.

It should display the server-provided state and send normal interaction requests through the appropriate Minecraft/Fabric mechanisms.

### Persistence

Homes and their virtual storage must survive:

- closing the world
- restarting Minecraft
- server restart

Do not rely on ordinary Java fields alone for persistent game data.

---

## Home rules

Initial rules:

1. A Home is created by placing an Infinity Home Chest.
2. A Home has a configurable radius.
3. A Home Chest defines the Home's center.
4. Only one Home may occupy/overlap the protected area according to the chosen overlap rule.
5. An Infinity Access Chest must be placed inside a Home.
6. An Access Chest connects to the Home's virtual storage.
7. Multiple Access Chests in one Home share exactly the same storage.
8. Breaking an Access Chest does not delete storage.
9. Home deletion behavior must be explicitly designed before implementation.
10. Do not silently destroy a player's virtual items.

For the first implementation, prefer a conservative approach to Home deletion: never destroy storage merely because a block was broken until the storage recovery/deletion behavior has been explicitly implemented and tested.

---

## Multiplayer rules

The server is authoritative.

The storage must be shared between players.

Example:

    Player A opens Access Chest
    Player B opens Access Chest
             |
             v
       Same InfinityStorage

If Player A removes 64 Stone, Player B must eventually see the updated quantity.

Do not maintain independent client-side copies as the source of truth.

Concurrency and validation should be considered whenever items are inserted/extracted.

---

## GUI goals

The first GUI can resemble a chest.

Eventually:

    +------------------------------------------+
    |             INFINITY STORAGE             |
    +------------------------------------------+
    |                                          |
    | [Stone] [Iron] [Diamond] [Oak] [Coal]  |
    |                                          |
    | [Dirt]  [Glass] [Food]  ...             |
    |                                          |
    +------------------------------------------+
    | Search: [________________________]       |
    +------------------------------------------+
    |              PLAYER INVENTORY            |
    | [ ][ ][ ][ ][ ][ ][ ][ ][ ]             |
    | [ ][ ][ ][ ][ ][ ][ ][ ][ ]             |
    | [ ][ ][ ][ ][ ][ ][ ][ ][ ]             |
    +------------------------------------------+

Potential later features:

- search
- sorting
- deposit all
- take all
- item categories
- pagination if needed
- permissions/members
- separate input/output blocks

Do not implement these before the core storage system works.

---

## Development milestones

### Milestone 1 — Environment

- Install Java 25
- Install VS Code
- Install Java extensions
- Generate Fabric 26.3 project
- Import Gradle project
- Launch development Minecraft client

### Milestone 2 — Mod foundation

- Change mod ID/name/package
- Confirm mod initializes
- Learn Fabric entrypoints

### Milestone 3 — Home Chest

- Register block
- Register item/block item
- Add model/textures/translation
- Handle placement
- Create StorageHome

### Milestone 4 — Home persistence

- Save Homes to world data
- Reload Homes after restart
- Learn serialization/persistent world state

### Milestone 5 — Home validation

- Detect existing Homes
- Enforce Home radius
- Prevent conflicting Home placement
- Display useful feedback to the player

### Milestone 6 — Access Chest

- Register block
- Require placement inside a Home
- Associate it with the correct Home
- Open a temporary/test interface

### Milestone 7 — Virtual storage

- Create InfinityStorage
- Insert/extract items
- Count items
- Correct ItemStack matching
- Persist storage

### Milestone 8 — GUI

- Create ScreenHandler
- Create client Screen
- Display virtual contents
- Implement item interaction

### Milestone 9 — Multiplayer

- Test two players
- Verify server authority
- Verify simultaneous access
- Test race/validation cases

### Milestone 10 — Quality of life

Only after the core is stable:

- search
- sorting
- deposit all
- take all
- permissions
- multiple storage features
- better textures/UI
- sounds/animations

---

## Testing philosophy

Every milestone should have a test.

Examples:

### Home test

1. Place Home Chest.
2. Verify Home is created.
3. Restart world.
4. Verify Home still exists.

### Access test

1. Place Access Chest inside Home.
2. Open it.
3. Verify it finds the correct Home.
4. Place a second Access Chest.
5. Verify both access the same storage.

### Storage test

1. Put 64 Stone into Access Chest A.
2. Open Access Chest B.
3. Verify 64 Stone is visible.
4. Remove 32 Stone.
5. Open A.
6. Verify 32 remains.

### Multiplayer test

1. Player A opens Access Chest.
2. Player B opens another Access Chest.
3. Player A inserts an item.
4. Player B sees it.
5. Player B removes it.
6. Player A sees the change.

---

## Code quality rules

Prefer:

- small classes
- one responsibility per class
- descriptive names
- constants for configurable values
- immutable fields where appropriate
- server/client separation
- clear comments explaining _why_, not obvious comments explaining _what_

Avoid:

- giant manager classes
- static global mutable state unless justified
- magic numbers
- duplicated logic
- client-authoritative inventory changes
- scanning huge areas every game tick
- storing the entire inventory only in memory
- copying old Minecraft tutorials without checking their version

---

## Performance rules

Do not scan every block in a Home every tick.

Home/access relationships should be cached or persisted where appropriate.

If a scan is needed, perform it:

- when necessary
- when the network is invalidated
- after relevant block changes
- when explicitly refreshing/rebuilding the network

Do not optimize prematurely. First make the logic correct, then measure/improve it.

---

## Minecraft version rule

Minecraft 26.3 is the target.

If an API/class/method from an older tutorial does not exist:

1. Do not immediately force the old code to compile.
2. Check the current 26.3 API/mappings.
3. Determine the modern equivalent.
4. Explain the version difference to the user.

Minecraft 26.3 has important mod-development changes. Fabric currently recommends Loom 1.17 and Gradle 9.6.0 for 26.3, and current Fabric development uses the modern non-obfuscated/Mojang-mapped setup.

---

## Agent behavior

When helping with this project:

- Assume the user wants to understand the implementation.
- Explain concepts before abstractions become large.
- Keep each coding step small.
- Tell the user what to run/test after a change.
- If something breaks, diagnose it from the actual error rather than replacing the project.
- Never invent Minecraft 26.3 API names.
- Prefer official Fabric documentation for version-sensitive information.
- Preserve the Home Chest + Access Chest architecture.
- Do not reintroduce `/storage create` unless the user explicitly changes the design.
- Do not turn normal vanilla chests into the storage backend unless the user explicitly asks for that change.

---

## Current product definition

The intended user experience is:

1. Craft/place an **Infinity Home Chest**.
2. The Home Chest creates a storage Home.
3. The Home has a defined radius.
4. Craft/place **Infinity Access Chests** inside the Home.
5. Any Access Chest opens the same virtual Infinity Storage.
6. Items inserted through one Access Chest are immediately available through every other Access Chest.
7. The storage is effectively unlimited.
8. Multiple Homes have independent storage.
9. The system works for multiple players on the same server.
10. The system persists across world/server restarts.

Build toward this definition incrementally.
