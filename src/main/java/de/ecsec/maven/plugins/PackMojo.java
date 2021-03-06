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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;
import java.util.zip.GZIPOutputStream;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.IOUtil;


/**
 * Goal capable of compressing a JAR with the pack200 tool.
 *
 * @author Benedikt Biallowons
 */
@Mojo(name = "pack", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public class PackMojo extends AbstractPack200Mojo {

    protected static final String PACK200_GZIP_FILE_ENDING = ".pack.gz";
    protected static final String PACK200_GZIP_TYPE = "jar.pack.gz";

    @Parameter(defaultValue = "true")
    private boolean packAndGzip;

    @Override
    public void execute() throws MojoExecutionException {
	getLog().debug("starting pack");

	init();

	int numArtifacts = artifacts.size();
	getLog().info("packing " + numArtifacts + " artifact" + (numArtifacts == 0 || numArtifacts > 1 ? "s" : ""));

	for (Artifact artifactToPack : artifacts) {
	    List<Artifact> artifactList = new ArrayList<>();
	    Artifact artifact;
	    File origFile = artifactToPack.getFile();
	    File packFile = new File(origFile.getAbsolutePath() + PACK200_FILE_ENDING);

	    try {
		JarFile jar = new JarFile(origFile);
		try (FileOutputStream fos = new FileOutputStream(packFile)) {
		    getLog().debug("packing " + origFile + " to " + packFile);
		    packer.pack(jar, fos);
		}

		if (packAndGzip) {
		    File packGzipFile = new File(origFile.getAbsolutePath() + PACK200_GZIP_FILE_ENDING);
		    try (FileInputStream fis = new FileInputStream(packFile)) {
			try (GZIPOutputStream gzipOut = new GZIPOutputStream(new FileOutputStream(packGzipFile))) {
			    getLog().debug("compressing " + packFile + " to " + packGzipFile);
			    IOUtil.copy(fis, gzipOut);
			}
		    }

		    getLog().debug("removing " + packFile);
		    packFile.delete();

		    artifact = new DefaultArtifact(artifactToPack.getGroupId(), artifactToPack.getArtifactId(),
			    artifactToPack.getVersionRange(), artifactToPack.getScope(), PACK200_GZIP_TYPE,
			    artifactToPack.getClassifier(), new DefaultArtifactHandler(PACK200_GZIP_TYPE));

		    artifact.setFile(packGzipFile);
		} else {
		    artifact = new DefaultArtifact(artifactToPack.getGroupId(), artifactToPack.getArtifactId(),
			    artifactToPack.getVersionRange(), artifactToPack.getScope(), PACK200_TYPE,
			    artifactToPack.getClassifier(), new DefaultArtifactHandler(PACK200_TYPE));

		    artifact.setFile(packFile);
		}

		artifactList.add(artifact);

		for (Artifact artifactToAttach : artifactList) {
		    getLog().debug("attaching " + artifactToAttach);
		    project.addAttachedArtifact(artifactToAttach);
		}

		getLog().debug("finished packing " + packFile);
	    } catch (IOException e) {
		throw new MojoExecutionException("Failed to pack jar", e);
	    }
	}
    }

}
