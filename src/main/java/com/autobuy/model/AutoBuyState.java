package com.autobuy.model;

/**
 * Represents the execution state of the auto-buy process.
 */
public enum AutoBuyState {
	IDLE, RUNNING, AWAITING_MAPPING, AWAITING_EXHAUSTED_RESOLUTIONS, AWAITING_FINAL_REVIEW, SUCCESS, FAILED
}
