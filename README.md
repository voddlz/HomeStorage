# Home Storage

A Fabric mod for Minecraft **26.3** that adds a shared, effectively unlimited storage network built from two blocks.

## The idea

- **Infinity Home Chest** creates a *Home*: a cube-shaped area (128 blocks in every direction from the chest by default) that owns one virtual storage.
- **Infinity Access Chest** is a door into that storage. Place as many as you like inside the Home; all of them open the **same** inventory, and items put in through one are instantly available through every other one.
- The storage is virtual: it keeps a count per item (including its name, enchantments and other data), not thousands of chest slots. The only limit is a `long` per item type, which you will not reach.

Different Homes have independent storage, and Homes cannot overlap: a new Home Chest must be more than twice the Home radius away from every existing Home.

## Getting started

1. Craft an **Infinity Access Chest** (4 iron ingots, 4 planks, 1 chest).
2. Craft an **Infinity Home Chest** (an Access Chest, 4 iron blocks, 2 lapis, 2 redstone).
3. Place the Home Chest. It creates a Home owned by you.
4. Place Access Chests anywhere inside the Home and right-click one to open the storage.

### The storage screen

- Left-click an item to take a stack, right-click to take half. Shift-click moves a stack to your inventory.
- With an item on your cursor, left-click inserts all of it, right-click inserts one. Shift-click an item in your inventory to store it.
- Use the search box, the sort button (A-Z, most, least) and the page buttons at the top. The screen updates live when someone else uses the storage.

## Owners, members and permissions

| | Owner | Member | Everyone else |
|---|---|---|---|
| Open and use the storage | yes | yes | no |
| Place and break Access Chests | yes | yes | no |
| Manage members, delete the Home | yes | no | no |

Server operators (game master and above) can do all of this on any Home.

**Managing members:** right-click your Home Chest. The left list shows members (click **x** to remove), the right list shows online players you can add (click **+**).

## The Home Chest cannot be broken

The Home Chest is unbreakable and blast-proof. It is removed only by the **Delete Home** button on its screen (click twice to confirm), and only when the storage is empty, so items can never be lost by accident. Deleting a Home breaks the chest, drops it as an item, and frees the area.

## Admin commands

Game master and above:

- `/homestorage list` shows every Home with its id, owner, position, state and how many item types it holds.
- `/homestorage delete here` or `/homestorage delete <id>` deletes a Home. It is refused while items remain unless you add `force`, which destroys them.

There is deliberately no command that creates a Home.

## Installing

The mod must be installed on the **server and on every client**. It needs:

- Minecraft 26.3
- Fabric Loader 0.19.5 or newer
- Fabric API 0.161.0+26.3 or a compatible version

Back up your world before adding the mod to an existing one.

## Building from source

Requires JDK 25.

```
./gradlew build
```

The mod jar is written to `build/libs/homestorage-<version>.jar` (ignore the `-sources` jar). Use `./gradlew runClient` and `./gradlew runServer` to test in a development environment.

## License

CC0-1.0, see `LICENSE`.
