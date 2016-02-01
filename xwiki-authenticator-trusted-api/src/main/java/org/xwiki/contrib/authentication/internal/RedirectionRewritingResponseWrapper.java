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

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.web.XWikiServletResponse;
import com.xpn.xwiki.web.XWikiServletURLFactory;

/**
 * XWiki servlet response allowing rewriting redirection URL.
 * This wrapper is limited, it does not fully support relative path redirection.
 * Absolute path redirection is prepended with the server URL to became full url before being rewritten by calling the
 * {@link URLRewriter}.
 *
 * @version $Id$
 */
public class RedirectionRewritingResponseWrapper extends XWikiServletResponse
{
    private static final Logger LOGGER = LoggerFactory.getLogger(RedirectionRewritingResponseWrapper.class);

    /**
     * Interface for URL rewriting.
     */
    public interface URLRewriter
    {
        /**
         * Rewrite the provided URL.
         *
         * @param location the URL to be rewritten.
         * @return the rewritten URL.
         */
        String rewrite(String location);
    }

    private final URLRewriter rewriter;
    private final String serverURL;

    /**
     * Construct a new wrapper.
     *
     * @param rewriter a URL rewriter that will be used to convert redirection URL.
     * @param context current XWiki context used to convert received redirection path into a full URL.
     */
    public RedirectionRewritingResponseWrapper(URLRewriter rewriter, XWikiContext context)
    {
        super(context.getResponse());
        this.rewriter = rewriter;
        this.serverURL = getServerURL(context);
    }

    @Override
    public void sendRedirect(String location) throws IOException
    {
        String redirect;

        if (location.startsWith("/")) {
            redirect = serverURL + location;
        } else {
            redirect = location;
        }

        redirect = rewriter.rewrite(redirect);
        LOGGER.debug("Redirection to [{}] rewritten and redirected to [{}]", redirect, location);
        super.sendRedirect(encodeRedirectURL(redirect));
    }

    /**
     * @return the server URL for the current context.
     */
    private static String getServerURL(XWikiContext context)
    {
        XWikiServletURLFactory urlFactory = new XWikiServletURLFactory(context);
        try {
            return urlFactory.getServerURL(context).toExternalForm();
        } catch (Exception e) {
            return "";
        }
    }
}
