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

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Provider;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.xwiki.component.util.DefaultParameterizedType;
import org.xwiki.contrib.authentication.AuthenticationPersistenceStore;
import org.xwiki.contrib.authentication.TrustedAuthenticationConfiguration;
import org.xwiki.contrib.authentication.TrustedAuthenticationAdapter;
import org.xwiki.contrib.authentication.TrustedAuthenticator;
import org.xwiki.contrib.authentication.UserManager;
import org.xwiki.model.internal.DefaultModelConfiguration;
import org.xwiki.model.internal.DefaultModelContext;
import org.xwiki.model.internal.reference.DefaultEntityReferenceValueProvider;
import org.xwiki.model.internal.reference.DefaultStringDocumentReferenceResolver;
import org.xwiki.model.internal.reference.DefaultStringEntityReferenceResolver;
import org.xwiki.model.internal.reference.DefaultStringEntityReferenceSerializer;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.mockito.MockitoComponentMockingRule;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ComponentList({
    DefaultModelConfiguration.class,
    DefaultModelContext.class,
    DefaultEntityReferenceValueProvider.class,
    DefaultStringEntityReferenceSerializer.class,
    DefaultStringEntityReferenceResolver.class,
    DefaultStringDocumentReferenceResolver.class
    //,DefaultLoggerManager.class,
    //DefaultObservationManager.class
})
public class DefaultTrustedAuthenticatorTest
{
    private static final String USER_WIKI = "xwiki";
    private static final String USER_SPACE = "XWiki";

    private static final String TEST_USER = "test.user@example.com";
    private static final String VALID_TEST_USER = "test=user_example=com";
    private static final String TEST_USER_FN = USER_WIKI + ':' + USER_SPACE + '.' + VALID_TEST_USER;

    private static final DocumentReference TEST_USER_REF = new DocumentReference(USER_WIKI, USER_SPACE, VALID_TEST_USER);

    @Rule
    public final MockitoComponentMockingRule<TrustedAuthenticator> mocker =
        new MockitoComponentMockingRule(DefaultTrustedAuthenticator.class); //, Arrays.asList(Logger.class));

    private XWikiContext context;

    private TrustedAuthenticationConfiguration authConfig;

    private UserManager userManager;

    private TrustedAuthenticationAdapter authAdapter;

    private AuthenticationPersistenceStore store;

    private XWiki xwikimock;

    @Before
    public void before() throws Exception
    {
        //mocker.registerMemoryConfigurationSource();
        //LoggerManager loggerManager = mocker.getInstance(LoggerManager.class);
        //loggerManager.setLoggerLevel("org.xwiki", LogLevel.DEBUG);

        Provider<XWikiContext> contextProvider =
            mocker.getInstance(new DefaultParameterizedType(null, Provider.class, XWikiContext.class));
        context = mock(XWikiContext.class);
        xwikimock = mock(XWiki.class);
        when(contextProvider.get()).thenReturn(context);
        when(context.getWiki()).thenReturn(xwikimock);

        authConfig = mocker.getInstance(TrustedAuthenticationConfiguration.class);
        store = mock(AuthenticationPersistenceStore.class);
        authAdapter = mock(TrustedAuthenticationAdapter.class);
        when(authConfig.getPersistenceStore()).thenReturn(store);
        when(authConfig.getAuthenticationAdapter()).thenReturn(authAdapter);

        userManager = mocker.getInstance(UserManager.class);
    }

    @Test
    public void testAlreadyAuthenticatedInTrustedStore() throws Exception
    {
        when(store.retrieve()).thenReturn(TEST_USER_FN);
        when(authConfig.isPersistenceStoreTrusted()).thenReturn(true);

        assertThat(mocker.getComponentUnderTest().authenticate(), equalTo(TEST_USER_FN));
    }

    @Test
    public void testNoAuthenticationEvenInTrustedStore() throws Exception
    {
        when(authConfig.isPersistenceStoreTrusted()).thenReturn(true);
        assertThat(mocker.getComponentUnderTest().authenticate(), nullValue());
        verify(store, never()).clear();
        verify(store, never()).store(any(String.class));
    }

    @Test
    public void testNoMoreAuthenticatedButInUntrustedStore() throws Exception
    {
        when(store.retrieve()).thenReturn(TEST_USER_FN);
        assertThat(mocker.getComponentUnderTest().authenticate(), nullValue());
        verify(store, times(1)).clear();
        verify(store, never()).store(any(String.class));
    }

