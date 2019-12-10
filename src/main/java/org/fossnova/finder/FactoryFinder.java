/*
 * Copyright (c) 2012-2020, FOSS Nova Software foundation (FNSF),
 * and individual contributors as indicated by the @author tags.
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
package org.fossnova.finder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;

import static java.security.AccessController.doPrivileged;

/**
 * Stateless utility class as alternative to <code>java.util.ServiceLoader</code>.
 * Its main design goal is to avoid class loader leaks thus it never caches anything.
 *
 * @author <a href="mailto:opalka.richard@gmail.com">Richard Opalka</a>
 */
public final class FactoryFinder {

    private static final String PREFIX = "META-INF/services/";

    private static final AccessControlContext ACC;

    static {
        ACC = System.getSecurityManager() != null ? AccessController.getContext() : null;
    }

    private FactoryFinder() {
        // no instantiation
    }

    /**
     * Instantiates a factory object given the factory's interface class.
     * First it seeks for system property <B>-DfactoryIface=factoryImpl</B>.
     * If such property is not specified then <B>factoryIface</B> classloader
     * is inspected whether it contains <B>META-INF/services/factoryIface</B> entity.
     * If either system property or META-INF service is defined
     * <B>factoryIface</B> classloader is used to instantiate the implementation class.
     *
     * @param factoryIface required factory interface class to look implementation for
     * @param <T> interface class type
     * @return factory implementation
     * @throws RuntimeException if factory implementation cannot be found or instantiated
     */
    public static <T> T find( final Class< T > factoryIface ) {
        return find( factoryIface, null );
    }

    /**
     * Instantiates a factory object given the factory's interface class.
     * First it seeks for system property <B>-DfactoryIface=factoryImpl</B>.
     * If such property is not specified then <B>factoryIface</B> classloader
     * is inspected whether it contains <B>META-INF/services/factoryIface</B> entity.
     * If either system property or META-INF service or <B>fallbackImpl</B> is defined
     * <B>factoryIface</B> classloader is used to instantiate the implementation class.
     *
     * @param factoryIface required factory interface class to look implementation for
     * @param fallbackImpl optional fallback factory implementation class name
     * @param <T> interface class type
     * @return factory implementation
     * @throws RuntimeException if factory implementation cannot be found or instantiated
     */
    @SuppressWarnings( "unchecked" )
    public static <T> T find( final Class< T > factoryIface, final String fallbackImpl ) {
        if ( factoryIface == null ) {
            throw new NullPointerException( "Factory interface class cannot be null" );
        }
        final ClassLoader loader = factoryIface.getClassLoader();
        String factoryImplClassName = getFactoryImplFromSystemProperty( factoryIface.getName() );
        if ( factoryImplClassName == null ) {
            factoryImplClassName = getFactoryImplClassNameFromServiceProvider( factoryIface.getName(), loader );
        }
        if ( factoryImplClassName == null ) {
            factoryImplClassName = fallbackImpl;
        }
        if ( factoryImplClassName == null ) {
            throw new RuntimeException( "Factory implementation for interface '" + factoryIface.getName() + "' not found" );
        }
        return ( T ) newInstance( factoryImplClassName, loader );
    }

    private static String getFactoryImplFromSystemProperty( final String factoryIfaceName ) {
        final SecurityManager sm = System.getSecurityManager();
        if ( sm != null ) {
            return doPrivileged( new PrivilegedAction< String >() {
                public String run() {
                    return System.getProperty( factoryIfaceName );
                }
            }, ACC );
        }
        return System.getProperty( factoryIfaceName );
    }

    private static String getFactoryImplClassNameFromServiceProvider( final String factoryIfaceName, final ClassLoader loader ) {
        final String resourceName = PREFIX + factoryIfaceName;
        String factoryImplName = null;
        try ( InputStream is = loader.getResourceAsStream( resourceName ) ) {
            if ( is != null ) {
                final BufferedReader reader = new BufferedReader( new InputStreamReader( is, "UTF-8" ) );
                factoryImplName = reader.readLine().trim();
            }
        } catch ( final IOException e ) {
            // ignored
        }
        return isDefined( factoryImplName ) ? factoryImplName : null;
    }

    private static Object newInstance( final String factoryImplClassName, final ClassLoader loader ) {
        try {
            final Class< ? > factory = Class.forName( factoryImplClassName, true, loader );
            return factory.newInstance();
        } catch ( final ClassNotFoundException e ) {
            throw new RuntimeException( "Factory '" + factoryImplClassName + "' not found", e );
        } catch ( final Exception e ) {
            throw new RuntimeException( "Factory '" + factoryImplClassName + "' not instatiated", e );
        }
    }

    private static boolean isDefined( final String s ) {
        return s != null && s.length() > 0;
    }

}
