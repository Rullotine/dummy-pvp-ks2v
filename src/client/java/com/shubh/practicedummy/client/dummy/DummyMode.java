package com.shubh.practicedummy.client.dummy;

/** How the dummy behaves. */
public enum DummyMode {
	STAND("standing still"),
	WALK("wandering around"),
	STRAFE("circle-strafing you"),
	FIGHT("fighting you (closes in, backs off, hops, swings)"),
	STUNPRACTICE("stun practice: shield up, facing you (axe it, then slam it)");

	public final String description;

	DummyMode(String description) {
		this.description = description;
	}

	public DummyMode next() {
		DummyMode[] all = values();
		return all[(ordinal() + 1) % all.length];
	}
}
