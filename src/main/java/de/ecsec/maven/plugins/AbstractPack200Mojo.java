/****************************************************************************
 * Copyright (C) 2013 ecsec GmbH.
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.jar.Pack200;
import java.util.jar.Pack200.Packer;
import java.util.jar.Pack200.Unpacker;
import java.util.logging.Level;
import java.util.logging.LogManager;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;


/**
 * @author Benedikt Biallowons <benedikt.biallowons@ecsec.de>
 */
public abstract class AbstractPack200Mojo extends AbstractMojo {

    protected static final String LOGGING_CONFIGURATION_FILE_PROPERTY = "java.util.logging.config.file";
    protected static final String PACK200_LOGGER_LEVEL_PROPERTY = "java.util.jar.Pack200.level";

    protected static final String PACK200_FILE_ENDING = ".pack";
    protected static final String PACK200_TYPE = "jar.pack";

    protected static final ArrayList<String> DEBUG_ATTRIBUTES = new ArrayList<String>();

    protected Packer packer;
    protected Unpacker unpacker;
    protected List<Artifact> artifacts;

    static {
	DEBUG_ATTRIBUTES.add("SourceFile");
	DEBUG_ATTRIBUTES.add("LineNumberTable");
	DEBUG_ATTRIBUTES.add("LocalVariableTable");
	DEBUG_ATTRIBUTES.add("LocalVariableTypeTable");
    }

    @Component
    protected MavenProject project;

    @Parameter(defaultValue = "true")
    protected boolean processMainArtifact;

    @Parameter
    protected Set<String> includeClassifiers;

    @Parameter(defaultValue = "5")
    protected int effort;

    @Parameter(defaultValue = "1000000")
    protected int segmentLimit;

    @Parameter(defaultValue = "true")
    protected boolean keepFileOrder;

    @Parameter(defaultValue = "keep")
    protected String modificationTime;

    @Parameter(defaultValue = "keep")
    protected String deflateHint;

    @Parameter(defaultValue = "false")
    protected boolean stripDebugAttributes;

    @Parameter
    protected Set<String> stripCodeAttributes;

    @Parameter
    protected Set<String> stripClassAttributes;

    @Parameter
    protected Set<String> stripFieldAttributes;

    @Parameter
    protected Set<String> stripMethodAttributes;

    @Parameter(defaultValue = "true")
    protected boolean failOnUnknownAttributes;

    @Parameter
    protected Set<String> passFiles;

    @Parameter(defaultValue = "INFO")
    protected String packLogLevel;

    @Parameter
    protected String loggingProperties;

    protected void init() throws MojoExecutionException {
	packer = getPacker();
	unpacker = Pack200.newUnpacker();
	artifacts = getArtifacts();

	setPackLoggingLevel();
    }

