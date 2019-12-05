package esic;

import java.awt.*;
import javax.swing.*;

public class AHASettings
{
	public final static String s_settingsFileName="AHAGUI.settings";
	private int m_verbosity=0;
	private java.awt.Font m_uiFont=null;
	protected java.util.Properties m_props=new java.util.Properties();
	
	public AHASettings (String args[]) { this(s_settingsFileName, args); }

	public AHASettings (String fileName, String args[])
	{
		loadFromDisk(fileName);
		
		java.util.TreeMap<String,String> argsMap=new java.util.TreeMap<>();
		for (String s : args) //grab the args and put them in something that makes it easier to check them without iteration later (i.e. a map)
		{
			try
			{
				String[] argTokens=s.split("=");
				if (argTokens[0]==null) { continue; }
				String key=argTokens[0].toLowerCase(), value="";
				if ( argTokens.length > 1 ) { value=argTokens[1]; }
				argsMap.put(key.replace("--", ""), value.replace("--", ""));
			} catch (Exception e) { e.printStackTrace(); }
		}
		
		try //LET'S GET READY TO SET SOME DEEEEFAAAAUUUULLLLTTTTZZZZ
		{
			String[] defaultFalse={"verbose", "debug", "single", "bigfont", "forcejmenu", "notheme", "noforcescale" };
			for ( String s : defaultFalse )
			{
				s=s.toLowerCase();
				String prop=getProperty(s);
				if (prop==null || prop.toLowerCase().contains("false")) { setProperty(s, "false"); }
				else { setProperty(s, "true"); }
				if (argsMap.get(s)!=null) { setProperty(s, "true"); }
			}
			
			String credsFile=getProperty("credsfile", "credentials.txt").trim(), inputFile=getProperty("inputfile", "BinaryAnalysis.csv").trim();
			if ( argsMap.get("credsfile")!=null ) { credsFile=argsMap.get("credsfile"); }
			if ( argsMap.get("inputfile")!=null ) { inputFile=argsMap.get("inputfile"); }
			setProperty("credsfile", credsFile);
			setProperty("inputfile", inputFile);
			if ( argsMap.get("scorefile")!=null ) //only deal with scoreFile if it's specified on the commandline, otherwise let whatever value we have lie (because null is the default)
			{
				setProperty("scorefile", argsMap.get("scorefile").trim());
			}
		} catch (Exception e) { e.printStackTrace(); }
		validateSettings();
		
		if ( m_verbosity > 0 ) { printProperties(); }
	}

	protected void showPrefsPane(AHAGUI parent) //shows the window that lists the listening sockets 
	{
		new JFrame("Preferences")
		{
			{ //adding a menubar is the easiest way to make it so we can dismiss the frame via the keyboard. eventually we should probably add more keyboard features to the data view
				JFrame self=this;
				setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
				setBackground(java.awt.Color.black);
				setSize(new java.awt.Dimension(600,600));
				setLocation(parent.getLocation().x+20, parent.getLocation().y+20); //move it down and away a little bit so people understand it's a new window
				getRootPane().setBorder(new javax.swing.border.LineBorder(java.awt.Color.GRAY,2));
				
				GridBagConstraints gc=new GridBagConstraints();
				getContentPane().setLayout(new GridBagLayout());

				gc.gridy=gc.gridx=0;
				gc.gridwidth=gc.gridheight=1;
				gc.weightx=1;
				gc.weighty=0;
				gc.fill=GridBagConstraints.HORIZONTAL;

				java.util.Set<AWTKeyStroke> forwardKeys = getFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS);
				java.util.Set<AWTKeyStroke> newForwardKeys = new java.util.HashSet<>(forwardKeys);
				newForwardKeys.add(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0));
				setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS,newForwardKeys);
				setFocusTraversalKeysEnabled(true);

				gc.gridwidth=1;
				gc.gridx=1;
				AHAGUIHelpers.addLabel(this, "Application behavior:", JLabel.LEFT, gc, AHAGUIHelpers.DirectionToIncrement.INC_Y);
				AHAGUIHelpers.addLabel(this, " ", JLabel.LEFT, gc, AHAGUIHelpers.DirectionToIncrement.INC_Y);
				
