package esic;

//Copyright 2018 ESIC at WSU distributed under the MIT license. Please see LICENSE file for further info.

import java.awt.GridBagConstraints;
import java.awt.event.ActionEvent;
import java.awt.event.MouseWheelEvent;
import org.graphstream.graph.*;

public class AHAGUI extends javax.swing.JFrame implements org.graphstream.ui.view.ViewerListener, java.awt.event.ActionListener, java.awt.event.MouseWheelListener
{
	protected javax.swing.JLabel m_name=new javax.swing.JLabel("Name:          "), m_connections=new javax.swing.JLabel("Connections:          "), m_score=new javax.swing.JLabel("Score:          ");
	protected javax.swing.JCheckBox m_hideOSProcsCheckbox=new javax.swing.JCheckBox("Hide OS Procs"), m_hideExtCheckbox=new javax.swing.JCheckBox("Hide Ext Node"), m_showFQDN=new javax.swing.JCheckBox("DNS Names");
	protected org.graphstream.ui.swingViewer.ViewPanel m_viewPanel=null;
	protected org.graphstream.ui.view.Viewer m_viewer=null;
	protected org.graphstream.ui.view.ViewerPipe m_graphViewPump=null;
	protected AHAModel m_model=null;
	protected InspectorWindow m_inspectorWindow=null;

	public AHAGUI(AHAModel model)
	{
		m_model=model;
		setDefaultCloseOperation(javax.swing.JFrame.EXIT_ON_CLOSE);
		setSize(1152, 768);
		setTitle(AHAGUI.class.getPackage().getImplementationVersion().split(" B")[0]); //This should result in something like "AHA-GUI v0.5.6b1" being displayed
		getRootPane().setBorder(new javax.swing.border.LineBorder(java.awt.Color.GRAY,2)); //TODO: tried this to clean up the weird dashed appearance of the right gray border on macOS, but to no avail. figure it out later.
		setLayout(new java.awt.BorderLayout(2,0));
		
		System.setProperty("org.graphstream.ui.renderer", "org.graphstream.ui.j2dviewer.J2DGraphRenderer");
		if (m_model.m_multi) { m_model.m_graph = new org.graphstream.graph.implementations.MultiGraph("MultiGraph"); }
		else { m_model.m_graph = new org.graphstream.graph.implementations.SingleGraph("SingleGraph"); }
		m_viewer = new org.graphstream.ui.view.Viewer(m_model.m_graph, org.graphstream.ui.view.Viewer.ThreadingModel.GRAPH_IN_ANOTHER_THREAD);
		m_graphViewPump = m_viewer.newViewerPipe();
		m_graphViewPump.addViewerListener(this);
		m_graphViewPump.addSink(m_model.m_graph);
		m_viewer.enableAutoLayout();
		m_viewPanel=m_viewer.addDefaultView(false);

		addMouseWheelListener(this);
		org.graphstream.ui.view.util.MouseManager mouseManager=new AHAGUIMouseAdapter(500,this);
		mouseManager.init(m_viewer.getGraphicGraph(), m_viewPanel);
		m_viewPanel.setMouseManager(mouseManager);
		m_model.m_graph.addAttribute("layout.gravity", 0.000001); //layout.quality
		m_model.m_graph.addAttribute("layout.quality", 4);
		m_model.m_graph.addAttribute("layout.stabilization-limit", 0.95);
		m_model.m_graph.addAttribute("ui.antialias", true); //enable anti aliasing (looks way better this way)
		this.add(m_viewPanel, java.awt.BorderLayout.CENTER);
		
		javax.swing.JPanel bottomPanel=new javax.swing.JPanel();
		bottomPanel.setLayout(new java.awt.GridBagLayout());
		{
			java.awt.GridBagConstraints gbc=new java.awt.GridBagConstraints();
			gbc.fill=java.awt.GridBagConstraints.HORIZONTAL;
			javax.swing.JPanel bottomButtons=new javax.swing.JPanel(); //silly, but had to do this or the clickable area of the JCheckbox gets stretched the whole width...which makes for strange clicking
			bottomButtons.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT,1,0));
			
			javax.swing.JButton dataViewBtn=new javax.swing.JButton(" Show Data View ");
			dataViewBtn.setActionCommand("dataView");
			dataViewBtn.addActionListener(this);
			dataViewBtn.setToolTipText(styleToolTipText("Shows the list of listening processes to aid in creation of firewall rules."));
			
