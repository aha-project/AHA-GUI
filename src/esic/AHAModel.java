package esic;

//Copyright 2018 ESIC at WSU distributed under the MIT license. Please see LICENSE file for further info.

import org.graphstream.graph.Edge;
import org.graphstream.graph.Graph;
import org.graphstream.graph.Node;

//√ remove score (now shows "N/A") for external nodes
//√ ReFactor GUI separate from scoring model
//√ update 'formatToolTipText' so that it <BR>s every 100 chars or so, preferably at a ',' or a ' '

//TODO: SOON items
//TODO: look into possibly using different auto layout algorithm(s)
//TODO: look at SIDs for services and treat per service ID favorably
//TODO: overlay on hover of a node? ToolTips maybe?

//TODO: Intermediate time frame: custom scoring CSV:
//TODO:    support reading in score criteria for security items (ASLR=10pts for instance)
//TODO:    support reading in score criteria for user IDs?
//TODO:    support setting node color thresholds in custom score.csv file
//TODO:    support max bounds if feature is missing (AKA ASLR cap to 30pts or something)
//TODO:    break scoring stuff out into score file (with default)
//TODO:    move CSS to score file?

//TODO: possible FUTURE features
//TODO:    eventually support collapsing nodes based on some...3rd file?
//TODO:    show more raw graph info?
//TODO:    show FireWall suggestions in more detail?
//TODO:    support reading in a directory of separate node host scans and graph the whole thing
//TODO:    support reading several files in which are node scans over time and assemble graph from aggregate information

public class AHAModel
{
	protected boolean m_debug=false, m_multi=true, m_useCustomScore=false, m_hideOSProcs=false; //flags for verbose output, hiding of operating system procs, and drawing of multiple edges between verticies
	protected String m_inputFileName="BinaryAnalysis.csv", m_scoreFileName="scorefile.csv";
	protected int m_minScoreLowVuln=25, m_minScoreMedVuln=15, m_scoringMode=0;
	protected java.util.TreeMap<String,String> m_listeningProcessMap=new java.util.TreeMap<String,String>(), m_osProcs=new java.util.TreeMap<String,String>();
	protected Graph m_graph=null;
	protected AHAGUI m_gui=null;
	
	protected String[] m_increaseScoreKey=   {"aslr", "dep","authenticode","strongnaming","safeseh",  "arch", "ControlFlowGuard","HighentropyVA",};
	protected String[] m_increaseScoreValue= {"true","true",        "true",        "true",   "true", "amd64",             "true",         "true",};
	protected int[] m_increaseScoreIfTrueValues= {10,     1,            10,             1,        1,      10,                 30,             10,};
	
	protected static final String CUSTOMSTYLE="ui.weAppliedCustomStyle";
	protected String styleSheet = 	"graph { fill-color: black; }"+
			"node { size: 30px; fill-color: red; text-color: white; text-style: bold; text-size: 12; text-background-color: #222222; text-background-mode: plain; }"+
			"node.low { fill-color: green; }"+
			"node.high { fill-color: orange; }"+
			"node.medium { fill-color: yellow; }"+
			"node.custom { fill-mode: dyn-plain; }"+
			"node.external { size: 50px; fill-color: red; }"+
			"node:clicked { fill-color: blue; }"+
			"edge { shape: line; fill-color: #CCCCCC; }"+
			"edge.tw { stroke-mode: dashes; fill-color: #CCCCCC; }"+
			"edge.duplicate { fill-color: #303030; }"+
			"edge.duplicatetw { fill-color: #303030; stroke-mode: dashes; }"+
			"edge.external { fill-color: #883030; }"+
			"edge.externaltw { fill-color: #883030; stroke-mode: dashes; }"+
			"edge.duplicateExternal { fill-color: #553030; }"+
			"edge.duplicateExternaltw { fill-color: #553030; stroke-mode: dashes; }";

