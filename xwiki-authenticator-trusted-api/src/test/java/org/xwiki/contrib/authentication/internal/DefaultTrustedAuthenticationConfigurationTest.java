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

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.xwiki.contrib.authentication.AuthenticationPersistenceStore;
import org.xwiki.contrib.authentication.TrustedAuthenticationAdapter;
import org.xwiki.contrib.authentication.TrustedAuthenticationConfiguration;
import org.xwiki.contrib.authentication.TrustedAuthenticationConfiguration.CaseStyle;
import org.xwiki.model.internal.DefaultModelConfiguration;
import org.xwiki.model.internal.DefaultModelContext;
import org.xwiki.test.LogRule;
import org.xwiki.test.annotation.ComponentList;

import com.xpn.xwiki.internal.DefaultXWikiStubContextProvider;
import com.xpn.xwiki.internal.XWikiContextProvider;
import com.xpn.xwiki.internal.model.reference.CompactWikiStringEntityReferenceSerializer;
import com.xpn.xwiki.internal.model.reference.CurrentEntityReferenceValueProvider;
import com.xpn.xwiki.internal.model.reference.CurrentStringDocumentReferenceResolver;
import com.xpn.xwiki.internal.model.reference.CurrentStringEntityReferenceResolver;
import com.xpn.xwiki.test.MockitoOldcoreRule;
import com.xpn.xwiki.user.impl.xwiki.GroovyAuthServiceImpl;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ComponentList({
    DefaultModelConfiguration.class,
    DefaultModelContext.class,
    CurrentEntityReferenceValueProvider.class,
    CurrentStringEntityReferenceResolver.class,
    CurrentStringDocumentReferenceResolver.class,
    CompactWikiStringEntityReferenceSerializer.class,
    DefaultTrustedAuthenticationConfiguration.class,
    CookieAuthenticationPersistenceStore.class,
    SessionAuthenticationPersistenceStore.class,
    XWikiContextProvider.class,
    DefaultXWikiStubContextProvider.class
})
public class DefaultTrustedAuthenticationConfigurationTest
{
    private static final String PREF_PREFIX = "trustedauth_";

    private static final String CONF_PREFIX = "xwiki.authentication.trusted.";

    private static final String AUTHENTICATION_ADAPTER_HINT_PROPERTY = "adapterHint";
    private static final String AUTHENTICATION_ADAPTER_HINT_CUSTOM = "custom";

    private static final String PERSISTANCE_STORE_HINT_PROPERTY = "persistenceStoreHint";
    private static final String PERSISTANCE_STORE_HINT_DEFAULT = "session";
    private static final String PERSISTANCE_STORE_HINT_SESSION = PERSISTANCE_STORE_HINT_DEFAULT;
    private static final String PERSISTANCE_STORE_HINT_COOKIE = "cookie";

    private static final String PERSISTANCE_STORE_TRUSTED_PROPERTY = "isPersistenceStoreTrusted";
    private static final boolean PERSISTANCE_STORE_TRUSTED_DEFAULT = false;

    private static final String PERSISTANCE_STORE_TTL_PROPERTY = "persistenceStoreTTL";
    private static final int PERSISTANCE_STORE_TTL_DEFAULT = -1;

    private static final String AUTHORITATIVE_PROPERTY = "isAuthoritative";
    private static final boolean AUTHORITATIVE_DEFAULT = false;

    private static final String FALLBACK_AUTH_PROPERTY = "fallbackAuthenticator";

    private static final String USERPROFILE_CASE_PROPERTY = "userProfileCase";
    private static final CaseStyle USERPROFILE_CASE_DEFAULT = CaseStyle.LOWERCASE;

    private static final String USERPROFILE_REPLACEMENTS_PROPERTY = "userProfileReplacements";
    private static final char USERPROFILE_REPLACEMENTS_SEP = '|';

    private static final String GROUP_MAPPING_PROPERTY = "groupsMapping";
    private static final String PROPERTY_MAPPING_PROPERTY = "propertiesMapping";

    private static final String LOGOUTPAGE_CONFIG_KEY = "xwiki.authentication.logoutpage";
    private static final String LOGOUTPAGE_PATTERN = "(/|/[^/]+/|/wiki/[^/]+/)logout/*";

    @Rule
    public MockitoOldcoreRule oldcore = new MockitoOldcoreRule();

    /**
     * Capture warning about missing context manager reported by Utils when getting component from legacy code.
     */
    @Rule
    public LogRule logRule = new LogRule() {{
        record(LogLevel.WARN);
        recordLoggingForType(com.xpn.xwiki.web.Utils.class);
    }};

    private Map<String, String> config = new HashMap<>();

