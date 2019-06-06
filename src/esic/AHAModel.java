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
	
	protected boolean m_debug=false, m_verbose=false, m_useCustomOverlayScoreFile=false;
	protected String m_inputFileName="", m_scoreFileName="scorefile.csv";
	protected Graph m_graph=null;
	protected AHAGUI m_gui=null;
	protected java.util.TreeMap<String,Integer> platformSpecificMetricsTableScores=new java.util.TreeMap<>(), m_listeningPortConnectionCount=new java.util.TreeMap<>();
	protected java.util.TreeMap<String,String> m_allListeningProcessMap=new java.util.TreeMap<>(), m_intListeningProcessMap=new java.util.TreeMap<>(), m_extListeningProcessMap=new java.util.TreeMap<>();
	protected java.util.TreeMap<String,String> m_knownAliasesForLocalComputer=new java.util.TreeMap<>(), m_miscMetrics=new java.util.TreeMap<>();
	
	protected static enum ScoreMethod //method to number the enums is a bit ridiculous, but oh well.
	{
		Normal(0),WorstCommonProcBETA(1),RelativeScoreBETA(2);
		private int value;
    private static java.util.HashMap<Integer,ScoreMethod> map = new java.util.HashMap<>();

    private ScoreMethod(int value) {
        this.value = value;
    }

    static {
        for (ScoreMethod pageType : ScoreMethod.values()) {
            map.put(pageType.value, pageType);
        }
    }

    public static ScoreMethod valueOf(int scoreMethod) {
        return (ScoreMethod) map.get(scoreMethod);
    }
    
    public static ScoreMethod getValue(String scoreMethod) {
     try { return (ScoreMethod) map.get(Integer.parseInt(scoreMethod)); }
     catch (Exception e) { e.printStackTrace(); return Normal; }
  }

    public int getValue() {
        return value;
    }
	}
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
					for (String s : criteria) { criteriaVec.add(s.trim().trim().trim()); }
					System.out.println("MetricsTable read criterion: if "+tokens[1]+"="+criteriaVec.toString()+" score="+scoreDelta+" Explanation='"+humanReadibleExplanation+"'"); //TODO fix
					m_scoreTable.add(new ScoreItem(tokens[0],tokens[1],criteriaVec,scoreDelta, humanReadibleExplanation)); //TODO this should probably get cleaned up, as comments and/or anything after the first main 3 fields will consume memory even though we never use them.
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
		java.util.TreeMap<String, Integer> lowestScoreForUserName=new java.util.TreeMap<>();
		for (Node node : graph) //Stage 1 of scoring, either the entirety of a scoring algorithm, such as "Normal", or the first pass for multi stage algs
		{
			try
			{
				int score=generateNormalNodeScore(node);
				node.setAttribute(scrMethdAttr(ScoreMethod.Normal), Integer.toString(score)); //if we didn't have a custom score from another file, use our computed score
				
				//Begin WorstUserProc stage1 scoring
				String nodeUserName=(String)node.getAttribute("username");
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
				String nodeUserName=(String)node.getAttribute("username");
				if (nodeUserName!=null)
				{
					Integer lowScore=lowestScoreForUserName.get(node.getAttribute("username"));
					if (lowScore==null) { System.err.println("no low score found, this should not happen"); continue; }
					node.setAttribute(scrMethdAttr(ScoreMethod.WorstCommonProcBETA), lowScore.toString());
					node.setAttribute(scrMethdAttr(ScoreMethod.WorstCommonProcBETA)+"Reason", "WPScore="+lowScore);
					node.setAttribute(scrMethdAttr(ScoreMethod.WorstCommonProcBETA)+"ExtendedReason", "WPScore="+lowScore);
				} 
				else 
				{ 
					node.setAttribute(scrMethdAttr(ScoreMethod.WorstCommonProcBETA), "0");
					node.setAttribute(scrMethdAttr(ScoreMethod.WorstCommonProcBETA)+"Reason", "WPScore=0");
					node.setAttribute(scrMethdAttr(ScoreMethod.WorstCommonProcBETA)+"ExtendedReason", "WPScore=0");
				} 
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
				java.util.List<String> nodeParents= castObjectAsStringList(node.getAttribute("parents"));
				java.util.List<String> nodeParentsDistinct = nodeParents.stream().distinct().collect(java.util.stream.Collectors.toList());
				node.setAttribute("parents", nodeParentsDistinct);
			}
			if (node.getAttribute("sibling") != null)
			{
				java.util.List<String> nodeSiblings= castObjectAsStringList(node.getAttribute("sibling"));
				java.util.List<String> nodeSiblingsDistinct = nodeSiblings.stream().distinct().collect(java.util.stream.Collectors.toList());
				node.setAttribute("sibling", nodeSiblingsDistinct);
			}
		}
		double maxscore=100, minscore=0;
		double maxNorm=maxscore+5, minNorm=minscore-5;     
		for (Node node:graph)
		{   
			if (node.getAttribute(scrMethdAttr(ScoreMethod.Normal))!=null)
			{
				double nodeScore   = Double.valueOf((String)node.getAttribute(scrMethdAttr(ScoreMethod.Normal)));
				double attacksurface =  1 - ((nodeScore-minNorm)/(maxNorm-minNorm));
				node.setAttribute(NormalizedScore, ((Double)attacksurface).toString());
				node.setAttribute(RAWRelativeScore, ((Double)0.0).toString());
				node.setAttribute(ForSibling, ((Double)0.0).toString());
			}
			node.setAttribute("finishScoring",((int)0));
			node.setAttribute("readyforsibling",((int)0));
		}
		int allDone=0;
		while (allDone == 0)
		{
			for (Node node:graph)
			{
				if ((node.getAttribute("parents") == null) && (node.getAttribute("sibling") == null))
				{
					node.setAttribute("finishScoring",((int)1));
					node.setAttribute("readyforsibling",((int)1));
					double normalized = Double.valueOf((String)node.getAttribute(NormalizedScore));
					node.setAttribute(RAWRelativeScore, ((Double)normalized).toString());
					node.setAttribute(ForSibling, ((Double)0.0).toString());
				}
				if ((node.getAttribute("parents") == null) && (node.getAttribute("sibling") != null))
				{
					if ((int)node.getAttribute("readyforsibling") == 0)
					{
						double normalized = Double.valueOf((String)node.getAttribute(NormalizedScore));
						node.setAttribute(RAWRelativeScore, ((Double)normalized).toString());
						node.setAttribute("readyforsibling",((int)1));
					}
					if ((int)node.getAttribute("finishScoring") == 0)
					{
						java.util.List<String> nodeSiblings=castObjectAsStringList(node.getAttribute("sibling"));
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
								double nScore= Double.valueOf((String)node.getAttribute(NormalizedScore));
								double pScore = Double.valueOf((String)tempSibling.getAttribute(ForSibling));
								sum = sum + (nScore * pScore);
							} 
							node.setAttribute(RAWRelativeScore, ((Double)sum).toString());
							node.setAttribute("finishScoring",((int)1));
						}
					}  
				}
				if ((node.getAttribute("parents") != null) && (node.getAttribute("sibling") == null))
				{
					if ((int)node.getAttribute("readyforsibling") == 0) { node.setAttribute("readyforsibling",((int)1)); }
					if ((int)node.getAttribute("finishScoring") == 0)
					{
						java.util.List<String> nodeparents=castObjectAsStringList(node.getAttribute("parents"));
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
							double sum=0, nScore=Double.valueOf((String)node.getAttribute(NormalizedScore));
							for (int i = 0; i < nodeparents.size() ; i++) 
							{ 
								Node tempParent = graph.getNode(nodeparents.get(i)) ;
								double pScore = Double.valueOf((String)tempParent.getAttribute(RAWRelativeScore));
								sum = sum + (nScore * pScore);
							} 
							node.setAttribute(RAWRelativeScore, ((Double)(sum)).toString());
							node.setAttribute("finishScoring",((int)1));
						}
					}  
				}
				if ((node.getAttribute("parents") != null) && (node.getAttribute("sibling") != null))
				{
					if ((int)node.getAttribute("readyforsibling") == 0)
					{
						java.util.List<String> nodeparents=castObjectAsStringList(node.getAttribute("parents"));
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
							double nScore= Double.valueOf((String)node.getAttribute(NormalizedScore));
							for (int i = 0; i < nodeparents.size() ; i++) 
							{ 
								Node tempParent = graph.getNode(nodeparents.get(i)) ;
								double pScore = Double.valueOf((String)tempParent.getAttribute(RAWRelativeScore));
								sum = sum + (nScore * pScore);
							} 
							node.setAttribute(ForSibling, ((Double)(sum)).toString());
							node.setAttribute("readyforsibling",((int)1));
						}
					}
					if (((int)node.getAttribute("finishScoring") == 0) && ((int) node.getAttribute("readyforsibling") == 1))
					{
						java.util.List<String> nodeSiblings=castObjectAsStringList(node.getAttribute("sibling"));
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
							double nScore= Double.valueOf((String)node.getAttribute(NormalizedScore));
							double SScore= Double.valueOf((String)node.getAttribute(ForSibling));
							for (int i = 0; i < nodeSiblings.size() ; i++) 
							{ 
								Node tempSibling = graph.getNode(nodeSiblings.get(i)) ;
								double pScore = Double.valueOf((String)tempSibling.getAttribute(ForSibling));
								sum = sum + (nScore * pScore);
							} 
							node.setAttribute(RAWRelativeScore, ((Double)(sum+SScore)).toString());
							node.setAttribute("finishScoring",((int)1));
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
		double minRelative = Double.valueOf((String)graph.getNode(1).getAttribute(RAWRelativeScore)); //TODO: should both of these lines be the same?
		double maxRelative = Double.valueOf((String)graph.getNode(1).getAttribute(RAWRelativeScore));
		for (Node node:graph)
		{
			double relativeScore = Double.valueOf((String)node.getAttribute(RAWRelativeScore));
			if (relativeScore > maxRelative) { maxRelative = relativeScore ; }
			if (relativeScore < minRelative) { minRelative = relativeScore ; }
		}

		int newRangeMin = 0;
		int newRangeMax = 100;
		for (Node node:graph)
		{
			double relativeScore = Double.valueOf((String)node.getAttribute(RAWRelativeScore));
			double newRelativeScore= newRangeMin +((newRangeMax-newRangeMin)/(maxRelative-minRelative))*(relativeScore - minRelative);
			double newReversedRelativeScore= newRangeMax - newRelativeScore + newRangeMin ;
			node.setAttribute(scrMethdAttr(ScoreMethod.RelativeScoreBETA), Long.toString(Math.round(newReversedRelativeScore))); //).toString()
			node.setAttribute(scrMethdAttr(ScoreMethod.RelativeScoreBETA)+"Reason", "RelativeScore="+Long.toString(Math.round(newReversedRelativeScore))+"");
			node.setAttribute(scrMethdAttr(ScoreMethod.RelativeScoreBETA)+"ExtendedReason", "RelativeScore="+Long.toString(Math.round(newReversedRelativeScore))+"");
			//node.setAttribute(ReversedRangedRelativeScore, ((Double)newReversedRelativeScore).toString());
		}
	}
	
	protected int generateNormalNodeScore(Node node)
	{
		int score=0;
		String scoreReason="", extendedReason="";
		if (m_verbose) { System.out.println("Process: "+node.getId()+" examining metrics:"); }
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
								{
									//System.out.println("matched criteria for attribute="+attribute+" greater than criteria="+criterion);
									score=score+si.scoreDelta;
									numberOfTimesTrue++;
								}
							} catch (Exception e) { e.printStackTrace(); }
									
							
						}
					}
					if (m_verbose) { System.out.println("    "+si.criteria+": input='"+attribute+"' looking for="+si.criteriaValues+" matched "+numberOfTimesTrue+" times"); }
					//if (numberOfTimesTrue>0) { scoreReason+=", "+si.criteria+"="+(si.scoreDelta*numberOfTimesTrue); }
					if (numberOfTimesTrue>0) { scoreReason+=", "+si.humanReadableReason+"="+(si.scoreDelta*numberOfTimesTrue); } 
				}
				String xtra="";
				if (numberOfTimesTrue>1) { xtra=" x"+numberOfTimesTrue; }
				//extendedReason+=", "+si.criteria+"."+si.operation+si.criteriaValues+":"+si.scoreDelta+"="+capitalizeFirstLetter(Boolean.toString(numberOfTimesTrue>0))+xtra;
				extendedReason+=", "+si.humanReadableReason+"."+si.operation+si.criteriaValues+":"+si.scoreDelta+"="+capitalizeFirstLetter(Boolean.toString(numberOfTimesTrue>0))+xtra;
			}
			catch (Exception e) { System.out.println(e.getMessage()); }
		}
		if (m_verbose) { System.out.println("    Score: " + score); }
		if (score < 0) { score=0; } //System.out.println("Minimum final node score is 0. Setting to 0."); 
		node.setAttribute(scrMethdAttr(ScoreMethod.Normal)+"Reason", "FinalScore="+score+scoreReason);
		node.setAttribute(scrMethdAttr(ScoreMethod.Normal)+"ExtendedReason", "FinalScore="+score+extendedReason);
		return score;
	}

	public void swapNodeStyles(ScoreMethod m, long startTime)
	{
		for (Node node : m_graph)
		{
			try
			{
				String currentClass=(String)node.getAttribute("ui.class"), customStyle=(String)node.getAttribute(CUSTOMSTYLE);
				String sScore=(String)node.getAttribute(scrMethdAttr(m)), sScoreReason=(String)node.getAttribute(scrMethdAttr(m)+"Reason"), sScoreExtendedReason=(String)node.getAttribute(scrMethdAttr(m)+"ExtendedReason"); 
				Integer intScore=null;
				try { intScore=Integer.parseInt(sScore); }
				catch (Exception e) { System.err.printf("Failed to parse score for node=%s failed to parse='%s'\n",node.getId(), sScore); } 
				if (currentClass==null || !currentClass.equalsIgnoreCase("external") || intScore!=null)
				{
					if (currentClass!=null && currentClass.equalsIgnoreCase("external"))
					{ 
						node.setAttribute("ui.score", "0");
						node.setAttribute("ui.scoreReason", "External Node");
						node.setAttribute("ui.scoreExtendedReason", "External Node");
					}
					else if (m_useCustomOverlayScoreFile==true && customStyle!=null && customStyle.equalsIgnoreCase("yes"))
					{
						String score=(String)node.getAttribute(CUSTOMSTYLE+".score");
						String style=(String)node.getAttribute(CUSTOMSTYLE+".style");
						node.removeAttribute("ui.class");
						node.setAttribute("ui.style", style);
						node.setAttribute("ui.score", score);
						node.setAttribute("ui.scoreReason", "Custom Scorefile Overlay");
						node.setAttribute("ui.scoreExtendedReason", "Custom Scorefile Overlay");
					}
					else if (intScore!=null)
					{ 
						int score=intScore.intValue();
						node.setAttribute("ui.score", score);
						if (sScoreReason!=null) { node.setAttribute("ui.scoreReason", sScoreReason); } //TODO: since scoreReason only really exists for 'normal' this means that 'normal' reason persists in other scoring modes. For modes that do not base their reasoning on 'normal' this is probably incorrect.
						if (sScoreExtendedReason!=null) { node.setAttribute("ui.scoreExtendedReason", sScoreExtendedReason); }
						String uiClass="severeVuln";
						if (score > (0.25*maxScore)) { uiClass="highVuln"; }
						if (score > (0.50*maxScore)) { uiClass="medVuln"; }
						if (score > (0.75*maxScore)) { uiClass="lowVuln"; }
						node.setAttribute("ui.class", uiClass); //apply the class
						if (m_verbose) { System.out.println(node.getId()+" Applying Score: "+score+"   Vulnerability Score given: "+uiClass); }
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
	
	
	@SuppressWarnings("unchecked")
	public java.util.List<String> castObjectAsStringList(Object o)
	{
		try
		{
			if (o instanceof java.util.List)
			{
				java.util.List<?> temp=(java.util.List<?>)o;
				if (temp.get(0) instanceof String)
				{
					return (java.util.List<String>) temp;
				}
			}
		} catch (Exception e) { System.err.println("Failed to cast to List<String>"); e.printStackTrace(); }
		return null;
	}
	
	
	protected void readInputFile()
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

		int lineNumber=0;
		for (java.util.ArrayList<String> tokens : inputLines)
		{
			lineNumber++;
			//fixups for well known file issues (will persist for all of readInputFile since we modify the input tokens)
			if (tokens.get(hdr.get("protocol")).contains("6")) { tokens.set(hdr.get("protocol"),tokens.get(hdr.get("protocol")).replaceAll("6", "")); } //if we have tcp6/udp6, remove the '6'
			if (tokens.get(hdr.get("protocol")).equals("udp")) { tokens.set(hdr.get("state"),"listening"); } //fix up in the case that a line is protocol==UDP and state==""
			//if (tokens.get(hdr.get("protocol")).equals("pipe")) { tokens.set(hdr.get("state"),"listening"); } //TEST treating every pipe as a listener...
			if (hdr.get("remotehostname")==null) { tokens.set(hdr.get("remotehostname"),""); } //some versions of the linux scanner forgot to populate this column
			
			String fromNode=tokens.get(hdr.get("processname"))+"_"+tokens.get(hdr.get("pid")), protoLocalPort=tokens.get(hdr.get("protocol"))+"_"+tokens.get(hdr.get("localport"));
			String connectionState=tokens.get(hdr.get("state")), localAddr=tokens.get(hdr.get("localaddress")).trim();

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
					e.setAttribute("ui.class", "external");
					node.setAttribute("ui.hasExternalConnection", "yes");
					portMapKey="ui.extListeningPorts"; //since this is external, change the key we read/write when we store this new info
					m_extListeningProcessMap.put(protoLocalPort,fromNode);
					// BEGIN RelativeScore CODE //
					java.util.List<String> parents=castObjectAsStringList(node.getAttribute("parents"));
					if (parents == null) { parents = new java.util.ArrayList<>(); }
					parents.add("external");
					node.setAttribute("parents", parents);
					// END RelativeScore CODE //
				}
				else { m_intListeningProcessMap.put(protoLocalPort,fromNode); }
				java.util.TreeMap<String,String> listeningPorts=getTreeMapFromObj(node.getAttribute(portMapKey));
				if (listeningPorts==null ) { listeningPorts=new java.util.TreeMap<>(); }
				listeningPorts.put(protoLocalPort, protoLocalPort);
				node.setAttribute(portMapKey, listeningPorts);
			}
			for (int i=0;i<tokens.size() && i<hdr.size();i++)
			{
				String processToken=tokens.get(i);
				if (  processToken==null || processToken.isEmpty() ) { processToken="null"; }
				if (m_debug) { System.out.printf("   Setting attribute %s for process %s\n",reverseHdr.get(i),tokens.get(i)); }
				node.setAttribute(reverseHdr.get(i),processToken);
			}
			if (lineNumber<5 && hdr.get("addedon")!=null && tokens.get(hdr.get("addedon"))!=null) { m_miscMetrics.put("detectiontime", tokens.get(hdr.get("addedon"))); } //only try to set the first few read lines
			if (lineNumber<5 && hdr.get("detectiontime")!=null && tokens.get(hdr.get("detectiontime"))!=null) { m_miscMetrics.put("detectiontime", tokens.get(hdr.get("detectiontime"))); } //only try to set the first few read lines //back compat for old scans, remove someday
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
		for (java.util.ArrayList<String> tokens : inputLines)
		{
			{
				lineNumber++;
				//if (tokens.get(hdr.get("protocol")).equals("pipe")) { tokens.set(hdr.get("state"),"established"); } //TEST treating every pipe as a listener...
				String toNode="UnknownToNodeError", fromNode=tokens.get(hdr.get("processname"))+"_"+tokens.get(hdr.get("pid")), proto=tokens.get(hdr.get("protocol")), localPort=tokens.get(hdr.get("localport")), remotePort=tokens.get(hdr.get("remoteport"));
				String protoLocalPort=proto+"_"+localPort, protoRemotePort=proto+"_"+remotePort;
				String remoteAddr=tokens.get(hdr.get("remoteaddress")).trim(), localAddr=tokens.get(hdr.get("localaddress")), connectionState=tokens.get(hdr.get("state")), remoteHostname=tokens.get(hdr.get("remotehostname"));

				Node node = m_graph.getNode(fromNode);
				if(node == null)
				{
					System.out.println("WARNING: Second scan found new process: Name=|"+fromNode+"|, on line "+lineNumber+" ignoring."); 
					continue;
				}
				boolean duplicateEdge=false, timewait=false, exactSameEdgeAlreadyExists=false, isLocalOnly=false;
				Node tempNode=null;
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
							if (!fromNode.startsWith("unknown") && m_verbose) {System.out.println("Found a timewait we could correlate, but the name does not contain unknown. Would have changed from: "+fromNode+" to: "+m_allListeningProcessMap.get(protoLocalPort)+"?"); }
							else { fromNode=m_allListeningProcessMap.get(protoLocalPort); }
						}
						toNode="Ext_"+remoteAddr;
						connectionName=(toNode+":"+protoLocalPort+"<->"+fromNode+":"+protoRemotePort);
						reverseConnectionName=(fromNode+":"+protoRemotePort+"<->"+toNode+":"+protoLocalPort);	
						if (remoteHostname.equals("")) { remoteHostname=remoteAddr; } //cover the case that there is no FQDN
					}

					if (connectionName!=null) { tempNode=m_graph.getNode(fromNode); } //some of the cases above wont actually result in a sane node setup, so only grab a node if we have connection name
					if (tempNode!=null)
					{
						java.util.Iterator<Edge> it=tempNode.iterator(); //TODO rewrite in gs2.0 parlance
						while (it.hasNext())
						{
							Edge e=it.next();
							if (e.getOpposite(tempNode).getId().equals(toNode)) { duplicateEdge=true; }
							if (e.getId().equals(connectionName) || e.getId().equals(reverseConnectionName) ) { exactSameEdgeAlreadyExists=true; System.out.println("Exact same edge already exists!"); }
						}
						if (!exactSameEdgeAlreadyExists)
						{
							Edge e=m_graph.addEdge(connectionName,fromNode,toNode);
							if (m_debug) { System.out.println("Adding edge from="+fromNode+" to="+toNode+" name="+connectionName); }
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
								m_graph.getNode(toNode).setAttribute("ui.class", "external");
								m_graph.getNode(toNode).setAttribute("hostname", remoteHostname);
								m_graph.getNode(toNode).setAttribute("IP", remoteAddr);
								e.setAttribute("layout.weight", 9); //try to make internal edges longer
								if (duplicateEdge) { e.setAttribute("layout.weight", 4); }
								if (!timewait && !duplicateEdge) { e.setAttribute("ui.class", "external"); }
								if (!timewait && duplicateEdge) { e.setAttribute("ui.class", "duplicate, xternal"); }
								if (timewait && !duplicateEdge) { e.setAttribute("ui.class", "external, tw"); } 
								if (timewait && duplicateEdge) { e.setAttribute("ui.class", "duplicate, external, tw"); }
							}
							// BEGIN RelativeScore CODE //
							Node toNode_node = m_graph.getNode(toNode);
							if ( toNode.startsWith("Ext_") || node.toString().startsWith("Ext_") )
							{
								java.util.List<String> parents=castObjectAsStringList(node.getAttribute("parents"));
								if (parents== null) { parents = new java.util.ArrayList<>(); }
								if (toNode.startsWith("Ext_"))
								{
									parents.add(toNode);
									node.setAttribute("parents", parents);
								}
								else
								{
									parents.add(node.toString());
									toNode_node.setAttribute("parents", parents);
								}
							}
							else
							{
								java.util.List<String> siblings=castObjectAsStringList(node.getAttribute("sibling"));
								if (siblings == null) { siblings=new java.util.ArrayList<>(); }
								siblings.add(toNode);
								node.setAttribute("sibling", siblings);

								siblings=castObjectAsStringList(toNode_node.getAttribute("sibling"));
								if (siblings == null) { siblings = new java.util.ArrayList<>(); }
								siblings.add(node.toString());
								toNode_node.setAttribute("sibling", siblings);
							} // END RelativeScore CODE //
						}
					}
				}
			}
		}
	}
	
	public static String getCommaSepKeysFromStringMap(Object o)
	{
		java.util.TreeMap<?, ?> map=(java.util.TreeMap<?, ?>) o;
		StringBuilder sb=new StringBuilder("");
		try
		{
			if (map!=null && !map.isEmpty())
			{
				java.util.Iterator<?> it=map.keySet().iterator();
				while (it.hasNext())
				{
					sb.append((String)it.next());
					if (it.hasNext()) { sb.append(", "); }
				}
				return sb.toString();
			}
		} catch (Exception e) { System.err.println("Error in getCommanSepKeysFromStringMap returning 'None'."); e.printStackTrace(); }
		return "None.";
	}
	
	
	@SuppressWarnings("unchecked")
	public static java.util.TreeMap<String,String> getTreeMapFromObj(Object o)
	{
		try
		{
			return (java.util.TreeMap<String, String>) o;
		} catch (Exception e) {}
		return null;
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
							String processPath=(String)node.getAttribute("processpath");
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
									java.util.Iterator<Edge> it=node.iterator(); //TODO rewrite in gs2.0 parlance
									while (it.hasNext())
									{
										Edge e=it.next();
										Node toNode=e.getOpposite(node);
										String toNodeProcessPath=(String)toNode.getAttribute("processpath");
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
	
	protected void useFQDNLabels(Graph g, boolean useFQDN) 
	{
		for (Node n : m_graph) { n.setAttribute("ui.label", capitalizeFirstLetter(n.getId())); } //add labels
		if (useFQDN) 
		{  
			for (Node n : m_graph) 
			{ 
				String temp=(String)n.getAttribute("ui.class");
				if (temp!=null && temp.equals("external"))
				{
					if (!n.getId().equals("external")) { n.setAttribute("ui.label", capitalizeFirstLetter("Ext_"+n.getAttribute("hostname"))); }
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
	protected void hideFalseExternalNode(Graph g, boolean hide)  { genericHideUnhideNodes( "processpath==external",hide ); }
	
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
			String seeking="";
			if (args.length >1 && args[1]!=null) { seeking=args[1].toLowerCase(); }//important to do every loop since seeking may be modified if it is inverted
			for (Node node:m_graph) //System.out.println("attr='"+attribute+"' seeking='"+seeking+"'");
			{
				String attrValue=(String)node.getAttribute(attribute);
				if (attrValue!=null && seeking!=null && notInverseSearch==attrValue.equals(seeking))
				{
					if (notInverseSearch==false) { seeking="!"+seeking; }
					java.util.TreeMap<String, String> hideReasons=getTreeMapFromObj(node.getAttribute("ui.hideReasons"));
					if (hideReasons==null)
					{
						hideReasons=new java.util.TreeMap<>();
						node.setAttribute("ui.hideReasons", hideReasons);
					}
					if (hide) { hideReasons.put(seeking, seeking); }
					else { hideReasons.remove(seeking); }
					
					boolean nodeWillHide=!hideReasons.isEmpty();
					if (nodeWillHide)
					{
						if (node.getAttribute("ui.hide")==null) { hidden++; } //if it's not already hidden, count that we're going to hide it
						node.setAttribute( "ui.hide" );
						if (m_verbose) { System.out.println("Hide node="+node.getId()); }
						
					}
					else
					{
						node.removeAttribute( "ui.hide" );
						if (m_verbose) { System.out.println("Unhide node="+node.getId()); }
					}
					
					java.util.Iterator<Edge> it=node.iterator(); //TODO rewrite in gs2.0 parlance
					while (it.hasNext())
					{
						Edge e=it.next();
						if (nodeWillHide) { e.setAttribute( "ui.hide" ); }
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
				String attrValue=(String)node.getAttribute(attribute);
				if (attrValue!=null && seeking!=null && notInverseSearch==attrValue.equals(seeking))
				{
					if (notInverseSearch==false) { seeking="!"+seeking; } 
					java.util.TreeMap<String, String> emphasizeReasons=getTreeMapFromObj(node.getAttribute("ui.emphasizeReasons"));
					if (emphasizeReasons==null)
					{
						emphasizeReasons=new java.util.TreeMap<>();
						node.setAttribute("ui.emphasizeReasons", emphasizeReasons);
					}
					if (emphasize) { emphasizeReasons.put(seeking, seeking); }
					else { emphasizeReasons.remove(seeking); }
					
					boolean nodeWillEmphasize=!emphasizeReasons.isEmpty();
					String nodeClass=(String)node.getAttribute("ui.class");
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
					node.setAttribute("ui.class", nodeClass);
					
					java.util.Iterator<Edge> it=node.iterator(); //TODO rewrite in gs2.0 parlance
					while (it.hasNext())
					{
						Edge e=it.next();
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
	
	protected java.util.Vector<String> m_lastSearchTokens=new java.util.Vector<>();
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
			ext.setAttribute("ui.class", "external"); //Add a node for "external"
			ext.setAttribute("processpath","external"); //add fake process path
			
			System.out.println("JRE: Vendor="+System.getProperty("java.vendor")+", Version="+System.getProperty("java.version"));
			System.out.println("OS: Arch="+System.getProperty("os.arch")+" Name="+System.getProperty("os.name")+" Vers="+System.getProperty("os.version"));
			System.out.println("AHA-GUI Version: "+AHAModel.class.getPackage().getImplementationVersion()+" starting up.");
			
			readInputFile();
			readScoreTable(null);
			readCustomScorefile();
			useFQDNLabels(m_graph, false);
			exploreAndScore(m_graph);
			
			m_gui.startGraphRefreshThread();
			
			Thread.sleep(1500); 
			m_gui.m_graphViewer.disableAutoLayout();
			Thread.sleep(100);  //add delay to see if issues with moving ext nodes goes away
			
			java.util.Vector<Node> leftSideNodes=new java.util.Vector<>(); //moved this below the 1.5s graph stabilization threshold to see if it makes odd occasional issues with moving ext nodes better
			for (Node n : m_graph) 
			{
				if (n.getId().contains("Ext_")) { leftSideNodes.add(n); }
				n.setAttribute("layout.weight", 6); //switched to add attribute rather than set attribute since it seems to prevent a possible race condition.
			}
			int numLeftNodes=leftSideNodes.size()+2; //1 is for main External node, 2 is so we dont put one at the very top or bottom
			leftSideNodes.insertElementAt(m_graph.getNode("external"),leftSideNodes.size()/2);
			Thread.sleep(100);  //add delay to see if issues with moving ext nodes goes away
			int i=1;
			try //FIX TODO FIXME 
			{
				for (Node n : leftSideNodes)
				{ 
					org.graphstream.ui.geom.Point3 loc=m_gui.m_graphViewPanel.getCamera().transformPxToGu(60, (m_gui.m_graphViewPanel.getHeight()/numLeftNodes)*i);
					n.setAttribute("xyz", loc.x,loc.y,loc.z);
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
								String normalScore=(String)n.getAttribute(AHAModel.scrMethdAttr(ScoreMethod.Normal));
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
								String normalScore=(String)n.getAttribute(AHAModel.scrMethdAttr(ScoreMethod.Normal));
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
						String name=(String)n.getAttribute("processname");
						if (name==null) { name=(String)n.getAttribute("ui.label"); }
						data[j++]=name;
						data[j++]=strAsInt((String)n.getAttribute("pid"));
						data[j++]=n.getAttribute("username");
						data[j++]=Integer.valueOf((int)n.edges().count()); //cant use wrapInt here because  //TODO: deduplicate connection set?
						String extPortString="";
						java.util.TreeMap<String,String> extPorts=getTreeMapFromObj(n.getAttribute("ui.extListeningPorts"));
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
						data[j++]=strAsInt((String)n.getAttribute(AHAModel.scrMethdAttr(ScoreMethod.Normal)));
						data[j++]=strAsInt((String)n.getAttribute(AHAModel.scrMethdAttr(ScoreMethod.WorstCommonProcBETA)));
						// RelativeScore CODE //
						data[j++]=strAsDouble((String)n.getAttribute(scrMethdAttr(ScoreMethod.RelativeScoreBETA))); //data[j++]=n.getAttribute("parents"); //data[j++]=n.getAttribute("sibling");
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
	
	protected static boolean readCSVFileIntoArrayList(String inputFileName, java.util.TreeMap<String, String> metadata, java.util.TreeMap<String,Integer> headerTokens, java.util.ArrayList<java.util.ArrayList<String>> inputLines)
	{
		if (inputFileName.equals("")) { inputFileName="BinaryAnalysis.csv"; } //try the default filename here if all else fails
		int lineNumber=0;
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
				if (headerToken.contains("processname") && !headerToken.equals("processname") && headerToken.length()=="processname".length() ) { System.out.println("Had to fix up 'processname' headertoken due to ByteOrderMark issues.");headerToken="processname"; }
				if (headerToken.contains("processname") && !headerToken.equals("processname") && Math.abs(headerToken.length()-"processname".length())<5) { System.out.println("Had to fix up 'processname' headertoken due to ByteOrderMark issues. hdrlen="+headerToken.length()+" ptlen="+"processname".length()); headerToken="processname"; }
				headerTokens.put(headerToken, Integer.valueOf(i)); 
			}
			String requiredTokens=metadata.get("RequiredHeaderTokens"); //leave the others between process name and the sums as this might point to a malformed file
			if (requiredTokens!=null)
			{
				String[] required=requiredTokens.split(",");
				if (required.length>2) { minTokens=required.length; }
				for (String s:required) { if (s.length() > 2 && headerTokens.get(s)==null) { System.out.println("Input file: required column header field=\""+s+"\" was not detected, this will not go well."); } }
			}

			while ((line = br.readLine()) != null) //this is the first loop, in this loop we're loading all the vertexes and their meta data, so we can then later connect the vertices
			{
				try
				{
					String[] tokens = fixCSVLine(line); 
					if (tokens.length<minTokens) 
					{ 
						if ( line.trim().trim().length() > 0 ) // more than likely this means something converted the line endings and we've and we've got crlf\nclrfloletc, so only print and increment if there's a real issue.
						{
							System.err.println("First Stage Read: Skipping line #"+lineNumber+" because it is malformed."); 
							lineNumber++;
						}
						continue;
					}
					lineNumber++;
					java.util.ArrayList<String> saveTokens=new java.util.ArrayList<>();
					saveTokens.addAll(java.util.Arrays.asList(tokens));
					inputLines.add(saveTokens); //save these for when we write out the new results file
				}
				catch (Exception e) { System.out.print("start: first readthrough: input line "+lineNumber+":"); e.printStackTrace(); }
			}
		} 
		catch (Exception e) { System.out.print("start: first readthrough: input line "+lineNumber+":"); e.printStackTrace(); } 
		return true;
	}
	
	public AHAModel(String inputFileName, boolean useOverlayScoreFile, String scoreFileName, boolean debug, boolean verbose)
	{
		m_inputFileName=inputFileName;
		m_useCustomOverlayScoreFile=useOverlayScoreFile;
		m_scoreFileName=scoreFileName;
		m_debug=debug;
		m_verbose=verbose;
	}
}


