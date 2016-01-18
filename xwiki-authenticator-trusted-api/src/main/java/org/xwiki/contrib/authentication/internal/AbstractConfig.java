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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Provider;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.xwiki.contrib.authentication.TrustedAuthenticationConfiguration;
import org.slf4j.Logger;

import com.xpn.xwiki.XWikiContext;

/**
 * Abstract component providing generic helper methods for accessing authenticator configuration.
 * Still use xwiki.cfg to retrieve config, since authenticator class is still configured there.
 * Don't use the new ConfigurationSource, so it keep compatibility with versions older than 6.1
 *
 * @version $Id$
 */
public abstract class AbstractConfig implements TrustedAuthenticationConfiguration
{
    @Inject
    protected Logger logger;

    @Inject
    protected Provider<XWikiContext> contextProvider;

    private final String prefPrefix;
    private final String confPrefix;

    public AbstractConfig(String prefPrefix, String confPrefix)
    {
        this.prefPrefix = prefPrefix + '_';
        this.confPrefix = confPrefix + '.';
    }

    public boolean getCustomPropertyAsBoolean(String name, boolean def)
    {
        String param = getCustomProperty(name, null);
        if (param != null) {
            return BooleanUtils.toBoolean(param);
        }
        return def;
    }

    public String getCustomProperty(String name, String def)
    {
        XWikiContext context = contextProvider.get();
        String param = null;

        try {
            param = context.getWiki().getXWikiPreference(prefPrefix + name, context);
        } catch (Exception e) {
            logger.error("Failed to get preference [{}]", this.prefPrefix + name, e);
        }

        if (StringUtils.isEmpty(param)) {
            try {
                param = context.getWiki().Param(confPrefix + name);
            } catch (Exception e) {
                logger.error("Failed to get config [{}]", this.prefPrefix + name, e);
            }
        }

        if (param == null) {
            param = def;
        }

        logger.debug("Param [{}]: {}", name, param);

        return param;
    }

    public List<String> getCustomPropertyAsList(String name, char separator, List<String> def)
    {
        List<String> list = def;

        String str = getCustomProperty(name, null);

        if (str != null) {
            if (!StringUtils.isEmpty(str)) {
                list = splitParam(str, separator);
            } else {
                list = Collections.emptyList();
            }
        }

        return list;
    }

    public Set<String> getCustomPropertyAsSet(String name, char separator, Set<String> def)
    {
        Set<String> set = def;

        String str = getCustomProperty(name, null);

        if (str != null) {
            if (!StringUtils.isEmpty(str)) {
                set = new HashSet<String>(Arrays.asList(StringUtils.split(str, separator)));
            } else {
                set = Collections.emptySet();
            }
        }

        return set;
    }

    public Map<String, String> getCustomPropertyAsMap(String name, char separator, Map<String, String> def,
        boolean forceLowerCaseKey)
    {
        Map<String, String> mappings = def;

        List<String> list = getCustomPropertyAsList(name, separator, null);

        if (list != null) {
            if (list.isEmpty()) {
                mappings = Collections.emptyMap();
            } else {
                mappings = new LinkedHashMap<String, String>();

                for (String fieldStr : list) {
                    int index = fieldStr.indexOf('=');
                    if (index != -1) {
                        String key = fieldStr.substring(0, index);
                        String value = index + 1 == fieldStr.length() ? "" : fieldStr.substring(index + 1);

                        mappings.put(forceLowerCaseKey ? key.toLowerCase() : key, value);
                    } else {
                        logger.warn("Error parsing [{}] attribute in xwiki.cfg: {}", name, fieldStr);
                    }
                }
            }
        }

        return mappings;
    }

    public Map<String, Collection<String>> getCustomPropertyAsMapOfSet(String name, char separator,
        Map<String, Collection<String>> def, boolean left)
    {
        Map<String, Collection<String>> oneToMany = def;

        List<String> list = getCustomPropertyAsList(name, separator, null);

        if (list != null) {
            if (list.isEmpty()) {
                oneToMany = Collections.emptyMap();
            } else {
                oneToMany = new LinkedHashMap<String, Collection<String>>();

                for (String mapping : list) {
                    int splitIndex = mapping.indexOf('=');

                    if (splitIndex < 1) {
                        logger.error("Error parsing [{}] attribute: {}", name, mapping);
                    } else {
                        String leftProperty =
                            left ? mapping.substring(0, splitIndex) : mapping.substring(splitIndex + 1);
                        String rightProperty =
                            left ? mapping.substring(splitIndex + 1) : mapping.substring(0, splitIndex);

                        Collection<String> rightCollection = oneToMany.get(leftProperty);

                        if (rightCollection == null) {
                            rightCollection = new HashSet<String>();
                            oneToMany.put(leftProperty, rightCollection);
                        }

                        rightCollection.add(rightProperty);

                        logger.debug("[{}] mapping found: {}", name, leftProperty + " " + rightCollection);
                    }
                }
            }
        }

        return oneToMany;
    }

    private List<String> splitParam(String text, char delimiter)
    {
        List<String> tokens = new ArrayList<String>();
        boolean escaped = false;
        StringBuilder sb = new StringBuilder();

        for (char ch : text.toCharArray()) {
            if (escaped) {
                sb.append(ch);
                escaped = false;
            } else if (ch == delimiter) {
                if (sb.length() > 0) {
                    tokens.add(sb.toString());
                    sb.delete(0, sb.length());
                }
            } else if (ch == '\\') {
                escaped = true;
            } else {
                sb.append(ch);
            }
        }

        if (sb.length() > 0) {
            tokens.add(sb.toString());
        }

        return tokens;
    }
}
