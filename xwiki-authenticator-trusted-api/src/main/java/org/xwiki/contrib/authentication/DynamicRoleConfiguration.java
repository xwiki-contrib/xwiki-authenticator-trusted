package org.xwiki.contrib.authentication;

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

/**
 * Dynamic role configuration.
 *
 * @version $Id$
 * @since 1.5.2
 */
public interface DynamicRoleConfiguration
{
    /**
     * @return whether the given role matche this configuration.
     * @param role the role to match
     */
    boolean matchesRole(String role);

    /**
     * @return the role prefix.
     */
    String getRolePrefix();

    /**
     * @return the role suffix.
     */
    String getRoleSuffix();

    /**
     * @return the role regex.
     */
    String getRoleRegex();

    /**
     * @return the replacement to use with the role regex to get the group name.
     */
    String getReplacement();

    /**
     * @return the group prefix.
     */
    String getGroupPrefix();

    /**
     * @return the group suffix.
     */
    String getGroupSuffix();

    /**
     * @return whether the involved groups should be auto created as needed.
     */
    boolean getAutoCreate();
}
