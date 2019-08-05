package esic;
//Copyright 2018 ESIC at WSU distributed under the MIT license. Please see LICENSE file for further info.

import esic.AHAGraph.AHANode;

public class AHAModel implements Runnable
{
	private static class ScoreItem
	{
		String criteria="", operation="",  humanReadableReason="";
		java.util.Vector<String> criteriaValues=null;
		int scoreDelta=0;
		public ScoreItem(String op, String crit, java.util.Vector<String> critvals, int sD, String explanation)
		{
			operation=op;
			criteria=crit;
			criteriaValues=critvals;
			scoreDelta=sD;
			humanReadableReason=explanation;
		}
	}
	private int maxScore=0, metricsTableMultiPlatformScore=0;
	private java.util.ArrayList<ScoreItem> m_scoreTable=new java.util.ArrayList<>(32);
	private AHAController m_controller=null;
	private java.util.Vector<String> m_lastSearchTokens=new java.util.Vector<>();
	
	protected static enum ScoreMethod //method to number the enumerations is a bit ridiculous, but oh well.
	{
		Normal(0),WorstCommonProcBETA(1),RelativeScoreBETA(2);
		private int value;
    private static java.util.HashMap<Integer,ScoreMethod> map = new java.util.HashMap<>();
    private ScoreMethod(int value) { this.value = value; }
    static { for (ScoreMethod pageType : ScoreMethod.values()) { map.put(pageType.value, pageType); } }
    public static ScoreMethod valueOf(int scoreMethod) { return (ScoreMethod) map.get(scoreMethod); }
    public static ScoreMethod getValue(String scoreMethod)
    {
    	try { return (ScoreMethod) map.get(Integer.parseInt(scoreMethod)); }
    	catch (Exception e) { e.printStackTrace(); return Normal; }
    }
    public int getValue() { return value; }
	}
	
	protected static class TableDataHolder
	{
		protected String[][] columnNames=null;
		protected Object[][][] tableData=null;
	}
	
	protected static class ConnectionEntry
	{
		public String localAddr="", localPort="", remoteAddr="", remotePort="", remoteHostName="", protocol="", state="";
		public String toString() { return String.format("local=%s:%s remote=%s:%s dns=%s proto=%s state=%s",localAddr, localPort, remoteAddr, remotePort, remoteHostName, protocol, state ); }
	}
	
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
  protected static final String NormalizedScore="aha.ScoreMethodInternalNormalizedScore",RAWRelativeScore="aha.ScoreMethodInternalRAWRelativeScore", ForSibling="aha.ScoreMethodInternalForSibling", CUSTOMSTYLE="aha.weAppliedCustomStyle";
	protected int m_verbosity=0;
	protected boolean m_useCustomOverlayScoreFile=false;
	protected String m_inputFileName=null, m_scoreFileName=null;
	protected AHAGraph m_graph=new AHAGraph();
	protected java.util.TreeMap<String,Integer> m_platformSpecificMetricsTableScores=new java.util.TreeMap<>(), m_listeningPortConnectionCount=new java.util.TreeMap<>();
	protected java.util.TreeMap<String,String> m_allListeningProcessMap=new java.util.TreeMap<>(), m_intListeningProcessMap=new java.util.TreeMap<>(), m_extListeningProcessMap=new java.util.TreeMap<>();
	protected java.util.TreeMap<String,String> m_knownAliasesForLocalComputer=new java.util.TreeMap<>(), m_miscMetrics=new java.util.TreeMap<>();
	private java.util.concurrent.atomic.AtomicReference<ScoreMethod> m_currentlyPresentedScoreMethod=new java.util.concurrent.atomic.AtomicReference<>(ScoreMethod.Normal);
	
	public static String scrMethdAttr(ScoreMethod m) { 	return "aha.ScoreMethod"+m; }
	
	private void readScoreTable(String filename)
	{
		if (filename==null || filename.equals("")) { filename="MetricsTable.cfg"; }
		maxScore=0;
		metricsTableMultiPlatformScore=0;
		System.out.println("Reading MetricsTable file="+filename);
		java.io.BufferedReader buff=null;
		try 
		{
			buff = new java.io.BufferedReader(new java.io.InputStreamReader(new java.io.FileInputStream(filename),"UTF8"));//new java.io.BufferedReader(new java.io.FileReader(filename));
			String line="";
			while ((line = buff.readLine()) != null)
			{
				line=line.trim();
				if (line.startsWith("//") || line.startsWith("#") || line.length()<5) { continue; }
				String[] tokens=fixCSVLine(line);
				if (tokens.length<5) { System.out.println("Malformed MetricsTable line:|"+line+"|"); continue;}
				int scoreDelta=-10000;
				String platform=tokens[4];
				String humanReadibleExplanation="";
				try
				{
					int beginIdx=line.toLowerCase().indexOf(tokens[5]);
					humanReadibleExplanation=line.substring(beginIdx, tokens[5].length()+beginIdx);
				} catch (Exception e) { e.printStackTrace(); }
				try { scoreDelta=Integer.parseInt(tokens[3]); }
				catch (Exception e) { e.printStackTrace(); }
				
				if (scoreDelta > -101 && scoreDelta < 101) //valid range is only -100 to 100
				{
					String[] criteria=tokens[2].split("\\|");
					java.util.Vector<String> criteriaVec=new java.util.Vector<>();
					for (String s : criteria) { criteriaVec.add(s.trim()); }
					if (m_verbosity>0) { System.out.println("MetricsTable read criterion: if "+tokens[1]+"="+criteriaVec.toString()+" score="+scoreDelta+" Explanation='"+humanReadibleExplanation+"'"); }
					m_scoreTable.add(new ScoreItem(tokens[0],tokens[1],criteriaVec,scoreDelta, humanReadibleExplanation)); 
					if (scoreDelta>0 && platform.equals("multiplatform")) { metricsTableMultiPlatformScore+=scoreDelta; }
					else if ( scoreDelta>0 && !platform.equals("optional"))
					{
						Integer platScore=m_platformSpecificMetricsTableScores.get(platform);
						if (platScore==null) { platScore=Integer.valueOf(0); }
						platScore+=scoreDelta;
						m_platformSpecificMetricsTableScores.put(platform, platScore);
					}
				}
			}
		}
		catch(Exception e) { e.printStackTrace(); }
		int maxPlatformScore=0;
		for ( java.util.Map.Entry<String, Integer> platformEntry: m_platformSpecificMetricsTableScores.entrySet() )
		{
			System.out.println("Platform="+platformEntry.getKey()+" platform specific score="+platformEntry.getValue());
			if (platformEntry.getValue()>maxPlatformScore) { maxPlatformScore=platformEntry.getValue(); }
		}
		System.out.println("MetricsTable: multiplatform max="+metricsTableMultiPlatformScore+" setting max possible score to: "+(maxScore=metricsTableMultiPlatformScore+maxPlatformScore));
		m_controller.updateOverlayLegendScale(maxScore);
	}
	
