package esic;

//Copyright 2018 ESIC at WSU distributed under the MIT license. Please see LICENSE file for further info.

import org.graphstream.graph.Edge;
import org.graphstream.graph.Graph;
import org.graphstream.graph.Node;
import org.graphstream.algorithm.measure.*;

//TODO: SOON items
//TODO: Figure out way to show tool tips/etc for different scoring methods
//TODO: figure out a way to support the new Test-Privilege info
//TODO: Linux support stuff
//TODO: write out a report containing the node scoring

//TODO: null out scoreReason(s) for methods that do not base on 'normal', since right now 'normal' reasons persist for any scoring method that does not explicitly state.
//TODO: look into possibly using different auto layout algorithm(s)
//TODO: look at SIDs for services and treat per service ID favorably
//TODO: overlay on hover of a node? ToolTips maybe?

//TODO: Intermediate time frame: custom scoring CSV:
//TODO:    support reading in score criteria for user IDs?
//TODO:    support setting node color thresholds in custom score.csv file
//TODO:    support max bounds if feature is missing (AKA ASLR cap to 30pts or something)
//TODO:    move CSS to score file?

//TODO: possible FUTURE features
//TODO:    eventually support collapsing nodes based on some...3rd file?
//TODO:    show more raw graph info?
//TODO:    show FireWall suggestions in more detail?
//TODO:    support reading in a directory of separate node host scans and graph the whole thing
//TODO:    support reading several files in which are node scans over time and assemble graph from aggregate information

public class AHAModel
{
	private static class ScoreItem
	{
		String criteria="", criteriaValue="";
		int scoreDelta=0;
		public ScoreItem(String crit, String critval, int sD)
		{
			criteria=crit;
			criteriaValue=critval;
			scoreDelta=sD;
		}
	}
	
	public static enum ScoreMethod {Normal,WorstCommonProc,ECScore}

	protected boolean m_debug=false, m_multi=true, m_overlayCustomScoreFile=false, m_hideOSProcs=false; //flags for verbose output, hiding of operating system procs, and drawing of multiple edges between verticies
	protected String m_inputFileName="BinaryAnalysis.csv", m_scoreFileName="scorefile.csv";
	protected int m_minScoreLowVuln=25, m_minScoreMedVuln=15;
	
	protected java.util.TreeMap<String,String> m_allListeningProcessMap=new java.util.TreeMap<String,String>(), m_osProcs=new java.util.TreeMap<String,String>();
	protected java.util.TreeMap<String,String> m_intListeningProcessMap=new java.util.TreeMap<String,String>(), m_extListeningProcessMap=new java.util.TreeMap<String,String>();
	protected Graph m_graph=null;
	protected AHAGUI m_gui=null;
	
	private java.util.ArrayList<ScoreItem> m_scoreTable=new java.util.ArrayList<ScoreItem>(32);
	
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

	public static String scrMethdAttr(ScoreMethod m) { 	return "ui.ScoreMethod"+m; }
	
