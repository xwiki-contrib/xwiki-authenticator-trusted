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

import java.util.regex.Pattern;
import java.util.regex.Matcher;

import org.xwiki.contrib.authentication.AddGroupToFieldConfiguration;
import org.xwiki.contrib.authentication.DynamicRoleConfiguration;

import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.model.reference.DocumentReferenceResolver;
import com.xpn.xwiki.api.Document;
import com.xpn.xwiki.api.XWiki;
import com.xpn.xwiki.web.Utils;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Add group to field utility class.
 *
 * @version $Id$
 */
final class AddGroupToField
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultAddGroupToFieldConfiguration.class);
    private static final String DEFAULT = "default";

    private AddGroupToField() { }

    private static com.xpn.xwiki.api.Object getObject(Document doc, String className, String number, String property)
    {
        if (className.isEmpty()) {
            return doc.getFirstObject(property);
        }

        if (number.isEmpty()) {
            return doc.getObject(className);
        }

        return doc.getObject(className, Integer.parseInt(number));
    }

    private static EntityReferenceSerializer<String> getDefaultSerializer() {
        return Utils.getComponent(EntityReferenceSerializer.TYPE_STRING, DEFAULT);
    }

    private static DocumentReferenceResolver<String> getDefaultResolver() {
        return Utils.getComponent(DocumentReferenceResolver.TYPE_STRING, DEFAULT);
    }

    private static boolean isGroupRolePresent(DocumentReference group, String role,
            String values, AddGroupToFieldConfiguration c)
    {
        Pattern valueRegex = Pattern.compile(c.getValueRegex());
        EntityReferenceSerializer<String> defaultSerializer = getDefaultSerializer();
        String sGroup = defaultSerializer.serialize(group);
        for (String keyVal : values.split(Pattern.quote(c.getSeparator()))) {
            Matcher m = valueRegex.matcher(keyVal);
            if (m.matches()) {
                try {
                    String mGroup = m.group("group");
                    if (mGroup != null && !group.getName().equals(mGroup) && !sGroup.endsWith(mGroup)) {
                        continue;
                    }
                } catch (IllegalArgumentException e) {
                    // no named-capturing group "group".
                    // do nothing and keep going, it's a match by absence of constraint.
                }

                try {
                    String mRole = m.group("role");
                    if (mRole != null && !role.equals(mRole)) {
                        continue;
                    }
                } catch (IllegalArgumentException e) {
                    // no named-capturing group "group".
                    // do nothing and keep going, it's a match by absence of constraint.
                }

                LOGGER.debug("Group [{}] / role [{}] is already in property [{}] of [{}]. Matching value: [{}]",
                    group, role, c.getPropertyName(), c.getPage(), keyVal);

                return true;
            }
        }

        return false;
    }

    protected static void add(XWikiContext context, DocumentReference group, String role, DynamicRoleConfiguration conf)
    {
        AddGroupToFieldConfiguration c = conf.getAddGroupToFieldConfiguration();
        if (c == null) {
            return;
        }

        DocumentReference page = getDefaultResolver().resolve(c.getPage());
        XWiki wiki = new XWiki(context.getWiki(), context);

        Document doc;
        try {
            doc = wiki.getDocument(page);
        } catch (XWikiException e) {
            LOGGER.error("Failed to get document matching configuration [{}]. The field won't be updated.",
                c.toString());
            return;
        }

        com.xpn.xwiki.api.Object obj = getObject(doc, c.getClassName(), c.getObjectNumber(), c.getPropertyName());

        if (obj == null) {
            LOGGER.error("Could not find any object matching configuration [{}]. The field won't be updated.",
                c.toString());
            return;
        }

        String values;
        try {
            values = (String) obj.getValue(c.getPropertyName());
        } catch (ClassCastException e) {
            LOGGER.error("Could not convert the field to a string for configuration [{}]. The field won't be updated",
                c.toString(), e);
            return;
        }

        if (isGroupRolePresent(group, role, values, c)) {
            return;
        }

        String newValue = c.getValue(group, role);
        LOGGER.debug("Group [{}] / role [{}] was not found in property [{}] of [{}]. Adding it: [{}]",
            group, role, c.getPropertyName(), c.getPage(), newValue);

        obj.set(c.getPropertyName(), values + c.getSeparator() + newValue);
        try {
            doc.save("Adding a new group / role from the trusted authentication module");
        } catch (XWikiException e) {
            LOGGER.error("Could not update [{}] with new group [{}] / role [{}] in [{}].",
                c.getPropertyName(), group, role, c.getPage(), e);
        }
    }
}