	protected void exploreAndScore(Graph graph) //explores the node graph and assigns a scaryness score
	{
		java.util.TreeMap<String, Integer> lowestScoreForUserName=new java.util.TreeMap<String, Integer>();
		for (Node next:graph)
		{
			int score=generateNodeScore(next);
			String nodeUserName=next.getAttribute("username");
			if (nodeUserName!=null && m_scoringMode==1)
			{
				Integer lowScore=lowestScoreForUserName.remove(nodeUserName);
				if (lowScore!=null && score>lowScore) { score=lowScore.intValue(); }
				lowestScoreForUserName.put(nodeUserName,score);
			}

			if (m_scoringMode==0)
			{
				if (next.getAttribute(CUSTOMSTYLE)==null || !m_useCustomScore) { next.setAttribute("score", Integer.toString(score)); } //if we didn't have a custom score from another file, use our computed score
				else { System.out.println("Preexisting score found, ignoring computed score"); }
				styleNode(next,null);
			}
		}

		if (m_scoringMode==1)
		{
			System.out.println("Scores="+lowestScoreForUserName);
			for (Node next:graph)
			{
				String nodeUserName=next.getAttribute("username");
				if (nodeUserName!=null)
				{
					Integer lowScore=lowestScoreForUserName.get(next.getAttribute("username"));
					if (lowScore==null) { System.out.println("no low score found, this should not happen"); continue; }

					if (next.getAttribute(CUSTOMSTYLE)==null || !m_useCustomScore) { next.setAttribute("score", lowScore.toString()); } //if we didn't have a custom score from another file, use our computed score
					else { System.out.println("Preexisting score found, ignoring computed score"); }
					styleNode(next,null);
				}
			}
		}
		if (m_useCustomScore)
		{
			for (Node next:graph)
			{
				try
				{
					if (next.getAttribute(CUSTOMSTYLE)!=null)
					{
						System.out.println("Custom style found for node="+next.getId());
						next.removeAttribute("ui.class");
						next.setAttribute("ui.style",next.getAttribute(CUSTOMSTYLE+".style"));
						next.setAttribute("score",next.getAttribute(CUSTOMSTYLE+".score"));
						styleNode(next,null);
					}
				}catch(Exception e) {}
			}
		}
		System.out.println("Scoring completed using method="+m_scoringMode+" with useCustomScoring="+Boolean.toString(m_useCustomScore));
	}

	protected int generateNodeScore(Node n)
	{
		int score = 0;
		String scoreReason="";
		System.out.println("Process: "+n.getId());
		if(n.getAttribute("username") != null)
		{
			System.out.println("    priv: " + n.getAttribute("username"));
			if(n.getAttribute("username").equals("NT AUTHORITY\\LOCAL SERVICE".toLowerCase())) { score++; } //TODO: probably more can be done here...really we probably want to look for bad users and remove score...but that requires research into what bad users are
		}
		for (int i=0; i<m_increaseScoreKey.length; i++) //any strings matched in this loop will increase the score
		{
			try
			{
				String s=m_increaseScoreKey[i].toLowerCase();
				if(n.getAttribute(s) != null)
				{
					System.out.println("    "+s+": " + n.getAttribute(s));
					if(n.getAttribute(s).toString().equals(m_increaseScoreValue[i].toLowerCase())) 
					{ 
						score+=m_increaseScoreIfTrueValues[i]; 
						scoreReason+=", "+s+"="+m_increaseScoreValue[i].toLowerCase();
					}
				}
			}
			catch (Exception e) { System.out.println(e.getMessage()); }
		}
		System.out.println("  Score: " + score);
		n.setAttribute("ScoreReason", "FinalScore="+score+scoreReason);
		return score;
	}

