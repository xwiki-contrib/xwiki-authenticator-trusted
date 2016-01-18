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
import javax.servlet.http.HttpSession;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.xwiki.component.util.DefaultParameterizedType;
import org.xwiki.contrib.authentication.AuthenticationPersistenceStore;
import org.xwiki.test.mockito.MockitoComponentMockingRule;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.web.XWikiRequest;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SessionAuthenticationPersistenceStoreTest
{
    private static final String TEST_USER = "test.user@example.com";

    @Rule
    public final MockitoComponentMockingRule<AuthenticationPersistenceStore> mocker =
        new MockitoComponentMockingRule(SessionAuthenticationPersistenceStore.class);

    private XWikiContext context;

    private XWikiRequest request;

    private HttpSession session;

    private Map<String, Object> map = new HashMap<String, Object>();

    @Before
    public void before() throws Exception
    {
        Provider<XWikiContext> contextProvider =
            mocker.getInstance(new DefaultParameterizedType(null, Provider.class, XWikiContext.class));
        context = mock(XWikiContext.class);
        request = mock(XWikiRequest.class);
        session = mock(HttpSession.class);

        when(contextProvider.get()).thenReturn(context);
        when(context.getRequest()).thenReturn(request);
        when(request.getSession(true)).thenReturn(session);

        doAnswer(new Answer<Object>() {
            public Object answer(InvocationOnMock invocationOnMock) {
                String key = (String) invocationOnMock.getArguments()[0];
                Object value = invocationOnMock.getArguments()[1];
                map.put(key, value);
                return null;
            }
        }).when(session).setAttribute(any(String.class), any(Object.class));

        when(session.getAttribute(any(String.class))).then(
            new Answer<Object>()
            {
                @Override
                public Object answer(InvocationOnMock invocationOnMock) throws Throwable
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
        }).when(session).removeAttribute(any(String.class));
    }

    @Test
    public void testStoreRetrieveAndClear() throws Exception
    {
        mocker.getComponentUnderTest().store(TEST_USER);
        assertThat(mocker.getComponentUnderTest().retrieve(), equalTo(TEST_USER));

        mocker.getComponentUnderTest().clear();
        assertThat(mocker.getComponentUnderTest().retrieve(), nullValue());
    }
}
