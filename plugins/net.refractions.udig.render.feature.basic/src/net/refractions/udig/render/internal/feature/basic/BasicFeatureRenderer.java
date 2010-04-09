/*
 * uDig - User Friendly Desktop Internet GIS client http://udig.refractions.net (C) 2004, Refractions Research Inc. This
 * library is free software; you can redistribute it and/or modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; version 2.1 of the License. This library is distributed in the
 * hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 */
package net.refractions.udig.render.internal.feature.basic;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.refractions.udig.core.TransparencyRemovingVisitor;
import net.refractions.udig.project.ILayer;
import net.refractions.udig.project.internal.ProjectPlugin;
import net.refractions.udig.project.internal.StyleBlackboard;
import net.refractions.udig.project.internal.render.SelectionLayer;
import net.refractions.udig.project.internal.render.impl.RendererImpl;
import net.refractions.udig.project.internal.render.impl.Styling;
import net.refractions.udig.project.preferences.PreferenceConstants;
import net.refractions.udig.project.render.ILabelPainter;
import net.refractions.udig.project.render.IRenderContext;
import net.refractions.udig.project.render.RenderException;
import net.refractions.udig.render.feature.basic.internal.Messages;
import net.refractions.udig.ui.ProgressManager;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jface.preference.IPreferenceStore;
import org.geotools.data.DefaultQuery;
import org.geotools.data.FeatureSource;
import org.geotools.data.FeatureStore;
import org.geotools.data.Query;
import org.geotools.data.crs.ForceCoordinateSystemFeatureResults;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.SchemaException;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.map.DefaultMapContext;
import org.geotools.map.DefaultMapLayer;
import org.geotools.map.MapContext;
import org.geotools.map.MapLayer;
import org.geotools.renderer.GTRenderer;
import org.geotools.renderer.RenderListener;
import org.geotools.renderer.lite.StreamingRenderer;
import org.geotools.styling.Style;
import org.geotools.styling.visitor.DuplicatingStyleVisitor;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.TransformException;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.TopologyException;

/**
 * The default victim renderer. Based on the Lite-Renderer from Geotools.
 * 
 * @author Jesse Eichar
 * @version $Revision: 1.9 $
 */
public class BasicFeatureRenderer extends RendererImpl {

	private GTRenderer renderer = null;

	protected MapContext map = null;

    protected MapLayer[] layers = null;

	protected BasicRenderListener listener = new BasicRenderListener();

