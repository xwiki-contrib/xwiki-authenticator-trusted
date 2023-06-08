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

import org.xwiki.contrib.authentication.DynamicRoleConfiguration;
import org.xwiki.contrib.authentication.TrustedAuthenticationConfiguration;

/**
 * Default implementation of {@link DynamicRoleConfiguration}.
 *
 * @version $Id$
 * @since 1.6.0
 */
public class DefaultDynamicRoleConfiguration implements DynamicRoleConfiguration
{
    private final String configurationName;
    private final String rolePrefix;
    private final String roleSuffix;
    private final String roleRegex;
    private final String replacement;
    private final String groupPrefix;
    private final String groupSuffix;
    private final boolean autoCreate;

    protected DefaultDynamicRoleConfiguration(TrustedAuthenticationConfiguration authConf,
            String confPrefix, String configurationName)
    {
        this.configurationName = configurationName;
        rolePrefix = authConf.getCustomProperty(confPrefix + "rolePrefix", "");
        roleSuffix = authConf.getCustomProperty(confPrefix + "roleSuffix", "");
        roleRegex = authConf.getCustomProperty(confPrefix + "roleRegex", "");
        groupPrefix = authConf.getCustomProperty(confPrefix + "groupPrefix", "");
        groupSuffix = authConf.getCustomProperty(confPrefix + "groupSuffix", "");
        replacement = authConf.getCustomProperty(confPrefix + "replacement", "");
        autoCreate = authConf.getCustomPropertyAsBoolean(confPrefix + "autocreate", true);
    }

    @Override
    public boolean matchesRole(String role)
    {
        if (role == null || (rolePrefix.isEmpty() && roleSuffix.isEmpty() && roleRegex.isEmpty())) {
            return false;
        }

        if (!role.startsWith(rolePrefix) || !role.endsWith(roleSuffix)) {
            return false;
        }

        if (!roleRegex.isEmpty() && !role.matches(roleRegex)) {
            return false;
        }

        return true;
    }

    @Override
    public String getRolePrefix()
    {
        return rolePrefix;
    }

    @Override
    public String getRoleSuffix()
    {
        return roleSuffix;
    }

    @Override
    public String getRoleRegex()
    {
        return roleRegex;
    }

    @Override
    public String getReplacement()
    {
        return replacement;
    }

    @Override
    public String getGroupPrefix()
    {
        return groupPrefix;
    }

    @Override
    public String getGroupSuffix()
    {
        return groupSuffix;
    }

    @Override
    public boolean isAutoCreate()
    {
        return autoCreate;
    }

    @Override
    public String toString()
    {
        return "name: " + configurationName
            + ", role prefix: " + rolePrefix
            + ", role suffix: " + roleSuffix
            + ", role regex: "  + roleRegex
            + ", replacement: " + replacement
            + ", group prefix: " + groupPrefix
            + ", group suffix: " + groupSuffix
            + ", auto create groups: " + autoCreate;
    }
}
