package com.shubh.practicedummy.client.dummy;

/** How the dummy behaves while alive. */
public enum DummyMode {
	STAND("standing still"),
	WALK("wandering around"),
	STRAFE("circle-strafing you");

	public final String description;

	DummyMode(String description) {
		this.description = description;
	}

	public DummyMode next() {
		DummyMode[] all = values();
		return all[(ordinal() + 1) % all.length];
	}
}
