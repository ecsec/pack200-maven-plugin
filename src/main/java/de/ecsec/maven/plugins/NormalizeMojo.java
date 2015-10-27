/****************************************************************************
 * Copyright (C) 2013-2015 ecsec GmbH.
 * All rights reserved.
 * Contact: ecsec GmbH (info@ecsec.de)
 *
 * GNU General Public License Usage
 * This file may be used under the terms of the GNU General Public
 * License version 3.0 as published by the Free Software Foundation
 * and appearing in the file LICENSE.GPL included in the packaging of
 * this file. Please review the following information to ensure the
 * GNU General Public License version 3.0 requirements will be met:
 * http://www.gnu.org/copyleft/gpl.html.
 *
 * Other Usage
 * Alternatively, this file may be used in accordance with the terms
 * and conditions contained in a signed written agreement between
 * you and ecsec GmbH.
 *
 ***************************************************************************/

package de.ecsec.maven.plugins;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;


/**
 * Goal capable of normalizing a JAR with the pack200 tool.
 *
 * @author Benedikt Biallowons
 */
@Mojo(name = "normalize", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public class NormalizeMojo extends AbstractPack200Mojo {

    @Override
    public void execute() throws MojoExecutionException {
	getLog().debug("starting normalize");

	init();

	int numArtifacts = artifacts.size();
	getLog().info("normalizing " + numArtifacts + " artifact" + (numArtifacts == 0 || numArtifacts > 1 ? "s" : ""));

	for (Artifact artifact : artifacts) {
	    File origFile = artifact.getFile();
	    File packFile = new File(origFile.getAbsolutePath() + PACK200_FILE_ENDING);

	    try {
		JarFile jar = new JarFile(origFile);
		try (FileOutputStream fos = new FileOutputStream(packFile)) {
		    getLog().debug("packing " + origFile + " to " + packFile);
		    packer.pack(jar, fos);
		}

		try (JarOutputStream origJarStream = new JarOutputStream(new FileOutputStream(origFile))) {
		    getLog().debug("unpacking " + packFile + " to " + origFile);
		    unpacker.unpack(packFile, origJarStream);
		}

		getLog().debug("removing " + packFile);
		packFile.delete();

		getLog().debug("finished normalizing " + packFile);
	    } catch (IOException e) {
		throw new MojoExecutionException("Failed to pack jar", e);
	    }
	}
    }
}
