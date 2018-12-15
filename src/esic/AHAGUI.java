package esic;

//Copyright 2018 ESIC at WSU distributed under the MIT license. Please see LICENSE file for further info.

import java.awt.GridBagConstraints;
import java.awt.event.ActionEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.WindowEvent;
import org.graphstream.graph.*;

public class AHAGUI extends javax.swing.JFrame implements org.graphstream.ui.view.ViewerListener, java.awt.event.ActionListener, java.awt.event.MouseWheelListener, java.awt.event.WindowListener
{
	protected javax.swing.JLabel m_name=new javax.swing.JLabel("Name:          "), m_connections=new javax.swing.JLabel("Connections:          "), m_score=new javax.swing.JLabel("Score:          "), m_searchStatus=new javax.swing.JLabel("");
	protected javax.swing.JLabel[] m_overlayLabels= new javax.swing.JLabel[4]; 
	protected javax.swing.JCheckBox m_hideOSProcsCheckbox=new javax.swing.JCheckBox("Hide OS Procs"), m_hideExtCheckbox=new javax.swing.JCheckBox("Hide Ext Node"), m_showFQDN=new javax.swing.JCheckBox("DNS Names"), m_changeOnMouseOver=new javax.swing.JCheckBox("Update on MouseOver",false);
	protected javax.swing.JTextField m_search=new javax.swing.JTextField("Search...");
	protected java.awt.event.ComponentAdapter resizeHandler=null;
	protected org.graphstream.ui.swingViewer.ViewPanel m_viewPanel=null;
	protected org.graphstream.ui.view.Viewer m_viewer=null;
	protected org.graphstream.ui.view.ViewerPipe m_graphViewPump=null;
	protected AHAModel m_model=null;
	protected javax.swing.JPanel m_inspectorPanel=null;
	private java.util.concurrent.atomic.AtomicReference<Thread> m_graphRefreshThread=new java.util.concurrent.atomic.AtomicReference<Thread>(null);
	private static java.util.concurrent.Semaphore s_GUIActive=new java.util.concurrent.Semaphore(0);
	private static java.util.concurrent.atomic.AtomicBoolean s_userWantsToOpenNewFile=new java.util.concurrent.atomic.AtomicBoolean(true);
	private static java.awt.Dimension s_preferredSize=new java.awt.Dimension(1400, 800);

