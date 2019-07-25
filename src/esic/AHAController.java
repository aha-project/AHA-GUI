package esic;
//Copyright 2018 ESIC at WSU distributed under the MIT license. Please see LICENSE file for further info.

import esic.AHAGraph.AHANode;

public class AHAController implements org.graphstream.ui.view.ViewerListener, java.awt.event.ActionListener, java.awt.event.MouseWheelListener, java.awt.event.WindowListener
{
	private java.util.concurrent.atomic.AtomicReference<AHAModel> m_model=new java.util.concurrent.atomic.AtomicReference<>();
	private AHAGUI m_gui;
	private final java.util.concurrent.atomic.AtomicReference<AHANode> m_currentlyDisplayedNode=new java.util.concurrent.atomic.AtomicReference<>(null);
	
	public AHAController(String inputFileName, String scoreFileName, int verbosity, boolean useMultiLineGraph)
	{
		m_model.set(new AHAModel(this, inputFileName, scoreFileName, verbosity));
		m_gui=new AHAGUI(m_model.get(), this, useMultiLineGraph);
	}
	
	public void start() { model().run(); }
	private AHAModel model() { return m_model.get(); }
	
	protected void openfileOrReload(boolean reload)
	{
		String title="AHA-GUI";
		try { title=AHAGUI.class.getPackage().getImplementationVersion().split(" B")[0]; } catch (Exception ex) {ex.printStackTrace();}
		boolean ret=reload;
		if (!reload) { ret=m_gui.openFile(model(), title); }
		if (ret==true)
		{
			synchronized (m_gui.synch_dataViewLock) { m_gui.synch_dataViewFrame=null; }
			m_currentlyDisplayedNode.set(null);
			System.err.println("\n");
			AHAModel oldModel=model();
			AHAModel newModel=new AHAModel(this, oldModel.m_inputFileName, oldModel.m_scoreFileName, oldModel.m_verbosity);
			m_model.set(newModel);
			m_gui.initGraphView(newModel);
			new Thread( newModel,"ReaderThread").start();
		}
	}	
	
	public void actionPerformed(java.awt.event.ActionEvent e) //swing actions go to here
	{
		Object source=e.getSource();
		String actionCommand=e.getActionCommand();
		if (actionCommand.equals("dataView")) { m_gui.showDataView(model(), m_gui); }
		else if (actionCommand.equals("hideOSProcs")) { model().hideOSProcs(((javax.swing.JCheckBoxMenuItem)source).isSelected()); }
		else if (actionCommand.equals("showFQDN")) { model().useFQDNLabels(((javax.swing.JCheckBoxMenuItem)source).isSelected()); updateSidebar(m_currentlyDisplayedNode.get(), false);}
		else if (actionCommand.equals("resetZoom")) { m_gui.m_graphViewPanel.getCamera().resetView(); }
		else if (actionCommand.equals("exit")) { m_gui.dispatchEvent(new java.awt.event.WindowEvent(m_gui, java.awt.event.WindowEvent.WINDOW_CLOSING)); }
		else if (actionCommand.equals("openNewFile")) { openfileOrReload(false); } 
		else if (actionCommand.equals("refreshInfoPanel")) { updateSidebar(m_currentlyDisplayedNode.get(), false); } //update info display because a checkbox somewhere changed
		else if (actionCommand.equals("search")) { m_gui.m_btmPnlSearchStatus.setText(model().handleSearch(m_gui.m_btmPnlSearch.getText())); }
		else if (actionCommand.contains("aha.graphlayer==") || actionCommand.contains("processpath==")) 
		{ 
			boolean hide=!((javax.swing.JCheckBoxMenuItem)e.getSource()).isSelected();
			model().genericHideUnhideNodes( actionCommand,hide );
		}
		else if (actionCommand.contains("scoreMethod") || actionCommand.equals("useCustom"))
		{ 
			AHAModel.ScoreMethod scoremethod=null;
			try 
			{
				String method=e.getActionCommand().split("-")[1];
				scoremethod=AHAModel.ScoreMethod.getValue(method);
			} catch (Exception ex) {  }
			model().m_useCustomOverlayScoreFile=((javax.swing.JRadioButtonMenuItem)e.getSource()).isSelected();
			model().swapNodeStyles(scoremethod, System.currentTimeMillis());
			updateSidebar(m_currentlyDisplayedNode.get(), false); //refresh the info panel now that we're probably on a new score mode
		}
		else if (actionCommand.equals("updateFileFromRemoteDB"))
		{ 
			final AHAController controller=this;
			new Thread(){
				public void run() { FileUpdater.updateCSVFileWithRemoteVulnDBData(model().m_inputFileName, "credentials.txt", m_gui, controller, model().m_verbosity); }
			}.start(); 
		}
		else { System.err.println("AHAController: ActionPerformed: Unknown action command='"+e.getActionCommand()+"'"); }
	}
	
