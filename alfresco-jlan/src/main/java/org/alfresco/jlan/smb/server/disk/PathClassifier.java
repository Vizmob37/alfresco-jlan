package org.alfresco.jlan.smb.server.disk;

import org.apache.commons.io.FilenameUtils;

public class PathClassifier {
	public static final String DOS_SEPARATOR_STR = "\\";

	public PathClassifier() {
	}

	public static String getFileName(String filePath) {
		String name = null;
		if (isFilePath(filePath)) {
			name = FilenameUtils.getName(filePath);
		}

		return name;
	}

	public static String getDirPath(String filePath) {

		String path;
		if (isFilePath(filePath)) {
			path = FilenameUtils.getPath(filePath);
		} else {
			path = filePath;
		}

		if (path.endsWith(PathClassifier.DOS_SEPARATOR_STR)) {
			path = path.substring(0, path.length() - 1);
		}

		return path;
	}

	// Presence of file extension is used to classify given path either as a file path or a directory path.
	static boolean isFilePath(String path) {
		String extension = FilenameUtils.getExtension(path);

		return (extension != null) && !extension.isEmpty();
	}
}