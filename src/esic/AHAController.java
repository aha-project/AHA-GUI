package esic;
//Copyright 2018 ESIC at WSU distributed under the MIT license. Please see LICENSE file for further info.

import esic.AHAGraph.AHANode;

public class AHAController implements org.graphstream.ui.view.ViewerListener, java.awt.event.ActionListener, java.awt.event.MouseWheelListener, java.awt.event.WindowListener
{
	private java.util.concurrent.atomic.AtomicReference<AHAModel> m_model=new java.util.concurrent.atomic.AtomicReference<>();
	private AHAGUI m_gui;
	private final java.util.concurrent.atomic.AtomicReference<AHANode> m_currentlyDisplayedNode=new java.util.concurrent.atomic.AtomicReference<>(null);
	protected java.util.concurrent.atomic.AtomicReference<String> m_layoutMode=new java.util.concurrent.atomic.AtomicReference<>(); //set the default in AHAGUI:resetUI
	private java.util.concurrent.ExecutorService m_backgroundExec= java.util.concurrent.Executors.newCachedThreadPool();
	protected AHASettings m_settings=null;
	
	public void start() 
	{ 
		AHAModel m=model();
		if (m!=null) { m.run(); }
	}
	private AHAModel model() { return m_model.get(); }
	
	protected void openfileOrReload(boolean reload)
	{
		String title="AHA-GUI";
		try { title=AHAGUI.class.getPackage().getImplementationVersion().split(" B")[0]; } catch (Exception ex) {ex.printStackTrace(); }
		boolean ret=reload;
		if (!reload) { ret=m_gui.openFile(model(), title); }
		if (ret==true)
		{
			synchronized (m_gui.synch_dataViewLock) { m_gui.synch_dataViewFrame=null; }
			m_currentlyDisplayedNode.set(null);
			System.err.println("\n");
			AHAModel oldModel=model();
			m_settings.setProperty("inputfile", oldModel.m_inputFileName);
			m_gui.setTitle(title+" "+oldModel.m_inputFileName);
			AHAModel newModel=new AHAModel(this, m_settings);
			m_model.set(newModel);
			m_gui.initGraphView(newModel);
			m_backgroundExec.execute(newModel);
		}
	}	
	