	private void exploreAndScore(AHAGraph graph) //explores the node graph and assigns a score about how scary it is
	{
		long time=System.currentTimeMillis();
		java.util.TreeMap<String, Integer> lowestScoreForUserName=new java.util.TreeMap<>();
		for (AHANode node : graph) //Stage 1 of scoring, either the entirety of a scoring algorithm, such as "Normal", or the first pass for multi stage algs
		{
			try
			{
				int score=generateNormalNodeScore(node);
				node.setAttribute(scrMethdAttr(ScoreMethod.Normal), Integer.toString(score)); //if we didn't have a custom score from another file, use our computed score
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

		if (m_verbosity>0) { System.out.println("Worst User Scores="+lowestScoreForUserName); }
		
		for (AHANode node:graph) //Stage 2 of scoring, for scoring algorithms that need to make a second pass over the graph
		{
			try
			{ //Begin WorstUserProc stage2 scoring
				String nodeUserName=node.getAttribute("username");
				if (nodeUserName!=null)
				{
					Integer lowScore=lowestScoreForUserName.get(node.getAttribute("username"));
					if (lowScore==null) { System.err.println("no low score found, this should not happen"); continue; }
					node.setAttribute(scrMethdAttr(ScoreMethod.WorstCommonProcBETA), lowScore.toString());
					node.setAttribute(scrMethdAttr(ScoreMethod.WorstCommonProcBETA)+"Reason", "WPScore="+lowScore);
				}
				else
				{
					node.setAttribute(scrMethdAttr(ScoreMethod.WorstCommonProcBETA), "0");
					node.setAttribute(scrMethdAttr(ScoreMethod.WorstCommonProcBETA)+"Reason", "WPScore=0");
				}
			} catch (Exception e) { e.printStackTrace(); } //End WorstUserProc stage2 scoring
		}
		computeRelativeScores(graph);
		swapNodeStyles(ScoreMethod.Normal, time); //since we just finished doing the scores, swap to the 'Normal' score style when we're done.
	}

	private void computeRelativeScores(AHAGraph graph) //RelativeScore CODE 
	{ 
		try
		{
			for (AHANode node:graph)
			{
				if (node.getAttribute("parents") != null)
				{
					java.util.List<String> nodeParents=node.getStringList("parents");
					java.util.List<String> nodeParentsDistinct=nodeParents.stream().distinct().collect(java.util.stream.Collectors.toList());
					node.putStringList("parents", nodeParentsDistinct);
				}
				if (node.getAttribute("sibling") != null)
				{
					java.util.List<String> nodeSiblings=node.getStringList("sibling");
					java.util.List<String> nodeSiblingsDistinct=nodeSiblings.stream().distinct().collect(java.util.stream.Collectors.toList());
					node.putStringList("sibling", nodeSiblingsDistinct);
				}
			}
			double maxscore=maxScore, minscore=0;
			double maxNorm=maxscore+5, minNorm=minscore-5;
			for (AHANode node:graph)
			{
				if (node.getAttribute(scrMethdAttr(ScoreMethod.Normal))!=null)
				{
					double nodeScore=Double.valueOf(node.getAttribute(scrMethdAttr(ScoreMethod.Normal)));
					double attacksurface= 1-((nodeScore-minNorm)/(maxNorm-minNorm));
					node.setAttribute(NormalizedScore, Double.toString(attacksurface));
					node.setAttribute(RAWRelativeScore, "0.0");
					node.setAttribute(ForSibling, "0.0");
				}
				node.setAttribute("finishScoring","false");
				node.setAttribute("readyforsibling","false");
			}
			boolean allDone=false;
			while (allDone==false)
			{
				for (AHANode node:graph)
				{
					if ((node.getAttribute("parents") == null) && (node.getAttribute("sibling") == null))
					{
						node.setAttribute("finishScoring","true");
						node.setAttribute("readyforsibling","true");
						node.setAttribute(RAWRelativeScore, node.getAttribute(NormalizedScore));
						node.setAttribute(ForSibling, "0.0");
					}
					if ((node.getAttribute("parents") == null) && (node.getAttribute("sibling") != null))
					{
						if (node.getAttribute("readyforsibling").equals("true"))
						{
							node.setAttribute(RAWRelativeScore, node.getAttribute(NormalizedScore));
							node.setAttribute("readyforsibling","true");
						}
						if (node.getAttribute("finishScoring").equals("true"))
						{
							java.util.List<String> nodeSiblings=node.getStringList("sibling");
							boolean test=true; // check all the parents of the node have complete scoring process
							for (int i=0; i<nodeSiblings.size(); i++)
							{
								AHANode tempSibling = graph.getNode(nodeSiblings.get(i));
								if (tempSibling.getAttribute("readyforsibling").equals("false"))
								{
									test=false;
									break;
								}
							}
							if (test == true)
							{
								double sum=0;
								for (int i=0; i<nodeSiblings.size(); i++)
								{
									AHANode tempSibling=graph.getNode(nodeSiblings.get(i));
									double nScore=Double.valueOf(node.getAttribute(NormalizedScore));
									double pScore=Double.valueOf(tempSibling.getAttribute(ForSibling));
									sum=sum+(nScore*pScore);
								}
								node.setAttribute(RAWRelativeScore, Double.toString(sum));
								node.setAttribute("finishScoring","true");
							}
						}
					}
					if ((node.getAttribute("parents") != null) && (node.getAttribute("sibling") == null))
					{
						if (node.getAttribute("readyforsibling").equals("false")) { node.setAttribute("readyforsibling","true"); }
						if (node.getAttribute("finishScoring").equals("false"))
						{
							java.util.List<String> nodeparents=node.getStringList("parents");
							boolean test=true; // check all the parents of the node have complete scoring process
							for (int i = 0; i < nodeparents.size() ; i++)
							{
								AHANode tempParent = graph.getNode(nodeparents.get(i));
								if (tempParent.getAttribute("finishScoring").equals("false"))
								{
									test=false;
									break;
								}
							}
							if (test == true)
							{
								double sum=0, nScore=Double.valueOf(node.getAttribute(NormalizedScore));
								for (int i=0; i<nodeparents.size(); i++)
								{
									AHANode tempParent=graph.getNode(nodeparents.get(i));
									double pScore=Double.valueOf(tempParent.getAttribute(RAWRelativeScore));
									sum=sum+(nScore*pScore);
								}
								node.setAttribute(RAWRelativeScore, Double.toString(sum));
								node.setAttribute("finishScoring","true");
							}
						}
					}
					if ((node.getAttribute("parents") != null) && (node.getAttribute("sibling") != null))
					{
						if (node.getAttribute("readyforsibling").equals("false"))
						{
							java.util.List<String> nodeparents=node.getStringList("parents");
							boolean test=true; // check all the parents of the node have complete scoring process
							for (int i=0; i<nodeparents.size(); i++)
							{
								AHANode tempParent = graph.getNode(nodeparents.get(i));
								if (tempParent.getAttribute("finishScoring").equals("false"))
								{
									test=false;
									break;
								}
							}
							if (test == true)
							{
								double sum=0;
								double nScore= Double.valueOf(node.getAttribute(NormalizedScore));
								for (int i=0; i<nodeparents.size(); i++)
								{
									AHANode tempParent = graph.getNode(nodeparents.get(i));
									double pScore = Double.valueOf(tempParent.getAttribute(RAWRelativeScore));
									sum=sum+(nScore*pScore);
								}
								node.setAttribute(ForSibling, Double.toString(sum));
								node.setAttribute("readyforsibling","true");
							}
						}
						if ((node.getAttribute("finishScoring").equals("false")) && (node.getAttribute("readyforsibling").equals("true")))
						{
							java.util.List<String> nodeSiblings=node.getStringList("sibling");
							boolean test=true; // check all the parents of the node have complete scoring process
							for (int i=0; i<nodeSiblings.size(); i++)
							{
								AHANode tempSibling = graph.getNode(nodeSiblings.get(i));
								if (tempSibling.getAttribute("readyforsibling").equals("false"))
								{
									test=false;
									break;
								}
							}
							if (test == true)
							{
								double sum=0;
								double nScore=Double.valueOf(node.getAttribute(NormalizedScore));
								double SScore=Double.valueOf(node.getAttribute(ForSibling));
								for (int i=0; i<nodeSiblings.size(); i++)
								{
									AHANode tempSibling=graph.getNode(nodeSiblings.get(i));
									double pScore=Double.valueOf(tempSibling.getAttribute(ForSibling));
									sum=sum+(nScore*pScore);
								}
								node.setAttribute(RAWRelativeScore, Double.toString(sum+SScore));
								node.setAttribute("finishScoring","true");
							}
						}
					}
				}
				allDone=true;
				for (AHANode node:graph)
				{
					if (node.getAttribute("finishScoring").equals("false"))
					{
						allDone=false;
						break;
					}
				}
			}
			double minRelative=Double.valueOf(graph.getNode("external").getAttribute(RAWRelativeScore)); //TODO: should both of these lines be the same? it was getNode(1)...which didn't make sense to me...
			double maxRelative=Double.valueOf(graph.getNode("external").getAttribute(RAWRelativeScore));
			for (AHANode node:graph)
			{
				double relativeScore = Double.valueOf(node.getAttribute(RAWRelativeScore));
				if (relativeScore > maxRelative) { maxRelative=relativeScore; }
				if (relativeScore < minRelative) { minRelative=relativeScore; }
			}
			int newRangeMin=0, newRangeMax=100;
			for (AHANode node:graph)
			{
				double relativeScore = Double.valueOf(node.getAttribute(RAWRelativeScore));
				double newRelativeScore= newRangeMin +((newRangeMax-newRangeMin)/(maxRelative-minRelative))*(relativeScore - minRelative);
				double newReversedRelativeScore= newRangeMax - newRelativeScore + newRangeMin;
				node.setAttribute(scrMethdAttr(ScoreMethod.RelativeScoreBETA), Long.toString(Math.round(newReversedRelativeScore)));
				node.setAttribute(scrMethdAttr(ScoreMethod.RelativeScoreBETA)+"Reason", "RelativeScore="+Long.toString(Math.round(newReversedRelativeScore))+"");
			}
		}
		catch (Exception e) { e.printStackTrace(); }
	}
	
	private int generateNormalNodeScore(AHANode node)
	{
		int score=0;
		String reason="";
		if (m_verbosity>0) { System.out.println("Process: "+node.getId()+" examining metrics:"); }
		for (ScoreItem si : m_scoreTable) 
		{
			try
			{
				int numberOfTimesTrue=0;
				if(node.getAttribute(si.criteria) != null)
				{
					String attribute=node.getAttribute(si.criteria).toString().toLowerCase();
					for (String criterion : si.criteriaValues)
					{ 
						if( (si.operation.equals("eq") && attribute.equals(criterion)) || (si.operation.equals("ct") && attribute.contains(criterion))) 
						{ 
							score=score+si.scoreDelta;
							numberOfTimesTrue++;
						}
						if( si.operation.equals("gt") && attribute!=null && criterion!=null && !attribute.equals("null") && !attribute.equals("")) 
						{ 
							try
							{
								Double criteriaDouble=Double.parseDouble(criterion);
								Double attributeDouble=Double.parseDouble(attribute);
								
								if ( attributeDouble > criteriaDouble )
								{	//System.out.println("matched criteria for attribute="+attribute+" greater than criteria="+criterion);
									score=score+si.scoreDelta;
									numberOfTimesTrue++;
								}
							} catch (Exception e) { e.printStackTrace(); }
						}
					} 
					if (m_verbosity>0) { System.out.println("    "+si.criteria+": input='"+attribute+"' looking for="+si.criteriaValues+" matched "+numberOfTimesTrue+" times"); }
				}
				String xtra="";
				if (numberOfTimesTrue>1) { xtra=" x"+numberOfTimesTrue; }
				reason+=", "+si.humanReadableReason+"."+si.operation+si.criteriaValues+":"+si.scoreDelta+"="+capitalizeFirstLetter(Boolean.toString(numberOfTimesTrue>0))+xtra;
			}
			catch (Exception e) { System.out.println(e.getMessage()); }
		}
		if (m_verbosity>0) { System.out.println("    Score: " + score); }
		if (score < 0) { score=0; } //System.out.println("Minimum final node score is 0. Setting to 0."); 
		node.setAttribute(scrMethdAttr(ScoreMethod.Normal)+"Reason", "FinalScore="+score+reason);
		return score;
	}

	protected void swapNodeStyles(ScoreMethod m, long startTime)
	{ 
		if (m==null) { m=m_currentlyPresentedScoreMethod.get(); }
		else { m_currentlyPresentedScoreMethod.set(m); }
		for (AHANode node : m_graph)
		{
			try
			{
				String currentClass=node.getAttribute("ui.class"), customStyle=node.getAttribute(CUSTOMSTYLE);
				String sScore=node.getAttribute(scrMethdAttr(m)), sScoreReason=node.getAttribute(scrMethdAttr(m)+"Reason"); 
				Integer intScore=null;
				try { intScore=Integer.parseInt(sScore); }
				catch (Exception e) { System.err.printf("Failed to parse score for node=%s failed to parse='%s'\n",node.getId(), sScore); } 
				if (currentClass==null || !currentClass.equalsIgnoreCase("external") || intScore!=null)
				{
					if (currentClass!=null && currentClass.equalsIgnoreCase("external"))
					{ 
						node.setAttribute("aha.score", "0");
						node.setAttribute("aha.scoreReason", "External Node=N/A");
					}
					else if (m_useCustomOverlayScoreFile==true && customStyle!=null && customStyle.equalsIgnoreCase("yes"))
					{
						String score=node.getAttribute(CUSTOMSTYLE+".score");
						String style=node.getAttribute(CUSTOMSTYLE+".style");
						node.removeAttribute("ui.class");
						node.setAttribute("ui.style", style);
						node.setAttribute("aha.score", score);
						node.setAttribute("aha.scoreReason", "Custom Scorefile Overlay=N/A");
					}
					else if (intScore!=null)
					{ 
						int score=intScore.intValue();
						node.setAttribute("aha.score", intScore.toString());
						if (sScoreReason!=null) { node.setAttribute("aha.scoreReason", sScoreReason); } 
						String uiClass="severeVuln";
						if (score > (0.25*maxScore)) { uiClass="highVuln"; }
						if (score > (0.50*maxScore)) { uiClass="medVuln"; }
						if (score > (0.75*maxScore)) { uiClass="lowVuln"; }
						node.setAttribute("ui.class", uiClass); //apply the class
						if (m_verbosity>0) { System.out.printf("%16s Applying Score: %3d   Vulnerability Score given: %s\n",node.getId(),score,uiClass); }
					}
				}
			}
			catch(Exception e) { e.printStackTrace(); }
		}
		System.out.println("Graph score complete using method="+m+" with useCustomScoring="+Boolean.toString(m_useCustomOverlayScoreFile)+". Took "+(System.currentTimeMillis()-startTime)+"ms.");
	}
	
	private static String[] fixCSVLine(String s) //helper function to split, lower case, and clean lines of CSV into tokens
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
	
	private void bumpIntRefCount(java.util.TreeMap<String,Integer> dataset, String key, int ammountToBump)
	{
		Integer value=dataset.get(key);
		if ( value==null ) { value=Integer.valueOf(0); }
		value+=ammountToBump;
		dataset.put(key, value); //System.out.println("Bumping ref for key="+key+" to new value="+value);
	}
	
	private void readInputFile()
	{
		System.out.println("Reading primary input file=\""+m_inputFileName+"\"");
		m_knownAliasesForLocalComputer.put("127.0.0.1", "127.0.0.1"); //ensure these obvious ones are inserted in case we never see them in the scan
		m_knownAliasesForLocalComputer.put("::1", "::1"); //ensure these obvious ones are inserted in case we never see them in the scan
		java.util.TreeMap<String,Integer> hdr=new java.util.TreeMap<>();
		java.util.TreeMap<Integer,String> reverseHdr=new java.util.TreeMap<>();
		java.util.ArrayList<java.util.ArrayList<String>> inputLines=new java.util.ArrayList<>(512);
		java.util.TreeMap<String, String> metadata=new java.util.TreeMap<>();
		metadata.put("RequiredHeaderTokens", "processname,pid,protocol,state,localport,localaddress,remoteaddress,remoteport,remotehostname");
		if (!readCSVFileIntoArrayList(m_inputFileName, metadata, hdr, inputLines)) { System.out.println("Failed to read input file, bailing."); return; }
		for (String s:hdr.keySet()) { reverseHdr.put(hdr.get(s), s); } 
		java.util.Vector<String> nodeAttributeExclusions=new java.util.Vector<>();
		java.util.Collections.addAll(nodeAttributeExclusions, new String[]{"localaddress","localport","remoteaddress","remoteport","state","protocol"}); //TODO blacklist proto when the new system for show/hide nodes is in place
		
		AHANode ext=m_graph.addNode("external");
		ext.setAttribute("ui.class", "external"); //Add a node for "external"
		ext.setAttribute("aha.externalNode","aha.externalNode");
		ext.getStringMap("aha.graphlayer").put("aha.virtextnode", "aha.virtextnode");
		
		int lineNumber=0;
		for (java.util.ArrayList<String> tokens : inputLines)
		{
			lineNumber++;
			//fix-ups for well known file issues (will persist for all of readInputFile since we modify the input tokens)
			if (tokens.get(hdr.get("protocol")).contains("6")) { tokens.set(hdr.get("protocol"),tokens.get(hdr.get("protocol")).replaceAll("6", "")); } //if we have tcp6/udp6, remove the '6'
			if (tokens.get(hdr.get("protocol")).equals("udp")) { tokens.set(hdr.get("state"),"listening"); } //fix up in the case that a line is protocol==UDP and state==""
			if (hdr.get("remotehostname")==null) { tokens.set(hdr.get("remotehostname"),""); } //some versions of the linux scanner forgot to populate this column
			
			String fromNode=tokens.get(hdr.get("processname"))+"_"+tokens.get(hdr.get("pid")), protoLocalPort=tokens.get(hdr.get("protocol"))+"_"+tokens.get(hdr.get("localport")), proto=tokens.get(hdr.get("protocol"));
			String connectionState=tokens.get(hdr.get("state")), localAddr=tokens.get(hdr.get("localaddress")).trim();

			AHANode node = m_graph.getNode(fromNode);
			if(node == null)
			{
				if (m_verbosity>3) { System.out.println("Found new process: Name=|"+fromNode+"|"); }
				node = m_graph.addNode(fromNode);
			}
			if (connectionState.contains("listen") )
			{
				m_knownAliasesForLocalComputer.put(localAddr, localAddr);
				m_allListeningProcessMap.put(protoLocalPort,fromNode); //push a map entry in the form of (proto_port, procname_PID) example map entry (tcp_49263, alarm.exe_5)
				if (m_verbosity>4) { System.out.printf("ListenMapPush: localPort=|%s| fromNode=|%s|\n",protoLocalPort,fromNode); }
				String portMapKey="aha.localListeningPorts";
				if( !localAddr.equals("127.0.0.1") && !localAddr.equals("::1") && !localAddr.startsWith("192.168") && !localAddr.startsWith("10.") && !proto.equals("pipe")) //TODO "localhost" stuff should be configurable somewhere. for now all pipes are considered localhost, until we find a better way to determine int/ext. 
				{ 
					try
					{
						org.graphstream.graph.Edge e=m_graph.addEdge(node.getId()+"_external",node.getId(),"external");
						e.setAttribute("ui.class", "external");
						node.setAttribute("aha.hasExternalConnection", "yes");
					} catch (Exception e) {} //TODO: make a better check for a node having multiple externally accessible ports...
					
					portMapKey="aha.extListeningPorts"; //since this is external, change the key we read/write when we store this new info
					m_extListeningProcessMap.put(protoLocalPort,fromNode);
					// BEGIN RelativeScore CODE //
					java.util.List<String> parents=node.getStringList("parents");
					if (parents == null) { parents = new java.util.ArrayList<>(); }
					parents.add("external");
					node.putStringList("parents", parents);
					// END RelativeScore CODE //
				}
				else { m_intListeningProcessMap.put(protoLocalPort,fromNode); }
				java.util.TreeMap<String,String> listeningPorts=node.getStringMap(portMapKey);
				if (listeningPorts==null ) { listeningPorts=new java.util.TreeMap<>(); }
				listeningPorts.put(protoLocalPort, protoLocalPort);
				node.putStringMap(portMapKey, listeningPorts);
			}
			for (int i=0;i<tokens.size() && i<hdr.size();i++)
			{
				String processToken=tokens.get(i);
				if ( reverseHdr.get(i)==null ) { System.err.println("No hdr token found in lookup for i, continuing"); continue; }
				if ( processToken==null || processToken.isEmpty() ) { processToken="null"; }
				if ( nodeAttributeExclusions.contains(reverseHdr.get(i)) ) { continue; }
				if ( m_verbosity>4 ) { System.out.printf("   Setting process attribute=%s value=%s\n",reverseHdr.get(i),processToken); }
				node.setAttribute(reverseHdr.get(i),processToken);
			}
			if (proto!=null) { node.getStringMap("aha.graphlayer").put("proto."+proto, "proto."+proto); } //we want a deduplicated set of all the protocols this node is connected on
			if (proto!=null && !proto.equals("none")) //don't need to add a connection entry for nodes that are protocol==none
			{
				ConnectionEntry conEntry=new ConnectionEntry();
				conEntry.localAddr=tokens.get(hdr.get("localaddress"));
				conEntry.localPort=tokens.get(hdr.get("localport"));
				conEntry.remoteAddr=tokens.get(hdr.get("remoteaddress"));
				conEntry.remotePort=tokens.get(hdr.get("remoteport"));
				conEntry.remoteHostName=tokens.get(hdr.get("remotehostname")); 
				conEntry.state=tokens.get(hdr.get("state"));
				conEntry.protocol=tokens.get(hdr.get("protocol"));
				node.getConnectionEntryTable("allConnections").add(conEntry);
				if (m_verbosity>3) { System.err.println("     +"+fromNode+" "+conEntry.toString()); }
			}
			if (lineNumber<5 && hdr.get("addedon")!=null && tokens.get(hdr.get("addedon"))!=null) { m_miscMetrics.put("detectiontime", tokens.get(hdr.get("addedon"))); } //only try to set the first few read lines
			if (lineNumber<5 && hdr.get("detectiontime")!=null && tokens.get(hdr.get("detectiontime"))!=null) { m_miscMetrics.put("detectiontime", tokens.get(hdr.get("detectiontime"))); } //only try to set the first few read lines //back compat for old scans, remove someday
		}

		if (m_verbosity>4)
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
		for (java.util.ArrayList<String> tokens : inputLines)
		{
			{
				lineNumber++;
				String toNode="UnknownToNodeError", fromNode=tokens.get(hdr.get("processname"))+"_"+tokens.get(hdr.get("pid")), proto=tokens.get(hdr.get("protocol")), localPort=tokens.get(hdr.get("localport")), remotePort=tokens.get(hdr.get("remoteport"));
				String protoLocalPort=proto+"_"+localPort, protoRemotePort=proto+"_"+remotePort;
				String remoteAddr=tokens.get(hdr.get("remoteaddress")).trim(), localAddr=tokens.get(hdr.get("localaddress")), connectionState=tokens.get(hdr.get("state")), remoteHostname=tokens.get(hdr.get("remotehostname"));

				AHANode node = m_graph.getNode(fromNode);
				if(node == null)
				{
					System.out.println("WARNING: Second scan found new process: Name=|"+fromNode+"|, on line "+lineNumber+" ignoring."); 
					continue;
				}
				boolean duplicateEdge=false, timewait=false, exactSameEdgeAlreadyExists=false, isLocalOnly=false;
				AHANode tempNode=null;
				String connectionName=null,reverseConnectionName=null;
				if ( !connectionState.contains("listen") && !connectionState.equalsIgnoreCase("") ) //empty string is listening state for a bound udp socket on windows apparently
				{
					if (connectionState.toLowerCase().contains("wait") || connectionState.toLowerCase().contains("syn_") || connectionState.toLowerCase().contains("last_")) { timewait=true; }
					if ( m_knownAliasesForLocalComputer.get(remoteAddr)!=null ) 
					{ //if it's in that map, then this should be a connection to ourself
						isLocalOnly=true;
						if ( (toNode=m_allListeningProcessMap.get(protoRemotePort))!=null )
						{
							connectionName=(toNode+":"+protoLocalPort+"<->"+fromNode+":"+protoRemotePort);
							reverseConnectionName=(fromNode+":"+protoRemotePort+"<->"+toNode+":"+protoLocalPort);	
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
							if (!fromNode.startsWith("unknown") && m_verbosity>0) {System.out.println("Found a timewait we could correlate, but the name does not contain unknown. Would have changed from: "+fromNode+" to: "+m_allListeningProcessMap.get(protoLocalPort)+"?"); }
							else { fromNode=m_allListeningProcessMap.get(protoLocalPort); }
						}
						toNode="Ext_"+remoteAddr;
						connectionName=(toNode+":"+protoLocalPort+"<->"+fromNode+":"+protoRemotePort);
						reverseConnectionName=(fromNode+":"+protoRemotePort+"<->"+toNode+":"+protoLocalPort);	
						if (remoteHostname.equals("")) { remoteHostname=remoteAddr; } //cover the case that there is no FQDN
					}

					if (m_graph.getNode(toNode)==null ) //TODO: fix this, right now some pipes will try to put their remote ends on new ext nodes
					{ 
						if (proto.equals("pipe")) { System.err.println("Skipping line due to bug with pipe hadling. To be fixed at a later date."); continue; }
						if (toNode.equals("UnknownToNodeError")) { System.err.println("Unknown error creating node, bailing. FromNode="+fromNode); continue;}
						System.err.println("WARNING: toNode="+toNode+" DID NOT EXIST, CREATING."); 
						AHANode extNode=m_graph.addNode(toNode); 
						extNode.getStringMap("aha.graphlayer").put("aha.realextnode", "aha.realextnode");
						extNode.setAttribute("ui.class", "external");
						extNode.setAttribute("aha.externalNode","aha.externalNode");
						extNode.setAttribute("aha.realextnode","aha.realextnode");
					}
					if (connectionName!=null) { tempNode=m_graph.getNode(fromNode); } //some of the cases above wont actually result in a sane node setup, so only grab a node if we have connection name
					if (tempNode!=null)
					{
						for (org.graphstream.graph.Edge e : tempNode)
						{
							if (e.getOpposite(tempNode.graphNode).getId().equals(toNode)) { duplicateEdge=true; }
							if (e.getId().equals(connectionName) || e.getId().equals(reverseConnectionName) ) { exactSameEdgeAlreadyExists=true; System.out.println("Exact same edge already exists!"); }
						}
						if (!exactSameEdgeAlreadyExists)
						{
							org.graphstream.graph.Edge e=null;
							try { e=m_graph.addEdge(connectionName,fromNode,toNode); }
							catch (Exception ex) { ex.printStackTrace(); } 
							if (m_verbosity>4) { System.out.println("Adding edge from="+fromNode+" to="+toNode+" name="+connectionName); }
							if (e==null) { System.out.println("!!WARNING: Failed to add edge (null) from="+fromNode+" to="+toNode+" name="+connectionName+" forcing creation."); e=m_graph.addEdge("Fake+"+System.nanoTime(),fromNode,toNode);}
							if (m_allListeningProcessMap.get(protoLocalPort)!=null) { bumpIntRefCount(m_listeningPortConnectionCount,protoLocalPort,1); }
							if (e!=null && isLocalOnly)
							{
								e.setAttribute("layout.weight", 10); //try to make internal edges longer
								if (duplicateEdge) { e.setAttribute("layout.weight", 5); }
								if (timewait && !duplicateEdge) { e.setAttribute("ui.class", "tw"); }
								if (!timewait && duplicateEdge) { e.setAttribute("ui.class", "duplicate"); }
								if (timewait && duplicateEdge) { e.setAttribute("ui.class", "duplicate, tw"); }
								if (m_allListeningProcessMap.get(protoRemotePort)!=null) { bumpIntRefCount(m_listeningPortConnectionCount,protoRemotePort,1); }
							}
							else if(e!=null)
							{
								if ( m_graph.getNode(toNode)==null ) { System.out.println("tonode for actual node "+toNode+" is null!"); }
								m_graph.getNode(toNode).setAttribute("ui.class", "external");
								m_graph.getNode(toNode).setAttribute("hostname", remoteHostname);
								m_graph.getNode(toNode).setAttribute("IP", remoteAddr);
								e.setAttribute("layout.weight", 9); //try to make internal edges longer
								if (duplicateEdge) { e.setAttribute("layout.weight", 4); }
								if (!timewait && !duplicateEdge) { e.setAttribute("ui.class", "external"); }
								if (!timewait && duplicateEdge) { e.setAttribute("ui.class", "duplicate, xternal"); }
								if (timewait && !duplicateEdge) { e.setAttribute("ui.class", "external, tw"); } 
								if (timewait && duplicateEdge) { e.setAttribute("ui.class", "duplicate, external, tw"); }
							} // BEGIN RelativeScore CODE //
							AHANode toNode_node = m_graph.getNode(toNode);
							if ( toNode.startsWith("Ext_") || node.getId().startsWith("Ext_") ) //TODO, this should probably use ui.class rather than string matching?
							{
								java.util.List<String> parents=node.getStringList("parents");
								if (parents== null) { parents = new java.util.ArrayList<>(); }
								if (toNode.startsWith("Ext_"))
								{
									parents.add(toNode);
									node.putStringList("parents", parents);
								}
								else
								{
									parents.add(node.getId());
									toNode_node.putStringList("parents", parents);
								}
							}
							else
							{
								java.util.List<String> siblings=node.getStringList("sibling");
								if (siblings == null) { siblings=new java.util.ArrayList<>(); }
								siblings.add(toNode);
								node.putStringList("sibling", siblings);

								siblings=toNode_node.getStringList("sibling");
								if (siblings == null) { siblings = new java.util.ArrayList<>(); }
								siblings.add(node.getId());
								toNode_node.putStringList("sibling", siblings);
							} // END RelativeScore CODE //
						}
					}
				}
			}
		}
	}
	
	private void readCustomScorefile()
	{
		if (m_scoreFileName==null) { return; }
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
					for (AHANode node:m_graph)
					{
						try
						{
							String processPath=node.getAttribute("processpath");
							if (processPath!=null && processPath.equalsIgnoreCase(nodePathName))
							{
								if(directive.equals("node") && nodePathName!=null)
								{
									node.setAttribute(CUSTOMSTYLE,"yes");
									node.setAttribute(CUSTOMSTYLE+".score", score);
									node.setAttribute(CUSTOMSTYLE+".style", style);
									System.out.printf("CustomScoreOverlay read line: node=%s path=%s, setting score=%s color=%s\n", node.getId(),nodePathName,score, style);
								}
								else if (directive.equals("edge") && nodePathName!=null)
								{
									String toName=tokens[4];
									for (org.graphstream.graph.Edge e : node)
									{
										String toNodeProcessPath=m_graph.getNode(e.getOpposite(node.graphNode).getId()).getAttribute("processpath");
										if ( toNodeProcessPath.equalsIgnoreCase(toName) )
										{
											e.setAttribute(CUSTOMSTYLE,"yes");
											e.setAttribute(CUSTOMSTYLE+".score", score);
											e.setAttribute(CUSTOMSTYLE+".style", style);
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
	
	protected void useFQDNLabels(boolean useFQDN) 
	{
		if (useFQDN) 
		{  
			for (AHANode n : m_graph) 
			{ 
				String temp=n.getAttribute("ui.class");
				if (temp!=null && temp.equals("external"))
				{
					if (!n.getId().equals("external")) { n.setAttribute("ui.label", capitalizeFirstLetter("Ext_"+n.getAttribute("hostname"))); }
				}
			}
		} 
	}
	
	protected void hideOSProcs(boolean hide) 
	{
		String[] osProcs={"c:\\windows\\system32\\services.exe","c:\\windows\\system32\\svchost.exe","c:\\windows\\system32\\wininit.exe","c:\\windows\\system32\\lsass.exe","null","system",};
		for (String s : osProcs) { genericHideUnhideNodes( "processpath=="+s,hide ); }
	}
	protected void hideFalseExternalNode(boolean hide)  { genericHideUnhideNodes( "processpath==external",hide ); }
	
	
	private java.util.TreeMap<String,String> hiddenGraphLayers=new java.util.TreeMap<>();
	protected int genericHideUnhideNodes( String criteria, boolean hide )
	{
		boolean notInverseSearch=true;
		int hidden=0;
		String regexp="";
		if (criteria.contains("==")) { regexp="=="; }
		if (criteria.contains("!=")) { regexp="!="; notInverseSearch=false; }
		String[] args=criteria.trim().split(regexp);
		if (args.length < 2) { System.err.println("Hide: Unable to parse tokens:|"+criteria.trim()+"|"); return 0; }
		try
		{
			String attribute=args[0].toLowerCase();
			
			String seeking="";
			if (args.length >1 && args[1]!=null) { seeking=args[1].toLowerCase(); }//important to do every loop since seeking may be modified if it is inverted
			
			if (attribute.equals("aha.graphlayer"))
			{
				if (hide) { hiddenGraphLayers.put(seeking, seeking); }
				else { hiddenGraphLayers.remove(seeking); }
			}
			if (m_verbosity > 1) { System.err.println("layers '"+hiddenGraphLayers.toString()+"' should be hidden"); }
			
			for (AHANode node:m_graph) //System.out.println("attr='"+attribute+"' seeking='"+seeking+"'");
			{
				try
				{
					java.util.TreeMap<String, String> hideReasons=node.getStringMap("aha.hideReasons");
					String attrValue=node.getAttribute(attribute);
				
					if (attribute.equals("aha.graphlayer"))
					{
						int numberOfUnmatchedLayers=0;
						java.util.TreeMap<String,String> nodeLayers=node.getStringMap("aha.graphlayer");
						if (nodeLayers==null || nodeLayers.size()==0) { System.err.println("FIX: Only sadness detected for node='"+node.getId()+"'"); continue; }
						for (String key : nodeLayers.keySet())
						{
							if (hiddenGraphLayers.get(key)==null) { numberOfUnmatchedLayers++; }
						}
						if (numberOfUnmatchedLayers > 0) { hideReasons.remove("aha.graphlayer"); }
						else { hideReasons.put("aha.graphlayer","aha.graphlayer"); }
						//todo don't forget to get/modify hideReasons
					}
					else if (attrValue!=null && seeking!=null && notInverseSearch==attrValue.equals(seeking))
					{
						if (notInverseSearch==false) { seeking="!"+seeking; }
						if (hide) { hideReasons.put(seeking, seeking); }
						else { hideReasons.remove(seeking); }
					}
					else { hideReasons=null; }
					if (hideReasons!=null)
					{
						boolean nodeWillHide=!hideReasons.isEmpty();
						if (nodeWillHide)
						{
							if (node.getAttribute("ui.hide")==null) { hidden++; } //if it's not already hidden, count that we're going to hide it
							node.setAttribute("ui.hide","");
							if (m_verbosity>2) { System.out.println("Hide node="+node.getId()); }
						}
						else
						{
							node.removeAttribute("ui.hide");
							if (m_verbosity>2) { System.out.println("Unhide node="+node.getId()); }
						}
						for (org.graphstream.graph.Edge e : node)
						{
							if (nodeWillHide) { e.setAttribute( "ui.hide" ); }
							else { if (e.getOpposite(node.graphNode).getAttribute("ui.hide")==null ) { e.removeAttribute( "ui.hide" ); } }
						}
					}
				} catch(Exception e) { e.printStackTrace(); }
			} 
		}
		catch (Exception e) { e.printStackTrace(); }
		return hidden;
	}
	
	protected int genericEmphasizeNodes( String criteria, boolean emphasize ) 
	{
		int emphasized=0;
		try
		{
			boolean notInverseSearch=true;
			String regexp="";
			String[] args=criteria.trim().split(regexp);
			if (args.length < 2) { System.err.println("Emphasize: Unable to parse tokens:|"+criteria.trim()+"|"); return 0; }
			
			if (criteria.contains("==")) { regexp="=="; }
			if (criteria.contains("!=")) { regexp="!="; notInverseSearch=false;}
			
			String attribute=args[0].toLowerCase();
			for (AHANode node:m_graph)
			{
				String seeking=args[1].toLowerCase(); //important to do every loop since seeking may be modified if it is inverted
				String attrValue=node.getAttribute(attribute);
				if (attrValue!=null && seeking!=null && notInverseSearch==attrValue.equals(seeking))
				{
					if (notInverseSearch==false) { seeking="!"+seeking; } 
					java.util.TreeMap<String, String> emphasizeReasons=node.getStringMap("aha.emphasizeReasons");
					if (emphasizeReasons==null)
					{
						emphasizeReasons=new java.util.TreeMap<>();
						node.putStringMap("aha.emphasizeReasons", emphasizeReasons);
					}
					if (emphasize) { emphasizeReasons.put(seeking, seeking); }
					else { emphasizeReasons.remove(seeking); }
					
					boolean nodeWillEmphasize=!emphasizeReasons.isEmpty();
					String nodeClass=node.getAttribute("ui.class");
					if (nodeClass==null) { nodeClass=""; }
					if (nodeWillEmphasize)
					{
						if (!nodeClass.contains("emphasize, ")) { nodeClass="emphasize, "+nodeClass; emphasized++;}
						if (m_verbosity>0) { System.out.println("Emphasize node="+node.getId()); }
					}
					else
					{
						nodeClass=nodeClass.replaceAll("emphasize, ", ""); 
						if (m_verbosity>0) { System.out.println("unEmphasize node="+node.getId()); }
					}
					node.setAttribute("ui.class", nodeClass);
					
					for (org.graphstream.graph.Edge e : node)
					{
						String edgeClass=(String)e.getAttribute("ui.class");
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
	
	protected String handleSearch (String searchText)
	{
		if (searchText==null ) { return ""; }
		for (String s : m_lastSearchTokens) //undo what we did last time... maybe we can optimize and only undo what's changed since last time someday
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
			token=token.trim();
			m_lastSearchTokens.add(token);
			System.out.println("token=|"+token+"|");
			if (token.startsWith("~")) { hid+=genericHideUnhideNodes(token.substring(1), true); }
			else { emph+=genericEmphasizeNodes(token, true); }
		}
		System.out.println("");
		if ( hid==0 && emph==0 ) { return ""; } //this is needed to clear out the status when the user is no longer using it
		return " Status: Hidden="+hid+" Highlighted="+emph+" ";
	}
	
	public static String capitalizeFirstLetter(String s)
	{
		s = s.toLowerCase();
		return Character.toString(s.charAt(0)).toUpperCase()+s.substring(1);
	}
	
	private void computeUIInformation()
	{
		for (AHANode node : m_graph)
		{
			try
			{	// prepare the top part of the sidebar, general node info
				java.util.ArrayList<String> generalNodeInfo=new java.util.ArrayList<>();
				generalNodeInfo.add("Name: "+node.getAttribute("ui.label"));
				String uiclass=node.getAttribute("ui.class");
				if (uiclass!=null && uiclass.equalsIgnoreCase("external")) 
				{ 
					if (node.getAttribute("IP")!=null)
					{
						generalNodeInfo.add("IP: "+node.getAttribute("IP"));
						generalNodeInfo.add("Hostname: "+node.getAttribute("hostname"));
						generalNodeInfo.add("Description: External host.");
					}
					else if (node.getId().toLowerCase().equals("external"))
					{
						generalNodeInfo.add("Description: Virtual External node.");
						generalNodeInfo.add("Dummy node which connects to any node listening for outside connections.");
						generalNodeInfo.add("To Hide, use 'Hide Ext Node' menu item in 'view' menu.");
					}
				}
				else
				{
					generalNodeInfo.add("Description: A process.");
					generalNodeInfo.add("User: "+node.getAttribute("username"));
					generalNodeInfo.add("Path: "+node.getAttribute("processpath"));
					generalNodeInfo.add("Services: "+node.getAttribute("processservices"));
				}
				String[] aDolusAttributes= {"aDolusScore", "aDolusKnownMalware", "aDolusCVEs", "aDolusNumCVEs", "aDolusCVEScore", "aDolusWorstCVEScore", "aDolusDataSource"};
				for (String attrib : aDolusAttributes)
				{
					String value=node.getAttribute(attrib.toLowerCase());
					if (value!=null && !value.equals("") && !value.equals("null")) { generalNodeInfo.add(attrib+": "+value); }
				}
				String[][] infoData=new String[generalNodeInfo.size()][1];
				for (int i=0;i<generalNodeInfo.size();i++) { infoData[i][0]=generalNodeInfo.get(i); }
				node.putSidebarAttribute("aha.SidebarGeneralInfo", infoData);
			} catch (Exception e) { e.printStackTrace(); node.putSidebarAttribute("aha.SidebarGeneralInfo",(new String[][]{{"Error"}}) ); }
			// end updating data for general node info
			
			{ //update data for the 'internal ports' and 'external ports' sections of the info panel
				String insertionKey="aha.SidebarInternalPorts", sourceKey="aha.localListeningPorts";
				for (int times=0;times<2;times++)
				{
					try
					{ 
						java.util.TreeMap<String, String> portSourceData=node.getStringMap(sourceKey);
						Object[][] portResultData={{"None",""}};
						if (portSourceData!=null && portSourceData.size()>0)
						{
							portResultData=new Object[portSourceData.size()][2];
							int i=0;
							for (java.util.Map.Entry<String, String> entry : portSourceData.entrySet())
							{
								String[] temp=entry.getKey().split("_");
								portResultData[i][0]=strAsInt(temp[1]);
								portResultData[i][1]=temp[0].toUpperCase();
								i++;
							}
						}
						node.putSidebarAttribute(insertionKey, portResultData);
					} catch (Exception e) { e.printStackTrace(); node.putSidebarAttribute( insertionKey, new String[][]{{"Error"}} ); }
					insertionKey="aha.SidebarExternalPorts";
					sourceKey="aha.extListeningPorts";
				}
			} // end updating data for internal/external ports
		}
	}
	
	public void run()
	{
		long time=System.currentTimeMillis(), time2=0;
		try
		{
			java.io.File testInputFile=getFileAtPath(m_inputFileName);
			if ( m_inputFileName==null || m_inputFileName.equals("") || testInputFile==null || !testInputFile.exists()) { System.err.println("No input file specified, bailing."); return; }
			
			if (m_graph==null) { System.err.println("SOMEHOW GRPAH IS NULL!!!"); }
			
			readInputFile();
			readScoreTable(null);
			readCustomScorefile();
			useFQDNLabels(false);
			exploreAndScore(m_graph);
			computeUIInformation();
			
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
			synchronized(synch_report) { synch_report=new TableDataHolder(); }
			writeReport(generateReport(),"AHA-GUI-Report.csv");
			
			time2=System.currentTimeMillis();
			m_controller.moveExternalNodes(this);
		} catch(Exception e) { e.printStackTrace(); } 
		System.err.println("AHAModel: input files read, task complete: elapsed time="+(time2-time)+"ms");
	}
	
	protected static Object strAsInt(String s) //try to box in an integer (important for making sorters in tables work) but fall back to just passing through the Object
	{ 
		try { if (s!=null && !s.equals("") ) { return Integer.valueOf(s); } }
		catch (NumberFormatException nfe) {} //we really don't care about this, we'll just return the original object
		catch (Exception e) { System.out.println("s="+s);e.printStackTrace(); } //something else weird happened, let's print about it
		return s;
	}
	private static Object strAsDouble(String s) //try to box in an integer (important for making sorters in tables work) but fall back to just passing through the Object
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
				String[][] columnHeaders= {{"Scan Information", "Value"},{"Process", "PID", "User","Connections","ExtPorts","Signed","ASLR","DEP","CFG","HiVA", "Score", "WPScore","RelativeScore",},{}};
				Object[][][] tableData=new Object[NUM_SCORE_TABLES][][];
				{ //general info
					Object[][] data=new Object[9][2];
					int i=0;
					data[i][0]="Local Addresses of Scanned Machine";
					data[i++][1]=m_knownAliasesForLocalComputer.keySet().toString();
					data[i][0]="Local Time of Host Scan";
					data[i++][1]=m_miscMetrics.get("detectiontime");
					data[i][0]="Scan File Name";
					data[i++][1]=m_inputFileName;
					{
						int numExt=0, worstScore=100;
						double denominatorAccumulator=0.0d;
						String worstScoreName="";
						for (AHANode n : m_graph)
						{
							if (n.getAttribute("username")!=null && n.getAttribute("aha.hasExternalConnection")!=null && n.getAttribute("aslr")!=null && !n.getAttribute("aslr").equals("") && !n.getAttribute("aslr").equals("scanerror")) 
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
						for (AHANode n : m_graph)
						{
							if (n.getAttribute("username")!=null && n.getAttribute("aha.hasExternalConnection")==null && n.getAttribute("aslr")!=null && !n.getAttribute("aslr").equals("") && !n.getAttribute("aslr").equals("scanerror")) 
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
					tableData[tableNumber]=new Object[m_graph.graph.getNodeCount()][];
					int i=0;
					for (AHANode n : m_graph)
					{
						int j=0; //tired of reordering numbers after moving columns around
						Object[] data=new Object[columnHeaders[tableNumber].length]; //if we run out of space we forgot to make a column header anyway
						String name=n.getAttribute("processname");
						if (name==null) { name=n.getAttribute("ui.label"); }
						data[j++]=name;
						data[j++]=strAsInt(n.getAttribute("pid"));
						data[j++]=n.getAttribute("username");
						data[j++]=Integer.valueOf((int)n.graphNode.edges().count()); //can't use wrapInt here because  //TODO: de-duplicate connection set?
						String extPortString="";
						java.util.TreeMap<String,String> extPorts=n.getStringMap("aha.extListeningPorts");
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
						data[j++]=strAsDouble(n.getAttribute(scrMethdAttr(ScoreMethod.RelativeScoreBETA))); //data[j++]=n.getAttribute("parents"); //data[j++]=n.getAttribute("sibling");
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
	
	protected static java.io.File getFileAtPath(String fileName)
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
	
	protected static boolean readCSVFileIntoArrayList(String inputFileName, java.util.TreeMap<String, String> metadata, java.util.TreeMap<String,Integer> headerTokens, java.util.ArrayList<java.util.ArrayList<String>> inputLines)
	{
		if (inputFileName.equals("")) { inputFileName="BinaryAnalysis.csv"; } //try the default filename here if all else fails
		try ( java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(new java.io.FileInputStream(inputFileName),"UTF8")); )
		{
			System.out.printf("Reading inputFile=%s\n", inputFileName);
			java.io.File input=new java.io.File(inputFileName);
			int minTokens=5;
			if (!input.exists()) { System.out.println("Failed to open input file, bailing."); return false; }
			String originalHeaderLine=br.readLine();
			metadata.put("OriginalHeaderLine", originalHeaderLine);
			String line = "", header[]=fixCSVLine(originalHeaderLine);
			for (int i=0;i<header.length;i++)
			{
				String headerToken=header[i];
				headerTokens.put(headerToken, Integer.valueOf(i));
			}
			String requiredTokens=metadata.get("RequiredHeaderTokens"); //leave the others between process name and the sums as this might point to a malformed file
			if (requiredTokens!=null)
			{
				String[] required=requiredTokens.split(",");
				if (required.length>2) { minTokens=required.length; }
				for (String s:required) { if (s.length() > 2 && headerTokens.get(s)==null) { System.out.println("Input file: required column header field=\""+s+"\" was not detected, this will not go well."); } }
			}

			int lineNumber=1;
			while ((line = br.readLine()) != null) //this is the first loop, in this loop we're loading all the vertexes and their meta data, so we can then later connect the vertices
			{
				lineNumber++;
				try
				{
					String[] tokens = fixCSVLine(line);
					if (tokens.length<minTokens)
					{
						if ( line.trim().length() > 0 ) { System.err.println("ReadCSVFile: Skipping line #"+lineNumber+" because it is malformed."); } // more than likely this means something converted the line endings and we've and we've got crlf\nclrfloletc
						continue;
					}
					java.util.ArrayList<String> saveTokens=new java.util.ArrayList<>();
					saveTokens.addAll(java.util.Arrays.asList(tokens));
					inputLines.add(saveTokens); //save these for when we write out the new results file
				}
				catch (Exception e) { System.out.print("ReadCSVLine: Error:: input line "+lineNumber+":"); e.printStackTrace(); }
			}
			return true;
		} 
		catch (Exception e) { System.out.print("ReadCSVLine: Error:"); e.printStackTrace(); } 
		return false;
	}
	
	public AHAModel(AHAController controller, String inputFileName, String scoreFileName, int verbosity)
	{
		m_inputFileName=inputFileName;
		m_scoreFileName=scoreFileName;
		m_verbosity=verbosity;
		m_controller=controller;
	}
}
