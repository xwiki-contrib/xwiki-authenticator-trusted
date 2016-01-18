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
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.xwiki.component.annotation.Role;

import com.xpn.xwiki.user.api.XWikiAuthService;

/**
 * Configuration of the generic trusted authentication.
 *
 * @version $Id$
 */
@Role
public interface TrustedAuthenticationConfiguration
{
    /**
     * Return the authentication adapter that will bridge the trusted authentication with this generic
     * trusted authenticator.
     * @return the authentication adapter used for retrieving user information.
     */
    TrustedAuthenticationAdapter getAuthenticationAdapter();

    /**
     * Mainly for performance reason, the currently logged user UID should be persisted across request, this
     * method return the persistence store according to the configuration. Most common implementation is provided
     * for storing userUID in session or encrypted cookie, with respective hint <code>session</code> and
     * <code>cookie</code>, default is to use <code>session</code>.
     * @return the persistence store to be used.
     */
    AuthenticationPersistenceStore getPersistenceStore();

    /**
     * @return true if the above persistence store should be trusted for authentication, or false if it should
     * only be used for optimization of the user and group synchronization.
     */
    boolean isPersistenceStoreTrusted();

    /**
     * TTL of the persistence storage, currently only applicable to COOKIE* modes.
     * @return how long in seconds persistence should be considered valid before being drop since initial setup.
     */
    int getPersistenceTTL();


    /**
     * Retrieve the mapping between XWiki user properties and user property provided by the AuthenticationAdapter.
     * @return a map between XWikiUsers property names and names of properties returned by the AuthenticationAdapter
     */
    Map<String, String> getUserPropertyMappings();

    /**
     * Retrieve the mapping between XWiki groups and user roles provided by the AuthenticationAdapter.
     * @return a map between XWiki groups and a list of roles
     */
    Map<String, Collection<String>> getGroupMappings();

    /**
     * @return true if this authenticator should be authoritative and not fallback to any other one including the
     *         standard XWiki authentication service.
     */
    boolean isAuthoritative();

    /**
     * @return another authenticator service to be used as fallback, or null if no fallback should be applied
     */
    XWikiAuthService getFallbackAuthenticator();

    /**
     * Retrieve value of a simple property.
     * Example: <code>name = value</code>
     *
     * @param name the property name
     * @param def default value to be used when no value for the property has been found
     * @return the value of the property or the default value
     */
    String getCustomProperty(String name, String def);

    /**
     * Retrieve value of a property as a boolean.
     * Example: <code>name = yes</code>
     *
     * @param name the property name
     * @param def default value to be used when no value for the property has been found
     * @return the boolean equivalent of the property, 'true', 'on' or 'yes' (case insensitive) will return true.
     */
    boolean getCustomPropertyAsBoolean(String name, boolean def);

    /**
     * Retrieve list of values assigned to a property.
     * Example: <code>name = value1, value2, value1, value3</code>
     * With coma as separator, it return a list of 4 values
     *
     * @param name the property name
     * @param separator the value separator
     * @param def the default list of value to be used when no value for the property has been found
     * @return the list of values assigned to a property or the default list of value
     */
    List<String> getCustomPropertyAsList(String name, char separator, List<String> def);

    /**
     * Retrieve set of values assigned to a property.
     * Example: <code>name = value1, value2, value1, value3</code>
     * With coma as separator, it return a set of 3 values
     *
     * @param name the property name
     * @param separator the value separator
     * @param def the default set of value to be used when no value for the property has been found
     * @return the set of values assigned to a property or the default set of value
     */
    Set<String> getCustomPropertyAsSet(String name, char separator, Set<String> def);

    /**
     * Retrieve map of key/value pairs assigned to a property.
     * Example: <code>name = key1=value1, key2=value2, key1=value1b, key3=value3</code>
     * With coma as separator, it return a map with 3 entries, key1 being associated to value1b.
     *
     * @param name the property name
     * @param separator the value separator
     * @param def the default set of value to be used when no value for the property has been found
     * @return the set of values assigned to a property or the default set of value
     */
    Map<String, String> getCustomPropertyAsMap(String name, char separator, Map<String, String> def,
        boolean forceLowerCaseKey);

    /**
     * Retrieve map of key/listOfValues pairs assigned to a property.
     * Example: <code>name = key1=value1, key2=value2, key1=value1b, key3=value3</code>
     * With coma as separator, it return a map with 3 entries, <code>key1</code> being associated to
     * a list of 2 values.
     *
     * @param name the property name
     * @param separator the value separator
     * @param def the default set of value to be used when no value for the property has been found
     * @return the set of values assigned to a property or the default set of value
     */
    Map<String, Collection<String>> getCustomPropertyAsMapOfSet(String name, char separator,
        Map<String, Collection<String>> def, boolean left);
}
