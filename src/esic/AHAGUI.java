package esic;
//Copyright 2018 ESIC at WSU distributed under the MIT license. Please see LICENSE file for further info.

import java.awt.AWTKeyStroke;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.Properties;
import javax.swing.*;

public class AHAGUI extends JFrame
{
	protected org.graphstream.ui.view.Viewer m_graphViewer=null;
	protected org.graphstream.ui.swing_viewer.ViewPanel m_graphViewPanel=null;
	protected org.graphstream.ui.view.ViewerPipe m_graphViewPump=null;
	private JSplitPane m_mainContentSplitPane=null;
	
	private AHAController m_controller=null;
	protected JLabel m_btmPnlSearchStatus=new JLabel("");
	protected JCheckBoxMenuItem m_infoPnlUpdateOnMouseover=new JCheckBoxMenuItem("Update on MouseOver",false), m_infoPnlShowOnlyMatchedMetrics=new JCheckBoxMenuItem("Show Only Matched Metrics",true), m_infoPnlShowScoringSpecifics=new JCheckBoxMenuItem("Show Score Metric Specifics",false);
	protected JTextField m_btmPnlSearch=new JTextField("Search...");
	private boolean m_multilineGraph=false;
	private JPanel m_topLeftOverlay=new JPanel();
	private JScrollPane m_inspectorPanel=null;
	private final int m_menuShortcutKey;
	protected final String[][] m_infoPnlColumnHeaders={{"Info"},{"Open Internal Port", "Proto"},{"Open External Port", "Proto"},{"Connected Process", "PID"}, {"Score Metric", "Value"}}; //right now things would break if the number of these ever got changed at runtime, so made static.
	private final String[][] m_infoPnlColumnTooltips={{"Info"},{"Port that is able to be connected to from other processes internally.", "Protocol in use."},{"Port that is able to be connected to from other external hosts/processes.", "Protocol in use."},{"Names of processes connected to this one", "Process Identifier"}, {"The scoring metric checked against.", "Result of the checked metric."}};
	protected final JTable[] m_infoPnlTables= new JTable[m_infoPnlColumnHeaders.length]; //if you need more tables just add another column header set above
	private final JLabel[] m_overlayLabels= new JLabel[4]; 
	private final java.util.concurrent.atomic.AtomicReference<Thread> m_graphRefreshThread=new java.util.concurrent.atomic.AtomicReference<>(null);
	private int m_preferredInfoPnlWidth=270;
	private java.util.Vector<JMenuItem> defaultTrue=new java.util.Vector<>(), defaultFalse=new java.util.Vector<>();
	private Properties m_props;
	
	protected static java.awt.Dimension s_preferredTotalSize=new java.awt.Dimension(1920, 1200); //TODO: make this a pref
	public final static String s_settingsFileName="AHAGUI.settings";
	
	@SuppressWarnings("deprecation") //we have to use the old'n'busted version here for java8 compatibility
	private static int getMenuShortcutKeyMask() { return java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMask(); }
	
