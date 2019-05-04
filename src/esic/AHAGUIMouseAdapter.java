package esic;

import org.graphstream.ui.graphicGraph.GraphicElement;

/*
 * This class is built upon a modified version of a mouse adapter class which only exists in the git master for graph stream
 * so is back ported here, and then modified to meet our needs. Original CeCILL-C / LGPL license applies to this class only. 
 * source back ported from: https://github.com/graphstream/gs-core/blob/master/src/org/graphstream/ui/view/util/MouseOverMouseManager.java
 * 
 * Original license from class we modified:
 * This program is free software distributed under the terms of two licenses, the
 * CeCILL-C license that fits European law, and the GNU Lesser General Public
 * License. You can  use, modify and/ or redistribute the software under the terms
 * of the CeCILL-C license as circulated by CEA, CNRS and INRIA at the following
 * URL <http://www.cecill.info> or under the terms of the GNU LGPL as published by
 * the Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * See graphstream website/repo or licenses in /deps/org.graphstream/ for full info
 * 
 * We (ESIC @ WSU) relinquish any copyright claims to this modified class and defer to the original copyrights/licenses
 */

public class AHAGUIMouseAdapter extends org.graphstream.ui.swing_viewer.util.DefaultMouseManager
{
	private static java.util.EnumSet<org.graphstream.ui.view.util.InteractiveElement> ALL_ELEMENTS=java.util.EnumSet.of(org.graphstream.ui.view.util.InteractiveElement.NODE ,org.graphstream.ui.view.util.InteractiveElement.SPRITE);
	private float scaleFactor=1, inverseScaleFactor=1;
	public AHAGUIMouseAdapter(final long delay, AHAGUI target) 
	{
		super();
		this.delay = delay;
		m_target=target;
	}
	public void mousePressed(java.awt.event.MouseEvent e) 
	{ 
		curElement = view.findGraphicElementAt(ALL_ELEMENTS, e.getX()*scaleFactor, e.getY()*scaleFactor);
		if (curElement != null) 
		{
			mouseButtonPressOnElement(curElement, e);
		} 
		else 
		{
			x1 = e.getX()*scaleFactor;
			y1 = e.getY()*scaleFactor;
			mouseButtonPress(e);
		}
	}

	public void mouseReleased(java.awt.event.MouseEvent e) 
	{ 
		if (curElement != null) 
		{
			mouseButtonReleaseOffElement(curElement, e);
			curElement = null;
		} 
		else 
		{
			float x2 = e.getX()*scaleFactor, y2 = e.getY()*scaleFactor, t;
			if (x1 > x2) 
			{
				t = x1;
				x1 = x2;
				x2 = t;
			}
			if (y1 > y2) 
			{
				t = y1;
				y1 = y2;
				y2 = t;
			}
			mouseButtonRelease(e, view.allGraphicElementsIn(ALL_ELEMENTS, x1, y1, x2, y2)); //view.allNodesOrSpritesIn(x1, y1, x2, y2) //TODO FIX
		}
	}

	public void mouseDragged(java.awt.event.MouseEvent e) 
	{ 
		float x2 = e.getX()*scaleFactor, y2 = e.getY()*scaleFactor;
		if (x1==x2 && y1==y2) { return; }
		if (curElement != null) { elementMoving(curElement, e); } //TODO: this function occasionally seems to make _wild_ jumps on the graph rather than moving it a little bit. Not sure how it determines acceleration/etc, but often single pixel moves will throw things off the graph edge
		else 
		{
			//System.out.println("strt------------------------------------");
			org.graphstream.ui.geom.Point3 center=view.getCamera().getViewCenter();
			//System.out.printf("center x=%f y=%f z=%f\n", center.x, center.y, center.z);
			org.graphstream.ui.geom.Point3 pixels=view.getCamera().transformGuToPx(center.x, center.y, center.z);
			//System.out.printf("pixels x=%f y=%f z=%f\n", pixels.x, pixels.y, pixels.z);

			//System.out.printf("pixels*2 x=%f y=%f z=%f\n", pixels.x, pixels.y, pixels.z);
			pixels.x+=((x1-x2)/inverseScaleFactor);
			pixels.y+=((y1-y2)/inverseScaleFactor);
			//System.out.printf("pixelsmoved x=%f y=%f z=%f\n", pixels.x, pixels.y, pixels.z);
			
			center=view.getCamera().transformPxToGu(pixels.x, pixels.y);
			//System.out.printf("newcenter x=%f y=%f z=%f\n", center.x, center.y, center.z);
			view.getCamera().setViewCenter(center.x, center.y, center.z);
			//System.out.println("end------------------------------------");
			x1=x2;
			y1=y2;
		}
	}
	
	protected void elementMoving(GraphicElement element, java.awt.event.MouseEvent e) {
		view.moveElementAtPx(element, e.getX()*scaleFactor, e.getY()*scaleFactor);
	}

	private org.graphstream.ui.graphicGraph.GraphicElement hoveredElement;
	private long hoveredElementLastChanged;
	private java.util.concurrent.locks.ReentrantLock hoverLock = new java.util.concurrent.locks.ReentrantLock();
	private static java.util.Timer hoverTimer = new java.util.Timer("AHAGUIMouseAdapterTimer",true); //TODO, changed this to static so we stop creating new ones on new file load...might be a bad move, we'll see
	private HoverTimerTask latestHoverTimerTask;
	private final long delay;
	private final AHAGUI m_target;

	public void mouseMoved(java.awt.event.MouseEvent e) 
	{ 
		try {
			
			hoverLock.lockInterruptibly();
			boolean stayedOnElement = false;
			org.graphstream.ui.graphicGraph.GraphicElement currentElement = view.findGraphicElementAt(ALL_ELEMENTS, e.getX()*scaleFactor, e.getY()*scaleFactor); 
			if (hoveredElement != null) 
			{
				stayedOnElement = currentElement == null ? false : currentElement.equals(hoveredElement);
				if (!stayedOnElement ) 
				{ 
					m_target.stoppedHoveringOverElement(hoveredElement);
					this.hoveredElement = null;
				}
			}
			if (!stayedOnElement && currentElement != null) 
			{
				if (delay <= 0) { m_target.startedHoveringOverElement(curElement); } 
				else 
				{
					hoveredElement = currentElement;
					hoveredElementLastChanged = e.getWhen();
					if (latestHoverTimerTask != null) { latestHoverTimerTask.cancel(); }
					latestHoverTimerTask = new HoverTimerTask(hoveredElementLastChanged, hoveredElement);
					hoverTimer.schedule(latestHoverTimerTask, delay);
				}
			}
		} 
		catch(InterruptedException iex) { /*NOP*/ } 
		finally { hoverLock.unlock(); }
	}
	private final class HoverTimerTask extends java.util.TimerTask 
	{
		private final long lastChanged;
		private final org.graphstream.ui.graphicGraph.GraphicElement element;
		public HoverTimerTask(long lastChanged, org.graphstream.ui.graphicGraph.GraphicElement element) 
		{
			this.lastChanged = lastChanged;
			this.element = element;
		}
		public void run() 
		{
			try 
			{
				hoverLock.lock();
				if (hoveredElementLastChanged == lastChanged) { m_target.startedHoveringOverElement(element); }
			} 
			catch (Exception ex) { ex.printStackTrace(); } 
			finally { hoverLock.unlock(); }
		}
	}	
}
