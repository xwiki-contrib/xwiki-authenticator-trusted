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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.xwiki.contrib.authentication.UserManager;
import org.xwiki.model.internal.DefaultModelConfiguration;
import org.xwiki.model.internal.DefaultModelContext;
import org.xwiki.model.internal.reference.LocalStringEntityReferenceSerializer;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.test.LogRule;
import org.xwiki.test.annotation.ComponentList;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.internal.DefaultXWikiStubContextProvider;
import com.xpn.xwiki.internal.XWikiContextProvider;
import com.xpn.xwiki.internal.model.reference.CompactStringEntityReferenceSerializer;
import com.xpn.xwiki.internal.model.reference.CompactWikiStringEntityReferenceSerializer;
import com.xpn.xwiki.internal.model.reference.CurrentEntityReferenceValueProvider;
import com.xpn.xwiki.internal.model.reference.CurrentReferenceDocumentReferenceResolver;
import com.xpn.xwiki.internal.model.reference.CurrentReferenceEntityReferenceResolver;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.objects.classes.BaseClass;
import com.xpn.xwiki.test.MockitoOldcoreRule;
import com.xpn.xwiki.user.api.XWikiGroupService;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ComponentList({
    DefaultModelConfiguration.class,
    DefaultModelContext.class,
    CurrentEntityReferenceValueProvider.class,
    CompactStringEntityReferenceSerializer.class,
    CompactWikiStringEntityReferenceSerializer.class,
    CurrentReferenceEntityReferenceResolver.class,
    CurrentReferenceDocumentReferenceResolver.class,
    LocalStringEntityReferenceSerializer.class,
    XWikiContextProvider.class,
    DefaultXWikiStubContextProvider.class,
    DefaultUserManager.class
    //,DefaultLoggerManager.class,
    //DefaultObservationManager.class
})
public class DefaultUserManagerTest
{
    private static final String USER_WIKI = "xwiki";
    private static final String USER_SPACE = "XWiki";

    private static final String TEST_USER = "test.user@example.com";
    private static final String VALID_TEST_USER = "test=user_example=com";
    private static final String TEST_USER_FN = USER_WIKI + ':' + USER_SPACE + '.' + VALID_TEST_USER;

    private static final String CHANGE_COMMENT = "Syncho with changes";
    private static final String NOCHANGE_COMMENT = "Syncho without changes";
    private static final String CHANGE1_COMMENT = "Syncho with changes 1";
    private static final String CHANGE2_COMMENT = "Syncho with changes 2";
    private static final String CHANGE3_COMMENT = "Syncho with changes 3";
    private static final String CHANGE4_COMMENT = "Syncho with changes 4";

    private static final String GROUPA_NAME = "GroupA";
    private static final String GROUPB_NAME = "GroupB";
    private static final String GROUPC_NAME = "GroupC";
    private static final String GROUPD_NAME = "GroupD";

    private static final String MEMBER_PROPERTY = "member";

    @Rule
    public MockitoOldcoreRule oldcore = new MockitoOldcoreRule();

    /**
     * Capture warning about missing context manager reported by Utils when getting component from legacy code.
     */
    @Rule
    public LogRule logRule = new LogRule() {{
        record(LogLevel.WARN);
        recordLoggingForType(com.xpn.xwiki.web.Utils.class);
        recordLoggingForType(DefaultUserManager.class);
    }};

    public UserManager userManager;

    private XWikiGroupService groupService;

    private EntityReferenceSerializer<String> serializer;

