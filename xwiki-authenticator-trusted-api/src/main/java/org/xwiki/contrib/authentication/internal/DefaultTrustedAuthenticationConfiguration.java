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

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.contrib.authentication.AddGroupToFieldConfiguration;
import org.xwiki.contrib.authentication.AuthenticationPersistenceStore;
import org.xwiki.contrib.authentication.TrustedAuthenticationAdapter;
import org.xwiki.contrib.authentication.DynamicRoleConfiguration;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.user.api.XWikiAuthService;

/**
 * Default implementation of {@link org.xwiki.contrib.authentication.TrustedAuthenticationConfiguration}.
 *
 * @version $Id$
 */
@Component
@Singleton
public class DefaultTrustedAuthenticationConfiguration extends AbstractConfig
{
    private static final String PREF_PREFIX = "trustedauth";
    private static final String CONF_PREFIX = "xwiki.authentication.trusted";

    private static final String AUTHENTICATION_ADAPTER_HINT_PROPERTY = "adapterHint";
    private static final String AUTHENTICATION_ADAPTER_HINT_DEFAULT = "default";

    private static final String PERSISTANCE_STORE_HINT_PROPERTY = "persistenceStoreHint";
    private static final String PERSISTANCE_STORE_HINT_DEFAULT = "session";

    private static final String PERSISTANCE_STORE_TRUSTED_PROPERTY = "isPersistenceStoreTrusted";
    private static final boolean PERSISTANCE_STORE_TRUSTED_DEFAULT = false;

    private static final String PERSISTANCE_STORE_TRUSTED_ON_MISSING_AUTH_PROPERTY =
        "isPersistenceStoreTrustedOnMissingAuthentication";
    private static final boolean PERSISTANCE_STORE_TRUSTED_ON_MISSING_AUTH_DEFAULT = false;

    private static final String PERSISTANCE_STORE_TTL_PROPERTY = "persistenceStoreTTL";
    private static final int    PERSISTANCE_STORE_TTL_DEFAULT = -1;

    private static final String AUTHORITATIVE_PROPERTY = "isAuthoritative";
    private static final boolean AUTHORITATIVE_DEFAULT = false;

    private static final String FALLBACK_AUTH_PROPERTY = "fallbackAuthenticator";

    private static final String USERPROFILE_CASE_PROPERTY = "userProfileCase";
    private static final CaseStyle USERPROFILE_CASE_DEFAULT = CaseStyle.LOWERCASE;

    private static final String USERPROFILE_REPLACEMENTS_PROPERTY = "userProfileReplacements";
    private static final char USERPROFILE_REPLACEMENTS_SEP = '|';

    private static final String GROUP_MAPPING_PROPERTY = "groupsMapping";
    private static final char GROUP_MAPPING_SEP = '|';

    private static final String PROPERTY_MAPPING_PROPERTY = "propertiesMapping";
    private static final char PROPERTY_MAPPING_SEP = '|';

    private static final String ADD_GROUP_TO_FIELD_PREFIX = "addGroupToField.";
    private static final String DYNAMIC_ROLE_PROPERTY = "dynamicRole";
    private static final String DYNAMIC_ROLE_CONFIGURATIONS_PROPERTY = DYNAMIC_ROLE_PROPERTY + ".configurations";
    private static final String DYNAMIC_ROLE_CONFIGURATION_PREFIX = DYNAMIC_ROLE_PROPERTY + ".configuration.";
    private static final String LOGOUTPAGE_CONFIG_KEY = "xwiki.authentication.logoutpage";

    @Inject
    private ComponentManager componentManager;

    private Collection<DynamicRoleConfiguration> dynamicRoleConfigurations;

    /**
     * Default constructor.
     */
    public DefaultTrustedAuthenticationConfiguration()
    {
        super(PREF_PREFIX, CONF_PREFIX);
    }

    @Override
    public TrustedAuthenticationAdapter getAuthenticationAdapter()
    {
        String authAdapterHint =
            getCustomProperty(AUTHENTICATION_ADAPTER_HINT_PROPERTY, AUTHENTICATION_ADAPTER_HINT_DEFAULT);
        try {
            return componentManager.getInstance(TrustedAuthenticationAdapter.class, authAdapterHint);
        } catch (ComponentLookupException e) {
            logger.error("Failed to load authentication adapter [" + authAdapterHint + "]", e);
        }
        return null;
    }

    @Override
    public AuthenticationPersistenceStore getPersistenceStore()
    {
        String persistenceStoreHint =
            getCustomProperty(PERSISTANCE_STORE_HINT_PROPERTY, PERSISTANCE_STORE_HINT_DEFAULT);
        try {
            return componentManager.getInstance(AuthenticationPersistenceStore.class, persistenceStoreHint);
        } catch (ComponentLookupException e) {
            logger.error("Failed to load persistence store [" + persistenceStoreHint + "]", e);
        }
        return null;
    }

