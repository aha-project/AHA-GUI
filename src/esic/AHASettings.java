package esic;

import java.awt.AWTKeyStroke;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.KeyboardFocusManager;
import java.io.File;
import java.util.Properties;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;

public class AHASettings
{
	public final static String s_settingsFileName="AHAGUI.settings";
	
	protected Properties m_props=new Properties();
	
	public AHASettings (String args[]) { this(s_settingsFileName, args); }

	public AHASettings (String fileName, String args[])
	{
		java.io.File settingsFile=new File(fileName);
		try
		{
			if (!settingsFile.exists()) { System.out.println("No settings file found, at "+fileName); }
			else { m_props.load(new java.io.FileInputStream(settingsFile)); }
		} catch (Exception e) { e.printStackTrace(); }
		
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
				String prop=m_props.getProperty(s);
				if (prop==null || prop.toLowerCase().contains("false")) { m_props.setProperty(s, "false"); }
				else { m_props.setProperty(s, "true"); }
				if (argsMap.get(s)!=null) { m_props.setProperty(s, "true"); }
			}
			
			String credsFile=m_props.getProperty("credsfile", "credentials.txt").trim(), inputFile=m_props.getProperty("inputfile", "BinaryAnalysis.csv").trim();
			if ( argsMap.get("credsfile")!=null ) { credsFile=argsMap.get("credsfile"); }
			if ( argsMap.get("inputfile")!=null ) { inputFile=argsMap.get("inputfile"); }
			m_props.setProperty("credsfile", credsFile);
			m_props.setProperty("inputfile", inputFile);
			if ( argsMap.get("scorefile")!=null ) //only deal with scoreFile if it's specified on the commandline, otherwise let whatever value we have lie (because null is the default)
			{
				m_props.setProperty("scorefile", argsMap.get("scorefile").trim());
			}
		} catch (Exception e) { e.printStackTrace(); }
		
		if (m_props.get("verbose").equals("true") || m_props.get("debug").equals("true") )
		{
			System.out.println("Read in settings:");
			for (java.util.Map.Entry<Object, Object> entry : m_props.entrySet()) { System.out.printf("key='%s' value='%s'\n", entry.getKey(), entry.getValue()); }
		}
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
				JCheckBox verbose=AHAGUIHelpers.addCheckbox(self.getContentPane(), null, "Verbose", "", JLabel.LEFT, gc, AHAGUIHelpers.DirectionToIncrement.INC_Y);
				verbose.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS,newForwardKeys);
				if (m_props.getProperty("verbose","false").toLowerCase().contains("true")) { verbose.setSelected(true); }
				
				JCheckBox debug=AHAGUIHelpers.addCheckbox(self.getContentPane(), null, "Debug", "", JLabel.LEFT, gc, AHAGUIHelpers.DirectionToIncrement.INC_Y);
				debug.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS,newForwardKeys);
				if (m_props.getProperty("debug","false").toLowerCase().contains("true")) { debug.setSelected(true); }
				
				JCheckBox single=AHAGUIHelpers.addCheckbox(self.getContentPane(), null, "Single line graph", "", JLabel.LEFT, gc, AHAGUIHelpers.DirectionToIncrement.INC_Y);
				single.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS,newForwardKeys);
				if (m_props.getProperty("single","false").toLowerCase().contains("true")) { single.setSelected(true); }
				
				JCheckBox bigfont=AHAGUIHelpers.addCheckbox(self.getContentPane(), null, "Use Large Fonts", "", JLabel.LEFT, gc, AHAGUIHelpers.DirectionToIncrement.INC_Y);
				bigfont.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS,newForwardKeys);
				if (m_props.getProperty("bigfont","false").toLowerCase().contains("true")) { bigfont.setSelected(true); }
				
				JCheckBox forceJMenu=AHAGUIHelpers.addCheckbox(self.getContentPane(), null, "Force JMenu", "", JLabel.LEFT, gc, AHAGUIHelpers.DirectionToIncrement.INC_Y);
				forceJMenu.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS,newForwardKeys);
				if (m_props.getProperty("forcejmenu","false").toLowerCase().contains("true")) { forceJMenu.setSelected(true); }
				
				JCheckBox notheme=AHAGUIHelpers.addCheckbox(self.getContentPane(), null, "Disable Appearance Theming", "", JLabel.LEFT, gc, AHAGUIHelpers.DirectionToIncrement.INC_Y);
				notheme.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS,newForwardKeys);
				if (m_props.getProperty("notheme","false").toLowerCase().contains("true")) { notheme.setSelected(true); }
				
				JCheckBox noforcescale=AHAGUIHelpers.addCheckbox(self.getContentPane(), null, "Do not force Java UI scale to 100%", "", JLabel.LEFT, gc, AHAGUIHelpers.DirectionToIncrement.INC_Y);
				noforcescale.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS,newForwardKeys);
				if (m_props.getProperty("noforcescale","false").toLowerCase().contains("true")) { noforcescale.setSelected(true); }

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
				
				JTextField inputFile=AHAGUIHelpers.addTextfield(this, m_props.getProperty("inputfile",""), gc, AHAGUIHelpers.DirectionToIncrement.INC_Y);
				gc.weightx=1;
				inputFile.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS,newForwardKeys);
				
				gc.gridwidth=1;
				gc.gridx=0;
				AHAGUIHelpers.addLabel(this, "Default Credentials Filename ", JLabel.RIGHT, gc, AHAGUIHelpers.DirectionToIncrement.INC_X);
				gc.weightx=10;
				gc.gridwidth=2;
				JTextField credsFile=AHAGUIHelpers.addTextfield(this, m_props.getProperty("credsfile",""), gc, AHAGUIHelpers.DirectionToIncrement.INC_Y);
				gc.weightx=1;
				credsFile.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS,newForwardKeys);
				
				gc.gridwidth=1;
				gc.gridx=0;
				AHAGUIHelpers.addLabel(this, "Default Scorefile Filename ", JLabel.RIGHT, gc, AHAGUIHelpers.DirectionToIncrement.INC_X);
				gc.weightx=10;
				gc.gridwidth=2;
				JTextField scoreFile=AHAGUIHelpers.addTextfield(this, m_props.getProperty("scorefile",""), gc, AHAGUIHelpers.DirectionToIncrement.INC_Y);
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
          	m_props.setProperty("verbose", Boolean.toString(verbose.isSelected()).toLowerCase());
          	m_props.setProperty("debug", Boolean.toString(debug.isSelected()).toLowerCase());
          	m_props.setProperty("single", Boolean.toString(single.isSelected()).toLowerCase());
          	m_props.setProperty("bigfont", Boolean.toString(bigfont.isSelected()).toLowerCase());
          	m_props.setProperty("forcejmenu", Boolean.toString(forceJMenu.isSelected()).toLowerCase());
          	m_props.setProperty("notheme", Boolean.toString(notheme.isSelected()).toLowerCase());
          	m_props.setProperty("noforcescale", Boolean.toString(noforcescale.isSelected()).toLowerCase());
          	
          	if (inputFile.getText()!=null && !inputFile.getText().equals("")) { m_props.setProperty("inputfile", inputFile.getText()); }
          	if (credsFile.getText()!=null && !credsFile.getText().equals("")) { m_props.setProperty("credsfile", credsFile.getText()); }
          	if (scoreFile.getText()!=null && !scoreFile.getText().equals("")) { m_props.setProperty("scorefile", scoreFile.getText()); }
          	
          	try { m_props.store(new java.io.FileOutputStream(new File(s_settingsFileName)), "update settings "); } 
          	catch (Exception e) { e.printStackTrace(); }
          	dispose();
          }
				});
				gc.gridx=0;
			}
		}.setVisible(true);
	}

}