    @Before
    public void before() throws Exception
    {
        //LoggerManager loggerManager = oldcore.getMocker().getInstance(LoggerManager.class);
        //loggerManager.setLoggerLevel("org.xwiki", LogLevel.DEBUG);

        userManager = oldcore.getMocker().getInstance(UserManager.class);
        serializer = oldcore.getMocker().getInstance(EntityReferenceSerializer.TYPE_STRING, "compact");

        groupService = mock(XWikiGroupService.class);
        when(this.oldcore.getMockXWiki().getGroupService(any(XWikiContext.class))).thenReturn(groupService);

        when(groupService.getAllGroupsReferencesForMember(any(DocumentReference.class), eq(0), eq(0), any(XWikiContext.class))).then(
            new Answer<Collection<DocumentReference>>()
            {
                @Override
                public Collection<DocumentReference> answer(InvocationOnMock invocationOnMock) throws Throwable
                {
                    Collection<DocumentReference> result = new ArrayList<DocumentReference>();
                    String user = serializer.serialize((DocumentReference) invocationOnMock.getArguments()[0]);
                    XWikiContext context = (XWikiContext) invocationOnMock.getArguments()[3];
                    BaseClass groupClass = context.getWiki().getGroupClass(context);
                    for(XWikiDocument doc : oldcore.getDocuments().values()) {
                        if (doc.getXObject(groupClass.getReference(), MEMBER_PROPERTY, user) != null) {
                            result.add(doc.getDocumentReference());
                        }
                    }
                    return result;
                }
            }
        );

        when(oldcore.getMockXWiki().createUser(any(String.class), any(Map.class), any(XWikiContext.class))).then(
            new Answer<Integer>()
            {
                @Override
                public Integer answer(InvocationOnMock invocationOnMock) throws Throwable
                {
                    String username = (String) invocationOnMock.getArguments()[0];
                    Map<String, String> extInfo = (Map<String, String>) invocationOnMock.getArguments()[1];
                    XWikiContext context = (XWikiContext) invocationOnMock.getArguments()[2];

                    XWikiDocument usrDoc = oldcore.getMockXWiki().getDocument(
                        new DocumentReference(USER_WIKI, USER_SPACE, username), context);
                    if (usrDoc.isNew()) {
                        BaseClass userClass = oldcore.getMockXWiki().getUserClass(oldcore.getXWikiContext());
                        BaseObject obj = usrDoc.newXObject(userClass.getReference(), oldcore.getXWikiContext());
                        for (String prop : extInfo.keySet()) {
                            obj.set(prop, extInfo.get(prop), oldcore.getXWikiContext());
                        }
                        oldcore.getMockXWiki().saveDocument(usrDoc, "New user", oldcore.getXWikiContext());
                        return 1;
                    } else {
                        return -3;
                    }
                }
            }
        );
    }

    private XWikiDocument saveNewUserGroupDocument(String userGroup) throws Exception
    {
        return saveNewUserGroupDocument(userGroup, oldcore.getXWikiContext());
    }

    private XWikiDocument saveNewUserGroupDocument(String userGroup, XWikiContext context) throws Exception
    {
        XWikiDocument doc = getUserGroupDoc(userGroup, context);
        if (doc.isNew()) {
            oldcore.getMockXWiki().saveDocument(doc, "New document", oldcore.getXWikiContext());
            return doc;
        }
        return null;
    }

    private XWikiDocument getUserGroupDoc(String userGroup) throws Exception
    {
        return getUserGroupDoc(userGroup, oldcore.getXWikiContext());
    }

    private XWikiDocument getUserGroupDoc(String userGroup, XWikiContext context) throws Exception
    {
        return oldcore.getMockXWiki().getDocument(new DocumentReference(USER_WIKI, USER_SPACE, userGroup),
            context);
    }

    @Test
    public void testGetValidUsername() throws Exception
    {
        assertThat(userManager.getValidUserName(TEST_USER), equalTo(VALID_TEST_USER));
    }

    private Map<String, String> getExtendedInfo()
    {
        Map<String, String> extInfo = new HashMap<>();
        extInfo.put("email", "user@example.com");
        extInfo.put("first_name", "john");
        extInfo.put("last_name", "doe");
        return extInfo;
    }

    @Test
    public void testCreateNewUser() throws Exception
    {
        Map<String, String> extInfo = getExtendedInfo();

        assertThat(userManager.createUser(new DocumentReference(USER_WIKI, USER_SPACE, TEST_USER), extInfo), is(true));

        extInfo.put("active", "1");

        verify(oldcore.getMockXWiki(), times(1))
            .createUser(eq(TEST_USER), eq(extInfo), eq(oldcore.getXWikiContext()));
    }

