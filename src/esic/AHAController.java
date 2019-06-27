package esic;

import org.graphstream.graph.Edge;
import org.graphstream.graph.Node;

public class AHAController implements org.graphstream.ui.view.ViewerListener, java.awt.event.ActionListener, java.awt.event.MouseWheelListener, java.awt.event.WindowListener
{
	private AHAModel m_model;
	private AHAGUI m_gui;
	private final java.util.concurrent.atomic.AtomicReference<Node> m_currentlyDisplayedNode=new java.util.concurrent.atomic.AtomicReference<>(null);
	
	public AHAController(AHAModel m, AHAGUI g)
	{
		m_model=m;
		m_gui=g;
	}
	
	protected void openfileOrReload(boolean reload)
	{
		String title="AHA-GUI";
		try { title=AHAGUI.class.getPackage().getImplementationVersion().split(" B")[0]; } catch (Exception ex) {ex.printStackTrace();}
		boolean ret=reload;
		if (!reload) { ret=m_gui.openFile(title); }
		if (ret==true)
		{
			synchronized (m_gui.synch_dataViewLock) { m_gui.synch_dataViewFrame=null; }
			m_currentlyDisplayedNode.set(null);
			System.err.println("\n");
			m_gui.initGraphView();
			new Thread( m_model,"ReaderThread").start();
		}
	}	
	
	public void actionPerformed(java.awt.event.ActionEvent e) //swing actions go to here
	{
		Object source=e.getSource();
		String actionCommand=e.getActionCommand();
		if (actionCommand.equals("dataView")) { m_gui.showDataView(m_gui); }
		else if (actionCommand.equals("hideOSProcs")) { m_model.hideOSProcs(m_model.m_graph, ((javax.swing.JCheckBoxMenuItem)source).isSelected()); }
		else if (actionCommand.equals("showFQDN")) { m_model.useFQDNLabels(m_model.m_graph, ((javax.swing.JCheckBoxMenuItem)source).isSelected()); updateSidebar(m_currentlyDisplayedNode.get(), false);}
		else if (actionCommand.equals("resetZoom")) { m_gui.m_graphViewPanel.getCamera().resetView(); }
		else if (actionCommand.equals("exit")) { m_gui.dispatchEvent(new java.awt.event.WindowEvent(m_gui, java.awt.event.WindowEvent.WINDOW_CLOSING)); }
		else if (actionCommand.equals("openNewFile")) { openfileOrReload(false); } 
		else if (actionCommand.equals("refreshInfoPanel")) { updateSidebar(m_currentlyDisplayedNode.get(), false); } //update info display because a checkbox somewhere changed
		else if (actionCommand.equals("search")) { m_gui.m_btmPnlSearchStatus.setText(m_model.handleSearch(m_gui.m_btmPnlSearch.getText())); }
		else if (actionCommand.contains("protocol==") || actionCommand.contains("processpath==")) 
		{ 
			boolean hide=!((javax.swing.JCheckBoxMenuItem)e.getSource()).isSelected();
			m_model.genericHideUnhideNodes( actionCommand,hide );
		}
		else if (actionCommand.contains("scoreMethod"))
		{ 
			AHAModel.ScoreMethod scoremethod=AHAModel.ScoreMethod.Normal;
			try 
			{
				String method=e.getActionCommand().split("-")[1];
				scoremethod=AHAModel.ScoreMethod.getValue(method);
			} catch (Exception ex) { ex.printStackTrace(); }
			m_model.swapNodeStyles(scoremethod, System.currentTimeMillis());
			updateSidebar(m_currentlyDisplayedNode.get(), false); //refresh the info panel now that we're probably on a new score mode
		}
		else if (actionCommand.equals("useCustom"))
		{
			m_model.m_useCustomOverlayScoreFile=((javax.swing.JCheckBoxMenuItem)e.getSource()).isSelected();
			m_model.exploreAndScore(m_model.m_graph);
		}
		else { System.err.println("Unknown action command='"+e.getActionCommand()+"'"); }
	}
	
	protected void updateSidebar(Node node, boolean occuredFromMouseOver)
	{
		if ( node==null || (occuredFromMouseOver && !m_gui.m_infoPnlUpdateOnMouseover.isSelected()) ) { return; } //if element is null, or triggered from mosueover and we're presently supposed to ignore that, return
		m_currentlyDisplayedNode.set(node);
		Object[][] infoData=(Object[][])node.getAttribute("ui.SidebarGeneralInfo"), intPorts=(Object[][])node.getAttribute("ui.SidebarInternalPorts"), extPorts=(Object[][])node.getAttribute("ui.SidebarExternalPorts"), connectionData={{"None",""}}, scoreReasons=null;
		
		try
		{ //update the fourth "Connected Process Name" table. This is updated in the controller because the user can toggle IP/FQDN view at runtime, thus the names of some nodes will change
			if (node.getDegree()>0)
			{
				java.util.ArrayList<String> connectedNodes=new java.util.ArrayList<>();
				for (Edge e : node )
				{
					String nodeName=(String)e.getOpposite(node).getAttribute("ui.label");
					if (!connectedNodes.contains(nodeName)) { connectedNodes.add(nodeName); } //deduplicate
				}
				if (connectedNodes.size()>0)
				{
					connectionData=new Object[connectedNodes.size()][2];
					int i=0;
					for (String nodeName : connectedNodes)
					{
						int idx=nodeName.lastIndexOf('_');
						String pidNum="";
						if (idx > 0 )
						{
							pidNum=nodeName.substring(idx+1); //do this first before nodeName is consumed...
							nodeName=nodeName.substring(0, idx);
						}
						connectionData[i][0]=nodeName;
						if (pidNum.length()>0) { connectionData[i][1]=AHAModel.strAsInt(pidNum); }
						i++;
					}
				}
			}
		} catch (Exception e) { e.printStackTrace(); connectionData=new String[][]{{"Error"}}; }
		
		try
		{ //update the fifth "Score Metric" table
			String score=(String)node.getAttribute("ui.scoreReason");
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
							if (isNegativeScore) { output="<html><font color=red>"+output+"</font></html>"; }
							
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
			Node node=m_model.m_graph.getNode(id);
			if (node==null) { return; }

			java.util.Iterator<Edge> it=node.iterator(); //TODO rewrite in gs2.0 parlance
			while (it.hasNext())
			{
				Edge e=it.next();
				String currentClasses=(String)e.getAttribute("ui.class");
				if (currentClasses==null) { currentClasses=""; }
				if (!currentClasses.contains("clickedAccent")) { e.setAttribute("ui.class", "clickedAccent, "+currentClasses); } //System.out.println("Adding classes: old uiclass was |"+currentClasses+"| is now |"+e.getAttribute("ui.class")+"|");
			}
			updateSidebar(node, false);
		} catch (Exception e) { e.printStackTrace(); }
	}
	
	public synchronized void buttonReleased(String id) 
	{ 
		if (id==null || id.equals("")) { return; }
		try
		{
			Node node=m_model.m_graph.getNode(id);
			if (node==null) { System.out.println("node is null, returning early"); return;}

			java.util.Iterator<Edge> it=node.iterator(); //TODO rewrite in gs2.0 parlance
			while (it.hasNext())
			{
				Edge e=it.next();
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
		Node node=m_model.m_graph.getNode(element.getId());
		updateSidebar(node, true);
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
