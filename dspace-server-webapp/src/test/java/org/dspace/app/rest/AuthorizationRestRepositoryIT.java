/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.Serializable;
import java.util.UUID;

import com.jayway.jsonpath.matchers.JsonPathMatchers;
import org.dspace.app.rest.authorization.AlwaysFalseFeature;
import org.dspace.app.rest.authorization.AlwaysThrowExceptionFeature;
import org.dspace.app.rest.authorization.AlwaysTrueFeature;
import org.dspace.app.rest.authorization.Authorization;
import org.dspace.app.rest.authorization.AuthorizationFeature;
import org.dspace.app.rest.authorization.AuthorizationFeatureService;
import org.dspace.app.rest.authorization.AuthorizationRestUtil;
import org.dspace.app.rest.authorization.TrueForAdminsFeature;
import org.dspace.app.rest.authorization.TrueForLoggedUsersFeature;
import org.dspace.app.rest.authorization.TrueForTestUsersFeature;
import org.dspace.app.rest.builder.CommunityBuilder;
import org.dspace.app.rest.builder.EPersonBuilder;
import org.dspace.app.rest.converter.ConverterService;
import org.dspace.app.rest.matcher.AuthorizationMatcher;
import org.dspace.app.rest.model.CommunityRest;
import org.dspace.app.rest.model.SiteRest;
import org.dspace.app.rest.projection.DefaultProjection;
import org.dspace.app.rest.test.AbstractControllerIntegrationTest;
import org.dspace.app.rest.utils.Utils;
import org.dspace.content.Community;
import org.dspace.content.Site;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.SiteService;
import org.dspace.core.Constants;
import org.dspace.discovery.FindableObject;
import org.dspace.eperson.EPerson;
import org.dspace.services.ConfigurationService;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Test suite for the Authorization endpoint
 * 
 * @author Andrea Bollini (andrea.bollini at 4science.it)
 *
 */
public class AuthorizationRestRepositoryIT extends AbstractControllerIntegrationTest {

    @Autowired
    private AuthorizationFeatureService authorizationFeatureService;

    @Autowired
    private AuthorizationRestUtil authorizationRestUtil;

    @Autowired
    private ConverterService converterService;

    @Autowired
    private ConfigurationService configurationService;

    @Autowired
    private Utils utils;

    private SiteService siteService;

    private AuthorizationFeature alwaysTrue;

    private AuthorizationFeature alwaysFalse;

    private AuthorizationFeature alwaysException;

    private AuthorizationFeature trueForAdmins;

    private AuthorizationFeature trueForLoggedUsers;

