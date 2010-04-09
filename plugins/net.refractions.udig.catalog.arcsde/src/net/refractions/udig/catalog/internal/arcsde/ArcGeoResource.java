/*
 *    uDig - User Friendly Desktop Internet GIS client
 *    http://udig.refractions.net
 *    (C) 2004, Refractions Research Inc.
 *
 *    This library is free software; you can redistribute it and/or
 *    modify it under the terms of the GNU Lesser General Public
 *    License as published by the Free Software Foundation;
 *    version 2.1 of the License.
 *
 *    This library is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *    Lesser General Public License for more details.
 *
 */
package net.refractions.udig.catalog.internal.arcsde;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import net.refractions.udig.catalog.CatalogPlugin;
import net.refractions.udig.catalog.IGeoResource;
import net.refractions.udig.catalog.IGeoResourceInfo;
import net.refractions.udig.catalog.IResolveChangeEvent;
import net.refractions.udig.catalog.IResolveDelta;
import net.refractions.udig.catalog.IService;
import net.refractions.udig.catalog.internal.CatalogImpl;
import net.refractions.udig.catalog.internal.ResolveChangeEvent;
import net.refractions.udig.catalog.internal.ResolveDelta;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.geotools.data.DataStore;
import org.geotools.data.FeatureSource;
import org.geotools.data.FeatureStore;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

/**
 * Provides ...TODO summary sentence
 * <p>
 * TODO Description
 * </p>
 * 
 * @author David Zwiers, Refractions Research
 * @since 0.6
 */
public class ArcGeoResource extends IGeoResource {
    String typename = null;

    /**
     * Construct <code>PostGISGeoResource</code>.
     * 
     * @param parent
     * @param typename
     */
    public ArcGeoResource( ArcServiceImpl service, String typename ) {
        this.service = service;
        this.typename = typename;
    }

    public URL getIdentifier() {
        try {
            return new URL(service.getIdentifier().toString() + "#" + typename); //$NON-NLS-1$
        } catch (MalformedURLException e) {
            return service.getIdentifier();
        }
    }

    /*
     * @see net.refractions.udig.catalog.IGeoResource#getStatus()
     */
    public Status getStatus() {
        return service.getStatus();
    }

    /*
     * @see net.refractions.udig.catalog.IGeoResource#getStatusMessage()
     */
    public Throwable getMessage() {
        return service.getMessage();
    }

    /*
     * Required adaptions: <ul> <li>IGeoResourceInfo.class <li>IService.class </ul>
     * @see net.refractions.udig.catalog.IResolve#resolve(java.lang.Class,
     * org.eclipse.core.runtime.IProgressMonitor)
     */
    public <T> T resolve( Class<T> adaptee, IProgressMonitor monitor ) throws IOException {
        if (adaptee == null)
            return null;
        if (adaptee.isAssignableFrom(IService.class))
            return adaptee.cast(service(monitor));
        if (adaptee.isAssignableFrom(IGeoResource.class))
            return adaptee.cast(this);
        if (adaptee.isAssignableFrom(IGeoResourceInfo.class))
            return adaptee.cast(createInfo(monitor));
        if (adaptee.isAssignableFrom(FeatureStore.class)) {
            FeatureSource<SimpleFeatureType, SimpleFeature> fs = service(monitor).getDS(monitor)
                    .getFeatureSource(typename);
            if (fs instanceof FeatureStore)
                return adaptee.cast(fs);
            if (adaptee.isAssignableFrom(FeatureSource.class))
                return adaptee.cast(service(monitor).getDS(null).getFeatureSource(typename));
        }
        return super.resolve(adaptee, monitor);
    }

    /*
     * @see net.refractions.udig.catalog.IResolve#canResolve(java.lang.Class)
     */
    public <T> boolean canResolve( Class<T> adaptee ) {
        if (adaptee == null)
            return false;
        return (adaptee.isAssignableFrom(IGeoResourceInfo.class)
                || adaptee.isAssignableFrom(FeatureStore.class)
                || adaptee.isAssignableFrom(FeatureSource.class) || adaptee
                .isAssignableFrom(IService.class));
    }

    @Override
    public ArcGeoResourceInfo getInfo( IProgressMonitor monitor ) throws IOException {
        return (ArcGeoResourceInfo) super.getInfo(monitor);
    }
    protected ArcGeoResourceInfo createInfo( IProgressMonitor monitor ) throws IOException {
        if (getStatus() == Status.BROKEN) {
            return null; // could not connect
        }
        if( monitor == null ) monitor = new NullProgressMonitor();
        
        ArcServiceImpl arcService = service( new SubProgressMonitor( monitor, 50 ));
        DataStore dataStore = arcService.getDS( new SubProgressMonitor( monitor, 50 ));
        
        synchronized ( dataStore ) {
            return new ArcGeoResourceInfo(this, dataStore );
        }
    }

    public ArcServiceImpl service( IProgressMonitor monitor ) throws IOException {
        return (ArcServiceImpl) super.service(monitor);
    }
}