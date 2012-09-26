/*
 * Copyright (c) 2012, FOSS Nova Software foundation (FNSF),
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
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Class loader leak free factory finder utility.
 * This class is alternative to <code>java.util.ServiceLoader</code>.
 * It always ignores current context class loader
 * which is many times the main cause of various class loader leaks.
 *
 * @author <a href="mailto:opalka dot richard at gmail dot com">Richard Opalka</a>
 */
public final class FactoryFinder {

    private FactoryFinder() {
        // no instances
    }

    /**
     * Instantiates a factory object given the factory's interface class.
     *
     * @param factoryIface required factory interface class to look impl. for
     * @return a factory object
     */
    public static <T> T find( final Class< T > factoryIface ) {
        return find( factoryIface, null );
    }

    /**
     * Instantiates a factory object given the factory's interface class.
     *
     * @param factoryIface required factory interface class to look impl. for
     * @param fallbackFactoryImplName optional fallback factory impl. class name
     * @return a factory object
     */
    @SuppressWarnings( "unchecked" )
    public static <T> T find( final Class< T > factoryIface, final String fallbackFactoryImplName ) {
        if ( factoryIface == null ) {
            throw new IllegalArgumentException( "Factory interface class cannot be null" );
        }
        final ClassLoader loader = factoryIface.getClassLoader();
        String factoryImplClassName = getFactoryImplFromSystemProperty( factoryIface.getName() );
        if ( factoryImplClassName == null ) {
            factoryImplClassName = getFactoryImplClassNameFromServiceProvider( factoryIface.getName(), loader );
        }
        if ( factoryImplClassName == null ) {
            factoryImplClassName = fallbackFactoryImplName;
        }
        if ( factoryImplClassName == null ) {
            throw new RuntimeException( "Factory implementation for interface '" + factoryIface.getName() + "' not found" );
        }
        return ( T ) newInstance( factoryImplClassName, loader );
    }

    private static String getFactoryImplFromSystemProperty( final String factoryIfaceName ) {
        return System.getProperty( factoryIfaceName );
    }

    private static String getFactoryImplClassNameFromServiceProvider( final String factoryIfaceName, final ClassLoader loader ) {
        final String resourceName = "META-INF/services/" + factoryIfaceName;
        final InputStream is = getResource( resourceName, loader );
        String factoryImplName = null;
        BufferedReader reader = null;
        if ( is != null ) {
            try {
                reader = new BufferedReader( new InputStreamReader( is, "UTF-8" ) );
                factoryImplName = reader.readLine().trim();
            } catch ( final IOException e ) {
                // ignored
            } finally {
                safeClose( reader );
            }
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

    private static InputStream getResource( final String resource, final ClassLoader loader ) {
        InputStream is = ClassLoader.getSystemResourceAsStream( resource );
        if ( is == null ) {
            is = loader.getResourceAsStream( resource );
        }
        return is;
    }

    private static void safeClose( final Closeable c ) {
        if ( c != null ) {
            try {
                c.close();
            } catch ( final IOException e ) {
                // ignored
            }
        }
    }

    private static boolean isDefined( final String s ) {
        return s != null && s.length() > 0;
    }

}
