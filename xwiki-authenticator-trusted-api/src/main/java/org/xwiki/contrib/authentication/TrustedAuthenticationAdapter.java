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

import java.util.Collection;

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


    /**
     * @return the roles the user is part of.
     * @since 1.6.0
     */
    default Collection<String> getUserRoles()
    {
        return null;
    }

    /**
     * The location returned will be use to redirect the user during a logout action. It is expected that the
     * user will redirected back to the provided location after having been logout from the external authentication
     * system. If you do not want external logout, simply return null.
     *
     * @param location the location to redirect back after the external logout being done. This could be null and
     *                 and should be handle as a test to know if a redirection will be available, so it should not
     *                 throw, but return a non-null value to confirm redirection availability. This parameter will
     *                 be like the one received by HttpServletResponse#sendRedirect().
     * @return the location to redirect the user to for logout. This location will be passed
     *         to HttpServletResponse#encodeRedirectUrl() before being given to HttpServletResponse#sendRedirect().
     */
    String getLogoutURL(String location);
}