    @Test
    public void testCreateNewInactiveUser() throws Exception
    {
        Map<String, String> extInfo = getExtendedInfo();

        extInfo.put("active", "0");

        assertThat(userManager.createUser(new DocumentReference(USER_WIKI, USER_SPACE, VALID_TEST_USER), extInfo), is(true));

        verify(oldcore.getMockXWiki(), times(1))
            .createUser(eq(VALID_TEST_USER), eq(extInfo), eq(oldcore.getXWikiContext()));
    }

    @Test
    public void testCreateDuplicateUser() throws Exception
    {
        Map<String, String> extInfo = getExtendedInfo();

        assertThat(userManager.createUser(new DocumentReference(USER_WIKI, USER_SPACE, TEST_USER), extInfo), is(true));
        assertThat(userManager.createUser(new DocumentReference(USER_WIKI, USER_SPACE, TEST_USER), extInfo), is(false));
    }

    private void checkUserInfos(DocumentReference userRef, Map<String, String> extInfo, boolean changes)
        throws XWikiException
    {
        XWikiDocument usrDoc = oldcore.getMockXWiki().getDocument(userRef, oldcore.getXWikiContext());
        assertThat(usrDoc.isNew(), is(false));
        if (changes) {
            assertThat(usrDoc.getComment(), equalTo(CHANGE_COMMENT));
        } else {
            assertThat(usrDoc.getComment(), not(equalTo(NOCHANGE_COMMENT)));
        }
        BaseClass userClass = oldcore.getMockXWiki().getUserClass(oldcore.getXWikiContext());
        BaseObject userObj = usrDoc.getXObject(userClass.getReference());
        assertThat(userObj, not(nullValue()));
        for (String prop : extInfo.keySet()) {
            assertThat(userObj.getStringValue(prop), equalTo(extInfo.get(prop)));
        }
    }

    @Test
    public void testMissingUserProfileSynchronization() throws Exception
    {
        Map<String, String> extInfo = getExtendedInfo();
        DocumentReference userRef = new DocumentReference(USER_WIKI, USER_SPACE, TEST_USER);
        assertThat(userManager.synchronizeUserProperties(userRef, extInfo, CHANGE_COMMENT), is(false));
        assertThat(
            logRule.contains("User [" + userRef + "] does not exist and will not be synchronized"),
            is(true));
    }

    @Test
    public void testUserProfileSynchronizationWithChanges() throws Exception
    {
        Map<String, String> extInfo = getExtendedInfo();
        DocumentReference userRef = new DocumentReference(USER_WIKI, USER_SPACE, TEST_USER);

        assertThat(userManager.createUser(new DocumentReference(USER_WIKI, USER_SPACE, TEST_USER), extInfo), is(true));

        extInfo.put("email", "john.doe@example.com");
        extInfo.put("unknown", "to be dropped");
        extInfo.put("comment", "XWiki is great !");

        assertThat(userManager.synchronizeUserProperties(userRef, extInfo, CHANGE_COMMENT), is(true));

        assertThat(
            logRule.contains("User property [unknown] does not exist in user profile and will not be synchronized"),
            is(true));
        extInfo.remove("unknown");

        checkUserInfos(userRef, extInfo, false);
    }

    @Test
    public void testUserProfileSynchronizationWithoutChange() throws Exception
    {
        Map<String, String> extInfo = getExtendedInfo();
        DocumentReference userRef = new DocumentReference(USER_WIKI, USER_SPACE, TEST_USER);

        assertThat(userManager.createUser(new DocumentReference(USER_WIKI, USER_SPACE, TEST_USER), extInfo), is(true));

        extInfo.put("unknown", "to be dropped");

        assertThat(userManager.synchronizeUserProperties(userRef, extInfo, NOCHANGE_COMMENT), is(true));

        assertThat(
            logRule.contains("User property [unknown] does not exist in user profile and will not be synchronized"),
            is(true));
        extInfo.remove("unknown");
        checkUserInfos(userRef, extInfo, false);
    }

