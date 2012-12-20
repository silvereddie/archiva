package org.apache.archiva.admin.repository.runtime;
/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import net.sf.beanlib.provider.replicator.BeanReplicator;
import org.apache.archiva.admin.model.RepositoryAdminException;
import org.apache.archiva.admin.model.beans.LdapConfiguration;
import org.apache.archiva.admin.model.beans.RedbackRuntimeConfiguration;
import org.apache.archiva.admin.model.runtime.ArchivaRuntimeConfigurationAdmin;
import org.apache.archiva.configuration.ArchivaConfiguration;
import org.apache.archiva.configuration.Configuration;
import org.apache.archiva.configuration.IndeterminateConfigurationException;
import org.apache.archiva.redback.components.registry.RegistryException;
import org.apache.archiva.redback.configuration.UserConfiguration;
import org.apache.archiva.redback.configuration.UserConfigurationException;
import org.apache.archiva.redback.configuration.UserConfigurationKeys;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;

/**
 * @author Olivier Lamy
 * @since 1.4-M4
 */
@Service( "userConfiguration#archiva" )
public class DefaultArchivaRuntimeConfigurationAdmin
    implements ArchivaRuntimeConfigurationAdmin, UserConfiguration
{

    protected Logger log = LoggerFactory.getLogger( getClass() );

    @Inject
    private ArchivaConfiguration archivaConfiguration;

    @Inject
    @Named( value = "userConfiguration#redback" )
    UserConfiguration userConfiguration;

    @PostConstruct
    public void initialize()
        throws UserConfigurationException
    {
        try
        {
            RedbackRuntimeConfiguration redbackRuntimeConfiguration = getArchivaRuntimeConfiguration();
            // migrate or not data from redback
            if ( !redbackRuntimeConfiguration.isMigratedFromRedbackConfiguration() )
            {
                // so migrate if available
                String userManagerImpl = userConfiguration.getString( UserConfigurationKeys.USER_MANAGER_IMPL );
                if ( StringUtils.isNotEmpty( userManagerImpl ) )
                {
                    redbackRuntimeConfiguration.getUserManagerImpls().add( userManagerImpl );
                }

                // now ldap

                LdapConfiguration ldapConfiguration = redbackRuntimeConfiguration.getLdapConfiguration();
                if ( ldapConfiguration == null )
                {
                    ldapConfiguration = new LdapConfiguration();
                    redbackRuntimeConfiguration.setLdapConfiguration( ldapConfiguration );
                }

                ldapConfiguration.setHostName(
                    userConfiguration.getString( UserConfigurationKeys.LDAP_HOSTNAME, null ) );
                ldapConfiguration.setPort( userConfiguration.getInt( UserConfigurationKeys.LDAP_PORT, -1 ) );
                ldapConfiguration.setSsl( userConfiguration.getBoolean( UserConfigurationKeys.LDAP_SSL, false ) );
                ldapConfiguration.setBaseDn(
                    userConfiguration.getConcatenatedList( UserConfigurationKeys.LDAP_BASEDN, null ) );
                ldapConfiguration.setContextFactory(
                    userConfiguration.getString( UserConfigurationKeys.LDAP_CONTEX_FACTORY, null ) );
                ldapConfiguration.setBindDn(
                    userConfiguration.getConcatenatedList( UserConfigurationKeys.LDAP_BINDDN, null ) );
                ldapConfiguration.setPassword(
                    userConfiguration.getString( UserConfigurationKeys.LDAP_PASSWORD, null ) );
                ldapConfiguration.setAuthenticationMethod(
                    userConfiguration.getString( UserConfigurationKeys.LDAP_AUTHENTICATION_METHOD, null ) );

                redbackRuntimeConfiguration.setMigratedFromRedbackConfiguration( true );

                updateArchivaRuntimeConfiguration( redbackRuntimeConfiguration );

            }

            // we must ensure userManagerImpls list is not empty if so put at least jdo one !
            if ( redbackRuntimeConfiguration.getUserManagerImpls().isEmpty() )
            {
                log.info(
                    "redbackRuntimeConfiguration with empty userManagerImpls so force at least jdo implementation !" );
                redbackRuntimeConfiguration.getUserManagerImpls().add( "jdo" );
                updateArchivaRuntimeConfiguration( redbackRuntimeConfiguration );
            }

        }
        catch ( RepositoryAdminException e )
        {
            throw new UserConfigurationException( e.getMessage(), e );
        }
    }

    public RedbackRuntimeConfiguration getArchivaRuntimeConfiguration()
    {
        return build( archivaConfiguration.getConfiguration().getRedbackRuntimeConfiguration() );
    }

    public void updateArchivaRuntimeConfiguration( RedbackRuntimeConfiguration redbackRuntimeConfiguration )
        throws RepositoryAdminException
    {
        org.apache.archiva.configuration.RedbackRuntimeConfiguration
            runtimeConfiguration = build( redbackRuntimeConfiguration );
        Configuration configuration = archivaConfiguration.getConfiguration();
        configuration.setRedbackRuntimeConfiguration( runtimeConfiguration );
        try
        {
            archivaConfiguration.save( configuration );
        }
        catch ( RegistryException e )
        {
            throw new RepositoryAdminException( e.getMessage(), e );
        }
        catch ( IndeterminateConfigurationException e )
        {
            throw new RepositoryAdminException( e.getMessage(), e );
        }
    }

    private RedbackRuntimeConfiguration build( org.apache.archiva.configuration.RedbackRuntimeConfiguration runtimeConfiguration )
    {
        RedbackRuntimeConfiguration redbackRuntimeConfiguration =
            new BeanReplicator().replicateBean( runtimeConfiguration, RedbackRuntimeConfiguration.class );

        if ( runtimeConfiguration.getLdapConfiguration() != null )
        {
            redbackRuntimeConfiguration.setLdapConfiguration(
                new BeanReplicator().replicateBean( runtimeConfiguration.getLdapConfiguration(),
                                                    LdapConfiguration.class ) );
        }

        if ( redbackRuntimeConfiguration.getLdapConfiguration() == null )
        {
            // prevent NPE
            redbackRuntimeConfiguration.setLdapConfiguration( new LdapConfiguration() );
        }

        return redbackRuntimeConfiguration;
    }

    private org.apache.archiva.configuration.RedbackRuntimeConfiguration build( RedbackRuntimeConfiguration archivaRuntimeConfiguration )
    {
        org.apache.archiva.configuration.RedbackRuntimeConfiguration redbackRuntimeConfiguration =
            new BeanReplicator().replicateBean( archivaRuntimeConfiguration, org.apache.archiva.configuration.RedbackRuntimeConfiguration.class );

        redbackRuntimeConfiguration.setLdapConfiguration(
            new BeanReplicator().replicateBean( archivaRuntimeConfiguration.getLdapConfiguration(),
                                                org.apache.archiva.configuration.LdapConfiguration.class ) );

        return redbackRuntimeConfiguration;
    }

    // wrapper for UserConfiguration to intercept values (and store it not yet migrated


    public String getString( String key )
    {
        if ( UserConfigurationKeys.USER_MANAGER_IMPL.equals( key ) )
        {
            // possible false for others than archiva user manager
            return getArchivaRuntimeConfiguration().getUserManagerImpls().get( 0 );
        }

        RedbackRuntimeConfiguration conf = getArchivaRuntimeConfiguration();

        if ( conf.getConfigurationProperties().containsKey( key ) )
        {
            return conf.getConfigurationProperties().get( key );
        }

        String value = userConfiguration.getString( key );
        if ( value == null )
        {
            return null;
        }
        conf.getConfigurationProperties().put( key, value );

        try
        {
            updateArchivaRuntimeConfiguration( conf );
        }
        catch ( RepositoryAdminException e )
        {
            log.error( "fail to save RedbackRuntimeConfiguration: {}", e.getMessage(), e );
            throw new RuntimeException( e.getMessage(), e );
        }

        return value;
    }

    public String getString( String key, String defaultValue )
    {
        if ( UserConfigurationKeys.LDAP_HOSTNAME.equals( key ) )
        {
            return getArchivaRuntimeConfiguration().getLdapConfiguration().getHostName();
        }
        if ( UserConfigurationKeys.LDAP_CONTEX_FACTORY.equals( key ) )
        {
            return getArchivaRuntimeConfiguration().getLdapConfiguration().getContextFactory();
        }
        if ( UserConfigurationKeys.LDAP_PASSWORD.equals( key ) )
        {
            return getArchivaRuntimeConfiguration().getLdapConfiguration().getPassword();
        }
        if ( UserConfigurationKeys.LDAP_AUTHENTICATION_METHOD.equals( key ) )
        {
            return getArchivaRuntimeConfiguration().getLdapConfiguration().getAuthenticationMethod();
        }

        RedbackRuntimeConfiguration conf = getArchivaRuntimeConfiguration();

        if ( conf.getConfigurationProperties().containsKey( key ) )
        {
            return conf.getConfigurationProperties().get( key );
        }

        String value = userConfiguration.getString( key, defaultValue );

        if ( value == null )
        {
            return null;
        }

        conf.getConfigurationProperties().put( key, value );
        try
        {
            updateArchivaRuntimeConfiguration( conf );
        }
        catch ( RepositoryAdminException e )
        {
            log.error( "fail to save RedbackRuntimeConfiguration: {}", e.getMessage(), e );
            throw new RuntimeException( e.getMessage(), e );
        }

        return value;
    }

    public int getInt( String key )
    {
        RedbackRuntimeConfiguration conf = getArchivaRuntimeConfiguration();

        if ( conf.getConfigurationProperties().containsKey( key ) )
        {
            return Integer.valueOf( conf.getConfigurationProperties().get( key ) );
        }

        int value = userConfiguration.getInt( key );

        conf.getConfigurationProperties().put( key, Integer.toString( value ) );
        try
        {
            updateArchivaRuntimeConfiguration( conf );
        }
        catch ( RepositoryAdminException e )
        {
            log.error( "fail to save RedbackRuntimeConfiguration: {}", e.getMessage(), e );
            throw new RuntimeException( e.getMessage(), e );
        }

        return value;
    }

    public int getInt( String key, int defaultValue )
    {
        if ( UserConfigurationKeys.LDAP_PORT.equals( key ) )
        {
            return getArchivaRuntimeConfiguration().getLdapConfiguration().getPort();
        }

        RedbackRuntimeConfiguration conf = getArchivaRuntimeConfiguration();

        if ( conf.getConfigurationProperties().containsKey( key ) )
        {
            return Integer.valueOf( conf.getConfigurationProperties().get( key ) );
        }

        int value = userConfiguration.getInt( key, defaultValue );

        conf.getConfigurationProperties().put( key, Integer.toString( value ) );
        try
        {
            updateArchivaRuntimeConfiguration( conf );
        }
        catch ( RepositoryAdminException e )
        {
            log.error( "fail to save RedbackRuntimeConfiguration: {}", e.getMessage(), e );
            throw new RuntimeException( e.getMessage(), e );
        }

        return value;
    }

    public boolean getBoolean( String key )
    {
        RedbackRuntimeConfiguration conf = getArchivaRuntimeConfiguration();

        if ( conf.getConfigurationProperties().containsKey( key ) )
        {
            return Boolean.valueOf( conf.getConfigurationProperties().get( key ) );
        }

        boolean value = userConfiguration.getBoolean( key );

        conf.getConfigurationProperties().put( key, Boolean.toString( value ) );
        try
        {
            updateArchivaRuntimeConfiguration( conf );
        }
        catch ( RepositoryAdminException e )
        {
            log.error( "fail to save RedbackRuntimeConfiguration: {}", e.getMessage(), e );
            throw new RuntimeException( e.getMessage(), e );
        }

        return value;
    }

    public boolean getBoolean( String key, boolean defaultValue )
    {
        if ( UserConfigurationKeys.LDAP_SSL.equals( key ) )
        {
            return getArchivaRuntimeConfiguration().getLdapConfiguration().isSsl();
        }

        RedbackRuntimeConfiguration conf = getArchivaRuntimeConfiguration();

        if ( conf.getConfigurationProperties().containsKey( key ) )
        {
            return Boolean.valueOf( conf.getConfigurationProperties().get( key ) );
        }

        boolean value = userConfiguration.getBoolean( key, defaultValue );

        conf.getConfigurationProperties().put( key, Boolean.toString( value ) );
        try
        {
            updateArchivaRuntimeConfiguration( conf );
        }
        catch ( RepositoryAdminException e )
        {
            log.error( "fail to save RedbackRuntimeConfiguration: {}", e.getMessage(), e );
            throw new RuntimeException( e.getMessage(), e );
        }

        return value;
    }

    public List<String> getList( String key )
    {
        List<String> value = userConfiguration.getList( key );

        RedbackRuntimeConfiguration conf = getArchivaRuntimeConfiguration();
        // TODO concat values
        conf.getConfigurationProperties().put( key, "" );
        try
        {
            updateArchivaRuntimeConfiguration( conf );
        }
        catch ( RepositoryAdminException e )
        {
            log.error( "fail to save RedbackRuntimeConfiguration: {}", e.getMessage(), e );
            throw new RuntimeException( e.getMessage(), e );
        }

        return value;
    }

    public String getConcatenatedList( String key, String defaultValue )
    {
        if ( UserConfigurationKeys.LDAP_BASEDN.equals( key ) )
        {
            return getArchivaRuntimeConfiguration().getLdapConfiguration().getBaseDn();
        }
        if ( UserConfigurationKeys.LDAP_BINDDN.equals( key ) )
        {
            return getArchivaRuntimeConfiguration().getLdapConfiguration().getBindDn();
        }
        return userConfiguration.getConcatenatedList( key, defaultValue );
    }
}