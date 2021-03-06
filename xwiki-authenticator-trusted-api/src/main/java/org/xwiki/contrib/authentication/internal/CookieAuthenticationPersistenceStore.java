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

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.servlet.http.Cookie;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.phase.Initializable;
import org.xwiki.component.phase.InitializationException;
import org.xwiki.contrib.authentication.AuthenticationPersistenceStore;
import org.xwiki.contrib.authentication.TrustedAuthenticationConfiguration;
import org.slf4j.Logger;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.web.XWikiRequest;

/**
 * Persistence store implementation that store userUID in cookie.
 *
 * @version $Id$
 */
@Component
@Singleton
@Named("cookie")
public class CookieAuthenticationPersistenceStore implements AuthenticationPersistenceStore, Initializable
{
    private static final String AUTHENTICATION_CONFIG_PREFIX = "xwiki.authentication";

    private static final String COOKIE_PREFIX_PROPERTY = AUTHENTICATION_CONFIG_PREFIX + ".cookieprefix";
    private static final String COOKIE_PATH_PROPERTY = AUTHENTICATION_CONFIG_PREFIX + ".cookiepath";
    private static final String COOKIE_DOMAINS_PROPERTY = AUTHENTICATION_CONFIG_PREFIX + ".cookiedomains";
    private static final String ENCRYPTION_KEY_PROPERTY = AUTHENTICATION_CONFIG_PREFIX + ".encryptionKey";

    private static final String CIPHER_ALGORITHM = "TripleDES";

    private static final String AUTHENTICATION_COOKIE = "XWIKITRUSTEDAUTH";

    /**
     * The string used to prefix cookie domain to conform to RFC 2109.
     */
    private static final String COOKIE_DOT_PFX = ".";

    private static final String EQUAL_SIGN = "=";
    private static final String UNDERSCORE = "_";

    @Inject
    private Logger logger;

    @Inject
    private Provider<XWikiContext> contextProvider;

    @Inject
    private TrustedAuthenticationConfiguration config;

    private String cookiePfx;
    private String cookiePath;
    private String[] cookieDomains;
    private int cookieMaxAge;
    private Cipher encryptionCipher;
    private Cipher decryptionCipher;

    @Override
    public void initialize() throws InitializationException
    {
        XWikiContext context = contextProvider.get();
        cookiePfx = context.getWiki().Param(COOKIE_PREFIX_PROPERTY, "");
        cookiePath = context.getWiki().Param(COOKIE_PATH_PROPERTY, "/");

        String[] cdlist = StringUtils.split(context.getWiki().Param(COOKIE_DOMAINS_PROPERTY), ',');
        if (cdlist != null && cdlist.length > 0) {
            this.cookieDomains = new String[cdlist.length];
            for (int i = 0; i < cdlist.length; ++i) {
                cookieDomains[i] = conformCookieDomain(cdlist[i]);
            }
        } else {
            cookieDomains = null;
        }

        cookieMaxAge = config.getPersistenceTTL();

        try {
            encryptionCipher = getCipher(true);
            decryptionCipher = getCipher(false);
        } catch (Exception e) {
            throw new InitializationException("Unable to initialize ciphers", e);
        }
    }

    @Override
    public void clear()
    {
        setAuthenticationCookie(null, 0);
    }

    @Override
    public void store(String userUid)
    {
        setAuthenticationCookie(encryptText(userUid), cookieMaxAge);
    }

    /**
     * Set the authentication cookie to the given value and max age.
     * @param value the value to be set.
     * @param maxAge the maximum age of the cookie.
     */
    private void setAuthenticationCookie(String value, int maxAge)
    {
        XWikiContext context = contextProvider.get();

        Cookie cookie = new Cookie(cookiePfx + AUTHENTICATION_COOKIE, value);
        cookie.setMaxAge(maxAge);
        cookie.setPath(cookiePath);
        String cookieDomain = getCookieDomain();
        if (cookieDomain != null) {
            cookie.setDomain(cookieDomain);
        }
        if (context.getRequest().isSecure()) {
            cookie.setSecure(true);
        }
        context.getResponse().addCookie(cookie);
    }

    @Override
    public String retrieve()
    {
        String cookie = getAuthenticationCookieValue();
        if (cookie != null) {
            return decryptText(cookie);
        }
        return null;
    }

    /**
     * Retrieve the encrypted authentication cookie value.
     * @return the encrypted value found in the cookie if any, null otherwise.
     */
    private String getAuthenticationCookieValue()
    {
        XWikiRequest request = contextProvider.get().getRequest();
        if (request != null) {
            Cookie cookie = request.getCookie(cookiePfx + AUTHENTICATION_COOKIE);
            if (cookie != null) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private Cipher getCipher(boolean encrypt)
        throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException
    {
        Cipher cipher = null;
        String secretKey = contextProvider.get().getWiki().Param(ENCRYPTION_KEY_PROPERTY);
        if (secretKey != null) {
            secretKey = secretKey.substring(0, 24);
            SecretKeySpec key = new SecretKeySpec(secretKey.getBytes(), CIPHER_ALGORITHM);
            cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE, key);
        }
        return cipher;
    }

    private String encryptText(String text)
    {
        try {
            return new String(Base64.encodeBase64(encryptionCipher.doFinal(text.getBytes())))
                .replaceAll(EQUAL_SIGN, UNDERSCORE);
        } catch (Exception e) {
            logger.error("Failed to encrypt text", e);
        }
        return null;
    }

    private String decryptText(String text)
    {
        try {
            return new String(decryptionCipher.doFinal(
                Base64.decodeBase64(text.replaceAll(UNDERSCORE, EQUAL_SIGN).getBytes("ISO-8859-1"))));
        } catch (Exception e) {
            logger.error("Failed to decrypt text", e);
        }
        return null;
    }

    /**
     * Compute the actual domain the cookie is supposed to be set for. Search through the list of generalized domains
     * for a partial match. If no match is found, then no specific domain is used, which means that the cookie will be
     * valid only for the requested host.
     *
     * @return The configured domain generalization that matches the request, or null if no match is found.
     */
    private String getCookieDomain()
    {
        String cookieDomain = null;
        if (this.cookieDomains != null) {
            // Conform the server name like we conform cookie domain by prefixing with a dot.
            // This will ensure both localhost.localdomain and any.localhost.localdomain will match
            // the same cookie domain.
            String servername = conformCookieDomain(contextProvider.get().getRequest().getServerName());
            for (String domain : this.cookieDomains) {
                if (servername.endsWith(domain)) {
                    cookieDomain = domain;
                    break;
                }
            }
        }
        logger.debug("Cookie domain is:" + cookieDomain);
        return cookieDomain;
    }

    /**
     * Ensure cookie domains are prefixed with a dot to conform to RFC 2109.
     *
     * @param domain a cookie domain.
     * @return a conform cookie domain.
     */
    private String conformCookieDomain(String domain)
    {
        if (domain != null && !domain.startsWith(COOKIE_DOT_PFX)) {
            return COOKIE_DOT_PFX.concat(domain);
        } else {
            return domain;
        }
    }

}
