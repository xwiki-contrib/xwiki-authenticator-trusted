/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.xwiki.contrib.authentication.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.phase.Initializable;
import org.xwiki.component.phase.InitializationException;
import org.xwiki.contrib.authentication.AuthenticationPersistenceStore;
import org.xwiki.contrib.authentication.DynamicRoleConfiguration;
import org.xwiki.contrib.authentication.TrustedAuthenticationAdapter;
import org.xwiki.contrib.authentication.TrustedAuthenticationConfiguration;
import org.xwiki.contrib.authentication.TrustedAuthenticator;
import org.xwiki.contrib.authentication.UserManager;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.observation.ObservationManager;
import org.xwiki.security.authentication.UserAuthenticatedEvent;
import org.xwiki.user.UserReferenceResolver;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;

/**
 * Default implementation of the {@link TrustedAuthenticator} role.
 *
 * @version $Id$
 */
@Component
@Singleton
public class DefaultTrustedAuthenticator implements TrustedAuthenticator, Initializable
{
    private static final EntityReference USER_SPACE_REFERENCE = new EntityReference("XWiki", EntityType.SPACE);

    @Inject
    private Logger logger;

    @Inject
    private Provider<XWikiContext> contextProvider;

    @Inject
    private TrustedAuthenticationConfiguration configuration;

    @Inject
    private UserManager userManager;

    @Inject
    private DocumentReferenceResolver<EntityReference> defaultEntityDocumentReferenceResolver;

    @Inject
    private DocumentReferenceResolver<String> defaultStringDocumentReferenceResolver;

    @Inject
    private EntityReferenceSerializer<String> defaultStringEntityReferenceSerializer;

    @Inject
    @Named("local")
    private EntityReferenceSerializer<String> localStringEntityReferenceSerializer;

    @Inject
    private ObservationManager observation;

    @Inject
    @Named("document")
    private UserReferenceResolver<DocumentReference> userReferenceResolver;

    private TrustedAuthenticationAdapter authenticationAdapter;

    private AuthenticationPersistenceStore persistenceStore;

    /**
     * Cache of the group mapping.
     */
    private Map<DocumentReference, Collection<String>> groupMappings;

    /**
     * Cache of the logout pattern matcher.
     */
    private RequestMatcher logoutMatcher;

    @Override
    public void initialize() throws InitializationException
    {
        authenticationAdapter = configuration.getAuthenticationAdapter();
        persistenceStore = configuration.getPersistenceStore();
    }

    @Override
    public DocumentReference authenticate()
    {
        logger.debug("Starting trusted authentication...");

        DocumentReference authenticatedUser = authenticate(persistenceStore.retrieve());

        if (isLogoutRequest()) {
            wrapResponseForLogoutRediction();
            persistenceStore.clear();
        }

        return authenticatedUser;
    }

    /**
     * Proceed to the authentication, checking with the adapter if the previously authenticated user is not trusted, and
     * creating and synchronizing user profile as needed.
     *
     * @param currentUser the currently authenticated user (serialized reference of his profile name).
     * @return the authenticated user (document reference to the user profile).
     */
    private DocumentReference authenticate(String currentUser)
    {
        if (configuration.isPersistenceStoreTrusted()) {
            if (currentUser != null) {
                logger.debug("User [{}] authenticated from trusted persistence store.", currentUser);
                return defaultStringDocumentReferenceResolver.resolve(currentUser);
            }
        }

        return authenticate(currentUser, authenticationAdapter.getUserUid());
    }

    /**
     * Proceed to the authentication. When the adapter return a null UID, if the persistent store is trusted on missing
     * authentication the persisted user is used straight, else the persistence store is cleared and public access is
     * returned. When the adapter provide a valid user UID, this one is used, and the user and groups are synchronized`
     * only if it differ from the user available in the persistent store.
     *
     * @param previouslyAuthenticatedUser the previously authenticated user (serialized reference of his profile name).
     * @param userUid the new user UID effectively connected.
     * @return the authenticated user (document reference to the user profile).
     */
    private DocumentReference authenticate(String previouslyAuthenticatedUser, String userUid)
    {
        // Sometimes Auth adapters/Auth systems might return an empty (non-null) value. Therefore strict checking here.
        if (StringUtils.isBlank(userUid)) {
            logger.debug("No user available from trusted authenticator.");
            if (previouslyAuthenticatedUser != null) {
                if (configuration.isPersistenceStoreTrustedOnMissingAuthentication()) {
                    logger.debug("User [{}] authenticated from 'trusted on missing authentication' persistence store .",
                        previouslyAuthenticatedUser);
                    return defaultStringDocumentReferenceResolver.resolve(previouslyAuthenticatedUser);
                } else {
                    logger.debug("Clearing persistenceStore, removing [{}].", previouslyAuthenticatedUser);
                    persistenceStore.clear();
                }
            }
            logger.debug("Trusted authentication ended with public access.");
            return null;
        }
        logger.debug("User [{}] retrieved from the authentication adapter.", userUid);

        return authenticate(previouslyAuthenticatedUser, getUserProfileReference(userUid));
    }

