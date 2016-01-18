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

package org.xwiki.contrib.authentication;

import org.xwiki.component.annotation.Role;

/**
 * Adapter role for providing the trusted authentication information to this generic trusted authenticator.
 *
 * @version $Id$
 */
@Role
public interface TrustedAuthenticationAdapter
{
    /**
     * Called first to identify or confirm identification of the user, this method should return fast and provide
     * an unique identifier for the currently logged user. If no user is authenticated, this method should return null.
     *
     * @return a unique identifier for the authenticated user. Null means no user is authenticated.
     */
    String getUserUid();

    /**
     * The value returned by this method is used to compute the name of the user profile page.
     * The returned value will be normalized to support common xwiki restrictions, it shoud be as unique as
     * possible, but duplicates will be managed by suffixing a sequential number when needed.
     *
     * @return a friendly username for the authenticated user.
     */
    String getUserName();

    /**
     * Retrieve the value of user property.
     * Name given in parameter will be those from the user property mapping configuration.
     *
     * @param name name of the user property
     * @return value of the user property or null if this property has no value or does not exists.
     */
    String getUserProperty(String name);

    /**
     * Check if user is part of a given role.
     * Role requested in parameter will be those from the group mapping configuration.
     *
     * @param role the role to be checked
     * @return true if the user has been assign that role.
     */
    boolean isUserInRole(String role);
}