			javax.swing.JButton resetBtn=new javax.swing.JButton(" Reset Zoom ");
			resetBtn.setActionCommand("resetZoom");
			resetBtn.addActionListener(this);
			resetBtn.setToolTipText(styleToolTipText("Resets the zoom of the graph view to default."));

			javax.swing.JButton inspectorBtn=new javax.swing.JButton(" Show Inspector ");
			inspectorBtn.setActionCommand("showInspector");
			inspectorBtn.addActionListener(this);
			inspectorBtn.setToolTipText(styleToolTipText("Show the graph detail inspector."));
			
			javax.swing.JComboBox<AHAModel.ScoreMethod> scoreMethod=new javax.swing.JComboBox<AHAModel.ScoreMethod>(AHAModel.ScoreMethod.values());
			scoreMethod.setActionCommand("scoreMethod");
			scoreMethod.addActionListener(this);
			scoreMethod.setToolTipText(styleToolTipText("Select the scoring method used to calculate node scores"));
			((javax.swing.JLabel)scoreMethod.getRenderer()).setHorizontalAlignment(javax.swing.JLabel.CENTER);
			scoreMethod.setPreferredSize(inspectorBtn.getPreferredSize()); //prevent this from being off by one pixel from the other buttons in height (which was annoying me)
			
			m_hideOSProcsCheckbox.setActionCommand("hideOSProcs");
			m_hideOSProcsCheckbox.addActionListener(this);
			m_hideOSProcsCheckbox.setToolTipText(styleToolTipText("Hides the usual Windows™ operating system processes, while interesting these processes can get in the way of other analysis."));
			
			m_hideExtCheckbox.setActionCommand("hideExtNode");
			m_hideExtCheckbox.addActionListener(this);
			m_hideExtCheckbox.setToolTipText(styleToolTipText("Hides the main 'External' node from the graph that all nodes which listen on externally accessible addresses connect to."));
			
			m_showFQDN.setActionCommand("showFQDN");
			m_showFQDN.addActionListener(this);
			m_showFQDN.setToolTipText(styleToolTipText("Show the DNS names of external nodes rather than IPs."));
			
			javax.swing.JCheckBox useCustom=new javax.swing.JCheckBox("Custom ScoreFile");
			useCustom.setActionCommand("useCustom");
			useCustom.setSelected(m_model.m_overlayCustomScoreFile);
			useCustom.addActionListener(this);
			useCustom.setToolTipText(styleToolTipText("If a custom score file was loaded, this option will apply those custom directives to the graph view."));
			
			bottomButtons.add(dataViewBtn);
			bottomButtons.add(resetBtn);
			bottomButtons.add(inspectorBtn);
			bottomButtons.add(scoreMethod);
			bottomButtons.add(m_hideOSProcsCheckbox);
			bottomButtons.add(m_hideExtCheckbox);
			bottomButtons.add(m_showFQDN);
			bottomButtons.add(useCustom);
			bottomButtons.setBorder(null);
			