    protected Packer getPacker() {
	Packer packer = Pack200.newPacker();

	getLog().debug("configuring packer");

	Map<String, String> packerProps = packer.properties();

	getLog().debug("setting effort to " + String.valueOf(effort));
	packerProps.put(Packer.EFFORT, String.valueOf(effort));
	getLog().debug("setting segment limit to " + String.valueOf(segmentLimit));
	packerProps.put(Packer.SEGMENT_LIMIT, String.valueOf(segmentLimit));
	getLog().debug("setting keep file order to " + String.valueOf(keepFileOrder));
	packerProps.put(Packer.KEEP_FILE_ORDER, String.valueOf(keepFileOrder));
	getLog().debug("setting modification time to " + modificationTime);
	packerProps.put(Packer.MODIFICATION_TIME, modificationTime);
	getLog().debug("setting deflate hint to " + deflateHint);
	packerProps.put(Packer.DEFLATE_HINT, deflateHint);

	if (stripDebugAttributes) {
	    getLog().debug("stripping debug attributes");

	    for (String attribute : DEBUG_ATTRIBUTES) {
		packerProps.put(Packer.CODE_ATTRIBUTE_PFX + attribute, Packer.STRIP);
	    }
	}

	if (stripCodeAttributes != null) {
	    for (String attribute : stripCodeAttributes) {
		if (stripDebugAttributes && DEBUG_ATTRIBUTES.contains(attribute)) {
		    continue;
		}

		getLog().debug("stripping code attribute " + attribute);
		packerProps.put(Packer.CODE_ATTRIBUTE_PFX + attribute, Packer.STRIP);
	    }
	}

	if (stripClassAttributes != null) {
	    for (String attribute : stripClassAttributes) {
		getLog().debug("stripping class attribute " + attribute);
		packerProps.put(Packer.CLASS_ATTRIBUTE_PFX + attribute, Packer.STRIP);
	    }
	}

	if (stripFieldAttributes != null) {
	    for (String attribute : stripFieldAttributes) {
		getLog().debug("stripping field attribute " + attribute);
		packerProps.put(Packer.FIELD_ATTRIBUTE_PFX + attribute, Packer.STRIP);
	    }
	}

	if (stripMethodAttributes != null) {
	    for (String attribute : stripMethodAttributes) {
		getLog().debug("stripping method attribute " + attribute);
		packerProps.put(Packer.METHOD_ATTRIBUTE_PFX + attribute, Packer.STRIP);
	    }
	}

	if (failOnUnknownAttributes) {
	    getLog().debug("failing on unknown attributes");
	    packerProps.put(Packer.UNKNOWN_ATTRIBUTE, Packer.ERROR);
	}

	if (passFiles != null) {
	    int i = 0;
	    for (String file : passFiles) {
		getLog().debug("passing file " + file);
		packerProps.put(Packer.PASS_FILE_PFX + i, file);
		i++;
	    }
	}

	getLog().debug("finished configuring packer");

	return packer;
    }

    protected List<Artifact> getArtifacts() {
	List<Artifact> artifacts = new ArrayList<Artifact>();

	getLog().debug("configuring artifacts to pack");

	if (processMainArtifact) {
	    getLog().debug("packing " + project.getArtifact().getFile().getAbsolutePath());
	    artifacts.add(project.getArtifact());
	}

	if (includeClassifiers != null) {
	    for (Artifact attachedArtifact : project.getAttachedArtifacts()) {
		if ("jar".equals(attachedArtifact.getType()) && attachedArtifact.hasClassifier()
			&& includeClassifiers.contains(attachedArtifact.getClassifier())) {
		    getLog().debug("packing " + attachedArtifact.getFile().getAbsolutePath());
		    artifacts.add(attachedArtifact);
		}
	    }
	}

	getLog().debug("finished configuring artifacts to pack");

	return artifacts;
    }

    protected void setPackLoggingLevel() throws MojoExecutionException {
	Level logLevel = null;
	String loggingPropertiesFile = null;

	try {
	    logLevel = Level.parse(packLogLevel.toUpperCase());
	    getLog().debug("setting pack200 logging level to " + logLevel.getName());
	} catch (IllegalArgumentException e) {
	    throw new MojoExecutionException("Log level not supported. For supported levels see java.util.logging.Level: ", e);
	}

	if (loggingProperties != null) {
	    loggingPropertiesFile = loggingProperties;
	} else if(System.getProperty(LOGGING_CONFIGURATION_FILE_PROPERTY) != null) {
	    loggingPropertiesFile = System.getProperty(LOGGING_CONFIGURATION_FILE_PROPERTY);
	} else {
	    // TODO: check if this is a valid path on most JREs
	    loggingPropertiesFile = System.getProperty("java.home") + File.separatorChar + "lib" + File.separatorChar + "logging.properties";
	}

	try {
	    FileInputStream fis = new FileInputStream(loggingPropertiesFile);
	    Properties properties = new Properties();
	    properties.load(fis);
	    fis.close();

	    properties.put(PACK200_LOGGER_LEVEL_PROPERTY, logLevel.toString());

	    ByteArrayOutputStream bos = new ByteArrayOutputStream();
	    properties.store(bos, null);
	    ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
	    bos.close();

	    LogManager.getLogManager().readConfiguration(bis);
	    bis.close();
	} catch (FileNotFoundException e) {
	    throw new MojoExecutionException("Logging.properties file not found. Please specify it via the 'loggingProperties' configuration option or globally via -Djava.util.logging.config.file", e);
	} catch (IOException e) {
	    throw new MojoExecutionException(null, e);
	}
    }

}
