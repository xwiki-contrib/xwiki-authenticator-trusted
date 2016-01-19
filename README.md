**A generic XWiki authentication service based on pluggable adapters to provide trusted authentication from external
sources.**

Report any bug or suggest new feature in [JIRA TRUSTAUTH project](http://jira.xwiki.org/browse/TRUSTAUTH)

This extension bridge the old `XWikiAuthService` with the new component architecture of XWiki. It is oriented to 
ease the development of trusted authenticators by using configurable adapter components, that bridge the effective
trusted authentication with a generic authenticator. Out of the box, you get user creation, configurable user properties
synchronization and group membership synchronization.

Some default adapters will be provided over time, actually starting with a reimplementation of the headers
authenticator.

# Trusted authenticator API

The general behavior of the trusted authentication is:

  * If persistent store is trusted and not null, return the already authenticated user
  * `getUserId()` from the adapter:
    * if user is null, return with public access
    * else compute the user profile reference from `getUserName()`, replacing `.` by `=` and `@` by `_`
      * if user is found in persistence store, return that authenticated user
      * else, check user for existance:
        * if the user exists, synchronize user properties and group membership
        * else create user and synchronize group membership
        * stores the authenticated user to persistence store and returns it
           
Currently, it is mandatory that `getUserId()` and `getUserName()` returns the exact same value. In a
future version it is expected that only `getUserId()` should be unique, and `getUserName()` a more
meaningful value that may have duplicates, without causing confusions.

## General configuration

### xwiki.cfg file

    #-# Replace the default XWikiAuthService authentication
    xwiki.authentication.authclass=org.xwiki.contrib.authentication.XWikiTrustedAuthenticator

    #-# Define the hint of the TrustedAuthenticationAdapter that should be used for providing the effective
    #-# trusted authentication. This parameter is mandatory.
    #-# Here is an example for the HeadersTrustedAuthenticationAdapter:
    xwiki.authentication.trusted.adapterHint=headers
    
    #-# Define the hint of the AuthenticationPersistenceStore that will be used to persist authentication between
    #-# requests. The default is to use the SessionAuthenticationPersistenceStore, which will store authentication
    #-# information into the Servlet container session. 
    #-# Another option is to use the CookieAuthenticationPersistenceStore (hint: cookie), that will store the
    #-# information into an encrypted cookie. The cookie prefix, domain, path and encryption is customizable using the
    #-# same configuration as the standard authentication services (xwiki.authentication.cookieprefix, 
    #-# xwiki.authentication.cookiepath, xwiki.authentication.cookiedomains and xwiki.authentication.encryptionKey)
    # xwiki.authentication.trusted.persistenceStoreHint=session

    #-# By default the persistence store is not trusted, but only used to optimize the synchronization process.
    #-# If the authentication process is time consuming, you may improve performance by trusting the authentication
    #-# provided by the persistence store without requesting the external authentication, simply uncomment: 
    # xwiki.authentication.trusted.isPersistenceStoreTrusted=true;
   
    #-# Only used with the Cookie persistence store, allow setting the cookie Time To Live in seconde to keep 
    #-# persistence between browser restart. The default is to use a session cookie.
    #-# Here is an example using a 1 day TTL, which means the persistence is kept for 1 day after last response.
    #-# Combine with the above parameter, this could also keep the authentication for a longer period than
    #-# the one of the external authenticator, but this is obviously less secure.
    # xwiki.authentication.trusted.persistenceStoreTTL=84600;
 
    #-# By default, on failure to find an authenticated user, the authentication fallback (to a custom fallback or
    #-# the default XWiki authentication). To prevent fallbacking, and return public access on failure to find an
    #-# authenticated user, simply uncomment:
    # xwiki.authentication.trusted.isAuthoritative=true;
 
    #-# Only applicable if the previous parameter is true, this allow defining the classname of the
    #-# XWikiAuthService to fallback to. By the fault, the authenticator fallback to the default XWikiAuthService
    #-# implementation, and you should not uncomment the following with targetting another service, since it will
    #-# just have a negative performance impact.
    # xwiki.authentication.trusted.fallbackAuthenticator=com.xpn.xwiki.user.impl.xwiki.XWikiAuthServiceImpl

    #-# Mapping between XWiki group name and external authentication role name.
    #-# Mapping are separated with the pipe character, and the same XWiki group can be mapped multiple times to
    #-# different external roles.
    # xwiki.authentication.trusted.groupsMapping=XWiki.XWikiGroupA=groupA|XWiki.XWikiGroupB=groupB|XWiki.XWikiGroupA=groupAbis
    
    #-# Mapping between XWiki users property name and external user property names.
    # xwiki.authentication.trusted.propertiesMapping=email=mail,first_name=givenname,last_name=sn
    

### XWikiPreferences

While not recommended, it's also possible to put any of theses configuration in the `XWiki.XWikiPreferences` object 
in the `XWiki.XWikiPreferences` page of the main wiki. Add a string field with the proper name to the class and put 
the value you want.

The fields names are not exactly the same, you have to change `xwiki.authentication.trusted.` prefix to `trustedauth_`:

    xwiki.authentication.trusted.adapterHint -> trustedauth.adapterHint
    ...

For performance reason, most parameters are cached at startup, so changing those parameters in the preferences is not
sufficient, you also need to restart XWiki for them to be taken into account.

## Install

* copy this `xwiki-authenticator-trusted-api` jar file into `WEB_INF/lib/` (or install with EM for XWiki >6.1) 
* provide an authentication adapter component (or install one from another module in this repository)
* setup `xwiki.cfg`

## Troubleshoot

### Debug log

    <!-- Authentication debugging -->
    <logger name="org.xwiki.contrib.authentication" level="trace"/>

See [Logging in the Admnistration Guide](http://platform.xwiki.org/xwiki/bin/view/AdminGuide/Logging) for general
information about logging in XWiki.

# Adapters

## Headers

Provides XWiki authentication by trusting HTTP Headers and getting information about new users from those same headers.

This authenticator has the following specific behavior:

 * getUserId(): Check and verify the `secret_field` http header against the `secret_value`, and on success returns the
   value of the `auth_field` http header
 * getUserName(): returns the value of the `id_field` http header
 * getUserProperty(): returns the value of the http header having the given name
 * isUserInRole(): return true if the splitted array of the `group_field` http header by the `group_value_separator`
   contains the given name

### Specific configuration

    #-# Define the hint of the HeadersTrustedAuthenticationAdapter to be used for providing the effective
    #-# trusted authentication.
    xwiki.authentication.trusted.adapterHint=headers

    #-# Name of the header field used to check for the authentication of a user.
    #-# The content of this field should not be empty to have this authenticator to proceed, and it will be put
    #-# in the debugging log. But not real usage of this header value is done by the authenticator.
    #-# The default is to use the REMOTE_USER header.
    # xwiki.authentication.trusted.auth_field=remote_user

    #-# Name of the header field holding the UserID of the authenticated user.
    #-# This name will be used as the unique user name. It will be transformed in lowercase, and it will be
    #-# cleaned by replacing dots (.) by equal signs (=), and replacing at signs (@) by underscores (_).
    #-# For example John.Doe@example.com will became john=doe_example=com.
    #-# The default is to use the REMOTE_USER header.
    # xwiki.authentication.trusted.id_field=remote_user

    #-# Name of a header field containing a shared secret value.
    #-# While not mandatory, this field is hardly recommended to properly authenticate that headers has not be forged.
    #-# If not set, a warning will remind you in the log, since this is really a risky situation.
    # xwiki.authentication.trusted.secret_field=
    
    #-# The shared secred that should match the content of the shared secret header field.
    # xwiki.authentication.trusted.secret_value= (no default, only used when set)
    
    #-# Name of a header field holding the list of group the user is a member of.
    #-# If not configure, no group synchronization is provided.
    # xwiki.authentication.trusted.group_field=
    
    #-# A separator used to split the list of groups into group names.
    #-# Default to the pipe character.
    # xwiki.authentication.trusted.group_value_separator=|

## Install

* copy this `xwiki-authenticator-trusted-headers` jar file into `WEB_INF/lib/` (or install with EM for XWiki >6.1)
* setup `xwiki.cfg`