	public BasicFeatureRenderer() {

        ClassLoader current = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(StreamingRenderer.class.getClassLoader());
            Logger logger = Logger.getLogger("org.geotools.rendering");//$NON-NLS-1$
            if (RendererPlugin.isDebugging(Trace.FINEST)) {
                logger.setLevel(Level.FINE);
                ConsoleHandler ch = new ConsoleHandler();
                ch.setLevel(Level.FINE);
                logger.addHandler(ch);
            } else {
                logger.setLevel(Level.SEVERE);
            }
        } finally {
            Thread.currentThread().setContextClassLoader(current);
        }
    }

	/**
	 * @see net.refractions.udig.project.internal.render.impl.RendererImpl#render(java.awt.Graphics2D,
	 *      org.eclipse.core.runtime.IProgressMonitor)
	 */
	public void render(Graphics2D destination, IProgressMonitor monitor)
			throws RenderException {
		render(destination, getContext().getImageBounds(), monitor);
	}

	private final static int NOT_INITIALIZED = -2;

	int count = NOT_INITIALIZED;

	protected void setQueries() {
		try {
			// The context seems to have other ideas about the query we should draw
			// (in order to filter out the features being edited at the moment)
			//
			Query featureQuery = getContext().getFeatureQuery();
			layers[0].setQuery(featureQuery);
		} catch (Exception e) {
			// do nothing.
		}
	}

	/**
	 * does some additional initialization in preparation for drawing. It only
	 * needs to be done once so there is a quick shortcircuit check in the
	 * beginning Obtains the features source, creates the MapLayer and Map
	 * context objects required for Lite renderer and creates the lite renderer.
	 * 
	 * @throws IOException
	 * @throws SchemaException 
	 */
	private void prepareDraw(IProgressMonitor monitor) throws IOException, SchemaException {

		// check for style information on the blackboard
 		ILayer layer = getContext().getLayer();
        StyleBlackboard styleBlackboard = (StyleBlackboard) layer.getStyleBlackboard();
        FeatureSource<SimpleFeatureType, SimpleFeature> featureSource;
		featureSource = layer.getResource(
				FeatureStore.class, new SubProgressMonitor(monitor, 0));
		if( featureSource == null ){
		    featureSource = layer.getResource(
	                FeatureSource.class, new SubProgressMonitor(monitor, 0));
		}
		Style style = getStyle(styleBlackboard, featureSource);
        
		layers = new MapLayer[1];
		CoordinateReferenceSystem layerCRS = layer.getCRS();
		CoordinateReferenceSystem dataCRS = featureSource.getSchema().getCoordinateReferenceSystem();
		
		if (!layerCRS.equals(dataCRS)){
			// need to force the coordinate reference system to match the layer definition
		    // FeatureCollection<SimpleFeatureType, SimpleFeature> reprojectingFc = new ForceCoordinateSystemFeatureResults(featureSource.getFeatures(), layer.getCRS()); 
			// layers[0] = new DefaultMapLayer(reprojectingFc, style, "Test"); //$NON-NLS-1$
	         layers[0] = new DefaultMapLayer(featureSource, style, "Test"); //$NON-NLS-1$
	         DefaultQuery query = new DefaultQuery();
	         query.setTypeName( featureSource.getSchema().getTypeName() );
	         query.setCoordinateSystem( layerCRS );
	         layers[0].setQuery( query );
		}else{
			layers[0] = new DefaultMapLayer(featureSource, style, "Test"); //$NON-NLS-1$
		}
		map = new DefaultMapContext(layers, getContext().getCRS());

	}

    protected Style getStyle( StyleBlackboard styleBlackboard,  FeatureSource<SimpleFeatureType, SimpleFeature> featureSource ) {
        // pull style information off the blackboard
		Style style = (Style) styleBlackboard.lookup(Style.class);
		IPreferenceStore store = ProjectPlugin.getPlugin().getPreferenceStore();
		boolean transparency = store
				.getBoolean(PreferenceConstants.P_TRANSPARENCY);
		try{
		    if (!transparency) {
                if (style != null) {
                    DuplicatingStyleVisitor duplicator = new DuplicatingStyleVisitor();
                    style.accept(duplicator);
                    style=(Style) duplicator.getCopy();
                    style = removeTransparency(style);                    
                }
		    }
		}catch (Throwable e) {
		    RendererPlugin.log("Error duplicating style for transparency setting", e); //$NON-NLS-1$
        }
		if (style == null) {
			style = Styling.createLineStyle(featureSource.getSchema()
					.getName().getLocalPart(), Color.BLUE);
		}
        return style;
    }

	private Style removeTransparency(Style style) {
		style.accept(new TransparencyRemovingVisitor());
		return style;
	}

	/**
	 * @see net.refractions.udig.project.internal.render.impl.RendererImpl#dispose()
	 */
	public void dispose() {
	    if (getRenderer() != null)
	        getRenderer().stopRendering();

	}

    @Override
    public void setState( int newState ) {
        super.setState(newState);
    }

	/**
	 * @see net.refractions.udig.project.internal.render.impl.RendererImpl#render(com.vividsolutions.jts.geom.Envelope,
	 *      org.eclipse.core.runtime.IProgressMonitor)
	 */
	public void render(IProgressMonitor monitor)
			throws RenderException {
		Graphics2D graphics = null;
		try {
			graphics = getContext().getImage().createGraphics();
			render(graphics, getRenderBounds(), monitor);
		} finally {
			if (graphics != null)
				graphics.dispose();
		}
	}

    @SuppressWarnings("unchecked")
    private void render(Graphics2D graphics, Envelope bounds,
			IProgressMonitor monitor) throws RenderException {
        getContext().setStatus(ILayer.WAIT);
        getContext().setStatusMessage(Messages.BasicFeatureRenderer_rendering_status);
        String endMessage=null;
        int endStatus=ILayer.DONE;
		try {
            
            if( getContext().getLayer().getSchema()==null || 
                    getContext().getLayer().getSchema().getGeometryDescriptor()==null ){
                endStatus=ILayer.WARNING;
                endMessage=Messages.BasicFeatureRenderer_layer_has_no_geometry;
                return;
            }
            
			prepareDraw(monitor);

            if( monitor.isCanceled() )
                return;
			// setFeatureLoading(monitor);
            
			ReferencedEnvelope validBounds = validateBounds(bounds, monitor, getContext());
            
            if( validBounds.isNull() )
                return;
            
            try{
                validBounds.transform(getContext().getLayer().getCRS(), true);
            }catch( TransformException te){
                RendererPlugin.log("viewable area is available in the layer CRS", te); //$NON-NLS-1$
                endMessage=Messages.BasicFeatureRenderer_warning1;
                endStatus=ILayer.WARNING;
                return;
            }catch( AssertionError te){
                // this clause is enable this fix to work even during developement 
                RendererPlugin.log("Viewable area available in the layer CRS", te); //$NON-NLS-1$
                endMessage=Messages.BasicFeatureRenderer_warning1;
                endStatus=ILayer.WARNING;
                return;
            } catch (FactoryException e) {
                throw (RenderException) new RenderException( ).initCause( e );
            }
            
            
			listener.init(monitor);
			setQueries();
			Point min = getContext()
					.worldToPixel(
							new Coordinate(validBounds.getMinX(), validBounds
									.getMinY()));
			Point max = getContext()
					.worldToPixel(
							new Coordinate(validBounds.getMaxX(), validBounds
									.getMaxY()));
			int width = Math.abs(max.x - min.x);
			int height = Math.abs(max.y - min.y);
			//TODO: if width or height = 0, then it's a point...need to 
			// figure out how much ti render (examine style)
			Rectangle paintArea;
			if (height == 0 || width == 0) {
				width = 50;
				height = 50;
				min.x -= 25;
				min.y -= 25;
                max.x += 25;
                max.x += 25;
				graphics.setBackground(new Color(0,0,0,0));
				graphics.clearRect( min.x, min.y, width, height );
				paintArea = new Rectangle(Math.min(min.x, max.x), Math
				        .min(min.y, max.y), width, height);
				validBounds=getContext().worldBounds(paintArea);
			}else{
                paintArea = new Rectangle(Math.min(min.x, max.x), Math
                        .min(min.y, max.y), width, height);
            }

			map.setAreaOfInterest(validBounds, getContext().getViewportModel()
					.getCRS());

			GTRenderer renderer2 = getRenderer();
            
            if( bounds!=null && !bounds.isNull() ){
                graphics.setClip(paintArea);
            }
			java.util.Map<Object, Object> rendererHints=renderer2.getRendererHints();

            rendererHints.put(StreamingRenderer.DECLARED_SCALE_DENOM_KEY, getContext().getViewportModel().getScaleDenominator());
            rendererHints.put(StreamingRenderer.SCALE_COMPUTATION_METHOD_KEY, StreamingRenderer.SCALE_ACCURATE);
            ILabelPainter labelPainter = getContext().getLabelPainter();
            Point origin = new Point(paintArea.x, paintArea.y);
            String layerId=getContext().getLayer().getID().toString();
            if ( getContext().getLayer() instanceof SelectionLayer )
                layerId = layerId+"-Selection"; //$NON-NLS-1$
            rendererHints.put(StreamingRenderer.LABEL_CACHE_KEY, new LabelCacheDecorator(labelPainter, origin, layerId));
            
            renderer2.setRendererHints(rendererHints);

            RenderingHints hints = new RenderingHints(Collections.EMPTY_MAP);
            hints.add(new RenderingHints(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED));
            hints.add(new RenderingHints(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_DISABLE));
            hints.add(new RenderingHints(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_SPEED));
            hints.add(new RenderingHints(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_SPEED));
            hints.add(new RenderingHints(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR));
            hints.add(new RenderingHints(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE));
            hints.add(new RenderingHints(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF));

            IPreferenceStore store = ProjectPlugin.getPlugin().getPreferenceStore();
            boolean antiAliasing = store.getBoolean(PreferenceConstants.P_ANTI_ALIASING);
            hints.add(new RenderingHints(RenderingHints.KEY_ANTIALIASING, antiAliasing
                    ? RenderingHints.VALUE_ANTIALIAS_ON
                    : RenderingHints.VALUE_ANTIALIAS_OFF));

            graphics.addRenderingHints(hints);
            renderer2.setJava2DHints(hints);
            
            
            if( monitor.isCanceled() )
                return;

			renderer2.paint(graphics, paintArea, validBounds);

		} catch (Throwable e1) {
            if( e1 instanceof InterruptedException)
                return;
			RenderException e2 = new RenderException(
					"Exception(s) occured during rendering: " //$NON-NLS-1$
							+ e1.getLocalizedMessage());
			e2.initCause(e1);
			throw e2;
		} finally {
            /*
             * vitalus:
             * Clear MapContext to remove  <code>FeatureListener</code>s from
             * FeatureStore implementation, otherwises listeners hell takes place
             * (example is a ShapefileDataStore and its FeatureListenerManager).
             */
			if( map!=null ){
				map.clearLayerList();
			}
            
            getContext().setStatus(endStatus);
            getContext().setStatusMessage(endMessage);
			Exception renderingProblem = listener.exception;
            if (renderingProblem != null) {
                if( renderingProblem instanceof InterruptedException)
                    return;
				if (!(renderingProblem instanceof TopologyException)) {
					RenderException e2 = new RenderException(
							Messages.BasicFeatureRenderer_renderingProblem
							+ renderingProblem.getLocalizedMessage(), renderingProblem);
					throw e2;
				}
			}
			if (!listener.featureRendered) {
				int totalFeatures = -1;
				if (totalFeatures != -1 && totalFeatures > 0) {
					getContext()
							.setStatusMessage(
									Messages.BasicFeatureRenderer_noFeatures);
				}
			}
		}
	}

	protected GTRenderer getRenderer() {
		if (renderer == null) {
			renderer = new StreamingRenderer();
			HashMap<Object, Object> rendererHints = new HashMap<Object, Object>();
			rendererHints.put("optimizedDataLoadingEnabled", true); //$NON-NLS-1$
			renderer.setRendererHints(rendererHints);
			//renderer.removeRenderListener(StreamingRenderer.DEFAULT_LISTENER);
			renderer.addRenderListener(listener);
            
		}
		renderer.setContext(map);
		return renderer;
	}

	/**
	 * Validates the bounds.
	 * 
	 * <p>This function:
	 * <ul>
	 * <li>checks if the bounds are null; if null it will set them to the viewport bounds</li>
	 * <li>checks if the bounds outside of the layer bounds; if so then the bounds are set to a 
	 * null referenced envelope (nothing to render).</li>
	 * </ul>
	 * <p>
	 * It returns the validated bounds. 
	 *
	 * @param bounds
	 * @param monitor
	 * @param context
	 * @return
	 * @throws IOException
	 * @throws FactoryException
	 * @throws RenderException
	 */
	public static ReferencedEnvelope validateBounds(Envelope bounds, IProgressMonitor monitor, IRenderContext context)
			throws IOException, FactoryException, RenderException {
        
	    if (bounds == null){
	        //get the bounds from the context
	        bounds = context.getImageBounds();
	    }
        CoordinateReferenceSystem viewportCRS = context.getCRS();
        ReferencedEnvelope layerBounds = context.getLayer().getBounds(new SubProgressMonitor(monitor, 0), viewportCRS);
        //if the bounds do not intersect the layer bounds then 
        //we have nothing to render to return empty referenced envelope
        if( !layerBounds.intersects(bounds) )
            return new ReferencedEnvelope(context.getCRS());
        return new ReferencedEnvelope(bounds, viewportCRS);
	}

	private class BasicRenderListener implements RenderListener {

		IProgressMonitor monitor;

		Exception exception;

		int exceptionCount = 0;

		boolean featureRendered = false;
		int count=0;
		long lastUpdate;

		private static final int UPDATE_INTERVAL = 3000;

		/**
		 * @see org.geotools.renderer.lite.RenderListener#featureRenderer(org.geotools.feature.SimpleFeature)
		 */
		public void featureRenderer(SimpleFeature feature) {
			if (!featureRendered)
				featureRendered = true;
            
            count++;
			synchronized (monitor) {
				if (monitor.isCanceled())
					getRenderer().stopRendering();
			}
			long current = System.currentTimeMillis();
			if (current - lastUpdate > UPDATE_INTERVAL) {
				lastUpdate = current;
				setState(RENDERING);
			}
		}

		/**
		 * @see org.geotools.renderer.lite.RenderListener#errorOccurred(java.lang.Exception)
		 */
		public void errorOccurred(Exception e) {
			if( e!=null ){
			    e.printStackTrace();
			    if( e.getMessage() != null  &&
			          ( e.getMessage().toLowerCase().contains("timeout")|| //$NON-NLS-1$
	                    e.getMessage().toLowerCase().contains("time-out") || //$NON-NLS-1$
	                    e.getMessage().toLowerCase().contains("timed out") )){ //$NON-NLS-1$
	                exception = new Exception(Messages.BasicFeatureRenderer_request_timed_out);
	                if( getRenderer()!=null )
	                    getRenderer().stopRendering();
	            }
			}
            if ( e instanceof IOException ){
                if( getRenderer()!=null)
                    getRenderer().stopRendering();
                exception=e;
            }
                
			if (exceptionCount > 500) 
                if( getRenderer()!=null)
                    getRenderer().stopRendering();
			exception = e;
			exceptionCount++;
		}

		/**
		 * Initialize listener
		 * 
		 * @param monitor
		 */
		public void init(IProgressMonitor monitor) {
			lastUpdate = System.currentTimeMillis();
			this.monitor = monitor;
			exception = null;
			exceptionCount = 0;
			featureRendered = false;
            count=0;
		}
	}
    
    @SuppressWarnings("nls")
    public void refreshImage(){
        try{
            render(ProgressManager.instance().get());
        }catch( RenderException e ){
            getContext().setStatus(ILayer.ERROR);
            if( e.getCause() != null ){
                getContext().setStatusMessage(e.getLocalizedMessage()+" - "+e.getLocalizedMessage() );     //$NON-NLS-1$
            }
            else {
                getContext().setStatusMessage(e.getLocalizedMessage());
            }
        }
    }
}