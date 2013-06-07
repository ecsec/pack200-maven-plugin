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
 * Goal capable of compressing a jar with the pack200 tool.
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

	getLog().info("packing " + artifacts.size() + " artifact" + (artifacts.size() == 0 || artifacts.size() > 1 ? "s" : ""));

	for (Artifact artifactToPack : artifacts) {
	    List<Artifact> artifacts = new ArrayList<Artifact>();
	    Artifact artifact = null;
	    File origFile = artifactToPack.getFile();
	    File packFile = new File(origFile.getAbsolutePath() + PACK200_FILE_ENDING);

	    try {
		JarFile jar = new JarFile(origFile);
		FileOutputStream fos = new FileOutputStream(packFile);

		getLog().debug("packing " + origFile + " to " + packFile);
		packer.pack(jar, fos);
		fos.close();

		if (packAndGzip) {
		    File packGzipFile = new File(origFile.getAbsolutePath() + PACK200_GZIP_FILE_ENDING);
		    FileInputStream fis = new FileInputStream(packFile);

		    getLog().debug("compressing " + packFile + " to " + packGzipFile);
		    GZIPOutputStream gzipOutputStream = new GZIPOutputStream(new FileOutputStream(packGzipFile));
		    IOUtil.copy(fis, gzipOutputStream);
		    gzipOutputStream.close();
		    fis.close();

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

		artifacts.add(artifact);

		for (Artifact artifactToAttach : artifacts) {
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