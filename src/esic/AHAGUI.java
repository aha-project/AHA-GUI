package esic;

//Copyright 2018 ESIC at WSU distributed under the MIT license. Please see LICENSE file for further info.

import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.event.ActionEvent;
import java.awt.event.MouseWheelEvent;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import org.graphstream.graph.*;
import esic.AHAModel.ScoreMethod;

public class AHAGUI extends javax.swing.JFrame implements org.graphstream.ui.view.ViewerListener, java.awt.event.ActionListener, java.awt.event.MouseWheelListener
{
	protected javax.swing.JLabel m_name=new javax.swing.JLabel("Name:          "), m_connections=new javax.swing.JLabel("Connections:          "), m_score=new javax.swing.JLabel("Score:          ");
	protected javax.swing.JCheckBox m_hideOSProcsCheckbox=new javax.swing.JCheckBox("Hide OS Procs"), m_hideExtCheckbox=new javax.swing.JCheckBox("Hide Ext Node"), m_showFQDN=new javax.swing.JCheckBox("DNS Names");
	protected org.graphstream.ui.swingViewer.ViewPanel m_viewPanel=null;
	protected org.graphstream.ui.view.Viewer m_viewer=null;
	protected org.graphstream.ui.view.ViewerPipe m_graphViewPump=null;
	protected AHAModel m_model=null;
	protected InspectorWindow m_inspectorWindow=null;
	protected java.util.concurrent.atomic.AtomicReference<TableHolder> m_report=new java.util.concurrent.atomic.AtomicReference<TableHolder>();
	
	public AHAGUI(AHAModel model)
	{
		m_model=model;
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setSize(1152, 768);
		setTitle("AHA-GUI");
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
		{
			bottomPanel.setLayout(new java.awt.GridBagLayout());
			java.awt.GridBagConstraints gbc=new java.awt.GridBagConstraints();
			gbc.fill=java.awt.GridBagConstraints.HORIZONTAL;
			javax.swing.JPanel bottomButtons=new javax.swing.JPanel(); //silly, but had to do this or the clickable area of the JCheckbox gets stretched the whole width...which makes for strange clicking
			bottomButtons.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT,0,0));
			
			javax.swing.JButton dataViewBtn=new javax.swing.JButton("Open Data View");
			dataViewBtn.setActionCommand("dataView");
			dataViewBtn.addActionListener(this);
			dataViewBtn.setToolTipText(styleToolTipText("Shows the list of listening processes to aid in creation of firewall rules."));
			
			javax.swing.JButton resetBtn=new javax.swing.JButton("Reset Zoom");
			resetBtn.setActionCommand("resetZoom");
			resetBtn.addActionListener(this);
			resetBtn.setToolTipText(styleToolTipText("Resets the zoom of the graph view to default."));

			javax.swing.JButton inspectorBtn=new javax.swing.JButton("Show Inspector");
			inspectorBtn.setActionCommand("showInspector");
			inspectorBtn.addActionListener(this);
			inspectorBtn.setToolTipText(styleToolTipText("Show the graph detail inspector."));
			
			javax.swing.JComboBox<AHAModel.ScoreMethod> scoreMethod=new javax.swing.JComboBox<AHAModel.ScoreMethod>(AHAModel.ScoreMethod.values());
			scoreMethod.setActionCommand("scoreMethod");
			scoreMethod.addActionListener(this);
			scoreMethod.setToolTipText(styleToolTipText("Select the scoring method used to calculate node scores"));
//			for (int i = 1; i < scoreMethod.getComponentCount(); i++) 
//			{
//			    if (scoreMethod.getComponent(i) instanceof javax.swing.JComponent) {
//			        ((javax.swing.JComponent) scoreMethod.getComponent(i)).setBorder(new javax.swing.border.EmptyBorder(0,0,0,0));
//			        System.err.println("set empty border for element "+i);
//			    }
//			    if (scoreMethod.getComponent(i) instanceof javax.swing.AbstractButton) {
//			        ((javax.swing.AbstractButton) scoreMethod.getComponent(i)).setBorderPainted(false);
//			        System.err.println("did the other thing for element "+i);
//			    }
//			}
			
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
	
