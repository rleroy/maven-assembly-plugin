package org.apache.maven.plugins.assembly.artifact;

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

import static org.easymock.EasyMock.expect;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryFactory;
import org.apache.maven.artifact.repository.LegacyLocalRepositoryManager;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.DefaultMavenExecutionResult;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.testing.stubs.StubArtifactRepository;
import org.apache.maven.plugins.assembly.AssemblerConfigurationSource;
import org.apache.maven.plugins.assembly.model.Assembly;
import org.apache.maven.plugins.assembly.model.DependencySet;
import org.apache.maven.plugins.assembly.model.ModuleBinaries;
import org.apache.maven.plugins.assembly.model.ModuleSet;
import org.apache.maven.plugins.assembly.model.Repository;
import org.apache.maven.plugins.assembly.resolved.AssemblyId;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.repository.internal.MavenRepositorySystemSession;
import org.codehaus.plexus.PlexusTestCase;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.logging.console.ConsoleLogger;
import org.easymock.classextension.EasyMockSupport;
import org.sonatype.aether.RepositorySystemSession;
import org.sonatype.aether.repository.LocalRepositoryManager;

public class DefaultDependencyResolverTest
    extends PlexusTestCase
{

    private ArtifactFactory factory;

    private ArtifactRepositoryFactory repoFactory;

    private ArtifactRepositoryLayout layout;

    private DefaultDependencyResolver resolver;

    @Override
    public void setUp()
        throws Exception
    {
        super.setUp();

        resolver = (DefaultDependencyResolver) lookup( DependencyResolver.class );

        factory = lookup( ArtifactFactory.class );
        repoFactory = lookup( ArtifactRepositoryFactory.class );
        layout = lookup( ArtifactRepositoryLayout.class, "default" );
    }
    
    protected MavenSession newMavenSession( MavenProject project )
    {
        MavenExecutionRequest request = new DefaultMavenExecutionRequest();
        MavenExecutionResult result = new DefaultMavenExecutionResult();

        MavenRepositorySystemSession repoSession = new MavenRepositorySystemSession();
        
        repoSession.setLocalRepositoryManager( LegacyLocalRepositoryManager.wrap( new StubArtifactRepository( "target/local-repo" ),
                                                                                  null ) );
        MavenSession session = new MavenSession( getContainer(), repoSession, request, result );
        session.setCurrentProject( project );
        session.setProjects( Arrays.asList( project ) );
        return session;
    }


    public void test_getDependencySetResolutionRequirements()
        throws DependencyResolutionException
    {
        final DependencySet ds1 = new DependencySet();
        ds1.setScope( Artifact.SCOPE_COMPILE );
        ds1.setUseTransitiveDependencies( false );

        final DependencySet ds2 = new DependencySet();
        ds2.setScope( Artifact.SCOPE_SYSTEM );
        ds2.setUseTransitiveDependencies( false );

        final MavenProject project = createMavenProject( "main-group", "main-artifact", "1", null );

        final ResolutionManagementInfo info = new ResolutionManagementInfo( project );

        final Assembly assembly = new Assembly();
        
        ProjectBuildingRequest buildingRequest = newMavenSession( project ).getProjectBuildingRequest();
        
        resolver.updateDependencySetResolutionRequirements( ds1, info, AssemblyId.createAssemblyId( assembly ),
                                                            buildingRequest, project );

        assertTrue( info.isResolutionRequired() );
        assertFalse( info.isResolvedTransitively() );

        assertTrue( info.getScopeFilter().getIncluded().contains( Artifact.SCOPE_COMPILE ) );
        assertTrue( info.getScopeFilter().getIncluded().contains( Artifact.SCOPE_SYSTEM ) );

        assertTrue( info.getScopeFilter().getIncluded().contains( Artifact.SCOPE_PROVIDED ) );

        assertFalse( info.getScopeFilter().getIncluded().contains( Artifact.SCOPE_RUNTIME ) );
        assertFalse( info.getScopeFilter().getIncluded().contains( Artifact.SCOPE_TEST ) );
    }

    public void test_getModuleSetResolutionRequirements()
        throws DependencyResolutionException
    {
        final EasyMockSupport mm = new EasyMockSupport();

        final AssemblerConfigurationSource cs = mm.createMock( AssemblerConfigurationSource.class );

        final File rootDir = new File( "root" );
        final MavenProject project = createMavenProject( "main-group", "main-artifact", "1", rootDir );

        final File module1Dir = new File( rootDir, "module-1" );
        final MavenProject module1 = createMavenProject( "main-group", "module-1", "1", module1Dir );
        final MavenProject module1a =
            createMavenProject( "group1", "module-1a", "1", new File( module1Dir, "module-1a" ) );
        final MavenProject module1b =
            createMavenProject( "group1.b", "module-1b", "1", new File( module1Dir, "module-1b" ) );

        module1.getModel().addModule( module1a.getArtifactId() );
        module1.getModel().addModule( module1b.getArtifactId() );

        final File module2Dir = new File( rootDir, "module-2" );
        final MavenProject module2 = createMavenProject( "main-group", "module-2", "1", module2Dir );
        final MavenProject module2a =
            createMavenProject( "main-group", "module-2a", "1", new File( module2Dir, "module-2a" ) );

        module2.getModel().addModule( module2a.getArtifactId() );

        project.getModel().addModule( module1.getArtifactId() );
        project.getModel().addModule( module2.getArtifactId() );

        final List<MavenProject> allProjects = new ArrayList<MavenProject>();
        allProjects.add( project );
        allProjects.add( module1 );
        allProjects.add( module1a );
        allProjects.add( module1b );
        allProjects.add( module2 );
        allProjects.add( module2a );
        
        expect( cs.getReactorProjects() ).andReturn( allProjects ).anyTimes();

        expect( cs.getProject() ).andReturn( project ).anyTimes();
        
        expect( cs.getMavenSession() ).andReturn( newMavenSession( project ) ).anyTimes();

        final ResolutionManagementInfo info = new ResolutionManagementInfo( project );

        final List<ModuleSet> moduleSets = new ArrayList<ModuleSet>();

        final ModuleSet ms1 = new ModuleSet();
        final DependencySet ds1 = new DependencySet();
        {
            ms1.addInclude( "*module1*" );
            ms1.setIncludeSubModules( false );

            final ModuleBinaries mb = new ModuleBinaries();

            ds1.setScope( Artifact.SCOPE_COMPILE );

            mb.addDependencySet( ds1 );
            ms1.setBinaries( mb );
            moduleSets.add( ms1 );
        }

        final ModuleSet ms2 = new ModuleSet();
        final DependencySet ds2 = new DependencySet();
        {
            ms2.addInclude( "main-group:*" );
            ms2.setIncludeSubModules( true );

            final ModuleBinaries mb = new ModuleBinaries();

            ds2.setScope( Artifact.SCOPE_TEST );

            mb.addDependencySet( ds2 );
            ms2.setBinaries( mb );
            moduleSets.add( ms2 );
        }

        mm.replayAll();

        resolver.enableLogging( new ConsoleLogger( Logger.LEVEL_DEBUG, "test" ) );

        final Assembly assembly = new Assembly();
        assembly.setModuleSets( moduleSets );

        resolver.updateModuleSetResolutionRequirements( AssemblyId.createAssemblyId( assembly ), ms1, ds1, info, cs );
        resolver.updateModuleSetResolutionRequirements( AssemblyId.createAssemblyId( assembly ), ms2, ds2, info, cs );

        assertTrue( info.isResolutionRequired() );

        final Set<MavenProject> enabledProjects = info.getEnabledProjects();
        assertTrue( enabledProjects.contains( project ) );

        assertTrue( enabledProjects.contains( module1 ) );

        // these should be excluded since sub-modules are not traversable
        assertFalse( enabledProjects.contains( module1a ) );
        assertFalse( enabledProjects.contains( module1b ) );

        assertTrue( enabledProjects.contains( module2 ) );
        assertTrue( enabledProjects.contains( module2a ) );

        // these are the two we directly set above.
        assertTrue( info.getScopeFilter().getIncluded().contains( Artifact.SCOPE_TEST ) );
        assertTrue( info.getScopeFilter().getIncluded().contains( Artifact.SCOPE_COMPILE ) );

        // this combination should be implied by the two direct scopes set above.
        assertTrue( info.getScopeFilter().getIncluded().contains( Artifact.SCOPE_RUNTIME ) );
        assertTrue( info.getScopeFilter().getIncluded().contains( Artifact.SCOPE_PROVIDED ) );
        assertTrue( info.getScopeFilter().getIncluded().contains( Artifact.SCOPE_SYSTEM ) );

        mm.verifyAll();
    }

    public void test_getRepositoryResolutionRequirements()
    {
        final List<Repository> repositories = new ArrayList<Repository>();

        {
            final Repository r = new Repository();
            r.setScope( Artifact.SCOPE_COMPILE );
            repositories.add( r );
        }

        {
            final Repository r = new Repository();
            r.setScope( Artifact.SCOPE_SYSTEM );
            repositories.add( r );
        }

        final MavenProject project = createMavenProject( "group", "artifact", "1.0", null );
        final Assembly assembly = new Assembly();
        assembly.setRepositories( repositories );

        final ResolutionManagementInfo info = new ResolutionManagementInfo( project );
        resolver.updateRepositoryResolutionRequirements( assembly,
                                                                                                           info );

        assertTrue( info.isResolutionRequired() );

        assertTrue( info.getScopeFilter().getIncluded().contains( Artifact.SCOPE_COMPILE ) );
        assertTrue( info.getScopeFilter().getIncluded().contains( Artifact.SCOPE_SYSTEM ) );

        assertTrue( info.getScopeFilter().getIncluded().contains( Artifact.SCOPE_PROVIDED ) );

        assertFalse( info.getScopeFilter().getIncluded().contains( Artifact.SCOPE_RUNTIME ) );
        assertFalse( info.getScopeFilter().getIncluded().contains( Artifact.SCOPE_TEST ) );
    }

    public void test_aggregateRemoteArtifactRepositories()
    {
        final List<ArtifactRepository> externalRepos = new ArrayList<ArtifactRepository>();

        final ArtifactRepository er1 =
            repoFactory.createArtifactRepository( "test.1", "http://test.com/path", layout, null, null );
        externalRepos.add( er1 );

        final ArtifactRepository er2 =
            repoFactory.createArtifactRepository( "test.2", "http://test2.com/path", layout, null, null );
        externalRepos.add( er2 );

        final List<ArtifactRepository> projectRepos = new ArrayList<ArtifactRepository>();

        final ArtifactRepository pr1 =
            repoFactory.createArtifactRepository( "project.1", "http://test.com/project", layout, null, null );
        projectRepos.add( pr1 );

        final ArtifactRepository pr2 =
            repoFactory.createArtifactRepository( "project.2", "http://test2.com/path", layout, null, null );
        projectRepos.add( pr2 );

        final MavenProject project = createMavenProject( "group", "artifact", "1", new File( "base" ) );
        project.setRemoteArtifactRepositories( projectRepos );

        final List<ArtifactRepository> aggregated =
            resolver.aggregateRemoteArtifactRepositories( externalRepos, Collections.singleton( project ) );

        assertRepositoryWithId( er1.getId(), aggregated, true );
        assertRepositoryWithId( er2.getId(), aggregated, true );
        assertRepositoryWithId( pr1.getId(), aggregated, true );
        assertRepositoryWithId( pr2.getId(), aggregated, false );
    }

    // public void test_manageArtifact()
    // {
    // Artifact managed = factory.createArtifact( "group", "artifact", "1", Artifact.SCOPE_PROVIDED, "jar" );
    //
    // Artifact target =
    // factory.createArtifact( managed.getGroupId(), managed.getArtifactId(), "2", Artifact.SCOPE_COMPILE,
    // managed.getType() );
    //
    // Artifact target2 =
    // factory.createArtifact( "other-group", managed.getArtifactId(), "2", Artifact.SCOPE_COMPILE,
    // managed.getType() );
    //
    // Map managedVersions = Collections.singletonMap( managed.getDependencyConflictId(), managed );
    //
    // DefaultDependencyResolver resolver =
    // new DefaultDependencyResolver().setLogger( new ConsoleLogger( Logger.LEVEL_DEBUG, "test" ) );
    //
    // resolver.manageArtifact( target, managedVersions );
    // resolver.manageArtifact( target2, managedVersions );
    //
    // assertEquals( managed.getVersion(), target.getVersion() );
    // assertEquals( managed.getScope(), target.getScope() );
    //
    // assertEquals( "2", target2.getVersion() );
    // assertEquals( Artifact.SCOPE_COMPILE, target2.getScope() );
    // }

    // public void test_buildManagedVersionMap_NonTransitiveResolution()
    // throws ArtifactResolutionException, ArchiveCreationException, InvalidVersionSpecificationException,
    // InvalidDependencyVersionException
    // {
    // Assembly assembly = new Assembly();
    //
    // DependencySet ds = new DependencySet();
    // ds.setScope( Artifact.SCOPE_PROVIDED );
    // ds.setUseTransitiveDependencies( false );
    //
    // assembly.addDependencySet( ds );
    //
    // ModuleSet ms = new ModuleSet();
    // ModuleBinaries mb = new ModuleBinaries();
    // ms.setBinaries( mb );
    //
    // DependencySet mds = new DependencySet();
    // mds.setScope( Artifact.SCOPE_PROVIDED );
    // mds.setUseTransitiveDependencies( false );
    //
    // mb.addDependencySet( mds );
    //
    // assembly.addModuleSet( ms );
    //
    // MavenProject project = createMavenProject( "group", "artifact", "1", new File( "base" ) );
    //
    // Dependency d1 = new Dependency();
    // d1.setGroupId( "group.dep" );
    // d1.setArtifactId( "dep1" );
    // d1.setVersion( "1" );
    // d1.setScope( Artifact.SCOPE_COMPILE );
    //
    // project.getModel().addDependency( d1 );
    //
    // Dependency d2 = new Dependency();
    // d2.setGroupId( "group.dep" );
    // d2.setArtifactId( "dep2" );
    // d2.setVersion( "1" );
    // d2.setScope( Artifact.SCOPE_PROVIDED );
    //
    // project.getModel().addDependency( d2 );
    //
    // Dependency d3 = new Dependency();
    // d3.setGroupId( "group.dep" );
    // d3.setArtifactId( "dep3" );
    // d3.setVersion( "1" );
    // d3.setScope( Artifact.SCOPE_PROVIDED );
    //
    // project.getModel().addDependency( d3 );
    //
    // MavenProject module = createMavenProject( "group", "module", "1", new File( "base/module" ) );
    //
    // project.getModel().addModule( module.getArtifactId() );
    //
    // Dependency md = new Dependency();
    // md.setGroupId( "group.dep" );
    // md.setArtifactId( "dep3" );
    // md.setVersion( "2" );
    // md.setScope( Artifact.SCOPE_PROVIDED );
    //
    // module.getModel().addDependency( md );
    //
    // List allProjects = new ArrayList();
    // allProjects.add( project );
    // allProjects.add( module );
    //
    // MockManager mm = new MockManager();
    //
    // MockControl csControl = MockControl.createControl( AssemblerConfigurationSource.class );
    // mm.add( csControl );
    //
    // AssemblerConfigurationSource cs = (AssemblerConfigurationSource) csControl.getMock();
    //
    // cs.getProject();
    // csControl.setReturnValue( project, MockControl.ZERO_OR_MORE );
    //
    // cs.getReactorProjects();
    // csControl.setReturnValue( allProjects, MockControl.ZERO_OR_MORE );
    //
    // cs.getRemoteRepositories();
    // csControl.setReturnValue( Collections.EMPTY_LIST, MockControl.ZERO_OR_MORE );
    //
    // mm.replayAll();
    //
    // DefaultDependencyResolver resolver = new DefaultDependencyResolver();
    // resolver.setArtifactFactory( factory );
    // resolver.setLogger( new ConsoleLogger( Logger.LEVEL_DEBUG, "test" ) );
    //
    // Map managedVersionMap = resolver.buildManagedVersionMap( assembly, cs );
    //
    // {
    // Dependency d = d1;
    // Artifact a = (Artifact) managedVersionMap.get( d.getManagementKey() );
    // assertNull( a );
    // }
    //
    // {
    // Dependency d = d2;
    // Artifact a = (Artifact) managedVersionMap.get( d.getManagementKey() );
    // assertNotNull( a );
    // assertEquals( d.getVersion(), a.getVersion() );
    // assertEquals( d.getScope(), a.getScope() );
    // }
    //
    // {
    // Dependency d = d3;
    // Artifact a = (Artifact) managedVersionMap.get( d.getManagementKey() );
    // assertNotNull( a );
    // assertEquals( d.getVersion(), a.getVersion() );
    // assertEquals( d.getScope(), a.getScope() );
    // }
    //
    // mm.verifyAll();
    // }
    //
    // public void test_buildManagedVersionMap_TransitiveResolution()
    // throws ArtifactResolutionException, ArchiveCreationException, InvalidVersionSpecificationException,
    // InvalidDependencyVersionException
    // {
    // Assembly assembly = new Assembly();
    //
    // DependencySet ds = new DependencySet();
    // ds.setScope( Artifact.SCOPE_COMPILE );
    // ds.setUseTransitiveDependencies( true );
    //
    // assembly.addDependencySet( ds );
    //
    // MavenProject project = createMavenProject( "group", "artifact", "1", new File( "base" ) );
    //
    // Dependency d1 = new Dependency();
    // d1.setGroupId( "group.dep" );
    // d1.setArtifactId( "dep1" );
    // d1.setVersion( "1" );
    // d1.setScope( Artifact.SCOPE_COMPILE );
    //
    // project.getModel().addDependency( d1 );
    //
    // Dependency d2 = new Dependency();
    // d2.setGroupId( "group.dep" );
    // d2.setArtifactId( "dep2" );
    // d2.setVersion( "1" );
    // d2.setScope( Artifact.SCOPE_COMPILE );
    // final Artifact a2 = factory.createArtifact( d2.getGroupId(), d2.getArtifactId(), d2.getVersion(), d2.getScope(),
    // "jar" );
    //
    // project.getModel().addDependency( d2 );
    //
    // Dependency d3 = new Dependency();
    // d3.setGroupId( "group.dep" );
    // d3.setArtifactId( "dep3" );
    // d3.setVersion( "1" );
    // d3.setScope( Artifact.SCOPE_COMPILE );
    //
    // project.getModel().addDependency( d3 );
    //
    // final Artifact a2a = factory.createArtifact( d3.getGroupId(), d3.getArtifactId(), "2", Artifact.SCOPE_RUNTIME,
    // "jar" );
    //
    // MockManager mm = new MockManager();
    //
    // MockControl msControl = MockControl.createControl( ArtifactMetadataSource.class );
    // mm.add( msControl );
    //
    // ArtifactMetadataSource ms = (ArtifactMetadataSource) msControl.getMock();
    //
    // try
    // {
    // ms.retrieve( null, null, null );
    // }
    // catch ( ArtifactMetadataRetrievalException e )
    // {
    // }
    //
    // msControl.setDefaultReturnValue( new ResolutionGroup( null, Collections.EMPTY_SET, Collections.EMPTY_LIST ) );
    // msControl.setMatcher( new ArgumentsMatcher()
    // {
    // public boolean matches( Object[] expected, Object[] actual )
    // {
    // Artifact a = (Artifact) actual[0];
    //
    // return a2.getArtifactId().equals( a.getArtifactId() );
    // }
    //
    // public String toString( Object[] args )
    // {
    // return "with artifact: " + args[0] ;
    // }
    //
    // } );
    // msControl.setReturnValue( new ResolutionGroup( a2, Collections.singleton( a2a ), Collections.EMPTY_LIST ) );
    //
    //
    // MockControl csControl = MockControl.createControl( AssemblerConfigurationSource.class );
    // mm.add( csControl );
    //
    // AssemblerConfigurationSource cs = (AssemblerConfigurationSource) csControl.getMock();
    //
    // cs.getProject();
    // csControl.setReturnValue( project, MockControl.ZERO_OR_MORE );
    //
    // String tmpDir = System.getProperty( "java.io.tmpdir" );
    // ArtifactRepository lr = repoFactory.createArtifactRepository( "local", "file://" + tmpDir, layout, null, null );
    //
    // cs.getLocalRepository();
    // csControl.setReturnValue( lr, MockControl.ZERO_OR_MORE );
    //
    // cs.getRemoteRepositories();
    // csControl.setReturnValue( Collections.EMPTY_LIST, MockControl.ZERO_OR_MORE );
    //
    // mm.replayAll();
    //
    // DefaultDependencyResolver resolver = new DefaultDependencyResolver();
    // resolver.setArtifactMetadataSource( ms );
    // resolver.setArtifactCollector( collector );
    // resolver.setArtifactFactory( factory );
    // resolver.setLogger( new ConsoleLogger( Logger.LEVEL_DEBUG, "test" ) );
    //
    // Map managedVersionMap = resolver.buildManagedVersionMap( assembly, cs );
    //
    // {
    // Dependency d = d1;
    // Artifact a = (Artifact) managedVersionMap.get( d.getManagementKey() );
    // assertNotNull( a );
    // assertEquals( d.getVersion(), a.getVersion() );
    // assertEquals( d.getScope(), a.getScope() );
    // }
    //
    // {
    // Dependency d = d2;
    // Artifact a = (Artifact) managedVersionMap.get( d.getManagementKey() );
    // assertNotNull( a );
    // assertEquals( d.getVersion(), a.getVersion() );
    // assertEquals( d.getScope(), a.getScope() );
    // }
    //
    // {
    // Dependency d = d3;
    // Artifact a = (Artifact) managedVersionMap.get( d.getManagementKey() );
    // assertNotNull( a );
    // assertEquals( d.getVersion(), a.getVersion() );
    // assertEquals( d.getScope(), a.getScope() );
    // }
    //
    // mm.verifyAll();
    // }

    private void assertRepositoryWithId( final String repoId, final List<ArtifactRepository> repos,
                                         final boolean shouldExist )
    {
        if ( ( repos == null || repos.isEmpty() ) )
        {
            if ( shouldExist )
            {
                fail( "Repository with id: " + repoId + " should be present, but repository list is null or empty." );
            }
        }
        else
        {
            boolean found = false;
            for ( final ArtifactRepository repo : repos )
            {
                if ( repoId.equals( repo.getId() ) )
                {
                    found = true;
                    break;
                }
            }

            if ( shouldExist )
            {
                assertTrue( "Repository with id: " + repoId + " should be present in repository list.", found );
            }
            else
            {
                assertFalse( "Repository with id: " + repoId + " should NOT be present in repository list.", found );
            }
        }
    }

    private MavenProject createMavenProject( final String groupId, final String artifactId, final String version,
                                             final File basedir )
    {
        final Model model = new Model();

        model.setGroupId( groupId );
        model.setArtifactId( artifactId );
        model.setVersion( version );
        model.setPackaging( "pom" );

        final MavenProject project = new MavenProject( model );

        final Artifact pomArtifact = factory.createProjectArtifact( groupId, artifactId, version );
        project.setArtifact( pomArtifact );

        project.setFile( new File( basedir, "pom.xml" ) );

        return project;
    }

}