	protected void updateSidebar(AHANode node, boolean occuredFromMouseOver)
	{
		if ( node==null || (occuredFromMouseOver && !m_gui.m_infoPnlUpdateOnMouseover.isSelected()) ) { return; } //if element is null, or triggered from mosueover and we're presently supposed to ignore that, return
		m_currentlyDisplayedNode.set(node);
		Object[][] infoData=node.getSidebarAttributes("aha.SidebarGeneralInfo"), intPorts=node.getSidebarAttributes("aha.SidebarInternalPorts"), extPorts=node.getSidebarAttributes("aha.SidebarExternalPorts"), connectionData={{"None",""}}, scoreReasons=null;
		
		try
		{ //update the fourth "Connected Process Name" table. This is updated in the controller because the user can toggle IP/FQDN view at runtime, thus the names of some nodes will change
			if (node.graphNode.getDegree()>0)
			{
				java.util.ArrayList<String> connectedNodes=new java.util.ArrayList<>();
				for (org.graphstream.graph.Edge e : node )
				{
					String nodeName=(String)e.getOpposite(node.graphNode).getId();
					if (!connectedNodes.contains(nodeName)) { connectedNodes.add(nodeName); } //deduplicate
				}
				if (connectedNodes.size()>0)
				{
					connectionData=new Object[connectedNodes.size()][2];
					int i=0;
					for (String nodeName : connectedNodes)
					{
						String pidNum="", uiClass="";
						try { uiClass=(String)model().m_graph.getNode(nodeName).getAttribute("ui.class"); }
						catch (Exception e) { System.out.println("Exception getting nodename="+nodeName); e.printStackTrace(); }
						if (uiClass==null || !uiClass.equals("external"))
						{
							int idx=nodeName.lastIndexOf('_');
							if (idx > 0 )
							{
								pidNum=nodeName.substring(idx+1).trim(); //do this first before nodeName is consumed...
								nodeName=nodeName.substring(0, idx);
							}
						}
						connectionData[i][0]=nodeName;
						connectionData[i][1]=AHAModel.strAsInt(pidNum);
						i++;
					}
				}
			}
		} catch (Exception e) { e.printStackTrace(); connectionData=new String[][]{{"Error"}}; }
		
		try
		{ //update the fifth "Score Metric" table
			String score=(String)node.getAttribute("aha.scoreReason");
			String[] scores=score.split(", ");
			int length=0;
			for (int i=0;i<scores.length;i++) 
			{ 
				if (scores[i].toLowerCase().endsWith("false") && m_gui.m_infoPnlShowOnlyMatchedMetrics.isSelected()) {continue;}
				length++;
			}
			scoreReasons=new Object[length][2];
			int j=0;
			for (int i=0;i<scores.length;i++) 
			{ 
				String[] scrTokens=scores[i].split("=");
				if (scrTokens!=null && scrTokens.length>=2)
				{
					if (m_gui.m_infoPnlShowOnlyMatchedMetrics.isSelected()==true && scrTokens[1].toLowerCase().contains("false")) { continue; } 
					scoreReasons[j][0]=scrTokens[0];
					scoreReasons[j][1]=scrTokens[1];
					if (!m_gui.m_infoPnlShowScoringSpecifics.isSelected()) 
					{ 
						String input=(String)scoreReasons[j][0];
						if (input!=null && input.contains("[") && input.contains("]:")) 
						{ 
							String scoreString=input.split("\\.")[0], scoreValue=input.split("\\]:")[1];
							boolean isNegativeScore=scoreValue.charAt(0)=='-';
							if (!isNegativeScore) { scoreValue="+"+scoreValue; } 
							String output=scoreString+" ("+scoreValue+")";
							if (isNegativeScore) //make scoreMetris that take points off show up in red
							{ 
								output="<html><font color=red>"+output+"</font></html>";
								scoreReasons[j][1]="<html><font color=red>"+scrTokens[1]+"</font></html>";
							}
							scoreReasons[j][0]=output;
						}
					}
					j++;
				}
			}
			
		} catch (Exception e) { e.printStackTrace(); }

		final Object[][][] data={infoData,intPorts,extPorts,connectionData,scoreReasons}; // create final pointer to pass to swing.infokelater. as long as this order of these object arrays is correct, everything will work :)
		javax.swing.SwingUtilities.invokeLater(new Runnable() //perform task on gui thread
		{
			public void run()
			{
				for (int i=0;i<data.length;i++)
				{
					try
					{
						java.util.Vector<Integer> preferredWidths=new java.util.Vector<>();
						try 
						{ 
							for (int j=0;j<m_gui.m_infoPnlTables[i].getColumnModel().getColumnCount();j++)
							{
								preferredWidths.add(m_gui.m_infoPnlTables[i].getColumnModel().getColumn(j).getPreferredWidth());
							}
						} catch (Exception e1) { e1.printStackTrace(); }
						javax.swing.table.DefaultTableModel infoPanelDataModel=(javax.swing.table.DefaultTableModel)m_gui.m_infoPnlTables[i].getModel();
						infoPanelDataModel.setDataVector(data[i], m_gui.m_infoPnlColumnHeaders[i]);
						try 
						{ 
							for (int j=0;j<m_gui.m_infoPnlTables[i].getColumnModel().getColumnCount();j++)
							{
								m_gui.m_infoPnlTables[i].getColumnModel().getColumn(j).setPreferredWidth(preferredWidths.get(j));
							}
						} catch (Exception e1) { e1.printStackTrace(); }
					} catch (Exception e) { e.printStackTrace(); }
				}
			}
		});
	}
	
