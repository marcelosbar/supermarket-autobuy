package com.autobuy.model;

/**
 * Represents a user action taken during interactive product mapping resolution.
 */
public record ResolutionAction(ActionType type, String value) {
	public enum ActionType {
		SELECT, SKIP, REFINE
	}
}
