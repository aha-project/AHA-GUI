package esic;

import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import javax.swing.JFrame;

import org.graphstream.graph.*;

public class AHAGUI extends javax.swing.JFrame implements org.graphstream.ui.view.ViewerListener, java.awt.event.ActionListener, java.awt.event.MouseWheelListener
{
	protected java.awt.Color s_backgroundColor=java.awt.Color.black, s_foregroundColor=java.awt.Color.green;
	protected javax.swing.JLabel m_name=new javax.swing.JLabel("Name:          "), m_connections=new javax.swing.JLabel("Connections:          "), m_score=new javax.swing.JLabel("Score:          ");
	protected javax.swing.JCheckBox m_hideOSProcsCheckbox=new javax.swing.JCheckBox("Hide OS Procs"), m_hideExtCheckbox=new javax.swing.JCheckBox("Hide Ext Node"), m_showFQDN=new javax.swing.JCheckBox("DNS Names");
	protected org.graphstream.ui.swingViewer.ViewPanel m_viewPanel=null;
	protected org.graphstream.ui.view.Viewer m_viewer=null;
	protected org.graphstream.ui.view.ViewerPipe m_graphViewPump=null;
	protected AHAModel m_model;
	
	public AHAGUI(boolean bigFonts, AHAModel model)
	{
		Font uiFont=new Font(Font.MONOSPACED,Font.PLAIN,12);
		if (bigFonts) { uiFont=new Font(Font.MONOSPACED,Font.PLAIN,18); }
		m_model=model;
		this.getContentPane().setBackground(s_backgroundColor);
		this.setBackground(s_backgroundColor);
		this.setTitle("AHA-GUI");
		this.setLayout(new java.awt.BorderLayout());
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

		org.graphstream.ui.view.util.MouseManager mouseManager=new AUIMouseManager();
		mouseManager.init(m_viewer.getGraphicGraph(), m_viewPanel);
		m_viewPanel.setMouseManager(mouseManager);
		m_model.m_graph.addAttribute("layout.gravity", 0.000001); //layout.quality
		m_model.m_graph.addAttribute("layout.quality", 4);
		m_model.m_graph.addAttribute("layout.stabilization-limit", 0.95);
		m_model.m_graph.addAttribute("ui.antialias", true); //enable anti aliasing (looks way better this way)
		this.add(m_viewPanel, java.awt.BorderLayout.CENTER);
		
		new Thread(){
			public void run()
			{
				while (!Thread.interrupted())
				{
					try { m_graphViewPump.blockingPump(); }
					catch (Exception e) { e.printStackTrace(); }
				}
			}
		}.start();

		javax.swing.JPanel bottomPanel=new javax.swing.JPanel();
		{
			bottomPanel.setBackground(s_backgroundColor);
			bottomPanel.setLayout(new java.awt.GridBagLayout());
			java.awt.GridBagConstraints gbc=new java.awt.GridBagConstraints();
			gbc.fill=java.awt.GridBagConstraints.HORIZONTAL;
			javax.swing.JPanel bottomButtons=new javax.swing.JPanel(); //silly, but had to do this or the clickable area of the JCheckbox gets stretched the whole width...which makes for strange clicking
			bottomButtons.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT,4,0));
			
			javax.swing.JButton fwBtn=new javax.swing.JButton("FW Suggestions");
			fwBtn.setActionCommand("listenerWindow");
			fwBtn.addActionListener(this);
			fwBtn.setFont(uiFont);
			fwBtn.setToolTipText(styleToolTipText("Shows the list of listening processes to aid in creation of firewall rules."));
			
			javax.swing.JButton resetBtn=new javax.swing.JButton("Reset Zoom");
			resetBtn.setActionCommand("resetZoom");
			resetBtn.addActionListener(this);
			resetBtn.setFont(uiFont);
			resetBtn.setToolTipText(styleToolTipText("Resets the zoom of the graph view to default."));
			
			m_hideOSProcsCheckbox.setForeground(s_foregroundColor);
			m_hideOSProcsCheckbox.setBackground(s_foregroundColor);
			m_hideOSProcsCheckbox.setActionCommand("hideOSProcs");
			m_hideOSProcsCheckbox.addActionListener(this);
			m_hideOSProcsCheckbox.setFont(uiFont);
			m_hideOSProcsCheckbox.setToolTipText(styleToolTipText("Hides the usual Windows™ operating system processes, while interesting these processes can get in the way of other analysis."));
			
			m_hideExtCheckbox.setForeground(s_foregroundColor);
			m_hideExtCheckbox.setBackground(s_foregroundColor);
			m_hideExtCheckbox.setActionCommand("hideExtNode");
			m_hideExtCheckbox.addActionListener(this);
			m_hideExtCheckbox.setFont(uiFont);
			m_hideExtCheckbox.setToolTipText(styleToolTipText("Hides the main 'External' node from the graph that all nodes which listen on externally accessible addresses connect to."));
			
