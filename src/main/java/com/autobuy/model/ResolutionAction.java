package com.autobuy.model;

/**
 * Represents a user action taken during interactive product mapping resolution.
 */
public record ResolutionAction(ActionType type, String value, boolean saveMapping) {
	public enum ActionType {
		SELECT, SKIP, REFINE
	}

	public ResolutionAction(ActionType type, String value) {
		this(type, value, true);
	}
}
