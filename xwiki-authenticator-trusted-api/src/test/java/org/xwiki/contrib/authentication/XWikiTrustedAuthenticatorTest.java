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

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.xwiki.model.internal.DefaultModelConfiguration;
import org.xwiki.model.internal.DefaultModelContext;
import org.xwiki.model.internal.reference.DefaultEntityReferenceValueProvider;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.test.LogRule;
import org.xwiki.test.annotation.ComponentList;

import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.internal.model.reference.CompactWikiStringEntityReferenceSerializer;
import com.xpn.xwiki.internal.model.reference.CurrentEntityReferenceValueProvider;
import com.xpn.xwiki.internal.model.reference.CurrentMixedEntityReferenceValueProvider;
import com.xpn.xwiki.internal.model.reference.CurrentMixedStringDocumentReferenceResolver;
import com.xpn.xwiki.internal.model.reference.CurrentStringDocumentReferenceResolver;
import com.xpn.xwiki.internal.model.reference.CurrentStringEntityReferenceResolver;
import com.xpn.xwiki.test.MockitoOldcoreRule;
import com.xpn.xwiki.user.api.XWikiAuthService;
import com.xpn.xwiki.user.api.XWikiUser;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ComponentList({
    DefaultModelConfiguration.class,
    DefaultModelContext.class,
    CurrentEntityReferenceValueProvider.class,
    CurrentStringEntityReferenceResolver.class,
    CurrentStringDocumentReferenceResolver.class,
    CompactWikiStringEntityReferenceSerializer.class,
    CurrentMixedStringDocumentReferenceResolver.class,
    CurrentMixedEntityReferenceValueProvider.class,
    DefaultEntityReferenceValueProvider.class
    //,DefaultLoggerManager.class,
    //DefaultObservationManager.class
})
public class XWikiTrustedAuthenticatorTest
{
    private static final String USER_WIKI = "xwiki";
    private static final String USER_SPACE = "XWiki";

    private static final String TEST_USER = "test.user@example.com";
    private static final String TEST_PASSWORD = "password";
    private static final String TEST_REMEMBERME = "yes";
    private static final String VALID_TEST_USER = "test=user_example=com";
    private static final String TEST_USER_FN = USER_WIKI + ':' + USER_SPACE + '.' + VALID_TEST_USER;
    private static final DocumentReference TEST_USER_REF = new DocumentReference(USER_WIKI, USER_SPACE, VALID_TEST_USER);

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

    private XWikiAuthService authenticator;

    private TrustedAuthenticator trustedAuthenticator;

    private TrustedAuthenticationConfiguration configuration;

    private Map<String, String> config = new HashMap<>();

    @Before
    public void before() throws Exception
    {
        //LoggerManager loggerManager = oldcore.getMocker().getInstance(LoggerManager.class);
        //loggerManager.setLoggerLevel("org.xwiki", LogLevel.DEBUG);

        trustedAuthenticator = mock(TrustedAuthenticator.class);
        oldcore.getMocker().registerComponent(TrustedAuthenticator.class, trustedAuthenticator);
        configuration = mock(TrustedAuthenticationConfiguration.class);
        oldcore.getMocker().registerComponent(TrustedAuthenticationConfiguration.class, configuration);

        authenticator = new XWikiTrustedAuthenticator();
        this.oldcore.getXWikiContext().setWikiId("wiki");
        when(this.oldcore.getMockXWiki().getAuthService()).thenReturn(authenticator);
    }

    @Test
    public void testSuccessfullTrustedAuthentication() throws XWikiException
    {
        when(trustedAuthenticator.authenticate()).thenReturn(TEST_USER_REF);
        assertThat(oldcore.getMockXWiki().getAuthService().checkAuth(oldcore.getXWikiContext()),
            equalTo(new XWikiUser(TEST_USER_FN)));

        when(trustedAuthenticator.authenticate()).thenReturn(TEST_USER_REF);
        assertThat(oldcore.getMockXWiki().getAuthService()
            .checkAuth(TEST_USER, TEST_PASSWORD, TEST_REMEMBERME, oldcore.getXWikiContext()),
            equalTo(new XWikiUser(TEST_USER_FN)));
    }

    @Test
    public void testFailedAuthoritativeTrustedAuthentication() throws XWikiException
    {
        when(configuration.isAuthoritative()).thenReturn(true);
        assertThat(oldcore.getMockXWiki().getAuthService().checkAuth(oldcore.getXWikiContext()), nullValue());

        when(configuration.isAuthoritative()).thenReturn(true);
        assertThat(oldcore.getMockXWiki().getAuthService()
            .checkAuth(TEST_USER, TEST_PASSWORD, TEST_REMEMBERME, oldcore.getXWikiContext()), nullValue());
    }

    @Test
    public void testFallbackToAnotherAuthService() throws XWikiException
    {
        XWikiAuthService authService = mock(XWikiAuthService.class);

        when(configuration.getFallbackAuthenticator()).thenReturn(authService);
        oldcore.getMockXWiki().getAuthService().checkAuth(oldcore.getXWikiContext());
        verify(authService, times(1)).checkAuth(oldcore.getXWikiContext());

        oldcore.getMockXWiki().getAuthService()
            .checkAuth(TEST_USER, TEST_PASSWORD, TEST_REMEMBERME, oldcore.getXWikiContext());
        verify(authService, times(1)).checkAuth(TEST_USER, TEST_PASSWORD, TEST_REMEMBERME, oldcore.getXWikiContext());
    }

    @Test
    public void testFallbackToDefaultXWikiAuthentication() throws Exception
    {
        try {
            oldcore.getMockXWiki().getAuthService().checkAuth(oldcore.getXWikiContext());
        } catch (XWikiException e) {
            // Fallback to normal auth cause exception since the authenticator could not be initialized.
            assertThat(e.getModule(), equalTo(XWikiException.MODULE_XWIKI_USER));
            assertThat(e.getCode(), equalTo(XWikiException.ERROR_XWIKI_USER_INIT));
        }

        try {
            oldcore.getMockXWiki().getAuthService()
                .checkAuth(TEST_USER, TEST_PASSWORD, TEST_REMEMBERME, oldcore.getXWikiContext());
        } catch (XWikiException e) {
            // Fallback to normal auth cause exception since the authenticator could not be initialized.
            assertThat(e.getModule(), equalTo(XWikiException.MODULE_XWIKI_USER));
            assertThat(e.getCode(), equalTo(XWikiException.ERROR_XWIKI_USER_INIT));
        }
    }
}