	protected void styleNode(Node n, String overrideUiClass)
	{
		String currentClass=n.getAttribute("ui.class"); 

		String sScore=n.getAttribute("score");
		Integer ptrScore=null;
		try {ptrScore=Integer.parseInt(sScore);}
		catch (Exception e) {} 
		if (currentClass==null || !currentClass.equalsIgnoreCase("external") || ptrScore!=null || overrideUiClass!=null)
		{
			if (currentClass!=null && currentClass.equalsIgnoreCase("external")) 
			{ 
				//n.addAttribute("ui.class", "external");
				//	for (Edge e : n.getEachEdge()) { e.addAttribute("ui.class", "external");	} //so far this no longer seems required. remove in the future?
				//System.out.printf("Sorry, %s is already classified as external, not changing.\n", n.getId()); 
			}
			else if (overrideUiClass!=null)
			{
				n.setAttribute("ui.class", overrideUiClass);
			}
			else if (ptrScore!=null)
			{ 
				int score=ptrScore.intValue();
				System.out.println("  Applying Score: " + score);
				n.setAttribute("ui.class", "high"); //default
				if (score > 99) { n.setAttribute("ui.class", "custom"); System.out.println("   Scored: custom"); }
				else if(score > m_minScoreLowVuln) { n.setAttribute("ui.class", "low"); System.out.println("   Scored: low");}
				else if(score > m_minScoreMedVuln) { n.setAttribute("ui.class", "medium"); System.out.println("   Scored: medium");} 
				else { System.out.println("   Scored: high"); }
			}
		}
	}

	protected static String[] fixCSVLine(String s) //helper function to split, lower case, and clean lines of CSV into tokens
	{
		String[] ret=null;
		try
		{
			ret=s.toLowerCase().split("\",\"");
			for (int i=0;i<ret.length;i++)
			{
				if (ret[i]!=null) { ret[i]=ret[i].replaceAll("\"", ""); } //remove double quotes as we break into tokens
			}
		}
		catch (Exception e) { e.printStackTrace(); }
		return ret;
	}
	
	public static String substringBeforeInstanceOf(String s, String separator)
	{
		int index=s.lastIndexOf(separator);
		if (index > 1) { s=s.substring(0, index); }
		return s;
	}