	public AHAGUI(AHAModel model, java.awt.Font uiFontBold)
	{
		System.err.println("Starting GUI Construction.");
		m_model=model;
		setBackground(java.awt.Color.BLACK); //this does indeed reduce a bit of flicker on load/reload of the frame
		setDefaultCloseOperation(javax.swing.JFrame.DISPOSE_ON_CLOSE);
		addWindowListener(this);
		
		pack(); //populate all the info about the insets
		setSize(s_preferredSize);
		String title="AHA-GUI";
		try { title=AHAGUI.class.getPackage().getImplementationVersion().split(" B")[0]; } catch (Exception e) {}
		setTitle(title); //This should result in something like "AHA-GUI v0.5.6b1" being displayed

		System.setProperty("org.graphstream.ui.renderer", "org.graphstream.ui.j2dviewer.J2DGraphRenderer");
		if (m_model.m_multi) { m_model.m_graph = new org.graphstream.graph.implementations.MultiGraph("MultiGraph"); }
		else { m_model.m_graph = new org.graphstream.graph.implementations.SingleGraph("SingleGraph"); }
		m_model.m_graph.addAttribute("ui.stylesheet", m_model.styleSheet);
		m_viewer = new org.graphstream.ui.view.Viewer(m_model.m_graph, org.graphstream.ui.view.Viewer.ThreadingModel.GRAPH_IN_ANOTHER_THREAD);
		m_viewPanel=m_viewer.addDefaultView(false);

		addMouseWheelListener(this);
		org.graphstream.ui.view.util.MouseManager mouseManager=new AHAGUIMouseAdapter(500,this);
		mouseManager.init(m_viewer.getGraphicGraph(), m_viewPanel);
		mouseManager.release(); //want it to be initialized, but we want to add it to the graph ourselves on the next line, otherwise we will get 2x the events.
		m_viewPanel.setMouseManager(mouseManager); 
		m_model.m_graph.addAttribute("layout.gravity", 0.000001); //layout.quality
		m_model.m_graph.addAttribute("layout.quality", 4);
		m_model.m_graph.addAttribute("layout.stabilization-limit", 0.95);
		m_model.m_graph.addAttribute("ui.antialias", true); //enable anti aliasing (looks way better this way)

		javax.swing.JPanel bottomPanel=new javax.swing.JPanel(), topLeftOverlay=new javax.swing.JPanel();
		topLeftOverlay.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT,2,2));
		{
			for (int i=0;i<m_overlayLabels.length;i++)
			{
				m_overlayLabels[i]=new javax.swing.JLabel(" ");
				m_overlayLabels[i].setFont(uiFontBold);
				m_overlayLabels[i].setForeground(java.awt.Color.BLACK);
				m_overlayLabels[i].setOpaque(true);
				topLeftOverlay.add(m_overlayLabels[i]);
			}
			m_overlayLabels[0].setBackground(java.awt.Color.GREEN);
			m_overlayLabels[1].setBackground(java.awt.Color.YELLOW);
			m_overlayLabels[2].setBackground(java.awt.Color.ORANGE);
			m_overlayLabels[3].setBackground(java.awt.Color.RED);
			topLeftOverlay.setOpaque(false);
		}
		bottomPanel.setLayout(new java.awt.GridBagLayout());
		javax.swing.JPanel bottomButtons=new javax.swing.JPanel(); //silly, but had to do this or the clickable area of the JCheckbox gets stretched the whole width...which makes for strange clicking
		{
			java.awt.GridBagConstraints gbc=new java.awt.GridBagConstraints();
			gbc.fill=java.awt.GridBagConstraints.HORIZONTAL;
			bottomButtons.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT,1,0));

			javax.swing.JButton dataViewBtn=new javax.swing.JButton(" Show Data View ");
			dataViewBtn.setActionCommand("dataView");
			dataViewBtn.addActionListener(this);
			dataViewBtn.setToolTipText(AHAGUIHelpers.styleToolTipText("Shows the list of listening processes to aid in creation of firewall rules."));

			javax.swing.JButton resetBtn=new javax.swing.JButton(" Reset Zoom ");
			resetBtn.setActionCommand("resetZoom");
			resetBtn.addActionListener(this);
			resetBtn.setToolTipText(AHAGUIHelpers.styleToolTipText("Resets the zoom of the graph view to default."));

			javax.swing.JButton openNewFileBtn=new javax.swing.JButton(" Open New File ");
			openNewFileBtn.setActionCommand("openNewFile");
			openNewFileBtn.addActionListener(this);
			openNewFileBtn.setToolTipText(AHAGUIHelpers.styleToolTipText("Close this file and open a new one."));

			javax.swing.JComboBox<AHAModel.ScoreMethod> scoreMethod=new javax.swing.JComboBox<AHAModel.ScoreMethod>(AHAModel.ScoreMethod.values());
			scoreMethod.setActionCommand("scoreMethod");
			scoreMethod.addActionListener(this);
			scoreMethod.setToolTipText(AHAGUIHelpers.styleToolTipText("Select the scoring method used to calculate node scores"));
			((javax.swing.JLabel)scoreMethod.getRenderer()).setHorizontalAlignment(javax.swing.JLabel.CENTER);
			java.awt.Dimension newDimension=scoreMethod.getPreferredSize();//=inspectorBtn.getPreferredSize();
			newDimension.setSize(newDimension.getWidth(), dataViewBtn.getPreferredSize().height);
			scoreMethod.setPreferredSize(newDimension); //prevent this from being off by one pixel from the other buttons in height (which was annoying me)

			m_hideOSProcsCheckbox.setActionCommand("hideOSProcs");
			m_hideOSProcsCheckbox.addActionListener(this);
			m_hideOSProcsCheckbox.setToolTipText(AHAGUIHelpers.styleToolTipText("Hides the usual Windows™ operating system processes, while interesting these processes can get in the way of other analysis."));

			m_hideExtCheckbox.setActionCommand("hideExtNode");
			m_hideExtCheckbox.addActionListener(this);
			m_hideExtCheckbox.setToolTipText(AHAGUIHelpers.styleToolTipText("Hides the main 'External' node from the graph that all nodes which listen on externally accessible addresses connect to."));

			m_showFQDN.setActionCommand("showFQDN");
			m_showFQDN.addActionListener(this);
			m_showFQDN.setToolTipText(AHAGUIHelpers.styleToolTipText("Show the DNS names of external nodes rather than IPs."));

			javax.swing.JCheckBox useCustom=new javax.swing.JCheckBox("Custom ScoreFile");
			useCustom.setActionCommand("useCustom");
			useCustom.setSelected(m_model.m_overlayCustomScoreFile);
			useCustom.addActionListener(this);
			useCustom.setToolTipText(AHAGUIHelpers.styleToolTipText("If a custom score file was loaded, this option will apply those custom directives to the graph view."));

			m_changeOnMouseOver.setToolTipText(AHAGUIHelpers.styleToolTipText("Enable change of the inspector above on hovering over nodes in addition to clicking."));

			bottomButtons.add(dataViewBtn);
			bottomButtons.add(resetBtn);
			bottomButtons.add(scoreMethod);
			bottomButtons.add(openNewFileBtn);
			bottomButtons.add(m_hideOSProcsCheckbox);
			bottomButtons.add(m_hideExtCheckbox);
			bottomButtons.add(m_showFQDN);
			bottomButtons.add(useCustom);
			bottomButtons.add(m_changeOnMouseOver);
			bottomButtons.setBorder(null);

			gbc.gridx=0; gbc.gridy=0; gbc.anchor=java.awt.GridBagConstraints.WEST; gbc.weightx=10; gbc.insets=new java.awt.Insets(0,2,0,0); gbc.gridwidth=java.awt.GridBagConstraints.REMAINDER;
			bottomPanel.add(m_name, gbc);
			gbc.gridy++;
			bottomPanel.add(m_connections, gbc);
			gbc.gridy++;
			bottomPanel.add(m_score, gbc);
			gbc.gridy++;
			
			m_search.addFocusListener(new java.awt.event.FocusListener()
			{
				public void focusGained(java.awt.event.FocusEvent e)
				{
					String txt=m_search.getText();
					if (txt!=null && txt.equals("Search...")) { m_search.setText(""); }
				}
				public void focusLost(java.awt.event.FocusEvent e)
				{
					String txt=m_search.getText();
					if (txt!=null && txt.equals("")) { m_search.setText("Search..."); }
				}
			});

			m_search.addActionListener(this);
			m_search.setActionCommand("search");
			m_search.setToolTipText(AHAGUIHelpers.styleToolTipText("Search\nTo emphasize nodes you're looking for:\nex: processname==svchost.exe\nwill highlight all nodes and connections\nYou can also highlight all nodes except the search term by inverting the search:\nex: processname!=svchost.exe\nwill highlight everything that is not svchost.exe\n\n To hide nodes prepend '~' ex: ~processname==unknown\n\n you can also create complex searches using the || symbol\n ex: processname==svchost.exe || ~processname==unknown"));
			gbc.insets=new java.awt.Insets(0,1,1,1);
			gbc.gridwidth=1;
			gbc.weightx=1000;
			bottomPanel.add(m_search, gbc);
			gbc.gridx++;
			gbc.weightx=0;
			gbc.insets=new java.awt.Insets(0,0,0,0);
			bottomPanel.add(m_searchStatus, gbc);

			gbc.gridy++;
			gbc.gridx=0;
			gbc.gridwidth=java.awt.GridBagConstraints.REMAINDER;
			bottomPanel.add(bottomButtons, gbc);
			bottomPanel.setBorder( new javax.swing.border.MatteBorder(1, 0, 0, 0, java.awt.Color.GRAY));
		}
		javax.swing.ToolTipManager.sharedInstance().setDismissDelay(Integer.MAX_VALUE);
		javax.swing.ToolTipManager.sharedInstance().setInitialDelay(500);
		setLayout(new java.awt.BorderLayout(1,0));
		javax.swing.JLayeredPane layeredPane = new javax.swing.JLayeredPane();
		m_inspectorPanel=createInspectorPanel((javax.swing.JFrame)this);

		resizeHandler=new java.awt.event.ComponentAdapter() {
			public void componentResized(java.awt.event.ComponentEvent componentEvent) 
			{
				s_preferredSize=getSize();
				java.awt.Dimension preferredBottomPanelSize=bottomPanel.getPreferredSize();
				layeredPane.setPreferredSize(getContentPane().getSize());
				topLeftOverlay.setBounds(0, 0, topLeftOverlay.getPreferredSize().width, topLeftOverlay.getPreferredSize().height);
				m_viewPanel.setBounds(0, 0, getContentPane().getSize().width-270, getContentPane().getSize().height-bottomPanel.getPreferredSize().height/*m_search.getPreferredSize().height-bottomButtons.getPreferredSize().height*/);
				m_inspectorPanel.setBounds(getContentPane().getSize().width-270, 0, 270, getContentPane().getSize().height-preferredBottomPanelSize.height);
				bottomPanel.setBounds(0, getContentPane().getSize().height-preferredBottomPanelSize.height, getContentPane().getSize().width, preferredBottomPanelSize.height);
			}
		}; //this is basically the layout manager for the overall frame including the graph itself, the bottom section, and the top overlay

		resizeHandler.componentResized(null); //fire once
		addComponentListener(resizeHandler);
		layeredPane.add(m_viewPanel, Integer.valueOf(0));
		layeredPane.add(m_inspectorPanel, Integer.valueOf(0));
		layeredPane.add(bottomPanel, Integer.valueOf(0));
		layeredPane.add(topLeftOverlay, Integer.valueOf(1));
		add(layeredPane, java.awt.BorderLayout.CENTER);
		getRootPane().setBorder(new javax.swing.border.LineBorder(java.awt.Color.GRAY,2)); //TODO: tried this to clean up the weird dashed appearance of the right gray border on macOS, but to no avail. figure it out later.
		setVisible(true);

		System.err.println("Finished Laying out GUI.");
		
		if ( m_model.m_inputFileName==null || m_model.m_inputFileName.equals("") )
		{
			javax.swing.JFileChooser fc=new javax.swing.JFileChooser("./");
			fc.setDialogTitle("Select Input File");
			javax.swing.filechooser.FileNameExtensionFilter filter = new javax.swing.filechooser.FileNameExtensionFilter("Comma Separated Value","csv");
	    fc.setFileFilter(filter);
			int retVal=fc.showOpenDialog(this);
			if (retVal==javax.swing.JFileChooser.APPROVE_OPTION)
			{ 
				m_model.m_inputFileName=fc.getSelectedFile().getAbsolutePath();
				System.err.println("User selected filename="+m_model.m_inputFileName);
			}
		}
		s_userWantsToOpenNewFile.set(false);
		m_graphRefreshThread.set(new Thread("GraphViewEventPump"){ //Don't do this until after we explore and score, to reduce odd concurrency errors that seem to occur
			public void run()
			{
				while (!Thread.interrupted())
				{
					try
					{
						int pumpErrs=0;
						//m_model.m_graph.clearSinks(); //executing this line will put it in the state that it gets into when it breaks...if i could figure out how to get it out of this state...then everything would work? simply adding //m_model.m_graph.addSink(m_model.m_graph); doesnt do anything useful
						//m_model.m_graph.addSink(m_model.m_graph);
						//m_model.m_graph.addSink(m_viewer.newThreadProxyOnGraphicGraph());
						m_graphViewPump = m_viewer.newViewerPipe();
						m_graphViewPump.addViewerListener(m_model.m_gui);
						m_graphViewPump.addSink(m_model.m_graph);
						
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
	}
	public void startGraphRefreshThread() { m_graphRefreshThread.get().start(); }

	public void updateOverlayLegend(int maxScore)
	{ 
		javax.swing.SwingUtilities.invokeLater(new Runnable() //perform task on gui thread
		{
			public void run()
			{
				m_overlayLabels[0].setText(" Best >"+(int)(0.75*maxScore)+" ");
				m_overlayLabels[1].setText(" >"+(int)(0.50*maxScore)+" ");
				m_overlayLabels[2].setText(" >"+(int)(0.25*maxScore)+" ");
				m_overlayLabels[3].setText(" Worst ");
				resizeHandler.componentResized(null);
			}
		});
	}

	public void actionPerformed(ActionEvent e) //swing actions go to here
	{
		if (e.getActionCommand().equals("hideOSProcs")) { m_model.hideOSProcs(m_model.m_graph, m_hideOSProcsCheckbox.isSelected()); }
		if (e.getActionCommand().equals("hideExtNode")) { m_model.hideFalseExternalNode(m_model.m_graph, m_hideExtCheckbox.isSelected()); }
		if (e.getActionCommand().equals("showFQDN")) { m_model.useFQDNLabels(m_model.m_graph, m_showFQDN.isSelected()); }
		if (e.getActionCommand().equals("dataView")) { showDataView(this); }
		if (e.getActionCommand().equals("resetZoom")) { m_viewPanel.getCamera().resetView(); }
		if (e.getActionCommand().equals("openNewFile")) { s_userWantsToOpenNewFile.set(true); this.dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING)); } //close window
		if (e.getActionCommand().equals("scoreMethod")) { m_model.swapNodeStyles(((AHAModel.ScoreMethod)((javax.swing.JComboBox<?>)e.getSource()).getSelectedItem()), System.currentTimeMillis()); }
		if (e.getActionCommand().equals("search")) { m_searchStatus.setText(m_model.handleSearch(m_search.getText())); }
		if (e.getActionCommand().equals("useCustom"))
		{
			m_model.m_overlayCustomScoreFile=((javax.swing.JCheckBox)e.getSource()).isSelected();
			m_model.exploreAndScore(m_model.m_graph);
		}
	}

	private Object synch_dataViewLock=new Object();
	private javax.swing.JFrame synch_dataViewFrame=null;
	private void showDataView(AHAGUI parent) //shows the window that lists the listening sockets
	{
		synchronized (synch_dataViewLock)
		{
			if (synch_dataViewFrame==null)
			{
				synch_dataViewFrame=new javax.swing.JFrame("Data View")
				{
					{ 
						setLayout(new java.awt.BorderLayout());
						setSize(new java.awt.Dimension(parent.getSize().width-40,parent.getSize().height-40));
						setLocation(parent.getLocation().x+20, parent.getLocation().y+20); //move it down and away a little bit so people understand it's a new window
						getRootPane().setBorder(new javax.swing.border.LineBorder(java.awt.Color.GRAY,2));
						javax.swing.JTabbedPane tabBar=new javax.swing.JTabbedPane();
						tabBar.setBorder(javax.swing.BorderFactory.createEmptyBorder());
						add(tabBar, java.awt.BorderLayout.CENTER);
						{ // Find data for, create table, etc for the "Graph Data" view
							AHAModel.TableDataHolder t=parent.m_model.generateReport();
							String[][] columTooltips= {{"Global data from the scan which took place.", "The result for this metric."},{"The name of the process.","Process ID of the process.", "User under which the process is running.", "The number of connections this process has.", "The number of ports this process has opened that external hosts/processes could connect to.", "Whether or not this process is codesigned. Code signing is recomended and allows executalbes to be authenticated as genuine.", "Address Space Layout Randomization is a recomended security feature which helps to reduce succeptability to malicious attacks.", "Data Execution Prevention is a recomended security feature which ensures that areas of memory which are writable (and could have code stored to by an attacker) are not executable.", "Control Flow Guard is a recomended security feature which helps prevent attackers from subverting normal code execution, reducing ease of attack.", "HiVA is an improved ASLR with additional etropy to further complicate any possible attacks.", "This is the score granted to the process by the 'Normal' scoring methodology which uses the MetricsTable.cfg to determine the score.","This is a beta scoring method.","This is a beta scoring method."}};
							tabBar.add("Vulnerability Metrics", createTablesInScrollPane(t.columnNames, columTooltips, t.tableData, new javax.swing.JTable[t.tableData.length], new int[]{180,40,200,86,80,50,44,44,44,44,44,44,60}) ); //TODO //FIXME
						}
						{ // Find data for, create table, etc for the "Listening Processes" tab
							javax.swing.JTable[] fwTables=new javax.swing.JTable[2];
							String[][] columnHeaders={{"Listening Internally", "PID", "Proto", "Port", "Connections"},{"Listening Externally", "PID", "Proto", "Port", "Connections"}};
							Object[][][] tableData=new Object[2][][];
							java.util.TreeMap<String,String> dataset=m_model.m_intListeningProcessMap;
							for (int i=0;i<2;i++)
							{
								java.util.TreeMap<String,Object[]> sortMe=new java.util.TreeMap<String,Object[]>();
								for (java.util.Map.Entry<String, String> entry : dataset.entrySet() )
								{
									String key=entry.getKey(), value=entry.getValue();
									String[] keyTokens=key.split("_"), valueTokens=value.split("_");
									Object strArrVal[]=new Object[5];
									strArrVal[0]=valueTokens[0];
									strArrVal[1]=AHAModel.strAsInt(valueTokens[1]);
									strArrVal[2]=keyTokens[0].toUpperCase();
									strArrVal[3]=AHAModel.strAsInt(keyTokens[1]);
									strArrVal[4]=m_model.m_listeningPortConnectionCount.get(key);
									String newKey=valueTokens[0]+valueTokens[1]+"-"+keyTokens[0].toUpperCase()+keyTokens[1];
									sortMe.put(newKey, strArrVal);
								}
								Object[][] data = new Object[sortMe.size()][5];
								int j=0;
								for (Object[] lineDat : sortMe.values()) { data[j++]=lineDat; }
								tableData[i]=data;
								dataset=m_model.m_extListeningProcessMap;
							}
							String[][] columTooltips= {{"Processes which have open ports that can only be connected to by processes on this host.", "Process ID of the process.", "The protocol of this listening port, such as TCP or UDP.", "The port number.", "The number of connections to this open port."},{"Processes which have open ports that can be connected to by remote hosts/processes.", "Process ID of the process.", "The protocol of this listening port, such as TCP or UDP.", "The port number.", "The number of connections to this open port."}};
							tabBar.add("Listening Processes", createTablesInScrollPane(columnHeaders,columTooltips, tableData, fwTables, new int[]{200,50,50,50})); //TODO: FIXME:
						}
					}
				};
			}
			synch_dataViewFrame.setVisible(true);
		}
	}

	public static javax.swing.JScrollPane createTablesInScrollPane(String[][] columnHeaders, String[][] columnTooltips, Object[][][] initialData, javax.swing.JTable[] tableRefs, int[] columnWidths)
	{
		javax.swing.JPanel scrollContent=new javax.swing.JPanel();
		scrollContent.setLayout(new javax.swing.BoxLayout(scrollContent, javax.swing.BoxLayout.Y_AXIS));
		javax.swing.table.DefaultTableCellRenderer tcRenderer=new javax.swing.table.DefaultTableCellRenderer(){{setHorizontalAlignment(javax.swing.table.DefaultTableCellRenderer.LEFT);}};
		for (int i=0; i<tableRefs.length; i++)
		{
			final String[] columnToolt=columnTooltips[i];
			if (tableRefs[i]==null) 
			{ 
				tableRefs[i]=new javax.swing.JTable() 
				{
					private String[] columnToolTips=columnToolt;
					public void changeSelection(int rowIndex, int columnIndex, boolean toggle, boolean extend) { super.changeSelection(rowIndex, columnIndex, !extend, extend); } //Always toggle on single selection (allows users to deselect rows easier)
					public boolean isCellEditable(int row, int column) { return false; } //disable cell editing
					public java.awt.Component prepareRenderer(javax.swing.table.TableCellRenderer renderer, int row, int column) 
					{
						java.awt.Component c = super.prepareRenderer(renderer, row, column);
						if (c instanceof javax.swing.JComponent) 
						{
							javax.swing.JComponent jc = (javax.swing.JComponent) c;
							Object o=getValueAt(row, column);
							if (o!=null) {  jc.setToolTipText(o.toString());}
						}
						return c;
					}
					protected javax.swing.table.JTableHeader createDefaultTableHeader() 
					{
						return new javax.swing.table.JTableHeader(columnModel) 
						{
							public String getToolTipText(java.awt.event.MouseEvent e) 
							{
								java.awt.Point p = e.getPoint();
								int tblIdx = columnModel.getColumnIndexAtX(p.x);
								int columnIdx = columnModel.getColumn(tblIdx).getModelIndex();
								if (columnIdx<columnToolTips.length) { return AHAGUIHelpers.styleToolTipText(columnToolTips[columnIdx]); }
								return "";
							}
						};
					}
				}; 
			}
			tableRefs[i].setModel( new javax.swing.table.DefaultTableModel(initialData[i], columnHeaders[i]) 
			{ 
				public Class<?> getColumnClass(int column) //makes it so row sorters work properly
				{ 
					try
					{
						Object o=null; //System.out.println("ColDetectorCalled for column="+column); //lazy hack but seems to work so shrug
						for (int row=0;row<getRowCount();row++) 
						{
							o=getValueAt(row, column);
							if (o!=null) { break; }
						}
						if (o instanceof String) { return String.class; }
						if (o instanceof Integer) { return Integer.class; }
						if (o instanceof Double) { return Double.class; }
						if (o instanceof Float) { return Float.class; }
						if (o instanceof Long) { return Long.class; }
					} catch (Exception e) { e.printStackTrace(); }
					return Object.class;
				}
			});
			tableRefs[i].setDefaultRenderer(Integer.class, tcRenderer);
			tableRefs[i].getTableHeader().setBorder(null);
			tableRefs[i].setBorder(null);
			tableRefs[i].setPreferredScrollableViewportSize(tableRefs[i].getPreferredSize());
			tableRefs[i].setAlignmentY(TOP_ALIGNMENT);
			tableRefs[i].getTableHeader().setAlignmentY(TOP_ALIGNMENT);
			tableRefs[i].setAutoCreateRowSorter(true);
			for (int j=0;j<tableRefs[i].getColumnModel().getColumnCount() && j<columnWidths.length; j++) { tableRefs[i].getColumnModel().getColumn(j).setPreferredWidth(columnWidths[j]); }
			scrollContent.add(tableRefs[i].getTableHeader());
			scrollContent.add(tableRefs[i]);
		}
		javax.swing.JScrollPane scrollPane = new javax.swing.JScrollPane(scrollContent);
		scrollPane.setViewportBorder(javax.swing.BorderFactory.createEmptyBorder());
		scrollPane.setBorder(javax.swing.BorderFactory.createEmptyBorder());
		return scrollPane;
	}

	private javax.swing.JCheckBox m_showOnlyMatchedMetrics=new javax.swing.JCheckBox("Show Only Matched Metrics",true), m_showScoringSpecifics=new javax.swing.JCheckBox("Show Score Metric Specifics",false);
	private String[][] m_inspectorWindowColumnHeaders={{"Info"},{"Open Internal Port", "Proto"},{"Open External Port", "Proto"},{"Connected Process Name", "PID"}, {"Score Metric", "Value"}};
	private String[][] m_inspectorWindowColumnTooltips={{"Info"},{"Port that is able to be connected to from other processes internally.", "Protocol in use."},{"Port that is able to be connected to from other external hosts/processes.", "Protocol in use."},{"Names of processes connected to this one", "Process Identifier"}, {"The scoring metric checked against.", "Result of the checked metric."}};
	private javax.swing.JTable[] m_inspectorWindowTables= new javax.swing.JTable[m_inspectorWindowColumnHeaders.length]; //if you need more tables just add another column header set above

	public javax.swing.JPanel createInspectorPanel(javax.swing.JFrame parent)
	{
		javax.swing.JPanel infoPanel=new javax.swing.JPanel();
		m_showOnlyMatchedMetrics.setToolTipText(AHAGUIHelpers.styleToolTipText("Only displays metrics which were matched, for example if ASLR was true (Note: please click on a new node after enabling)."));
		m_showScoringSpecifics.setToolTipText(AHAGUIHelpers.styleToolTipText("Shows the specific metric in the inspector above that matched (Note: please click on a new node after enabling)."));

		infoPanel.setBorder(new javax.swing.border.MatteBorder(0,1,0,0,java.awt.Color.GRAY));
		infoPanel.setLayout(new java.awt.GridBagLayout());
		java.awt.GridBagConstraints gbc=new java.awt.GridBagConstraints();
		gbc.insets = new java.awt.Insets(0, 0, 0, 0);
		gbc.fill=gbc.fill=GridBagConstraints.BOTH;
		gbc.gridx=0; gbc.gridy=0;  gbc.weightx=1; gbc.weighty=100;

		String[][][] initialData={{{"None"}},{{"None"}},{{"None"}},{{"None"}},{{"None"}},}; //digging this new 3d array literal initializer: this is a String[5][1][1] where element[i][0][0]="None".
		infoPanel.add(createTablesInScrollPane(m_inspectorWindowColumnHeaders, m_inspectorWindowColumnTooltips, initialData, m_inspectorWindowTables, new int[]{160,40}), gbc);

		gbc.gridy++;
		gbc.weighty=1;
		gbc.fill=java.awt.GridBagConstraints.HORIZONTAL;
		infoPanel.add(m_showOnlyMatchedMetrics, gbc);
		gbc.gridy++;
		infoPanel.add(m_showScoringSpecifics, gbc);
		return infoPanel;
	}

	protected void updateInfoDisplayForGraphElement(org.graphstream.ui.graphicGraph.GraphicElement element, boolean occuredFromMouseOver, AHAModel model)
	{
		if (element==null || occuredFromMouseOver && !m_changeOnMouseOver.isSelected()) { return; }
		Node node=model.m_graph.getNode(element.getId());
		if (node==null) { return; }
		
		Object[][] infoData=null, intPorts=null, extPorts=null, connectionData=null, scoreReasons=null;
		try
		{ //update the top info panel table
			String[] infoLines=getNameString(node,"\n").trim().split("\n");
			infoData=new String[infoLines.length][1];
			for (int i=0;i<infoLines.length;i++) { infoData[i][0]=infoLines[i]; }
		} catch (Exception e) { e.printStackTrace(); }
		try
		{ //update the "Open Internal Ports" second table
			String[] ports=AHAModel.getCommaSepKeysFromStringMap(element.getAttribute("ui.localListeningPorts")).split(", ");
			intPorts=new Object[ports.length][2];
			for (int i=0;i<ports.length;i++)
			{
				String[] temp=ports[i].split("_");
				if (temp.length > 1)
				{
					intPorts[i]=new Object[2];
					intPorts[i][0]=AHAModel.strAsInt(temp[1]);
					intPorts[i][1]=temp[0].toUpperCase(); //reverse array
				} else { intPorts[i][0]="None"; }
			}
		} catch (Exception e) { e.printStackTrace(); }
		try
		{ //update the third "Open External Ports" table
			String[] ports=AHAModel.getCommaSepKeysFromStringMap(element.getAttribute("ui.extListeningPorts")).split(", ");
			extPorts=new Object[ports.length][2];
			for (int i=0;i<ports.length;i++)
			{
				String[] temp=ports[i].split("_");
				if (temp.length > 1)
				{
					extPorts[i]=new Object[2];
					extPorts[i][0]=AHAModel.strAsInt(temp[1]);
					extPorts[i][1]=temp[0].toUpperCase(); //reverse array
				} else { extPorts[i][0]="None"; }
			}
		} catch (Exception e) { e.printStackTrace(); }
		try
		{ //update the fourth "Connected Process Name" table
			String[] connections=getNodeConnectionString(node,model).split(", ");
			connectionData=new Object[connections.length][2];
			for (int i=0;i<connections.length;i++) 
			{ 
				String[] tokens=connections[i].split("_");
				connectionData[i][0]=tokens[0];
				if (tokens[0].equals("Ext")) { connectionData[i][0]=connections[i]; }
				else if (tokens.length > 1) { connectionData[i][1]=AHAModel.strAsInt(tokens[1]); }
			}
		} catch (Exception e) { e.printStackTrace(); }
		try
		{ //update the fifth "Score Metric" table
			String score=getNodeScoreReasonString(node, true);
			String[] scores=score.split(", ");
			int length=0;
			for (int i=0;i<scores.length;i++) 
			{ 
				if (scores[i].toLowerCase().endsWith("false") && m_showOnlyMatchedMetrics.isSelected()) {continue;}
				length++;
			}
			scoreReasons=new String[length][2];
			int j=0;
			for (int i=0;i<scores.length;i++) 
			{ 
				String[] scrTokens=scores[i].split("=");
				if (scrTokens!=null && scrTokens.length>=2)
				{
					if (m_showOnlyMatchedMetrics.isSelected()==true && scrTokens[1].toLowerCase().contains("false")) { continue; } 
					scoreReasons[j]=scrTokens;
					if (!m_showScoringSpecifics.isSelected()) 
					{ 
						String input=(String)scoreReasons[j][0];
						if (input!=null && input.contains("[") && input.contains("]:")) { scoreReasons[j][0]=input.split("\\.")[0]+"("+input.split("\\]:")[1]+")"; }
					}
					j++;
				}
			}
			if (scores.length==0 || score.trim().trim().equals("N/A") ){ scoreReasons=new String[][]{{"Scoring not applicable."}}; }
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
						javax.swing.table.DefaultTableModel dm=(javax.swing.table.DefaultTableModel)m_inspectorWindowTables[i].getModel();
						dm.setDataVector(data[i], m_inspectorWindowColumnHeaders[i]);
						m_inspectorWindowTables[i].getColumnModel().getColumn(0).setPreferredWidth(160);
						if (m_inspectorWindowTables[i].getColumnModel().getColumnCount() > 1)
						{
							m_inspectorWindowTables[i].getColumnModel().getColumn(1).setPreferredWidth(40);
						}
					} catch (Exception e) { e.printStackTrace(); }
				}
			}
		});
		updateBottomInfoPanel(node); //TODO: hmmm, if this is enabled, the gui does weird things?
	}
	
	private void updateBottomInfoPanel(Node node) //TODO: merge this into the same udpate method as the one for the side panel
	{
		final String connections="Connections: "+getNodeConnectionString(node,m_model), nameText=getNameString(node,"  "), scoreReason="Score: "+getNodeScoreReasonString(node, false);
		javax.swing.SwingUtilities.invokeLater(new Runnable() //perform task on gui thread
		{
			public void run()
			{
				m_name.setText(nameText);
				m_name.setToolTipText(AHAGUIHelpers.styleToolTipText(nameText));
				m_connections.setText(connections);
				m_connections.setToolTipText(AHAGUIHelpers.styleToolTipText(connections));
				m_score.setText(scoreReason);
				m_score.setToolTipText(AHAGUIHelpers.styleToolTipText(scoreReason));
			}
		});
	}

	public static String getNodeScoreReasonString(Node node, boolean extendedReason)
	{ 
		if (node==null) { return " "; }
		String score=node.getAttribute("ui.scoreReason");
		if (extendedReason) { score=node.getAttribute("ui.scoreExtendedReason"); }
		if (score==null) { score=" "; }
		if (node.getAttribute("ui.class")!=null && node.getAttribute("ui.class").toString().toLowerCase().equals("external")) { score="N/A"; } //this was requested to make the UI feel cleaner, since nothing can be done to help the score of an external node anyway.
		return score;
	}

	public static String getNodeConnectionString(Node node, AHAModel model)
	{
		if (node==null || model==null || node.getEachEdge()==null) { return " "; }
		String connections="";
		java.util.Iterator<Edge> it=node.getEachEdge().iterator();
		while (it.hasNext())
		{
			Edge e=it.next();
			Node tempNode=e.getOpposite(node);
			String t2=tempNode.getAttribute("ui.label");
			if (t2!=null && !connections.contains(t2)) { connections+=t2+", "; } //some vertices have multiple connections between them, only print once
		}
		connections=AHAModel.substringBeforeInstanceOf(connections,", ");
		if (connections==null || connections.length() < 1) { return "None"; }
		return connections;
	}

	public static String getNameString(Node node, String separator)
	{
		if (node==null) { return " "; }
		String nameTxt="Name: "+node.getAttribute("ui.label")+separator+"User: "+node.getAttribute("username")+separator+"Path: "+node.getAttribute("processpath");
		String services=node.getAttribute("processservices");
		String uiclass=node.getAttribute("ui.class");
		if (uiclass!=null && uiclass.equalsIgnoreCase("external")) 
		{ 
			if (node.getAttribute("IP")!=null)
			{
				nameTxt="Name: "+node.getAttribute("ui.label")+separator+"IP: "+node.getAttribute("IP")+separator+"DNS Name: "+node.getAttribute("hostname"); 
			}
			else if (node.getId().toLowerCase().equals("external"))
			{
				nameTxt="Name:"+node.getAttribute("ui.label")+separator+"dummy node which connects to any node listening for outside connections. This can be hidden using 'Hide Ext Node' checkbox.";
			}
		}
		if (services!=null && !services.equals("") && !services.equals("null")) { nameTxt+=separator+"Services: "+services; }
		return nameTxt;
	}

	public synchronized void buttonPushed(String id) //called when you click on a graph node/edge
	{
		if (id==null || id.equals("")) { return; } //try { m_graphViewPump.pump(); } catch (Exception e) {e.printStackTrace();}  //non blocking pump to clear anything out before we heckle the graph
		try
		{
			Node node=m_model.m_graph.getNode(id);
			if (node==null) { return; }

			for (Edge e : node.getEdgeSet())
			{
				String currentClasses=e.getAttribute("ui.class");
				if (currentClasses==null) { currentClasses=""; }
				if (!currentClasses.contains("clickedAccent")) { e.addAttribute("ui.class", "clickedAccent, "+currentClasses); } //System.out.println("Adding classes: old uiclass was |"+currentClasses+"| is now |"+e.getAttribute("ui.class")+"|");
			}
			updateBottomInfoPanel(node);
		} catch (Exception e) { e.printStackTrace(); }
	}
	
	public synchronized void mouseWheelMoved(MouseWheelEvent e) //zooms graph in and out using mouse wheel
	{
		double newZoom=m_viewPanel.getCamera().getViewPercent()+((double)e.getWheelRotation()/100d);
		if (newZoom <= 0) { newZoom=0.01d; }
		if (newZoom > 0 && newZoom < 20 ) { m_viewPanel.getCamera().setViewPercent(newZoom); }
	}
	public synchronized void buttonReleased(String id) 
	{ 
		if (id==null || id.equals("")) { return; }
		try
		{
			Node node=m_model.m_graph.getNode(id);
			if (node==null) { System.out.println("node is null, returning early"); return;}
			for (Edge e : node.getEdgeSet())
			{
				try 
				{
					String currentClasses=e.getAttribute("ui.class");
					if (currentClasses!=null) { e.addAttribute("ui.class", currentClasses.replaceAll("clickedAccent, ", "")); } //System.out.println("Removing classes: old uiclass was |"+currentClasses+"| is now |"+e.getAttribute("ui.class")+"|");
				} catch (Exception ex) { ex.printStackTrace(); }
			}
		} catch (Exception e2) { e2.printStackTrace(); }
	} //graph mouse function
	public synchronized void viewClosed(String arg0) {} //graph viewer interface function

	public synchronized void stoppedHoveringOverElement(org.graphstream.ui.graphicGraph.GraphicElement element) {}
	public synchronized void startedHoveringOverElementOrClicked(org.graphstream.ui.graphicGraph.GraphicElement element, boolean occuredFromMouseOver)
	{
		updateInfoDisplayForGraphElement(element, occuredFromMouseOver, m_model);
	}
	
	public void windowOpened(WindowEvent e) {} //Window listener related events
	public void windowIconified(WindowEvent e) {}
	public void windowDeiconified(WindowEvent e) {}
	public void windowActivated(WindowEvent e) {}
	public void windowDeactivated(WindowEvent e) {}
	public void windowClosing(WindowEvent e) { setVisible(false); }
	public void windowClosed(WindowEvent e)
	{ 
		Thread t=m_graphRefreshThread.get();
		t.interrupt(); 
		System.err.println("Window closed."); 
		s_GUIActive.release(); 
		if (m_viewer!=null) { m_viewer.close(); } 
		if (m_model.m_graph!=null) { m_model.m_graph.clearSinks(); m_model.m_graph.clear(); } 
	} //lets us start a new window and open a new file

	public static void main(String args[]) //It's kind of dumb that our stub to main is in this class, but this way when we run on operating systems that display the name in places, it will say AHAGUI rather than AHAModel
	{ 
		boolean firstRun=true;
		try
		{
			while (!Thread.interrupted() && AHAGUI.s_userWantsToOpenNewFile.get()) //quit out if they didn't ask to open a new file
			{
				AHAModel model=new AHAModel(args, firstRun);
				model.start();
				s_GUIActive.acquire(); //block here until the GUI is closed
				firstRun=false; //allow user to specify filename to load next time //TODO: uncomment this after testing
			}
		} catch (Exception e) { e.printStackTrace(System.err); }
		System.err.println("Reqeusting exit.");
	}

}
