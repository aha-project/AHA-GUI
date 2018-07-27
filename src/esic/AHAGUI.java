package esic;

import java.awt.Color;

//Copyright 2018 ESIC at WSU distributed under the MIT license. Please see LICENSE file for further info.

import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.event.ActionEvent;
import java.awt.event.MouseWheelEvent;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import org.graphstream.graph.*;

public class AHAGUI extends javax.swing.JFrame implements org.graphstream.ui.view.ViewerListener, java.awt.event.ActionListener, java.awt.event.MouseWheelListener
{
	public static java.awt.Color s_backgroundColor=java.awt.Color.black, s_foregroundColor=java.awt.Color.green;
	protected javax.swing.JLabel m_name=new javax.swing.JLabel("Name:          "), m_connections=new javax.swing.JLabel("Connections:          "), m_score=new javax.swing.JLabel("Score:          ");
	protected javax.swing.JCheckBox m_hideOSProcsCheckbox=new javax.swing.JCheckBox("Hide OS Procs"), m_hideExtCheckbox=new javax.swing.JCheckBox("Hide Ext Node"), m_showFQDN=new javax.swing.JCheckBox("DNS Names");
	protected org.graphstream.ui.swingViewer.ViewPanel m_viewPanel=null;
	protected org.graphstream.ui.view.Viewer m_viewer=null;
	protected org.graphstream.ui.view.ViewerPipe m_graphViewPump=null;
	protected AHAModel m_model=null;
	protected InspectorWindow m_inspectorWindow=null;
	public static Font s_uiFont=new Font(Font.MONOSPACED,Font.PLAIN,12);
	