    @Test
    public void testAddToExistingGroup() throws Exception
    {
        BaseClass groupClass = oldcore.getXWikiContext().getWiki().getGroupClass(oldcore.getXWikiContext());
        DocumentReference userRef = new DocumentReference(USER_WIKI, USER_SPACE, TEST_USER);
        DocumentReference groupRef = new DocumentReference(USER_WIKI, USER_SPACE, GROUPA_NAME);

        assertThat(userManager.createUser(userRef, Collections.<String, String>emptyMap()), is(true));
        saveNewUserGroupDocument(GROUPA_NAME);

        assertThat(userManager.addToGroup(userRef, groupRef, CHANGE_COMMENT, false), is(true));

        assertThat(getUserGroupDoc(GROUPA_NAME).getComment(), equalTo(CHANGE_COMMENT));
        assertThat(getUserGroupDoc(GROUPA_NAME).getXObject(groupClass.getReference(), MEMBER_PROPERTY,
            serializer.serialize(userRef)), not(nullValue()));

        assertThat(userManager.addToGroup(userRef, groupRef, NOCHANGE_COMMENT, false), is(true));

        assertThat(getUserGroupDoc(GROUPA_NAME).getComment(), not(equalTo(NOCHANGE_COMMENT)));
    }

    @Test
    public void testAddToMissingGroup() throws Exception
    {
        DocumentReference userRef = new DocumentReference(USER_WIKI, USER_SPACE, TEST_USER);
        DocumentReference groupRef = new DocumentReference(USER_WIKI, USER_SPACE, GROUPA_NAME);
        assertThat(userManager.createUser(userRef, Collections.<String, String>emptyMap()), is(true));

        assertThat(userManager.addToGroup(userRef, groupRef, CHANGE_COMMENT, false), is(false));

        assertThat(
            logRule.contains("User [" + userRef + "] cannot be added to unknown group [" + groupRef + "]"),
            is(true));
    }

    @Test
    public void testAddToMissingGroupWithCreation() throws Exception
    {
        BaseClass groupClass = oldcore.getXWikiContext().getWiki().getGroupClass(oldcore.getXWikiContext());
        DocumentReference userRef = new DocumentReference(USER_WIKI, USER_SPACE, TEST_USER);
        DocumentReference groupRef = new DocumentReference(USER_WIKI, USER_SPACE, GROUPA_NAME);

        assertThat(userManager.createUser(userRef, Collections.<String, String>emptyMap()), is(true));

        assertThat(userManager.addToGroup(userRef,
            new DocumentReference(USER_WIKI, USER_SPACE, GROUPA_NAME), CHANGE_COMMENT, true), is(true));

        assertThat(getUserGroupDoc(GROUPA_NAME).getComment(), equalTo(CHANGE_COMMENT));
        assertThat(getUserGroupDoc(GROUPA_NAME).getXObject(groupClass.getReference(), MEMBER_PROPERTY,
            serializer.serialize(userRef)), not(nullValue()));
    }

    @Test
    public void testRemoveFromExistingGroup() throws Exception
    {
        BaseClass groupClass = oldcore.getXWikiContext().getWiki().getGroupClass(oldcore.getXWikiContext());
        DocumentReference userRef = new DocumentReference(USER_WIKI, USER_SPACE, TEST_USER);
        DocumentReference groupRef = new DocumentReference(USER_WIKI, USER_SPACE, GROUPA_NAME);

        assertThat(userManager.createUser(userRef, Collections.<String, String>emptyMap()), is(true));
        saveNewUserGroupDocument(GROUPA_NAME);
        assertThat(userManager.addToGroup(userRef, groupRef, CHANGE_COMMENT, true), is(true));
        assertThat(getUserGroupDoc(GROUPA_NAME).getXObject(groupClass.getReference(), MEMBER_PROPERTY,
            serializer.serialize(userRef)), not(nullValue()));

        assertThat(userManager.removeFromGroup(userRef, groupRef, CHANGE_COMMENT), is(true));
        assertThat(getUserGroupDoc(GROUPA_NAME).getXObject(groupClass.getReference(), MEMBER_PROPERTY,
            serializer.serialize(userRef)), nullValue());
    }