	private void readScoreTable(String filename)
	{
		if (filename==null || filename.equals("")) { filename="MetricsTable.cfg"; }
		java.io.BufferedReader buff=null;
		try 
		{
			buff = new java.io.BufferedReader(new java.io.InputStreamReader(new java.io.FileInputStream(filename),"UTF8"));//new java.io.BufferedReader(new java.io.FileReader(filename));
			String line="";
			while ((line = buff.readLine()) != null)
			{
				line=line.trim();
				if (line.startsWith("//") || line.startsWith("#")) { continue; }
				String[] tokens=fixCSVLine(line);
				if (tokens.length<2) { continue;}
				int scoreDelta=-10000;
				try { scoreDelta=Integer.parseInt(tokens[2]); }
				catch (Exception e) { e.printStackTrace(); }
				
				if (scoreDelta > -101 && scoreDelta < 101) //valid range is only -100 to 100
				{
					System.out.println("ScoreConfig Line: criterion="+tokens[0]+" value="+tokens[1]+" score="+tokens[2]);
					m_scoreTable.add(new ScoreItem(tokens[0],tokens[1],scoreDelta)); //TODO this should probably get cleaned up, as comments and/or anything after the first main 3 fields will consume memory even though we never use them.
				}
			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
	}
	
	protected void exploreAndScore(Graph graph) //explores the node graph and assigns a scaryness score
	{
		long time=System.currentTimeMillis();
		java.util.TreeMap<String, Integer> lowestScoreForUserName=new java.util.TreeMap<String, Integer>();
		for (Node node : graph) //Stage 1 of scoring, either the entirety of a scoring algorithm, such as "Normal", or the first pass for multi stage algs
		{
			try
			{
				String nodeClass=node.getAttribute("ui.class"); 
				int score=generateNormalNodeScore(node);
				node.addAttribute(scrMethdAttr(ScoreMethod.Normal), Integer.toString(score)); //if we didn't have a custom score from another file, use our computed score
				
				//Begin WorstUserProc stage1 scoring
				String nodeUserName=node.getAttribute("username");
				if ( nodeUserName!=null )
				{
					Integer lowScore=lowestScoreForUserName.remove(nodeUserName);
					if (lowScore!=null && score>lowScore) { score=lowScore.intValue(); }
					lowestScoreForUserName.put(nodeUserName,score);
				} //End WorstUserProc stage1 scoring
				
				{ //Begin EC Method stage1 scoring
					if(nodeClass!=null && nodeClass.equalsIgnoreCase("external")) { node.addAttribute(scrMethdAttr(ScoreMethod.ECScore)+"Tmp", Integer.toString(200)); }
					else { node.addAttribute(scrMethdAttr(ScoreMethod.ECScore)+"Tmp", Double.toString(100-score)); } 
				} //End EC Method stage 1 scoring
			} catch (Exception e) { e.printStackTrace(); }
		}

		//Begin EC between loop compute
		EigenvectorCentrality ec = new EigenvectorCentrality(scrMethdAttr(ScoreMethod.ECScore)+"Tmp", org.graphstream.algorithm.measure.AbstractCentrality.NormalizationMode.MAX_1_MIN_0, 100, scrMethdAttr(ScoreMethod.Normal));
		ec.init(graph);
		ec.compute();
		System.out.println("Worst User Scores="+lowestScoreForUserName);
		//END EC between 
		
		for (Node node:graph) //Stage 2 of scoring, for scoring algorithms that need to make a second pass over the graph
		{
			try
			{ //Begin WorstUserProc stage2 scoring
				String nodeUserName=node.getAttribute("username");
				if (nodeUserName!=null)
				{
					Integer lowScore=lowestScoreForUserName.get(node.getAttribute("username"));
					if (lowScore==null) { System.out.println("no low score found, this should not happen"); continue; }
					node.addAttribute(scrMethdAttr(ScoreMethod.WorstCommonProc), lowScore.toString());
				} //End WorstUserProc stage2 scoring
				{ //Begin EC stage2 scoring
					double nodeScore   = Double.valueOf(node.getAttribute(scrMethdAttr(ScoreMethod.Normal)));
					double ecNodeScore = node.getAttribute(scrMethdAttr(ScoreMethod.ECScore)+"Tmp");
					ecNodeScore = nodeScore * (1-2*ecNodeScore);
					node.addAttribute(scrMethdAttr(ScoreMethod.ECScore), ((Integer)((Double)ecNodeScore).intValue()).toString());
				} //End EC stage2 scoring
			} catch (Exception e) { e.printStackTrace(); }
		}
		swapNodeStyles(ScoreMethod.Normal, time); //since we just finished doing the scores, swap to the 'Normal' score style when we're done.
	}

	protected int generateNormalNodeScore(Node n)
	{
		int score = 0;
		String scoreReason="", extendedReason="";
		System.out.println("Process: "+n.getId());
		for (ScoreItem si : m_scoreTable) 
		{
			try
			{
				boolean scoredTrue=false;
				if(n.getAttribute(si.criteria) != null)
				{
					System.out.println("    "+si.criteria+": " + n.getAttribute(si.criteria)); //TODO make printout more obvious here
					if(n.getAttribute(si.criteria).toString().equals(si.criteriaValue)) 
					{ 
						score+=si.scoreDelta; 
						scoreReason+=", "+si.criteria+"="+si.scoreDelta;
						scoredTrue=true;
					}
				}
				extendedReason+=", "+si.criteria+"("+si.scoreDelta+")"+"="+Boolean.toString(scoredTrue);
			}
			catch (Exception e) { System.out.println(e.getMessage()); }
		}
		System.out.println("  Score: " + score);
		n.addAttribute(scrMethdAttr(ScoreMethod.Normal)+"Reason", "FinalScore="+score+scoreReason);
		n.addAttribute(scrMethdAttr(ScoreMethod.Normal)+"ExtendedReason", "FinalScore="+score+extendedReason);
		return score;
	}

	public void swapNodeStyles(ScoreMethod m, long startTime)
	{
		System.out.println("Beginning swapNodeStyles()");
		for (Node n : m_graph)
		{
			try
			{
				String currentClass=n.getAttribute("ui.class"), customStyle=n.getAttribute(CUSTOMSTYLE);
				String sScore=n.getAttribute(scrMethdAttr(m)), sScoreReason=n.getAttribute(scrMethdAttr(m)+"Reason"), sScoreExtendedReason=n.getAttribute(scrMethdAttr(m)+"ExtendedReason"); 
				Integer intScore=null;
				try {intScore=Integer.parseInt(sScore);}
				catch (Exception e) {} 
				if (currentClass==null || !currentClass.equalsIgnoreCase("external") || intScore!=null)
				{
					if (currentClass!=null && currentClass.equalsIgnoreCase("external"))
					{ 
						n.addAttribute("ui.score", "0");
						n.addAttribute("ui.scoreReason", "External Node");
						n.addAttribute("ui.scoreExtendedReason", "External Node");
					}
					else if (m_overlayCustomScoreFile==true && customStyle!=null && customStyle.equalsIgnoreCase("yes"))
					{
						String score=n.getAttribute(CUSTOMSTYLE+".score");
						String style=n.getAttribute(CUSTOMSTYLE+".style");
						n.removeAttribute("ui.class");
						n.addAttribute("ui.style", style);
						n.addAttribute("ui.score", score);
						n.addAttribute("ui.scoreReason", "Custom Scorefile Overlay");
						n.addAttribute("ui.scoreExtendedReason", "Custom Scorefile Overlay");
					}
					else if (intScore!=null)
					{ 
						int score=intScore.intValue();
						n.addAttribute("ui.score", score);
						if (sScoreReason!=null) { n.addAttribute("ui.scoreReason", sScoreReason); } //TODO: since scoreReason only really exists for 'normal' this means that 'normal' reason persists in other scoring modes. For modes that do not base their reasoning on 'normal' this is probably incorrect.
						if (sScoreExtendedReason!=null) { n.addAttribute("ui.scoreExtendedReason", sScoreExtendedReason); }
						System.out.print(n.getId()+" Applying Score: " + score);
						n.addAttribute("ui.class", "high"); //default
						if(score > m_minScoreLowVuln) { n.addAttribute("ui.class", "low"); System.out.println("   Scored: low");}
						else if(score > m_minScoreMedVuln) { n.addAttribute("ui.class", "medium"); System.out.println("   Scored: medium");} 
						else { System.out.println("   Scored: high"); }
					}
				}
			}
			catch(Exception e) { e.printStackTrace(); }
		}
		System.out.println("Graph score complete using method="+m+" with useCustomScoring="+Boolean.toString(m_overlayCustomScoreFile)+". Took "+(System.currentTimeMillis()-startTime)+"ms.\n");
	}

	protected static String[] fixCSVLine(String s) //helper function to split, lower case, and clean lines of CSV into tokens
	{
		String[] ret=null;
		try
		{
			ret=s.replace('\ufeff', ' ').toLowerCase().split("\",\""); //the replace of ("\ufeff", "") removes the unicode encoding char at the beginning of the file, if it exists in the line. bleh.
			for (int i=0;i<ret.length;i++)
			{
				if (ret[i]!=null) { ret[i]=ret[i].replaceAll("\"", "").trim(); } //remove double quotes as we break into tokens, trim any whitespace on the outsides
			}
		}
		catch (Exception e) { System.out.print("fixCSVLine:"); e.printStackTrace(); }
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
		
		System.out.println("JRE: Vendor="+System.getProperty("java.vendor")+", Version="+System.getProperty("java.version"));
		System.out.println("OS: Arch="+System.getProperty("os.arch")+" Name="+System.getProperty("os.name")+" Vers="+System.getProperty("os.version"));
		System.out.println("AHA-GUI Version: "+AHAModel.class.getPackage().getImplementationVersion()+" starting up.");
		System.out.println("Using inputFile="+m_inputFileName);
		java.io.BufferedReader br = null;
		int lineNumber=1;
		java.util.TreeMap<String,Integer> hdr=new java.util.TreeMap<String,Integer>();
		try 
		{
			br = new java.io.BufferedReader(new java.io.InputStreamReader(new java.io.FileInputStream(m_inputFileName),"UTF8"));
			String line = "", header[]=fixCSVLine(br.readLine());
			for (int i=0;i<header.length;i++) 
			{ 
				String headerToken=header[i];
				if (headerToken.contains("processname") && !headerToken.equals("processname") && headerToken.length()=="processname".length() ) { System.out.println("Had to fix up 'processname' headertoken due to ByteOrderMark issues.");headerToken="processname"; }
				if (headerToken.contains("processname") && !headerToken.equals("processname") && Math.abs(headerToken.length()-"processname".length())<5) { System.out.println("Had to fix up 'processname' headertoken due to ByteOrderMark issues. hdrlen="+headerToken.length()+" ptlen="+"processname".length()); headerToken="processname"; }
				hdr.put(headerToken, Integer.valueOf(i)); 
			}
			String[] required={"processname","pid","protocol","state","localport","localaddress","remoteaddress","remoteport","remotehostname"};
			for (String s:required) { if (hdr.get(s)==null) { System.out.println("Input file: required column header field=\""+s+"\" was not detected, this will not go well."); } }

			while ((line = br.readLine()) != null) //this is the first loop, in this loop we're loading all the vertexes and their meta data, so we can then later connect the vertices
			{
				try
				{
					String[] tokens = fixCSVLine(line); 
					String fromNode=tokens[hdr.get("processname")]+"_"+tokens[hdr.get("pid")], protoLocalPort=tokens[hdr.get("protocol")]+"_"+tokens[hdr.get("localport")];
					String connectionState=tokens[hdr.get("state")], localAddr=tokens[hdr.get("localaddress")];
				
					Node node = m_graph.getNode(fromNode);
					if(node == null)
					{
						if (m_debug) { System.out.println("Found new process: Name=|"+fromNode+"|"); }
						node = m_graph.addNode(fromNode);
					}
					if (connectionState.equals("listening") || connectionState.equals("") )
					{
						m_allListeningProcessMap.put(protoLocalPort,fromNode); //push a map entry in the form of (proto_port, procname_PID) example map entry (tcp_49263, alarm.exe_5)
						if (m_debug) { System.out.printf("ListenMapPush: localPort=|%s| fromNode=|%s|\n",protoLocalPort,fromNode); }
						String portMapKey="ui.localListeningPorts";
						if( localAddr.equals("0.0.0.0") || localAddr.equals("::")) 
						{ 
							Edge e=m_graph.addEdge(node+"_external",node.toString(),"external");
							e.addAttribute("ui.class", "external");
							portMapKey="ui.extListeningPorts"; //since this is external, change the key we read/write when we store this new info
							m_extListeningProcessMap.put(protoLocalPort,fromNode);
						}
						else { m_intListeningProcessMap.put(protoLocalPort,fromNode); }
						java.util.TreeMap<String,String> listeningPorts=node.getAttribute(portMapKey);
						if (listeningPorts==null ) { listeningPorts=new java.util.TreeMap<String,String>(); }
						listeningPorts.put(protoLocalPort, protoLocalPort);
						node.addAttribute(portMapKey, listeningPorts);
					}
					for (int i=0;i<tokens.length && i<header.length;i++)
					{
						String processToken=tokens[i];
						if (  processToken==null || processToken.isEmpty() ) { processToken="null"; }
						if (m_debug) { System.out.printf("   Setting attribute %s for process %s\n",header[i],tokens[i]); }
						node.addAttribute(header[i],processToken);
					}
				}
				catch (Exception e) { System.out.print("start: first readthrough: input line "+lineNumber+":"); e.printStackTrace(); }
				lineNumber++;
			}
		} 
		catch (Exception e) { System.out.print("start: first readthrough: input line "+lineNumber+":"); e.printStackTrace(); } 
		finally 
		{
				try { if (br!=null) br.close(); } 
				catch (Exception e) { System.out.print("Failed to close file: "); e.printStackTrace(); }
		}

		if (m_debug)
		{
			System.out.println("\nAll Listeners:");
			for (java.util.Map.Entry<String, String> entry : m_allListeningProcessMap.entrySet()) { System.out.println(entry.getKey()+"="+entry.getValue()); }
			System.out.println("-------\nInternal Listeners:");
			for (java.util.Map.Entry<String, String> entry : m_intListeningProcessMap.entrySet()) { System.out.println(entry.getKey()+"="+entry.getValue()); }
			System.out.println("-------\nExternal Listeners:");
			for (java.util.Map.Entry<String, String> entry : m_extListeningProcessMap.entrySet()) { System.out.println(entry.getKey()+"="+entry.getValue()); }
			System.out.println("-------\n");
		}

		int connectionNumber=0;
		lineNumber=1;
		br = null;
		try 
		{
			br = new java.io.BufferedReader(new java.io.InputStreamReader(new java.io.FileInputStream(m_inputFileName),"UTF8"));
			String line = "";
			br.readLine(); //consume first line
			while ((line = br.readLine()) != null)  //this is the second loop, in this loop we're loading the connections between nodes
			{
				try
				{
					String[] tokens = fixCSVLine(line);
					String toNode, fromNode=tokens[hdr.get("processname")]+"_"+tokens[hdr.get("pid")], proto=tokens[hdr.get("protocol")], localPort=tokens[hdr.get("localport")], remotePort=tokens[hdr.get("remoteport")];
					String protoLocalPort=proto+"_"+localPort, protoRemotePort=proto+"_"+remotePort;
					String remoteAddr=tokens[hdr.get("remoteaddress")], localAddr=tokens[hdr.get("localaddress")], connectionState=tokens[hdr.get("state")], remoteHostname=tokens[hdr.get("remotehostname")];
					
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
							if ( (toNode=m_allListeningProcessMap.get(protoRemotePort))!=null )
							{
								Node tempNode=m_graph.getNode(fromNode);
								boolean duplicateEdge=false, timewait=false;
								if (connectionState.toLowerCase().contains("wait")) { timewait=true; }
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
									e.addAttribute("layout.weight", 10); //try to make internal edges longer
									if (duplicateEdge) { e.addAttribute("layout.weight", 5); }
									if (timewait && !duplicateEdge) { e.addAttribute("ui.class", "tw"); }
									if (!timewait && duplicateEdge) { e.addAttribute("ui.class", "duplicate"); }
									if (timewait && duplicateEdge) { e.addAttribute("ui.class", "duplicatetw"); }
									if (m_debug) { System.out.println("Adding edge from="+fromNode+" to="+toNode); }
								}
							}
							else if ( !(localAddr.equals("127.0.0.1") || localAddr.equals("::1")) )
							{
								System.out.printf("WARNING: Failed to find listener for: %s External connection? info: line=%d name=%s local=%s:%s remote=%s:%s status=%s\n",protoRemotePort,lineNumber,fromNode,localAddr,localPort,remoteAddr,remotePort,connectionState);
							}
							else if ( (localAddr.equals("127.0.0.1") || localAddr.equals("::1")) && (m_allListeningProcessMap.get(protoLocalPort)!=null) ) { /*TODO: probably in this case we should store this line and re-examine it later after reversing the from/to and make sure someone else has the link?*/ /*System.out.printf("     Line=%d expected?: Failed to find listener for: %s External connection? info: name=%s local=%s:%s remote=%s:%s status=%s\n",lineNumber,protoRemotePort,fromNode,localAddr,localPort,remoteAddr,remotePort,connectionState);*/  }
							else { System.out.printf("WARNING: Failed to find listener for: %s External connection? info: line=%d name=%s local=%s:%s remote=%s:%s status=%s\n",protoRemotePort,lineNumber,fromNode,localAddr,localPort,remoteAddr,remotePort,connectionState); }
						}
						else // if (connectionState.equalsIgnoreCase("established") && !(remoteAddr.trim().equals("127.0.0.1") || remoteAddr.trim().equals("::1")) )
						{ //System.out.printf("WARNING: Failed to find listener for: %s External connection? info: name=%s local=%s:%s remote=%s:%s status=%s\n",protoRemotePort,fromNode,localAddr,localPort,remoteAddr,remotePort,connectionState);
							Node tempNode=m_graph.getNode(fromNode);
							toNode="Ext_"+remoteAddr;
							if (remoteHostname==null || remoteHostname.equals("")) { remoteHostname=remoteAddr; } //cover the case that there is no FQDN
							boolean duplicateEdge=false, timewait=false;
							if (connectionState.toLowerCase().contains("wait")) { timewait=true; }
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
								e.addAttribute("layout.weight", 9); //try to make internal edges longer
								if (duplicateEdge) { e.addAttribute("layout.weight", 4); }
								if (!timewait && !duplicateEdge) { e.addAttribute("ui.class", "external"); }
								if (!timewait && duplicateEdge) { e.addAttribute("ui.class", "duplicateExternal"); }
								if (timewait && !duplicateEdge) { e.addAttribute("ui.class", "externaltw"); } 
								if (timewait && duplicateEdge) { e.addAttribute("ui.class", "duplicateExternaltw"); }
							}
						}
						//else if (m_listeningProcessMap.get(protoLocalPort)==null) //if we have a local port in the listeners we can ignore this connection
						//	{ //System.out.printf("WARNING: Failed to find listener for: %s External connection? info: name=%s local=%s:%s remote=%s:%s status=%s\n",protoRemotePort,fromNode,localAddr,localPort,remoteAddr,remotePort,connectionState); }
					}
				}
				catch (Exception e) { System.out.print("start: second readthrough: input line "+lineNumber+":"); e.printStackTrace(); }
				lineNumber++;
			}
		} 
		catch (Exception e) { System.out.print("start: second readthrough: input line "+lineNumber+":"); e.printStackTrace(); } 
		finally 
		{
			if (br != null) 
			{
				try { br.close(); } 
				catch (Exception e) { System.out.print("Failed to close file: "); e.printStackTrace(); }
			}
		}

		readScoreTable(null);
		readCustomScorefile();
		useFQDNLabels(m_graph, m_gui.m_showFQDN.isSelected());
		exploreAndScore(m_graph);
		new Thread(){
			public void run()
			{
				while (!Thread.interrupted())
				{
					try { m_gui.m_graphViewPump.blockingPump(); }
					catch (Exception e) { e.printStackTrace(); }
				}
			}
		}.start();
		
		
		java.util.Vector<Node> leftSideNodes=new java.util.Vector<Node>();
		for (Node n : m_graph) 
		{
			if (n.getId().contains("Ext_")) { leftSideNodes.add(n); }
			n.addAttribute("layout.weight", 6); //switched to add attribute rather than set attribute since it seems to prevent a possible race condition.
		}
		int numLeftNodes=leftSideNodes.size()+2; //1 is for main External node, 2 is so we dont put one at the very top or bottom
		leftSideNodes.insertElementAt(m_graph.getNode("external"),leftSideNodes.size()/2);
		
		try { Thread.sleep(1500); } catch (Exception e) {}
		m_gui.m_viewer.disableAutoLayout();
		
		int i=1;
		for (Node n : leftSideNodes)
		{ 
			org.graphstream.ui.geom.Point3 loc=m_gui.m_viewPanel.getCamera().transformPxToGu(30, (m_gui.m_viewPanel.getHeight()/numLeftNodes)*i);
			n.addAttribute("xyz", loc.x,loc.y,loc.z);
			i++;
		}
	}
	
	public static String getCommaSepKeysFromStringMap(java.util.Map<String, String> map)
	{
		StringBuilder sb=new StringBuilder("");
		if (map==null || map.isEmpty()) { return "None"; } //right now this makes for optimal code on the clients of this function, may not be the case in the future. 
		java.util.Iterator<String> it=map.keySet().iterator();
		while (it.hasNext())
		{
			sb.append(it.next());
			if (it.hasNext()) { sb.append(", "); }
		}
		return sb.toString();
	}

	protected void readCustomScorefile()
	{
		System.out.println("Using custom score fileName="+m_scoreFileName);
		java.io.BufferedReader br = null;
		try 
		{
			br = new java.io.BufferedReader(new java.io.InputStreamReader(new java.io.FileInputStream(m_scoreFileName),"UTF8"));
			String line = "";
			int lineNumber=0;
			while ((line = br.readLine()) != null)  //this is the second loop, in this loop we're loading the connections between nodes
			{
				try
				{
					String[] tokens = fixCSVLine(line);
					String directive=tokens[0], style=tokens[2], nodePathName=tokens[3];
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
									node.addAttribute(CUSTOMSTYLE,"yes");
									node.addAttribute(CUSTOMSTYLE+".score", score);
									node.addAttribute(CUSTOMSTYLE+".style", style);
									System.out.printf("scorefile: found node=%s path=%s, setting score=%s color=%s\n", node.getId(),nodePathName,score, style);
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
											e.addAttribute(CUSTOMSTYLE,"yes");
											e.addAttribute(CUSTOMSTYLE+".score", score);
											e.addAttribute(CUSTOMSTYLE+".style", style);
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
				if (argTokens[0].equalsIgnoreCase("scorefile")) { m_scoreFileName=argTokens[1]; m_overlayCustomScoreFile=true; } //path to custom score file, and enable since...that makes sense in this case
				if (argTokens[0].equalsIgnoreCase("inputFile")) { m_inputFileName=argTokens[1]; } //path to input file
				if (argTokens[0].equalsIgnoreCase("lowVulnThreshold")) { m_minScoreLowVuln=Integer.parseInt(argTokens[1]); } //set the minimum score to attain low vulnerability status
				if (argTokens[0].equalsIgnoreCase("medVulnThreshold")) { m_minScoreMedVuln=Integer.parseInt(argTokens[1]); } //set the minimum score to attain medium vulnerability status
				
				if (argTokens[0].equals("help")||argTokens[0].equals("?")) 
				{  
					System.out.println
					(
							"Arguments are as follows:"+
							"--debug : print additional information to console while running"+
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
