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
 * Common interface for persistence stores.
 *
 * @version $Id$
 */
@Role
public interface AuthenticationPersistenceStore
{
    /**
     * Store the given user UID into the persistence store.
     * @param userUid user unique identifier to be persisted.
     */
    void store(String userUid);

    /**
     * @return the current user UID persisted in the store, or null if none is found.
     */
    String retrieve();

    /**
     * Clear the current authentication persisted in the store if any.
     */
    void clear();
}
