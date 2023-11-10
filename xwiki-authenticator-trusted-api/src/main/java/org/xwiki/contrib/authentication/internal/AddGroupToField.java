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

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xwiki.contrib.authentication.AddGroupToFieldConfiguration;
import org.xwiki.contrib.authentication.DynamicRoleConfiguration;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReferenceSerializer;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.web.Utils;

/**
 * Add group to field utility class.
 *
 * @version $Id$
 */
class AddGroupToField
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultAddGroupToFieldConfiguration.class);

    private static final String DEF = "default";

    private final Map<String, XWikiDocument> documentsToSave;

    protected AddGroupToField()
    {
        documentsToSave = new HashMap<String, XWikiDocument>();
    }

    private static BaseObject getObject(XWikiDocument doc, XWikiContext context, String className, String number,
        String property)
    {
        if (className.isEmpty()) {
            return doc.getFirstObject(property);
        }

        if (number.isEmpty()) {
            return doc.getXObject(resolve(className));
        }

        return doc.getXObject(resolve(className), Integer.parseInt(number));
    }

    private static String serialize(DocumentReference docRef)
    {
        EntityReferenceSerializer<String> serializer = Utils.getComponent(EntityReferenceSerializer.TYPE_STRING, DEF);
        return serializer.serialize(docRef);
    }

    private static DocumentReference resolve(String page)
    {
        DocumentReferenceResolver<String> resolver = Utils.getComponent(DocumentReferenceResolver.TYPE_STRING, DEF);
        return resolver.resolve(page);
    }

    private static boolean isGroupRolePresent(DocumentReference group, String role, String values,
        AddGroupToFieldConfiguration c)
    {
        Pattern valueRegex = Pattern.compile(c.getValueRegex());
        String sGroup = serialize(group);
        for (String keyVal : values.split(Pattern.quote(c.getSeparator()))) {
            Matcher m = valueRegex.matcher(keyVal);
            if (m.matches()) {
                LOGGER.debug("Regex [{}] matches value [{}]", c.getValueRegex(), keyVal);
                try {
                    String mGroup = m.group("group");
                    if (mGroup != null && !group.getName().equals(mGroup) && !sGroup.endsWith(mGroup)) {
                        LOGGER.debug("Groups don't match: [{}] vs [{}]", sGroup, mGroup);
                        continue;
                    }
                    LOGGER.debug("Groups match");
                } catch (IllegalArgumentException e) {
                    // no named-capturing group "group".
                    // do nothing and keep going, it's a match by absence of constraint.
                    LOGGER.debug("No capturing group named 'group' the regex. Not checking groups match.");
                }

                try {
                    String mRole = m.group("role");
                    if (mRole != null && !role.equals(mRole)) {
                        LOGGER.debug("Rols don't match: [{}] vs [{}]", role, mRole);
                        continue;
                    }
                    LOGGER.debug("Roles match");
                } catch (IllegalArgumentException e) {
                    // no named-capturing group "group".
                    // do nothing and keep going, it's a match by absence of constraint.
                    LOGGER.debug("No capturing group named 'role' in the regex. Not checking roles match.");
                }

                LOGGER.debug("Group [{}] / role [{}] is already in property [{}] of [{}]. Matching value: [{}]", group,
                    role, c.getPropertyName(), c.getPage(), keyVal);

                return true;
            } else {
                LOGGER.debug("Regex [{}] does not match value [{}]", c.getValueRegex(), keyVal);
            }
        }

        LOGGER.debug("Group [{}] / role [{}] was not found in property [{}] of [{}].", group, role, c.getPropertyName(),
            c.getPage());

        return false;
    }

    private XWikiDocument getXWikiDocument(String page, XWikiContext context, boolean saving) throws XWikiException
    {

        XWikiDocument xdoc = documentsToSave.get(page);
        if (xdoc == null) {
            xdoc = context.getWiki().getDocument(resolve(page), context);
            if (saving) {
                documentsToSave.put(page, xdoc);
            }
        }

        return xdoc;
    }

    protected void add(XWikiContext context, DocumentReference group, String role, DynamicRoleConfiguration conf)
    {
        AddGroupToFieldConfiguration c = conf.getAddGroupToFieldConfiguration();
        if (c == null) {
            LOGGER.debug("Group [{}] / role [{}] does not have any add group to field conf.", group, role);
            return;
        }

        LOGGER.debug("Adding group [{}] / role [{}] to a field using the following conf: [{}].", group, role, c);

        String page = c.getPage();
        if (page == null) {
            return;
        }

        if (page.indexOf(':') == -1) {
            page = context.getMainXWiki() + ":" + page;
        }

        XWikiDocument doc;
        try {
            doc = getXWikiDocument(page, context, false);
        } catch (XWikiException e) {
            LOGGER.error("Failed to get document matching configuration [{}]. The field won't be updated.",
                c.toString(), e);
            return;
        }

        BaseObject obj = getObject(doc, context, c.getClassName(), c.getObjectNumber(), c.getPropertyName());

        if (obj == null) {
            LOGGER.error("Could not find any object matching configuration [{}]. The field won't be updated.",
                c.toString());
            return;
        }

        String values;
        try {
            values = obj.getLargeStringValue(c.getPropertyName());
        } catch (ClassCastException e) {
            LOGGER.error("Could not convert the field to a string for configuration [{}]. The field won't be updated",
                c.toString(), e);
            return;
        }

        if (!isGroupRolePresent(group, role, values, c)) {
            String newValue = c.getValue(group, role);
            LOGGER.debug("Group [{}] / role [{}] was not found in property [{}] of [{}]. Adding it: [{}]", group, role,
                c.getPropertyName(), page, newValue);

            obj.setLargeStringValue(c.getPropertyName(), values + c.getSeparator() + newValue);
            documentsToSave.put(page, doc);
        }
    }

    protected void save(XWikiContext context)
    {
        for (XWikiDocument doc : documentsToSave.values()) {
            try {
                context.getWiki().saveDocument(doc, "Add a new role/group from the trusted authenticator", context);
            } catch (XWikiException e) {
                LOGGER.error("Could not update the group/role field of page [{}].", doc, e);
            }
            LOGGER.debug("Updated [{}] with new group(s)/role(s).", doc);
        }
    }
}
