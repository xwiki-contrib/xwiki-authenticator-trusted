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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.authentication.UserManager;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReferenceSerializer;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.objects.classes.BaseClass;

/**
 * Default implementation of {@link UserManager}.
 *
 * @version $Id$
 */
@Component
@Singleton
public class DefaultUserManager implements UserManager
{
    private static final String USER_PROPERTY_ACTIVE = "active";

    private static final String GROUP_PROPERTY_MEMBER = "member";

    @Inject
    private Logger logger;
    
    @Inject
    private Provider<XWikiContext> contextProvider;

    @Inject
    @Named("compactwiki")
    private EntityReferenceSerializer<String> compactWikiEntityReferenceSerializer;

    @Override
    public String getValidUserName(String userName)
    {
        return userName.replace('.', '=').replace('@', '_').toLowerCase();
    }

    @Override
    public boolean createUser(DocumentReference user, Map<String, String> extInfo)
    {
        logger.debug("Creating new XWiki user [{}]", user);
        XWikiContext context = contextProvider.get();

        Map<String, String> extended = new HashMap<String, String>();
        if (extInfo != null) {
            extended.putAll(extInfo);
        }

        if (extended.get(USER_PROPERTY_ACTIVE) == null) {
            extended.put(USER_PROPERTY_ACTIVE, "1");
        }

        try {
            int result = context.getWiki().createUser(user.getName(), extended, context);
            if (result != 1) {
                throw new XWikiException(XWikiException.MODULE_XWIKI_USER, XWikiException.ERROR_XWIKI_USER_CREATE,
                    String.format("Error [%s] while creating user [%s]", result, user));
            }
        } catch (XWikiException e) {
            logger.error("Failed to create user [{}]", user, e);
            return false;
        }
        return true;
    }

    @Override
    public boolean synchronizeUserProperties(DocumentReference user, Map<String, String> extInfos, String comment)
    {
        XWikiContext context = contextProvider.get();

        try {
            BaseClass userClass = context.getWiki().getUserClass(context);
            XWikiDocument userDoc = context.getWiki().getDocument(user, context);

            if (userDoc.isNew()) {
                logger.error("User [{}] does not exist and will not be synchronized", user);
                return false;
            }

            BaseObject userObj = userDoc.getXObject(userClass.getReference());
            Map<String, String> extended = new HashMap<String, String>();

            for (Map.Entry<String, String> entry : extInfos.entrySet()) {
                String key = entry.getKey();
                if (userClass.get(key) == null) {
                    logger.warn("User property [{}] does not exist in user profile and will not be synchronized", key);
                    continue;
                }
                String value = entry.getValue();

                String objValue = userObj.getStringValue(key);
                if ((value != null && objValue == null) || (objValue != null && !objValue.equals(value))) {
                    extended.put(key, value);
                }
            }

            if (!extended.isEmpty()) {
                userClass.fromMap(extended, userObj);
                context.getWiki().saveDocument(userDoc, comment, context);
            }
        } catch (Exception e) {
            logger.error("Failed to synchronize profile properties for user [{}]", user, e);
            return false;
        }
        return true;
    }

    @Override
    public boolean synchronizeGroupsMembership(DocumentReference user, Collection<DocumentReference> groupInRefs,
        Collection<DocumentReference> groupOutRefs, String comment)
    {
        XWikiContext context = contextProvider.get();

        logger.debug("XWiki groups the user should be a member: {}", groupInRefs);
        logger.debug("XWiki groups the user should not be a member: {}", groupOutRefs);

        Collection<DocumentReference> xwikiUserGroupList;
        try {
            xwikiUserGroupList =
                context.getWiki().getGroupService(context).getAllGroupsReferencesForMember(user, 0, 0, context);
        } catch (XWikiException e) {
            logger.error("Failed to synchronize groups for user [{}]", user, e);
            return false;
        }

        logger.debug("XWiki groups the user is currently a member: {}", xwikiUserGroupList);

        boolean success = true;

        for (DocumentReference groupRef : groupInRefs) {
            if (!xwikiUserGroupList.contains(groupRef)) {
                success &= addToGroup(user, groupRef, comment, false);
            }
        }

        for (DocumentReference groupRef : groupOutRefs) {
            if (xwikiUserGroupList.contains(groupRef)) {
                success &= removeFromGroup(user, groupRef, comment);
            }
        }

        return success;
    }

    @Override
    public boolean removeFromGroup(DocumentReference user, DocumentReference group, String comment)
    {
        XWikiContext context = contextProvider.get();

        try {
            BaseClass groupClass = context.getWiki().getGroupClass(context);
            String userName = compactWikiEntityReferenceSerializer.serialize(user);

            // Get the XWiki document holding the objects comprising the group membership list
            XWikiDocument groupDoc = context.getWiki().getDocument(group, context);

            if (groupDoc.isNew()) {
                logger.warn("User [{}] cannot be removed from unknown group [{}]", user, group);
                return false;
            }

            groupDoc = groupDoc.clone();
            // Get and remove the specific group membership object for the user
            BaseObject groupObj = groupDoc.getXObject(groupClass.getReference(), GROUP_PROPERTY_MEMBER, userName);

            if (groupObj != null) {
                groupDoc.removeXObject(groupObj);

                // Save modifications
                context.getWiki().saveDocument(groupDoc, comment, context);
                logger.debug("User [{}] removed from xwiki group [{}]", user, group);
            }
        } catch (Exception e) {
            logger.error("Failed to remove a user [{}] from a group [{}]", user, group, e);
            return false;
        }
        return true;
    }

    @Override
    public boolean addToGroup(DocumentReference user, DocumentReference group, String comment, boolean create)
    {
        XWikiContext context = contextProvider.get();

        try {
            BaseClass groupClass = context.getWiki().getGroupClass(context);
            String userName = compactWikiEntityReferenceSerializer.serialize(user);

            // Get document representing group
            XWikiDocument groupDoc = context.getWiki().getDocument(group, context);

            if (groupDoc.isNew()) {
                if (!create) {
                    logger.error("User [{}] cannot be added to unknown group [{}]", user, group);
                    return false;
                } else {
                    logger.debug("Group [{}] created to be able to add user [{}]", group, user);
                }
            } else {
                groupDoc = groupDoc.clone();
            }

            // Get and remove the specific group membership object for the user
            BaseObject groupObj = groupDoc.getXObject(groupClass.getReference(), GROUP_PROPERTY_MEMBER, userName);

            if (groupObj == null) {
                // Add a member object to document
                BaseObject memberObj = groupDoc.newXObject(groupClass.getReference(), context);
                memberObj.setStringValue(GROUP_PROPERTY_MEMBER, userName);

                // Save modifications
                context.getWiki().saveDocument(groupDoc, comment, context);
                logger.debug("User [{}] added to xwiki group [{}]", user, group);
            }
        } catch (Exception e) {
            logger.error("Failed to add a user [{}] to a group [{}]", user, group, e);
            return false;
        }
        return true;
    }
}