	protected void moveExternalNodes(AHAModel m)
	{
		try 
		{
			Thread.sleep(1500); 
			m_gui.m_graphViewer.disableAutoLayout();
			Thread.sleep(100);  //add delay to see if issues with moving ext nodes goes away
			
			java.util.Vector<AHANode> leftSideNodes=new java.util.Vector<>(); //moved this below the 1.5s graph stabilization threshold to see if it makes odd occasional issues with moving ext nodes better
			for (AHANode n : m.m_graph) 
			{
				if (n.getAttribute("aha.realextnode")!=null) { leftSideNodes.add(n); }  //extNode.setAttribute("aha.externalNode","aha.externalNode");
				n.graphNode.setAttribute("layout.weight", 6); //switched to add attribute rather than set attribute since it seems to prevent a possible race condition.
			}
			int numLeftNodes=leftSideNodes.size()+2; //1 is for main External node, 2 is so we dont put one at the very top or bottom
			leftSideNodes.insertElementAt(m.m_graph.getNode("external"),leftSideNodes.size()/2);
			Thread.sleep(100);  //add delay to see if issues with moving ext nodes goes away
			int i=1;
		
			org.graphstream.ui.view.camera.Camera cam=m_gui.m_graphViewPanel.getCamera();
			for (AHANode n : leftSideNodes)
			{ 
				org.graphstream.ui.geom.Point3 loc=cam.transformPxToGu(60, (m_gui.m_graphViewPanel.getHeight()/numLeftNodes)*i);
				n.graphNode.setAttribute("xyz", loc.x,loc.y,loc.z);
				i++;
			}
			m_gui.m_graphViewPanel.getCamera().setViewPercent(1.01d);
			org.graphstream.ui.geom.Point3 center=m_gui.m_graphViewPanel.getCamera().getViewCenter();
			org.graphstream.ui.geom.Point3 pixels=m_gui.m_graphViewPanel.getCamera().transformGuToPx(center.x, center.y, center.z);
			pixels.x-=60;
			center=m_gui.m_graphViewPanel.getCamera().transformPxToGu(pixels.x, pixels.y);
			m_gui.m_graphViewPanel.getCamera().setViewCenter(center.x, center.y, center.z);
		} catch (Exception e) { e.printStackTrace(); }
	}
	
