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

import java.util.HashMap;
import java.util.Map;

import javax.inject.Provider;
import javax.servlet.http.Cookie;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.xwiki.component.util.DefaultParameterizedType;
import org.xwiki.contrib.authentication.AuthenticationPersistenceStore;
import org.xwiki.contrib.authentication.TrustedAuthenticationConfiguration;
import org.xwiki.test.mockito.MockitoComponentMockingRule;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.web.XWikiRequest;
import com.xpn.xwiki.web.XWikiResponse;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CookieAuthenticationPersistenceStoreTest
{
    private static final String AUTHENTICATION_CONFIG_PREFIX = "xwiki.authentication";

    private static final String COOKIE_PREFIX_PROPERTY = AUTHENTICATION_CONFIG_PREFIX + ".cookieprefix";
    private static final String COOKIE_PATH_PROPERTY= AUTHENTICATION_CONFIG_PREFIX + ".cookiepath";
    private static final String COOKIE_DOMAINS_PROPERTY = AUTHENTICATION_CONFIG_PREFIX + ".cookiedomains";
    private static final String ENCRYPTION_KEY_PROPERTY = AUTHENTICATION_CONFIG_PREFIX + ".encryptionKey";


    private static final String TEST_USER = "test.user@example.com";
    private static final String AUTHENTICATION_COOKIE = "XWIKITRUSTEDAUTH";
    private static final int    PERSISTANCE_STORE_TTL = 12345;

    private static final String TEST_COOKIE_PREFIX = "prefix";
    private static final String TEST_COOKIE_DOMAIN = "example.com";
    private static final String TEST_COOKIE_PATH = "/path/for/cookie";

    @Rule
    public final MockitoComponentMockingRule<AuthenticationPersistenceStore> mocker =
        new MockitoComponentMockingRule(CookieAuthenticationPersistenceStore.class);

    private TrustedAuthenticationConfiguration authConfig;

    private XWikiContext context;

    private XWikiRequest request;

    private XWikiResponse response;

    private XWiki xwikimock;

    private Map<String, String> config = new HashMap<>();

    private Map<String, Cookie> map = new HashMap<>();

    @Before
    public void before() throws Exception
    {
        xwikimock = mock(XWiki.class);
        Provider<XWikiContext> contextProvider =
            mocker.getInstance(new DefaultParameterizedType(null, Provider.class, XWikiContext.class));
        authConfig = mocker.getInstance(TrustedAuthenticationConfiguration.class);
        context = mock(XWikiContext.class);
        request = mock(XWikiRequest.class);
        response = mock(XWikiResponse.class);

        when(contextProvider.get()).thenReturn(context);
        when(authConfig.getPersistenceTTL()).thenReturn(PERSISTANCE_STORE_TTL);
        when(context.getWiki()).thenReturn(xwikimock);
        when(context.getRequest()).thenReturn(request);
        when(context.getResponse()).thenReturn(response);

        when(xwikimock.Param(any(String.class), any(String.class))).then(
            new Answer<String>()
            {
                @Override
                public String answer(InvocationOnMock invocationOnMock) throws Throwable
                {
                    String paramName = (String) invocationOnMock.getArguments()[0];

                    if (config.containsKey(paramName)) {
                        return config.get(paramName);
                    }
                    return (String) invocationOnMock.getArguments()[1];
                }
            }
        );

        when(xwikimock.Param(any(String.class))).then(
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

        doAnswer(new Answer<Object>() {
            public Object answer(InvocationOnMock invocationOnMock) {
                Cookie cookie = (Cookie) invocationOnMock.getArguments()[0];
                map.put(cookie.getName(), cookie);
                return null;
            }
        }).when(response).addCookie(any(Cookie.class));

        when(request.getCookie(any(String.class))).then(
            new Answer<Cookie>()
            {
                @Override
                public Cookie answer(InvocationOnMock invocationOnMock) throws Throwable
                {
                    String key = (String) invocationOnMock.getArguments()[0];
                    return map.get(key);
                }
            }
        );

        doAnswer(new Answer<Object>() {
            public Object answer(InvocationOnMock invocationOnMock) {
                String key = (String) invocationOnMock.getArguments()[0];
                map.remove(key);
                return null;
            }
        }).when(response).removeCookie(any(String.class), eq(request));

        config.put(ENCRYPTION_KEY_PROPERTY, "GUhxLXCCg2x8EbohwtLt9B2r");
    }

    @Test
    public void testStoreRetrieveAndClear() throws Exception
    {
        mocker.getComponentUnderTest().store(TEST_USER);
        assertThat(mocker.getComponentUnderTest().retrieve(), equalTo(TEST_USER));

        Cookie cookie = map.get(AUTHENTICATION_COOKIE);
        assertThat(cookie, not(nullValue()));
        assertThat(cookie.getValue(), not(nullValue()));
        assertThat(cookie.getValue(), not(equalTo(TEST_USER)));
        assertThat(cookie.getMaxAge(), equalTo(PERSISTANCE_STORE_TTL));
        assertThat(cookie.getPath(), equalTo("/"));
        assertThat(cookie.getDomain(), nullValue());
        assertThat(cookie.getSecure(), is(false));

        mocker.getComponentUnderTest().clear();
        assertThat(mocker.getComponentUnderTest().retrieve(), nullValue());
    }

    @Test
    public void testCookiePrefix() throws Exception
    {
        config.put(COOKIE_PREFIX_PROPERTY, TEST_COOKIE_PREFIX);

        mocker.getComponentUnderTest().store(TEST_USER);
        assertThat(mocker.getComponentUnderTest().retrieve(), equalTo(TEST_USER));

        Cookie cookie = map.get(TEST_COOKIE_PREFIX + AUTHENTICATION_COOKIE);
        assertThat(cookie, not(nullValue()));

        mocker.getComponentUnderTest().clear();
        assertThat(mocker.getComponentUnderTest().retrieve(), nullValue());
    }

    @Test
    public void testCookiePath() throws Exception
    {
        config.put(COOKIE_PATH_PROPERTY, TEST_COOKIE_PATH);

        mocker.getComponentUnderTest().store(TEST_USER);

        Cookie cookie = map.get(AUTHENTICATION_COOKIE);
        assertThat(cookie, not(nullValue()));
        assertThat(cookie.getPath(), equalTo(TEST_COOKIE_PATH));
    }

    @Test
    public void testCookieDomainUnprefixed() throws Exception
    {
        config.put(COOKIE_DOMAINS_PROPERTY,"xwiki.com," + TEST_COOKIE_DOMAIN);
        when(request.getServerName()).thenReturn("localhost." + TEST_COOKIE_DOMAIN);

        mocker.getComponentUnderTest().store(TEST_USER);

        Cookie cookie = map.get(AUTHENTICATION_COOKIE);
        assertThat(cookie, not(nullValue()));
        assertThat(cookie.getDomain(), equalTo("." + TEST_COOKIE_DOMAIN));
    }

    @Test
    public void testCookieDomainDotPrefixed() throws Exception
    {
        config.put(COOKIE_DOMAINS_PROPERTY,".xwiki.com,." + TEST_COOKIE_DOMAIN);
        when(request.getServerName()).thenReturn("localhost." + TEST_COOKIE_DOMAIN);

        mocker.getComponentUnderTest().store(TEST_USER);

        Cookie cookie = map.get(AUTHENTICATION_COOKIE);
        assertThat(cookie, not(nullValue()));
        assertThat(cookie.getDomain(), equalTo("." + TEST_COOKIE_DOMAIN));
    }

    @Test
    public void testSecureCookie() throws Exception
    {
        when(request.isSecure()).thenReturn(true);

        mocker.getComponentUnderTest().store(TEST_USER);

        Cookie cookie = map.get(AUTHENTICATION_COOKIE);
        assertThat(cookie, not(nullValue()));
        assertThat(cookie.getSecure(), is(true));
    }
}
