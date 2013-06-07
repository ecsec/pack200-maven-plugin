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
 * Goal capable of normalizing a jar with the pack200 tool.
 */
@Mojo(name = "normalize", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public class NormalizeMojo extends AbstractPack200Mojo {

    @Override
    public void execute() throws MojoExecutionException {
	getLog().debug("starting normalize");

	init();

	getLog().info("normalizing " + artifacts.size() + " artifact" + (artifacts.size() == 0 || artifacts.size() > 1? "s" : ""));

	for (Artifact artifact : artifacts) {
	    File origFile = artifact.getFile();
	    File packFile = new File(origFile.getAbsolutePath() + PACK200_FILE_ENDING);

	    try {
		JarFile jar = new JarFile(origFile);
		FileOutputStream fos = new FileOutputStream(packFile);

		getLog().debug("packing " + origFile + " to " + packFile);
		packer.pack(jar, fos);
		fos.close();

		getLog().debug("unpacking " + packFile + " to " + origFile);
		JarOutputStream origJarStream = new JarOutputStream(new FileOutputStream(origFile));
		unpacker.unpack(packFile, origJarStream);

		origJarStream.close();

		getLog().debug("removing " + packFile);
		packFile.delete();

		getLog().debug("finished normalizing " + packFile);
	    } catch (IOException e) {
		throw new MojoExecutionException("Failed to pack jar", e);
	    }
	}
    }
}
