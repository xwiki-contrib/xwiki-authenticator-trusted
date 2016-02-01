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

import org.junit.Test;

import com.xpn.xwiki.web.XWikiRequest;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RequestMatcherTest
{
    @Test
    public void testDefaultLogoutPagePatternMatching() throws Exception
    {
        RequestMatcher matcher = new RequestMatcher("(/|/[^/]+/|/wiki/[^/]+/)logout/*");
        XWikiRequest request = mock(XWikiRequest.class);

        when(request.getServletPath()).thenReturn("/bin");
        when(request.getPathInfo()).thenReturn("/view/Main/");
        assertThat(matcher.match(request), is(false));

        when(request.getServletPath()).thenReturn("/bin");
        when(request.getPathInfo()).thenReturn("/logout/Main/");
        assertThat(matcher.match(request), is(true));

        when(request.getServletPath()).thenReturn("");
        when(request.getPathInfo()).thenReturn("/logout/Main/");
        assertThat(matcher.match(request), is(true));

        when(request.getServletPath()).thenReturn("/wiki");
        when(request.getPathInfo()).thenReturn("/wikiname/logout/Main/");
        assertThat(matcher.match(request), is(true));
    }
}