    private TrustedAuthenticationConfiguration configuration;

    @Before
    public void before() throws Exception
    {
        this.configuration = oldcore.getMocker().getInstance(TrustedAuthenticationConfiguration.class);
        this.oldcore.getXWikiContext().setWikiId("wiki");
        when(this.oldcore.getMockXWiki().Param(any(String.class))).then(
            new Answer<String>()
            {
                @Override
                public String answer(InvocationOnMock invocationOnMock) throws Throwable
                {
                    String paramName = (String) invocationOnMock.getArguments()[0];

                    if (config.containsKey(paramName)) {
                        return config.get(paramName);
                    }
                    return null;
                }
            }
        );
    }

    @Test
    public void testDefaultAuthenticationAdapater() throws Exception
    {
        TrustedAuthenticationAdapter defaultAuthenticationAdapter = mock(TrustedAuthenticationAdapter.class);
        TrustedAuthenticationAdapter customAuthenticationAdapter = mock(TrustedAuthenticationAdapter.class);

        oldcore.getMocker().registerComponent(TrustedAuthenticationAdapter.class, defaultAuthenticationAdapter);
        oldcore.getMocker().registerComponent(TrustedAuthenticationAdapter.class, AUTHENTICATION_ADAPTER_HINT_CUSTOM,
            customAuthenticationAdapter);

        assertThat(configuration.getAuthenticationAdapter(), sameInstance(defaultAuthenticationAdapter));
    }

    @Test
    public void testCustomAuthenticationAdapter() throws Exception
    {
        TrustedAuthenticationAdapter defaultAuthenticationAdapter = mock(TrustedAuthenticationAdapter.class);
        TrustedAuthenticationAdapter customAuthenticationAdapter = mock(TrustedAuthenticationAdapter.class);

        oldcore.getMocker().registerComponent(TrustedAuthenticationAdapter.class, defaultAuthenticationAdapter);
        oldcore.getMocker().registerComponent(TrustedAuthenticationAdapter.class,
            AUTHENTICATION_ADAPTER_HINT_CUSTOM, customAuthenticationAdapter);

        config.put(CONF_PREFIX + AUTHENTICATION_ADAPTER_HINT_PROPERTY, AUTHENTICATION_ADAPTER_HINT_CUSTOM);

        assertThat(configuration.getAuthenticationAdapter(), sameInstance(customAuthenticationAdapter));
    }

    @Test
    public void testDefaultPersistenceStore() throws Exception
    {
        AuthenticationPersistenceStore defaultPersistenceStore =
            oldcore.getMocker().getInstance(AuthenticationPersistenceStore.class, PERSISTANCE_STORE_HINT_DEFAULT);
        assertThat(configuration.getPersistenceStore(), sameInstance(defaultPersistenceStore));
    }

    @Test
    public void testSessionPersistenceStore() throws Exception
    {
        AuthenticationPersistenceStore sessionPersistenceStore =
            oldcore.getMocker().getInstance(AuthenticationPersistenceStore.class, PERSISTANCE_STORE_HINT_SESSION);

        config.put(CONF_PREFIX + PERSISTANCE_STORE_HINT_PROPERTY, PERSISTANCE_STORE_HINT_SESSION);

        assertThat(configuration.getPersistenceStore(), sameInstance(sessionPersistenceStore));
    }

    @Test
    public void testCookiePersistenceStore() throws Exception
    {
        AuthenticationPersistenceStore cookiePersistenceStore =
            oldcore.getMocker().getInstance(AuthenticationPersistenceStore.class, PERSISTANCE_STORE_HINT_COOKIE);

        config.put(CONF_PREFIX + PERSISTANCE_STORE_HINT_PROPERTY, PERSISTANCE_STORE_HINT_COOKIE);

        assertThat(configuration.getPersistenceStore(), sameInstance(cookiePersistenceStore));
    }

    @Test
    public void testDefaultPersistenceTrust() throws Exception
    {
        assertThat(configuration.isPersistenceStoreTrusted(), is(PERSISTANCE_STORE_TRUSTED_DEFAULT));
    }

    @Test
    public void testNonDefaultPersistenceTrust() throws Exception
    {
        config.put(CONF_PREFIX + PERSISTANCE_STORE_TRUSTED_PROPERTY,
            Boolean.toString(!PERSISTANCE_STORE_TRUSTED_DEFAULT));
        assertThat(configuration.isPersistenceStoreTrusted(), is(!PERSISTANCE_STORE_TRUSTED_DEFAULT));
    }

    @Test
    public void testDefaultPersistenceStoreTTL() throws Exception
    {
        assertThat(configuration.getPersistenceTTL(), equalTo(PERSISTANCE_STORE_TTL_DEFAULT));
    }

