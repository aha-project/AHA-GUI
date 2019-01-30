package esic;

//Copyright 2018 ESIC at WSU distributed under the MIT license. Please see LICENSE file for further info.

import org.graphstream.graph.*;

public class AHAModel
{
	protected static class TableDataHolder
	{
		protected String[][] columnNames=null;
		protected Object[][][] tableData=null;
	}
	
	private static class ScoreItem
	{
		String criteria="", operation="";
		java.util.Vector<String> criteriaValues=null;
		int scoreDelta=0;
		public ScoreItem(String op, String crit, java.util.Vector<String> critvals, int sD)
		{
			operation=op;
			criteria=crit;
			criteriaValues=critvals;
			scoreDelta=sD;
		}
	}
	
	private int maxScore=0, metricsTableMultiPlatformScore=0;
	private java.util.ArrayList<ScoreItem> m_scoreTable=new java.util.ArrayList<ScoreItem>(32);
	
	protected boolean m_debug=false, m_verbose=false, m_useCustomOverlayScoreFile=false;
	protected String m_inputFileName="", m_scoreFileName="scorefile.csv";
	protected Graph m_graph=null;
	protected AHAGUI m_gui=null;
	protected java.util.TreeMap<String,Integer> platformSpecificMetricsTableScores=new java.util.TreeMap <String,Integer>(), m_listeningPortConnectionCount=new java.util.TreeMap<String,Integer>();
	protected java.util.TreeMap<String,String> m_allListeningProcessMap=new java.util.TreeMap<String,String>(), m_intListeningProcessMap=new java.util.TreeMap<String,String>(), m_extListeningProcessMap=new java.util.TreeMap<String,String>();
	protected java.util.TreeMap<String,String> m_knownAliasesForLocalComputer=new java.util.TreeMap<String,String>(), m_miscMetrics=new java.util.TreeMap<String,String>();
	
	protected static enum ScoreMethod {Normal,WorstCommonProcBETA,RelativeScoreBETA}
  protected static final String NormalizedScore="ui.ScoreMethodInternalNormalizedScore",RAWRelativeScore="ui.ScoreMethodInternalRAWRelativeScore", ForSibling="ui.ScoreMethodInternalForSibling", CUSTOMSTYLE="ui.weAppliedCustomStyle";
	protected String styleSheet = "graph { fill-color: black; }"+
			"node { size: 30px; fill-color: red; text-color: white; text-style: bold; text-size: 12; text-background-color: #222222; text-background-mode: plain; }"+
			"node.lowVuln { fill-color: green; }"+
			"node.medVuln { fill-color: yellow; }"+
			"node.highVuln { fill-color: orange; }"+
			"node.severeVuln { fill-color: red; }"+
			"node.custom { fill-mode: dyn-plain; }"+
			"node.external { size: 50px; fill-color: red; }"+
			"node.emphasize { stroke-mode: plain; stroke-color: blue; stroke-width: 2; }"+
			"node:clicked { stroke-mode: plain; stroke-color: purple; stroke-width: 4; }"+ /*fill-color: blue;*/
			"edge { shape: line; fill-color: #CCCCCC; stroke-width: 2; }"+
			"edge.duplicate { fill-color: #303030; }"+
			"edge.tw { stroke-mode: dashes; }"+
			"edge.external { fill-color: #883030; }"+
			"edge.clickedAccent { fill-color: purple; }"+
			"edge.emphasize { fill-color: blue; }";

	public static String scrMethdAttr(ScoreMethod m) { 	return "ui.ScoreMethod"+m; }
	
	private void readScoreTable(String filename)
	{
		if (filename==null || filename.equals("")) { filename="MetricsTable.cfg"; }
		System.out.println("Reading MetricsTable file="+filename);
		java.io.BufferedReader buff=null;
		try 
		{
			buff = new java.io.BufferedReader(new java.io.InputStreamReader(new java.io.FileInputStream(filename),"UTF8"));//new java.io.BufferedReader(new java.io.FileReader(filename));
			String line="";
			while ((line = buff.readLine()) != null)
			{
				line=line.trim().trim().trim();
				if (line.startsWith("//") || line.startsWith("#") || line.length()<5) { continue; }
				String[] tokens=fixCSVLine(line);
				if (tokens.length<4) { System.out.println("Malformed MetricsTable line:|"+line+"|"); continue;}
				int scoreDelta=-10000;
				String platform=tokens[4];
				try { scoreDelta=Integer.parseInt(tokens[3]); }
				catch (Exception e) { e.printStackTrace(); }
				
				if (scoreDelta > -101 && scoreDelta < 101) //valid range is only -100 to 100
				{
					String[] criteria=tokens[2].split("\\|");
					java.util.Vector<String> criteriaVec=new java.util.Vector<String>();
					for (String s : criteria) { criteriaVec.add(s.trim().trim().trim()); }
					System.out.println("MetricsTable read criterion: if "+tokens[1]+"="+criteriaVec.toString()+" score="+scoreDelta); //TODO fix
					m_scoreTable.add(new ScoreItem(tokens[0],tokens[1],criteriaVec,scoreDelta)); //TODO this should probably get cleaned up, as comments and/or anything after the first main 3 fields will consume memory even though we never use them.
					if (scoreDelta>0 && platform.equals("multiplatform")) { metricsTableMultiPlatformScore+=scoreDelta; }
					else if ( scoreDelta>0 && !platform.equals("optional"))
					{
						Integer platScore=platformSpecificMetricsTableScores.get(platform);
						if (platScore==null) { platScore=Integer.valueOf(0); }
						platScore+=scoreDelta;
						platformSpecificMetricsTableScores.put(platform, platScore);
					}
				}
			}
		}
		catch(Exception e) { e.printStackTrace(); }
		int maxPlatformScore=0;
		for ( java.util.Map.Entry<String, Integer> platformEntry: platformSpecificMetricsTableScores.entrySet() )
		{
			System.out.println("Platform="+platformEntry.getKey()+" platform specific score="+platformEntry.getValue());
			if (platformEntry.getValue()>maxPlatformScore) { maxPlatformScore=platformEntry.getValue(); }
		}
		System.out.println("MetricsTable: multiplatform max="+metricsTableMultiPlatformScore+" setting max possible score to: "+(maxScore=metricsTableMultiPlatformScore+maxPlatformScore));
		m_gui.updateOverlayLegendScale(maxScore);
	}
	