			gbc.gridx=0; gbc.gridy=0; gbc.anchor=java.awt.GridBagConstraints.WEST; gbc.weightx=10; gbc.insets=new java.awt.Insets(0,2,0,0);
			bottomPanel.add(m_name, gbc);
			gbc.gridy++;
			bottomPanel.add(m_connections, gbc);
			gbc.gridy++;
			bottomPanel.add(m_score, gbc);
			gbc.gridy++;
			gbc.insets=new java.awt.Insets(0,0,0,0);
			bottomPanel.add(bottomButtons, gbc);
		}
		javax.swing.ToolTipManager.sharedInstance().setDismissDelay(Integer.MAX_VALUE);
		javax.swing.ToolTipManager.sharedInstance().setInitialDelay(500);
		this.add(bottomPanel, java.awt.BorderLayout.SOUTH);
		this.setVisible(true);
		m_inspectorWindow=new InspectorWindow((javax.swing.JFrame)this);
	}
	
	public void actionPerformed(ActionEvent e) //swing actions go to here
	{
		if (e.getActionCommand().equals("hideOSProcs")) { m_model.hideOSProcs(m_model.m_graph, m_hideOSProcsCheckbox.isSelected()); }
		if (e.getActionCommand().equals("hideExtNode")) { m_model.hideFalseExternalNode(m_model.m_graph, m_hideExtCheckbox.isSelected()); }
		if (e.getActionCommand().equals("showFQDN")) { m_model.useFQDNLabels(m_model.m_graph, m_showFQDN.isSelected()); }
		if (e.getActionCommand().equals("dataView")) { showDataView(this); }
		if (e.getActionCommand().equals("resetZoom")) { m_viewPanel.getCamera().resetView(); }
		if (e.getActionCommand().equals("showInspector")) { m_inspectorWindow.setVisible(true); }
		if (e.getActionCommand().equals("scoreMethod")) { m_model.swapNodeStyles(((AHAModel.ScoreMethod)((javax.swing.JComboBox<?>)e.getSource()).getSelectedItem()), System.currentTimeMillis()); }
		if (e.getActionCommand().equals("useCustom"))
		{
			m_model.m_overlayCustomScoreFile=((javax.swing.JCheckBox)e.getSource()).isSelected();
			m_model.exploreAndScore(m_model.m_graph);
		}
	}
	
	private static String styleToolTipText(String s) //format all tool tip texts by making them HTML (so we can apply text effects, and more importantly line breaks)
	{
		if (s.length()>60)
		{
			StringBuilder sb=new StringBuilder("");
			int currentLineLength=0;
			for (int i=0;i<s.length();i++)
			{
				s.replaceAll("<BR>", "\n");
				char c=s.charAt(i);
				if ( (c=='\n') || ((c==',' || c=='.' || c==';') && currentLineLength>50) || (c==' ' && currentLineLength>75))
				{
					sb.append(c);
					if (currentLineLength>0) { sb.append("<BR>"); } //should collapse a sequence of <BR><BR><BR>
					currentLineLength=0;
				}
				else
				{
					sb.append(c);
					currentLineLength++;
				}
			}
			s=sb.toString();
		}
		return "<html><p style='font-style:bold;color:black;background:white;'>"+s+"</p></html> ";
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
							tabBar.add("Vulnerability Metrics", createTablesInScrollPane(t.columnNames, t.tableData, new javax.swing.JTable[t.tableData.length], new int[]{180,40,200,86,80,50,44,44,44,44,44,44,60}) );
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
							tabBar.add("Listening Processes", createTablesInScrollPane(columnHeaders, tableData, fwTables, new int[]{200,50,50,50}));
						}
					}
				};
			}
			synch_dataViewFrame.setVisible(true);
		}
	}
	
	public static javax.swing.JScrollPane createTablesInScrollPane(String[][] columnHeaders, Object[][][] initialData, javax.swing.JTable[] tableRefs, int[] columnWidths)
	{
		javax.swing.JPanel scrollContent=new javax.swing.JPanel();
		scrollContent.setLayout(new javax.swing.BoxLayout(scrollContent, javax.swing.BoxLayout.Y_AXIS));
		javax.swing.table.DefaultTableCellRenderer tcRenderer=new javax.swing.table.DefaultTableCellRenderer(){{setHorizontalAlignment(javax.swing.table.DefaultTableCellRenderer.LEFT);}};
		for (int i=0; i<tableRefs.length; i++)
		{
			if (tableRefs[i]==null) 
			{ 
				tableRefs[i]=new javax.swing.JTable() 
				{
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
				}; 
			}
			tableRefs[i].setModel( new javax.swing.table.DefaultTableModel(initialData[i], columnHeaders[i]) 
			{ public Class<?> getColumnClass(int column) //makes it so row sorters work properly
        { try
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
	
	public static class InspectorWindow extends javax.swing.JFrame
	{
		private javax.swing.JCheckBox m_changeOnMouseOver=new javax.swing.JCheckBox("Update on MouseOver",false);
		private String[][] m_inspectorWindoColumnHeaders={{"Info"},{"Open Internal Port", "Proto"},{"Open External Port", "Proto"},{"Connected Process Name", "PID"}, {"Score Metric", "Value"}};
		private javax.swing.JTable[] m_inspectorWindowTables= new javax.swing.JTable[m_inspectorWindoColumnHeaders.length]; //if you need more tables just add another column header set above
		
		public InspectorWindow(javax.swing.JFrame parent)
		{
			setTitle("Graph Node Inspector");
			getRootPane().setBorder(new javax.swing.border.LineBorder(java.awt.Color.GRAY,2));
			setLayout(new java.awt.GridBagLayout());
			java.awt.GridBagConstraints gbc=new java.awt.GridBagConstraints();
			gbc.insets = new java.awt.Insets(2, 5, 2, 5);
			gbc.fill=gbc.fill=GridBagConstraints.BOTH;
			gbc.gridx=0; gbc.gridy=0;  gbc.weightx=1; gbc.weighty=100;
			
			String[][][] initialData={{{"None"}},{{"None"}},{{"None"}},{{"None"}},{{"None"}},}; //digging this new 3d array literal initializer: this is a String[5][1][1] where element[i][0][0]="None".
			this.add(createTablesInScrollPane(m_inspectorWindoColumnHeaders, initialData, m_inspectorWindowTables, new int[]{160,40}), gbc);
			
			gbc.gridy++;
			gbc.weighty=1;
			gbc.fill=java.awt.GridBagConstraints.HORIZONTAL;
			m_changeOnMouseOver.setToolTipText("Update the information above as you hover over items in the graph.");
			this.add(m_changeOnMouseOver, gbc);
			
			this.setLocation(parent.getLocation().x+parent.getWidth(), 0);
			this.setSize(286,768);
			this.setDefaultCloseOperation(javax.swing.JFrame.HIDE_ON_CLOSE);
			this.setVisible(true);
		}
		
		public void updateDisplayForGraphElement(org.graphstream.ui.graphicGraph.GraphicElement element, boolean occuredFromMouseOver, AHAModel model)
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
					if (tokens.length > 1) { connectionData[i][1]=AHAModel.strAsInt(tokens[1]); }
				}
			} catch (Exception e) { e.printStackTrace(); }
			try
			{ //update the fifth "Score Metric" table
				String[] scores=getNodeScoreReasonString(node, true).split(", ");
				scoreReasons=new String[scores.length][2];
				for (int i=0;i<scores.length;i++) { scoreReasons[i]=scores[i].split("="); }
				if (scores.length==0){ scoreReasons=new String[][]{{"No score results found"}}; }
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
							dm.setDataVector(data[i], m_inspectorWindoColumnHeaders[i]);
							m_inspectorWindowTables[i].getColumnModel().getColumn(0).setPreferredWidth(140);
							if (m_inspectorWindowTables[i].getColumnModel().getColumnCount() > 1)
							{
								m_inspectorWindowTables[i].getColumnModel().getColumn(1).setPreferredWidth(40);
							}
						} catch (Exception e) { e.printStackTrace(); }
					}
				}
			});
		}
	}
	
	public void mouseWheelMoved(MouseWheelEvent e) //zooms graph in and out using mouse wheel
	{
		m_viewPanel.getCamera().setViewPercent(m_viewPanel.getCamera().getViewPercent()+((double)e.getWheelRotation()/100d));
	}
	public void buttonReleased(String id) {} //graph mouse function
	public void viewClosed(String arg0) {} //graph viewer interface function
	
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
			if (t2!=null && !connections.contains(t2)) //some vertices have multiple connections between them, only print once
			{ 
				String processPath=tempNode.getAttribute("processpath");
				if ( processPath==null || !(model.m_hideOSProcs&&model.m_osProcs.get(processPath)!=null) ) 
				{ 
					connections+=t2+", ";
				}
			}
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
	
	public void buttonPushed(String id) //called when you click on a graph node/edge
	{
		if (id==null || id.equals("")) { return; }
		Node node=m_model.m_graph.getNode(id);
		if (node==null) { return; }
		final String connections="Connections: "+getNodeConnectionString(node,m_model), nameText=getNameString(node,"  "), scoreReason="Score: "+getNodeScoreReasonString(node, false);

		javax.swing.SwingUtilities.invokeLater(new Runnable() //perform task on gui thread
		{
			public void run()
			{
				m_name.setText(nameText);
				m_name.setToolTipText(styleToolTipText(nameText));
				m_connections.setText(connections);
				m_connections.setToolTipText(styleToolTipText(connections));
				m_score.setText(scoreReason);
				m_score.setToolTipText(styleToolTipText(scoreReason));
			}
		});
	}
	
	public void stoppedHoveringOverElement(org.graphstream.ui.graphicGraph.GraphicElement element) {}
	public void startedHoveringOverElementOrClicked(org.graphstream.ui.graphicGraph.GraphicElement element, boolean occuredFromMouseOver)
	{
		m_inspectorWindow.updateDisplayForGraphElement(element, occuredFromMouseOver, m_model);
	}
	
	public static void main(String args[]) //It's kind of dumb that our stub to main is in this class, but this way when we run on operating systems that display the name in places, it will say AHAGUI rather than AHAModel
	{ 
		try { javax.swing.UIManager.setLookAndFeel( javax.swing.UIManager.getCrossPlatformLookAndFeelClassName() ); }
		catch (Exception e) { System.err.println("Failed to set look and feel:"); e.printStackTrace(); }
		
		AHAModel model=new AHAModel(args);
		model.start();
	}
}