	protected void start()
	{
		m_graph.addAttribute("ui.stylesheet", styleSheet);
		m_graph.setAutoCreate(true);
		m_graph.setStrict(false);
		Node ext=m_graph.addNode("external");
		ext.addAttribute("ui.class", "external"); //Add a node for "external"
		ext.addAttribute("processpath","external"); //add fake process path
		
		System.out.println("Using inputFile="+m_inputFileName);
		java.io.BufferedReader br = null;
		try 
		{
			br = new java.io.BufferedReader(new java.io.FileReader(m_inputFileName));
			String line = "", header[]=fixCSVLine(br.readLine());

			while ((line = br.readLine()) != null) //this is the first loop, in this loop we're loading all the vertexes and their metadata, so we can then later connect the verticies
			{
				String[] processTokens = fixCSVLine(line); 
				if(processTokens!=null && !processTokens[0].isEmpty() && !processTokens[0].contains("processname"))
				{
					String fromNode=processTokens[0]+"_"+processTokens[1], protoLocalPort=processTokens[3]+"_"+processTokens[5];
					String connectionState=processTokens[11], localAddr=processTokens[4];
					Node node = m_graph.getNode(fromNode);
					if(node == null)
					{
						if (m_debug) { System.out.println("Found new process: Name=|"+fromNode+"|"); }
						node = m_graph.addNode(fromNode);
					}
					if (connectionState.equals("listening") || connectionState.equals("") )
					{
						m_listeningProcessMap.put(protoLocalPort,fromNode); //push a map entry in the form of (proto_port, procname_PID) example map entry (tcp_49263, alarm.exe_5)
						if (m_debug) { System.out.printf("ListenMapPush: localPort=|%s| fromNode=|%s|\n",protoLocalPort,fromNode); }
						
						if( localAddr.equals("0.0.0.0") || localAddr.equals("::")) 
						{ 
							Edge e=m_graph.addEdge(node+"_external",node.toString(),"external");
							e.addAttribute("ui.class", "external");
							String extListeningPorts=node.getAttribute("ui.extListeningPorts");
							if (extListeningPorts==null ) { extListeningPorts="";}
							else { extListeningPorts+=", ";}
							node.setAttribute("ui.extListeningPorts", extListeningPorts+protoLocalPort);
						}
						else 
						{
							String listeningPorts=node.getAttribute("ui.localListeningPorts");
							if (listeningPorts==null ) { listeningPorts="";}
							else { listeningPorts+=", ";}
							node.setAttribute("ui.localListeningPorts", listeningPorts+protoLocalPort);
						}
					}
					for (int i=0;i<processTokens.length && i<header.length;i++)
					{
						String processToken=processTokens[i];
						if (  processToken==null || processToken.isEmpty() ) { processToken="null"; }
						if (m_debug) { System.out.printf("   Setting attribute %s for process %s\n",header[i],processTokens[i]); }
						node.setAttribute(header[i],processToken);
					}
				}
			}
		} 
		catch (Exception e) { e.printStackTrace(); } 
		finally 
		{
				try { if (br!=null) br.close(); } 
				catch (Exception e) {  }
		}

		if (m_debug)
		{
			System.out.println("Listeners:");
			for (java.util.Map.Entry<String, String> entry : m_listeningProcessMap.entrySet()) { System.out.println(entry.getKey()+"="+entry.getValue()); }
			System.out.println("-------\n");
		}

		int connectionNumber=0, lineNumber=0;
		br = null;
		try 
		{
			br = new java.io.BufferedReader(new java.io.FileReader(m_inputFileName));
			String line = "";

			while ((line = br.readLine()) != null)  //this is the second loop, in this loop we're loading the connections between nodes
			{
				lineNumber++;
				String[] processTokens = fixCSVLine(line);
				if(processTokens!=null && !processTokens[0].isEmpty() && !processTokens[0].contains("processname"))
				{
					String toNode, fromNode=processTokens[0]+"_"+processTokens[1], proto=processTokens[3], localPort=processTokens[5], remotePort=processTokens[8];
					String protoLocalPort=proto+"_"+localPort, protoRemotePort=proto+"_"+remotePort;
					String remoteAddr=processTokens[7], localAddr=processTokens[4], connectionState=processTokens[11], remoteHostname=processTokens[9];
					Node node = m_graph.getNode(fromNode);
					if(node == null)
					{
						System.out.println("WARNING: Second scan found new process: Name=|"+fromNode+"|, on line "+lineNumber+" ignoring."); 
						continue;
					}
					if ( !connectionState.equalsIgnoreCase("listening") && !connectionState.equalsIgnoreCase("") )
					{
						if ( remoteAddr.equals("127.0.0.1") || remoteAddr.equals("::1") )
						{
							if ( (toNode=m_listeningProcessMap.get(protoRemotePort))!=null )
							{
								Node tempNode=m_graph.getNode(fromNode);
								boolean duplicateEdge=false, timewait=false;
								if (connectionState.toLowerCase().contains("time")) { timewait=true; }
								if (tempNode!=null)
								{
									for (Edge e : tempNode.getEdgeSet())
									{
										if (e.getOpposite(tempNode).getId().equals(toNode)) { duplicateEdge=true; }
									}
								}
								Edge e=m_graph.addEdge(String.valueOf(++connectionNumber),fromNode,toNode);
								if (e!=null)
								{
									e.setAttribute("layout.weight", 10); //try to make internal edges longer
									if (duplicateEdge) { e.setAttribute("layout.weight", 5); }
									if (timewait && !duplicateEdge) { e.setAttribute("ui.class", "tw"); }
									if (!timewait && duplicateEdge) { e.setAttribute("ui.class", "duplicate"); }
									if (timewait && duplicateEdge) { e.setAttribute("ui.class", "duplicatetw"); }
									if (m_debug) { System.out.println("Adding edge from="+fromNode+" to="+toNode); }
								}
							}
							else if ( !(localAddr.equals("127.0.0.1") || localAddr.equals("::1")) )
							{
								System.out.printf("WARNING: Failed to find listener for: %s External connection? info: line=%d name=%s local=%s:%s remote=%s:%s status=%s\n",protoRemotePort,lineNumber,fromNode,localAddr,localPort,remoteAddr,remotePort,connectionState);
							}
							else if ( (localAddr.equals("127.0.0.1") || localAddr.equals("::1")) && (m_listeningProcessMap.get(protoLocalPort)!=null) ) { /*TODO: probably in this case we should store this line and re-examine it later after reversing the from/to and make sure someone else has the link?*/ /*System.out.printf("     Line=%d expected?: Failed to find listener for: %s External connection? info: name=%s local=%s:%s remote=%s:%s status=%s\n",lineNumber,protoRemotePort,fromNode,localAddr,localPort,remoteAddr,remotePort,connectionState);*/  }
							else { System.out.printf("WARNING: Failed to find listener for: %s External connection? info: line=%d name=%s local=%s:%s remote=%s:%s status=%s\n",protoRemotePort,lineNumber,fromNode,localAddr,localPort,remoteAddr,remotePort,connectionState); }
						}
						else // if (connectionState.equalsIgnoreCase("established") && !(remoteAddr.trim().equals("127.0.0.1") || remoteAddr.trim().equals("::1")) )
						{ //System.out.printf("WARNING: Failed to find listener for: %s External connection? info: name=%s local=%s:%s remote=%s:%s status=%s\n",protoRemotePort,fromNode,localAddr,localPort,remoteAddr,remotePort,connectionState);
							Node tempNode=m_graph.getNode(fromNode);
							toNode="Ext_"+remoteAddr;
							if (remoteHostname==null || remoteHostname.equals("")) { remoteHostname=remoteAddr; } //cover the case that there is no FQDN
							boolean duplicateEdge=false, timewait=false;
							if (connectionState.toLowerCase().contains("time")) { timewait=true; }
							if (tempNode!=null)
							{
								for (Edge e : tempNode.getEdgeSet())
								{
									if (e.getOpposite(tempNode).getId().equals(toNode)) { duplicateEdge=true; }
								}
							}
							Edge e=m_graph.addEdge(String.valueOf(++connectionNumber),fromNode,toNode);
							
							if (m_debug) { System.out.println("Adding edge from="+fromNode+" to="+toNode); }
							m_graph.getNode(toNode).addAttribute("ui.class", "external");
							m_graph.getNode(toNode).addAttribute("hostname", remoteHostname);
							m_graph.getNode(toNode).addAttribute("IP", remoteAddr);
							if (e!=null)
							{
								e.setAttribute("layout.weight", 9); //try to make internal edges longer
								if (duplicateEdge) { e.setAttribute("layout.weight", 4); }
								if (!timewait && !duplicateEdge) { e.addAttribute("ui.class", "external"); }
								if (!timewait && duplicateEdge) { e.setAttribute("ui.class", "duplicateExternal"); }
								if (timewait && !duplicateEdge) { e.setAttribute("ui.class", "externaltw"); } 
								if (timewait && duplicateEdge) { e.setAttribute("ui.class", "duplicateExternaltw"); }
							}
						}
						//else if (m_listeningProcessMap.get(protoLocalPort)==null) //if we have a local port in the listeners we can ignore this connection
						//	{ //System.out.printf("WARNING: Failed to find listener for: %s External connection? info: name=%s local=%s:%s remote=%s:%s status=%s\n",protoRemotePort,fromNode,localAddr,localPort,remoteAddr,remotePort,connectionState); }
					}
				}
			}
		} 
		catch (Exception e) { e.printStackTrace(); } 
		finally 
		{
			if (br != null) 
			{
				try { br.close(); } 
				catch (Exception e) { e.printStackTrace(); }
			}
		}

		readCustomScorefile();
		useFQDNLabels(m_graph, m_gui.m_showFQDN.isSelected());
		exploreAndScore(m_graph);

		java.util.Vector<Node> leftSideNodes=new java.util.Vector<Node>();
		for (Node n : m_graph) 
		{
			if (n.getId().contains("Ext_")) { leftSideNodes.add(n); }
			n.setAttribute("layout.weight", 6);
		}
		int numLeftNodes=leftSideNodes.size()+2; //1 is for main External node, 2 is so we dont put one at the very top or bottom
		leftSideNodes.insertElementAt(m_graph.getNode("external"),leftSideNodes.size()/2);
		
		try { Thread.sleep(1500); } catch (Exception e) {}
		m_gui.m_viewer.disableAutoLayout();
		
		int i=1;
		for (Node n : leftSideNodes)
		{ 
			org.graphstream.ui.geom.Point3 loc=m_gui.m_viewPanel.getCamera().transformPxToGu(30, (m_gui.m_viewPanel.getHeight()/numLeftNodes)*i);
			n.setAttribute("xyz", loc.x,loc.y,loc.z);
			i++;
		}
	}