			m_showFQDN.setForeground(s_foregroundColor);
			m_showFQDN.setBackground(s_foregroundColor);
			m_showFQDN.setActionCommand("showFQDN");
			m_showFQDN.addActionListener(this);
			m_showFQDN.setFont(uiFont);
			m_showFQDN.setToolTipText(styleToolTipText("Show the DNS names of external nodes rather than IPs."));
			
			javax.swing.JCheckBox worstUserPriv=new javax.swing.JCheckBox("WorstUserProc Score");
			worstUserPriv.setActionCommand("worstUserPriv");
			worstUserPriv.setForeground(s_foregroundColor);
			worstUserPriv.setBackground(s_foregroundColor);
			worstUserPriv.addActionListener(this);
			worstUserPriv.setFont(uiFont);
			worstUserPriv.setToolTipText(styleToolTipText("The score from the worst node of any set of linked nodes will apply to all of the nodes of that linked set. The 'weakest link' in the chain scoring method."));
			
			javax.swing.JCheckBox useCustom=new javax.swing.JCheckBox("Custom ScoreFile");
			useCustom.setActionCommand("useCustom");
			useCustom.setForeground(s_foregroundColor);
			useCustom.setBackground(s_foregroundColor);
			useCustom.setSelected(m_model.m_useCustomScore);
			useCustom.addActionListener(this);
			useCustom.setFont(uiFont);
			useCustom.setToolTipText(styleToolTipText("If a custom score file was loaded, this option will apply those custom directives to the graph view."));
			
			bottomButtons.setBackground(s_backgroundColor);
			bottomButtons.add(fwBtn);
			bottomButtons.add(resetBtn);
			bottomButtons.add(m_hideOSProcsCheckbox);
			bottomButtons.add(m_hideExtCheckbox);
			bottomButtons.add(m_showFQDN);
			bottomButtons.add(worstUserPriv);
			bottomButtons.add(useCustom);
			bottomButtons.setBorder(null);
			