    /**
     * Proceed to the authentication, creating and synchronizing user profile as needed.
     *
     * @param previouslyAuthenticatedUser the previously authenticated user (serialized reference of his profile name).
     * @param userProfile the reference of the profile of the user effectively connected (may need to be created).
     * @return the authenticated user (document reference to the user profile).
     */
    private DocumentReference authenticate(String previouslyAuthenticatedUser, DocumentReference userProfile)
    {
        String authenticatedUser = defaultStringEntityReferenceSerializer.serialize(userProfile);

        if (previouslyAuthenticatedUser != null) {
            logger.debug("User [{}] retrieved from untrusted persistence store.", previouslyAuthenticatedUser);
            if (authenticatedUser.equals(previouslyAuthenticatedUser)) {
                logger.debug("User [{}] authenticated from the authentication adapter, no synchronization.",
                    userProfile);
                return userProfile;
            } else {
                logger.debug("Authentication changed, clearing persistenceStore, removing [{}].",
                    previouslyAuthenticatedUser);
                persistenceStore.clear();
            }
        }

        if (!synchronizeUser(userProfile)) {
            logger.error("Unable to synchronize user profile for user [{}], ended with public access.",
                authenticatedUser);
            return null;
        }

        persistenceStore.store(authenticatedUser);
        logger.debug("User [{}] authenticated from the authentication adapter and saved to persistence store.",
            authenticatedUser);

        // Notify listeners about this new authentication
        this.observation.notify(new UserAuthenticatedEvent(this.userReferenceResolver.resolve(userProfile)), null);

        return userProfile;
    }

    /**
     * Return the reference to the user profile of the user being authenticated.
     *
     * @param userUid the userUid retrieved from the authentication adapter (could not be any other userUid).
     * @return the reference to the user profile (existing or to be created).
     */
    private DocumentReference getUserProfileReference(String userUid)
    {
        // Sometimes Auth adapters/Auth systems might return an empty (non-null) value. Therefore strict checking here.
        String userName = authenticationAdapter.getUserName();
        if (StringUtils.isBlank(userName)) {
            throw new UnsupportedOperationException("Cannot work with an empty username!");
        }

        if (!userUid.equals(userName)) {
            throw new UnsupportedOperationException("Not yet implemented");
        }

        return defaultEntityDocumentReferenceResolver.resolve(
            new EntityReference(getCleanedUpUsername(getCaseNormalizedName(userName)), EntityType.DOCUMENT),
            USER_SPACE_REFERENCE);
    }

    /**
     * Apply replacements to clean up username for defining the user profile name.
     *
     * @param userName userName to clean
     * @return cleaned up userName
     */
    private String getCleanedUpUsername(String userName)
    {
        String result = userName;
        for (Map.Entry<String, String> replacement : configuration.getUserProfileReplacements().entrySet()) {
            result = result.replace(replacement.getKey(), replacement.getValue());
        }
        return result;
    }

    /**
     * Apply replacements to clean up username for defining the user profile name.
     *
     * @param userName userName to clean
     * @return cleaned up userName
     */
    private String getCaseNormalizedName(String userName)
    {
        switch (configuration.getUserProfileCaseStyle()) {
            case LOWERCASE:
                return userName.toLowerCase();
            case TITLECASE:
                if (userName.length() > 1) {
                    return userName.substring(0, 1).toUpperCase() + userName.substring(1).toLowerCase();
                }
                return userName.toUpperCase();
            case UPPERCASE:
                return userName.toUpperCase();
            default:
                return userName;
        }
    }

