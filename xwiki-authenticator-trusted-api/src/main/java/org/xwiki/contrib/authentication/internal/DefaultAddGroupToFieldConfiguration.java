package org.xwiki.contrib.authentication.internal;

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


import org.xwiki.model.reference.DocumentReference;

import com.xpn.xwiki.web.Utils;
import org.xwiki.model.reference.EntityReferenceSerializer;

import org.xwiki.contrib.authentication.AddGroupToFieldConfiguration;
import org.xwiki.contrib.authentication.TrustedAuthenticationConfiguration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Add group to field configuration.
 *
 * @version $Id$
 * @since 1.7.0
 */
class DefaultAddGroupToFieldConfiguration implements AddGroupToFieldConfiguration
{
    private static final String PAGE = "page";
    private static final String PROPERTY_NAME = "propertyName";

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultAddGroupToFieldConfiguration.class);

    private final String page;
    private final String className;
    private final String objectNumber;
    private final String propertyName;
    private final String separator;
    private final String valueRegex;
    private final String valueFormat;

    protected DefaultAddGroupToFieldConfiguration(TrustedAuthenticationConfiguration authConf, String confPrefix,
            AddGroupToFieldConfiguration parentConf)
    {
        page = authConf.getCustomProperty(confPrefix + PAGE,
            parentConf == null ? "" : parentConf.getPage());

        className = authConf.getCustomProperty(confPrefix + "className",
            parentConf == null ? "" : parentConf.getClassName());

        objectNumber = authConf.getCustomProperty(confPrefix + "objectNumber",
            parentConf == null ? "" : parentConf.getObjectNumber());

        propertyName = authConf.getCustomProperty(confPrefix + PROPERTY_NAME,
            parentConf == null ? "" : parentConf.getPropertyName());

        separator = authConf.getCustomProperty(confPrefix + "separator",
            parentConf == null ? "|" : parentConf.getSeparator());

        valueRegex = authConf.getCustomProperty(confPrefix + "valueRegex",
            parentConf == null ? "^(?<group>[^=]+)=[\\s\\S]*" : parentConf.getValueRegex());

        valueFormat = authConf.getCustomProperty(confPrefix + "valueFormat",
            parentConf == null ? "{group.fullName}={role}" : parentConf.getValueFormat());
    }


    public static DefaultAddGroupToFieldConfiguration parse(TrustedAuthenticationConfiguration authConf,
        String confPrefix, AddGroupToFieldConfiguration parentConf)
    {
        String defaultPage = parentConf == null ? "" : parentConf.getPage();
        String defaultProperty = parentConf == null ? "" : parentConf.getPropertyName();

        if (authConf.getCustomProperty(confPrefix + PAGE, defaultPage).isEmpty()
            && authConf.getCustomProperty(confPrefix + PROPERTY_NAME, defaultProperty).isEmpty()) {
            return null;
        }

        DefaultAddGroupToFieldConfiguration conf = new DefaultAddGroupToFieldConfiguration(authConf,
            confPrefix, parentConf);

        if (conf.getValue(null, "") != null) {
            // check that the value format is correctly formed. If not, errors will be printed.
            return conf;
        }

        return null;
    }

    @Override
    public String getPage()
    {
        return page;
    }

    @Override
    public String getClassName()
    {
        return className;
    }

    @Override
    public String getObjectNumber()
    {
        return objectNumber;
    }

    @Override
    public String getPropertyName()
    {
        return propertyName;
    }

    @Override
    public String getSeparator()
    {
        return separator;
    }

    @Override
    public String getValueRegex()
    {
        return valueRegex;
    }

    @Override
    public String getValueFormat()
    {
        return valueFormat;
    }

    @Override
    public String getValue(DocumentReference group, String role)
    {
        String value = "";
        int i = 0;
        int n = valueFormat.length();
        while (i < n) {
            char c = valueFormat.charAt(i);
            switch (c) {
                case '\\':
                    i++;
                    if (i < n) {
                        value += valueFormat.charAt(i);
                    }
                    break;

                case '{':
                    i++;
                    int startPlaceholderName = i;
                    int endBrace = valueFormat.indexOf('}', i);
                    if (endBrace == -1) {
                        LOGGER.error("Value format [{}] has an unmatched '{'. Escape it or add the missing brace.",
                            valueFormat);
                        return null;
                    }

                    String placeholderName = valueFormat.substring(startPlaceholderName, endBrace);

                    switch (placeholderName) {
                        case "group.name":
                            value += group.getName();
                            break;

                        case "group.fullName":
                            EntityReferenceSerializer<String> localSerializer =
                                Utils.getComponent(EntityReferenceSerializer.TYPE_STRING, "local");
                            value += localSerializer.serialize(group);
                            break;

                        case "group":
                            EntityReferenceSerializer<String> defaultSerializer =
                                Utils.getComponent(EntityReferenceSerializer.TYPE_STRING, "default");
                            value += defaultSerializer.serialize(group);
                            break;

                        case "role":
                            value += role;
                            break;

                        default:
                            LOGGER.error(
                                "Value format [{}] has an unknown placeholder [{}]. "
                                    + "Fix it or escape the opening brace '{' right before.",
                                valueFormat,
                                placeholderName);
                            return null;
                    }
                    i = endBrace;
                    break;

                default:
                    value += c;
            }
            i++;
        }
        return value;
    }

    @Override
    public String toString()
    {
        return "page: " + page
            + ", class name: " + className
            + ", object number: " + objectNumber
            + ", property name: "  + propertyName
            + ", separator: " + separator
            + ", value regex: " + valueRegex
            + ", value format: " + valueFormat;
    }
}