	protected void exploreAndScore(Graph graph) //explores the node graph and assigns a scaryness score
	{
		long time=System.currentTimeMillis();
		java.util.TreeMap<String, Integer> lowestScoreForUserName=new java.util.TreeMap<String, Integer>();
		for (Node node : graph) //Stage 1 of scoring, either the entirety of a scoring algorithm, such as "Normal", or the first pass for multi stage algs
		{
			try
			{
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
			} catch (Exception e) { e.printStackTrace(); }
		}

		if (m_verbose) { System.out.println("Worst User Scores="+lowestScoreForUserName); }
		
		for (Node node:graph) //Stage 2 of scoring, for scoring algorithms that need to make a second pass over the graph
		{
			try
			{ //Begin WorstUserProc stage2 scoring
				String nodeUserName=node.getAttribute("username");
				if (nodeUserName!=null)
				{
					Integer lowScore=lowestScoreForUserName.get(node.getAttribute("username"));
					if (lowScore==null) { System.err.println("no low score found, this should not happen"); continue; }
					node.addAttribute(scrMethdAttr(ScoreMethod.WorstCommonProcBETA), lowScore.toString());
				} 
				else { node.addAttribute(scrMethdAttr(ScoreMethod.WorstCommonProcBETA), "0"); } 
			} catch (Exception e) { e.printStackTrace(); } //End WorstUserProc stage2 scoring
		}
		computeRelativeScores(graph);
		swapNodeStyles(ScoreMethod.Normal, time); //since we just finished doing the scores, swap to the 'Normal' score style when we're done.
	}

	private void computeRelativeScores(Graph graph) //RelativeScore CODE //TODO: this code requires enums in scoreMethod that break the combo box display as well as possibly throwing NFEs
	{ 
		for (Node node:graph)
		{
			if (node.getAttribute("parents") != null)
			{
				java.util.List<String> nodeParents= node.getAttribute("parents");
				java.util.List<String> nodeParentsDistinct = nodeParents.stream().distinct().collect(java.util.stream.Collectors.toList());
				node.addAttribute("parents", nodeParentsDistinct);
			}
			if (node.getAttribute("sibling") != null)
			{
				java.util.List<String> nodeSiblings= node.getAttribute("sibling");
				java.util.List<String> nodeSiblingsDistinct = nodeSiblings.stream().distinct().collect(java.util.stream.Collectors.toList());
				node.addAttribute("sibling", nodeSiblingsDistinct);
			}
		}
		double maxscore=100, minscore=0;
		double maxNorm=maxscore+5, minNorm=minscore-5;     
		for (Node node:graph)
		{   
			if (node.getAttribute(scrMethdAttr(ScoreMethod.Normal))!=null)
			{
				double nodeScore   = Double.valueOf(node.getAttribute(scrMethdAttr(ScoreMethod.Normal)));
				double attacksurface =  1 - ((nodeScore-minNorm)/(maxNorm-minNorm));
				node.addAttribute(NormalizedScore, ((Double)attacksurface).toString());
				node.addAttribute(RAWRelativeScore, ((Double)0.0).toString());
				node.addAttribute(ForSibling, ((Double)0.0).toString());
			}
			node.addAttribute("finishScoring",((int)0));
			node.addAttribute("readyforsibling",((int)0));
		}
		int allDone=0;
		while (allDone == 0)
		{
			for (Node node:graph)
			{
				if ((node.getAttribute("parents") == null) && (node.getAttribute("sibling") == null))
				{
					node.addAttribute("finishScoring",((int)1));
					node.addAttribute("readyforsibling",((int)1));
					double normalized = Double.valueOf(node.getAttribute(NormalizedScore));
					node.addAttribute(RAWRelativeScore, ((Double)normalized).toString());
					node.addAttribute(ForSibling, ((Double)0.0).toString());
				}
				if ((node.getAttribute("parents") == null) && (node.getAttribute("sibling") != null))
				{
					if ((int)node.getAttribute("readyforsibling") == 0)
					{
						double normalized = Double.valueOf(node.getAttribute(NormalizedScore));
						node.addAttribute(RAWRelativeScore, ((Double)normalized).toString());
						node.addAttribute("readyforsibling",((int)1));
					}
					if ((int)node.getAttribute("finishScoring") == 0)
					{
						java.util.List<String> nodeSiblings= node.getAttribute("sibling");
						int test =1; // check all the parents of the node have complete scoring process
						for (int i = 0; i < nodeSiblings.size() ; i++) 
						{ 
							Node tempSibling = graph.getNode(nodeSiblings.get(i)) ;
							if ((int) tempSibling.getAttribute("readyforsibling") == 0)
							{
								test = 0;
								break;
							}
						} 
						if (test == 1)
						{
							double sum = 0;
							for (int i = 0; i < nodeSiblings.size() ; i++) 
							{ 
								Node tempSibling = graph.getNode(nodeSiblings.get(i)) ;
								double nScore= Double.valueOf(node.getAttribute(NormalizedScore));
								double pScore = Double.valueOf(tempSibling.getAttribute(ForSibling));
								sum = sum + (nScore * pScore);
							} 
							node.addAttribute(RAWRelativeScore, ((Double)sum).toString());
							node.addAttribute("finishScoring",((int)1));
						}
					}  
				}
				if ((node.getAttribute("parents") != null) && (node.getAttribute("sibling") == null))
				{
					if ((int)node.getAttribute("readyforsibling") == 0) { node.addAttribute("readyforsibling",((int)1)); }
					if ((int)node.getAttribute("finishScoring") == 0)
					{
						java.util.List<String> nodeparents= node.getAttribute("parents");
						int test =1; // check all the parents of the node have complete scoring process
						for (int i = 0; i < nodeparents.size() ; i++) 
						{ 
							Node tempParent = graph.getNode(nodeparents.get(i)) ;
							if ((int) tempParent.getAttribute("finishScoring") == 0)
							{
								test = 0;
								break;
							}
						} 
						if (test == 1)
						{
							double sum=0, nScore=Double.valueOf(node.getAttribute(NormalizedScore));
							for (int i = 0; i < nodeparents.size() ; i++) 
							{ 
								Node tempParent = graph.getNode(nodeparents.get(i)) ;
								double pScore = Double.valueOf(tempParent.getAttribute(RAWRelativeScore));
								sum = sum + (nScore * pScore);
							} 
							node.addAttribute(RAWRelativeScore, ((Double)(sum)).toString());
							node.addAttribute("finishScoring",((int)1));
						}
					}  
				}
				if ((node.getAttribute("parents") != null) && (node.getAttribute("sibling") != null))
				{
					if ((int)node.getAttribute("readyforsibling") == 0)
					{
						java.util.List<String> nodeparents= node.getAttribute("parents");
						int test =1; // check all the parents of the node have complete scoring process
						for (int i = 0; i < nodeparents.size() ; i++) 
						{ 
							Node tempParent = graph.getNode(nodeparents.get(i)) ;
							if ((int) tempParent.getAttribute("finishScoring") == 0)
							{
								test = 0;
								break;
							}
						} 
						if (test == 1)
						{
							double sum = 0;
							double nScore= Double.valueOf(node.getAttribute(NormalizedScore));
							for (int i = 0; i < nodeparents.size() ; i++) 
							{ 
								Node tempParent = graph.getNode(nodeparents.get(i)) ;
								double pScore = Double.valueOf(tempParent.getAttribute(RAWRelativeScore));
								sum = sum + (nScore * pScore);
							} 
							node.addAttribute(ForSibling, ((Double)(sum)).toString());
							node.addAttribute("readyforsibling",((int)1));
						}
					}
					if (((int)node.getAttribute("finishScoring") == 0) && ((int) node.getAttribute("readyforsibling") == 1))
					{
						java.util.List<String> nodeSiblings= node.getAttribute("sibling");
						int test =1; // check all the parents of the node have complete scoring process
						for (int i = 0; i < nodeSiblings.size() ; i++) 
						{ 
							Node tempSibling = graph.getNode(nodeSiblings.get(i)) ;
							if ((int) tempSibling.getAttribute("readyforsibling") == 0)
							{
								test = 0;
								break;
							}
						} 
						if (test == 1)
						{
							double sum = 0;
							double nScore= Double.valueOf(node.getAttribute(NormalizedScore));
							double SScore= Double.valueOf(node.getAttribute(ForSibling));
							for (int i = 0; i < nodeSiblings.size() ; i++) 
							{ 
								Node tempSibling = graph.getNode(nodeSiblings.get(i)) ;
								double pScore = Double.valueOf(tempSibling.getAttribute(ForSibling));
								sum = sum + (nScore * pScore);
							} 
							node.addAttribute(RAWRelativeScore, ((Double)(sum+SScore)).toString());
							node.addAttribute("finishScoring",((int)1));
						}
					}
				}
			}
			allDone=1;
			for (Node node:graph)
			{
				if ((int)node.getAttribute("finishScoring") == 0)
				{
					allDone=0;
					break;
				}
			}
		}
		double minRelative = Double.valueOf(graph.getNode(1).getAttribute(RAWRelativeScore)); //TODO: should both of these lines be the same?
		double maxRelative = Double.valueOf(graph.getNode(1).getAttribute(RAWRelativeScore));
		for (Node node:graph)
		{
			double relativeScore = Double.valueOf(node.getAttribute(RAWRelativeScore));
			if (relativeScore > maxRelative) { maxRelative = relativeScore ; }
			if (relativeScore < minRelative) { minRelative = relativeScore ; }
		}

		int newRangeMin = 0;
		int newRangeMax = 100;
		for (Node node:graph)
		{
			double relativeScore = Double.valueOf(node.getAttribute(RAWRelativeScore));
			double newRelativeScore= newRangeMin +((newRangeMax-newRangeMin)/(maxRelative-minRelative))*(relativeScore - minRelative);
			double newReversedRelativeScore= newRangeMax - newRelativeScore + newRangeMin ;
			node.addAttribute(scrMethdAttr(ScoreMethod.RelativeScoreBETA), Long.toString(Math.round(newReversedRelativeScore))); //).toString()
			//node.addAttribute(ReversedRangedRelativeScore, ((Double)newReversedRelativeScore).toString());
		}
	}
	
