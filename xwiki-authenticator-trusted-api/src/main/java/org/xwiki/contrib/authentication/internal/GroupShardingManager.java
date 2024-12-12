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

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.authentication.GroupShardingConfiguration;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.user.group.GroupException;
import org.xwiki.user.group.GroupManager;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

/**
 * Manager providing information about group sharding.
 *
 * @version $Id$
 * @since 1.9.4
 */
@Component(roles = { GroupShardingManager.class })
@Singleton
public class GroupShardingManager
{
    @Inject
    private GroupShardingConfiguration groupShardingConfiguration;

    @Inject
    @Named("compactwiki")
    private EntityReferenceSerializer<String> entityReferenceSerializer;

    @Inject
    private Provider<XWikiContext> contextProvider;

    @Inject
    private GroupManager groupManager;

    @Inject
    private Logger logger;

    /**
     * Calculate the reference of the user shard for the given group.
     *
     * @param group the user group
     * @param user the user
     * @return the reference to the sharded group
     */
    public DocumentReference getShardedGroupReference(DocumentReference group, DocumentReference user)
    {
        if (groupShardingConfiguration.getShardedGroups().contains(group.getName())) {
            String serializedUserReference = entityReferenceSerializer.serialize(user);
            String digest = DigestUtils.sha256Hex(serializedUserReference);
            String shardName = String.format("%s-Shard%s", group.getName(), digest.toUpperCase().charAt(0));
            return new DocumentReference(shardName, group.getLastSpaceReference());
        } else {
            return group;
        }
    }

    /**
     * Make sure that the group shard is added as a member of the group.
     *
     * @param group the group reference
     * @param shard the shard reference
     */
    public void setupGroupShard(DocumentReference group, DocumentReference shard)
    {
        XWikiContext context = contextProvider.get();
        XWiki xwiki = context.getWiki();

        try {
            // Let's first make sure the shard has actually to be set-up
            if (groupManager.getMembers(group, false).contains(shard)) {
                XWikiDocument groupDocument = xwiki.getDocument(group, context);
                addGroupShard(groupDocument, shard, context);
                xwiki.saveDocument(groupDocument, String.format("Add shard [%s]", shard.getName()), context);
            }
        } catch (GroupException | XWikiException e) {
            logger.error("Failed so set-up group shard for group [{}] and shard [{}]", group, shard, e);
        }
    }

    /**
     * Add a shard to the members of the provided group document without saving the document.
     *
     * @param groupDocument the document to modify
     * @param shard the shard to add
     * @param context the current context
     * @throws XWikiException if an error happens
     */
    public void addGroupShard(XWikiDocument groupDocument, DocumentReference shard, XWikiContext context)
        throws XWikiException
    {
        BaseObject groupObject = groupDocument.newXObject(getGroupClass(context), context);
        groupObject.setStringValue("member", entityReferenceSerializer.serialize(shard));
    }

    private DocumentReference getGroupClass(XWikiContext context) throws XWikiException
    {
        return context.getWiki().getGroupClass(context).getXClassReference();
    }
}