	public static void applyTheme(Font uiFont)
	{ //Apply theme for JCheckbox, JComboBox, JLabel,  JSrollPane, JTabbedPane, JTable
//		// Need to figure out what key something is named? The stuff below will search and then exit for keys containing the string
//		java.util.List<String> t=new java.util.ArrayList<String>(2048);
//		for (Object key : javax.swing.UIManager.getLookAndFeelDefaults().keySet()) { t.add(key.toString()); }
//		java.util.Collections.sort(t);
//		for (String key : t ) { if (key.toLowerCase().contains("background")) { System.out.println(key); } }
		java.awt.Color backgroundColor=java.awt.Color.BLACK, foregroundColor=java.awt.Color.GREEN, accentColor=java.awt.Color.DARK_GRAY.darker().darker();//, dbugcolor=java.awt.Color.ORANGE;
		javax.swing.UIManager.put("Button.background", accentColor.brighter().brighter());
		//no border
		javax.swing.UIManager.put("Button.darkShadow", backgroundColor);
		javax.swing.UIManager.put("Button.focus", accentColor.brighter().brighter()); //remove selection reticle
		javax.swing.UIManager.put("Button.font",uiFont);
		javax.swing.UIManager.put("Button.foreground", foregroundColor);
		javax.swing.UIManager.put("Button.highlight", backgroundColor);//accentColor.brighter().brighter().brighter());
		javax.swing.UIManager.put("Button.light", backgroundColor);
		javax.swing.UIManager.put("Button.select", backgroundColor);
		javax.swing.UIManager.put("Button.shadow", backgroundColor);  
		

		javax.swing.UIManager.put("CheckBox.foreground", foregroundColor);
		javax.swing.UIManager.put("CheckBox.background", backgroundColor);
		javax.swing.UIManager.put("CheckBox.focus", backgroundColor);
		javax.swing.UIManager.put("CheckBox.font", uiFont);
		javax.swing.UIManager.put("CheckBox.gradient", java.util.Arrays.asList( new Object[] {new Float(0f), new Float(0f), foregroundColor.darker(), foregroundColor.darker(), foregroundColor.darker() }));
		//		javax.swing.Icon i=javax.swing.UIManager.getIcon("CheckBox.icon"); //eventually figure out some way to green the icon up?
		//		javax.swing.GrayFilter gf=new javax.swing.GrayFilter(true,100);
		
		
		javax.swing.UIManager.put("ComboBox.background", accentColor.brighter().brighter());
		javax.swing.UIManager.put("ComboBox.buttonBackground", accentColor.brighter().brighter()); 
		javax.swing.UIManager.put("ComboBox.buttonDarkShadow", accentColor.brighter().brighter()); 
		javax.swing.UIManager.put("ComboBox.buttonHighlight", accentColor.brighter().brighter()); 
		javax.swing.UIManager.put("ComboBox.buttonShadow", accentColor.brighter().brighter());
		javax.swing.UIManager.put("ComboBox.disabledBackground", accentColor.brighter().brighter());
		javax.swing.UIManager.put("ComboBox.disabledForeground", foregroundColor);
		javax.swing.UIManager.put("ComboBox.font", uiFont); 
		javax.swing.UIManager.put("ComboBox.foreground", foregroundColor);
		javax.swing.UIManager.put("ComboBox.selectionForeground", foregroundColor);
		javax.swing.UIManager.put("ComboBox.selectionBackground", accentColor.brighter().brighter()); //ComboBoxUI
		
		
		javax.swing.UIManager.put("Label.foreground", foregroundColor);
		javax.swing.UIManager.put("Label.background", backgroundColor);
		javax.swing.UIManager.put("Label.font", uiFont);
		
		javax.swing.UIManager.put("Frame.foreground", foregroundColor);
		javax.swing.UIManager.put("Frame.background", backgroundColor);
		javax.swing.UIManager.put("Panel.foreground", foregroundColor);
		javax.swing.UIManager.put("Panel.background", backgroundColor);
		
		javax.swing.UIManager.put("ScrollBar.track", backgroundColor);
		javax.swing.UIManager.put("ScrollBar.thumbDarkShadow", backgroundColor);
		javax.swing.UIManager.put("ScrollBar.thumb", accentColor.brighter().brighter().brighter());
		javax.swing.UIManager.put("ScrollBar.thumbHighlight", accentColor.brighter().brighter().brighter());
		javax.swing.UIManager.put("ScrollBar.thumbShadow", accentColor.brighter().brighter().brighter());
		javax.swing.UIManager.put("ScrollBarUI", javax.swing.plaf.basic.BasicScrollBarUI.class.getName() );
		javax.swing.UIManager.put("ScrollBar.width", 8);
		
		javax.swing.UIManager.put("ScrollPane.foreground", foregroundColor);
		javax.swing.UIManager.put("ScrollPane.background", backgroundColor);
		
		javax.swing.UIManager.put("TabbedPane.foreground", foregroundColor);
		javax.swing.UIManager.put("TabbedPane.background", backgroundColor);
		javax.swing.UIManager.put("TabbedPane.light", backgroundColor);
		javax.swing.UIManager.put("TabbedPane.borderHightlightColor", backgroundColor);
		javax.swing.UIManager.put("TabbedPane.selected", accentColor.brighter().brighter().brighter());
		javax.swing.UIManager.put("TabbedPane.focus",accentColor.brighter().brighter().brighter());
		javax.swing.UIManager.put("TabbedPane.selectHighlight",accentColor.brighter().brighter().brighter());
		javax.swing.UIManager.put("TabbedPane.darkShadow", accentColor.brighter().brighter().brighter()); //removes almost imperceptible blue glow around inactive tab edge
		javax.swing.UIManager.put("TabbedPane.contentBorderInsets", new java.awt.Insets(0,0,0,0));
		javax.swing.UIManager.put("TabbedPane.tabsOverlapBorder", true); 
		
		javax.swing.UIManager.put("TableUI", javax.swing.plaf.basic.BasicTableUI.class.getName() );
		javax.swing.UIManager.put("Table.foreground", foregroundColor);
		javax.swing.UIManager.put("Table.background", backgroundColor);
		javax.swing.UIManager.put("Table.focusCellForeground", foregroundColor);
		javax.swing.UIManager.put("Table.focusCellBackground", backgroundColor);
		javax.swing.UIManager.put("Table.dropCellBackground", backgroundColor);
		javax.swing.UIManager.put("Table.gridColor", accentColor);
		javax.swing.UIManager.put("Table.font", uiFont); //
		javax.swing.UIManager.put("Table.selectionBackground", foregroundColor.darker());
		javax.swing.UIManager.put("Table.focusCellHighlightBorder", foregroundColor.darker());
		javax.swing.UIManager.put("Table.selectionForeground", backgroundColor);
		javax.swing.UIManager.put("Table.sortIconColor", foregroundColor); 
//		javax.swing.UIManager.put("Table.ascendingSortIcon","");
//		javax.swing.UIManager.put("Table.descendingSortIcon","");
//		javax.swing.ImageIcon i= new javax.swing.ImageIcon();
//		java.awt.Image i2;
//		i2.
		try
		{ 
			javax.swing.UIManager.put("Table.ascendingSortIcon",new sun.swing.SwingLazyValue("sun.swing.icon.SortArrowIcon",null, new Object[] { Boolean.TRUE, "Table.sortIconColor" }));
			javax.swing.UIManager.put("Table.descendingSortIcon",new sun.swing.SwingLazyValue("sun.swing.icon.SortArrowIcon",null,new Object[] { Boolean.FALSE, "Table.sortIconColor" }));
		} catch (Exception e) {}
		
		javax.swing.UIManager.put("TableHeaderUI", javax.swing.plaf.basic.BasicTableHeaderUI.class.getName() );
		javax.swing.UIManager.put("TableHeader.foreground", foregroundColor);
		javax.swing.UIManager.put("TableHeader.background", accentColor);
		javax.swing.UIManager.put("TableHeader.cellBorder", new javax.swing.border.BevelBorder(javax.swing.border.BevelBorder.RAISED));
		javax.swing.UIManager.put("TableHeader.font", uiFont); 
		
		
		javax.swing.UIManager.put("ToolTip.foreground", java.awt.Color.BLACK);
		javax.swing.UIManager.put("ToolTip.border", java.awt.Color.WHITE);
		javax.swing.UIManager.put("ToolTip.background", java.awt.Color.WHITE);
		javax.swing.UIManager.put("ToolTip.font", uiFont);
		
		javax.swing.UIManager.put("Viewport.foreground", foregroundColor);
		javax.swing.UIManager.put("Viewport.background", accentColor);
		
		javax.swing.UIManager.put("controlDkShadow", backgroundColor);
		javax.swing.UIManager.put("controlHighlight", backgroundColor);
		javax.swing.UIManager.put("controlLtHighlight", backgroundColor);
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

	protected static class TableHolder
	{
		protected javax.swing.JTable[] tables;
		protected String[][] columnNames;
		protected int[][] columnWidths;
		protected Object[][][] tableData;
	}
	
	protected TableHolder generateReport()
	{
		long time=System.currentTimeMillis();
		int NUM_SCORE_TABLES=2, tableNumber=0;
		String[][] columnHeaders= {{"Scan Information", "Value"},{"Process", "PID", "User","Connections","Signed","ASLR","DEP","CFG","HiVA", "Score", "ECScore", "WorstPrivScore"},{}};
		Object[][][] tableData=new Object[NUM_SCORE_TABLES][][];
		{ //general info
			Object[][] data=new Object[8][2];
			int i=0;
			
			data[i][0]="Local Addresses of Scanned Machine";
			data[i++][1]=m_model.m_knownAliasesForLocalComputer.keySet().toString();
			data[i][0]="Local Time of Host Scan";
			data[i++][1]=m_model.m_miscMetrics.get("detectiontime");
			
			{
				int numExt=0, worstScore=100;
				double denominatorAccumulator=0.0d;
				String worstScoreName="";
				System.out.print("computing harmonic mean:");
				for (Node n : m_model.m_graph)
				{
					if (n.getAttribute("username")!=null && n.getAttribute("ui.hasExternalConnection")!=null && n.getAttribute("aslr")!=null && !n.getAttribute("aslr").equals("") && !n.getAttribute("aslr").equals("scanerror")) 
					{ 
						numExt++;
						String normalScore=n.getAttribute(AHAModel.scrMethdAttr(ScoreMethod.Normal));
						try
						{
							Integer temp=Integer.parseInt(normalScore);
							if (worstScore > temp) { worstScore=temp; worstScoreName=n.getId();}
							System.out.print(temp+",");
							denominatorAccumulator+=(1.0d/(double)temp);
						} catch (Exception e) {}
					}
				}
				data[i][0]="Number of Externally Acccessible Processes";
				data[i++][1]=Integer.toString(numExt);
				data[i][0]="Score of Worst Externally Accessible Scannable Process";
				data[i++][1]="Process: "+worstScoreName+"  Score: "+worstScore;
				data[i][0]="Harmonic Mean of Scores of all Externally Accessible Processes";
				String harmonicMean="Harominc Mean Computation Error";
				if (denominatorAccumulator > 0.000001d) { harmonicMean=String.format("%.2f", ((double)numExt)/denominatorAccumulator); }
				data[i++][1]=harmonicMean;
				System.out.println(" mean="+harmonicMean);
			}
			
			{
				int numInt=0, worstScore=100;
				double denominatorAccumulator=0.0d;
				String worstScoreName="";
				System.out.print("computing harmonic mean:");
				for (Node n : m_model.m_graph)
				{
					if (n.getAttribute("username")!=null && n.getAttribute("ui.hasExternalConnection")==null && n.getAttribute("aslr")!=null && !n.getAttribute("aslr").equals("") && !n.getAttribute("aslr").equals("scanerror")) 
					{ 
						numInt++;
						String normalScore=n.getAttribute(AHAModel.scrMethdAttr(ScoreMethod.Normal));
						try
						{
							Integer temp=Integer.parseInt(normalScore);
							if (worstScore > temp) { worstScore=temp; worstScoreName=n.getId();}
							System.out.print(temp+",");
							denominatorAccumulator+=(1.0d/(double)temp);
						} catch (Exception e) {}
					}
				}
				data[i][0]="Number of Internally Acccessible Processes";
				data[i++][1]=Integer.toString(numInt);
				data[i][0]="Score of Worst Internally Accessible Scannable Process";
				data[i++][1]="Process: "+worstScoreName+"  Score: "+worstScore;
				data[i][0]="Harmonic Mean of Scores of all Internally Accessible Processes";
				String harmonicMean="Harominc Mean Computation Error";
				if (denominatorAccumulator > 0.000001d) { harmonicMean=String.format("%.2f", ((double)numInt)/denominatorAccumulator); }
				data[i++][1]=harmonicMean;
				System.out.println(" mean="+harmonicMean);
			}
			tableData[tableNumber]=data;
		}
		tableNumber++;
		{ //general node info
			tableData[tableNumber]=new Object[m_model.m_graph.getNodeCount()][];
			int i=0;
			for (Node n : m_model.m_graph)
			{
				int j=0; //tired of reordering numbers after moving columns around
				Object[] data=new Object[columnHeaders[tableNumber].length]; //if we run out of space we forgot to make a column header anyway
				String name=n.getAttribute("processname");
				if (name==null) { name=n.getAttribute("ui.label"); }
				data[j++]=name;
				data[j++]=strAsInt(n.getAttribute("pid"));
				data[j++]=n.getAttribute("username");
				data[j++]=Integer.valueOf(n.getEdgeSet().size()); //cant use wrapInt here because  //TODO: deduplicate connection set?
				data[j++]=n.getAttribute("authenticode");
				data[j++]=n.getAttribute("aslr"); //these all have to be lowercase to work remember :)
				data[j++]=n.getAttribute("dep");
				data[j++]=n.getAttribute("controlflowguard");
				data[j++]=n.getAttribute("highentropyva");
				data[j++]=strAsInt(n.getAttribute(AHAModel.scrMethdAttr(ScoreMethod.Normal)));
				data[j++]=strAsInt(n.getAttribute(AHAModel.scrMethdAttr(ScoreMethod.ECScore)));
				data[j++]=strAsInt(n.getAttribute(AHAModel.scrMethdAttr(ScoreMethod.WorstCommonProc)));
				tableData[tableNumber][i++]=data;
			}
		}
		TableHolder ret=new TableHolder();
		ret.columnNames=columnHeaders;
		ret.tableData=tableData;
		ret.tables=new javax.swing.JTable[NUM_SCORE_TABLES];
		ret.columnWidths= new int[][]{{180,40,240,86,50,44,44,44,44,44,44,80}};
		System.out.println("Elapsed time to create report="+(System.currentTimeMillis()-time));
		return ret;
	}
	
	private void showDataView(AHAGUI parent) //shows the window that lists the listening sockets
	{
		new JFrame("Data View")
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
					TableHolder t=parent.m_report.get();
					if (t==null)
					{
						System.err.println("Report was not readly, creating new report");
						t=generateReport();
						parent.m_report.set(t);
					}
					tabBar.add("Vulnerability Metrics", createTablesInScrollPane(t.columnNames, t.tableData, t.tables, t.columnWidths[0]) );
				}
//				{ // Find data for, create table, etc for the "Graph Data" view
//					javax.swing.JTable[] nodeTbl=new javax.swing.JTable[1];
//					String[][] columnHeaders= {{"Key", "Value"},{}};
//					Object[][][] tableData=new Object[1][10][2];
//					int i=0;
//					tableData[0][i][0]="Local Addresses of Scanned Machine";
//					tableData[0][i++][1]=m_model.m_knownAliasesForLocalComputer.keySet().toString();
//					tableData[0][i][0]="Local Time of Scan";
//					tableData[0][i++][1]=m_model.m_miscMetrics.get("detectiontime");
//					tabBar.add("Graph Metrics", createTablesInScrollPane(columnHeaders, tableData, nodeTbl, new int[]{200,500}));
//				}
				{ // Find data for, create table, etc for the "Listening Processes" tab
					javax.swing.JTable[] fwTables=new javax.swing.JTable[2];
					String[][] columnHeaders={{"Listening Internally", "PID", "Proto", "Port"},{"Listening Externally", "PID", "Proto", "Port"}};
					Object[][][] tableData=new Object[2][][];
					java.util.TreeMap<String,String> dataset=m_model.m_intListeningProcessMap;
					for (int i=0;i<2;i++)
					{
						java.util.TreeMap<String,Object[]> sortMe=new java.util.TreeMap<String,Object[]>();
						for (java.util.Map.Entry<String, String> entry : dataset.entrySet() )
						{
							String key=entry.getKey(), value=entry.getValue();
							String[] keyTokens=key.split("_"), valueTokens=value.split("_");
							Object strArrVal[]=new Object[4];
							strArrVal[0]=valueTokens[0];
							strArrVal[1]=strAsInt(valueTokens[1]);
							strArrVal[2]=keyTokens[0].toUpperCase();
							strArrVal[3]=strAsInt(keyTokens[1]);
							String newKey=valueTokens[0]+valueTokens[1]+"-"+keyTokens[0].toUpperCase()+keyTokens[1];
							sortMe.put(newKey, strArrVal);
						}
						Object[][] data = new Object[sortMe.size()][4];
						int j=0;
						for (Object[] lineDat : sortMe.values()) { data[j++]=lineDat; }
						tableData[i]=data;
						dataset=m_model.m_extListeningProcessMap;
					}
					tabBar.add("Listening Processes", createTablesInScrollPane(columnHeaders, tableData, fwTables, new int[]{200,50,50,50}));
				}
			}
		}.setVisible(true);
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
		JScrollPane scrollPane = new JScrollPane(scrollContent);
		scrollPane.setViewportBorder(javax.swing.BorderFactory.createEmptyBorder());
		scrollPane.setBorder(javax.swing.BorderFactory.createEmptyBorder());
		return scrollPane;
	}
	
	public static Object strAsInt(String s) //try to box in an integer (important for making sorters in tables work) but fall back to just passing through the Object
	{ 
		try { if (s!=null ) { return Integer.valueOf(s); } }
		catch (NumberFormatException nfe) {} //we really don't care about this, we'll just return the original object
		catch (Exception e) { System.out.println("s="+s);e.printStackTrace(); } //something else weird happened, let's print about it
		return s;
	}
	
	public static class InspectorWindow extends JFrame
	{
		private javax.swing.JCheckBox m_changeOnMouseOver=new javax.swing.JCheckBox("Update Inspector on MouseOver",false);
		private String[][] m_columnHeaders={{"Info"},{"Open Internal Port", "Proto"},{"Open External Port", "Proto"},{"Connected Process Name", "PID"}, {"Score Metric", "Value"}};
		private javax.swing.JTable[] m_tableRefs= new javax.swing.JTable[5];
		
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
			this.add(createTablesInScrollPane(m_columnHeaders, initialData, m_tableRefs, new int[]{160,40}), gbc);
			
			gbc.gridy++;
			gbc.weighty=1;
			gbc.fill=java.awt.GridBagConstraints.HORIZONTAL;
			this.add(m_changeOnMouseOver, gbc);
			
			this.setLocation(parent.getLocation().x+parent.getWidth(), 0);
			this.setSize(m_changeOnMouseOver.getPreferredSize().width+60,768);
			this.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
			this.setVisible(true);
		}
		
		public void updateDisplayForGraphElement(org.graphstream.ui.graphicGraph.GraphicElement element, boolean occuredFromMouseOver, AHAModel model)
		{
			if (element==null || occuredFromMouseOver && !m_changeOnMouseOver.isSelected()) { return; }
			Node node=model.m_graph.getNode(element.getId());
			if (node==null) { return; }
			
			Object[][] infoData=null, intPorts=null, extPorts=null, connectionData=null, scoreReasons=null;
			
			try
			{
				String[] infoLines=getNameString(node,"\n").trim().split("\n");
				infoData=new String[infoLines.length][1];
				for (int i=0;i<infoLines.length;i++) { infoData[i][0]=infoLines[i]; }
			} catch (Exception e) { e.printStackTrace(); }
			
			try
			{
				String[] ports=AHAModel.getCommaSepKeysFromStringMap(element.getAttribute("ui.localListeningPorts")).split(", ");
				intPorts=new Object[ports.length][2];
				for (int i=0;i<ports.length;i++)
				{
					String[] temp=ports[i].split("_");
					if (temp.length > 1)
					{
						intPorts[i]=new Object[2];
						intPorts[i][0]=strAsInt(temp[1]);
						intPorts[i][1]=temp[0].toUpperCase(); //reverse array
					} else { intPorts[i][0]="None"; }
				}
			} catch (Exception e) { e.printStackTrace(); }
			
			try
			{
				String[] ports=AHAModel.getCommaSepKeysFromStringMap(element.getAttribute("ui.extListeningPorts")).split(", ");
				extPorts=new Object[ports.length][2];
				for (int i=0;i<ports.length;i++)
				{
					String[] temp=ports[i].split("_");
					if (temp.length > 1)
					{
						extPorts[i]=new Object[2];
						extPorts[i][0]=strAsInt(temp[1]);
						extPorts[i][1]=temp[0].toUpperCase(); //reverse array
					} else { extPorts[i][0]="None"; }
				}
			} catch (Exception e) { e.printStackTrace(); }
			
			try
			{
				String[] connections=getNodeConnectionString(node,model).split(", ");
				connectionData=new Object[connections.length][2];
				for (int i=0;i<connections.length;i++) 
				{ 
					String[] tokens=connections[i].split("_");
					connectionData[i][0]=tokens[0];
					if (tokens.length > 1) { connectionData[i][1]=strAsInt(tokens[1]); }
				}
			} catch (Exception e) { e.printStackTrace(); }
			
			try
			{
				String[] scores=getNodeScoreRasonString(node, true).split(", ");
				scoreReasons=new String[scores.length][2];
				for (int i=0;i<scores.length;i++) { scoreReasons[i]=scores[i].split("="); }
			} catch (Exception e) { e.printStackTrace(); }
			
			final Object[][][] data={infoData,intPorts,extPorts,connectionData,scoreReasons}; //as long as this order is correct, everything will work :)
			
			javax.swing.SwingUtilities.invokeLater(new Runnable() //perform task on gui thread
			{
				public void run()
				{
					for (int i=0;i<data.length;i++)
					{
						try
						{
							javax.swing.table.DefaultTableModel dm=(javax.swing.table.DefaultTableModel)m_tableRefs[i].getModel();
							dm.setDataVector(data[i], m_columnHeaders[i]);
							m_tableRefs[i].getColumnModel().getColumn(0).setPreferredWidth(140);
							if (m_tableRefs[i].getColumnModel().getColumnCount() > 1)
							{
								m_tableRefs[i].getColumnModel().getColumn(1).setPreferredWidth(40);
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
	
	public static String getNodeScoreRasonString(Node node, boolean extendedReason)
	{ 
		if (node==null) { return " "; }
		String score=node.getAttribute("ui.scoreReason");
		if (extendedReason) { score=node.getAttribute("ui.scoreExtendedReason"); }
		if (score==null) { score=" "; }
		if (node.getAttribute("ui.class")!=null && node.getAttribute("ui.class").toString().toLowerCase().equals("external")) { score="Score: N/A"; } //this was requested to make the UI feel cleaner, since nothing can be done to help the score of an external node anyway.
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
			if (!connections.contains(t2)) //some vertices have multiple connections between them, only print once
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
		final String connections="Connections: "+getNodeConnectionString(node,m_model), nameText=getNameString(node,"  "), scoreReason="Score: "+getNodeScoreRasonString(node, false);

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
		try { javax.swing.UIManager.setLookAndFeel( javax.swing. UIManager.getCrossPlatformLookAndFeelClassName()); } 
		catch (Exception e) { }
		
		AHAModel model=new AHAModel(args);
		model.start();
	}
}
