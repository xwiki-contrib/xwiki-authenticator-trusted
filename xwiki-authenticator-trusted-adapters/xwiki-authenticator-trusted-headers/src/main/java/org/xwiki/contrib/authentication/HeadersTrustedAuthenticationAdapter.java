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

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.web.XWikiRequest;

/**
 * Implementation of the {@link TrustedAuthenticationAdapter} for header authentication.
 *
 * @version $Id$
 */
@Component
@Singleton
@Named("headers")
public class HeadersTrustedAuthenticationAdapter implements TrustedAuthenticationAdapter
{
    // Configuration
    private static final String CONFIG_AUTH_FIELD = "auth_field";
    private static final String CONFIG_ID_FIELD = "id_field";
    private static final String CONFIG_SECRET_FIELD = "secret_field";
    private static final String CONFIG_SECRET_VALUE = "secret_value";
    private static final String CONFIG_GROUP_FIELD = "group_field";
    private static final String CONFIG_GROUP_VALUE_SEPARATOR = "group_value_separator";
    private static final String CONFIG_LOGOUT_URL = "logout_url";

    // Default values for configuration
    private static final String DEFAULT_AUTH_FIELD = "remote_user";
    private static final String DEFAULT_ID_FIELD = DEFAULT_AUTH_FIELD;
    private static final String DEFAULT_GROUP_VALUE_SEPARATOR = "\\|";

    private static final String LOGOUT_URL_REDIRECTION_PLACEHOLDER = "__REDIRECT__";

    @Inject
    private Logger logger;

    @Inject
    private TrustedAuthenticationConfiguration configuration;

    @Inject
    private Provider<XWikiContext> contextProvider;

    @Override
    public String getUserUid()
    {
        String secretField = configuration.getCustomProperty(CONFIG_SECRET_FIELD, null);

        // Validate shared secret
        if (!StringUtils.isEmpty(secretField)) {
            String headerSecretValue = getHeader(secretField);

            if (headerSecretValue == null
                || !headerSecretValue.equals(configuration.getCustomProperty(CONFIG_SECRET_VALUE, null))) {
                logger.debug("Received invalid value [{}] for secret header [{}], falling back.",
                    headerSecretValue, secretField);
                return null;
            }
            logger.debug("Secret validation succeeded.");
        }

        return getHeader(configuration.getCustomProperty(CONFIG_AUTH_FIELD, DEFAULT_AUTH_FIELD));
    }

    @Override
    public String getUserName()
    {
        return getHeader(configuration.getCustomProperty(CONFIG_ID_FIELD, DEFAULT_ID_FIELD));
    }

    @Override
    public String getUserProperty(String name)
    {
        return getHeader(name);
    }

    @Override
    public boolean isUserInRole(String role)
    {
        return getGroupFieldHeaderValue().contains(role);
    }

    /**
     * @param name the name of the header.
     * @return the value of the named request header, or null if no value is defined.
     */
    private String getHeader(String name)
    {
        if (StringUtils.isBlank(name)) {
            return null;
        }

        XWikiRequest request = contextProvider.get().getRequest();
        if (request == null) {
            return null;
        }
        return request.getHeader(name);
    }

    private List<String> getGroupFieldHeaderValue()
    {
        String groupFieldName = configuration.getCustomProperty(CONFIG_GROUP_FIELD, null);
        String headerValue = null;
        if (groupFieldName != null) {
            headerValue = getHeader(groupFieldName);
        }
        if (StringUtils.isBlank(headerValue)) {
            return Collections.<String>emptyList();
        }

        return Arrays.asList(headerValue.split(configuration.getCustomProperty(CONFIG_GROUP_VALUE_SEPARATOR,
            DEFAULT_GROUP_VALUE_SEPARATOR)));
    }

    @Override
    public String getLogoutURL(String location)
    {
        String logoutUrl = configuration.getCustomProperty(CONFIG_LOGOUT_URL, null);

        if (StringUtils.isBlank(logoutUrl)) {
            return null;
        }

        if (location != null) {
            logoutUrl = logoutUrl.replace(LOGOUT_URL_REDIRECTION_PLACEHOLDER, urlEncode(location));
        }

        return logoutUrl;
    }

    private String urlEncode(String text) {
        try {
            return URLEncoder.encode(text, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            // Very unlikely to happen
            return text;
        }
    }
}