	public void actionPerformed(java.awt.event.ActionEvent ae) //swing actions go to here
	{
		String actionCommand=ae.getActionCommand();
		boolean sel=false;
		if (ae.getSource() instanceof javax.swing.AbstractButton) { sel=((javax.swing.AbstractButton)ae.getSource()).isSelected(); }
		final boolean selected=sel;
		
		//the first few actions do not require access to graph, and thus we don't bother putting them on the background thread
		if (actionCommand.equals("dataView")) { m_gui.showDataView(model(), m_gui); }
		else if (actionCommand.equals("showPrefsPane")) { m_settings.showPrefsPane(m_gui); } 
		else if (actionCommand.equals("exit")) { m_gui.dispatchEvent(new java.awt.event.WindowEvent(m_gui, java.awt.event.WindowEvent.WINDOW_CLOSING)); }
		else if (actionCommand.equals("openNewFile")) { openfileOrReload(false); } 
		else if (actionCommand.equals("refreshInfoPanel")) { updateSidebar(m_currentlyDisplayedNode.get(), false); }
		else //require access to ONLY graph and/or background thread, or use only functions that already do work on background and then update UI on foreground (e.g. updateSidebar)
		{
			final AHAController controller=this;
			m_backgroundExec.execute(new Runnable()
			{
				public void run()
				{ 
					if (actionCommand.equals("showOSProcs")) { model().showOSProcs(selected); } //hideNullPathProcs
					else if (actionCommand.equals("resetZoom")) { m_gui.m_graphViewPanel.getCamera().resetView(); }
					else if (actionCommand.contains("aha.graphlayer==") || actionCommand.contains("processpath==")) { model().genericHideUnhideNodes( actionCommand, !selected ); }
					else if (actionCommand.equals("showFQDN")) { model().useFQDNLabels(selected); updateSidebar(m_currentlyDisplayedNode.get(), false); }
					else if (actionCommand.contains("scoreMethod") || actionCommand.equals("useCustom"))
					{ 
						AHAModel.ScoreMethod scoremethod=null;
						try { scoremethod=AHAModel.ScoreMethod.getValue(actionCommand.split("-")[1]); }
						catch (Exception ex) { ex.printStackTrace(); }
						model().m_useCustomOverlayScoreFile=selected;
						model().swapNodeStyles(scoremethod, System.currentTimeMillis());
						updateSidebar(m_currentlyDisplayedNode.get(), false); //refresh the info panel now that we're probably on a new score mode
					}
					else if (actionCommand.contains("layoutMethod-"))
					{
						String layoutMethod=actionCommand.replaceFirst("layoutMethod-", "");
						m_layoutMode.set(layoutMethod);
						moveExternalNodes(model()); 
					}
					else if (actionCommand.equals("updateFileFromRemoteDB")) { FileUpdater.updateCSVFileWithRemoteVulnDBData(m_gui, controller, m_settings); }
					else if (actionCommand.equals("search")) //both uses the graph and requires an update to the GUI
					{ 
						final String status=model().handleSearch(m_gui.m_btmPnlSearch.getText());
						javax.swing.SwingUtilities.invokeLater(new Runnable()
						{
							public void run() { m_gui.m_btmPnlSearchStatus.setText(status);  }
						});
					}
					else if (actionCommand.equals("runAHAScraper"))
					{
						try
						{ //TODO: this will probably fail gloriously if they have not done the exec powershell bypass thing.
							String dirName="AHA-Scraper-Win", taskString="powershell.exe -ExecutionPolicy Bypass -File ./AHA-Scraper.ps1";
							java.io.File workingDir=new java.io.File(dirName), scriptFile=new java.io.File(dirName+"/AHA-Scraper.ps1");
							if ( !workingDir.exists() || !scriptFile.exists() )
							{
								String message="AHA-Scraper could not be located.\nPlease place it within the AHA-GUI directory in a directory called 'AHA-Scraper-Win'";
								if (!System.getProperty("java.vendor").toLowerCase().contains("window")) { message="Direct run of the scaper is currently only supported on Windows™"; }
								javax.swing.JOptionPane.showMessageDialog(m_gui, message, "Scraper Error", javax.swing.JOptionPane.WARNING_MESSAGE);
								return;
							}
							System.out.printf("Attempting to run AHA task '%s'\n", taskString);
							final Process process=new ProcessBuilder(taskString.split(" ")).redirectErrorStream(true).directory(workingDir).start();
							
							long startTime=System.currentTimeMillis();
							int progress=0;
							javax.swing.ProgressMonitor pm=null;
							AHAGUIHelpers.tryCancelSplashScreen();
							try 
							{ 
								String message="Running AHA-Scraper:                                                                "; //the giant blank string helps us guarantee a reasonable minimum size
								pm=new javax.swing.ProgressMonitor(m_gui,message,"Preparing to start updating file...",progress++,100); 
								pm.setMillisToDecideToPopup(1);
								pm.setMillisToPopup(1);
							} catch (Exception e) {} //in case we're running headlessly
							
							java.io.BufferedReader reader=new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()));
							while (process.isAlive() && (System.currentTimeMillis()-startTime < 100000) ) //limit max runtime to 100s
							{
								String output=reader.readLine();
								if (output==null) { break; }
								if (output.toLowerCase().contains("complete, elapsed time")) { break; }
								if (progress > 90) { progress--; }
								AHAGUIHelpers.tryUpdateProgress(pm,progress++,100,output);
								if (output.length()>0) { System.out.println(output.trim()); }
							}
							try { process.destroyForcibly(); } //older OSes/powershells may not exit when the script is done. 
							catch (Exception e) { e.printStackTrace(); }
							AHAGUIHelpers.tryUpdateProgress(pm,100,100,"Done.");
							java.io.File f=new java.io.File(dirName+"/BinaryAnalysis.csv");
							if (f.exists())
							{
								java.util.Date date = new java.util.Date();
								String today= new java.text.SimpleDateFormat("yyyy-MM-dd-hhmm").format(date);
								System.out.println("calculated date as '"+today+"'");
								String newFileName="BinaryAnalysis"+today+".csv";
								java.nio.file.Files.copy(java.nio.file.Paths.get(dirName+"/BinaryAnalysis.csv"), java.nio.file.Paths.get(newFileName), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
								model().m_inputFileName=newFileName;
								openfileOrReload(true);
							}
						}
						catch (Exception e) { e.printStackTrace(); }
					}
					else { System.err.println("AHAController: ActionPerformed: Unknown action command='"+actionCommand+"'"); }
				}
			}); 
		}
	}
	
	protected void updateSidebar(AHANode node, boolean occuredFromMouseOver)
	{
		m_backgroundExec.execute(new Runnable() 
		{
			public void run()
			{
				if ( node==null || (occuredFromMouseOver && !m_gui.m_infoPnlUpdateOnMouseover.isSelected()) ) { return; } //if element is null, or triggered from mosueover and we're presently supposed to ignore that, return
				m_currentlyDisplayedNode.set(node);
				Object[][] infoData=node.getSidebarAttributes("aha.SidebarGeneralInfo"), intPorts=node.getSidebarAttributes("aha.SidebarInternalPorts"), extPorts=node.getSidebarAttributes("aha.SidebarExternalPorts");
				Object[][] connectionData={{"None",""}}, scoreReasons=null, signData=node.getSidebarAttributes("aha.SidebarSigningInfo"), stampData=node.getSidebarAttributes("aha.SidebarTStampInfo");
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

				final Object[][][] data={infoData,intPorts,extPorts,connectionData,scoreReasons, signData, stampData}; // create final pointer to pass to swing.infokelater. as long as this order of these object arrays is correct, everything will work :)
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
		});
		
	}
	
	protected void moveExternalNodes(AHAModel m)
	{
		//System.out.printf("Size at time of layout: height=%d width=%d\n", m_gui.m_graphViewPanel.getHeight(), m_gui.m_graphViewPanel.getWidth());
		long time=System.currentTimeMillis();
		String layoutMode=m_layoutMode.get();
		if (layoutMode.equalsIgnoreCase("naiveBox")) { naiveBoxLayout(m); }
		else if (layoutMode.equalsIgnoreCase("test1")) { layoutTest1(m); }
		else if (layoutMode.equalsIgnoreCase("test2")) { layoutTest2(m); }
		else { autoLayoutAlg(m); } //default case
		System.out.printf("Layout alg=%s took %sms\n", layoutMode, (System.currentTimeMillis()-time));
	}
	
	private void layoutTest1(AHAModel m) { System.out.println("Layout Test 1 not implemented yet."); }
	
	private void layoutTest2(AHAModel m) { System.out.println("Layout Test 2 not implemented yet."); }
	
	private void autoLayoutAlg(AHAModel m)
	{
		try 
		{
			AHAGraph g=m.m_graph;
			m_gui.m_graphViewPanel.getCamera().setAutoFitView(true);
			for (AHANode n : g)
			{
				n.graphNode.removeAttribute("xyz"); //setAttribute("xyz", 0,0,0);
			}
			Thread.sleep(100); 
			m_gui.m_graphViewer.enableAutoLayout();
			Thread.sleep(500); 
			m_gui.m_graphViewer.disableAutoLayout();
			Thread.sleep(100); 
			org.graphstream.ui.geom.Point3 hi=m_gui.m_graphViewPanel.getCamera().getMetrics().hi, lo=m_gui.m_graphViewPanel.getCamera().getMetrics().lo;
			m_gui.m_graphViewPanel.getCamera().setGraphViewport(lo.x, lo.y, hi.x, hi.y);
			
			java.util.ArrayList<AHANode> leftSideNodes=new java.util.ArrayList<>(1024);
			for (AHANode n : g)
			{
				if (n.getAttribute("aha.realextnode")!=null) { leftSideNodes.add(n); }
			}
			
			double numLeftNodes=leftSideNodes.size()+2; //1 is for main External node, 2 is so we dont put one at the very top or bottom
			leftSideNodes.add(leftSideNodes.size()/2, g.getNode("external"));
			
			int i=1;
			org.graphstream.ui.view.camera.Camera cam=m_gui.m_graphViewPanel.getCamera();
			for (AHANode n : leftSideNodes)
			{ 
				org.graphstream.ui.geom.Point3 loc=cam.transformPxToGu(60, (m_gui.m_graphViewPanel.getHeight()/numLeftNodes)*i);
				n.graphNode.setAttribute("xyz", loc.x,loc.y,loc.z);
				i++;
			}
			
			//System.out.printf("xlo=%f xhi=%f ylo=%f yhi=%f\n", lo.x, hi.x, lo.y, hi.y);
			m_gui.m_graphViewPanel.getCamera().setGraphViewport(lo.x, lo.y, hi.x, hi.y);
		} catch (Exception e) { e.printStackTrace(); }
	}
	
	private void naiveBoxLayout(AHAModel m)
	{
		try 
		{
			m_gui.m_graphViewer.disableAutoLayout();
			Thread.sleep(200); 
			double height=70, width=100;
			m_gui.m_graphViewPanel.getCamera().setBounds(0, 0, 0, width, height, 0);
			m_gui.m_graphViewPanel.getCamera().setGraphViewport(0, 0, width, height);
			m_gui.m_graphViewPanel.getCamera().setAutoFitView(false);
			
			java.util.ArrayList<AHANode> nodes=new java.util.ArrayList<>(1024), leftSideNodes=new java.util.ArrayList<>(1024);
			AHAGraph g=m.m_graph;
			for (AHANode n : g)
			{
				if (n.getAttribute("aha.realextnode")!=null) { leftSideNodes.add(n); }
				else if (n.getAttribute("aha.externalNode")==null) { nodes.add(n); } //we have to ignore the 'virtual external node' here too
			}
			
			java.util.Collections.sort(nodes, new java.util.Comparator<AHANode>() 
			{
        public int compare(AHANode lhs, AHANode rhs) { return Integer.compare(rhs.graphNode.getDegree(), lhs.graphNode.getDegree()); }
			});
			
			double restOfNodes=nodes.size(), side=Math.ceil(Math.sqrt(restOfNodes)), colwidth=(width/side);
			double numLeftNodes=leftSideNodes.size()+2; //1 is for main External node, 2 is so we dont put one at the very top or bottom
			leftSideNodes.add(leftSideNodes.size()/2, g.getNode("external"));
			{
				int i=1;
				for (AHANode n : leftSideNodes)
				{ 
					n.graphNode.setAttribute("xyz", colwidth/2,((double)height/(numLeftNodes)*i),0);
					//System.out.printf("Setting node=%s x=%f y=%f\n", n.getId(),colwidth/2,((double)100d/(numLeftNodes)*i));
					i++;
				}
			}
			
			java.util.Iterator<AHANode> it=nodes.iterator();
			//System.out.printf("side=%f colwidth=%f --------------------------------\n", side, colwidth);
			for (int col=1; col < side; col++)
			{
				for (int row=0; row < side; row++)
				{
					if (!it.hasNext()) { break; }
					AHANode n=it.next();
					if (n!=null)
					{
						double colPos=colwidth*col+colwidth/2, rowPos=((double)height/(side)*row)+(height/2d/side);
						if (col%2==1) { rowPos+=(height/4d/side); } // move every other row up slightly to keep labels from overlapping
						else { rowPos-=(height/4d/side); }
						n.graphNode.setAttribute("xyz", colPos,rowPos,0);
						//System.out.printf("Setting node=%s x=%f y=%f\n", n.getId(),colPos,rowPos);
					}
				}
			}
			
			m_gui.m_graphViewPanel.getCamera().setBounds(0, 0, 0, width, height, 0);
			m_gui.m_graphViewPanel.getCamera().setGraphViewport(0, 0, width, height);
			m_gui.m_graphViewPanel.getCamera().setViewCenter(width/2, height/2, 0);
			
			//org.graphstream.ui.geom.Point3 hi=m_gui.m_graphViewPanel.getCamera().getMetrics().hi, lo=m_gui.m_graphViewPanel.getCamera().getMetrics().lo;
			//System.out.printf("xlo=%f xhi=%f ylo=%f yhi=%f\n", lo.x, hi.x, lo.y, hi.y);
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
	
	public synchronized void buttonPushed(String id) { pushOrRelease(id,true); } //called when you click on a graph node/edge
	public synchronized void buttonReleased(String id) { pushOrRelease(id,false); }
	
	private synchronized void pushOrRelease(String id, boolean pushed)
	{
		if (id==null || id.equals("")) { return; }
		final AHANode node=model().m_graph.getNode(id);
		if (node==null) { System.out.println("node is null, returning early"); return;}
		m_backgroundExec.execute(new Runnable() {
			public void run()
			{
				try 
				{
					for (org.graphstream.graph.Edge e : node)
					{
						try 
						{
							String currentClasses=(String)e.getAttribute("ui.class");
							if (currentClasses==null) { currentClasses=""; }
							if (pushed && !currentClasses.contains("clickedAccent")) { e.setAttribute("ui.class", "clickedAccent, "+currentClasses); } 
							if (!pushed) { e.setAttribute("ui.class", currentClasses.replaceAll("clickedAccent, ", "")); } 
						} catch (Exception ex) { ex.printStackTrace(); }
					}
					updateSidebar(node, false);
				} catch (Exception e) { e.printStackTrace(); }
			}
		});
	}
	
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
	
	
	public AHAController(String args[])
	{ 
		m_settings=new AHASettings(args); // construct settings object from defaults, overridden by user settings on disk (if exists), overridden by any command line arguments
		System.setProperty("apple.laf.useScreenMenuBar", Boolean.toString(!m_settings.getBooleanProperty("forcejmenu"))); //FYI OpenJDK8 on macOS does not seem to do the correct MenuBar thing regardless
		if (!m_settings.getBooleanProperty("noforcescale")) { System.setProperty("sun.java2d.uiScale", "100%"); } //sad little hack to work around current issues on HiDPI machines 
		if (!m_settings.getBooleanProperty("notheme")) { AHAGUIHelpers.applyTheme(m_settings.getDefaultFont()); }
		
		for (String s : args)
		{
			try
			{
				String[] argTokens=s.split("=");
				if (argTokens[0]==null) { continue; }
				if (argTokens[0].equalsIgnoreCase("--updateFile")) { FileUpdater.updateCSVFileWithRemoteVulnDBData(null, null, m_settings); return; } 
				if (argTokens[0].equalsIgnoreCase("--getenv")) 
				{
					java.util.Properties p =System.getProperties();
					for ( java.util.Map.Entry<Object,Object> e: p.entrySet())
					{
						System.out.printf("key='%s' value='%s'\n",e.getKey(), e.getValue());
					}
					ClassLoader cl = ClassLoader.getSystemClassLoader();
			    java.net.URL[] urls = ((java.net.URLClassLoader)cl).getURLs();

			    for(java.net.URL url: urls){ System.out.println(url.getFile()); }
				}
				if (argTokens[0].toLowerCase().contains("help")||argTokens[0].equals("?"))
				{
					System.out.println
					(
							"Possible arguments are as follows:\n"+
							"--debug : print tons of additional information for debugging use to console while running\n"+
							"--verbose : print additional information to console while running (but less than debug)\n"+
							"--single : use single lines between nodes with multiple connections\n"+
							"--bigfont : use 18pt font instead of the default 12pt font (good for demos). Will have no effect if used with --notheme\n"+
							"--forceJMenu : on macs, force use of normal JMenu rather than using the traditional macOS menubar at the top of the screen\n"+
							"--notheme : attempt to minimize theming information set on gui components so that the OS theme will be used. Unsupported / components may be oddly sized.\n"+
							"--noforcescale : ignore the app attempting to force the scale down to 100% to avoid several existing graphstream bugs. \n"+
							"--updateFile : update a given inputFile with information about attacksurface/threats from internet sources (presently aDolus)\n"+
							"verboisty=[number] : use the specified positive numeric integer as the debug level\n"+
							"scorefile=scorefile.csv : use the scorefile specified after the equals sign\n"+
							"inputFile=inputFile.csv : use the inputFile specified after the equals sign\n"+
							"credsFile=inputFile.csv : use the credsFile for the credentials to update the input file (used with --updateFile) specified after the equals sign\n"
					); return;
				}
			} catch (Exception e) { e.printStackTrace(); }
		}
		
		// In the case that we're not exiting due to one of the args above, then construct the objects, GUI, etc.
		m_model.set(new AHAModel(this, m_settings));
		m_gui=new AHAGUI(m_model.get(), this, !m_settings.getBooleanProperty("single"));
	}
	
}
