package org.alfresco.jlan.smb.server.disk;

import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.io.FileUtils;

public class TemplateCollector {

	private final File dir;
	public final File defaultFile;
	private final Map<String, Collection<File>> templatesByFiletype;
	private final Random random;
	private boolean cached;

	public TemplateCollector(File dir, File defaultFile, boolean cached) {
		this.dir = dir;
		this.defaultFile = defaultFile;
		this.cached = cached;
		this.templatesByFiletype = new ConcurrentHashMap<>();
		this.random = new Random();
	}

	public File getRandomTemplate(String fileExtension) {
		return cached ? getCachedRandomTemplate(fileExtension) : getRawRandomTemplate(fileExtension);
	}

	File getCachedRandomTemplate(String fileExtension) {
		Collection<File> templates = templatesByFiletype.get(fileExtension);

		if (templates == null) {
			templates = getTemplateList(fileExtension);
			templatesByFiletype.put(fileExtension, templates);
		}

		return templates.size() > 0 ? getRandomFile(templates) : defaultFile;
	}

	File getRawRandomTemplate(String fileExtension) {
		Collection<File> templates = getTemplateList(fileExtension);
		return templates.size() > 0 ? getRandomFile(templates) : defaultFile;
	}

	private Collection<File> getTemplateList(String fileExtension) {
		return FileUtils.listFiles(dir, new String[] { fileExtension }, true);
	}

	private File getRandomFile(Collection<? extends File> from) {
		int size = from.size();
		int idx = random.nextInt(size);

		return from.toArray(new File[size])[idx];
	}
}