    @Test
    public void testRemoveFromMissingGroup() throws Exception
    {
        DocumentReference userRef = new DocumentReference(USER_WIKI, USER_SPACE, TEST_USER);
        DocumentReference groupRef = new DocumentReference(USER_WIKI, USER_SPACE, GROUPA_NAME);

        assertThat(userManager.createUser(userRef, Collections.<String, String>emptyMap()), is(true));

        assertThat(userManager.removeFromGroup(userRef, groupRef, CHANGE_COMMENT), is(false));
        assertThat(
            logRule.contains("User [" + userRef + "] cannot be removed from unknown group [" + groupRef + "]"),
            is(true));
    }

    @Test
    public void testRemoveFromNonMemberGroup() throws Exception
    {
        DocumentReference userRef = new DocumentReference(USER_WIKI, USER_SPACE, TEST_USER);
        DocumentReference groupRef = new DocumentReference(USER_WIKI, USER_SPACE, GROUPA_NAME);

        assertThat(userManager.createUser(userRef, Collections.<String, String>emptyMap()), is(true));
        saveNewUserGroupDocument(GROUPA_NAME);

        assertThat(userManager.removeFromGroup(userRef, groupRef, NOCHANGE_COMMENT), is(true));

        assertThat(getUserGroupDoc(GROUPA_NAME).getComment(), not(equalTo(NOCHANGE_COMMENT)));
    }

