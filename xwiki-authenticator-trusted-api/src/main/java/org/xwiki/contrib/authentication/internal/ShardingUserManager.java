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

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.authentication.UserManager;
import org.xwiki.model.reference.DocumentReference;

/**
 * User manager that implements group sharding.
 *
 * @version $Id$
 * @since 1.9.4
 */
@Component
@Singleton
@Named("sharding")
public class ShardingUserManager implements UserManager
{
    @Inject
    private UserManager defaultUserManager;

    @Inject
    private GroupShardingManager groupShardingManager;

    @Override
    public boolean createUser(DocumentReference user, Map<String, String> extended)
    {
        return defaultUserManager.createUser(user, extended);
    }

    @Override
    public boolean synchronizeUserProperties(DocumentReference user, Map<String, String> extended, String comment)
    {
        return defaultUserManager.createUser(user, extended);
    }

    @Override
    public boolean synchronizeGroupsMembership(DocumentReference user, Collection<DocumentReference> groupInRefs,
        Collection<DocumentReference> groupOutRefs, String comment)
    {
        return synchronizeGroupsMembership(user, groupInRefs, Collections.emptyList(), groupOutRefs, comment);
    }

    @Override
    public boolean synchronizeGroupsMembership(DocumentReference user, Collection<DocumentReference> groupInRefs,
        Collection<DocumentReference> groupWithAutoCreateInRefs, Collection<DocumentReference> groupOutRefs,
        String comment)
    {
        Collection<DocumentReference> shardedGroupInRefs = new HashSet<>();
        for (DocumentReference groupInRef : groupInRefs) {
            DocumentReference shardedGroupReference = groupShardingManager.getShardedGroupReference(groupInRef, user);
            shardedGroupInRefs.add(shardedGroupReference);
        }

        Collection<DocumentReference> shardedGroupWithAutoCreateInRefs = new HashSet<>();
        for (DocumentReference groupWithAutoCreateInRef : groupWithAutoCreateInRefs) {
            DocumentReference shardedGroupReference =
                groupShardingManager.getShardedGroupReference(groupWithAutoCreateInRef, user);
            if (!shardedGroupReference.equals(groupWithAutoCreateInRef)) {
                groupShardingManager.setupGroupShard(groupWithAutoCreateInRef, shardedGroupReference);
            }
            shardedGroupWithAutoCreateInRefs.add(shardedGroupReference);
        }

        // Make sure that the user is also removed from the initial, non-sharded group, if the user remains.
        Collection<DocumentReference> shardedGroupOutRefs = new HashSet<>();
        for (DocumentReference groupOutRef : groupOutRefs) {
            DocumentReference shardedGroupReference =
                groupShardingManager.getShardedGroupReference(groupOutRef, user);
            shardedGroupOutRefs.add(shardedGroupReference);
            shardedGroupOutRefs.add(groupOutRef);
        }

        return defaultUserManager.synchronizeGroupsMembership(user, shardedGroupInRefs,
            shardedGroupWithAutoCreateInRefs, shardedGroupOutRefs, comment);
    }

    @Override
    public boolean removeFromGroup(DocumentReference user, DocumentReference group, String comment)
    {
        return defaultUserManager.removeFromGroup(user, group, comment);
    }

    @Override
    public boolean addToGroup(DocumentReference user, DocumentReference group, String comment, boolean create)
    {
        return defaultUserManager.addToGroup(user, group, comment, create);
    }
}
