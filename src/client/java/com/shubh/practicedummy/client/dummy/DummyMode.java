package com.shubh.practicedummy.client.dummy;

/** How the dummy behaves. */
public enum DummyMode {
	STAND("standing still"),
	WALK("wandering around"),
	STRAFE("circle-strafing you"),
	FIGHT("fighting you (closes in, backs off, hops, swings; archers kite and shoot)"),
	RUNNER("running away from you (sprint-hit / chase practice)"),
	JUMPER("bunny-hopping in place (air hits / crit practice)"),
	STUNPRACTICE("shield up, facing you (axe the shield, then mace-smash: stun slam practice)");

	public final String description;

	DummyMode(String description) {
		this.description = description;
	}

	public DummyMode next() {
		DummyMode[] all = values();
		return all[(ordinal() + 1) % all.length];
	}
}