    /**
     * Create the user if needed, or synchronize it and synchronize user in mapped groups.
     *
     * @param user the reference of the user document.
     * @return true if the user has been successfully created and/or synchronized.
     */
    private boolean synchronizeUser(DocumentReference user)
    {
        XWikiContext context = contextProvider.get();
        String database = context.getWikiId();
        try {
            // Switch to main wiki to force users to be global users
            context.setWikiId(user.getWikiReference().getName());

            Map<String, String> extInfos = getExtendedInformations();
            // test if user already exists
            if (!context.getWiki().exists(user, context)) {
                logger.debug("Creating user [{}]...", user);
                if (!userManager.createUser(user, extInfos)) {
                    return false;
                }
            } else if (!extInfos.isEmpty()) {
                logger.debug("Synchronizing profile for user [{}]...", user);
                userManager.synchronizeUserProperties(user, extInfos,
                    "Trusted authenticator user profile synchronization");
            }

            if (!synchronizeGroups(user)) {
                return false;
            }
        } finally {
            context.setWikiId(database);
        }
        return true;
    }

    /**
     * @return the user information based on properties mapping.
     */
    protected Map<String, String> getExtendedInformations()
    {
        Map<String, String> extInfos = new HashMap<String, String>();
        Map<String, String> mapping = configuration.getUserPropertyMappings();

        if (mapping.size() > 0) {
            for (Map.Entry<String, String> entry : mapping.entrySet()) {
                String value = authenticationAdapter.getUserProperty(entry.getValue());

                if (StringUtils.isNotBlank(value)) {
                    extInfos.put(entry.getKey(), value.trim());
                }
            }
        }

        return extInfos;
    }

    /**
     * Synchronize the user in mapped groups and in dynamic role groups.
     *
     * @param user the reference of the user document.
     */
    private boolean synchronizeGroups(DocumentReference user)
    {
        Collection<DocumentReference> groupInRefs = new ArrayList<DocumentReference>();
        Collection<DocumentReference> groupOutRefs = new ArrayList<DocumentReference>();
        Collection<DocumentReference> groupInWithAutoCreateRefs = new ArrayList<DocumentReference>();

        populateGroupsFromMappings(groupInRefs, groupOutRefs);

        if (!populateGroupsFromDynamicRoles(user, groupInRefs, groupInWithAutoCreateRefs, groupOutRefs)) {
            return false;
        }

        if (!(groupInRefs.isEmpty() && groupOutRefs.isEmpty() && groupInWithAutoCreateRefs.isEmpty())) {
            logger.debug("Synchronizing groups for user [{}]...", user);
            userManager.synchronizeGroupsMembership(user, groupInRefs, groupInWithAutoCreateRefs, groupOutRefs,
                "Trusted authentication group synchronization");
        }

        return true;
    }

    /**
     * Synchronize the user in mapped groups.
     *
     * @param groupInRefs will be filled with groups the user should be in.
     * @param groupOutRefs will be filled with groups the user should not be in.
     */
    private void populateGroupsFromMappings(Collection<DocumentReference> groupInRefs,
        Collection<DocumentReference> groupOutRefs)
    {
        // Only synchronize groups if a group mapping configuration exists
        Map<DocumentReference, Collection<String>> mappings = getGroupMapping();
        if (mappings.size() > 0) {
            // membership to add
            for (Map.Entry<DocumentReference, Collection<String>> mapping : mappings.entrySet()) {
                DocumentReference groupRef = mapping.getKey();
                Boolean isMember = false;
                for (String role : mapping.getValue()) {
                    if (authenticationAdapter.isUserInRole(role)) {
                        isMember = true;
                        break;
                    }
                }

                if (isMember) {
                    groupInRefs.add(groupRef);
                } else {
                    groupOutRefs.add(groupRef);
                }
            }
        }
    }

    /**
     * @return the group matching this role with the given dynamic role configuration.
     * @param groupInRefs is filled with groups the user should be in.
     * @param groupOutRefs is filled with groups the user should not be in.
     */
    private DocumentReference getGroupForRole(DynamicRoleConfiguration conf, String role)
    {
        if (conf.getRoleRegex().isEmpty() || conf.getReplacement().isEmpty()) {
            String radical =
                role.substring(conf.getRolePrefix().length(), role.length() - conf.getRoleSuffix().length());
            return resolveUserOrGroup(conf.getGroupPrefix() + radical + conf.getGroupSuffix());
        }
        return resolveUserOrGroup(role.replaceFirst(conf.getRoleRegex(), conf.getReplacement()));
    }