				gc.gridwidth=2;
				gc.gridx=1;
				JCheckBox verbose=AHAGUIHelpers.addCheckbox(self.getContentPane(), null, "Verbose", "", JLabel.LEFT, gc, AHAGUIHelpers.DirectionToIncrement.INC_Y, getBooleanProperty("verbose"));
				verbose.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS,newForwardKeys);
				
				JCheckBox debug=AHAGUIHelpers.addCheckbox(self.getContentPane(), null, "Debug", "", JLabel.LEFT, gc, AHAGUIHelpers.DirectionToIncrement.INC_Y, getBooleanProperty("debug"));
				debug.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS,newForwardKeys);
				
				JCheckBox single=AHAGUIHelpers.addCheckbox(self.getContentPane(), null, "Single line graph", "", JLabel.LEFT, gc, AHAGUIHelpers.DirectionToIncrement.INC_Y, getBooleanProperty("single"));
				single.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS,newForwardKeys);
				
				JCheckBox bigfont=AHAGUIHelpers.addCheckbox(self.getContentPane(), null, "Use Large Fonts", "", JLabel.LEFT, gc, AHAGUIHelpers.DirectionToIncrement.INC_Y, getBooleanProperty("bigfont"));
				bigfont.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS,newForwardKeys);
				
				JCheckBox forceJMenu=AHAGUIHelpers.addCheckbox(self.getContentPane(), null, "Force JMenu", "", JLabel.LEFT, gc, AHAGUIHelpers.DirectionToIncrement.INC_Y, getBooleanProperty("forcejmenu"));
				forceJMenu.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS,newForwardKeys);
				
				JCheckBox notheme=AHAGUIHelpers.addCheckbox(self.getContentPane(), null, "Disable Appearance Theming", "", JLabel.LEFT, gc, AHAGUIHelpers.DirectionToIncrement.INC_Y, getBooleanProperty("notheme"));
				notheme.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS,newForwardKeys);
				
				JCheckBox noforcescale=AHAGUIHelpers.addCheckbox(self.getContentPane(), null, "Do not force Java UI scale to 100%", "", JLabel.LEFT, gc, AHAGUIHelpers.DirectionToIncrement.INC_Y, getBooleanProperty("noforcescale"));
				noforcescale.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS,newForwardKeys);

				gc.gridwidth=1;
				gc.gridx=1;
				AHAGUIHelpers.addLabel(this, " ", JLabel.LEFT, gc, AHAGUIHelpers.DirectionToIncrement.INC_Y);
				AHAGUIHelpers.addLabel(this, " ", JLabel.LEFT, gc, AHAGUIHelpers.DirectionToIncrement.INC_Y);
				gc.gridwidth=1;
				gc.gridx=1;
				AHAGUIHelpers.addLabel(this, "Default file locations:", JLabel.LEFT, gc, AHAGUIHelpers.DirectionToIncrement.INC_Y);
				AHAGUIHelpers.addLabel(this, " ", JLabel.LEFT, gc, AHAGUIHelpers.DirectionToIncrement.INC_Y);
				
				
				gc.insets=new java.awt.Insets(1, 1, 1, 1); // need insets from here on
				gc.gridwidth=1;
				gc.gridx=0;
				AHAGUIHelpers.addLabel(this, "Default Input Filename ", JLabel.RIGHT, gc, AHAGUIHelpers.DirectionToIncrement.INC_X);
				gc.weightx=10;
				
				JTextField inputFile=AHAGUIHelpers.addTextfield(this, getProperty("inputfile",""), gc, AHAGUIHelpers.DirectionToIncrement.INC_Y);
				gc.weightx=1;
				inputFile.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS,newForwardKeys);
				
				gc.gridwidth=1;
				gc.gridx=0;
				AHAGUIHelpers.addLabel(this, "Default Credentials Filename ", JLabel.RIGHT, gc, AHAGUIHelpers.DirectionToIncrement.INC_X);
				gc.weightx=10;
				gc.gridwidth=2;
				JTextField credsFile=AHAGUIHelpers.addTextfield(this, getProperty("credsfile",""), gc, AHAGUIHelpers.DirectionToIncrement.INC_Y);
				gc.weightx=1;
				credsFile.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS,newForwardKeys);
				
				gc.gridwidth=1;
				gc.gridx=0;
				AHAGUIHelpers.addLabel(this, "Default Scorefile Filename ", JLabel.RIGHT, gc, AHAGUIHelpers.DirectionToIncrement.INC_X);
				gc.weightx=10;
				gc.gridwidth=2;
				JTextField scoreFile=AHAGUIHelpers.addTextfield(this, getProperty("scorefile",""), gc, AHAGUIHelpers.DirectionToIncrement.INC_Y);
				gc.weightx=1;
				scoreFile.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS,newForwardKeys);
				
				gc.gridwidth=3;
				gc.gridx=0;
				gc.weighty=10;
				gc.fill=GridBagConstraints.BOTH;
				AHAGUIHelpers.addLabel(this, "", JLabel.LEFT, gc, AHAGUIHelpers.DirectionToIncrement.INC_Y);
				
				gc.insets=new java.awt.Insets(4, 4, 4, 4);
				gc.weighty=0;
				gc.gridwidth=3;
				gc.fill=GridBagConstraints.HORIZONTAL;
				AHAGUIHelpers.addLabel(this, "Saved preferences are applied at next application launch.", JLabel.CENTER, gc, AHAGUIHelpers.DirectionToIncrement.INC_Y);
				gc.insets=new java.awt.Insets(4, 20, 8, 20);
				JButton save=AHAGUIHelpers.addButton(this, null, "Save", "save", gc, AHAGUIHelpers.DirectionToIncrement.INC_Y);
				save.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS,newForwardKeys);
				
				save.addActionListener(new java.awt.event.ActionListener()
				{
          public void actionPerformed(java.awt.event.ActionEvent event)
          {
          	setBooleanProperty("verbose", verbose.isSelected());
          	setBooleanProperty("debug", debug.isSelected());
          	setBooleanProperty("single", single.isSelected());
          	setBooleanProperty("bigfont", bigfont.isSelected());
          	setBooleanProperty("forcejmenu", forceJMenu.isSelected());
          	setBooleanProperty("notheme", notheme.isSelected());
          	setBooleanProperty("noforcescale", noforcescale.isSelected());
          	
          	if (inputFile.getText()!=null && !inputFile.getText().equals("")) { setProperty("inputfile", inputFile.getText()); }
          	if (credsFile.getText()!=null && !credsFile.getText().equals("")) { setProperty("credsfile", credsFile.getText()); }
          	if (scoreFile.getText()!=null && !scoreFile.getText().equals("")) { setProperty("scorefile", scoreFile.getText()); }
          	
          	storeToDisk(s_settingsFileName);
          	dispose();
          }
				});
				gc.gridx=0;
			}
		}.setVisible(true);
	}
	
	// ---   Internal Operations   ---
	private void loadFromDisk(String fileName)
	{
		java.io.File settingsFile=new java.io.File(fileName);
		try
		{
			if (!settingsFile.exists()) { System.out.println("No settings file found, at "+fileName); }
			else { m_props.load(new java.io.FileInputStream(settingsFile)); }
		} catch (Exception e) { e.printStackTrace(); }
	}
	
	private void storeToDisk(String fileName)
	{
		try { m_props.store(new java.io.FileOutputStream(new java.io.File(fileName)), "update settings "); } 
  	catch (Exception e) { e.printStackTrace(); }
	}
	
	private void printProperties()
	{
		System.out.println("AHA Settings:");
		for (java.util.Map.Entry<Object, Object> entry : m_props.entrySet()) { System.out.printf("key='%s' value='%s'\n", entry.getKey(), entry.getValue()); }
	}
	
	private synchronized void validateSettings() // ideally call anytime settings are changed. //TODO probably should call this ourselves within this class on every write
	{
		m_verbosity=0; //reset to 0
		if (getBooleanProperty("verbose")) { m_verbosity=1; }
		if (getBooleanProperty("debug")) { m_verbosity=5; }
		if (m_uiFont==null) //TODO this will only happen once...but it would be weird if parts of the UI got big and parts didnt when it changed at runtime
		{
			m_uiFont=new java.awt.Font(java.awt.Font.MONOSPACED,java.awt.Font.PLAIN,12);
			if (getBooleanProperty("bigfont")) { m_uiFont=new java.awt.Font(java.awt.Font.MONOSPACED,java.awt.Font.PLAIN,18); }
		}
	}
	
	// ---   Getters   ---
	protected synchronized String getProperty(String key) { return m_props.getProperty(key.toLowerCase()); }
	protected synchronized String getProperty(String key, String defaultValue) { return m_props.getProperty(key.toLowerCase(), defaultValue); }
	protected synchronized boolean getBooleanProperty(String key) { return (m_props.getProperty(key.toLowerCase(),"false")).toLowerCase().contains("true"); }
	protected synchronized int getVerbosity() { return m_verbosity; }
	protected synchronized java.awt.Font getDefaultFont() { return m_uiFont; }
	
	// ---   Setters   ---
	protected synchronized void setProperty(String key, String value) 
	{ 
		m_props.setProperty(key.toLowerCase().trim(),value.trim());
		validateSettings();
	}
	protected synchronized void setBooleanProperty(String key, boolean bool) 
	{ 
		m_props.setProperty(key.toLowerCase().trim(),Boolean.toString(bool).toLowerCase());
		validateSettings();
	}

}