    @Test
    public void testNotAuthenticatedEvenInUntrustedStore() throws Exception
    {
        assertThat(mocker.getComponentUnderTest().authenticate(), nullValue());
        verify(store, never()).clear();
        verify(store, never()).store(any(String.class));
    }

    @Test
    public void testAlreadyAuthenticatedInUntrustedStore() throws Exception
    {
        when(store.retrieve()).thenReturn(TEST_USER_FN);
        when(authAdapter.getUserUid()).thenReturn(TEST_USER);
        when(authAdapter.getUserName()).thenReturn(TEST_USER);
        when(userManager.getValidUserName(TEST_USER)).thenReturn(VALID_TEST_USER);
        assertThat(mocker.getComponentUnderTest().authenticate(), equalTo(TEST_USER_FN));
        verify(store, never()).clear();
        verify(store, never()).store(any(String.class));
    }

    @Test
    public void testNewUserAuthenticationFailingCreationNotEmptyUntrustedStore() throws Exception
    {
        when(store.retrieve()).thenReturn("xwiki:XWiki.OtherUser");
        when(authAdapter.getUserUid()).thenReturn(TEST_USER);
        when(authAdapter.getUserName()).thenReturn(TEST_USER);
        when(userManager.getValidUserName(TEST_USER)).thenReturn(VALID_TEST_USER);
        assertThat(mocker.getComponentUnderTest().authenticate(), nullValue());
        verify(store, times(1)).clear();
        verify(store, never()).store(any(String.class));
    }

    @Test
    public void testNewUserAuthenticationFailingCreationEmptyUntrustedStore() throws Exception
    {
        when(authAdapter.getUserUid()).thenReturn(TEST_USER);
        when(authAdapter.getUserName()).thenReturn(TEST_USER);
        when(userManager.getValidUserName(TEST_USER)).thenReturn(VALID_TEST_USER);
        assertThat(mocker.getComponentUnderTest().authenticate(), nullValue());
        verify(store, never()).clear();
        verify(store, never()).store(any(String.class));
    }

    @Test
    public void testNewUserAuthenticationNotEmptyUntrustedStore() throws Exception
    {
        when(store.retrieve()).thenReturn("xwiki:XWiki.OtherUser");
        when(authAdapter.getUserUid()).thenReturn(TEST_USER);
        when(authAdapter.getUserName()).thenReturn(TEST_USER);
        when(userManager.getValidUserName(TEST_USER)).thenReturn(VALID_TEST_USER);
        when(userManager.createUser(TEST_USER_REF, new HashMap<String, String>())).thenReturn(true);
        assertThat(mocker.getComponentUnderTest().authenticate(), equalTo(TEST_USER_FN));
        verify(store, times(1)).clear();
        verify(store, times(1)).store(TEST_USER_FN);
    }

    @Test
    public void testNewUserAuthenticationEmptyUntrustedStore() throws Exception
    {
        when(authAdapter.getUserUid()).thenReturn(TEST_USER);
        when(authAdapter.getUserName()).thenReturn(TEST_USER);
        when(userManager.getValidUserName(TEST_USER)).thenReturn(VALID_TEST_USER);
        when(userManager.createUser(TEST_USER_REF, new HashMap<String, String>())).thenReturn(true);
        assertThat(mocker.getComponentUnderTest().authenticate(), equalTo(TEST_USER_FN));
        verify(store, never()).clear();
        verify(store, times(1)).store(TEST_USER_FN);
    }

    @Test
    public void testExistingUserAuthenticationWithoutUserPropertyMapping() throws Exception
    {
        when(authAdapter.getUserUid()).thenReturn(TEST_USER);
        when(authAdapter.getUserName()).thenReturn(TEST_USER);
        when(userManager.getValidUserName(TEST_USER)).thenReturn(VALID_TEST_USER);
        when(xwikimock.exists(TEST_USER_REF, context)).thenReturn(true);
        assertThat(mocker.getComponentUnderTest().authenticate(), equalTo(TEST_USER_FN));
        verify(store, never()).clear();
        verify(userManager, never()).synchronizeUserProperties(any(DocumentReference.class), any(Map.class), any(String.class));
        verify(store, times(1)).store(TEST_USER_FN);
    }

