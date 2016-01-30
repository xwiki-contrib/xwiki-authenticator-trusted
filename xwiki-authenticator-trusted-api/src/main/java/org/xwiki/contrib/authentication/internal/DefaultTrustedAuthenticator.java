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
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.phase.Initializable;
import org.xwiki.component.phase.InitializationException;
import org.xwiki.contrib.authentication.AuthenticationPersistenceStore;
import org.xwiki.contrib.authentication.TrustedAuthenticationAdapter;
import org.xwiki.contrib.authentication.TrustedAuthenticationConfiguration;
import org.xwiki.contrib.authentication.TrustedAuthenticator;
import org.xwiki.contrib.authentication.UserManager;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceSerializer;

import com.xpn.xwiki.XWikiContext;

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
    private EntityReferenceSerializer<String> entityReferenceSerializer;

    private TrustedAuthenticationAdapter authenticationAdapter;

    private AuthenticationPersistenceStore persistenceStore;

    /**
     * Cache of the group mapping.
     */
    private Map<DocumentReference, Collection<String>> groupMappings;

    @Override
    public void initialize() throws InitializationException
    {
        authenticationAdapter = configuration.getAuthenticationAdapter();
        persistenceStore = configuration.getPersistenceStore();
    }

    @Override
    public String authenticate()
    {
        logger.debug("Starting trusted authentication...");
        String previouslyAuthenticatedUser = persistenceStore.retrieve();

        if (configuration.isPersistenceStoreTrusted()) {
            if (previouslyAuthenticatedUser != null) {
                logger.debug("User [{}] authenticated from trusted persistence store.", previouslyAuthenticatedUser);
                return previouslyAuthenticatedUser;
            }
        }

        String userUid = authenticationAdapter.getUserUid();
        if (userUid == null) {
            logger.debug("No user available from trusted authenticator.");
            if (previouslyAuthenticatedUser != null) {
                logger.debug("Clearing persistenceStore, removing [{}].", previouslyAuthenticatedUser);
                persistenceStore.clear();
            }
            logger.debug("Trusted authentication ended with public access.");
            return null;
        }

        return authenticate(previouslyAuthenticatedUser, userUid);
    }

    /**
     * Proceed to the authentication, creating and synchronizing user if needed based on the previous authentication.
     *
     * @param previouslyAuthenticatedUser the previously authenticated user retrieved from the store.
     * @param currentUserUid the user Uid to be authenticated now.
     * @return the finally authenticated user.
     */
    private String authenticate(String previouslyAuthenticatedUser, String currentUserUid)
    {
        logger.debug("User [{}] retrieved from the authentication adapter.", currentUserUid);

        DocumentReference userProfile = getUserProfileReference(currentUserUid);
        String authenticatedUser = entityReferenceSerializer.serialize(userProfile);

        if (previouslyAuthenticatedUser != null) {
            logger.debug("User [{}] retrieved from untrusted persistence store.", previouslyAuthenticatedUser);
            if (authenticatedUser.equals(previouslyAuthenticatedUser)) {
                logger.debug("User [{}] authenticated from the authentication adapter, no synchronization.",
                    userProfile);
                return authenticatedUser;
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
        return authenticatedUser;
    }

    /**
     * Return the reference to the user profile of the user being authenticated.
     *
     * @param userUid the userUid retrieved from the authentication adapter (could not be any other userUid).
     * @return the reference to the user profile (existing or to be created).
     */
    private DocumentReference getUserProfileReference(String userUid)
    {
        String userName = authenticationAdapter.getUserName();
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
        String database = context.getDatabase();
        try {
            // Switch to main wiki to force users to be global users
            context.setDatabase(user.getWikiReference().getName());

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

            synchronizeGroups(user);
        } finally {
            context.setDatabase(database);
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

                if (!StringUtils.isBlank(value)) {
                    extInfos.put(entry.getKey(), value.trim());
                }
            }
        }

        return extInfos;
    }

    /**
     * Synchronize the user in mapped groups.
     *
     * @param user the reference of the user document.
     */
    protected void synchronizeGroups(DocumentReference user)
    {
        Map<DocumentReference, Collection<String>> mappings = getGroupMapping();

        // Only synchronize groups if a group mapping configuration exists
        if (mappings.size() > 0) {
            logger.debug("Synchronizing groups for user [{}]...", user);

            Collection<DocumentReference> groupInRefs = new ArrayList<DocumentReference>();
            Collection<DocumentReference> groupOutRefs = new ArrayList<DocumentReference>();

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

            // apply synchronization
            userManager.synchronizeGroupsMembership(user, groupInRefs, groupOutRefs,
                "Trusted authentication group synchronization");
        }
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
                this.groupMappings.put(
                    defaultStringDocumentReferenceResolver.resolve(mapping.getKey(), USER_SPACE_REFERENCE),
                    mapping.getValue());
            }
        }

        return this.groupMappings;
    }
}