    private AuthorizationFeature trueForTestUsers;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        siteService = ContentServiceFactory.getInstance().getSiteService();
        alwaysTrue = authorizationFeatureService.find(AlwaysTrueFeature.NAME);
        alwaysFalse = authorizationFeatureService.find(AlwaysFalseFeature.NAME);
        alwaysException = authorizationFeatureService.find(AlwaysThrowExceptionFeature.NAME);
        trueForAdmins = authorizationFeatureService.find(TrueForAdminsFeature.NAME);
        trueForLoggedUsers = authorizationFeatureService.find(TrueForLoggedUsersFeature.NAME);
        trueForTestUsers = authorizationFeatureService.find(TrueForTestUsersFeature.NAME);
    }

    @Test
    /**
     * This method is not implemented
     *
     * @throws Exception
     */
    public void findAllTest() throws Exception {
        String adminToken = getAuthToken(admin.getEmail(), password);
        getClient(adminToken).perform(get("/api/authz/authorizations"))
                    .andExpect(status().isMethodNotAllowed());
        getClient().perform(get("/api/authz/authorizations"))
                    .andExpect(status().isMethodNotAllowed());
    }

    @Test
    /**
     * Verify that an user can access a specific authorization
     *
     * @throws Exception
     */
    public void findOneTest() throws Exception {
        Site site = siteService.findSite(context);

        // define three authorizations that we know must exists
        Authorization authAdminSite = new Authorization(admin, trueForAdmins, site);
        Authorization authNormalUserSite = new Authorization(eperson, trueForLoggedUsers, site);
        Authorization authAnonymousUserSite = new Authorization(null, alwaysTrue, site);

        // access the authorization for the admin user
        String adminToken = getAuthToken(admin.getEmail(), password);
        getClient(adminToken).perform(get("/api/authz/authorizations/" + authAdminSite.getID()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$",
                            Matchers.is(AuthorizationMatcher.matchAuthorization(authAdminSite))));

        // access the authorization for a normal user
        String epersonToken = getAuthToken(eperson.getEmail(), password);
        getClient(epersonToken).perform(get("/api/authz/authorizations/" + authNormalUserSite.getID()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$",
                            Matchers.is(AuthorizationMatcher.matchAuthorization(authNormalUserSite))));

        // access the authorization for a normal user as administrator
        getClient(adminToken).perform(get("/api/authz/authorizations/" + authNormalUserSite.getID()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$",
                            Matchers.is(AuthorizationMatcher.matchAuthorization(authNormalUserSite))));

        // access the authorization for an anonymous user
        getClient().perform(get("/api/authz/authorizations/" + authAnonymousUserSite.getID()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$",
                            Matchers.is(AuthorizationMatcher.matchAuthorization(authAnonymousUserSite))));
    }

    @Test
    /**
     * Verify that the unauthorized return code is used in the appropriate scenarios
     *
     * @throws Exception
     */
    public void findOneUnauthorizedTest() throws Exception {
        Site site = siteService.findSite(context);

        // define two authorizations that we know must exists
        Authorization authAdminSite = new Authorization(admin, alwaysTrue, site);
        Authorization authNormalUserSite = new Authorization(eperson, alwaysTrue, site);

        // try anonymous access to the authorization for the admin user
        getClient().perform(get("/api/authz/authorizations/" + authAdminSite.getID()))
                    .andExpect(status().isUnauthorized());

        // try anonymous access to the authorization for a normal user
        getClient().perform(get("/api/authz/authorizations/" + authNormalUserSite.getID()))
                    .andExpect(status().isUnauthorized());
    }

    @Test
    /**
     * Verify that the forbidden return code is used in the appropriate scenarios
     *
     * @throws Exception
     */
    public void findOneForbiddenTest() throws Exception {
        context.turnOffAuthorisationSystem();
        Site site = siteService.findSite(context);
        EPerson testEPerson = EPersonBuilder.createEPerson(context)
                .withEmail("test-authorization@example.com")
                .withPassword(password).build();
        context.restoreAuthSystemState();

        // define three authorizations that we know must exists
        Authorization authAdminSite = new Authorization(admin, alwaysTrue, site);
        Authorization authNormalUserSite = new Authorization(eperson, alwaysTrue, site);

        String testToken = getAuthToken(testEPerson.getEmail(), password);

        // try to access the authorization for the admin user with another user
        getClient(testToken).perform(get("/api/authz/authorizations/" + authAdminSite.getID()))
                    .andExpect(status().isForbidden());

        // try to access the authorization of a normal user with another user
        getClient(testToken).perform(get("/api/authz/authorizations/" + authNormalUserSite.getID()))
                    .andExpect(status().isForbidden());

        // check access as a test user to a not existing authorization for another
        // eperson (but existing for the test user)
        Authorization noTestAuthForNormalUserSite  = new Authorization(eperson, trueForTestUsers, site);
        getClient(testToken).perform(get("/api/authz/authorizations/" + noTestAuthForNormalUserSite.getID()))
                    .andExpect(status().isForbidden());
    }

    @Test
    /**
     * Verify that the not found return code is used in the appropriate scenarios
     *
     * @throws Exception
     */
    public void findOneNotFoundTest() throws Exception {
        context.turnOffAuthorisationSystem();
        Site site = siteService.findSite(context);
        context.restoreAuthSystemState();

        String epersonToken = getAuthToken(eperson.getEmail(), password);
        String adminToken = getAuthToken(admin.getEmail(), password);

        // define three authorizations that we know will be no granted
        Authorization authAdminSite = new Authorization(admin, alwaysFalse, site);
        Authorization authNormalUserSite = new Authorization(eperson, alwaysFalse, site);
        Authorization authAnonymousUserSite = new Authorization(null, alwaysFalse, site);

        getClient(adminToken).perform(get("/api/authz/authorizations/" + authAdminSite.getID()))
                    .andExpect(status().isNotFound());

        getClient(epersonToken).perform(get("/api/authz/authorizations/" + authNormalUserSite.getID()))
                    .andExpect(status().isNotFound());
        // also the admin cannot retrieve a not existing authorization for the normal user
        getClient(epersonToken).perform(get("/api/authz/authorizations/" + authNormalUserSite.getID()))
                .andExpect(status().isNotFound());

        getClient().perform(get("/api/authz/authorizations/" + authAnonymousUserSite.getID()))
                    .andExpect(status().isNotFound());
        // also the admin cannot retrieve a not existing authorization for the anonymous user
        getClient(adminToken).perform(get("/api/authz/authorizations/" + authAnonymousUserSite.getID()))
                    .andExpect(status().isNotFound());

        // build a couple of IDs that look good but are related to not existing authorizations
        // the trueForAdmins feature is not defined for eperson
        String authInvalidType = getAuthorizationID(admin, trueForAdmins, eperson);
        getClient(adminToken).perform(get("/api/authz/authorizations/" + authInvalidType))
                    .andExpect(status().isNotFound());

        // the specified item doesn't exist
        String authNotExistingObject = getAuthorizationID(admin, alwaysTrue, Constants.ITEM, UUID.randomUUID());
        getClient(adminToken).perform(get("/api/authz/authorizations/" + authNotExistingObject))
                    .andExpect(status().isNotFound());

        // the specified eperson doesn't exist
        String authNotExistingEPerson = getAuthorizationID(UUID.randomUUID(), alwaysTrue, site);
        getClient(adminToken).perform(get("/api/authz/authorizations/" + authNotExistingEPerson))
                    .andExpect(status().isNotFound());

        // the specified feature doesn't exist
        String authNotExistingFeature = getAuthorizationID(admin, "notexistingfeature", site);
        getClient(adminToken).perform(get("/api/authz/authorizations/" + authNotExistingFeature))
                    .andExpect(status().isNotFound());

        // check access as admin to a not existing authorization for another eperson (but existing for the admin)
        Authorization noAdminAuthForNormalUserSite  = new Authorization(eperson, trueForAdmins, site);
        getClient(adminToken).perform(get("/api/authz/authorizations/" + noAdminAuthForNormalUserSite.getID()))
                    .andExpect(status().isNotFound());

        // check a couple of completely wrong IDs
        String notValidID = "notvalidID";
        getClient(adminToken).perform(get("/api/authz/authorizations/" + notValidID))
                    .andExpect(status().isNotFound());

        String notValidIDWithWrongEpersonPart = getAuthorizationID("1", alwaysTrue.getName(),
                String.valueOf(site.getType()), site.getID().toString());
        // use the admin token otherwise it would result in a forbidden (attempt to access authorization of other users)
        getClient(adminToken).perform(get("/api/authz/authorizations/" + notValidIDWithWrongEpersonPart))
                    .andExpect(status().isNotFound());

        String notValidIDWithWrongObjectTypePart = getAuthorizationID(eperson.getID().toString(), alwaysTrue.getName(),
                "SITE", site.getID().toString());
        getClient(epersonToken).perform(get("/api/authz/authorizations/" + notValidIDWithWrongObjectTypePart))
                    .andExpect(status().isNotFound());

        String notValidIDWithUnknownObjectTypePart =
                getAuthorizationID(eperson.getID().toString(), alwaysTrue.getName(),
                        String.valueOf(Integer.MAX_VALUE), "1");
        getClient(epersonToken).perform(get("/api/authz/authorizations/" + notValidIDWithUnknownObjectTypePart))
                    .andExpect(status().isNotFound());

    }

    @Test
    /**
     * Verify that an exception in the feature check will be reported back
     *
     * @throws Exception
     */
    public void findOneInternalServerErrorTest() throws Exception {
        Site site = siteService.findSite(context);

        // define two authorizations that we know will throw exceptions
        Authorization authAdminSite = new Authorization(admin, alwaysException, site);
        Authorization authNormalUserSite = new Authorization(eperson, alwaysException, site);

        String adminToken = getAuthToken(admin.getEmail(), password);
        String epersonToken = getAuthToken(eperson.getEmail(), password);

        getClient(adminToken).perform(get("/api/authz/authorizations/" + authAdminSite.getID()))
                    .andExpect(status().isInternalServerError());

        getClient(epersonToken).perform(get("/api/authz/authorizations/" + authNormalUserSite.getID()))
                    .andExpect(status().isInternalServerError());
    }

    @Test
    /**
     * Verify that the search by object works properly in allowed scenarios:
     * - for an administrator
     * - for an administrator that want to inspect permission of the anonymous users or another user
     * - for a logged-in "normal" user
     * - for anonymous
     * 
     * @throws Exception
     */
    public void findByObjectTest() throws Exception {
        Site site = siteService.findSite(context);
        SiteRest siteRest = converterService.toRest(site, converterService.getProjection(DefaultProjection.NAME));
        String siteUri = utils.linkToSingleResource(siteRest, "self").getHref();

        // disarm the alwaysThrowExceptionFeature
        configurationService.setProperty("org.dspace.app.rest.authorization.AlwaysThrowExceptionFeature.turnoff", true);
        // verify that it works for administrators
        String adminToken = getAuthToken(admin.getEmail(), password);
        getClient(adminToken).perform(get("/api/authz/authorizations/search/object")
                .param("uri", siteUri)
                .param("eperson", admin.getID().toString()))
            .andExpect(status().isOk())
            // there are at least 3: alwaysTrue, trueForAdministrators and trueForLoggedUsers
            .andExpect(jsonPath("$._embedded.authorizations", Matchers.hasSize(greaterThanOrEqualTo(3))))
            .andExpect(jsonPath("$._embedded.authorizations", Matchers.everyItem(
                    Matchers.anyOf(
                            JsonPathMatchers.hasJsonPath("$.type", is("authorization")),
                            JsonPathMatchers.hasJsonPath("$._embedded.feature",
                                    Matchers.not(Matchers.anyOf(
                                                is(alwaysFalse.getName()),
                                                is(alwaysException.getName()),
                                                is(trueForTestUsers.getName())
                                            )
                                    )),
                            JsonPathMatchers.hasJsonPath("$._embedded.feature.resourcetypes",
                                    Matchers.hasItem(is("authorization"))),
                            JsonPathMatchers.hasJsonPath("$.id",
                                    Matchers.anyOf(
                                            Matchers.startsWith(admin.getID().toString()),
                                            Matchers.endsWith(site.getType() + "_" + site.getID()))))
                                    )
                    )
            )
            .andExpect(jsonPath("$._links.self.href",
                    Matchers.containsString("/api/authz/authorizations/search/object")))
            .andExpect(jsonPath("$.page.size", is(20)))
            .andExpect(jsonPath("$.page.totalElements", greaterThanOrEqualTo(3)));

        // verify that it works for normal loggedin users
        String epersonToken = getAuthToken(eperson.getEmail(), password);
        getClient(epersonToken).perform(get("/api/authz/authorizations/search/object")
                .param("uri", siteUri)
                .param("eperson", eperson.getID().toString()))
            .andExpect(status().isOk())
            // there are at least 2: alwaysTrue and trueForLoggedUsers
            .andExpect(jsonPath("$._embedded.authorizations", Matchers.hasSize(greaterThanOrEqualTo(2))))
            .andExpect(jsonPath("$._embedded.authorizations", Matchers.everyItem(
                    Matchers.anyOf(
                            JsonPathMatchers.hasJsonPath("$.type", is("authorization")),
                            JsonPathMatchers.hasJsonPath("$._embedded.feature",
                                    Matchers.not(Matchers.anyOf(
                                                is(alwaysFalse.getName()),
                                                is(alwaysException.getName()),
                                                is(trueForTestUsers.getName()),
                                                is(trueForAdmins.getName())
                                            )
                                    )),
                            JsonPathMatchers.hasJsonPath("$._embedded.feature.resourcetypes",
                                    Matchers.hasItem(is("authorization"))),
                            JsonPathMatchers.hasJsonPath("$.id",
                                    Matchers.anyOf(
                                            Matchers.startsWith(eperson.getID().toString()),
                                            Matchers.endsWith(site.getType() + "_" + site.getID()))))
                                    )
                    )
            )
            .andExpect(jsonPath("$._links.self.href",
                    Matchers.containsString("/api/authz/authorizations/search/object")))
            .andExpect(jsonPath("$.page.size", is(20)))
            .andExpect(jsonPath("$.page.totalElements", greaterThanOrEqualTo(2)));

        // verify that it works for administators inspecting other users
        getClient(adminToken).perform(get("/api/authz/authorizations/search/object")
                .param("uri", siteUri)
                .param("eperson", eperson.getID().toString()))
            .andExpect(status().isOk())
            // there are at least 2: alwaysTrue and trueForLoggedUsers
            .andExpect(jsonPath("$._embedded.authorizations", Matchers.hasSize(greaterThanOrEqualTo(2))))
            .andExpect(jsonPath("$._embedded.authorizations", Matchers.everyItem(
                    Matchers.anyOf(
                            JsonPathMatchers.hasJsonPath("$.type", is("authorization")),
                            JsonPathMatchers.hasJsonPath("$._embedded.feature",
                                    Matchers.not(Matchers.anyOf(
                                                is(alwaysFalse.getName()),
                                                is(alwaysException.getName()),
                                                is(trueForTestUsers.getName()),
                                                // this guarantee that we are looking to the eperson
                                                // authz and not to the admin ones
                                                is(trueForAdmins.getName())
                                            )
                                    )),
                            JsonPathMatchers.hasJsonPath("$._embedded.feature.resourcetypes",
                                    Matchers.hasItem(is("authorization"))),
                            JsonPathMatchers.hasJsonPath("$.id",
                                    Matchers.anyOf(
                                            // this guarantee that we are looking to the eperson
                                            // authz and not to the admin ones
                                            Matchers.startsWith(eperson.getID().toString()),
                                            Matchers.endsWith(site.getType() + "_" + site.getID()))))
                                    )
                    )
            )
            .andExpect(jsonPath("$._links.self.href",
                    Matchers.containsString("/api/authz/authorizations/search/object")))
            .andExpect(jsonPath("$.page.size", is(20)))
            .andExpect(jsonPath("$.page.totalElements", greaterThanOrEqualTo(2)));

        // verify that it works for anonymous users
        getClient().perform(get("/api/authz/authorizations/search/object")
                .param("uri", siteUri))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$._embedded.authorizations", Matchers.hasSize(greaterThanOrEqualTo(1))))
            .andExpect(jsonPath("$._embedded.authorizations", Matchers.everyItem(
                    Matchers.anyOf(
                            JsonPathMatchers.hasJsonPath("$.type", is("authorization")),
                            JsonPathMatchers.hasJsonPath("$._embedded.feature",
                                    Matchers.not(Matchers.anyOf(
                                                is(alwaysFalse.getName()),
                                                is(alwaysException.getName()),
                                                is(trueForTestUsers.getName()),
                                                is(trueForAdmins.getName())
                                            )
                                    )),
                            JsonPathMatchers.hasJsonPath("$._embedded.feature.resourcetypes",
                                    Matchers.hasItem(is("authorization"))),
                            JsonPathMatchers.hasJsonPath("$.id",
                                    Matchers.anyOf(
                                            Matchers.startsWith(eperson.getID().toString()),
                                            Matchers.endsWith(site.getType() + "_" + site.getID()))))
                                    )
                    )
            )
            .andExpect(jsonPath("$._links.self.href",
                    Matchers.containsString("/api/authz/authorizations/search/object")))
            .andExpect(jsonPath("$.page.size", is(20)))
            .andExpect(jsonPath("$.page.totalElements", greaterThanOrEqualTo(1)));

        // verify that it works for administrators inspecting anonymous users
        getClient(adminToken).perform(get("/api/authz/authorizations/search/object")
                .param("uri", siteUri))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$._embedded.authorizations", Matchers.hasSize(greaterThanOrEqualTo(1))))
            .andExpect(jsonPath("$._embedded.authorizations", Matchers.everyItem(
                    Matchers.anyOf(
                            JsonPathMatchers.hasJsonPath("$.type", is("authorization")),
                            JsonPathMatchers.hasJsonPath("$._embedded.feature",
                                    Matchers.not(Matchers.anyOf(
                                                is(alwaysFalse.getName()),
                                                is(alwaysException.getName()),
                                                is(trueForTestUsers.getName()),
                                                is(trueForAdmins.getName())
                                            )
                                    )),
                            JsonPathMatchers.hasJsonPath("$._embedded.feature.resourcetypes",
                                    Matchers.hasItem(is("authorization"))),
                            JsonPathMatchers.hasJsonPath("$.id",
                                    Matchers.anyOf(
                                            Matchers.startsWith(eperson.getID().toString()),
                                            Matchers.endsWith(site.getType() + "_" + site.getID()))))
                                    )
                    )
            )
            .andExpect(jsonPath("$._links.self.href",
                    Matchers.containsString("/api/authz/authorizations/search/object")))
            .andExpect(jsonPath("$.page.size", is(20)))
            .andExpect(jsonPath("$.page.totalElements", greaterThanOrEqualTo(1)));
    }

    @Test
    /**
     * Verify that the findByObject return an empty page when the requested object doesn't exist but the uri is
     * potentially valid (i.e. deleted object)
     * 
     * @throws Exception
     */
    public void findByNotExistingObjectTest() throws Exception {
        String wrongSiteUri = "http://localhost/api/core/sites/" + UUID.randomUUID();

        // disarm the alwaysThrowExceptionFeature
        configurationService.setProperty("org.dspace.app.rest.authorization.AlwaysThrowExceptionFeature.turnoff", true);
        // verify that it works for administrators, no result
        String adminToken = getAuthToken(admin.getEmail(), password);
        getClient(adminToken).perform(get("/api/authz/authorizations/search/object")
                .param("uri", wrongSiteUri)
                .param("eperson", admin.getID().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", JsonPathMatchers.hasNoJsonPath("$._embedded.authorizations")))
            .andExpect(jsonPath("$._links.self.href",
                    Matchers.containsString("/api/authz/authorizations/search/object")))
            .andExpect(jsonPath("$.page.size", is(20)))
            .andExpect(jsonPath("$.page.totalElements", is(0)));

        // verify that it works for normal loggedin users
        String epersonToken = getAuthToken(eperson.getEmail(), password);
        getClient(epersonToken).perform(get("/api/authz/authorizations/search/object")
                .param("uri", wrongSiteUri)
                .param("eperson", eperson.getID().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", JsonPathMatchers.hasNoJsonPath("$._embedded.authorizations")))
            .andExpect(jsonPath("$._links.self.href",
                    Matchers.containsString("/api/authz/authorizations/search/object")))
            .andExpect(jsonPath("$.page.size", is(20)))
            .andExpect(jsonPath("$.page.totalElements", is(0)));

        // verify that it works for administators inspecting other users
        getClient(adminToken).perform(get("/api/authz/authorizations/search/object")
                .param("uri", wrongSiteUri)
                .param("eperson", eperson.getID().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", JsonPathMatchers.hasNoJsonPath("$._embedded.authorizations")))
            .andExpect(jsonPath("$._links.self.href",
                    Matchers.containsString("/api/authz/authorizations/search/object")))
            .andExpect(jsonPath("$.page.size", is(20)))
            .andExpect(jsonPath("$.page.totalElements", is(0)));

        // verify that it works for anonymous users
        getClient().perform(get("/api/authz/authorizations/search/object")
                .param("uri", wrongSiteUri))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", JsonPathMatchers.hasNoJsonPath("$._embedded.authorizations")))
            .andExpect(jsonPath("$._links.self.href",
                    Matchers.containsString("/api/authz/authorizations/search/object")))
            .andExpect(jsonPath("$.page.size", is(20)))
            .andExpect(jsonPath("$.page.totalElements", is(0)));

        // verify that it works for administrators inspecting anonymous users
        getClient(adminToken).perform(get("/api/authz/authorizations/search/object")
                .param("uri", wrongSiteUri))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", JsonPathMatchers.hasNoJsonPath("$._embedded.authorizations")))
            .andExpect(jsonPath("$._links.self.href",
                    Matchers.containsString("/api/authz/authorizations/search/object")))
            .andExpect(jsonPath("$.page.size", is(20)))
            .andExpect(jsonPath("$.page.totalElements", is(0)));
    }

    @Test
    /**
     * Verify that the findByObject return the 400 Bad Request response for invalid or missing URI (required parameter)
     * 
     * @throws Exception
     */
    public void findByObjectBadRequestTest() throws Exception {
        String[] invalidUris = new String[] {
                "invalid-uri",
                "",
                "http://localhost/api/wrongcategory/wrongmodel/1",
                "http://localhost/api/core/sites/this-is-not-an-uuid"
        };

        // disarm the alwaysThrowExceptionFeature
        configurationService.setProperty("org.dspace.app.rest.authorization.AlwaysThrowExceptionFeature.turnoff", true);

        for (String invalidUri : invalidUris) {
            System.out.println("Testing the URI: " + invalidUri);
            // verify that it works for administrators with an invalid or missing uri
            String adminToken = getAuthToken(admin.getEmail(), password);
            getClient(adminToken).perform(get("/api/authz/authorizations/search/object")
                    .param("uri", invalidUri)
                    .param("eperson", admin.getID().toString()))
                .andExpect(status().isBadRequest());

            // verify that it works for normal loggedin users with an invalid or missing uri
            String epersonToken = getAuthToken(eperson.getEmail(), password);
            getClient(epersonToken).perform(get("/api/authz/authorizations/search/object")
                    .param("uri", invalidUri)
                    .param("eperson", eperson.getID().toString()))
                .andExpect(status().isBadRequest());

            // verify that it works for administators inspecting other users with an invalid or missing uri
            getClient(adminToken).perform(get("/api/authz/authorizations/search/object")
                    .param("uri", invalidUri)
                    .param("eperson", eperson.getID().toString()))
                .andExpect(status().isBadRequest());

            // verify that it works for anonymous users with an invalid or missing uri
            getClient().perform(get("/api/authz/authorizations/search/object")
                    .param("uri", invalidUri))
                .andExpect(status().isBadRequest());

            // verify that it works for administrators inspecting anonymous users with an invalid or missing uri
            getClient(adminToken).perform(get("/api/authz/authorizations/search/object")
                    .param("uri", invalidUri))
                .andExpect(status().isBadRequest());
        }
        //FIXME add once https://github.com/DSpace/DSpace/pull/2668 is merged
        //        getClient(adminToken).perform(get("/api/authz/authorizations/search/object")
        //                .param("eperson", admin.getID().toString()))
        //            .andExpect(status().isBadRequest());
        //        getClient(epersonToken).perform(get("/api/authz/authorizations/search/object")
        //                .param("eperson", eperson.getID().toString()))
        //            .andExpect(status().isBadRequest());
        //        getClient(adminToken).perform(get("/api/authz/authorizations/search/object")
        //                .param("eperson", eperson.getID().toString()))
        //            .andExpect(status().isBadRequest());
        //        getClient().perform(get("/api/authz/authorizations/search/object"))
        //            .andExpect(status().isBadRequest());
        //        getClient(adminToken).perform(get("/api/authz/authorizations/search/object"))
        //            .andExpect(status().isBadRequest());
    }

    @Test
    /**
     * Verify that the findByObject return the 401 Unauthorized response when an eperson is involved
     * 
     * @throws Exception
     */
    public void findByObjectUnauthorizedTest() throws Exception {
        Site site = siteService.findSite(context);
        SiteRest siteRest = converterService.toRest(site, converterService.getProjection(DefaultProjection.NAME));
        String siteUri = utils.linkToSingleResource(siteRest, "self").getHref();

        // disarm the alwaysThrowExceptionFeature
        configurationService.setProperty("org.dspace.app.rest.authorization.AlwaysThrowExceptionFeature.turnoff", true);

        getClient().perform(get("/api/authz/authorizations/search/object")
                .param("uri", siteUri)
                .param("eperson", admin.getID().toString()))
            .andExpect(status().isUnauthorized());

        // verify that it works for normal loggedin users with an invalid or missing uri
        getClient().perform(get("/api/authz/authorizations/search/object")
                .param("uri", siteUri)
                .param("eperson", eperson.getID().toString()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    /**
     * Verify that the findByObject return the 403 Forbidden response when a non-admin eperson try to search the
     * authorization of another eperson
     * 
     * @throws Exception
     */
    public void findByObjectForbiddenTest() throws Exception {
        Site site = siteService.findSite(context);
        SiteRest siteRest = converterService.toRest(site, converterService.getProjection(DefaultProjection.NAME));
        String siteUri = utils.linkToSingleResource(siteRest, "self").getHref();
        context.turnOffAuthorisationSystem();
        EPerson anotherEperson = EPersonBuilder.createEPerson(context).withEmail("another@example.com")
                .withPassword(password).build();
        context.restoreAuthSystemState();
        // disarm the alwaysThrowExceptionFeature
        configurationService.setProperty("org.dspace.app.rest.authorization.AlwaysThrowExceptionFeature.turnoff", true);
        String anotherToken = getAuthToken(anotherEperson.getEmail(), password);
        // verify that he cannot search the admin authorizations
        getClient(anotherToken).perform(get("/api/authz/authorizations/search/object")
                .param("uri", siteUri)
                .param("eperson", admin.getID().toString()))
            .andExpect(status().isForbidden());

        // verify that he cannot search the authorizations of another "normal" eperson
        getClient(anotherToken).perform(get("/api/authz/authorizations/search/object")
                .param("uri", siteUri)
                .param("eperson", eperson.getID().toString()))
            .andExpect(status().isForbidden());
    }

    @Test
    /**
     * Verify that an exception in the feature check will be reported back
     * @throws Exception
     */
    public void findByObjectInternalServerErrorTest() throws Exception {
        Site site = siteService.findSite(context);
        SiteRest siteRest = converterService.toRest(site, converterService.getProjection(DefaultProjection.NAME));
        String siteUri = utils.linkToSingleResource(siteRest, "self").getHref();

        // verify that it works for administrators
        String adminToken = getAuthToken(admin.getEmail(), password);
        getClient(adminToken).perform(get("/api/authz/authorizations/search/object")
                .param("uri", siteUri)
                // use a large page so that the alwaysThrowExceptionFeature is invoked
                // this could become insufficient at some point
                .param("size", "100")
                .param("eperson", admin.getID().toString()))
            .andExpect(status().isInternalServerError());

        // verify that it works for normal loggedin users
        String epersonToken = getAuthToken(eperson.getEmail(), password);
        getClient(epersonToken).perform(get("/api/authz/authorizations/search/object")
                .param("uri", siteUri)
                // use a large page so that the alwaysThrowExceptionFeature is invoked
                // this could become insufficient at some point
                .param("size", "100")
                .param("eperson", eperson.getID().toString()))
            .andExpect(status().isInternalServerError());

        // verify that it works for anonymous users
        getClient().perform(get("/api/authz/authorizations/search/object")
                .param("uri", siteUri)
                // use a large page so that the alwaysThrowExceptionFeature is invoked
                // this could become insufficient at some point
                .param("size", "100"))
            .andExpect(status().isInternalServerError());
    }

    @Test
    /**
     * Verify that the search by object and feature works properly in allowed scenarios:
     * - for an administrator
     * - for an administrator that want to inspect permission of the anonymous users or another user
     * - for a logged-in "normal" user
     * - for anonymous
     * 
     * @throws Exception
     */
    public void findByObjectAndFeatureTest() throws Exception {
        context.turnOffAuthorisationSystem();
        Community com = CommunityBuilder.createCommunity(context).withName("A test community").build();
        CommunityRest comRest = converterService.toRest(com, converterService.getProjection(DefaultProjection.NAME));
        String comUri = utils.linkToSingleResource(comRest, "self").getHref();
        context.restoreAuthSystemState();

        // verify that it works for administrators
        String adminToken = getAuthToken(admin.getEmail(), password);
        getClient(adminToken).perform(get("/api/authz/authorizations/search/objectAndFeature")
                .param("uri", comUri)
                .param("feature", alwaysTrue.getName())
                .param("eperson", admin.getID().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.type", is("authorization")))
            .andExpect(jsonPath("$._embedded.feature.id", is(alwaysTrue.getName())))
                .andExpect(jsonPath("$.id", Matchers.is(admin.getID().toString() + "_" + alwaysTrue.getName() + "_"
                        + com.getType() + "_" + com.getID())));

        // verify that it works for normal loggedin users
        String epersonToken = getAuthToken(eperson.getEmail(), password);
        getClient(epersonToken).perform(get("/api/authz/authorizations/search/objectAndFeature")
                .param("uri", comUri)
                .param("feature", alwaysTrue.getName())
                .param("eperson", eperson.getID().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.type", is("authorization")))
            .andExpect(jsonPath("$._embedded.feature.id", is(alwaysTrue.getName())))
                .andExpect(jsonPath("$.id", Matchers.is(eperson.getID().toString() + "_" + alwaysTrue.getName() + "_"
                        + com.getType() + "_" + com.getID())));

        // verify that it works for administators inspecting other users
        getClient(adminToken).perform(get("/api/authz/authorizations/search/objectAndFeature")
                .param("uri", comUri)
                .param("feature", alwaysTrue.getName())
                .param("eperson", eperson.getID().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.type", is("authorization")))
            .andExpect(jsonPath("$._embedded.feature.id", is(alwaysTrue.getName())))
                .andExpect(jsonPath("$.id", Matchers.is(eperson.getID().toString() + "_" + alwaysTrue.getName() + "_"
                        + com.getType() + "_" + com.getID())));

        // verify that it works for anonymous users
        getClient().perform(get("/api/authz/authorizations/search/objectAndFeature")
                .param("uri", comUri)
                .param("feature", alwaysTrue.getName()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.type", is("authorization")))
            .andExpect(jsonPath("$._embedded.feature.id", is(alwaysTrue.getName())))
            .andExpect(jsonPath("$.id",Matchers.is(alwaysTrue.getName() + "_" + com.getType() + "_" + com.getID())));

        // verify that it works for administrators inspecting anonymous users
        getClient(adminToken).perform(get("/api/authz/authorizations/search/objectAndFeature")
                .param("uri", comUri)
                .param("feature", alwaysTrue.getName()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.type", is("authorization")))
            .andExpect(jsonPath("$._embedded.feature.id", is(alwaysTrue.getName())))
            .andExpect(jsonPath("$.id",Matchers.is(alwaysTrue.getName() + "_" + com.getType() + "_" + com.getID())));
    }

    @Test
    /**
     * Verify that the search by object and feature works return 204 No Content when a feature is not granted
     * 
     * @throws Exception
     */
    public void findByObjectAndFeatureNotGrantedTest() throws Exception {
        Site site = siteService.findSite(context);
        SiteRest siteRest = converterService.toRest(site, converterService.getProjection(DefaultProjection.NAME));
        String siteUri = utils.linkToSingleResource(siteRest, "self").getHref();

        // verify that it works for administrators
        String adminToken = getAuthToken(admin.getEmail(), password);
        getClient(adminToken).perform(get("/api/authz/authorizations/search/objectAndFeature")
                .param("uri", siteUri)
                .param("feature", alwaysFalse.getName())
                .param("eperson", admin.getID().toString()))
            .andExpect(status().isNoContent());

        // verify that it works for normal loggedin users
        String epersonToken = getAuthToken(eperson.getEmail(), password);
        getClient(epersonToken).perform(get("/api/authz/authorizations/search/objectAndFeature")
                .param("uri", siteUri)
                .param("feature", trueForAdmins.getName())
                .param("eperson", eperson.getID().toString()))
            .andExpect(status().isNoContent());

        // verify that it works for administators inspecting other users
        getClient(adminToken).perform(get("/api/authz/authorizations/search/objectAndFeature")
                .param("uri", siteUri)
                .param("feature", trueForAdmins.getName())
                .param("eperson", eperson.getID().toString()))
            .andExpect(status().isNoContent());

        // verify that it works for anonymous users
        getClient().perform(get("/api/authz/authorizations/search/objectAndFeature")
                .param("uri", siteUri)
                .param("feature", trueForLoggedUsers.getName()))
            .andExpect(status().isNoContent());

        // verify that it works for administrators inspecting anonymous users
        getClient(adminToken).perform(get("/api/authz/authorizations/search/objectAndFeature")
                .param("uri", siteUri)
                .param("feature", trueForLoggedUsers.getName()))
            .andExpect(status().isNoContent());
    }

    @Test
    /**
     * Verify that the findByObject return the 204 No Content code when the requested object doesn't exist but the uri
     * is potentially valid (i.e. deleted object) or the feature doesn't exist
     * 
     * @throws Exception
     */
    public void findByNotExistingObjectAndFeatureTest() throws Exception {
        String wrongSiteUri = "http://localhost/api/core/sites/" + UUID.randomUUID();
        Site site = siteService.findSite(context);
        SiteRest siteRest = converterService.toRest(site, converterService.getProjection(DefaultProjection.NAME));
        String siteUri = utils.linkToSingleResource(siteRest, "self").getHref();

        // disarm the alwaysThrowExceptionFeature
        configurationService.setProperty("org.dspace.app.rest.authorization.AlwaysThrowExceptionFeature.turnoff", true);
        // verify that it works for administrators, no result
        String adminToken = getAuthToken(admin.getEmail(), password);
        getClient(adminToken).perform(get("/api/authz/authorizations/search/objectAndFeature")
                .param("uri", wrongSiteUri)
                .param("feature", alwaysTrue.getName())
                .param("eperson", admin.getID().toString()))
            .andExpect(status().isNoContent());

        getClient(adminToken).perform(get("/api/authz/authorizations/search/objectAndFeature")
                .param("uri", siteUri)
                .param("feature", "not-existing-feature")
                .param("eperson", admin.getID().toString()))
            .andExpect(status().isNoContent());

        // verify that it works for normal loggedin users
        String epersonToken = getAuthToken(eperson.getEmail(), password);
        getClient(epersonToken).perform(get("/api/authz/authorizations/search/objectAndFeature")
                .param("uri", wrongSiteUri)
                .param("feature", alwaysTrue.getName())
                .param("eperson", eperson.getID().toString()))
            .andExpect(status().isNoContent());

        getClient(epersonToken).perform(get("/api/authz/authorizations/search/objectAndFeature")
                .param("uri", siteUri)
                .param("feature", "not-existing-feature")
                .param("eperson", eperson.getID().toString()))
            .andExpect(status().isNoContent());

        // verify that it works for administators inspecting other users
        getClient(adminToken).perform(get("/api/authz/authorizations/search/objectAndFeature")
                .param("uri", wrongSiteUri)
                .param("feature", alwaysTrue.getName())
                .param("eperson", eperson.getID().toString()))
            .andExpect(status().isNoContent());

        getClient(adminToken).perform(get("/api/authz/authorizations/search/objectAndFeature")
                .param("uri", siteUri)
                .param("feature", "not-existing-feature")
                .param("eperson", eperson.getID().toString()))
            .andExpect(status().isNoContent());

        // verify that it works for anonymous users
        getClient().perform(get("/api/authz/authorizations/search/objectAndFeature")
                .param("uri", wrongSiteUri)
                .param("feature", alwaysTrue.getName()))
            .andExpect(status().isNoContent());

        getClient().perform(get("/api/authz/authorizations/search/objectAndFeature")
                .param("uri", siteUri)
                .param("feature", "not-existing-feature"))
            .andExpect(status().isNoContent());

        // verify that it works for administrators inspecting anonymous users
        getClient(adminToken).perform(get("/api/authz/authorizations/search/objectAndFeature")
                .param("uri", wrongSiteUri)
                .param("feature", alwaysTrue.getName()))
            .andExpect(status().isNoContent());

        getClient(adminToken).perform(get("/api/authz/authorizations/search/objectAndFeature")
                .param("uri", siteUri)
                .param("feature", "not-existing-feature"))
            .andExpect(status().isNoContent());
    }

    @Test
    /**
     * Verify that the findByObject return the 400 Bad Request response for invalid or missing URI or feature (required
     * parameters)
     * 
     * @throws Exception
     */
    public void findByObjectAndFeatureBadRequestTest() throws Exception {
        String[] invalidUris = new String[] {
                "invalid-uri",
                "",
                "http://localhost/api/wrongcategory/wrongmodel/1",
                "http://localhost/api/core/sites/this-is-not-an-uuid"
        };
        Site site = siteService.findSite(context);
        SiteRest siteRest = converterService.toRest(site, converterService.getProjection(DefaultProjection.NAME));
        String siteUri = utils.linkToSingleResource(siteRest, "self").getHref();
        // disarm the alwaysThrowExceptionFeature
        configurationService.setProperty("org.dspace.app.rest.authorization.AlwaysThrowExceptionFeature.turnoff", true);

        for (String invalidUri : invalidUris) {
            System.out.println("Testing the URI: " + invalidUri);
            // verify that it works for administrators with an invalid or missing uri
            String adminToken = getAuthToken(admin.getEmail(), password);
            getClient(adminToken).perform(get("/api/authz/authorizations/search/objectAndFeature")
                    .param("uri", invalidUri)
                    .param("feature", alwaysTrue.getName())
                    .param("eperson", admin.getID().toString()))
                .andExpect(status().isBadRequest());

            // verify that it works for normal loggedin users with an invalid or missing uri
            String epersonToken = getAuthToken(eperson.getEmail(), password);
            getClient(epersonToken).perform(get("/api/authz/authorizations/search/object")
                    .param("uri", invalidUri)
                    .param("feature", alwaysTrue.getName())
                    .param("eperson", eperson.getID().toString()))
                .andExpect(status().isBadRequest());

            // verify that it works for administators inspecting other users with an invalid or missing uri
            getClient(adminToken).perform(get("/api/authz/authorizations/search/object")
                    .param("uri", invalidUri)
                    .param("feature", alwaysTrue.getName())
                    .param("eperson", eperson.getID().toString()))
                .andExpect(status().isBadRequest());

            // verify that it works for anonymous users with an invalid or missing uri
            getClient().perform(get("/api/authz/authorizations/search/object")
                    .param("uri", invalidUri)
                    .param("feature", alwaysTrue.getName()))
                .andExpect(status().isBadRequest());

            // verify that it works for administrators inspecting anonymous users with an invalid or missing uri
            getClient(adminToken).perform(get("/api/authz/authorizations/search/object")
                    .param("uri", invalidUri)
                    .param("feature", alwaysTrue.getName()))
                .andExpect(status().isBadRequest());
        }

        //FIXME add once https://github.com/DSpace/DSpace/pull/2668 is merged
        //        getClient(adminToken).perform(get("/api/authz/authorizations/search/object")
        //                .param("eperson", admin.getID().toString()))
        //            .andExpect(status().isBadRequest());
        //        getClient(epersonToken).perform(get("/api/authz/authorizations/search/object")
        //                .param("eperson", eperson.getID().toString()))
        //            .andExpect(status().isBadRequest());
        //        getClient(adminToken).perform(get("/api/authz/authorizations/search/object")
        //                .param("eperson", eperson.getID().toString()))
        //            .andExpect(status().isBadRequest());
        //        getClient().perform(get("/api/authz/authorizations/search/object"))
        //            .andExpect(status().isBadRequest());
        //        getClient(adminToken).perform(get("/api/authz/authorizations/search/object"))
        //            .andExpect(status().isBadRequest());

        //        getClient(adminToken).perform(get("/api/authz/authorizations/search/object")
        //                .param("uri", siteUri.getID().toString()))
        //                .param("eperson", admin.getID().toString()))
        //            .andExpect(status().isBadRequest());
        //        getClient(epersonToken).perform(get("/api/authz/authorizations/search/object")
        //                .param("uri", siteUri.getID().toString()))
        //                .param("eperson", eperson.getID().toString()))
        //            .andExpect(status().isBadRequest());
        //        getClient(adminToken).perform(get("/api/authz/authorizations/search/object")
        //                .param("uri", siteUri.getID().toString()))
        //                .param("eperson", eperson.getID().toString()))
        //            .andExpect(status().isBadRequest());
        //        getClient().perform(get("/api/authz/authorizations/search/object")
        //                .param("uri", siteUri.getID().toString())))
        //            .andExpect(status().isBadRequest());
        //        getClient(adminToken).perform(get("/api/authz/authorizations/search/object")
        //                .param("uri", siteUri.getID().toString())))
        //            .andExpect(status().isBadRequest());
    }

    @Test
    /**
     * Verify that the findByObjectAndFeature return the 401 Unauthorized response when an eperson is involved
     * 
     * @throws Exception
     */
    public void findByObjectAndFeatureUnauthorizedTest() throws Exception {
        Site site = siteService.findSite(context);
        SiteRest siteRest = converterService.toRest(site, converterService.getProjection(DefaultProjection.NAME));
        String siteUri = utils.linkToSingleResource(siteRest, "self").getHref();

        // disarm the alwaysThrowExceptionFeature
        configurationService.setProperty("org.dspace.app.rest.authorization.AlwaysThrowExceptionFeature.turnoff", true);

        getClient().perform(get("/api/authz/authorizations/search/objectAndFeature")
                .param("uri", siteUri)
                .param("feature", alwaysTrue.getName())
                .param("eperson", admin.getID().toString()))
            .andExpect(status().isUnauthorized());

        // verify that it works for normal loggedin users with an invalid or missing uri
        getClient().perform(get("/api/authz/authorizations/search/objectAndFeature")
                .param("uri", siteUri)
                .param("feature", alwaysTrue.getName())
                .param("eperson", eperson.getID().toString()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    /**
     * Verify that the findByObjectAndFeature return the 403 Forbidden response when a non-admin eperson try to search
     * the authorization of another eperson
     * 
     * @throws Exception
     */
    public void findByObjectAndFeatureForbiddenTest() throws Exception {
        Site site = siteService.findSite(context);
        SiteRest siteRest = converterService.toRest(site, converterService.getProjection(DefaultProjection.NAME));
        String siteUri = utils.linkToSingleResource(siteRest, "self").getHref();
        context.turnOffAuthorisationSystem();
        EPerson anotherEperson = EPersonBuilder.createEPerson(context).withEmail("another@example.com")
                .withPassword(password).build();
        context.restoreAuthSystemState();
        // disarm the alwaysThrowExceptionFeature
        configurationService.setProperty("org.dspace.app.rest.authorization.AlwaysThrowExceptionFeature.turnoff", true);
        String anotherToken = getAuthToken(anotherEperson.getEmail(), password);
        // verify that he cannot search the admin authorizations
        getClient(anotherToken).perform(get("/api/authz/authorizations/search/objectAndFeature")
                .param("uri", siteUri)
                .param("feature", alwaysTrue.getName())
                .param("eperson", admin.getID().toString()))
            .andExpect(status().isForbidden());

        // verify that he cannot search the authorizations of another "normal" eperson
        getClient(anotherToken).perform(get("/api/authz/authorizations/search/objectAndFeature")
                .param("uri", siteUri)
                .param("feature", alwaysTrue.getName())
                .param("eperson", eperson.getID().toString()))
            .andExpect(status().isForbidden());
    }

    @Test
    /**
     * Verify that an exception in the feature check will be reported back
     * @throws Exception
     */
    public void findByObjectAndFeatureInternalServerErrorTest() throws Exception {
        Site site = siteService.findSite(context);
        SiteRest siteRest = converterService.toRest(site, converterService.getProjection(DefaultProjection.NAME));
        String siteUri = utils.linkToSingleResource(siteRest, "self").getHref();

        // verify that it works for administrators
        String adminToken = getAuthToken(admin.getEmail(), password);
        getClient(adminToken).perform(get("/api/authz/authorizations/search/objectAndFeature")
                .param("uri", siteUri)
                .param("feature", alwaysException.getName())
                .param("eperson", admin.getID().toString()))
            .andExpect(status().isInternalServerError());

        // verify that it works for normal loggedin users
        String epersonToken = getAuthToken(eperson.getEmail(), password);
        getClient(epersonToken).perform(get("/api/authz/authorizations/search/objectAndFeature")
                .param("uri", siteUri)
                .param("feature", alwaysException.getName())
                .param("eperson", eperson.getID().toString()))
            .andExpect(status().isInternalServerError());

        // verify that it works for anonymous users
        getClient().perform(get("/api/authz/authorizations/search/objectAndFeature")
                .param("uri", siteUri)
                .param("feature", alwaysException.getName()))
            .andExpect(status().isInternalServerError());
    }

    // utility methods to build authorization ID without having an authorization object
    private String getAuthorizationID(EPerson eperson, AuthorizationFeature feature, FindableObject obj) {
        return getAuthorizationID(eperson != null ? eperson.getID().toString() : null, feature.getName(),
                String.valueOf(obj.getType()), obj.getID());
    }

    private String getAuthorizationID(UUID epersonUuid, AuthorizationFeature feature, FindableObject obj) {
        return getAuthorizationID(epersonUuid != null ? epersonUuid.toString() : null, feature.getName(),
                String.valueOf(obj.getType()), obj.getID());
    }

    private String getAuthorizationID(EPerson eperson, String featureName, FindableObject obj) {
        return getAuthorizationID(eperson != null ? eperson.getID().toString() : null, featureName,
                String.valueOf(obj.getType()), obj.getID());
    }

    private String getAuthorizationID(EPerson eperson, AuthorizationFeature feature, int objType, Serializable objID) {
        return getAuthorizationID(eperson != null ? eperson.getID().toString() : null, feature.getName(),
                String.valueOf(objType), objID);
    }

    private String getAuthorizationID(String epersonUuid, String featureName, String type, Serializable id) {
        return (epersonUuid != null ? epersonUuid + "_" : "") + featureName + "_" + type + "_"
                + id.toString();
    }

}