package org.alfresco.jlan.smb.server.disk;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.Before;
import org.junit.Test;

public class TemplateFileTest {

	private String fileExtension;
	private File dir;

	@Before
	public void init() {
		fileExtension = "txt";
		dir = new File("src/test", "templates");
	}

	@Test
	public void testCached() {

		TemplateCollector tc = new TemplateCollector(dir, null, true);

		File randomTemplate = tc.getRandomTemplate(fileExtension);
		assertNotNull(randomTemplate);
		System.out.println(randomTemplate);

		assertTrue(randomTemplate.exists());
	}

	@Test
	public void testNonCached() {

		TemplateCollector tc = new TemplateCollector(dir, null, false);

		File randomTemplate = tc.getRandomTemplate(fileExtension);
		assertNotNull(randomTemplate);
		System.out.println(randomTemplate);

		assertTrue(randomTemplate.exists());
	}

}