    @Override
    public boolean isPersistenceStoreTrusted()
    {
        return getCustomPropertyAsBoolean(PERSISTANCE_STORE_TRUSTED_PROPERTY, PERSISTANCE_STORE_TRUSTED_DEFAULT);
    }

    @Override
    public boolean isPersistenceStoreTrustedOnMissingAuthentication()
    {
        return getCustomPropertyAsBoolean(PERSISTANCE_STORE_TRUSTED_ON_MISSING_AUTH_PROPERTY,
            PERSISTANCE_STORE_TRUSTED_ON_MISSING_AUTH_DEFAULT);
    }

    @Override
    public int getPersistenceTTL()
    {
        String ttl = getCustomProperty(PERSISTANCE_STORE_TTL_PROPERTY, null);
        if (ttl != null) {
            try {
                return Integer.parseInt(ttl);
            } catch (Exception e) {
                // ignored, use default
            }
        }
        return PERSISTANCE_STORE_TTL_DEFAULT;
    }

    @Override
    public CaseStyle getUserProfileCaseStyle()
    {
        try {
            return CaseStyle.valueOf(
                StringUtils.upperCase(StringUtils.trim(getCustomProperty(USERPROFILE_CASE_PROPERTY, null))));
        } catch (Exception e) {
            // ignored, use default
        }
        return USERPROFILE_CASE_DEFAULT;
    }

    @Override
    public Map<String, String> getUserProfileReplacements()
    {
        return getCustomPropertyAsMap(USERPROFILE_REPLACEMENTS_PROPERTY, USERPROFILE_REPLACEMENTS_SEP,
            Collections.<String, String>emptyMap(), false);
    }

    @Override
    public Map<String, String> getUserPropertyMappings()
    {
        return getCustomPropertyAsMap(PROPERTY_MAPPING_PROPERTY, PROPERTY_MAPPING_SEP,
            Collections.<String, String>emptyMap(), false);
    }

    @Override
    public Map<String, Collection<String>> getGroupMappings()
    {
        return getCustomPropertyAsMapOfSet(GROUP_MAPPING_PROPERTY, GROUP_MAPPING_SEP,
            Collections.<String, Collection<String>>emptyMap(), true);
    }

    @Override
    public boolean isAuthoritative()
    {
        return getCustomPropertyAsBoolean(AUTHORITATIVE_PROPERTY, AUTHORITATIVE_DEFAULT);
    }

    @Override
    public XWikiAuthService getFallbackAuthenticator()
    {
        String authenticatorClassName = getCustomProperty(FALLBACK_AUTH_PROPERTY, null);
        XWikiAuthService authenticator = null;

        if (authenticatorClassName != null) {
            try {
                authenticator = (XWikiAuthService) Class.forName(authenticatorClassName).newInstance();
            } catch (Exception e) {
                logger.error("Failed to get fallback authenticator [" + authenticatorClassName + "]", e);
            }
        }

        return authenticator;
    }

    @Override
    public String getLogoutPagePattern()
    {
        XWikiContext context = contextProvider.get();
        String logoutPage = null;

        try {
            logoutPage = context.getWiki().Param(LOGOUTPAGE_CONFIG_KEY);
        } catch (Exception e) {
            logger.error("Failed to get logout page config [{}]", LOGOUTPAGE_CONFIG_KEY, e);
        }

        if (logoutPage == null) {
            logoutPage = stripContextPathFromURL(
                context.getURLFactory().createURL("XWiki", "XWikiLogout", "logout", context));
        }

        return logoutPage;
    }

    private String stripContextPathFromURL(URL url)
    {
        XWikiContext context = contextProvider.get();
        return StringUtils.removeStart(url.toExternalForm(),
            url.getProtocol() + "://" + url.getAuthority() + context.getWiki().getWebAppPath(context));
    }

    @Override
    public Collection<DynamicRoleConfiguration> getDynamicRoleConfigurations()
    {
        if (dynamicRoleConfigurations == null) {
            String[] configurationNames = getCustomProperty(DYNAMIC_ROLE_CONFIGURATIONS_PROPERTY, "").split("\\|");
            dynamicRoleConfigurations = new ArrayList<DynamicRoleConfiguration>(configurationNames.length);
            AddGroupToFieldConfiguration atgConf = DefaultAddGroupToFieldConfiguration.parse(this,
                ADD_GROUP_TO_FIELD_PREFIX);

            for (String name : configurationNames) {
                String prefix = DYNAMIC_ROLE_CONFIGURATION_PREFIX + name + ".";
                DynamicRoleConfiguration conf = new DefaultDynamicRoleConfiguration(this, prefix, name, atgConf);
                if (conf.getGroupPrefix().isEmpty() && conf.getGroupSuffix().isEmpty()) {
                    // Allowing configurations without group prefix or suffix would be dangerous
                    // as it would match any group the user is in
                    logger.error("Dynamic role configuration [{}] doesn't specify any group prefix or suffix. "
                        + "To be safe, access will be denied until the configuration is fixed.");
                    return null;
                }
                dynamicRoleConfigurations.add(conf);
            }
        }
        return dynamicRoleConfigurations;
    }
}