	public AHAGUI(AHAModel model, AHAController controller, boolean useMultiLineGraph, Properties props)
	{
		m_props=props;
		m_controller=controller;
		m_multilineGraph=useMultiLineGraph;
		long time=System.currentTimeMillis();
		m_menuShortcutKey=getMenuShortcutKeyMask();
		setBackground(java.awt.Color.BLACK); //do this first to reduce a bit of flicker on load/reload of the frame
		System.err.println("Starting GUI Construction.");
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		addWindowListener(m_controller);

		pack(); //populate all the info about the insets
		java.awt.Dimension screenSize = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
		if (screenSize.width < s_preferredTotalSize.width) { s_preferredTotalSize.width=screenSize.width; System.out.println("Screen size limits width. Changing width to "+screenSize.width); }
		if (screenSize.height-40 < s_preferredTotalSize.height) { s_preferredTotalSize.height=screenSize.height-40; System.out.println("Screen size limits height. Changing height to "+(screenSize.height-40));}
		setSize(s_preferredTotalSize);
		setPreferredSize(s_preferredTotalSize);
		setMinimumSize(new java.awt.Dimension(800,600));
		String title="AHA-GUI";
		try { title=AHAGUI.class.getPackage().getImplementationVersion().split(" B")[0]; } catch (Exception e) {}
		setTitle(title); //This should result in something like "AHA-GUI v0.5.6b1" being displayed

		java.awt.Font boldFont=m_btmPnlSearchStatus.getFont().deriveFont(java.awt.Font.BOLD);
		m_topLeftOverlay.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT,2,2));
		{
			for (int i=0;i<m_overlayLabels.length;i++)
			{
				m_overlayLabels[i]=new JLabel(" ");
				m_overlayLabels[i].setFont(boldFont);
				m_overlayLabels[i].setForeground(java.awt.Color.BLACK);
				m_overlayLabels[i].setOpaque(true);
				m_topLeftOverlay.add(m_overlayLabels[i]);
			}
			m_overlayLabels[0].setBackground(java.awt.Color.GREEN);
			m_overlayLabels[1].setBackground(java.awt.Color.YELLOW);
			m_overlayLabels[2].setBackground(java.awt.Color.ORANGE);
			m_overlayLabels[3].setBackground(java.awt.Color.RED);
			m_topLeftOverlay.setOpaque(false);
		}
		m_topLeftOverlay.setToolTipText("Attacksurface rating. Green is best, Red is worst.");

		final JMenuBar menuBar = new JMenuBar();
		{ // Menubar code
			
			JMenu fileMenu=new JMenu(" File "), viewMenu=new JMenu(" View "), windowMenu=new JMenu(" Window ");
			menuBar.add(fileMenu);
			menuBar.add(viewMenu);
			menuBar.add(windowMenu);
			setJMenuBar(menuBar);

			// -- begin file menu --
			AHAGUIHelpers.createMenuItem(new JMenuItem("Open..."), m_controller, "openNewFile", "Open a new file", fileMenu,KeyStroke.getKeyStroke(KeyEvent.VK_O, m_menuShortcutKey), null);
			AHAGUIHelpers.createMenuItem(new JMenuItem("Show Data View"), m_controller, "dataView", "Shows the list of listening processes to aid in creation of firewall rules.", fileMenu,KeyStroke.getKeyStroke(KeyEvent.VK_D, m_menuShortcutKey), null);
			AHAGUIHelpers.createMenuItem(new JMenuItem("Run AHA-Scraper..."), m_controller, "runAHAScraper", "Run AHA-Scraper on this machine", fileMenu,KeyStroke.getKeyStroke(KeyEvent.VK_S , m_menuShortcutKey | KeyEvent.SHIFT_DOWN_MASK), null);
			fileMenu.addSeparator();
			AHAGUIHelpers.createMenuItem(new JMenuItem("Update File..."), m_controller, "updateFileFromRemoteDB", "Update the currently loaded file with information from remote databases such as aDolus.", fileMenu, KeyStroke.getKeyStroke(KeyEvent.VK_U, m_menuShortcutKey), null);
			fileMenu.addSeparator();
			AHAGUIHelpers.createMenuItem(new JMenuItem("Preferences"), m_controller, "showPrefsPane", "Show AHA-GUI preferences", fileMenu, KeyStroke.getKeyStroke(KeyEvent.VK_COMMA, m_menuShortcutKey), null);
			fileMenu.addSeparator();
			AHAGUIHelpers.createMenuItem(new JMenuItem("Exit"), m_controller, "exit", "Exit AHA-GUI", fileMenu, null, null);

			// -- begin view menu --
			AHAGUIHelpers.createMenuItem(new JCheckBoxMenuItem("Use DNS Names"), m_controller, "showFQDN", "Show the DNS names of external nodes rather than IPs.", viewMenu, null, defaultFalse);
			AHAGUIHelpers.createMenuItem(new JCheckBoxMenuItem("Use Custom ScoreFile"), m_controller, "useCustom", "If a custom score file was loaded, this option will apply those custom directives to the graph view.", viewMenu, null, defaultFalse);

			viewMenu.addSeparator();
			AHAGUIHelpers.createMenuItem(m_infoPnlShowOnlyMatchedMetrics, m_controller, "refreshInfoPanel", "Only displays metrics which were matched, for example if ASLR was true.", viewMenu, null, defaultTrue);
			AHAGUIHelpers.createMenuItem(m_infoPnlShowScoringSpecifics, m_controller, "refreshInfoPanel", "Shows the specific metric in the inspector above that matched.", viewMenu, null, defaultFalse);
			AHAGUIHelpers.createMenuItem(m_infoPnlUpdateOnMouseover, m_controller, "refreshInfoPanel", "Enable change of the inspector above on hovering over nodes in addition to clicking.", viewMenu, null, defaultFalse);
			
			viewMenu.addSeparator();
			AHAGUIHelpers.createMenuItem(new JCheckBoxMenuItem("Show Virtual External Node"), m_controller, "aha.graphlayer==aha.virtextnode", "Shows the main 'External' node from the graph that all nodes which listen on externally accessible addresses connect to.",viewMenu, null, defaultTrue);
			AHAGUIHelpers.createMenuItem(new JCheckBoxMenuItem("Show Real External Nodes"), m_controller, "aha.graphlayer==aha.realextnode", "Shows the main 'External' node from the graph that all nodes which listen on externally accessible addresses connect to.",viewMenu, null, defaultTrue);
			AHAGUIHelpers.createMenuItem(new JCheckBoxMenuItem("Show Connectionless Nodes", true), m_controller, "aha.graphlayer==proto.none", "Show / Hide nodes with no protocol in the graph.", viewMenu,null, defaultTrue);
			AHAGUIHelpers.createMenuItem(new JCheckBoxMenuItem("Show Operating System Processes"), m_controller, "hideOSProcs", "Hides the usual Windows™ operating system processes, while interesting these processes can get in the way of other analysis.", viewMenu, null, defaultTrue);
			AHAGUIHelpers.createMenuItem(new JCheckBoxMenuItem("Show Pipe", true), m_controller, "aha.graphlayer==proto.pipe", "Show / Hide Pipe protocol nodes in the graph.", viewMenu, null, defaultTrue);
			AHAGUIHelpers.createMenuItem(new JCheckBoxMenuItem("Show TCP", true), m_controller, "aha.graphlayer==proto.tcp", "Show / Hide TCP protocol nodes in the graph.", viewMenu, null, defaultTrue);
			AHAGUIHelpers.createMenuItem(new JCheckBoxMenuItem("Show UDP", true), m_controller, "aha.graphlayer==proto.udp", "Show / Hide UDP protocol nodes in the graph.", viewMenu, null, defaultTrue);
			
			viewMenu.addSeparator();
			ButtonGroup scoreButtonGroup=new ButtonGroup();
			scoreButtonGroup.add(AHAGUIHelpers.createMenuItem(new JRadioButtonMenuItem("Normal Score Method", true), m_controller, "scoreMethod-0", "Use the default scoring method", viewMenu, null, defaultTrue));
			scoreButtonGroup.add(AHAGUIHelpers.createMenuItem(new JRadioButtonMenuItem("WorstCommonProc Score Method (beta)"), m_controller, "scoreMethod-1", "Use the WorstCommonProc scoring method (beta)", viewMenu, null, null));
			scoreButtonGroup.add(AHAGUIHelpers.createMenuItem(new JRadioButtonMenuItem("Relative Score Method (beta)"), m_controller, "scoreMethod-2", "Use the RelativeScore scoring method (beta)", viewMenu, null, null));

			viewMenu.addSeparator();
			ButtonGroup layoutButtonGroup=new ButtonGroup();
			layoutButtonGroup.add(AHAGUIHelpers.createMenuItem(new JRadioButtonMenuItem("Default Autolayout"), m_controller, "layoutMethod-autolayout", "Use the default graph layout method", viewMenu, null, defaultTrue));
			layoutButtonGroup.add(AHAGUIHelpers.createMenuItem(new JRadioButtonMenuItem("NaiveBox Autolayout"), m_controller, "layoutMethod-naiveBox", "Naive box layout, attempt to spread all nodes out on an XY grid evenly", viewMenu, null, null));
			layoutButtonGroup.add(AHAGUIHelpers.createMenuItem(new JRadioButtonMenuItem("TestLayout1"), m_controller, "layoutMethod-test1", "Reserved menu spot to test future beta layouts", viewMenu, null, null));
			layoutButtonGroup.add(AHAGUIHelpers.createMenuItem(new JRadioButtonMenuItem("TestLayout2"), m_controller, "layoutMethod-test2", "Reserved menu spot to test future beta layouts", viewMenu, null, null));

			
			// -- begin window menu --
			AHAGUIHelpers.createMenuItem(new JMenuItem("Reset Zoom"), m_controller, "resetZoom", "Resets the zoom of the graph view to default.", windowMenu,KeyStroke.getKeyStroke(KeyEvent.VK_R, m_menuShortcutKey), null);
		}

		JPanel bottomPanel=new JPanel();
		{
			bottomPanel.setLayout(new java.awt.GridBagLayout());
			java.awt.GridBagConstraints gbc=new java.awt.GridBagConstraints();
			gbc.fill=java.awt.GridBagConstraints.HORIZONTAL;
			gbc.gridx=0; gbc.gridy=0; gbc.anchor=java.awt.GridBagConstraints.WEST; gbc.weightx=10; gbc.insets=new java.awt.Insets(0,2,0,0); gbc.gridwidth=java.awt.GridBagConstraints.REMAINDER;
			m_btmPnlSearch.addFocusListener(new java.awt.event.FocusListener()
			{
				public void focusGained(java.awt.event.FocusEvent e)
				{
					String txt=m_btmPnlSearch.getText();
					if (txt!=null && txt.equals("Search...")) { m_btmPnlSearch.setText(""); }
				}
				public void focusLost(java.awt.event.FocusEvent e)
				{
					String txt=m_btmPnlSearch.getText();
					if (txt!=null && txt.equals("")) { m_btmPnlSearch.setText("Search..."); }
				}
			});
			m_btmPnlSearch.addActionListener(m_controller);
			m_btmPnlSearch.setActionCommand("search");
			m_btmPnlSearch.setToolTipText(AHAGUIHelpers.styleToolTipText("Search\nTo emphasize nodes you're looking for:\nex: processname==svchost.exe\nwill highlight all nodes and connections\nYou can also highlight all nodes except the search term by inverting the search:\nex: processname!=svchost.exe\nwill highlight everything that is not svchost.exe\n\n To hide nodes prepend '~' ex: ~processname==unknown\n\n you can also create complex searches using the || symbol\n ex: processname==svchost.exe || ~processname==unknown"));
			gbc.insets=new java.awt.Insets(0,1,1,1);
			gbc.gridwidth=1;
			gbc.weightx=1000;
			bottomPanel.add(m_btmPnlSearch, gbc);
			gbc.gridx++;
			gbc.weightx=0;
			gbc.insets=new java.awt.Insets(0,0,0,0);
			bottomPanel.add(m_btmPnlSearchStatus, gbc);
			bottomPanel.setBorder( new javax.swing.border.MatteBorder(1, 0, 0, 0, java.awt.Color.GRAY));
		}
		
		ToolTipManager.sharedInstance().setDismissDelay(Integer.MAX_VALUE);
		ToolTipManager.sharedInstance().setInitialDelay(500);
		
		setLayout(new java.awt.BorderLayout(1,0));
		String[][][] initialData={{{"None"}},{{"None"}},{{"None"}},{{"None"}},{{"None"}},}; //digging this new 3d array literal initializer: this is a String[5][1][1] where element[i][0][0]="None".
		m_inspectorPanel=AHAGUIHelpers.createTablesInScrollPane(m_infoPnlColumnHeaders, m_infoPnlColumnTooltips, initialData, m_infoPnlTables, new int[]{160,40});

		m_topLeftOverlay.setBounds(0, 0, m_topLeftOverlay.getPreferredSize().width, m_topLeftOverlay.getPreferredSize().height);
		initGraphView(model);
		if (m_inspectorPanel.getPreferredSize().width>m_preferredInfoPnlWidth) { m_preferredInfoPnlWidth=m_inspectorPanel.getPreferredSize().width+20; }
		m_mainContentSplitPane=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, m_graphViewPanel, m_inspectorPanel);
		{
			m_mainContentSplitPane.setDividerSize(2);
			m_mainContentSplitPane.setBorder(null);
			m_mainContentSplitPane.setDividerLocation(getContentPane().getSize().width-m_preferredInfoPnlWidth-m_mainContentSplitPane.getDividerSize());
			m_mainContentSplitPane.setResizeWeight(1);
			m_mainContentSplitPane.setContinuousLayout(true); //update the split view while dragging the divider
		}
		add(m_mainContentSplitPane, java.awt.BorderLayout.CENTER);
		add(bottomPanel, java.awt.BorderLayout.SOUTH);

		getRootPane().setBorder(BorderFactory.createMatteBorder(0, 2, 2, 2, java.awt.Color.DARK_GRAY));
		setVisible(true);
		AHAGUIHelpers.tryCancelSplashScreen();
		System.err.println("Finished Laying out GUI. Elapsed time="+(System.currentTimeMillis()-time)+"ms");

		java.io.File testInputFile=AHAModel.getFileAtPath(model.m_inputFileName);
		if ( model.m_inputFileName==null || model.m_inputFileName.equals("") || testInputFile==null || !testInputFile.exists() ) { openFile(model, title); }
	}
	
	protected void initGraphView(AHAModel model)
	{
		System.setProperty("org.graphstream.ui", "swing");
		stopGraphRefreshThread();
		org.graphstream.ui.view.Viewer oldGraphViewer=m_graphViewer;
		
		if (m_multilineGraph) { model.m_graph.graph = new org.graphstream.graph.implementations.MultiGraph("MultiGraph", false, false, 512, 2048); }
		else { model.m_graph.graph = new org.graphstream.graph.implementations.SingleGraph("SingleGraph", false, false, 512, 2048); }

		model.m_graph.graph.setAttribute("ui.stylesheet", model.styleSheet);
		model.m_graph.graph.setAttribute("layout.gravity", 0.025);//0.000001); 
		model.m_graph.graph.setAttribute("layout.quality", 4);
		model.m_graph.graph.setAttribute("layout.stabilization-limit", 0.95);
		model.m_graph.graph.setAttribute("ui.antialias", true); //enable anti aliasing (looks way better this way)
		model.m_graph.graph.setAutoCreate(true);
		model.m_graph.graph.setStrict(true);
		m_graphViewer = new org.graphstream.ui.swing_viewer.SwingViewer(model.m_graph.graph, org.graphstream.ui.view.Viewer.ThreadingModel.GRAPH_IN_ANOTHER_THREAD);
		m_graphViewPanel=(org.graphstream.ui.swing_viewer.ViewPanel)m_graphViewer.addDefaultView(false);
		if (m_mainContentSplitPane!=null) 
		{
			m_mainContentSplitPane.setBackground(java.awt.Color.black);
			int loc=m_mainContentSplitPane.getDividerLocation();
			m_mainContentSplitPane.setLeftComponent(m_graphViewPanel);
			m_mainContentSplitPane.setDividerLocation(loc);
		}
		m_graphViewer.enableAutoLayout();

		addMouseWheelListener(m_controller);
		org.graphstream.ui.view.util.MouseManager mouseManager=new AHAGUIMouseAdapter(500,m_controller);
		m_graphViewPanel.setMouseManager(null);
		m_graphViewPanel.setMouseManager(mouseManager);
		((java.awt.FlowLayout)m_graphViewPanel.getLayout()).setAlignment(java.awt.FlowLayout.LEFT);
		m_graphViewPanel.add(m_topLeftOverlay);
		
		m_graphRefreshThread.set(new Thread("GraphViewEventPump"){ //Don't do this until after we explore and score, to reduce odd concurrency errors that seem to occur
			public void run()
			{
				while (!Thread.interrupted())
				{
					try
					{
						int pumpErrs=0;
						m_graphViewPump=m_graphViewer.newViewerPipe();
						m_graphViewPump.addViewerListener(m_controller);
						m_graphViewPump.addSink(model.m_graph.graph);
						while (!Thread.interrupted())
						{
							try { m_graphViewPump.blockingPump(); }
							catch (InterruptedException ie) { throw ie; }
							catch (Exception e) { e.printStackTrace(); if (++pumpErrs > 5) { e.printStackTrace(); System.err.println("Breaking loop due to pump errors."); break; } }
						}
					}
					catch (InterruptedException ie) { System.err.println("Graph refresh thread exiting."); return; }
					catch (Exception e) { e.printStackTrace();}
				}
				System.err.println("Graph refresh thread exiting, possibly unexpectedly?");
			}
		});
		m_graphRefreshThread.get().start();
		try { if (oldGraphViewer!=null) { oldGraphViewer.close(); } }
		catch (Exception e) {}
		resetUIToDefault();
	}
	
	private void stopGraphRefreshThread()
	{
		Thread t=m_graphRefreshThread.getAndSet(null);
		try {    if (t!=null) {t.interrupt();}    }
		catch(Exception ex) {ex.printStackTrace(); }
	}
	
	private void setMenuElementTo(JMenuItem item, boolean value)
	{
		try
		{
			if (item instanceof JCheckBoxMenuItem) { ((JCheckBoxMenuItem)item).setSelected(value); }
			else if (item instanceof JRadioButtonMenuItem) { ((JRadioButtonMenuItem)item).setSelected(value); }
		} catch (Exception e) { e.printStackTrace(); }
	}
	
	private void resetUIToDefault()
	{
		for (JMenuItem i : defaultFalse) { setMenuElementTo(i,false); }
		for (JMenuItem i : defaultTrue) { setMenuElementTo(i,true); }
		m_btmPnlSearch.setText("Search...");
		m_btmPnlSearchStatus.setText("");
		m_controller.m_layoutMode.set("autolayout");
	}
	
	protected boolean openFile (AHAModel model, String baseTitle)
	{	//should either open in PWD, directory of previously opened file, or homedir if path evaluates to null.
		System.out.println("inputFileName='"+model.m_inputFileName+"'");
		try
		{ //try to prevent confusing the user by launching them into their home directory rather than the AHAGUI directory if their filename is bad
			if (model.m_inputFileName==null || !new java.io.File(model.m_inputFileName).exists()) { model.m_inputFileName=""; }
		} catch (Exception e) {}
		JFileChooser fc=new JFileChooser(AHAModel.getFileAtPath(model.m_inputFileName).getAbsolutePath()); 
		fc.setDialogTitle("Select Input File");
		fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Comma Separated Value","csv"));
		if (fc.showOpenDialog(this)==JFileChooser.APPROVE_OPTION)
		{ 
			try
			{
				java.io.File selectedFile=fc.getSelectedFile();
				if (selectedFile!=null) 
				{ 
					model.m_inputFileName=selectedFile.getAbsolutePath();
					System.err.println("User selected filename="+model.m_inputFileName);
					baseTitle+=" "+selectedFile.getName();
					setTitle(baseTitle); //This should result in something like "AHA-GUI v0.5.6b1" along with the filename being displayed
					return true;
				}
			} catch(Exception e) { e.printStackTrace(); }
		}
		return false;
	}
	
	protected void updateOverlayLegendScale(int maxScore)
	{ 
		SwingUtilities.invokeLater(new Runnable() //perform task on gui thread
		{
			public void run()
			{
				m_overlayLabels[0].setText(" Best >"+(int)(0.75*maxScore)+" ");
				m_overlayLabels[1].setText(" >"+(int)(0.50*maxScore)+" ");
				m_overlayLabels[2].setText(" >"+(int)(0.25*maxScore)+" ");
				m_overlayLabels[3].setText(" Worst ");
			}
		});
	}
	
	protected Object synch_dataViewLock=new Object();
	protected JFrame synch_dataViewFrame=null;
	protected void showDataView(AHAModel model, AHAGUI parent) //shows the window that lists the listening sockets 
	{
		synchronized (synch_dataViewLock)
		{
			if (synch_dataViewFrame==null)
			{
				synch_dataViewFrame=new JFrame("Data View")
				{
					{ //adding a menubar is the easiest way to make it so we can dismiss the frame via the keyboard. eventually we should probably add more keyboard features to the data view
						final JMenuBar menuBar = new JMenuBar();
						JMenu fileMenu=new JMenu(" File ");
						menuBar.add(fileMenu);
						setJMenuBar(menuBar);
						JFrame self=this;
						java.awt.event.ActionListener al=new java.awt.event.ActionListener() 
						{
							public void actionPerformed(java.awt.event.ActionEvent e) { self.setVisible(false); } //in the case that we end up with more keyboard accelerators, we'll need to improve this function
						};
						AHAGUIHelpers.createMenuItem(new JMenuItem("Close Window"), al, "close", "Close the Data View Window", fileMenu,KeyStroke.getKeyStroke(KeyEvent.VK_W, m_menuShortcutKey), null);
						
						setBackground(java.awt.Color.black);
						setLayout(new java.awt.BorderLayout());
						setSize(new java.awt.Dimension(parent.getSize().width-40,parent.getSize().height-40));
						setLocation(parent.getLocation().x+20, parent.getLocation().y+20); //move it down and away a little bit so people understand it's a new window
						getRootPane().setBorder(new javax.swing.border.LineBorder(java.awt.Color.GRAY,2));
						JTabbedPane tabBar=new JTabbedPane();
						tabBar.setBorder(BorderFactory.createEmptyBorder());
						add(tabBar, java.awt.BorderLayout.CENTER);
						{ // Find data for, create table, etc for the "Graph Data" view
							AHAModel.TableDataHolder t=model.generateReport();
							String[][] columTooltips= {{"Global data from the scan which took place.", "The result for this metric."},{"The name of the process.","Process ID of the process.", "User under which the process is running.", "The number of connections this process has.", "The number of ports this process has opened that external hosts/processes could connect to.", "Whether or not this process is codesigned. Code signing is recomended and allows executalbes to be authenticated as genuine.", "Address Space Layout Randomization is a recomended security feature which helps to reduce succeptability to malicious attacks.", "Data Execution Prevention is a recomended security feature which ensures that areas of memory which are writable (and could have code stored to by an attacker) are not executable.", "Control Flow Guard is a recomended security feature which helps prevent attackers from subverting normal code execution, reducing ease of attack.", "HiVA is an improved ASLR with additional etropy to further complicate any possible attacks.", "This is the score granted to the process by the 'Normal' scoring methodology which uses the MetricsTable.cfg to determine the score.","This is a beta scoring method.","This is a beta scoring method."}};
							tabBar.add("Vulnerability Metrics", AHAGUIHelpers.createTablesInScrollPane(t.columnNames, columTooltips, t.tableData, new JTable[t.tableData.length], new int[]{180,40,200,86,80,50,44,44,44,44,44,44,60}) ); 
						}
						{ // Find data for, create table, etc for the "Listening Processes" tab
							JTable[] fwTables=new JTable[2];
							String[][] columnHeaders={{"Listening Internally", "PID", "Proto", "Address", "Port", "Connections"},{"Listening Externally", "PID", "Proto", "Address", "Port", "Connections"}};
							Object[][][] tableData=new Object[2][][];
							java.util.TreeMap<String,String> dataset=model.m_intListeningProcessMap;
							for (int i=0;i<2;i++)
							{
								java.util.TreeMap<String,Object[]> sortMe=new java.util.TreeMap<>();
								for (java.util.Map.Entry<String, String> entry : dataset.entrySet() )
								{
									String key=entry.getKey(), value=entry.getValue();
									String[] keyTokens=key.split("_"), valueTokens=value.split("_");
									Object strArrVal[]=new Object[6];
									AHAGraph.AHANode n=model.m_graph.getNode(value); //grab the node from the graph so we can use it for additional metrics 
									String addr=null;
									java.util.Vector<AHAModel.ConnectionEntry> connections=n.getConnectionEntryTable("allConnections");
									
									for (AHAModel.ConnectionEntry e : connections)
									{
										try
										{
											if (e.protocol!=null && e.protocol.equals(keyTokens[0].toLowerCase()) && e.localPort!=null && e.localPort.equals(keyTokens[1].toLowerCase())) 
											{ 
												addr=e.localAddr; 
												if (addr!=null) {  break; } //System.out.println("addr="+addr);
												System.err.println("FOUND, but addr was null anyway!?!?!?");
											}
										} catch (Exception ex) { ex.printStackTrace(); }
									}
									if (addr==null)
									{
										addr="";
										System.err.printf("Failed to find addr for proto=%s localport=%s\n",keyTokens[0], keyTokens[1]);
									}
									strArrVal[0]=valueTokens[0]; // process name
									strArrVal[1]=AHAModel.strAsInt(valueTokens[1]); //PID
									strArrVal[2]=keyTokens[0].toUpperCase(); //Protocol
									strArrVal[3]=addr; //the address of the process
									strArrVal[4]=AHAModel.strAsInt(keyTokens[1]); //Port
									strArrVal[5]=model.m_listeningPortConnectionCount.get(key); //Number of connections
									String newKey=valueTokens[0]+valueTokens[1]+"-"+keyTokens[0].toUpperCase()+keyTokens[1];
									sortMe.put(newKey, strArrVal);
								}
								Object[][] data = new Object[sortMe.size()][6];
								int j=0;
								for (Object[] lineDat : sortMe.values()) { data[j++]=lineDat; }
								tableData[i]=data;
								dataset=model.m_extListeningProcessMap;
							}
							String[][] columTooltips= {{"Processes which have open ports that can only be connected to by processes on this host.", "Process ID of the process.", "The protocol of this listening port, such as TCP or UDP.", "The port number.", "The number of connections to this open port."},{"Processes which have open ports that can be connected to by remote hosts/processes.", "Process ID of the process.", "The protocol of this listening port, such as TCP or UDP.", "The port number.", "The number of connections to this open port."}};
							tabBar.add("Listening Processes", AHAGUIHelpers.createTablesInScrollPane(columnHeaders,columTooltips, tableData, fwTables, new int[]{200,50,50,50})); 
						}
					}
				};
			}
			synch_dataViewFrame.setVisible(true);
		}
	}
	
	
	
	protected void showPrefsPane(AHAGUI parent) //shows the window that lists the listening sockets 
	{
		new JFrame("Preferences")
		{
			{ //adding a menubar is the easiest way to make it so we can dismiss the frame via the keyboard. eventually we should probably add more keyboard features to the data view
				JFrame self=this;
				setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
				setBackground(java.awt.Color.black);
				setSize(new java.awt.Dimension(600,600));
				setLocation(parent.getLocation().x+20, parent.getLocation().y+20); //move it down and away a little bit so people understand it's a new window
				getRootPane().setBorder(new javax.swing.border.LineBorder(java.awt.Color.GRAY,2));
				
				GridBagConstraints gc=new GridBagConstraints();
				getContentPane().setLayout(new GridBagLayout());

				gc.gridy=gc.gridx=0;
				gc.gridwidth=gc.gridheight=1;
				gc.weightx=1;
				gc.weighty=0;
				gc.fill=GridBagConstraints.HORIZONTAL;

				java.util.Set<AWTKeyStroke> forwardKeys = getFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS);
				java.util.Set<AWTKeyStroke> newForwardKeys = new java.util.HashSet<>(forwardKeys);
				newForwardKeys.add(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0));
				setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS,newForwardKeys);
				setFocusTraversalKeysEnabled(true);

				gc.gridwidth=1;
				gc.gridx=1;
				AHAGUIHelpers.addLabel(this, "Application behavior:", JLabel.LEFT, gc, AHAGUIHelpers.DirectionToIncrement.INC_Y);
				AHAGUIHelpers.addLabel(this, " ", JLabel.LEFT, gc, AHAGUIHelpers.DirectionToIncrement.INC_Y);
				
				gc.gridwidth=2;
				gc.gridx=1;
				JCheckBox verbose=AHAGUIHelpers.addCheckbox(self.getContentPane(), null, "Verbose", "", JLabel.LEFT, gc, AHAGUIHelpers.DirectionToIncrement.INC_Y);
				verbose.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS,newForwardKeys);
				if (m_props.getProperty("verbose","false").toLowerCase().contains("true")) { verbose.setSelected(true); }
				
				JCheckBox debug=AHAGUIHelpers.addCheckbox(self.getContentPane(), null, "Debug", "", JLabel.LEFT, gc, AHAGUIHelpers.DirectionToIncrement.INC_Y);
				debug.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS,newForwardKeys);
				if (m_props.getProperty("debug","false").toLowerCase().contains("true")) { debug.setSelected(true); }
				
				JCheckBox single=AHAGUIHelpers.addCheckbox(self.getContentPane(), null, "Single line graph", "", JLabel.LEFT, gc, AHAGUIHelpers.DirectionToIncrement.INC_Y);
				single.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS,newForwardKeys);
				if (m_props.getProperty("single","false").toLowerCase().contains("true")) { single.setSelected(true); }
				
				JCheckBox bigfont=AHAGUIHelpers.addCheckbox(self.getContentPane(), null, "Use Large Fonts", "", JLabel.LEFT, gc, AHAGUIHelpers.DirectionToIncrement.INC_Y);
				bigfont.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS,newForwardKeys);
				if (m_props.getProperty("bigfont","false").toLowerCase().contains("true")) { bigfont.setSelected(true); }
				
				JCheckBox forceJMenu=AHAGUIHelpers.addCheckbox(self.getContentPane(), null, "Force JMenu", "", JLabel.LEFT, gc, AHAGUIHelpers.DirectionToIncrement.INC_Y);
				forceJMenu.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS,newForwardKeys);
				if (m_props.getProperty("forcejmenu","false").toLowerCase().contains("true")) { forceJMenu.setSelected(true); }
				
				JCheckBox notheme=AHAGUIHelpers.addCheckbox(self.getContentPane(), null, "Disable Appearance Theming", "", JLabel.LEFT, gc, AHAGUIHelpers.DirectionToIncrement.INC_Y);
				notheme.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS,newForwardKeys);
				if (m_props.getProperty("notheme","false").toLowerCase().contains("true")) { notheme.setSelected(true); }
				
				JCheckBox noforcescale=AHAGUIHelpers.addCheckbox(self.getContentPane(), null, "Do not force Java UI scale to 100%", "", JLabel.LEFT, gc, AHAGUIHelpers.DirectionToIncrement.INC_Y);
				noforcescale.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS,newForwardKeys);
				if (m_props.getProperty("noforcescale","false").toLowerCase().contains("true")) { noforcescale.setSelected(true); }

				gc.gridwidth=1;
				gc.gridx=1;
				AHAGUIHelpers.addLabel(this, " ", JLabel.LEFT, gc, AHAGUIHelpers.DirectionToIncrement.INC_Y);
				AHAGUIHelpers.addLabel(this, " ", JLabel.LEFT, gc, AHAGUIHelpers.DirectionToIncrement.INC_Y);
				gc.gridwidth=1;
				gc.gridx=1;
				AHAGUIHelpers.addLabel(this, "Default file locations:", JLabel.LEFT, gc, AHAGUIHelpers.DirectionToIncrement.INC_Y);
				AHAGUIHelpers.addLabel(this, " ", JLabel.LEFT, gc, AHAGUIHelpers.DirectionToIncrement.INC_Y);
				
				gc.gridwidth=1;
				gc.gridx=0;
				AHAGUIHelpers.addLabel(this, "Default Input Filename ", JLabel.RIGHT, gc, AHAGUIHelpers.DirectionToIncrement.INC_X);
				gc.weightx=10;
				
				JTextField inputFile=AHAGUIHelpers.addTextfield(this, m_props.getProperty("inputfile",""), gc, AHAGUIHelpers.DirectionToIncrement.INC_Y);
				gc.weightx=1;
				inputFile.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS,newForwardKeys);
				gc.gridx=0;
				
				gc.gridwidth=1;
				gc.gridx=0;
				AHAGUIHelpers.addLabel(this, "Default Credentials Filename ", JLabel.RIGHT, gc, AHAGUIHelpers.DirectionToIncrement.INC_X);
				gc.weightx=10;
				gc.gridwidth=2;
				JTextField credsFile=AHAGUIHelpers.addTextfield(this, m_props.getProperty("credsfile",""), gc, AHAGUIHelpers.DirectionToIncrement.INC_Y);
				gc.weightx=1;
				credsFile.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS,newForwardKeys);
				gc.gridx=0;
				
				gc.gridwidth=1;
				gc.gridx=0;
				AHAGUIHelpers.addLabel(this, "Default Scorefile Filename ", JLabel.RIGHT, gc, AHAGUIHelpers.DirectionToIncrement.INC_X);
				gc.weightx=10;
				gc.gridwidth=2;
				JTextField scoreFile=AHAGUIHelpers.addTextfield(this, m_props.getProperty("scorefile",""), gc, AHAGUIHelpers.DirectionToIncrement.INC_Y);
				gc.weightx=1;
				scoreFile.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS,newForwardKeys);
				gc.gridx=0;
				
				gc.gridwidth=3;
				gc.gridx=0;
				gc.weighty=10;
				gc.fill=GridBagConstraints.BOTH;
				AHAGUIHelpers.addLabel(this, "", JLabel.LEFT, gc, AHAGUIHelpers.DirectionToIncrement.INC_Y);
				
				gc.weighty=0;
				gc.gridwidth=3;
				gc.fill=GridBagConstraints.HORIZONTAL;
				
				
				AHAGUIHelpers.addLabel(this, "Saved preferences are applied at next application launch.", JLabel.CENTER, gc, AHAGUIHelpers.DirectionToIncrement.INC_Y);
				JButton save=AHAGUIHelpers.addButton(this, null, "Save", "save", gc, AHAGUIHelpers.DirectionToIncrement.INC_Y);
				save.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS,newForwardKeys);
				
				save.addActionListener(new java.awt.event.ActionListener()
				{
          public void actionPerformed(java.awt.event.ActionEvent event)
          {
          	m_props.setProperty("verbose", Boolean.toString(verbose.isSelected()).toLowerCase());
          	m_props.setProperty("debug", Boolean.toString(debug.isSelected()).toLowerCase());
          	m_props.setProperty("single", Boolean.toString(single.isSelected()).toLowerCase());
          	m_props.setProperty("bigfont", Boolean.toString(bigfont.isSelected()).toLowerCase());
          	m_props.setProperty("forcejmenu", Boolean.toString(forceJMenu.isSelected()).toLowerCase());
          	m_props.setProperty("notheme", Boolean.toString(notheme.isSelected()).toLowerCase());
          	m_props.setProperty("noforcescale", Boolean.toString(noforcescale.isSelected()).toLowerCase());
          	
          	
          	if (inputFile.getText()!=null && !inputFile.getText().equals("")) { m_props.setProperty("inputfile", inputFile.getText()); }
          	if (credsFile.getText()!=null && !credsFile.getText().equals("")) { m_props.setProperty("credsfile", credsFile.getText()); }
          	if (scoreFile.getText()!=null && !scoreFile.getText().equals("")) { m_props.setProperty("scorefile", scoreFile.getText()); }
          	
          	try
          	{
          		m_props.store(new java.io.FileOutputStream(new File(s_settingsFileName)), "update settings ");
          	} catch (Exception e) { e.printStackTrace(); }
          	dispose();
          }
				});
				gc.gridx=0;
			}
		}.setVisible(true);
	}
	
	
	public static Properties loadProps (String fileName, String args[])
	{
		Properties props=new Properties();
		java.io.File settingsFile=new File(fileName);
		
		try
		{
			if (!settingsFile.exists()) { System.out.println("No settings file found, at "+fileName); }
			else { props.load(new java.io.FileInputStream(settingsFile)); }
		} catch (Exception e) { e.printStackTrace(); }
		
		java.util.TreeMap<String,String> argsMap=new java.util.TreeMap<>();
		for (String s : args) //grab the args and put them in something that makes it easier to check them without iteration later (i.e. a map)
		{
			try
			{
				String[] argTokens=s.split("=");
				if (argTokens[0]==null) { continue; }
				String key=argTokens[0].toLowerCase(), value="";
				if ( argTokens.length > 1 ) { value=argTokens[1]; }
				argsMap.put(key.replace("--", ""), value.replace("--", ""));
			} catch (Exception e) { e.printStackTrace(); }
		}
		
		try //LET'S GET READY TO SET SOME DEEEEFAAAAUUUULLLLTTTTZZZZ
		{
			String[] defaultFalse={"verbose", "debug", "single", "bigfont", "forcejmenu", "notheme", "noforcescale" };
			for ( String s : defaultFalse )
			{
				s=s.toLowerCase();
				String prop=props.getProperty(s);
				if (prop==null || prop.toLowerCase().contains("false")) { props.setProperty(s, "false"); }
				else { props.setProperty(s, "true"); }
				if (argsMap.get(s)!=null) { props.setProperty(s, "true"); }
			}
			
			String credsFile=props.getProperty("credsfile", "credentials.txt").trim(), inputFile=props.getProperty("inputfile", "BinaryAnalysis.csv").trim();
			if ( argsMap.get("credsfile")!=null ) { credsFile=argsMap.get("credsfile"); }
			if ( argsMap.get("inputfile")!=null ) { inputFile=argsMap.get("inputfile"); }
			props.setProperty("credsfile", credsFile);
			props.setProperty("inputfile", inputFile);
			if ( argsMap.get("scorefile")!=null ) //only deal with scoreFile if it's specified on the commandline, otherwise let whatever value we have lie (because null is the default)
			{
				props.setProperty("scorefile", argsMap.get("scorefile").trim());
			}
		} catch (Exception e) { e.printStackTrace(); }
		
		if (props.get("verbose").equals("true") || props.get("debug").equals("true") )
		{
			System.out.println("Read in settings:");
			for (java.util.Map.Entry<Object, Object> entry : props.entrySet()) { System.out.printf("key='%s' value='%s'\n", entry.getKey(), entry.getValue()); }
		}
		return props;
	}
	
	public static void main(String args[])
	{ 
		System.setProperty("sun.java2d.opengl","true"); //hopefully improve linux java perf //TODO if this works probably throw it behind a check for linuxy things. or at least nonwindows nonmacos things
		System.setProperty("swing.aatext","true");
		System.setProperty("org.graphstream.ui", "swing");
		System.out.println("JRE: Vendor="+System.getProperty("java.vendor")+", Version="+System.getProperty("java.version"));
		System.out.println("OS: Arch="+System.getProperty("os.arch")+" Name="+System.getProperty("os.name")+" Vers="+System.getProperty("os.version"));
		System.out.println("AHA-GUI Version: "+AHAGUI.class.getPackage().getImplementationVersion()+" starting up.");
		
		boolean useMultiLineGraph=true, applyTheme=true, force100percentScale=true, updateFile=false;
		String scoreFileName=null, inputFileName="", useAppleTopOfScreenMenubarIfApplicable="true", credentialsFileName="";
		java.awt.Font uiFont=new java.awt.Font(java.awt.Font.MONOSPACED,java.awt.Font.PLAIN,12);
		int verbosity=0;
		
		Properties props=null;
		try
		{
			props=loadProps(s_settingsFileName, args); //will return any defaults loaded, overriden by any cli args. since config is not saved until user clicks save, this is transient until they save, but will show currently used options when viewing settings
			if (props.getProperty("verbose").contains("true")) { verbosity=1; }
			if (props.getProperty("debug").contains("true")) { verbosity=5; }
			if (props.getProperty("single").contains("true")) { useMultiLineGraph=false; } //draw single lines between nodes
			if (props.getProperty("bigfont").contains("true")) { uiFont=new java.awt.Font(java.awt.Font.MONOSPACED,java.awt.Font.PLAIN,18); } //use 18pt font instead of default
			if (props.getProperty("forcejmenu").contains("true")) { useAppleTopOfScreenMenubarIfApplicable="false"; } //draw single lines between nodes
			if (props.getProperty("notheme").contains("true")) { applyTheme=false; } //draw single lines between nodes
			if (props.getProperty("noforcescale").contains("true")) { force100percentScale=false; } //draw single lines between nodes
			if (props.getProperty("credsfile")!=null) { credentialsFileName=props.getProperty("credsfile"); } //path to input file. ignore CLI filename on second time through here (means user asked to open a new file)
			if (props.getProperty("scorefile")!=null) { scoreFileName=props.getProperty("scorefile"); } //path to custom score file, and enable since...that makes sense in this case
			if (props.getProperty("inputfile")!=null) { inputFileName=props.getProperty("inputfile"); } //path to input file. ignore CLI filename on second time through here (means user asked to open a new file)
		}
		catch (Exception e) { e.printStackTrace(); }
		
		
		for (String s : args)
		{
			try
			{
				String[] argTokens=s.split("=");
				if (argTokens[0]==null) { continue; }
				if (argTokens[0].equalsIgnoreCase("--updateFile")) { updateFile=true; } //print more debugs while running
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
		System.setProperty("apple.laf.useScreenMenuBar", useAppleTopOfScreenMenubarIfApplicable); //FYI OpenJDK8 on macOS does not seem to do the correct MenuBar thing regardless
		if (force100percentScale) { System.setProperty("sun.java2d.uiScale", "100%"); } //sad little hack to work around current issues on HiDPI machines 
		if (applyTheme) { AHAGUIHelpers.applyTheme(uiFont); }
		
		if (updateFile) { FileUpdater.updateCSVFileWithRemoteVulnDBData(inputFileName, credentialsFileName, null, null, verbosity); }
		else
		{
			AHAController ctrl=new AHAController(inputFileName, scoreFileName, verbosity, useMultiLineGraph, props);
			ctrl.start();
		}
	}
}
