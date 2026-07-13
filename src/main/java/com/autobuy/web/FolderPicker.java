package com.autobuy.web;

/**
 * Interface representing a folder picker utility. Adheres to the Dependency
 * Inversion Principle (DIP) to decouple the controller from concrete graphical
 * user interface toolkits like Swing.
 */
public interface FolderPicker {
	/**
	 * Prompts the user to select a directory.
	 *
	 * @return the absolute path of the selected directory, or null if cancelled.
	 * @throws UnsupportedOperationException
	 *             if run in a headless environment.
	 */
	String selectDirectory();
}