	protected void readCustomScorefile()
	{
		System.out.println("Using custom score fileName="+m_scoreFileName);
		java.io.BufferedReader br = null;
		try 
		{
			br = new java.io.BufferedReader(new java.io.FileReader(m_scoreFileName));
			String line = "";
			int lineNumber=0;
			while ((line = br.readLine()) != null)  //this is the second loop, in this loop we're loading the connections between nodes
			{
				try
				{
					String[] tokens = fixCSVLine(line);
					String directive=tokens[0], color=tokens[2], nodePathName=tokens[3];
					String score=tokens[1].split("=")[1];

					lineNumber++; //we will now be on line 1.
					for (Node node:m_graph)
					{
						try
						{
							String processPath=node.getAttribute("processpath");
							if (processPath!=null && processPath.equalsIgnoreCase(nodePathName))
							{
								if(directive.equals("node") && nodePathName!=null)
								{
									node.addAttribute("score", score);
									node.removeAttribute("ui.class");
									node.addAttribute("ui.style", color);
									node.addAttribute(CUSTOMSTYLE,"yes");
									node.addAttribute(CUSTOMSTYLE+".score", score);
									node.addAttribute(CUSTOMSTYLE+".style", color);
									System.out.printf("scorefile: found node=%s path=%s, setting score=%s color=%s\n", node.getId(),nodePathName,score, color);
								}
								else if (directive.equals("edge") && nodePathName!=null)
								{
									String toName=tokens[4];
									for (Edge e : node.getEdgeSet())
									{
										Node toNode=e.getOpposite(node);
										String toNodeProcessPath=toNode.getAttribute("processpath");
										if ( toNodeProcessPath.equalsIgnoreCase(toName) )
										{
											e.addAttribute("score", score);
											e.removeAttribute("ui.class");
											e.addAttribute("ui.style", color);
											e.addAttribute(CUSTOMSTYLE,"yes");
											e.addAttribute(CUSTOMSTYLE+".score", score);
											e.addAttribute(CUSTOMSTYLE+".style", color);
											System.out.printf("scorefile: found edge from=%s to=%s score=%s\n", nodePathName,toName,score);
										}
									}
								}
							}
						} catch (Exception e) { e.printStackTrace(); }
					}
					lineNumber++;
				} catch (Exception e)
				{
					System.out.println("Failed to parse line="+lineNumber);
					e.printStackTrace();
				}
			}
		} 
		catch (java.io.FileNotFoundException fne) { System.out.println("No scorefile.csv found."); }
		catch (Exception e) { e.printStackTrace(); } 
		finally 
		{
			if (br != null) 
			{
				try { br.close(); } 
				catch (Exception e) { e.printStackTrace(); }
			}
		}
	}
	