			gbc.gridx=0; gbc.gridy=0; gbc.anchor=java.awt.GridBagConstraints.WEST; gbc.weightx=10;
			m_name.setForeground(s_foregroundColor);
			m_name.setFont(uiFont);
			bottomPanel.add(m_name, gbc);
			gbc.gridy++;
			m_connections.setForeground(s_foregroundColor);
			m_connections.setFont(uiFont);
			bottomPanel.add(m_connections, gbc);
			gbc.gridy++;
			m_score.setForeground(s_foregroundColor);
			m_score.setFont(uiFont);
			bottomPanel.add(m_score, gbc);
			gbc.gridy++;
			bottomPanel.add(bottomButtons, gbc);
			bottomPanel.setBorder(null);
		}
		javax.swing.ToolTipManager.sharedInstance().setDismissDelay(Integer.MAX_VALUE);
		javax.swing.ToolTipManager.sharedInstance().setInitialDelay(500);
		this.add(bottomPanel, java.awt.BorderLayout.SOUTH);
		this.setSize(1024, 768);
		this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		this.setVisible(true);
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

	public void buttonPushed(String id) //called when you click on a graph node/edge
	{
		if (id==null || id.equals("")) { return; }
		Node node=m_model.m_graph.getNode(id);
		if (node==null) { return; }
		String score=node.getAttribute("ScoreReason");
		String temp="";
		java.util.Iterator<Edge> it=node.getEachEdge().iterator();
		while (it.hasNext())
		{
			Edge e=it.next();
			Node tempNode=e.getOpposite(node);
			String t2=tempNode.getAttribute("ui.label");
			if (!temp.contains(t2)) //some vertices have multiple connections between them, only print once
			{ 
				String processPath=tempNode.getAttribute("processpath");
				if ( processPath==null || !(m_model.m_hideOSProcs&&m_model.m_osProcs.get(processPath)!=null) ) 
				{ 
					temp+=t2; 
					if (it.hasNext()) { temp+=", "; }
				}
			}
		}
		if (score==null) { score=" "; }
		else { score="Score: "+score; }
		String tmpNameText="Name: "+id+"  User: "+node.getAttribute("username")+"  Path: "+node.getAttribute("processpath"), services=node.getAttribute("processservices");
		if (node.getAttribute("ui.class").toString().toLowerCase().equals("external")) 
		{ 
			tmpNameText="Name: "+id;
			if (node.getAttribute("IP")!=null)
			{
				tmpNameText+="  IP: "+node.getAttribute("IP")+"  DNS Name: "+node.getAttribute("hostname"); 
			}
			else if (id.toLowerCase().equals("external"))
			{
				tmpNameText+=" - dummy node which connects to any node listening for outside connections. This can be hidden using 'Hide Ext Node' checkbox.";
			}
			score="Score: N/A";  //this was requested to make the UI feel cleaner, since nothing can be done to help the score of an external node anyway.
		}
		if (services!=null && !services.equals("") && !services.equals("null")) { tmpNameText+="  Services: "+services; }
		final String connections="Connections: "+temp, nameText=tmpNameText, scoreReason=score;

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

	private void showListenerSocketWindow() //shows the window that lists the listening sockets
	{
		new JFrame()
		{
			{
				setLayout(new java.awt.BorderLayout());
				javax.swing.JTextArea tArea=new javax.swing.JTextArea("Listening Processes:\n");
				java.util.TreeMap<String,String> sortMe=new java.util.TreeMap<String,String>();
				for (java.util.Map.Entry<String, String> entry : m_model.m_listeningProcessMap.entrySet())
				{
					String key=entry.getKey(), value=entry.getValue();
					String[] keyTokens=key.split("_"), valueTokens=value.split("_");
					String strValue=String.format("%-16s PID=%-6s Proto=%-4s Port=%s\n",valueTokens[0], valueTokens[1], keyTokens[0].toUpperCase(), keyTokens[1]);
					String newKey=valueTokens[0]+valueTokens[1]+"-"+keyTokens[0].toUpperCase()+keyTokens[1];
					sortMe.put(newKey, strValue); //System.out.println("key="+newKey);
				}
				for (String str : sortMe.values())
				{
					tArea.append(str);
				}
				tArea.setFont(new Font(Font.MONOSPACED,Font.PLAIN,12));
				tArea.setBackground(s_backgroundColor);
				tArea.setForeground(s_foregroundColor);
				tArea.setCaretColor(s_foregroundColor);
				tArea.setBorder(new javax.swing.border.LineBorder(java.awt.Color.gray));
				add(tArea, java.awt.BorderLayout.CENTER);
				java.awt.Dimension size=tArea.getPreferredSize();
				size.setSize(size.getWidth()+20,size.getHeight()+20);
				setSize(size);
				setBackground(s_backgroundColor);
				getContentPane().setBackground(s_backgroundColor);
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
		if (e.getActionCommand().equals("worstUserPriv"))
		{
			if (((javax.swing.JCheckBox)e.getSource()).isSelected()) { m_model.m_scoringMode=1; }
			else { m_model.m_scoringMode=0; }
			m_model.exploreAndScore(m_model.m_graph);
		}
		if (e.getActionCommand().equals("useCustom"))
		{
			m_model.m_useCustomScore=((javax.swing.JCheckBox)e.getSource()).isSelected();
			m_model.exploreAndScore(m_model.m_graph);
		}
	}

	public void mouseWheelMoved(MouseWheelEvent e) //zooms graph in and out using mouse wheel
	{
		m_viewPanel.getCamera().setViewPercent(m_viewPanel.getCamera().getViewPercent()+((double)e.getWheelRotation()/100d));
	}
	public void buttonReleased(String id) {} //graph mouse function
	public void viewClosed(String arg0) {} //graph viewer interface function

	public class AUIMouseManager extends org.graphstream.ui.view.util.DefaultMouseManager //GraphStream specific helper for clicking on nodes/etc
	{	
		public void mousePressed(MouseEvent e) 
		{
			curElement = view.findNodeOrSpriteAt(e.getX(), e.getY());
			if (curElement != null) 
			{
				mouseButtonPressOnElement(curElement, e);
			} 
			else 
			{
				x1 = e.getX();
				y1 = e.getY();
				mouseButtonPress(e);
			}
		}

		public void mouseDragged(MouseEvent e) 
		{
			if (curElement != null) { elementMoving(curElement, e); } 
			else 
			{
				int x2 = e.getX(), y2 = e.getY();
				org.graphstream.ui.geom.Point3 center=m_viewPanel.getCamera().getViewCenter();
				org.graphstream.ui.geom.Point3 pixels=m_viewPanel.getCamera().transformGuToPx(center.x, center.y, center.z);
				pixels.x+=(x1-x2);
				pixels.y+=(y1-y2);
				center=m_viewPanel.getCamera().transformPxToGu(pixels.x, pixels.y);
				m_viewPanel.getCamera().setViewCenter(center.x, center.y, center.z);
				x1=x2;
				y1=y2;
			}
		}

		public void mouseReleased(MouseEvent e) 
		{
			if (curElement != null) 
			{
				mouseButtonReleaseOffElement(curElement, e);
				curElement = null;
			} 
			else 
			{
				float x2 = e.getX(), y2 = e.getY(), t;
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
				mouseButtonRelease(e, view.allNodesOrSpritesIn(x1, y1, x2, y2));
			}
		}
	}
	
	public static void main(String args[]) //It's kind of dumb that our stub to main is in this class, but this way when we run on operating systems that display the name in places, it will say AHAGUI rather than AHAModel
	{ 
		AHAModel model=new AHAModel(args);
		model.start();
	}
}
