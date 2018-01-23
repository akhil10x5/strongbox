package org.carlspring.strongbox.controllers;

import org.carlspring.strongbox.config.IntegrationTest;
import org.carlspring.strongbox.configuration.ConfigurationManager;
import org.carlspring.strongbox.resource.ConfigurationResourceResolver;
import org.carlspring.strongbox.rest.common.MavenRestAssuredBaseTest;
import org.carlspring.strongbox.storage.Storage;
import org.carlspring.strongbox.storage.repository.Repository;
import org.carlspring.strongbox.xml.configuration.repository.MavenRepositoryConfiguration;

import javax.inject.Inject;
import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.Set;

import com.google.common.base.Throwables;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Martin Todorov
 * @author Alex Oreshkevich
 * @author Pablo Tirado
 */
@IntegrationTest
@RunWith(SpringRunner.class)
public class TrashControllerUndeleteTest
        extends MavenRestAssuredBaseTest
{

    private static final String BASEDIR = Paths.get(
            ConfigurationResourceResolver.getVaultDirectory()).toAbsolutePath().toString();

    private static final String REPOSITORY_WITH_TRASH = "tcut-releases-with-trash";

    private static final String REPOSITORY_RELEASES = "tcut-releases";

    private static final String REPOSITORY_WITH_TRASH_BASEDIR = BASEDIR +
                                                                "/storages/" + STORAGE0 + "/" + REPOSITORY_WITH_TRASH;

    @Inject
    private ConfigurationManager configurationManager;

    @BeforeClass
    public static void cleanUp()
            throws Exception
    {
        cleanUp(getRepositoriesToClean());
    }

    @Override
    public void init()
            throws Exception
    {
        super.init();

        Storage storage = configurationManager.getConfiguration().getStorage(STORAGE0);

        // Notes:
        // - Used by testForceDeleteArtifactNotAllowed()
        // - Forced deletions are not allowed
        // - Has enabled trash
        MavenRepositoryConfiguration mavenRepositoryConfiguration = new MavenRepositoryConfiguration();
        mavenRepositoryConfiguration.setIndexingEnabled(true);

        Repository repositoryWithTrash = new Repository(REPOSITORY_WITH_TRASH);
        repositoryWithTrash.setStorage(storage);
        repositoryWithTrash.setAllowsForceDeletion(false);
        repositoryWithTrash.setTrashEnabled(true);
        repositoryWithTrash.setRepositoryConfiguration(mavenRepositoryConfiguration);

        createRepository(repositoryWithTrash);

        // Notes:
        // - Used by testForceDeleteArtifactAllowed()
        // - Forced deletions are allowed
        Repository repositoryReleases = new Repository(REPOSITORY_RELEASES);
        repositoryReleases.setStorage(storage);
        repositoryReleases.setAllowsForceDeletion(false);
        repositoryReleases.setRepositoryConfiguration(mavenRepositoryConfiguration);

        createRepository(repositoryReleases);

        setUp();
    }

    private void setUp()
            throws Exception
    {
        generateArtifact(getRepositoryBasedir(STORAGE0, REPOSITORY_WITH_TRASH).getAbsolutePath(),
                         "org.carlspring.strongbox.undelete:test-artifact-undelete",
                         new String[]{ "1.0",
                                       "1.1" });

        generateArtifact(getRepositoryBasedir(STORAGE0, REPOSITORY_RELEASES).getAbsolutePath(),
                         "org.carlspring.strongbox.undelete:test-artifact-undelete",
                         new String[]{ "2.0" });

        // Delete the artifact (this one should get placed under the .trash)
        client.delete(STORAGE0,
                      REPOSITORY_WITH_TRASH,
                      "org/carlspring/strongbox/undelete/test-artifact-undelete/1.0/test-artifact-undelete-1.0.jar");
        client.delete(STORAGE0,
                      REPOSITORY_WITH_TRASH,
                      "org/carlspring/strongbox/undelete/test-artifact-undelete/1.1/test-artifact-undelete-1.1.jar");

        // Delete the artifact (this one shouldn't get placed under the .trash)
        client.delete(STORAGE0,
                      REPOSITORY_RELEASES,
                      "org/carlspring/strongbox/undelete/test-artifact-undelete/2.0/test-artifact-undelete-2.0.jar");
    }

    @Override
    public void shutdown()
    {
        try
        {
            getRepositoryIndexManager().closeIndexersForRepository(STORAGE0, REPOSITORY_WITH_TRASH);
            getRepositoryIndexManager().closeIndexersForRepository(STORAGE0, REPOSITORY_RELEASES);
            removeRepositories();
        }
        catch (IOException | JAXBException e)
        {
            throw Throwables.propagate(e);
        }
        super.shutdown();
    }

    private void removeRepositories()
            throws IOException, JAXBException
    {
        removeRepositories(getRepositoriesToClean());
    }

    public static Set<Repository> getRepositoriesToClean()
    {
        Set<Repository> repositories = new LinkedHashSet<>();
        repositories.add(createRepositoryMock(STORAGE0, REPOSITORY_WITH_TRASH));
        repositories.add(createRepositoryMock(STORAGE0, REPOSITORY_RELEASES));

        return repositories;
    }

    @Test
    public void testUndeleteArtifactFromTrashForRepository()
            throws Exception
    {
        assertFalse(indexContainsArtifact(STORAGE0,
                                          REPOSITORY_WITH_TRASH,
                                          "+g:org.carlspring.strongbox.undelete " +
                                          "+a:test-artifact-undelete " +
                                          "+v:1.0 " +
                                          "+p:jar"));

        String url = getContextBaseUrl() + "/trash/" + STORAGE0 + "/" + REPOSITORY_WITH_TRASH +
                     "/org/carlspring/strongbox/undelete/test-artifact-undelete/1.0/test-artifact-undelete-1.0.jar";

        given().header(HttpHeaders.ACCEPT, MediaType.TEXT_PLAIN_VALUE)
               .when()
               .post(url)
               .peek()
               .then()
               .statusCode(HttpStatus.OK.value())
               .body(equalTo(
                       "The trash for '" + STORAGE0 + ":" + REPOSITORY_WITH_TRASH +
                       "' was restored successfully."));

        final Path artifactFileRestoredFromTrash = Paths.get(REPOSITORY_WITH_TRASH_BASEDIR + "/" +
                                                             "org/carlspring/strongbox/undelete/test-artifact-undelete/1.0/" +
                                                             "test-artifact-undelete-1.0.jar");

        final Path artifactFileInTrash = Paths.get(REPOSITORY_WITH_TRASH_BASEDIR + "/.trash/" +
                                                   "org/carlspring/strongbox/undelete/test-artifact-undelete/1.0/" +
                                                   "test-artifact-undelete-1.0.jar");

        assertFalse("Failed to undelete trash for repository '" + REPOSITORY_WITH_TRASH + "'!",
                    Files.exists(artifactFileInTrash));
        assertTrue("Failed to undelete trash for repository '" + REPOSITORY_WITH_TRASH + "'!",
                   Files.exists(artifactFileRestoredFromTrash));

        assertIndexContainsArtifact(STORAGE0,
                                    REPOSITORY_WITH_TRASH,
                                    "+g:org.carlspring.strongbox.undelete +a:test-artifact-undelete +v:1.0 +p:jar");
    }

    @Test
    public void testUndeleteArtifactsForAllRepositoriesWithTextAcceptHeader()
            throws Exception
    {
        assertFalse(indexContainsArtifact(STORAGE0,
                                          REPOSITORY_WITH_TRASH,
                                          "+g:org.carlspring.strongbox.undelete " +
                                          "+a:test-artifact-undelete " +
                                          "+v:1.1 " +
                                          "+p:jar"));

        final Path artifactFileInTrash = Paths.get(REPOSITORY_WITH_TRASH_BASEDIR + "/.trash/" +
                                                   "org/carlspring/strongbox/undelete/test-artifact-undelete/1.1/" +
                                                   "test-artifact-undelete-1.1.jar");

        assertTrue("Failed to undelete trash for repository '" + REPOSITORY_WITH_TRASH + "'!",
                   Files.exists(artifactFileInTrash.getParent()));

        String url = getContextBaseUrl() + "/trash";

        given().header(HttpHeaders.ACCEPT, MediaType.TEXT_PLAIN_VALUE)
               .when()
               .post(url)
               .peek()
               .then()
               .statusCode(HttpStatus.OK.value())
               .body(equalTo("The trash for all repositories was successfully restored."));

        final Path artifactFileRestoredFromTrash = Paths.get(REPOSITORY_WITH_TRASH_BASEDIR + "/" +
                                                             "org/carlspring/strongbox/undelete/test-artifact-undelete/1.1/" +
                                                             "test-artifact-undelete-1.1.jar");

        assertFalse("Failed to undelete trash for repository '" + REPOSITORY_WITH_TRASH + "'!",
                    Files.exists(artifactFileInTrash));
        assertTrue("Failed to undelete trash for repository '" + REPOSITORY_WITH_TRASH +
                   "' (" + artifactFileRestoredFromTrash.toAbsolutePath().toString() + " does not exist)!",
                   Files.exists(artifactFileRestoredFromTrash));

        assertIndexContainsArtifact(STORAGE0,
                                    REPOSITORY_WITH_TRASH,
                                    "+g:org.carlspring.strongbox.undelete " +
                                    "+a:test-artifact-undelete " +
                                    "+v:1.1 " +
                                    "+p:jar");
    }

    @Test
    public void testUndeleteArtifactsForAllRepositoriesWithJsonAcceptHeader()
            throws Exception
    {
        assertFalse(indexContainsArtifact(STORAGE0,
                                          REPOSITORY_WITH_TRASH,
                                          "+g:org.carlspring.strongbox.undelete " +
                                          "+a:test-artifact-undelete " +
                                          "+v:1.1 " +
                                          "+p:jar"));

        final Path artifactFileInTrash = Paths.get(REPOSITORY_WITH_TRASH_BASEDIR + "/.trash/" +
                                                   "org/carlspring/strongbox/undelete/test-artifact-undelete/1.1/" +
                                                   "test-artifact-undelete-1.1.jar");

        assertTrue("Failed to undelete trash for repository '" + REPOSITORY_WITH_TRASH + "'!",
                   Files.exists(artifactFileInTrash.getParent()));

        String url = getContextBaseUrl() + "/trash";

        given().header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
               .when()
               .post(url)
               .peek()
               .then()
               .statusCode(HttpStatus.OK.value())
               .body("message", equalTo("The trash for all repositories was successfully restored."));

        final Path artifactFileRestoredFromTrash = Paths.get(REPOSITORY_WITH_TRASH_BASEDIR + "/" +
                                                             "org/carlspring/strongbox/undelete/test-artifact-undelete/1.1/" +
                                                             "test-artifact-undelete-1.1.jar");

        assertFalse("Failed to undelete trash for repository '" + REPOSITORY_WITH_TRASH + "'!",
                    Files.exists(artifactFileInTrash));
        assertTrue("Failed to undelete trash for repository '" + REPOSITORY_WITH_TRASH +
                   "' (" + artifactFileRestoredFromTrash.toAbsolutePath().toString() + " does not exist)!",
                   Files.exists(artifactFileRestoredFromTrash));

        assertIndexContainsArtifact(STORAGE0,
                                    REPOSITORY_WITH_TRASH,
                                    "+g:org.carlspring.strongbox.undelete " +
                                    "+a:test-artifact-undelete " +
                                    "+v:1.1 " +
                                    "+p:jar");
    }

}
