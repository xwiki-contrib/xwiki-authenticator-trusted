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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReferenceSerializer;

import java.security.Principal;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.user.api.XWikiAuthService;
import com.xpn.xwiki.user.api.XWikiUser;
import com.xpn.xwiki.user.impl.xwiki.XWikiAuthServiceImpl;
import com.xpn.xwiki.web.Utils;

/**
 * Implementation of {@link XWikiAuthServiceImpl}.
 *
 * @version $Id$
 */
public class XWikiTrustedAuthenticator extends XWikiAuthServiceImpl
{
    private static final Logger LOG = LoggerFactory.getLogger(XWikiTrustedAuthenticator.class);

    private TrustedAuthenticator authenticator = Utils.getComponent(TrustedAuthenticator.class);

    private TrustedAuthenticationConfiguration configuration =
        Utils.getComponent(TrustedAuthenticationConfiguration.class);

    private EntityReferenceSerializer<String> compactWikiStringEntityReferenceSerializer =
        Utils.getComponent(EntityReferenceSerializer.TYPE_STRING, "compactwiki");

    /**
     * {@inheritDoc}
     *
     * @see com.xpn.xwiki.user.impl.xwiki.AppServerTrustedAuthServiceImpl#checkAuth(com.xpn.xwiki.XWikiContext)
     */
    @Override
    public XWikiUser checkAuth(XWikiContext context) throws XWikiException
    {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Checking trusted authentication...");
        }
        DocumentReference authenticatedUser = authenticator.authenticate();
        if (authenticatedUser != null) {
            LOG.debug("Successful trusted authentication for user [{}]", authenticatedUser);
            return new XWikiUser(compactWikiStringEntityReferenceSerializer.serialize(authenticatedUser));
        }

        if (configuration.isAuthoritative()) {
            LOG.debug("Trusted authenticator is authoritative, no trusted user found, ended with public access.");
            return null;
        }

        XWikiAuthService authService = configuration.getFallbackAuthenticator();
        if (authService != null) {
            LOG.debug("Falling back to [{}] checkAuth.", authService);
            return authService.checkAuth(context);
        }

        LOG.debug("Falling back to default XWiki checkAuth.");
        return super.checkAuth(context);
    }

    /**
     * {@inheritDoc}
     *
     * @see com.xpn.xwiki.user.impl.xwiki.AppServerTrustedAuthServiceImpl#checkAuth(java.lang.String, java.lang.String,
     *      java.lang.String, com.xpn.xwiki.XWikiContext)
     */
    @Override
    public XWikiUser checkAuth(String username, String password, String rememberme, XWikiContext context)
        throws XWikiException
    {
        DocumentReference authenticatedUser = authenticator.authenticate();
        if (authenticatedUser != null) {
            return new XWikiUser(compactWikiStringEntityReferenceSerializer.serialize(authenticatedUser));
        }

        if (configuration.isAuthoritative()) {
            return null;
        }

        XWikiAuthService authService = configuration.getFallbackAuthenticator();
        if (authService != null) {
            return authService.checkAuth(username, password, rememberme, context);
        }

        return super.checkAuth(username, password, rememberme, context);
    }

    /**
     * Override the authenticate function to implement fallback.
     */
    @Override
    public Principal authenticate(String userId, String password, XWikiContext context) throws XWikiException
    {
        XWikiAuthService authService = configuration.getFallbackAuthenticator();
        if (authService != null) {
            LOG.debug("Falling back to [{}] authenticate.", authService);
            return authService.authenticate(userId, password, context);
        }
        LOG.debug("Falling back to default XWiki authenticate.");
        return super.authenticate(userId, password, context);
    }
}