    /**
     * @return the groups concerned by the given dynamic role configuration.
     * @param conf the dynamic role configuration to use.
     * @param groups the set of groups to filter.
     */
    private Collection<DocumentReference> groupsMatchingConfiguration(DynamicRoleConfiguration conf,
        Collection<DocumentReference> groups)
    {
        String[] wikiAndGroupPrefix = conf.getGroupPrefix().split(":", 2);
        String unqualifiedGroupPrefix = wikiAndGroupPrefix[wikiAndGroupPrefix.length - 1];

        Collection<DocumentReference> matchingGroups = new ArrayList<DocumentReference>();

        for (DocumentReference group : groups) {
            String g = localStringEntityReferenceSerializer.serialize(group);
            if (g.startsWith(unqualifiedGroupPrefix) && g.endsWith(conf.getGroupSuffix())) {
                logger.debug("Group [{}] matches this configuration", g);
                matchingGroups.add(group);
            } else {
                logger.debug("Group [{}] does not match this configuration", g);
            }
        }

        return matchingGroups;
    }

    /**
     * Fill the groups in which the user should be added, given the roles provided by the authentication adapter and the
     * dynamic role configurations.
     *
     * @param configurations all the dynamic role configurations.
     * @param groupInRefs is filled with the groups the user should be added to, not to be auto-created.
     * @param groupInWithAutoCreateRefs is filled with the groups the user should be added to, to be auto-created.
     * @return whether the operation succeeded.
     */
    private boolean addGroupsFromDynamicRoles(Collection<DynamicRoleConfiguration> configurations,
        Collection<DocumentReference> groupInRefs, Collection<DocumentReference> groupInWithAutoCreateRefs)
    {
        Collection<String> roles = authenticationAdapter.getUserRoles();
        logger.debug("Found roles: [{}]", roles);
        if (roles == null) {
            return false;
        }

        AddGroupToField agtf = new AddGroupToField();
        XWikiContext context = contextProvider.get();

        for (String role : roles) {
            DynamicRoleConfiguration conf = null;
            for (DynamicRoleConfiguration config : configurations) {
                if (config.matchesRole(role)) {
                    conf = config;
                    break;
                }
            }

            if (conf == null) {
                logger.debug("Did not find any dynamic configuration for role [{}]", role);
                continue;
            }

            logger.debug("Found a dynamic configuration for role [{}]: [{}]", role, conf);

            DocumentReference group = getGroupForRole(conf, role);
            if (group == null) {
                continue;
            }

            logger.debug("Found a group for this role: [{}]", group);

            if (conf.isAutoCreate()) {
                logger.debug("This group will be auto created if needed.");
                groupInWithAutoCreateRefs.add(group);
                agtf.add(context, group, role, conf);
            } else {
                logger.debug("This group will not be auto created.");
                groupInRefs.add(group);
            }
        }

        agtf.save(context);
        return true;
    }

    /**
     * Remove from the first parameters groups that are in the second parameter.
     *
     * @param groupsToRemove the collection to remove groups from.
     * @param addedGroups the collection of groups to remove
     */
    private void removeGroups(Collection<DocumentReference> groupsToRemove, Collection<DocumentReference> addedGroups)
    {
        for (DocumentReference addedGroup : addedGroups) {
            String sAddedGroup = defaultStringEntityReferenceSerializer.serialize(addedGroup);
            Iterator<DocumentReference> iter = groupsToRemove.iterator();
            while (iter.hasNext()) {
                String userGroup = defaultStringEntityReferenceSerializer.serialize(iter.next());
                if (userGroup.equals(sAddedGroup)) {
                    iter.remove();
                }
            }
        }
    }

