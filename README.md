# Effortless Fabric

Effortless Fabric greatly facilitates building larger structures in minecraft, by allowing
you to place and break blocks in geometric shapes like lines, rectangles or cubes.

This is a FORK of the Effortless fabric mod maintained
by [huskcasaca](https://github.com/huskcasaca). Purpose of the
fork is to provide updates for recent minecraft versions, and to add some features that I
wanted.

[Effortless Fabric](https://www.curseforge.com/minecraft/mc-mods/effortless-fabric) in
turn is a fabric port of the famous [Effortless Building](https://www.curseforge.com/minecraft/mc-mods/effortless-building) mod.
This mod implements most features from Effortless Building, but removed randomizer bag and
reach upgrade items to make it a pure vanilla compat one.

## Screenshots
### Building Menu
![Radial Menu](screenshots/radial_menu.png)
### Line Mode
![Line Mode](screenshots/line_mode.png)
### Filled Wall Mode
![Filled Wall Mode](screenshots/filled_wall_mode.png)
### Filled Floor Mode
![Filled Floor Mode](screenshots/filled_floor_mode.png)
### Filled Cube Mode
![Filled Cube Mode](screenshots/filled_cube_mode.png)

## Building

Hold ALT key to switch build modes in the radial panel. There are buttons for undo/redo,
modifier settings and replace modes on the left. The options for each build mode (like a
filled vs hollow wall) are on the right.

In Single mode, you can place blocks beneath your feet by looking straight downward. Thus
you can start construction in air or water.

Use R key to toggle replace mode.

Use M key to quickly cycle between X/Z Mirror modes (centered on player's position).

### Build Modes

- **Disable**: Place in vanilla way
- **Single**: Place single block
- **Line**: Place lines of blocks in any 3 axis
- **Wall**: Place walls with locked x or z axis
- **Floor**: Place floor with locked y axis
- **Diagonal Line**: Place freeform lines of blocks
- **Diagonal Wall**: Place walls at any angle
- **Slope Floor**: Place slopes at any angle
- **Cube**: Place cubes with 3 clicks
- **Circle**: Place blocks in a circle (ellipse)
- **Cylinder**: Place a cylindrical shape like a tower
- **Sphere**: Place a spheroid made of cubes

### Build Modifiers

- **Mirror**: Place blocks and their states in mirror, works with even and uneven builds.
- **Array**: Copies builds and block states in a certain direction a certain number of times.
- **Radial Mirror**: Places blocks in a circle around a certain point. The circle can be divided into slices, and each slide will copy your block placements.

### Replace Modes

- **Disable**: Placing blocks does not replace any existing blocks
- **Normal**: Placing blocks replaces the existing blocks except the first one
- **Quick**: Placing blocks replaces the existing blocks including the first one

## Roadmap
### Future plans as of 1.7.0

- Implement a "copy-paste" mode: scan a construction (like Cube mode), then 
place copies of it interactively.
- Place / "break" water blocks by holding water bucket
- Stair mode: Place with 45° bevel e.g. for roof corners
- Improve slab handling, particularly allow building of slab stairs

### Known issues

If any of these bothers you particularly, please open a github issue (or Pull request :-))

* Placing torches or other attachable items is not well-tested and most probably buggy.
* Cannot place double-slabs, and making a stair of slabs doesn't work as one would
  probably expect.
* When replacing blocks, will not replace a block of same type but with different
  Blockstate. E.g. when placing stairs, will not "rotate" an existing stair block.
* No "randomizer bag". Might support Shulker chests as stand-in somewhen.
* No support for Forge, Bukkit, etc and won't add this either due to total lack of
  knowhow.

## Changelog
### 1.7.0 (Fork only)
 
* Fix block state changing while "dragging" construction area. Blocks will now be placed
  as they were on first click.
* In particular this fixes that stairs could not be placed upside-down.
* Single mode: can place block in air by looking straight down.
* Add keybind M to cycle X/Z mirror mode.
* Fix mirror being offset by 1/2 block for negative coordinates
* Considerable reorganisation of core building code, to make it easier to modify.
 
### 1.6.5 (Fork only)

* Fix occasional crash when closing radial menu without clicking anything
* Fix toggle-replace-mode keybind not working
 
### Up to 1.6.3
 
See original repository of huskcasaca.

## Credits

* **[huskcasaca](https://github.com/huskcasaca)**, the author
  of [Effortless Fabric](https://www.curseforge.com/minecraft/mc-mods/effortless-fabric)
* **[Requioss](https://www.curseforge.com/members/requioss)**, the author of [Effortless Building](https://www.curseforge.com/minecraft/mc-mods/effortless-building) 

## License

Effortless Fabric is licensed under LGPLv3. For full text see LICENSE file.
