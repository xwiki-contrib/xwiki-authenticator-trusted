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

import java.util.concurrent.ThreadLocalRandom;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.servlet.http.HttpSession;

import org.apache.commons.lang3.RandomStringUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.authentication.AuthenticationPersistenceStore;
import org.slf4j.Logger;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.web.XWikiRequest;

/**
 * Persistence store implementation that store userUID in session.
 *
 * @version $Id$
 */
@Component
@Singleton
@Named("session")
public class SessionAuthenticationPersistenceStore implements AuthenticationPersistenceStore
{
    /**
     * Use a random key to store user in session, making uneasy to temper it :).
     */
    private static final String USERNAME_SESSION_KEY =
        RandomStringUtils.randomAlphanumeric(ThreadLocalRandom.current().nextInt(10, 21));

    @Inject
    private Logger logger;

    @Inject
    private Provider<XWikiContext> contextProvider;

    /**
     * @return the current session (create a new one if needed), or null if request is not available.
     */
    private HttpSession getSession()
    {
        XWikiRequest request = contextProvider.get().getRequest();
        if (request == null) {
            return null;
        }
        return request.getSession(true);
    }

    @Override
    public void clear()
    {
        HttpSession session = getSession();
        if (session == null) {
            return;
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Clean user UID from session [{}]", session.getId());
        }
        session.removeAttribute(USERNAME_SESSION_KEY);
    }

    @Override
    public void store(String userUid)
    {
        HttpSession session = getSession();
        if (session == null) {
            if (logger.isDebugEnabled()) {
                logger.error("No session to store user UID [{}]", userUid);
            }
            return;
        }
        if (logger.isDebugEnabled()) {
            logger.debug("User UID [{}] associated to session [{}]", userUid, session.getId());
        }
        session.setAttribute(USERNAME_SESSION_KEY, userUid);
    }

    @Override
    public String retrieve()
    {
        HttpSession session = getSession();
        if (session == null) {
            logger.debug("Unable to retrieve user UID from session");
            return null;
        }
        String userUid = (String) session.getAttribute(USERNAME_SESSION_KEY);
        if (logger.isDebugEnabled()) {
            if (userUid != null) {
                logger.debug("User UID [{}] retrieved from session [{}]", userUid, session.getId());
            } else {
                logger.debug("No user UID found in session [{}]", session.getId());
            }
        }
        return userUid;
    }
}
