package org.apache.maven.plugins.assembly.archive.phase;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.File;
import java.io.IOException;
import java.util.Collections;

import junit.framework.TestCase;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Model;
import org.apache.maven.plugins.assembly.InvalidAssemblerConfigurationException;
import org.apache.maven.plugins.assembly.archive.ArchiveCreationException;
import org.apache.maven.plugins.assembly.archive.task.testutils.ArtifactMock;
import org.apache.maven.plugins.assembly.archive.task.testutils.MockAndControlForAddDependencySetsTask;
import org.apache.maven.plugins.assembly.artifact.DependencyResolutionException;
import org.apache.maven.plugins.assembly.artifact.DependencyResolver;
import org.apache.maven.plugins.assembly.format.AssemblyFormattingException;
import org.apache.maven.plugins.assembly.model.Assembly;
import org.apache.maven.plugins.assembly.model.DependencySet;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.logging.console.ConsoleLogger;
import org.easymock.classextension.EasyMockSupport;

public class DependencySetAssemblyPhaseTest
    extends TestCase
{

    final EasyMockSupport mm = new EasyMockSupport();

    public void testExecute_ShouldAddOneDependencyFromProject()
        throws AssemblyFormattingException, ArchiveCreationException, IOException,
        InvalidAssemblerConfigurationException, DependencyResolutionException
    {
        final String outputLocation = "/out";

        final MavenProject project = newMavenProject( "group", "project", "0" );

        final ArtifactMock projectArtifactMock = new ArtifactMock( mm, "group", "project", "0", "jar", false );

        project.setArtifact( projectArtifactMock.getArtifact() );

        final DependencySet ds = new DependencySet();
        ds.setUseProjectArtifact( false );
        ds.setOutputDirectory( outputLocation );
        ds.setOutputFileNameMapping( "${artifact.artifactId}" );
        ds.setUnpack( false );
        ds.setScope( Artifact.SCOPE_COMPILE );
        ds.setFileMode( Integer.toString( 10, 8 ) );

        final Assembly assembly = new Assembly();

        assembly.setId( "test" );
        assembly.setIncludeBaseDirectory( false );
        assembly.addDependencySet( ds );

        final MockAndControlForAddDependencySetsTask macTask =
            new MockAndControlForAddDependencySetsTask( mm, project );

        final ArtifactMock artifactMock = new ArtifactMock( mm, "group", "dep", "1", "jar", false );

        System.out.println(
            "On test setup, hashcode for dependency artifact: " + artifactMock.getArtifact().hashCode() );

        macTask.expectCSGetRepositories( null, null );

        macTask.expectGetDestFile( new File( "junk" ) );
//        macTask.expectAddFile( artifactFile, "out/dep", 10 );

        project.setArtifacts( Collections.singleton( artifactMock.getArtifact() ) );

        macTask.expectCSGetFinalName( "final-name" );

        final Logger logger = new ConsoleLogger( Logger.LEVEL_DEBUG, "test" );

        final MavenProject depProject = newMavenProject( "group", "dep", "1" );

        macTask.expectBuildFromRepository( depProject );

        macTask.expectResolveDependencySets();

        mm.replayAll();

        createPhase( macTask, logger, macTask.dependencyResolver ).execute( assembly, macTask.archiver,
                                                                            macTask.configSource );

        mm.verifyAll();
    }

    private MavenProject newMavenProject( final String groupId, final String artifactId, final String version )
    {
        final Model model = new Model();
        model.setGroupId( groupId );
        model.setArtifactId( artifactId );
        model.setVersion( version );

        return new MavenProject( model );
    }

    public void testExecute_ShouldNotAddDependenciesWhenProjectHasNone()
        throws AssemblyFormattingException, ArchiveCreationException, IOException,
        InvalidAssemblerConfigurationException, DependencyResolutionException
    {
        final Assembly assembly = new Assembly();

        assembly.setId( "test" );
        assembly.setIncludeBaseDirectory( false );

        final Logger logger = new ConsoleLogger( Logger.LEVEL_DEBUG, "test" );

        final MockAndControlForAddDependencySetsTask macTask = new MockAndControlForAddDependencySetsTask( mm, null );

        macTask.expectResolveDependencySets();

        mm.replayAll();

        createPhase( macTask, logger, macTask.dependencyResolver ).execute( assembly, null, macTask.configSource );

        mm.verifyAll();
    }

    private DependencySetAssemblyPhase createPhase( final MockAndControlForAddDependencySetsTask macTask,
                                                    final Logger logger, DependencyResolver dr )
    {
        final DependencySetAssemblyPhase phase = new DependencySetAssemblyPhase( null, dr, null );

        phase.enableLogging( logger );

        return phase;
    }

}
