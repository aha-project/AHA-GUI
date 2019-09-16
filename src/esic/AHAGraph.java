package esic;
//Copyright 2018 ESIC at WSU distributed under the MIT license. Please see LICENSE file for further info.

public class AHAGraph implements java.lang.Iterable<AHAGraph.AHANode> // wrapper for graphstream so we can do the heavy lifting for attribute/metadata/data storage and only pass on stuff actually necessary to the graph, to the graph (colors, ui classes, etc)
{
	protected org.graphstream.graph.Graph graph=null;
	private java.util.TreeMap<String, AHANode> m_nodes=new java.util.TreeMap<>(); //TODO: is this the best datatype for this? (off the cuff thought is yes, but re-evaluate later)
	
	public static class AHANode implements java.lang.Iterable<org.graphstream.graph.Edge> 
	{
		private String m_nodeID="";
		protected org.graphstream.graph.Node graphNode=null;
		private java.util.HashMap<String,String> m_stringAttrs=new java.util.HashMap<>();
		private java.util.HashMap<String,Object[][]> m_objStarStarAttrs=new java.util.HashMap<>();
		private java.util.HashMap<String,java.util.Vector<esic.AHAModel.ConnectionEntry>> m_connectionEntryTables=new java.util.HashMap<>();
		private java.util.HashMap<String,java.util.List<String>> m_stringLists=new java.util.HashMap<>();
		private java.util.HashMap<String,java.util.TreeMap<String,String>> m_stringStringMap=new java.util.HashMap<>();
		
		public AHANode(String nodeID, org.graphstream.graph.Node node)
		{
			m_nodeID=nodeID;
			graphNode=node;
			setAttribute("ui.label", AHAModel.capitalizeFirstLetter(m_nodeID));
			node.setAttribute("layout.weight", 10);
			setAttribute("processname", AHAModel.capitalizeFirstLetter(m_nodeID)); //for testing
			putConnectionEntryTable("allConnections", new java.util.Vector<esic.AHAModel.ConnectionEntry>()); //TODO might want to not have to do this in the future / make generic
			putStringMap("aha.graphlayer", new java.util.TreeMap<String,String>());
			putStringMap("aha.hideReasons", new java.util.TreeMap<String,String>());
		}
		public String getId() { return m_nodeID; }
		
		public String getAttribute(String key) { return m_stringAttrs.get(key.toLowerCase()); }
		public String removeAttribute(String key) 
		{ 
			key=key.toLowerCase();
			if (key.startsWith("ui.") || key.equals("xyz")) { graphNode.removeAttribute(key); }
			return m_stringAttrs.remove(key); 
		}
		public void setAttribute(String key, String value)
		{
			key=key.toLowerCase();
			m_stringAttrs.put(key, value);
			if (key.startsWith("ui.") || key.equals("xyz")) { graphNode.setAttribute(key, value); }  //TODO: IMPROVE
		}
		
		public Object[][] getSidebarAttributes(String key) { return m_objStarStarAttrs.get(key.toLowerCase()); }
		public void putSidebarAttribute(String key, Object[][] value) { m_objStarStarAttrs.put(key.toLowerCase(), value); }
		
		public java.util.Vector<esic.AHAModel.ConnectionEntry> getConnectionEntryTable(String key) { return m_connectionEntryTables.get(key.toLowerCase()); }
		public void putConnectionEntryTable(String key, java.util.Vector<esic.AHAModel.ConnectionEntry> value) { m_connectionEntryTables.put(key.toLowerCase(), value); }
		
		public java.util.List<String> getStringList(String key) { return m_stringLists.get(key.toLowerCase()); }
		public void putStringList(String key, java.util.List<String> value) { m_stringLists.put(key.toLowerCase(), value); }
		
		public java.util.TreeMap<String,String> getStringMap(String key) { return m_stringStringMap.get(key.toLowerCase()); }
		public void putStringMap(String key, java.util.TreeMap<String,String> value) { m_stringStringMap.put(key.toLowerCase(), value); }
		
		public java.util.Iterator<org.graphstream.graph.Edge> iterator() { return graphNode.iterator(); }
		
		public String toString() { Thread.dumpStack(); return m_nodeID; } //make sure we know when implicit things are getting the Id...for now.
	} //end AHANode
	
	public AHANode addNode(String key) // graph methods
	{ 
		key=key.toLowerCase();
		AHANode newNode=new AHANode(key,graph.addNode(key));
		m_nodes.put(key,newNode);
		return newNode;
	}
	public AHANode getNode (String key) { return m_nodes.get(key.toLowerCase()); }
	public org.graphstream.graph.Edge addEdge (String key, String from, String to) { return graph.addEdge(key.toLowerCase(), from.toLowerCase(), to.toLowerCase()); }
	public java.util.Iterator<AHANode> iterator() { return m_nodes.values().iterator(); } //also enables 'for (node n : graph)' syntax
}
