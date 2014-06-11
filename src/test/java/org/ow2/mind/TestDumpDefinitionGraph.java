/**
 * Copyright (C) 2012 Schneider Electric
 *
 * This file is part of "Mind Compiler" is free software: you can redistribute 
 * it and/or modify it under the terms of the GNU Lesser General Public License 
 * as published by the Free Software Foundation, either version 3 of the 
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT 
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Contact: mind@ow2.org
 *
 * Authors: Julien TOUS
 * Contributors: 
 */

package org.ow2.mind;

import static org.testng.Assert.assertTrue;

import java.io.File;

import org.testng.annotations.Test;

public class TestDumpDefinitionGraph extends AbstractDumpDefinitionGraphCompositeTest {

	protected static File         buildDir = new File("target/build/");
	
	protected void cleanBuildDir() {
		if (buildDir.exists()) deleteDir(buildDir);
	}

	protected void deleteDir(final File f) {
		if (f.isDirectory()) {
			for (final File subFile : f.listFiles())
				deleteDir(subFile);
		}
		while (!f.delete());
		assertTrue(!f.exists(), "Couldn't delete \"" + f + "\".");
	}
	
	/**
	 * Test basic usage :
	 * Interface inheritance, Primitive inheritance, Composite, provide and requires
	 * @throws Exception
	 */
	@Test(groups = {"checkin"})
	public void testMisc() throws Exception {
		cleanBuildDir();  
		initSourcePath(getDepsDir("memory/api/Allocator.itf").getAbsolutePath(),"Misc");
		runner.compile("pkg2.Composite", null);
	}
	
	/**
	 * Tests dependency on template parameters type
	 * @throws Exception
	 */
	@Test(groups = {"checkin"})
	public void testTemplate() throws Exception {
		cleanBuildDir();
		initSourcePath(getDepsDir("memory/api/Allocator.itf").getAbsolutePath(),"Template");
		runner.compile("Main<Toto>", null);
	}
	
	/**
	 * Test flattening anonymous components (with inlined content)
	 * @throws Exception
	 */
	@Test(groups = {"checkin"})
	public void testAnonymous1() throws Exception {
		cleanBuildDir();
		initSourcePath(getDepsDir("memory/api/Allocator.itf").getAbsolutePath(),"Anonymous");
		runner.compileRunAndCheck("Main", null);
	}
}