	protected int generateNormalNodeScore(Node n)
	{
		int score=0;
		String scoreReason="", extendedReason="";
		if (m_verbose) { System.out.println("Process: "+n.getId()+" examining metrics:"); }
		for (ScoreItem si : m_scoreTable) 
		{
			try
			{
				int numberOfTimesTrue=0;
				if(n.getAttribute(si.criteria) != null)
				{
					String attribute=n.getAttribute(si.criteria).toString().toLowerCase();
					for (String criterion : si.criteriaValues)
					{ 
						if( (si.operation.equals("eq") && attribute.equals(criterion)) || (si.operation.equals("ct") && attribute.contains(criterion))) 
						{ 
							score=score+si.scoreDelta;
							numberOfTimesTrue++;
						}
					}
					if (m_verbose) { System.out.println("    "+si.criteria+": input='"+attribute+"' looking for="+si.criteriaValues+" matched "+numberOfTimesTrue+" times"); }
					if (numberOfTimesTrue>0) { scoreReason+=", "+si.criteria+"="+(si.scoreDelta*numberOfTimesTrue); } 
				}
				String xtra="";
				if (numberOfTimesTrue>1) { xtra=" x"+numberOfTimesTrue; }
				extendedReason+=", "+si.criteria+"."+si.operation+si.criteriaValues+":"+si.scoreDelta+"="+capitalizeFirstLetter(Boolean.toString(numberOfTimesTrue>0))+xtra;
			}
			catch (Exception e) { System.out.println(e.getMessage()); }
		}
		if (m_verbose) { System.out.println("    Score: " + score); }
		if (score < 0) { score=0; } //System.out.println("Minimum final node score is 0. Setting to 0."); 
		n.addAttribute(scrMethdAttr(ScoreMethod.Normal)+"Reason", "FinalScore="+score+scoreReason);
		n.addAttribute(scrMethdAttr(ScoreMethod.Normal)+"ExtendedReason", "FinalScore="+score+extendedReason);
		return score;
	}

	public void swapNodeStyles(ScoreMethod m, long startTime)
	{
		for (Node n : m_graph)
		{
			try
			{
				String currentClass=n.getAttribute("ui.class"), customStyle=n.getAttribute(CUSTOMSTYLE);
				String sScore=n.getAttribute(scrMethdAttr(m)), sScoreReason=n.getAttribute(scrMethdAttr(m)+"Reason"), sScoreExtendedReason=n.getAttribute(scrMethdAttr(m)+"ExtendedReason"); 
				Integer intScore=null;
				try { intScore=Integer.parseInt(sScore); }
				catch (Exception e) { System.err.printf("Failed to parse score for node=%s failed to parse='%s'\n",n.getId(), sScore); } 
				if (currentClass==null || !currentClass.equalsIgnoreCase("external") || intScore!=null)
				{
					if (currentClass!=null && currentClass.equalsIgnoreCase("external"))
					{ 
						n.addAttribute("ui.score", "0");
						n.addAttribute("ui.scoreReason", "External Node");
						n.addAttribute("ui.scoreExtendedReason", "External Node");
					}
					else if (m_useCustomOverlayScoreFile==true && customStyle!=null && customStyle.equalsIgnoreCase("yes"))
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
						String uiClass="severeVuln";
						if (score > (0.25*maxScore)) { uiClass="highVuln"; }
						if (score > (0.50*maxScore)) { uiClass="medVuln"; }
						if (score > (0.75*maxScore)) { uiClass="lowVuln"; }
						n.addAttribute("ui.class", uiClass); //apply the class
						if (m_verbose) { System.out.println(n.getId()+" Applying Score: "+score+"   Vulnerability Score given: "+uiClass); }
					}
				}
			}
			catch(Exception e) { e.printStackTrace(); }
		}
		System.out.println("Graph score complete using method="+m+" with useCustomScoring="+Boolean.toString(m_useCustomOverlayScoreFile)+". Took "+(System.currentTimeMillis()-startTime)+"ms.");
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
	