	protected void hideFalseExternalNode(Graph g, boolean hide) 
	{
		Node node=m_graph.getNode("external");
		try
		{
			System.out.println("Hide/unhide node="+node.getId());
			if (hide) { node.addAttribute( "ui.hide" ); }
			else { node.removeAttribute( "ui.hide" ); }

			for (Edge e : node.getEdgeSet())
			{
				if (hide) { e.addAttribute( "ui.hide" ); }
				else { if (e.getOpposite(node).getAttribute("ui.hide")==null ) { e.removeAttribute( "ui.hide" ); } }
			}
		} catch (Exception e) { e.printStackTrace(); }
	}
	
	protected void useFQDNLabels(Graph g, boolean useFQDN) 
	{
		for (Node n : m_graph) { n.addAttribute("ui.label", capitalizeFirstLetter(n.getId())); } //add labels
		if (useFQDN) 
		{  
			for (Node n : m_graph) 
			{ 
				if (n.getAttribute("ui.class").equals("external"))
				{
					if (!n.getId().equals("external")) { n.addAttribute("ui.label", capitalizeFirstLetter("Ext_"+n.getAttribute("hostname"))); }
				}
			}
		} 
	}
	
	protected void hideOSProcs(Graph g, boolean hide) 
	{
		m_hideOSProcs=hide;
		for (Node node:g)
		{
			try
			{
				String processPath=node.getAttribute("processpath");
				if (processPath!=null && m_osProcs.get(processPath)!=null)
				{
					System.out.println("Hide/unhide node="+node.getId());
					if (m_hideOSProcs) { node.addAttribute( "ui.hide" ); }
					else { node.removeAttribute( "ui.hide" ); }

					for (Edge e : node.getEdgeSet())
					{
						if (m_hideOSProcs) { e.addAttribute( "ui.hide" ); }
						else { if (e.getOpposite(node).getAttribute("ui.hide")==null ) { e.removeAttribute( "ui.hide" ); } }
					}
				}
			} catch (Exception e) { e.printStackTrace(); }
		}
	}
	