	public AHAGUI(boolean bigFonts, AHAModel model)
	{
		if (bigFonts) { s_uiFont=new Font(Font.MONOSPACED,Font.PLAIN,18); }
		m_model=model;
		this.getContentPane().setBackground(s_backgroundColor);
		this.setBackground(s_backgroundColor);
		this.setTitle("AHA-GUI");
		this.setLayout(new java.awt.BorderLayout(2,0));
		this.addMouseWheelListener(this);
		
		System.setProperty("org.graphstream.ui.renderer", "org.graphstream.ui.j2dviewer.J2DGraphRenderer");
		if (m_model.m_multi) { m_model.m_graph = new org.graphstream.graph.implementations.MultiGraph("MultiGraph"); }
		else { m_model.m_graph = new org.graphstream.graph.implementations.SingleGraph("SingleGraph"); }
		m_viewer = new org.graphstream.ui.view.Viewer(m_model.m_graph, org.graphstream.ui.view.Viewer.ThreadingModel.GRAPH_IN_ANOTHER_THREAD);
		m_graphViewPump = m_viewer.newViewerPipe();
		m_graphViewPump.addViewerListener(this);
		m_graphViewPump.addSink(m_model.m_graph);
		m_viewer.enableAutoLayout();
		m_viewPanel=m_viewer.addDefaultView(false);
		m_viewPanel.setBackground(s_backgroundColor);

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
			bottomPanel.setBackground(s_backgroundColor);
			bottomPanel.setLayout(new java.awt.GridBagLayout());
			java.awt.GridBagConstraints gbc=new java.awt.GridBagConstraints();
			gbc.fill=java.awt.GridBagConstraints.HORIZONTAL;
			javax.swing.JPanel bottomButtons=new javax.swing.JPanel(); //silly, but had to do this or the clickable area of the JCheckbox gets stretched the whole width...which makes for strange clicking
			bottomButtons.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT,0,0));
			
			javax.swing.JButton fwBtn=new javax.swing.JButton("FW Suggestions");
			fwBtn.setActionCommand("listenerWindow");
			fwBtn.addActionListener(this);
			fwBtn.setFont(s_uiFont);
			fwBtn.setToolTipText(styleToolTipText("Shows the list of listening processes to aid in creation of firewall rules."));
			
			javax.swing.JButton resetBtn=new javax.swing.JButton("Reset Zoom");
			resetBtn.setActionCommand("resetZoom");
			resetBtn.addActionListener(this);
			resetBtn.setFont(s_uiFont);
			resetBtn.setToolTipText(styleToolTipText("Resets the zoom of the graph view to default."));

			javax.swing.JButton inspectorBtn=new javax.swing.JButton("Show Inspector");
			inspectorBtn.setActionCommand("showInspector");
			inspectorBtn.addActionListener(this);
			inspectorBtn.setFont(s_uiFont);
			inspectorBtn.setToolTipText(styleToolTipText("Show the graph detail inspector."));
			
			javax.swing.JComboBox<AHAModel.ScoreMethod> scoreMethod=new javax.swing.JComboBox<AHAModel.ScoreMethod>(AHAModel.ScoreMethod.values());
			scoreMethod.setActionCommand("scoreMethod");
			scoreMethod.addActionListener(this);
			scoreMethod.setFont(s_uiFont);
			scoreMethod.setToolTipText(styleToolTipText("Select the scoring method used to calculate node scores"));
			
			m_hideOSProcsCheckbox.setForeground(s_foregroundColor);
			m_hideOSProcsCheckbox.setBackground(s_backgroundColor);
			m_hideOSProcsCheckbox.setActionCommand("hideOSProcs");
			m_hideOSProcsCheckbox.addActionListener(this);
			m_hideOSProcsCheckbox.setFont(s_uiFont);
			m_hideOSProcsCheckbox.setToolTipText(styleToolTipText("Hides the usual Windows™ operating system processes, while interesting these processes can get in the way of other analysis."));
			
			m_hideExtCheckbox.setForeground(s_foregroundColor);
			m_hideExtCheckbox.setBackground(s_backgroundColor);
			m_hideExtCheckbox.setActionCommand("hideExtNode");
			m_hideExtCheckbox.addActionListener(this);
			m_hideExtCheckbox.setFont(s_uiFont);
			m_hideExtCheckbox.setToolTipText(styleToolTipText("Hides the main 'External' node from the graph that all nodes which listen on externally accessible addresses connect to."));
			
			m_showFQDN.setForeground(s_foregroundColor);
			m_showFQDN.setBackground(s_backgroundColor);
			m_showFQDN.setActionCommand("showFQDN");
			m_showFQDN.addActionListener(this);
			m_showFQDN.setFont(s_uiFont);
			m_showFQDN.setToolTipText(styleToolTipText("Show the DNS names of external nodes rather than IPs."));
			
			javax.swing.JCheckBox useCustom=new javax.swing.JCheckBox("Custom ScoreFile");
			useCustom.setActionCommand("useCustom");
			useCustom.setForeground(s_foregroundColor);
			useCustom.setBackground(s_backgroundColor);
			useCustom.setSelected(m_model.m_overlayCustomScoreFile);
			useCustom.addActionListener(this);
			useCustom.setFont(s_uiFont);
			useCustom.setToolTipText(styleToolTipText("If a custom score file was loaded, this option will apply those custom directives to the graph view."));
			
			bottomButtons.setBackground(s_backgroundColor);
			bottomButtons.add(fwBtn);
			bottomButtons.add(resetBtn);
			bottomButtons.add(inspectorBtn);
			bottomButtons.add(scoreMethod);
			bottomButtons.add(m_hideOSProcsCheckbox);
			bottomButtons.add(m_hideExtCheckbox);
			bottomButtons.add(m_showFQDN);
			bottomButtons.add(useCustom);
			bottomButtons.setBorder(null);
			
			gbc.gridx=0; gbc.gridy=0; gbc.anchor=java.awt.GridBagConstraints.WEST; gbc.weightx=10;
			m_name.setForeground(s_foregroundColor);
			m_name.setFont(s_uiFont);
			bottomPanel.add(m_name, gbc);
			gbc.gridy++;
			m_connections.setForeground(s_foregroundColor);
			m_connections.setFont(s_uiFont);
			bottomPanel.add(m_connections, gbc);
			gbc.gridy++;
			m_score.setForeground(s_foregroundColor);
			m_score.setFont(s_uiFont);
			bottomPanel.add(m_score, gbc);
			gbc.gridy++;
			bottomPanel.add(bottomButtons, gbc);
			bottomPanel.setBorder(new javax.swing.border.EmptyBorder(0,5,0,0));
		}
		javax.swing.ToolTipManager.sharedInstance().setDismissDelay(Integer.MAX_VALUE);
		javax.swing.ToolTipManager.sharedInstance().setInitialDelay(500);
		this.add(bottomPanel, java.awt.BorderLayout.SOUTH);
		this.setSize(1140, 768);
		this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		this.setVisible(true);
		getRootPane().setBorder(new javax.swing.border.CompoundBorder(new javax.swing.border.LineBorder(java.awt.Color.gray),new javax.swing.border.EmptyBorder(0,0,0,0))); //TODO: tried this to clean up the weird dashed appearance of the right gray border on macOS, but to no avail. figure it out later.
		m_inspectorWindow=new InspectorWindow((javax.swing.JFrame)this);
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

	private void showListenerSocketWindow() //shows the window that lists the listening sockets
	{
		new JFrame("Listening Processes")
		{
			{
				setLayout(new java.awt.BorderLayout());
				{
					javax.swing.JTable[] tableRefs=new javax.swing.JTable[2];
					String[] intColumns={"Listening Internally", "PID", "Proto", "Port"}, extColumns={"Listening Externally", "PID", "Proto", "Port"};
					String[][] columnHeaders=new String[2][];
					columnHeaders[0]=intColumns; columnHeaders[1]=extColumns;
					String[][][] tableData=new String[2][][];
					java.util.TreeMap<String,String> dataset=m_model.m_intListeningProcessMap;
					for (int i=0;i<2;i++)
					{
						java.util.TreeMap<String,String[]> sortMe=new java.util.TreeMap<String,String[]>();
						for (java.util.Map.Entry<String, String> entry : dataset.entrySet() )
						{
							String key=entry.getKey(), value=entry.getValue();
							String[] keyTokens=key.split("_"), valueTokens=value.split("_");
							String strArrVal[]=new String[4];
							strArrVal[0]=valueTokens[0];
							strArrVal[1]=valueTokens[1];
							strArrVal[2]=keyTokens[0].toUpperCase();
							strArrVal[3]=keyTokens[1];
							String newKey=valueTokens[0]+valueTokens[1]+"-"+keyTokens[0].toUpperCase()+keyTokens[1];
							sortMe.put(newKey, strArrVal);
						}
						String[][] data = new String[sortMe.size()][4];
						int j=0;
						for (String[] str : sortMe.values()) { data[j++]=str; }
						tableData[i]=data;
						dataset=m_model.m_extListeningProcessMap;
					}
					
					javax.swing.JScrollPane jsp=createTablesInScrollPane(columnHeaders, tableData, tableRefs, new int[]{200,50,50,50});
					add(jsp, java.awt.BorderLayout.CENTER);
					java.awt.Dimension size=jsp.getPreferredSize();
					setSize(new java.awt.Dimension((int)size.getWidth()+40,768));
					setBackground(s_backgroundColor);
					getContentPane().setBackground(s_backgroundColor);
					getRootPane().setBorder(new javax.swing.border.LineBorder(java.awt.Color.gray));
				}
				
			}
		}.setVisible(true);
	}
	
	public void actionPerformed(ActionEvent e) //swing actions go to here
	{
		if (e.getActionCommand().equals("hideOSProcs")) { m_model.hideOSProcs(m_model.m_graph, m_hideOSProcsCheckbox.isSelected()); }
		if (e.getActionCommand().equals("hideExtNode")) { m_model.hideFalseExternalNode(m_model.m_graph, m_hideExtCheckbox.isSelected()); }
		if (e.getActionCommand().equals("showFQDN")) { m_model.useFQDNLabels(m_model.m_graph, m_showFQDN.isSelected()); }
		if (e.getActionCommand().equals("listenerWindow")) { showListenerSocketWindow(); }
		if (e.getActionCommand().equals("resetZoom")) { m_viewPanel.getCamera().resetView(); }
		if (e.getActionCommand().equals("showInspector")) { m_inspectorWindow.setVisible(true); }
		if (e.getActionCommand().equals("scoreMethod")) { m_model.swapNodeStyles(((AHAModel.ScoreMethod)((javax.swing.JComboBox<?>)e.getSource()).getSelectedItem()), System.currentTimeMillis()); }
		if (e.getActionCommand().equals("useCustom"))
		{
			m_model.m_overlayCustomScoreFile=((javax.swing.JCheckBox)e.getSource()).isSelected();
			m_model.exploreAndScore(m_model.m_graph);
		}
	}

	public void mouseWheelMoved(MouseWheelEvent e) //zooms graph in and out using mouse wheel
	{
		m_viewPanel.getCamera().setViewPercent(m_viewPanel.getCamera().getViewPercent()+((double)e.getWheelRotation()/100d));
	}
	public void buttonReleased(String id) {} //graph mouse function
	public void viewClosed(String arg0) {} //graph viewer interface function
	
	public static javax.swing.JScrollPane createTablesInScrollPane(String[][] columnHeaders, String[][][] initialData, javax.swing.JTable[] tableRefs, int[] columnWidths)
	{
		javax.swing.JPanel scrollContent=new javax.swing.JPanel();
		scrollContent.setLayout(new javax.swing.BoxLayout(scrollContent, javax.swing.BoxLayout.Y_AXIS));
		scrollContent.setBackground(AHAGUI.s_backgroundColor);
		
		for (int i=0; i<tableRefs.length; i++)
		{
			if (tableRefs[i]==null) 
			{ 
				tableRefs[i]=new javax.swing.JTable() 
				{
					public java.awt.Component prepareRenderer(javax.swing.table.TableCellRenderer renderer, int row, int column) 
					{
						java.awt.Component c = super.prepareRenderer(renderer, row, column);
		        if (c instanceof javax.swing.JComponent) 
		        {
	            javax.swing.JComponent jc = (javax.swing.JComponent) c;
	            Object o=getValueAt(row, column);
	            if (o!=null) {  jc.setToolTipText(o.toString()); }
		        }
		        return c;
					}
				}; 
			}
			javax.swing.table.DefaultTableModel dm=(javax.swing.table.DefaultTableModel)tableRefs[i].getModel();
			dm.setDataVector(initialData[i], columnHeaders[i]);
			tableRefs[i].setForeground(AHAGUI.s_foregroundColor);
			tableRefs[i].setBackground(AHAGUI.s_backgroundColor);
			tableRefs[i].getTableHeader().setForeground(AHAGUI.s_foregroundColor);
			tableRefs[i].getTableHeader().setBackground(AHAGUI.s_backgroundColor);
			tableRefs[i].getTableHeader().setBorder(null);
			tableRefs[i].setGridColor(Color.DARK_GRAY);
			tableRefs[i].setBorder(null);
			tableRefs[i].setFont(s_uiFont);
			for (int j=0;j<tableRefs[i].getColumnModel().getColumnCount() && j<columnWidths.length; j++) { tableRefs[i].getColumnModel().getColumn(j).setPreferredWidth(columnWidths[j]); }
			tableRefs[i].getTableHeader().setBackground(Color.DARK_GRAY);
			tableRefs[i].setPreferredScrollableViewportSize(tableRefs[i].getPreferredSize());
			tableRefs[i].setAlignmentY(TOP_ALIGNMENT);
			tableRefs[i].getTableHeader().setAlignmentY(TOP_ALIGNMENT);
			tableRefs[i].setAutoCreateRowSorter(true);
			scrollContent.add(tableRefs[i].getTableHeader());
			scrollContent.add(tableRefs[i]);
		}
		JScrollPane scrollPane = new JScrollPane(scrollContent);
		scrollPane.setForeground(AHAGUI.s_foregroundColor);
		scrollPane.setBackground(AHAGUI.s_backgroundColor);
		scrollPane.getViewport().setForeground(AHAGUI.s_foregroundColor);
		scrollPane.getViewport().setBackground(AHAGUI.s_backgroundColor);
		scrollPane.setViewportBorder(javax.swing.BorderFactory.createEmptyBorder());
		scrollPane.setBorder(javax.swing.BorderFactory.createEmptyBorder());
		return scrollPane;
	}
	
	public static class InspectorWindow extends JFrame
	{
		private javax.swing.JCheckBox m_changeOnMouseOver=new javax.swing.JCheckBox("Update Inspector on MouseOver",false);
		private String[][] m_columnHeaders=new String[5][];
		private javax.swing.JTable[] m_tableRefs= new javax.swing.JTable[5];
		private static final int INFO_TABLE=0, INTERNAL_PORTS_TABLE=1, EXTERNAL_PORTS_TABLE=2, CONNECTIONS_TABLE=3, SCORE_TABLE=4;
		
		public InspectorWindow(javax.swing.JFrame parent)
		{
			setTitle("Inspector Window");
			setBackground(AHAGUI.s_backgroundColor);
			getContentPane().setBackground(s_backgroundColor);
			getRootPane().setBorder(new javax.swing.border.LineBorder(java.awt.Color.gray));
			setLayout(new java.awt.GridBagLayout());
			java.awt.GridBagConstraints gbc=new java.awt.GridBagConstraints();
			gbc.insets = new java.awt.Insets(2, 5, 2, 5);
			gbc.fill=gbc.fill=GridBagConstraints.BOTH;
			gbc.gridx=0; gbc.gridy=0;  gbc.weightx=1; gbc.weighty=10;
			
			
			String[] infoTblHdr= {"Info"}, intPortsTblHdr={"Open Internal Port", "Proto"}, extPortsTblHdr={"Open External Port", "Proto"}, connectionTblHdr={"Connected Process Name", "PID"}, scoreTblHdr={"Score Metric", "Value"};
			m_columnHeaders[INFO_TABLE]=infoTblHdr;
			m_columnHeaders[INTERNAL_PORTS_TABLE]=intPortsTblHdr;
			m_columnHeaders[EXTERNAL_PORTS_TABLE]=extPortsTblHdr;
			m_columnHeaders[CONNECTIONS_TABLE]=connectionTblHdr;
			m_columnHeaders[SCORE_TABLE]=scoreTblHdr;
			
			String[][][] emptyData=new String[5][1][1];
			for (int i=0; i<emptyData.length;i++) { emptyData[i][0][0]="None"; }
			
			gbc.weighty=100;
			this.add(createTablesInScrollPane(m_columnHeaders, emptyData, m_tableRefs, new int[]{160,40}), gbc);
			
			m_changeOnMouseOver.setForeground(AHAGUI.s_foregroundColor);
			m_changeOnMouseOver.setBackground(AHAGUI.s_backgroundColor);
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
			
			String[][] infoData=null, intPorts=null, extPorts=null, connectionData=null, scoreReasons=null;
			
			try
			{
				String[] infoLines=getNameString(node,"\n").trim().split("\n");
				infoData=new String[infoLines.length][1];
				for (int i=0;i<infoLines.length;i++) { infoData[i][0]=infoLines[i]; }
			} catch (Exception e) { e.printStackTrace(); }
			
			try
			{
				String[] ports=AHAModel.getCommaSepKeysFromStringMap(element.getAttribute("ui.localListeningPorts")).split(", ");
				intPorts=new String[ports.length][2];
				for (int i=0;i<ports.length;i++)
				{
					String[] temp=ports[i].split("_");
					if (temp.length > 1)
					{
						intPorts[i]=new String[2];
						intPorts[i][0]=temp[1];
						intPorts[i][1]=temp[0]; //reverse array
					} else { intPorts[i][0]="None"; }
				}
			} catch (Exception e) { e.printStackTrace(); }
			
			try
			{
				String[] ports=AHAModel.getCommaSepKeysFromStringMap(element.getAttribute("ui.extListeningPorts")).split(", ");
				extPorts=new String[ports.length][2];
				for (int i=0;i<ports.length;i++)
				{
					String[] temp=ports[i].split("_");
					if (temp.length > 1)
					{
						extPorts[i]=new String[2];
						extPorts[i][0]=temp[1];
						extPorts[i][1]=temp[0]; //reverse array
					} else { extPorts[i][0]="None"; }
				}
			} catch (Exception e) { e.printStackTrace(); }
			
			try
			{
				String[] connections=getNodeConnectionString(node,model).split(", ");
				connectionData=new String[connections.length][2];
				for (int i=0;i<connections.length;i++) { connectionData[i]=connections[i].split("_"); }
			} catch (Exception e) { e.printStackTrace(); }
			
			try
			{
				String[] scores=getNodeScoreRasonString(node, true).split(", ");
				scoreReasons=new String[scores.length][2];
				for (int i=0;i<scores.length;i++) { scoreReasons[i]=scores[i].split("="); }
			} catch (Exception e) { e.printStackTrace(); }
			
			final String[][][] data=new String[5][][];
			data[INFO_TABLE]=infoData; 
			data[INTERNAL_PORTS_TABLE]=intPorts; 
			data[EXTERNAL_PORTS_TABLE]=extPorts;
			data[CONNECTIONS_TABLE]=connectionData;
			data[SCORE_TABLE]=scoreReasons;
			
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
		AHAModel model=new AHAModel(args);
		model.start();
	}
}
