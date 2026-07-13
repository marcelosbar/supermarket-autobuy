package com.autobuy.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Swing-based implementation of the FolderPicker interface. Excluded from
 * automated coverage checks because GUI components cannot be instantiated or
 * tested in headless test runners.
 */
@Component
public class SwingFolderPicker implements FolderPicker {

	private static final Logger log = LoggerFactory.getLogger(SwingFolderPicker.class);

	@Override
	public String selectDirectory() {
		if (GraphicsEnvironment.isHeadless()) {
			throw new UnsupportedOperationException(
					"Cannot open native folder picker: Graphics environment is headless.");
		}

		AtomicReference<String> selectedPath = new AtomicReference<>(null);
		try {
			setSystemLookAndFeel();

			final int[] result = new int[]{JFileChooser.CANCEL_OPTION};
			SwingUtilities.invokeAndWait(() -> {
				log.info("Instantiating JFileChooser on EDT...");
				JFileChooser chooser = new JFileChooser();
				chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
				chooser.setDialogTitle("Select Database Backup Directory");
				chooser.setCurrentDirectory(new File(System.getProperty("user.home")));

				log.info("Creating JDialog wrapper on EDT...");
				javax.swing.JDialog dialog = new javax.swing.JDialog((java.awt.Frame) null, "Select Folder", true);
				dialog.setAlwaysOnTop(true);
				dialog.setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
				dialog.add(chooser);
				dialog.pack();
				dialog.setLocationRelativeTo(null);

				chooser.addActionListener(e -> {
					if (JFileChooser.APPROVE_SELECTION.equals(e.getActionCommand())) {
						result[0] = JFileChooser.APPROVE_OPTION;
					}
					dialog.dispose();
				});

				log.info("Showing native open dialog on EDT...");
				dialog.setVisible(true);
				log.info("Native open dialog closed with result: {}", result[0]);

				if (result[0] == JFileChooser.APPROVE_OPTION) {
					selectedPath.set(chooser.getSelectedFile().getAbsolutePath().replace('\\', '/'));
					log.info("Directory selected: {}", selectedPath.get());
				}
			});
			return selectedPath.get();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Folder selection interrupted", e);
		} catch (Exception e) {
			throw new RuntimeException("Folder selection failed: " + e.getMessage(), e);
		}
	}

	private void setSystemLookAndFeel() {
		try {
			javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName());
		} catch (Exception e) {
			log.warn("Failed to set system look and feel: {}", e.getMessage());
		}
	}
}
