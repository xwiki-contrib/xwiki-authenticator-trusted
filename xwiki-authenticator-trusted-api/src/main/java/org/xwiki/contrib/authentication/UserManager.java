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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import org.xwiki.component.annotation.Role;
import org.xwiki.model.reference.DocumentReference;

/**
 * Helper for managing user creation, synchronization and group management.
 *
 * @version $Id$
 */
@Role
public interface UserManager
{
    /**
     * Create the user with the given user attributes.
     *
     * @param user the reference of the user document.
     * @param extended the list of extended properties to be assigned in the user profile.
     * @return true when the user has been successfully created
     */
    boolean createUser(DocumentReference user, Map<String, String> extended);

    /**
     * Synchronize properties in the user profile.
     *
     * @param user the reference of the user.
     * @param extended map of user properties to synchronize.
     * @param comment the comment used for saving modification to groups.
     * @return true when the user properties has been successfully synchronized
     */
    boolean synchronizeUserProperties(DocumentReference user, Map<String, String> extended, String comment);

    /**
     * Synchronize user membership to groups.
     *
     * @param user the reference of the user.
     * @param groupInRefs the reference of the groups the user should be in.
     * @param groupOutRefs the reference of the groups the user should not be in.
     * @param comment the comment used for saving modification to groups.
     * @return true when the user has been successfully synchronized.
     */
    boolean synchronizeGroupsMembership(DocumentReference user, Collection<DocumentReference> groupInRefs,
        Collection<DocumentReference> groupOutRefs, String comment);

    /**
     * Synchronize user membership to groups.
     *
     * @param user the reference of the user.
     * @param groupInRefs the reference of the groups the user should be in.
     * @param groupWithAutoCreateInRefs the reference of the groups the user should be in and that can
     *                                  be created if missing.
     * @param groupOutRefs the reference of the groups the user should not be in.
     * @param comment the comment used for saving modification to groups.
     * @return true when the user has been successfully synchronized.
     * @since 1.6.0
     */
    default boolean synchronizeGroupsMembership(DocumentReference user, Collection<DocumentReference> groupInRefs,
        Collection<DocumentReference> groupWithAutoCreateInRefs, Collection<DocumentReference> groupOutRefs,
        String comment)
    {
        Collection<DocumentReference> groupsIn = new ArrayList<DocumentReference>();
        groupsIn.addAll(groupInRefs);
        groupsIn.addAll(groupWithAutoCreateInRefs);
        return synchronizeGroupsMembership(user, groupsIn, groupOutRefs, comment);
    }

    /**
     * Remove user from group.
     *
     * @param user the reference of the user.
     * @param group the reference of the group.
     * @param comment the comment used for saving modification to the group.
     * @return true when the user has been successfully removed
     */
    boolean removeFromGroup(DocumentReference user, DocumentReference group, String comment);

    /**
     * Add user to group.
     *
     * @param user the reference of the user.
     * @param group the reference of the group.
     * @param comment the comment used for saving modification to the group.
     * @param create when true, a new group will be created if the group does not exist yet.
     * @return true when the user has been successfully added
     */
    boolean addToGroup(DocumentReference user, DocumentReference group, String comment, boolean create);
}