	private void bumpIntRefCount(java.util.TreeMap<String,Integer> dataset, String key, int ammountToBump)
	{
		Integer value=dataset.get(key);
		if ( value==null ) { value=Integer.valueOf(0); }
		value+=ammountToBump;
		dataset.put(key, value); //System.out.println("Bumping ref for key="+key+" to new value="+value);
	}

	protected void readInputFile()
	{
		System.out.println("Reading primary input file=\""+m_inputFileName+"\"");
		m_knownAliasesForLocalComputer.put("127.0.0.1", "127.0.0.1"); //ensure these obvious ones are inserted in case we never see them in the scan
		m_knownAliasesForLocalComputer.put("::1", "::1"); //ensure these obvious ones are inserted in case we never see them in the scan
		java.io.BufferedReader br = null;
		int lineNumber=0;
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
					if (tokens.length<5) 
					{ 
						if ( line.trim().trim().trim().trim().length() > 0 ) // more than likely this means something converted the line endings and we've and we've got crlf\nclrfloletc, so only print and increment if there's a real issue.
						{
							System.err.println("First Stage Read: Skipping line #"+lineNumber+" because it is malformed."); 
							lineNumber++;
						}
						continue; 
					}
					lineNumber++;
					String fromNode=tokens[hdr.get("processname")]+"_"+tokens[hdr.get("pid")], protoLocalPort=tokens[hdr.get("protocol")]+"_"+tokens[hdr.get("localport")];
					String connectionState=tokens[hdr.get("state")], localAddr=tokens[hdr.get("localaddress")].trim();
					if (tokens[hdr.get("protocol")].equals("udp")) { connectionState="listening"; } //fix up in the case that a line is protocol==UDP and state==""
				
					Node node = m_graph.getNode(fromNode);
					if(node == null)
					{
						if (m_debug) { System.out.println("Found new process: Name=|"+fromNode+"|"); }
						node = m_graph.addNode(fromNode);
					}
					if (connectionState.contains("listen") )
					{
						m_knownAliasesForLocalComputer.put(localAddr, localAddr);
						m_allListeningProcessMap.put(protoLocalPort,fromNode); //push a map entry in the form of (proto_port, procname_PID) example map entry (tcp_49263, alarm.exe_5)
						if (m_debug) { System.out.printf("ListenMapPush: localPort=|%s| fromNode=|%s|\n",protoLocalPort,fromNode); }
						String portMapKey="ui.localListeningPorts";
						if( !localAddr.equals("127.0.0.1") && !localAddr.equals("::1") && !localAddr.startsWith("192.168") && !localAddr.startsWith("10.")) //TODO we really want anything that's not listening on localhost here: localAddr.equals("0.0.0.0") || localAddr.equals("::") || 
						{ 
							Edge e=m_graph.addEdge(node+"_external",node.toString(),"external");
							e.addAttribute("ui.class", "external");
							node.addAttribute("ui.hasExternalConnection", "yes");
							portMapKey="ui.extListeningPorts"; //since this is external, change the key we read/write when we store this new info
							m_extListeningProcessMap.put(protoLocalPort,fromNode);
							// RelativeScore CODE //
							java.util.List<String> parents= node.getAttribute("parents");
							if (parents == null) { parents = new java.util.ArrayList<String>(); }
							parents.add("external");
							node.addAttribute("parents", parents);
							// END RelativeScore CODE //
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
					if (lineNumber<5 && hdr.get("addedon")!=null && tokens[hdr.get("addedon")]!=null) { m_miscMetrics.put("detectiontime", tokens[hdr.get("addedon")]); } //only try to set the first few read lines
					if (lineNumber<5 && hdr.get("detectiontime")!=null && tokens[hdr.get("detectiontime")]!=null) { m_miscMetrics.put("detectiontime", tokens[hdr.get("detectiontime")]); } //only try to set the first few read lines //back compat for old scans, remove someday
				}
				catch (Exception e) { System.out.print("start: first readthrough: input line "+lineNumber+":"); e.printStackTrace(); }
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
		m_knownAliasesForLocalComputer.remove("::");
		m_knownAliasesForLocalComputer.remove("0.0.0.0"); //these don't seem to make sense as aliases to local host, so i'm removing them

		lineNumber=0;
		br = null;
		try 
		{
			br = new java.io.BufferedReader(new java.io.InputStreamReader(new java.io.FileInputStream(m_inputFileName),"UTF8"));
			String line = "";
			br.readLine(); //consume first line
			while ((line = br.readLine()) != null)  //this is the second loop, in this loop we're loading the connections between nodes
			{
				lineNumber++;
				try
				{
					String[] tokens = fixCSVLine(line);
					if (tokens.length<5) 
					{ 
						if ( line.trim().trim().trim().trim().length() > 0 ) // more than likely this means something converted the line endings and we've and we've got crlf\nclrfloletc, so only print and increment if there's a real issue.
						{
							System.err.println("Second Stage Read: Skipping line #"+lineNumber+" because it is malformed."); 
							lineNumber++;
						}
						continue; 
					}
					String toNode="UnknownToNodeError", fromNode=tokens[hdr.get("processname")]+"_"+tokens[hdr.get("pid")], proto=tokens[hdr.get("protocol")], localPort=tokens[hdr.get("localport")], remotePort=tokens[hdr.get("remoteport")];
					String protoLocalPort=proto+"_"+localPort, protoRemotePort=proto+"_"+remotePort;
					String remoteAddr=tokens[hdr.get("remoteaddress")].trim(), localAddr=tokens[hdr.get("localaddress")], connectionState=tokens[hdr.get("state")], remoteHostname=tokens[hdr.get("remotehostname")];
					if (tokens[hdr.get("protocol")].equals("udp")) { connectionState="listening"; } //fix up in the case that a line is protocol==UDP and state==""
					
					Node node = m_graph.getNode(fromNode);
					if(node == null)
					{
						System.out.println("WARNING: Second scan found new process: Name=|"+fromNode+"|, on line "+lineNumber+" ignoring."); 
						continue;
					}
					boolean duplicateEdge=false, timewait=false, exactSameEdgeAlreadyExists=false, isLocalOnly=false;
					Node tempNode=null;
					String connectionName=null,reverseConnectionName=null;
					if ( !connectionState.equalsIgnoreCase("listening") && !connectionState.equalsIgnoreCase("") ) //empty string is listening state for a bound udp socket on windows apparently
					{
						if (connectionState.toLowerCase().contains("wait") || connectionState.toLowerCase().contains("syn_") || connectionState.toLowerCase().contains("last_")) { timewait=true; }
						if ( m_knownAliasesForLocalComputer.get(remoteAddr)!=null ) 
						{ //if it's in that map, then this should be a connection to ourself
							isLocalOnly=true;
							if ( (toNode=m_allListeningProcessMap.get(protoRemotePort))!=null )
							{
								connectionName=(protoLocalPort+"<->"+protoRemotePort);
								reverseConnectionName=(protoRemotePort+"<->"+protoLocalPort);
							}
							else if ( m_knownAliasesForLocalComputer.get(localAddr)==null ) { System.out.printf("WARNING1: Failed to find listener for: %s External connection? info: line=%d name=%s local=%s:%s remote=%s:%s status=%s\n",protoRemotePort,lineNumber,fromNode,localAddr,localPort,remoteAddr,remotePort,connectionState);}
							else if (  m_knownAliasesForLocalComputer.get(localAddr)!=null && (m_allListeningProcessMap.get(protoLocalPort)!=null) ) { /*TODO: probably in this case we should store this line and re-examine it later after reversing the from/to and make sure someone else has the link?*/ /*System.out.printf("     Line=%d expected?: Failed to find listener for: %s External connection? info: name=%s local=%s:%s remote=%s:%s status=%s\n",lineNumber,protoRemotePort,fromNode,localAddr,localPort,remoteAddr,remotePort,connectionState);*/  }
							else if ( !connectionState.contains("syn-sent") ){ System.out.printf("WARNING3: Failed to find listener for: %s External connection? info: line=%d name=%s local=%s:%s remote=%s:%s status=%s\n",protoRemotePort,lineNumber,fromNode,localAddr,localPort,remoteAddr,remotePort,connectionState); }
							if (toNode==null) { /*System.err.println("toNode is null, problems may arrise. Setting toNode='UnknownToNodeError'");*/ toNode="UnknownToNodeError";}
						}
						else 
						{ 
							if (timewait && m_allListeningProcessMap.get(protoLocalPort)!=null) 
							{ 
								if (!fromNode.startsWith("unknown") && m_verbose) {System.out.println("Found a timewait we could correlate, but the name does not contain unknown. Would have changed from: "+fromNode+" to: "+m_allListeningProcessMap.get(protoLocalPort)+"?"); }
								else { fromNode=m_allListeningProcessMap.get(protoLocalPort); }
							}
							connectionName=(protoLocalPort+"<->"+protoRemotePort);
							reverseConnectionName=(protoRemotePort+"<->"+protoLocalPort);	
							toNode="Ext_"+remoteAddr;
							if (remoteHostname==null || remoteHostname.equals("")) { remoteHostname=remoteAddr; } //cover the case that there is no FQDN
						}
						
						if (connectionName!=null) { tempNode=m_graph.getNode(fromNode); } //some of the cases above wont actually result in a sane node setup, so only grab a node if we have connection name
						if (tempNode!=null)
						{
							for (Edge e : tempNode.getEdgeSet())
							{
								if (e.getOpposite(tempNode).getId().equals(toNode)) { duplicateEdge=true; }
								if (e.getId().equals(connectionName) || e.getId().equals(reverseConnectionName) ) { exactSameEdgeAlreadyExists=true; System.out.println("Exact same edge already exists!"); }
							}
							if (!exactSameEdgeAlreadyExists)
							{
								Edge e=m_graph.addEdge(connectionName,fromNode,toNode);
								if (m_debug) { System.out.println("Adding edge from="+fromNode+" to="+toNode); }
								if (m_allListeningProcessMap.get(protoLocalPort)!=null) { bumpIntRefCount(m_listeningPortConnectionCount,protoLocalPort,1); }
								if (e!=null && isLocalOnly)
								{
									e.addAttribute("layout.weight", 10); //try to make internal edges longer
									if (duplicateEdge) { e.addAttribute("layout.weight", 5); }
									if (timewait && !duplicateEdge) { e.addAttribute("ui.class", "tw"); }
									if (!timewait && duplicateEdge) { e.addAttribute("ui.class", "duplicate"); }
									if (timewait && duplicateEdge) { e.addAttribute("ui.class", "duplicate, tw"); }
									if (m_allListeningProcessMap.get(protoRemotePort)!=null) { bumpIntRefCount(m_listeningPortConnectionCount,protoRemotePort,1); }
								}
								else if(e!=null)
								{
									m_graph.getNode(toNode).addAttribute("ui.class", "external");
									m_graph.getNode(toNode).addAttribute("hostname", remoteHostname);
									m_graph.getNode(toNode).addAttribute("IP", remoteAddr);
									e.addAttribute("layout.weight", 9); //try to make internal edges longer
									if (duplicateEdge) { e.addAttribute("layout.weight", 4); }
									if (!timewait && !duplicateEdge) { e.addAttribute("ui.class", "external"); }
									if (!timewait && duplicateEdge) { e.addAttribute("ui.class", "duplicate, xternal"); }
									if (timewait && !duplicateEdge) { e.addAttribute("ui.class", "external, tw"); } 
									if (timewait && duplicateEdge) { e.addAttribute("ui.class", "duplicate, external, tw"); }
								}
								// RelativeScore CODE //
								Node toNode_node = m_graph.getNode(toNode);
								if ( toNode.startsWith("Ext_") || node.toString().startsWith("Ext_") )
								{
									java.util.List<String> parents= node.getAttribute("parents");
									if (parents== null) { parents = new java.util.ArrayList<String>(); }
									if (toNode.startsWith("Ext_"))
									{
										parents.add(toNode);
										node.addAttribute("parents", parents);
									}
									else
									{
										parents.add(node.toString());
										toNode_node.addAttribute("parents", parents);
									}
								}
								else
								{
									java.util.List<String> siblings=node.getAttribute("sibling");
									if (siblings == null) { siblings=new java.util.ArrayList<String>(); }
									siblings.add(toNode);
									node.addAttribute("sibling", siblings);
									
									siblings=toNode_node.getAttribute("sibling");
									if (siblings == null) { siblings = new java.util.ArrayList<String>(); }
									siblings.add(node.toString());
									toNode_node.addAttribute("sibling", siblings);
								} // END RelativeScore CODE //
								
							}
						}
					}
				}
				catch (Exception e) { System.out.print("start: second readthrough: input line "+lineNumber+":"); e.printStackTrace(); }
			}
		} 
		catch (Exception e) { System.out.print("start: second readthrough: input line "+lineNumber+":"); e.printStackTrace(); } 
		finally 
		{ if (br != null) 
			{ try { br.close(); } 
				catch (Exception e) { System.out.print("Failed to close file: "); e.printStackTrace(); }
			}
		}
		if (m_verbose) { System.out.println("All known aliases for the local machine address:"+m_knownAliasesForLocalComputer.keySet()); }
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
		System.out.println("Using CustomScoreOverlay file="+m_scoreFileName);
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
									System.out.printf("CustomScoreOverlay read line: node=%s path=%s, setting score=%s color=%s\n", node.getId(),nodePathName,score, style);
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
	
	protected void useFQDNLabels(Graph g, boolean useFQDN) 
	{
		for (Node n : m_graph) { n.addAttribute("ui.label", capitalizeFirstLetter(n.getId())); } //add labels
		if (useFQDN) 
		{  
			for (Node n : m_graph) 
			{ 
				String temp=n.getAttribute("ui.class");
				if (temp!=null && temp.equals("external"))
				{
					if (!n.getId().equals("external")) { n.addAttribute("ui.label", capitalizeFirstLetter("Ext_"+n.getAttribute("hostname"))); }
				}
			}
		} 
	}
	
	protected void hideOSProcs(Graph g, boolean hide) 
	{
		String[] osProcs={"c:\\windows\\system32\\services.exe","c:\\windows\\system32\\svchost.exe","c:\\windows\\system32\\wininit.exe","c:\\windows\\system32\\lsass.exe","null","system",};
		for (String s : osProcs)
		{
			genericHideUnhideNodes( "processpath=="+s,hide );
		}
	}
	
	protected void hideFalseExternalNode(Graph g, boolean hide) 
	{
		genericHideUnhideNodes( "processpath==external",hide );
	}
	
	protected int genericHideUnhideNodes( String criteria, boolean hide )
	{
		boolean notInverseSearch=true;
		int hidden=0;
		String regexp="";
		if (criteria.contains("==")) { regexp="=="; }
		if (criteria.contains("!=")) { regexp="!="; notInverseSearch=false;}
		String[] args=criteria.trim().trim().split(regexp);
		if (args.length < 2) { System.err.println("Hide: Unable to parse tokens:|"+criteria.trim().trim()+"|"); return 0; }
		try
		{
			String attribute=args[0].toLowerCase();
			for (Node node:m_graph)
			{
				String seeking=args[1].toLowerCase(); //important to do every loop since seeking may be modified if it is inverted
				String attrValue=node.getAttribute(attribute);
				if (attrValue!=null && seeking!=null && notInverseSearch==attrValue.equals(seeking))
				{
					if (notInverseSearch==false) { seeking="!"+seeking; } 
					java.util.TreeMap<String, String> hideReasons=node.getAttribute("ui.hideReasons");
					if (hideReasons==null)
					{
						hideReasons=new java.util.TreeMap<String, String>();
						node.setAttribute("ui.hideReasons", hideReasons);
					}
					if (hide) { hideReasons.put(seeking, seeking); }
					else { hideReasons.remove(seeking); }
					
					boolean nodeWillHide=!hideReasons.isEmpty();
					if (nodeWillHide)
					{
						if (node.getAttribute("ui.hide")==null) { hidden++; } //if it's not already hidden, count that we're going to hide it
						node.addAttribute( "ui.hide" );
						if (m_verbose) { System.out.println("Hide node="+node.getId()); }
						
					}
					else
					{
						node.removeAttribute( "ui.hide" );
						if (m_verbose) { System.out.println("Unhide node="+node.getId()); }
					}
					
					for (Edge e : node.getEdgeSet())
					{
						if (nodeWillHide) { e.addAttribute( "ui.hide" ); }
						else { if (e.getOpposite(node).getAttribute("ui.hide")==null ) { e.removeAttribute( "ui.hide" ); } }
					}
				}
			}
		}
		catch (Exception e) { e.printStackTrace(); }
		return hidden;
	}
	
	protected int genericEmphasizeNodes( String criteria, boolean emphasize ) //TODO add some trycatchery
	{
		boolean notInverseSearch=true;
		String regexp="";
		int emphasized=0;
		if (criteria.contains("==")) { regexp="=="; }
		if (criteria.contains("!=")) { regexp="!="; notInverseSearch=false;}
		String[] args=criteria.trim().trim().split(regexp);
		if (args.length < 2) { System.err.println("Emphasize: Unable to parse tokens:|"+criteria.trim().trim()+"|"); return 0; }
		try
		{
			String attribute=args[0].toLowerCase();
			for (Node node:m_graph)
			{
				String seeking=args[1].toLowerCase(); //important to do every loop since seeking may be modified if it is inverted
				String attrValue=node.getAttribute(attribute);
				if (attrValue!=null && seeking!=null && notInverseSearch==attrValue.equals(seeking))
				{
					if (notInverseSearch==false) { seeking="!"+seeking; } 
					java.util.TreeMap<String, String> emphasizeReasons=node.getAttribute("ui.emphasizeReasons");
					if (emphasizeReasons==null)
					{
						emphasizeReasons=new java.util.TreeMap<String, String>();
						node.setAttribute("ui.emphasizeReasons", emphasizeReasons);
					}
					if (emphasize) { emphasizeReasons.put(seeking, seeking); }
					else { emphasizeReasons.remove(seeking); }
					
					boolean nodeWillEmphasize=!emphasizeReasons.isEmpty();
					String nodeClass=node.getAttribute("ui.class");
					if (nodeClass==null) { nodeClass=""; }
					if (nodeWillEmphasize)
					{
						if (!nodeClass.contains("emphasize, ")) { nodeClass="emphasize, "+nodeClass; emphasized++;}
						if (m_verbose) { System.out.println("Emphasize node="+node.getId()); }
					}
					else
					{
						nodeClass=nodeClass.replaceAll("emphasize, ", ""); 
						if (m_verbose) { System.out.println("unEmphasize node="+node.getId()); }
					}
					//System.out.println("Writing node class=|"+nodeClass+"|");
					node.setAttribute("ui.class", nodeClass);
					
					for (Edge e : node.getEdgeSet())
					{
						String edgeClass=e.getAttribute("ui.class");
						if (edgeClass==null) { edgeClass=""; }
						if (nodeWillEmphasize) 
						{ 
							if (!edgeClass.contains("emphasize, ")) { edgeClass="emphasize, "+edgeClass;}
						}
						else { edgeClass=edgeClass.replaceAll("emphasize, ", ""); }
						e.setAttribute("ui.class", edgeClass);
					}
				}
			}
		}
		catch (Exception e) { e.printStackTrace(); }
		return emphasized;
	}
	
	protected java.util.Vector<String> m_lastSearchTokens=new java.util.Vector<String>();
	protected String handleSearch (String searchText)
	{
		if (searchText==null ) { return ""; }
		//undo what we did last time //TODO: maybe we can optimize and only undo what's changed since last time someday
		for (String s : m_lastSearchTokens)
		{
			if (s.startsWith("~")) { genericHideUnhideNodes(s.substring(1), false); }
			else { genericEmphasizeNodes(s, false); } //undo the whatever else
		}
		m_lastSearchTokens.clear();
		
		System.out.println("Search called with string=|"+searchText+"|");
		String[] tokens=searchText.split("\\|\\|");
		int hid=0, emph=0;
		for (String token : tokens)
		{
			if (token==null || token.equals("")) { continue; }
			token=token.trim().trim();
			m_lastSearchTokens.add(token);
			System.out.println("token=|"+token+"|");
			if (token.startsWith("~")) { hid+=genericHideUnhideNodes(token.substring(1), true); }
			else { emph+=genericEmphasizeNodes(token, true); }
		}
		System.out.println("");
		if ( hid==0 && emph==0 ) { return ""; } //this is needed to clear out the status when the user is no longer using it
		return " Status: Hidden="+hid+" Highlighted="+emph+" ";
	}
	
	protected static String capitalizeFirstLetter(String s)
	{
		s = s.toLowerCase();
		return Character.toString(s.charAt(0)).toUpperCase()+s.substring(1);
	}
	
	protected void start()
	{
		try
		{
			java.io.File testInputFile=getFileAtPath(m_inputFileName);
			if ( m_inputFileName==null || m_inputFileName.equals("") || testInputFile==null || !testInputFile.exists()) { System.err.println("No input file specified, bailing."); return; }
			m_gui.m_graphViewer.enableAutoLayout();
			m_graph.setAutoCreate(true);
			m_graph.setStrict(false);
			Node ext=m_graph.addNode("external");
			ext.addAttribute("ui.class", "external"); //Add a node for "external"
			ext.addAttribute("processpath","external"); //add fake process path
			
			System.out.println("JRE: Vendor="+System.getProperty("java.vendor")+", Version="+System.getProperty("java.version"));
			System.out.println("OS: Arch="+System.getProperty("os.arch")+" Name="+System.getProperty("os.name")+" Vers="+System.getProperty("os.version"));
			System.out.println("AHA-GUI Version: "+AHAModel.class.getPackage().getImplementationVersion()+" starting up.");
			
			readInputFile();
			readScoreTable(null);
			readCustomScorefile();
			useFQDNLabels(m_graph, m_gui.m_btmPnlShowFQDN.isSelected());
			exploreAndScore(m_graph);
			
			m_gui.startGraphRefreshThread();
			
			Thread.sleep(1500); 
			m_gui.m_graphViewer.disableAutoLayout();
			Thread.sleep(100);  //add delay to see if issues with moving ext nodes goes away
			
			java.util.Vector<Node> leftSideNodes=new java.util.Vector<Node>(); //moved this below the 1.5s graph stabilization threshold to see if it makes odd occasional issues with moving ext nodes better
			for (Node n : m_graph) 
			{
				if (n.getId().contains("Ext_")) { leftSideNodes.add(n); }
				n.addAttribute("layout.weight", 6); //switched to add attribute rather than set attribute since it seems to prevent a possible race condition.
			}
			int numLeftNodes=leftSideNodes.size()+2; //1 is for main External node, 2 is so we dont put one at the very top or bottom
			leftSideNodes.insertElementAt(m_graph.getNode("external"),leftSideNodes.size()/2);
			Thread.sleep(100);  //add delay to see if issues with moving ext nodes goes away
			int i=1;
			try
			{
				for (Node n : leftSideNodes)
				{ 
					org.graphstream.ui.geom.Point3 loc=m_gui.m_graphViewPanel.getCamera().transformPxToGu(60, (m_gui.m_graphViewPanel.getHeight()/numLeftNodes)*i);
					n.addAttribute("xyz", loc.x,loc.y,loc.z);
					i++;
				}
				m_gui.m_graphViewPanel.getCamera().setViewPercent(1.01d);
				org.graphstream.ui.geom.Point3 center=m_gui.m_graphViewPanel.getCamera().getViewCenter();
				org.graphstream.ui.geom.Point3 pixels=m_gui.m_graphViewPanel.getCamera().transformGuToPx(center.x, center.y, center.z);
				pixels.x-=60;
				center=m_gui.m_graphViewPanel.getCamera().transformPxToGu(pixels.x, pixels.y);
				m_gui.m_graphViewPanel.getCamera().setViewCenter(center.x, center.y, center.z);
			} catch (Exception e) { e.printStackTrace(); }
			
			java.io.File fname=new java.io.File(m_inputFileName);
			String inputFileName=fname.getName(), outputPath="";//get the file name only sans the path
			if (inputFileName.toLowerCase().endsWith(".csv"))
			{
				outputPath=inputFileName.substring(0, inputFileName.length()-4);
				outputPath+="-AHAReport.csv";
				outputPath=m_inputFileName.replace(fname.getName(), outputPath);
			}
			else { outputPath=m_inputFileName+=".aha-report.csv"; } //in the case that their input file was not sanely named, we just append .report.csv
			System.out.println("Saving report to path=\""+outputPath+"\"");
			writeReport(generateReport(),"AHA-GUI-Report.csv");
		} catch(Exception e) { e.printStackTrace(); } 
	}
	
	public static Object strAsInt(String s) //try to box in an integer (important for making sorters in tables work) but fall back to just passing through the Object
	{ 
		try { if (s!=null ) { return Integer.valueOf(s); } }
		catch (NumberFormatException nfe) {} //we really don't care about this, we'll just return the original object
		catch (Exception e) { System.out.println("s="+s);e.printStackTrace(); } //something else weird happened, let's print about it
		return s;
	}
	public static Object strAsDouble(String s) //try to box in an integer (important for making sorters in tables work) but fall back to just passing through the Object
	{ 
		try { if (s!=null ) { return Double.valueOf(s); } }
		catch (NumberFormatException nfe) {} //we really don't care about this, we'll just return the original object
		catch (Exception e) { System.out.println("s="+s);e.printStackTrace(); } //something else weird happened, let's print about it
		return s;
	}
	
	private TableDataHolder synch_report=new TableDataHolder(); //do not access from anywhere other than generateReport!
	protected TableDataHolder generateReport()
	{
		synchronized (synch_report)
		{
			if (synch_report.columnNames==null)
			{
				int NUM_SCORE_TABLES=2, tableNumber=0;
				String[][] columnHeaders= {{"Scan Information", "Value"},{"Process", "PID", "User","Connections","ExtPorts","Signed","ASLR","DEP","CFG","HiVA", "Score", "WPScore",/*"Normalized Score",*/"RelativeScore",/*"RangedRelativeScore","ReversedRangedRelativeScore","parents","sibling"*/},{}};
				Object[][][] tableData=new Object[NUM_SCORE_TABLES][][];
				{ //general info
					Object[][] data=new Object[8][2];
					int i=0;
					
					data[i][0]="Local Addresses of Scanned Machine";
					data[i++][1]=m_knownAliasesForLocalComputer.keySet().toString();
					data[i][0]="Local Time of Host Scan";
					data[i++][1]=m_miscMetrics.get("detectiontime");
					
					{
						int numExt=0, worstScore=100;
						double denominatorAccumulator=0.0d;
						String worstScoreName="";
						for (Node n : m_graph)
						{
							if (n.getAttribute("username")!=null && n.getAttribute("ui.hasExternalConnection")!=null && n.getAttribute("aslr")!=null && !n.getAttribute("aslr").equals("") && !n.getAttribute("aslr").equals("scanerror")) 
							{ 
								numExt++;
								String normalScore=n.getAttribute(AHAModel.scrMethdAttr(ScoreMethod.Normal));
								try
								{
									Integer temp=Integer.parseInt(normalScore);
									if (worstScore > temp) { worstScore=temp; worstScoreName=n.getId();}
									denominatorAccumulator+=(1.0d/(double)temp);
								} catch (Exception e) {}
							}
						}
						data[i][0]="Number of Externally Acccessible Processes";
						data[i++][1]=Integer.toString(numExt);
						data[i][0]="Score of Worst Externally Accessible Scannable Process";
						data[i++][1]="Process: "+worstScoreName+"  Score: "+worstScore;
						data[i][0]="Harmonic Mean of Scores of all Externally Accessible Processes";
						String harmonicMean="Harmonic Mean Computation Error";
						if (denominatorAccumulator > 0.000001d) { harmonicMean=String.format("%.2f", ((double)numExt)/denominatorAccumulator); }
						data[i++][1]=harmonicMean;
					}
					
					{
						int numInt=0, worstScore=100;
						double denominatorAccumulator=0.0d;
						String worstScoreName="";
						for (Node n : m_graph)
						{
							if (n.getAttribute("username")!=null && n.getAttribute("ui.hasExternalConnection")==null && n.getAttribute("aslr")!=null && !n.getAttribute("aslr").equals("") && !n.getAttribute("aslr").equals("scanerror")) 
							{ 
								numInt++;
								String normalScore=n.getAttribute(AHAModel.scrMethdAttr(ScoreMethod.Normal));
								try
								{
									Integer temp=Integer.parseInt(normalScore);
									if (worstScore > temp) { worstScore=temp; worstScoreName=n.getId();}
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
					}
					tableData[tableNumber]=data;
				}
				tableNumber++;
				{ //general node info
					tableData[tableNumber]=new Object[m_graph.getNodeCount()][];
					int i=0;
					for (Node n : m_graph)
					{
						int j=0; //tired of reordering numbers after moving columns around
						Object[] data=new Object[columnHeaders[tableNumber].length]; //if we run out of space we forgot to make a column header anyway
						String name=n.getAttribute("processname");
						if (name==null) { name=n.getAttribute("ui.label"); }
						data[j++]=name;
						data[j++]=strAsInt(n.getAttribute("pid"));
						data[j++]=n.getAttribute("username");
						data[j++]=Integer.valueOf(n.getEdgeSet().size()); //cant use wrapInt here because  //TODO: deduplicate connection set?
						String extPortString="";
						java.util.TreeMap<String,String> extPorts=n.getAttribute("ui.extListeningPorts");
						if (extPorts!=null) 
						{ 
							for (String s : extPorts.keySet())
							{
								String[] tmp=s.split("_");
								if (!extPortString.equals("")) { extPortString+=", "; }
								extPortString+=tmp[1];
							}
						}
						data[j++]=extPortString;
						data[j++]=n.getAttribute("authenticode");
						data[j++]=n.getAttribute("aslr"); //these all have to be lowercase to work remember :)
						data[j++]=n.getAttribute("dep");
						data[j++]=n.getAttribute("controlflowguard");
						data[j++]=n.getAttribute("highentropyva");
						data[j++]=strAsInt(n.getAttribute(AHAModel.scrMethdAttr(ScoreMethod.Normal)));
						data[j++]=strAsInt(n.getAttribute(AHAModel.scrMethdAttr(ScoreMethod.WorstCommonProcBETA)));
						// RelativeScore CODE //
						data[j++]=strAsDouble(n.getAttribute(scrMethdAttr(ScoreMethod.RelativeScoreBETA)));
						//data[j++]=n.getAttribute("parents");
						//data[j++]=n.getAttribute("sibling");
						// END RelativeScore CODE //
						tableData[tableNumber][i++]=data;
					}
				}
				synch_report.columnNames=columnHeaders;
				synch_report.tableData=tableData;
			}
			return synch_report;
		}
	}
	
	public static java.io.File getFileAtPath(String fileName)
	{
		try { return new java.io.File(fileName); }
		catch (Exception e) { return null; }
	}
	
	private void writeReport(TableDataHolder report, String fileName)
	{
		try (java.io.FileWriter fileOutput=new java.io.FileWriter(fileName))
		{
			for (int table=0;table<report.tableData.length;table++)
			{
				for (int tableHeaderCell=0;tableHeaderCell<report.columnNames[table].length;tableHeaderCell++) //print column headers
				{
					fileOutput.write("\""+report.columnNames[table][tableHeaderCell]+"\",");
				}
				fileOutput.write("\n");
				for (int row=0; row<report.tableData[table].length;row++) //print table data
				{
					for (int cell=0;cell<report.tableData[table][row].length;cell++)
					{
						fileOutput.write("\""+report.tableData[table][row][cell]+"\",");
					}
					fileOutput.write("\n"); //end table line
				}
				fileOutput.write("\n\n\n"); //3 lines between tables
			}
		}
		catch (Exception e) { e.printStackTrace(); }
	}
	
//	public AHAModel(String args[], boolean firstRun)
	public AHAModel(String inputFileName, boolean useOverlayScoreFile, String scoreFileName, boolean debug, boolean verbose)
	{
		m_inputFileName=inputFileName;
		m_useCustomOverlayScoreFile=useOverlayScoreFile;
		m_scoreFileName=scoreFileName;
		m_debug=debug;
		m_verbose=verbose;
	}
}