    /**
     * Given the groups the user is in and the groups in which this user needs to be added, and given the dynamic role
     * configurations, fill in the groups from which the user is to be removed.
     *
     * @param configurations all the dynamic role configurations.
     * @param groupInRefs the groups the user is to be added to, that are not to be auto-created.
     * @param groupInWithAutoCreateRefs the groups the user is to be added to, that are to be auto-created.
     * @param groupOutRefs is filled with the groups the user should be removed from.
     * @return whether the operation succeeded.
     */
    private boolean removeGroupsFromDynamicRoles(Collection<DynamicRoleConfiguration> configurations,
        DocumentReference user, Collection<DocumentReference> groupInRefs,
        Collection<DocumentReference> groupInWithAutoCreateRefs, Collection<DocumentReference> groupOutRefs)
    {
        XWikiContext context = contextProvider.get();
        Collection<DocumentReference> userGroupsNotBeingAdded;

        try {
            userGroupsNotBeingAdded =
                context.getWiki().getGroupService(context).getAllGroupsReferencesForMember(user, 0, 0, context);
        } catch (XWikiException e) {
            logger.error("Failed to get user groups [{}]", user, e);
            return false;
        }
        logger.debug("User is in these groups: [{}]", userGroupsNotBeingAdded);

        removeGroups(userGroupsNotBeingAdded, groupInRefs);
        removeGroups(userGroupsNotBeingAdded, groupInWithAutoCreateRefs);

        logger.debug("These groups have not been added: [{}]", userGroupsNotBeingAdded);

        Collection<DocumentReference> matchingGroups = new ArrayList<DocumentReference>();
        for (DynamicRoleConfiguration conf : configurations) {
            logger.debug("Removing groups corresponding to missing roles for configuration [{}].", conf);
            matchingGroups.addAll(groupsMatchingConfiguration(conf, userGroupsNotBeingAdded));
        }
        logger.debug("The user will be removed from these groups: [{}].", matchingGroups);
        groupOutRefs.addAll(matchingGroups);
        return true;
    }

    /**
     * Given the groups the user is in and the dynamic role configurations, fill in the groups to which the user is to
     * be added and from which the user is to be removed.
     *
     * @param DocumentReference all the dynamic role configurations.
     * @param groupInRefs is filled with the groups the user is to be added to, not to be auto-created.
     * @param groupInWithAutoCreateRefs is filled with the groups the user is to be added to, to be auto-created.
     * @param groupOutRefs is filled with the groups the user should be removed from.
     * @return whether the operation succeeded.
     */
    private boolean populateGroupsFromDynamicRoles(DocumentReference user, Collection<DocumentReference> groupInRefs,
        Collection<DocumentReference> groupInWithAutoCreateRefs, Collection<DocumentReference> groupOutRefs)
    {
        Collection<DynamicRoleConfiguration> configs = configuration.getDynamicRoleConfigurations();

        return configs != null && addGroupsFromDynamicRoles(configs, groupInRefs, groupInWithAutoCreateRefs)
            && removeGroupsFromDynamicRoles(configs, user, groupInRefs, groupInWithAutoCreateRefs, groupOutRefs);
    }

    /**
     * @return the document reference for the given user or group, finding it by default in the default user space.
     */
    private DocumentReference resolveUserOrGroup(String userOrGroup)
    {
        return defaultStringDocumentReferenceResolver.resolve(userOrGroup, USER_SPACE_REFERENCE);
    }

    /**
     * @return the mapping between XWiki groups and user Roles.
     */
    private Map<DocumentReference, Collection<String>> getGroupMapping()
    {
        if (this.groupMappings == null) {
            Map<String, Collection<String>> mappings = configuration.getGroupMappings();
            this.groupMappings = new HashMap<DocumentReference, Collection<String>>();
            for (Map.Entry<String, Collection<String>> mapping : mappings.entrySet()) {
                this.groupMappings.put(resolveUserOrGroup(mapping.getKey()), mapping.getValue());
            }
        }

        return this.groupMappings;
    }

    /**
     * If the authenticator adapter provides a global logout URL, wrap the current response in order to rewrite
     * redirection URL using that external logout, which will be responsible to latter redirect back to the original
     * location requested. The redirection is not done now because we need to pass through the normal XWiki logout
     * process first.
     */
    private void wrapResponseForLogoutRediction()
    {
        XWikiContext context = contextProvider.get();
        if (authenticationAdapter.getLogoutURL(null) != null) {
            logger.debug("Wrapping the response for external logout redirection.");
            context.setResponse(
                new RedirectionRewritingResponseWrapper(new RedirectionRewritingResponseWrapper.URLRewriter()
                {
                    @Override
                    public String rewrite(String location)
                    {
                        return authenticationAdapter.getLogoutURL(location);
                    }
                }, context));
        }
    }

    /**
     * @return true if the current request match the configured logout page pattern.
     */
    private boolean isLogoutRequest()
    {
        if (logoutMatcher == null) {
            logoutMatcher = new RequestMatcher(configuration.getLogoutPagePattern());
        }

        return logoutMatcher.match(contextProvider.get().getRequest());
    }
}