	protected void updateOverlayLegendScale(int maxScore) { m_gui.updateOverlayLegendScale(maxScore); }
	
	//Begin graph interaction handlers
	public synchronized void mouseWheelMoved(java.awt.event.MouseWheelEvent e) //zooms graph in and out using mouse wheel
	{
		double newZoom=m_gui.m_graphViewPanel.getCamera().getViewPercent()+((double)e.getWheelRotation()/100d);
		if (newZoom <= 0) { newZoom=0.01d; }
		if (newZoom > 0 && newZoom < 20 ) { m_gui.m_graphViewPanel.getCamera().setViewPercent(newZoom); }
	}
	
	public synchronized void buttonPushed(String id) //called when you click on a graph node/edge
	{
		if (id==null || id.equals("")) { return; } //try { m_graphViewPump.pump(); } catch (Exception e) {e.printStackTrace();}  //non blocking pump to clear anything out before we heckle the graph
		try
		{
			AHANode node=model().m_graph.getNode(id);
			if (node==null) { return; }
			for (org.graphstream.graph.Edge e : node)
			{
				try 
				{
					String currentClasses=(String)e.getAttribute("ui.class");
					if (currentClasses==null) { currentClasses=""; }
					if (!currentClasses.contains("clickedAccent")) { e.setAttribute("ui.class", "clickedAccent, "+currentClasses); } //System.out.println("Adding classes: old uiclass was |"+currentClasses+"| is now |"+e.getAttribute("ui.class")+"|");
				} catch (Exception ex) { ex.printStackTrace(); }
			}
			updateSidebar(node, false);
		} catch (Exception e) { e.printStackTrace(); }
	}
	
	public synchronized void buttonReleased(String id) 
	{ 
		if (id==null || id.equals("")) { return; }
		try
		{
			AHANode node=model().m_graph.getNode(id);
			if (node==null) { System.out.println("node is null, returning early"); return;}
			for (org.graphstream.graph.Edge e : node)
			{
				try 
				{
					String currentClasses=(String)e.getAttribute("ui.class");
					if (currentClasses!=null) { e.setAttribute("ui.class", currentClasses.replaceAll("clickedAccent, ", "")); } //System.out.println("Removing classes: old uiclass was |"+currentClasses+"| is now |"+e.getAttribute("ui.class")+"|");
				} catch (Exception ex) { ex.printStackTrace(); }
			}
		} catch (Exception e2) { e2.printStackTrace(); }
	} //graph mouse function
	
	public synchronized void startedHoveringOverElement(org.graphstream.ui.graphicGraph.GraphicElement element)
	{
		if (element==null) { return; }
		updateSidebar(model().m_graph.getNode(element.getId()), true);
	}
	
	public synchronized void stoppedHoveringOverElement(org.graphstream.ui.graphicGraph.GraphicElement element) {}
	public synchronized void viewClosed(String arg0) {} //graph viewer interface function
	public void mouseOver(String id) {}
	public void mouseLeft(String id) {}
	public void windowOpened(java.awt.event.WindowEvent e) {} //Window listener related events
	public void windowIconified(java.awt.event.WindowEvent e) {}
	public void windowDeiconified(java.awt.event.WindowEvent e) {}
	public void windowActivated(java.awt.event.WindowEvent e) {}
	public void windowDeactivated(java.awt.event.WindowEvent e) {}
	public void windowClosing(java.awt.event.WindowEvent e) { }
	public void windowClosed(java.awt.event.WindowEvent e) { System.err.println("Window closed, exiting."); System.exit(0); } //lets us start a new window and open a new file
}