	protected static String capitalizeFirstLetter(String s)
	{
		s = s.toLowerCase();
		return Character.toString(s.charAt(0)).toUpperCase()+s.substring(1);
	}
	
	public AHAModel(String args[])
	{
		{ //fill m_osProcs
			m_osProcs.put("c:\\windows\\system32\\services.exe","services.exe");
			m_osProcs.put("c:\\windows\\system32\\svchost.exe","svchost.exe");
			m_osProcs.put("c:\\windows\\system32\\wininit.exe","wininit.exe");
			m_osProcs.put("c:\\windows\\system32\\lsass.exe","lsass.exe");
			m_osProcs.put("null","unknown");
			m_osProcs.put("system","system");
		}
		
		boolean bigfont=false;
		for (String s : args)
		{
			try
			{
				String[] argTokens=s.split("=");
				if (argTokens[0]==null) { continue; }
				if (argTokens[0].equalsIgnoreCase("--debug")) { m_debug=true; } //print more debugs while running
				if (argTokens[0].equalsIgnoreCase("--single")) { m_multi=false; } //draw single lines between nodes
				if (argTokens[0].equalsIgnoreCase("--bigfont")) { bigfont=true; } //use 18pt font instead of default
				if (argTokens[0].equalsIgnoreCase("scorefile")) { m_scoreFileName=argTokens[1]; m_useCustomScore=true; } //path to custom score file, and enable since...that makes sense in this case
				if (argTokens[0].equalsIgnoreCase("inputFile")) { m_inputFileName=argTokens[1]; } //path to input file
				if (argTokens[0].equalsIgnoreCase("lowVulnThreshold")) { m_minScoreLowVuln=Integer.parseInt(argTokens[1]); } //set the minimum score to attain low vulnerability status
				if (argTokens[0].equalsIgnoreCase("medVulnThreshold")) { m_minScoreMedVuln=Integer.parseInt(argTokens[1]); } //set the minimum score to attain medium vulnerability status
				
				if (argTokens[0].equals("help")||argTokens[0].equals("?")) 
				{  
					System.out.println
					(
							"Arguments are as follows:"+
							"--single : use single lines between nodes with multiple connections "+
							"--bigfont : use 18pt font instead of the default 12pt font (good for demos) "+
							"scorefile=scorefile.csv : use the scorefile specified after the equals sign "+
							"inputFile=inputFile.csv : use the inputFile specified after the equals sign "+
							"lowVulnThreshold=25 : use the integer after the equals as the minimum node score to get a low vulnerability score (green) "+
							"medVulnThreshold=15 : use the integer after the equals as the minimum node score to get a medium vulnerability score (yellow) "
					); return;
				}
			} catch (Exception e) { e.printStackTrace(); }
		}
		m_gui =new AHAGUI(bigfont,this);
	}
}