    @Test
    public void testGroupSynchronization() throws Exception
    {
        saveNewUserGroupDocument(GROUPA_NAME);
        saveNewUserGroupDocument(GROUPB_NAME);
        saveNewUserGroupDocument(GROUPC_NAME);
        saveNewUserGroupDocument(GROUPD_NAME);
        DocumentReference userRef1 = new DocumentReference(USER_WIKI, USER_SPACE, TEST_USER);
        DocumentReference userRef2 = new DocumentReference(USER_WIKI, USER_SPACE, VALID_TEST_USER);

        assertThat(userManager.createUser(userRef1, Collections.<String, String>emptyMap()), is(true));
        assertThat(userManager.createUser(userRef2, Collections.<String, String>emptyMap()), is(true));

        assertThat(userManager.synchronizeGroupsMembership(
            userRef1,
            Arrays.asList(new DocumentReference(USER_WIKI, USER_SPACE, GROUPC_NAME),
                          new DocumentReference(USER_WIKI, USER_SPACE, GROUPA_NAME)),
            Collections.<DocumentReference>emptyList(), CHANGE1_COMMENT
        ), is(true));

        assertThat(getUserGroupDoc(GROUPA_NAME).getComment(), equalTo(CHANGE1_COMMENT));
        assertThat(getUserGroupDoc(GROUPC_NAME).getComment(), equalTo(CHANGE1_COMMENT));

        assertThat(userManager.synchronizeGroupsMembership(
            userRef2,
            Arrays.asList(new DocumentReference(USER_WIKI, USER_SPACE, GROUPA_NAME),
                          new DocumentReference(USER_WIKI, USER_SPACE, GROUPD_NAME),
                          new DocumentReference(USER_WIKI, USER_SPACE, GROUPB_NAME)),
            Collections.<DocumentReference>emptyList(), CHANGE2_COMMENT
        ), is(true));

        assertThat(getUserGroupDoc(GROUPA_NAME).getComment(), equalTo(CHANGE2_COMMENT));
        assertThat(getUserGroupDoc(GROUPB_NAME).getComment(), equalTo(CHANGE2_COMMENT));
        assertThat(getUserGroupDoc(GROUPC_NAME).getComment(), equalTo(CHANGE1_COMMENT));
        assertThat(getUserGroupDoc(GROUPD_NAME).getComment(), equalTo(CHANGE2_COMMENT));

        XWikiContext context = oldcore.getXWikiContext();
        String database = context.getWikiId();
        try {
            // Switch to main wiki to force users to be global users
            context.setWikiId(context.getMainXWiki());
            assertThat(groupService.getAllGroupsReferencesForMember(userRef1, 0, 0, context),
                containsInAnyOrder(new DocumentReference(USER_WIKI, USER_SPACE, GROUPA_NAME),
                    new DocumentReference(USER_WIKI, USER_SPACE, GROUPC_NAME)));
            assertThat(groupService.getAllGroupsReferencesForMember(userRef2, 0, 0, context),
                containsInAnyOrder(new DocumentReference(USER_WIKI, USER_SPACE, GROUPA_NAME),
                    new DocumentReference(USER_WIKI, USER_SPACE, GROUPB_NAME),
                    new DocumentReference(USER_WIKI, USER_SPACE, GROUPD_NAME)));
        } finally {
            context.setWikiId(database);
        }

        assertThat(userManager.synchronizeGroupsMembership(
            userRef1,
            Arrays.asList(new DocumentReference(USER_WIKI, USER_SPACE, GROUPA_NAME),
                          new DocumentReference(USER_WIKI, USER_SPACE, GROUPD_NAME),
                          new DocumentReference(USER_WIKI, USER_SPACE, GROUPB_NAME)),
            Collections.singletonList(new DocumentReference(USER_WIKI, USER_SPACE, GROUPC_NAME)), CHANGE3_COMMENT
        ), is(true));

        assertThat(getUserGroupDoc(GROUPA_NAME).getComment(), equalTo(CHANGE2_COMMENT));
        assertThat(getUserGroupDoc(GROUPB_NAME).getComment(), equalTo(CHANGE3_COMMENT));
        assertThat(getUserGroupDoc(GROUPC_NAME).getComment(), equalTo(CHANGE3_COMMENT));
        assertThat(getUserGroupDoc(GROUPD_NAME).getComment(), equalTo(CHANGE3_COMMENT));

        assertThat(userManager.synchronizeGroupsMembership(
            userRef2,
            Arrays.asList(new DocumentReference(USER_WIKI, USER_SPACE, GROUPC_NAME),
                          new DocumentReference(USER_WIKI, USER_SPACE, GROUPA_NAME)),
            Arrays.asList(new DocumentReference(USER_WIKI, USER_SPACE, GROUPD_NAME),
                          new DocumentReference(USER_WIKI, USER_SPACE, GROUPB_NAME)), CHANGE4_COMMENT
        ), is(true));

        assertThat(getUserGroupDoc(GROUPA_NAME).getComment(), equalTo(CHANGE2_COMMENT));
        assertThat(getUserGroupDoc(GROUPB_NAME).getComment(), equalTo(CHANGE4_COMMENT));
        assertThat(getUserGroupDoc(GROUPC_NAME).getComment(), equalTo(CHANGE4_COMMENT));
        assertThat(getUserGroupDoc(GROUPD_NAME).getComment(), equalTo(CHANGE4_COMMENT));

        database = context.getWikiId();
        try {
            // Switch to main wiki to force users to be global users
            context.setWikiId(context.getMainXWiki());

            assertThat(groupService.getAllGroupsReferencesForMember(userRef1, 0, 0, context),
                containsInAnyOrder(new DocumentReference(USER_WIKI, USER_SPACE, GROUPA_NAME),
                    new DocumentReference(USER_WIKI, USER_SPACE, GROUPB_NAME),
                    new DocumentReference(USER_WIKI, USER_SPACE, GROUPD_NAME)));
            assertThat(groupService.getAllGroupsReferencesForMember(userRef2, 0, 0, context),
                containsInAnyOrder(new DocumentReference(USER_WIKI, USER_SPACE, GROUPA_NAME),
                    new DocumentReference(USER_WIKI, USER_SPACE, GROUPC_NAME)));
        } finally {
            context.setWikiId(database);
        }
    }
}
