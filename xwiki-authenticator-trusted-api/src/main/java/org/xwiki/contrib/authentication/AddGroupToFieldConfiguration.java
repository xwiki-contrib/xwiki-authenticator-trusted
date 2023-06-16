package org.xwiki.contrib.authentication;

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

import org.xwiki.model.reference.DocumentReference;

/**
 * Add group to field configuration.
 *
 * @version $Id$
 * @since 1.7.0
 */
public interface AddGroupToFieldConfiguration
{
    /**
     * @return the page.
     */
    String getPage();

    /**
     * @return the class name.
     */
    String getClassName();

    /**
     * @return the class name.
     */
    String getObjectNumber();

    /**
     * @return the property name.
     */
    String getPropertyName();

    /**
     * @return the separator.
     */
    String getSeparator();

    /**
     * @return the value regular expression.
     */
    String getValueRegex();

    /**
     * @return the value from the group and the role. null if this failed.
     * @param group the group to build the value from.
     * @param role the role to build the value from.
     */
    String getValue(DocumentReference group, String role);
}
