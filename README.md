# Practice Dummy (Fabric 1.21.11, client-only)

Spawns a client-side fake player you can hit, so you can practice PvP, mace, spear and
spearmace on any server. Nothing about the dummy is ever sent to the server, other players
can't see it, and it has infinite HP.

## Build
Needs JDK 21.

    ./gradlew build        (Windows: gradlew.bat build)

The jar ends up in `build/libs/practice-dummy-1.0.0.jar` (use the one WITHOUT `-sources`).
Put it in `.minecraft/mods` together with Fabric API.

If Gradle can't resolve a version, copy the current numbers for 1.21.11 from
https://fabricmc.net/develop into `gradle.properties`.

## Use
| Key (rebindable) | Command                          | What it does                                  |
|------------------|----------------------------------|-----------------------------------------------|
| Y                | /dummy spawn [stand/walk/strafe] | Spawn at the block you're looking at (or 3 blocks ahead) |
| U                | /dummy remove                    | Remove all dummies                            |
| I                | /dummy mode <stand/walk/strafe>  | Cycle / set behaviour                         |
| -                | /dummy armor <none/iron/diamond/netherite> | Visual armor                        |

Modes: **stand** (faces you), **walk** (wanders near its spawn point), **strafe** (circles you at ~3 blocks).
Up to 10 dummies at once.

## What is simulated
Damage number (attack-cooldown scaling, crits, sprint knockback, mace smash bonus), knockback,
hurt flash/sounds, 10-tick damage immunity, combo counter. The readout shows above your hotbar.
Spear jab is a ray check (4.5 block reach). Sharpness/Density/Breach etc. are not in the numbers.

## Limits (because the server never sees the hit)
- A mace smash on the dummy does NOT cancel your fall damage and gives no wind-burst boost.
  You'll take normal fall damage when you land. Practice over water/hay/slime, or with a safe fall.
- Spear charge (hold right-click) attacks and Lunge-based momentum are server-driven, so charge
  hits on the dummy aren't registered. Jab hits are.
- Whether a server allows client mods is up to that server's rules.