    private Map<String, String> getAuthInfo()
    {
        Map<String, String> authInfo = new HashMap<>();
        authInfo.put("mail", "user@example.com");
        authInfo.put("givenName", "john");
        authInfo.put("sn", "doe");
        return authInfo;
    }

    private Map<String, String> getUserPropertyMapping()
    {
        Map<String, String> propertyMapping = new HashMap<>();
        propertyMapping.put("email", "mail");
        propertyMapping.put("first_name", "givenName");
        propertyMapping.put("last_name", "sn");
        return propertyMapping;
    }

    private Map<String, String> getExtendedInfo()
    {
        Map<String, String> authInfo = getAuthInfo();
        Map<String, String> extInfo = new HashMap<>();
        for(Map.Entry<String, String> entry : getUserPropertyMapping().entrySet()) {
            extInfo.put(entry.getKey(), authInfo.get(entry.getValue()));
        }
        return extInfo;
    }

    @Test
    public void testExistingUserAuthenticationWithUserPropertyMapping() throws Exception
    {
        Map<String,String> userPropertyMapping = getUserPropertyMapping();

        when(authConfig.getUserPropertyMappings()).thenReturn(userPropertyMapping);
        when(authAdapter.getUserUid()).thenReturn(TEST_USER);
        when(authAdapter.getUserName()).thenReturn(TEST_USER);
        when(authAdapter.getUserProperty(any(String.class))).then(
            new Answer<String>()
            {
                @Override
                public String answer(InvocationOnMock invocationOnMock) throws Throwable
                {
                    return getAuthInfo().get((String) invocationOnMock.getArguments()[0]);
                }
            }
        );
        when(userManager.getValidUserName(TEST_USER)).thenReturn(VALID_TEST_USER);
        when(xwikimock.exists(TEST_USER_REF, context)).thenReturn(true);
        assertThat(mocker.getComponentUnderTest().authenticate(), equalTo(TEST_USER_FN));
        verify(store, never()).clear();
        verify(userManager, times(1)).synchronizeUserProperties(eq(TEST_USER_REF), eq(getExtendedInfo()), any(String.class));
        verify(store, times(1)).store(TEST_USER_FN);
    }

    private Map<String, Collection<String>> getGroupMapping()
    {
        Map<String, Collection<String>> groupMapping = new HashMap<String, Collection<String>>();
        groupMapping.put("XWiki.GroupA", Arrays.asList("groupa", "groupc"));
        groupMapping.put("XWiki.GroupB", Arrays.asList("groupb"));
        groupMapping.put("XWiki.GroupC", Arrays.asList("groupc"));
        groupMapping.put("XWiki.GroupD", Arrays.asList("groupa", "groupd"));
        return groupMapping;
    }

    @Test
    public void testGroupMapping() throws Exception
    {
        when(authConfig.getGroupMappings()).thenReturn(getGroupMapping());
        when(authAdapter.getUserUid()).thenReturn(TEST_USER);
        when(authAdapter.getUserName()).thenReturn(TEST_USER);
        when(authAdapter.isUserInRole("groupc")).thenReturn(true);
        when(userManager.getValidUserName(TEST_USER)).thenReturn(VALID_TEST_USER);
        when(xwikimock.exists(TEST_USER_REF, context)).thenReturn(true);
        assertThat(mocker.getComponentUnderTest().authenticate(), equalTo(TEST_USER_FN));
        verify(store, never()).clear();
        verify(userManager, times(1)).synchronizeGroupsMembership(eq(TEST_USER_REF),
            (Collection<DocumentReference>) argThat(containsInAnyOrder(
                new DocumentReference(USER_WIKI, USER_SPACE, "GroupA"),
                new DocumentReference(USER_WIKI, USER_SPACE, "GroupC"))),
            (Collection<DocumentReference>) argThat(containsInAnyOrder(
                new DocumentReference(USER_WIKI, USER_SPACE, "GroupB"),
                new DocumentReference(USER_WIKI, USER_SPACE, "GroupD"))),
            any(String.class));
        verify(store, times(1)).store(TEST_USER_FN);
    }

    @Test(expected=UnsupportedOperationException.class)
    public void testUnsupportedUsernameUidDiff() throws Exception
    {
        when(authAdapter.getUserUid()).thenReturn("userid");
        when(authAdapter.getUserName()).thenReturn("username");
        mocker.getComponentUnderTest().authenticate();
    }

}