    @Test
    public void testCustomPersistenceStoreTTL() throws Exception
    {
        config.put(CONF_PREFIX + PERSISTANCE_STORE_TTL_PROPERTY, "86400");
        assertThat(configuration.getPersistenceTTL(), equalTo(86400));
    }

    @Test
    public void testDefaultUserProfileCaseStyle() throws Exception
    {
        assertThat(configuration.getUserProfileCaseStyle(), equalTo(USERPROFILE_CASE_DEFAULT));
    }

    @Test
    public void testCustomUserProfileCaseStyle() throws Exception
    {
        config.put(CONF_PREFIX + USERPROFILE_CASE_PROPERTY, "UpperCase");
        assertThat(configuration.getUserProfileCaseStyle(), equalTo(CaseStyle.UPPERCASE));
    }

    @Test
    public void testDefaultUserProfileReplacements() throws Exception
    {
        assertThat(configuration.getUserProfileReplacements().isEmpty(), is(true));
    }

    @Test
    public void testCustomUserProfileReplacements() throws Exception
    {
        config.put(CONF_PREFIX + USERPROFILE_REPLACEMENTS_PROPERTY, ".==|@=_|&=");
        Map<String, String> expected = new HashMap<String, String>();
        expected.put(".","=");
        expected.put("@","_");
        expected.put("&","");
        assertThat(configuration.getUserProfileReplacements(), equalTo(expected));
    }

    @Test
    public void testUserPropertyMapping() throws Exception
    {
        config.put(CONF_PREFIX + PROPERTY_MAPPING_PROPERTY, "first_name=f=name|last\\|name=lname|email=ma\\|il");
        Map<String, String> expected = new HashMap<String, String>();
        expected.put("first_name","f=name");
        expected.put("last|name","lname");
        expected.put("email","ma|il");
        assertThat(configuration.getUserPropertyMappings(), equalTo(expected));
    }

    @Test
    public void testUnconfiguredUserPropertyMapping() throws Exception
    {
        assertThat(configuration.getUserPropertyMappings(), not(nullValue()));
        assertThat(configuration.getUserPropertyMappings().size(), equalTo(0));
    }

    @Test
    public void testGroupMapping() throws Exception
    {
        config.put(CONF_PREFIX + GROUP_MAPPING_PROPERTY, "XWiki.GroupA=group=a|XWiki.Gro\\|upB=groupb|XWiki.GroupA=gro\\|upc");
        Map<String, Collection<String>> expected = new HashMap<String, Collection<String>>();
        expected.put("XWiki.GroupA", Arrays.asList("group=a", "gro|upc"));
        expected.put("XWiki.Gro|upB", Arrays.asList("groupb"));
        Map<String, Collection<String>> actual = configuration.getGroupMappings();
        assertThat(actual.size(), equalTo(2));
        for (Map.Entry<String, Collection<String>> entry : actual.entrySet()) {
            assertThat(expected.containsKey(entry.getKey()), is(true));
            assertThat(entry.getValue(), containsInAnyOrder(expected.get(entry.getKey()).toArray()));
        }
    }

    @Test
    public void testUnconfiguredGroupMapping() throws Exception
    {
        assertThat(configuration.getGroupMappings(), not(nullValue()));
        assertThat(configuration.getGroupMappings().size(), equalTo(0));
    }

    @Test
    public void testDefaultAuthoritative() throws Exception
    {
        assertThat(configuration.isAuthoritative(), is(AUTHORITATIVE_DEFAULT));
    }

    @Test
    public void testNonDefaultAuthoritative() throws Exception
    {
        config.put(CONF_PREFIX + AUTHORITATIVE_PROPERTY, Boolean.toString(!AUTHORITATIVE_DEFAULT));
        assertThat(configuration.isAuthoritative(), is(!AUTHORITATIVE_DEFAULT));
    }

    @Test
    public void testDefaultFallbackAuthService() throws Exception
    {
        assertThat(configuration.getFallbackAuthenticator(), nullValue());
    }

    @Test
    public void testCustomFallbackAuthService() throws Exception
    {
        config.put(CONF_PREFIX + FALLBACK_AUTH_PROPERTY, GroovyAuthServiceImpl.class.getCanonicalName());
        assertThat(configuration.getFallbackAuthenticator(), instanceOf(GroovyAuthServiceImpl.class));
    }

    @Test
    public void testGetLogoutPagePattern() throws Exception
    {
        config.put(LOGOUTPAGE_CONFIG_KEY, LOGOUTPAGE_PATTERN);
        assertThat(configuration.getLogoutPagePattern(), equalTo(LOGOUTPAGE_PATTERN));
    }

}
