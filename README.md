# Practice Dummy (Fabric 1.21.11, client-only)

A client-side fake player you can spawn anywhere (including servers) and fight. Nothing about it
is ever sent to the server and other players can't see it.

## Keys (rebindable under Controls -> Practice Dummy)
Y spawn at crosshair, U remove all, I cycle behaviour.

## Commands
    /dummy spawn [stand|walk|strafe|fight]
    /dummy remove
    /dummy mode <stand|walk|strafe|fight>
    /dummy armor <none|iron|diamond|netherite>
    /dummy hold <none|sword|axe|mace|spear|shield>
    /dummy name <text>
    /dummy skin <player in tab list | self>
    /dummy health <hp>            (default 20 = 10 hearts)
    /dummy windburst <0-3>        (single-player only)
    /dummy boost <on|off>         (single-player)
    /dummy boost server <on|off>  (multiplayer, needs op: asks the server for real wind charges)

## What it does
- Finite health with infinite totems: when a hit would kill it, it pops a totem (sound + particles),
  drops to 1 heart, gets Absorption II (5s) and Regeneration II (45s), like the real thing.
- Hits show: red flash, hurt sound, vanilla-style knockback, red damage-indicator hearts, floating
  red damage numbers (first person), sword sweep, crit/sprint-knockback/smash sounds, combo counter.
- Mace smash bonus damage, attack-cooldown scaling, crits, spear jab (ray check, 4.5 blocks).
- Fight mode: closes in, backs off, circles, hops and swings its weapon at you (it can't hurt you).
- Mace boost: in single-player a smash launches you (Wind Burst level) and cancels the fall damage.
  On multiplayer the client never forces its own velocity (that would be a fly/no-fall hack). Instead,
  /dummy boost server on makes it run /summon wind_charge under you, so the SERVER launches you.
  That needs command permission (op / your own server); elsewhere it simply does nothing.

## Limits
- On servers there's no smash fall-damage cancel / wind-burst boost (see above).
- Spear charge attacks and Lunge momentum aren't simulated, only jabs.
- Sharpness / Density / Breach aren't in the damage numbers.
- Floating numbers assume your normal FOV setting; sprint FOV effects can nudge them slightly.

## 1.1.0 additions

- **Stun practice** (`/dummy mode stunpractice`): shield up, facing you. An axe hit disables it for 5s, a mace smash inside that window counts as a stun slam (reaction time tracked). Any other hit is blocked.
- **Armor math**: armor points, toughness, Protection (`/dummy protection 0-4`), netherite knockback resistance. **Breach, Sharpness and Density** on your held mace/sword are read automatically. Toggle with `/dummy armorcalc on|off`.
- **Wind Burst auto**: `/dummy windburst auto` reads the level from the mace you hold; `0-3` forces it. Chain counter, boost height and a live smash-damage meter under the crosshair.
- **Swimming**: the dummy swims and dives toward you, climbs out onto ledges (`/dummy swim on|off`).
- **Bows**: hold `bow` in FIGHT mode and it kites and shoots simulated arrows (particle trail, no real damage). Your own arrows hit the dummy with vanilla arrow damage.
- **New modes**: `runner` (flees, sprint-hit practice), `jumper` (bunny-hops in place).
- **Vanilla swing**: the dummy swings at your weapon's real attack speed and only inside 3 blocks of reach. Sprint knockback is consumed after a hit (w-tap needed again, like a vanilla server).
- **HUD**: status panel (health, absorption, totems, DPS, combo, shield/stun timer), totem-pop banner with time-to-pop, hit flashes. `/dummy hud on|off`.
- **GUI**: press `G` or `/dummy gui`.
- **Stats**: `/dummy stats`, `/dummy stats reset`. Also `/dummy autoheal`, `/dummy glint`.
