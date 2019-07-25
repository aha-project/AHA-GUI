package esic;
//Copyright 2018 ESIC at WSU distributed under the MIT license. Please see LICENSE file for further info.

import javax.swing.UIManager;

public class AHAGUIHelpers
{
	public static void applyTheme(java.awt.Font uiFont) //Apply theme for JCheckbox, JComboBox, JLabel,  JSrollPane, JTabbedPane, JTable
	{ // Need to figure out what key something is named? The stuff below will search and then exit for keys containing the string
		System.err.println("Applying theme.");
		try 
		{ 
			javax.swing.InputMap im = (javax.swing.InputMap) UIManager.get("TextField.focusInputMap"); //TODO other input map save/store might be required later if we have more components with inputmaps
			UIManager.setLookAndFeel( UIManager.getCrossPlatformLookAndFeelClassName() ); 
			UIManager.put("TextField.focusInputMap", im); //restore the original LAF input map (keep windows control c/v and macOS command c/v as they should be.
		} catch (Exception e) { System.err.println("Failed to set look and feel:"); e.printStackTrace(); }
		//	java.util.List<String> t=new java.util.ArrayList<String>(2048);
		//	for (Object key : UIManager.getLookAndFeelDefaults().keySet()) { t.add(key.toString()); }
		//	java.util.Collections.sort(t);
		//	for (String key : t ) { if (key.toLowerCase().contains("background")) { System.out.println(key); } }
		java.awt.Color backgroundColor=java.awt.Color.BLACK, foregroundColor=java.awt.Color.GREEN, accentColor=java.awt.Color.DARK_GRAY.darker().darker(), lightAccent=java.awt.Color.DARK_GRAY;//, dbugcolor=java.awt.Color.ORANGE;
		UIManager.put("Button.background", accentColor.brighter().brighter());
		UIManager.put("Button.darkShadow", backgroundColor);
		UIManager.put("Button.focus", accentColor.brighter().brighter()); //remove selection reticle
		UIManager.put("Button.font",uiFont);
		UIManager.put("Button.foreground", foregroundColor);
		UIManager.put("Button.select", java.awt.Color.GRAY.darker());
		javax.swing.border.Border b=new javax.swing.plaf.basic.BasicBorders.RolloverButtonBorder( lightAccent.brighter().brighter(),  lightAccent.brighter().brighter(), lightAccent.brighter().brighter(), lightAccent.brighter().brighter());
		UIManager.put("Button.border", new javax.swing.border.CompoundBorder(javax.swing.BorderFactory.createLineBorder(java.awt.Color.GRAY),b ));
		UIManager.put("CheckBox.foreground", foregroundColor);
		UIManager.put("CheckBox.background", backgroundColor);
		UIManager.put("CheckBox.focus", backgroundColor);
		UIManager.put("CheckBox.font", uiFont);
		UIManager.put("CheckBox.gradient", java.util.Arrays.asList( new Object[] {Float.valueOf(0f),Float.valueOf(0f), java.awt.Color.LIGHT_GRAY, java.awt.Color.LIGHT_GRAY, java.awt.Color.GRAY.brighter() }));
		
		UIManager.put("CheckBoxMenuItem.foreground", foregroundColor);
		UIManager.put("CheckBoxMenuItem.acceleratorForeground", foregroundColor);
		UIManager.put("CheckBoxMenuItem.background", accentColor);
		UIManager.put("CheckBoxMenuItem.selectionBackground", foregroundColor);
		UIManager.put("CheckBoxMenuItem.selectionForeground", backgroundColor);
		UIManager.put("CheckBoxMenuItem.gradient", null);
		UIManager.put("CheckBoxMenuItem.border", javax.swing.BorderFactory.createEmptyBorder());
		UIManager.put("ComboBox.background", accentColor.brighter().brighter());
		UIManager.put("ComboBox.font", uiFont); 
		UIManager.put("ComboBox.foreground", foregroundColor);
		UIManager.put("ComboBox.selectionForeground", foregroundColor);
		UIManager.put("ComboBox.selectionBackground", accentColor.brighter().brighter()); 
		UIManager.put("ComboBox.border", javax.swing.BorderFactory.createCompoundBorder(javax.swing.BorderFactory.createLineBorder(java.awt.Color.GRAY),javax.swing.BorderFactory.createLineBorder(accentColor.brighter().brighter(),2)));
		UIManager.put("ComboBoxUI", javax.swing.plaf.basic.BasicComboBoxUI.class.getName());
		
		UIManager.put("Desktop.background", backgroundColor);
		UIManager.put("Frame.foreground", foregroundColor);
		UIManager.put("Frame.background", backgroundColor);
		UIManager.put("Label.foreground", foregroundColor);
		UIManager.put("Label.background", backgroundColor);
		UIManager.put("Label.font", uiFont);
		UIManager.put("List.foreground", foregroundColor); //primarily here for the JFileChooser background file list pane
		UIManager.put("List.background", backgroundColor);
		UIManager.put("List.selectionForeground", backgroundColor);
		UIManager.put("List.selectionBackground", foregroundColor);
		UIManager.put("List.focusCellHighlightBorder", foregroundColor);
		UIManager.put("List.font", uiFont);
		UIManager.put("List.background", backgroundColor);
		
		UIManager.put("Menu.foreground", foregroundColor);
		UIManager.put("Menu.background", accentColor.brighter());
		UIManager.put("Menu.selectionBackground", foregroundColor);
		UIManager.put("Menu.selectionForeground", backgroundColor);
		UIManager.put("Menu.acceleratorForeground", foregroundColor);
		UIManager.put("Menu.acceleratorSelectionForeground", backgroundColor);
		UIManager.put("Menu.border", javax.swing.BorderFactory.createEmptyBorder()); //new javax.swing.border.LineBorder(accentColor));//
		UIManager.put("Menu.opaque", true);
		UIManager.put("MenuBar.foreground", foregroundColor);
		UIManager.put("MenuBar.background", accentColor.brighter());
		UIManager.put("MenuBar.border", javax.swing.BorderFactory.createEmptyBorder());
		UIManager.put("MenuBar.font", uiFont);
		UIManager.put("MenuItem.foreground", foregroundColor);
		UIManager.put("MenuItem.background", accentColor);
		UIManager.put("MenuItem.selectionBackground", foregroundColor);
		UIManager.put("MenuItem.selectionForeground", backgroundColor);
		UIManager.put("MenuItem.acceleratorForeground", foregroundColor);
		UIManager.put("MenuItem.border", javax.swing.BorderFactory.createEmptyBorder());
		UIManager.put("MenuItem.borderPainted", false);
		UIManager.put("MenuItem.acceleratorFont", UIManager.get("MenuItem.font")); //make this consistent otherwise accelerators are in a crazy font
		
		UIManager.put("OptionPane.foreground", foregroundColor);
		UIManager.put("OptionPane.messageForeground", foregroundColor);
		UIManager.put("OptionPane.background", backgroundColor);
		UIManager.put("OptionPane.font", uiFont);
		UIManager.put("Panel.foreground", foregroundColor);
		UIManager.put("Panel.background", backgroundColor);
		UIManager.put("PopupMenu.foreground", foregroundColor);
		UIManager.put("PopupMenu.background", accentColor);
		UIManager.put("PopupMenu.border", javax.swing.BorderFactory.createEmptyBorder());
		UIManager.put("ProgressBar.selectionForeground", foregroundColor);
		UIManager.put("ProgressBar.selectionBackground", backgroundColor);
		UIManager.put("ProgressBar.foreground", foregroundColor);
		UIManager.put("ProgressBar.background", backgroundColor);
		UIManager.put("ProgressBar.border", javax.swing.BorderFactory.createLineBorder(accentColor.brighter().brighter(),2));
		UIManager.put("ProgressBar.font", uiFont);
		UIManager.put("RadioButtonMenuItem.foreground", foregroundColor);
		UIManager.put("RadioButtonMenuItem.background", accentColor);
		UIManager.put("RadioButtonMenuItem.selectionBackground", foregroundColor);
		UIManager.put("RadioButtonMenuItem.selectionForeground", backgroundColor);
		UIManager.put("RadioButtonMenuItem.gradient", null);
		UIManager.put("RadioButtonMenuItem.border", javax.swing.BorderFactory.createEmptyBorder());
		UIManager.put("RadioButtonMenuItem.acceleratorForeground", foregroundColor);
		
		UIManager.put("ScrollBar.track", backgroundColor);
		UIManager.put("ScrollBar.thumbDarkShadow", backgroundColor);
		UIManager.put("ScrollBar.thumb", accentColor.brighter().brighter().brighter());
		UIManager.put("ScrollBar.thumbHighlight", accentColor.brighter().brighter().brighter());
		UIManager.put("ScrollBar.thumbShadow", accentColor.brighter().brighter().brighter());
		UIManager.put("ScrollBarUI", javax.swing.plaf.basic.BasicScrollBarUI.class.getName() );
		UIManager.put("ScrollBar.width", 8);
		UIManager.put("ScrollPane.foreground", foregroundColor);
		UIManager.put("ScrollPane.background", backgroundColor);
		UIManager.put("Separator.foreground", java.awt.Color.LIGHT_GRAY); //the separator used in menus
		UIManager.put("Separator.background", accentColor);
		UIManager.put("Separator.shadow", accentColor);
		UIManager.put("SplitPaneDivider.border", new javax.swing.border.LineBorder(lightAccent));
		UIManager.put("SplitPane.background", backgroundColor);

		UIManager.put("TabbedPane.foreground", foregroundColor);
		UIManager.put("TabbedPane.background", backgroundColor);
		UIManager.put("TabbedPane.light", backgroundColor);
		UIManager.put("TabbedPane.borderHightlightColor", backgroundColor);
		UIManager.put("TabbedPane.selected", accentColor.brighter().brighter().brighter());
		UIManager.put("TabbedPane.focus",accentColor.brighter().brighter().brighter());
		UIManager.put("TabbedPane.selectHighlight",accentColor.brighter().brighter().brighter());
		UIManager.put("TabbedPane.darkShadow", accentColor.brighter().brighter().brighter()); //removes difficult to see blue glow around inactive tab edge
		UIManager.put("TabbedPane.contentBorderInsets", new java.awt.Insets(0,0,0,0));
		UIManager.put("TabbedPane.tabsOverlapBorder", true); 
		UIManager.put("TableUI", javax.swing.plaf.basic.BasicTableUI.class.getName() );
		UIManager.put("Table.foreground", foregroundColor);
		UIManager.put("Table.background", backgroundColor);
		UIManager.put("Table.focusCellForeground", foregroundColor);
		UIManager.put("Table.focusCellBackground", backgroundColor);
		UIManager.put("Table.dropCellBackground", backgroundColor);
		UIManager.put("Table.gridColor", accentColor);
		UIManager.put("Table.font", uiFont); //
		UIManager.put("Table.selectionBackground", foregroundColor.darker());
		UIManager.put("Table.focusCellHighlightBorder", foregroundColor.darker());
		UIManager.put("Table.selectionForeground", backgroundColor);
		UIManager.put("Table.sortIconColor", foregroundColor); 
		UIManager.put("Table.ascendingSortIcon",new AHASortIcon(true));
		UIManager.put("Table.descendingSortIcon",new AHASortIcon(false));
		UIManager.put("TableHeaderUI", javax.swing.plaf.basic.BasicTableHeaderUI.class.getName() );
		UIManager.put("TableHeader.foreground", foregroundColor);
		UIManager.put("TableHeader.background", accentColor);
		UIManager.put("TableHeader.cellBorder", new javax.swing.border.BevelBorder(javax.swing.border.BevelBorder.RAISED));
		UIManager.put("TableHeader.font", uiFont); 
		UIManager.put("TextField.foreground", foregroundColor);
		UIManager.put("TextField.caretForeground", foregroundColor);
		UIManager.put("TextField.background", backgroundColor);
		UIManager.put("TextField.focus", backgroundColor);
		UIManager.put("TextField.font", uiFont);
		UIManager.put("TextField.border", new javax.swing.border.LineBorder(lightAccent,1));
		UIManager.put("ToolTip.foreground", java.awt.Color.BLACK);
		UIManager.put("ToolTip.border", java.awt.Color.WHITE);
		UIManager.put("ToolTip.background", java.awt.Color.WHITE);
		UIManager.put("ToolTip.font", uiFont);
		UIManager.put("Viewport.foreground", foregroundColor);
		UIManager.put("Viewport.background", accentColor);
	}

	public static class AHASortIcon implements javax.swing.Icon
	{ //maybe clean this up a little so it can be variably sized...but on the other hand, it looks good exactly with these numbers.
		boolean thingToDraw;
		public AHASortIcon(boolean icon) { thingToDraw=icon; }
		public void paintIcon(java.awt.Component c, java.awt.Graphics g, int x, int y)
		{
			java.awt.Graphics2D g2 = (java.awt.Graphics2D)g; //System.out.printf("paintIcon called x=%d y=%d\n",x,y);
			g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(java.awt.Color.GREEN);
			if (thingToDraw==true) { g2.fillPolygon(new int[]{x,x+6,x+3}, new int[]{y+5,y+5,y}, 3); }
			else { g2.fillPolygon(new int[]{x,x+6,x+3}, new int[]{y+1,y+1,y+6}, 3); }
		}
		public int getIconWidth() { return 6; }
		public int getIconHeight() { return 6; }
	}
	
	public static javax.swing.JMenuItem createMenuItem(javax.swing.JMenuItem item, java.awt.event.ActionListener listener, String actionCommand, String tooltip, javax.swing.JMenu menu, javax.swing.KeyStroke accelerator, java.util.Vector<javax.swing.JMenuItem> defaultSet)
	{
		item.setActionCommand(actionCommand);
		item.addActionListener(listener);
		try
		{
			if (System.getProperty("os.name").toLowerCase().contains("mac") && System.getProperty("apple.laf.useScreenMenuBar")!=null && System.getProperty("apple.laf.useScreenMenuBar").equalsIgnoreCase("false")) { tooltip=AHAGUIHelpers.styleToolTipText(tooltip); }
		} catch (Exception e) { System.err.print("Failed to style tooltip text...possibly due to inability to assess platform. error: ");e.printStackTrace(); }  
		item.setToolTipText(tooltip);
		menu.add(item);
		if (defaultSet!=null) { defaultSet.add(item); }
		if (accelerator!=null) { item.setAccelerator(accelerator); }
		return item;
	}
	
	public static String styleToolTipText(String s) //format all tool tip texts by making them HTML (so we can apply text effects, and more importantly line breaks)
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
		return "<html><p style='font-style:bold;color:black;background:white;'>"+s+"</p></html>";
	}
	
	public static javax.swing.JScrollPane createTablesInScrollPane(String[][] columnHeaders, String[][] columnTooltips, Object[][][] initialData, javax.swing.JTable[] tableRefs, int[] columnWidths)
	{
		javax.swing.JPanel scrollContent=new javax.swing.JPanel();
		scrollContent.setLayout(new javax.swing.BoxLayout(scrollContent, javax.swing.BoxLayout.Y_AXIS));
		javax.swing.table.DefaultTableCellRenderer tcLeftAlignRenderer=new javax.swing.table.DefaultTableCellRenderer(){{setHorizontalAlignment(javax.swing.table.DefaultTableCellRenderer.LEFT);}};
		for (int i=0; i<tableRefs.length; i++)
		{
			final String[] columnToolt=columnTooltips[i];
			if (tableRefs[i]==null) 
			{ 
				tableRefs[i]=new javax.swing.JTable() 
				{
					private String[] columnToolTips=columnToolt;
					public void changeSelection(int rowIndex, int columnIndex, boolean toggle, boolean extend) { super.changeSelection(rowIndex, columnIndex, !extend, extend); } //Always toggle on single selection (allows users to deselect rows easier)
					public boolean isCellEditable(int row, int column) { return false; } //disable cell editing
					public java.awt.Component prepareRenderer(javax.swing.table.TableCellRenderer renderer, int row, int column) 
					{
						java.awt.Component c = super.prepareRenderer(renderer, row, column);
						if (c instanceof javax.swing.JComponent) 
						{
							javax.swing.JComponent jc = (javax.swing.JComponent) c;
							Object o=getValueAt(row, column);
							if (o!=null) {  jc.setToolTipText(o.toString());}
						}
						return c;
					}
					protected javax.swing.table.JTableHeader createDefaultTableHeader() 
					{
						return new javax.swing.table.JTableHeader(columnModel) 
						{
							public String getToolTipText(java.awt.event.MouseEvent e) 
							{
								java.awt.Point p = e.getPoint();
								int tblIdx = columnModel.getColumnIndexAtX(p.x);
								int columnIdx = columnModel.getColumn(tblIdx).getModelIndex();
								if (columnIdx<columnToolTips.length) { return AHAGUIHelpers.styleToolTipText(columnToolTips[columnIdx]); }
								return "";
							}
						};
					}
				}; 
			}
			tableRefs[i].setModel( new javax.swing.table.DefaultTableModel(initialData[i], columnHeaders[i]) 
			{ 
				public Class<?> getColumnClass(int column) //makes it so row sorters work properly
				{ 
					try
					{
						Object o=null;
						for (int row=0;row<getRowCount();row++) 
						{
							o=getValueAt(row, column);
							if (o!=null) { break; }
						}
						if (o instanceof String) { return String.class; }//return java.text.AttributedString.class; }
						if (o instanceof Integer) { return Integer.class; }
						if (o instanceof Double) { return Double.class; }
						if (o instanceof Float) { return Float.class; }
						if (o instanceof Long) { return Long.class; }
					} catch (Exception e) { e.printStackTrace(); }
					return Object.class;
				}
			});
			tableRefs[i].setDefaultRenderer(Integer.class, tcLeftAlignRenderer);
			tableRefs[i].setPreferredScrollableViewportSize(tableRefs[i].getPreferredSize());
			tableRefs[i].setAlignmentY(javax.swing.JTable.TOP_ALIGNMENT);
			tableRefs[i].getTableHeader().setAlignmentY(javax.swing.JTable.TOP_ALIGNMENT);
			tableRefs[i].setAutoCreateRowSorter(true);
			for (int j=0;j<tableRefs[i].getColumnModel().getColumnCount() && j<columnWidths.length; j++) { tableRefs[i].getColumnModel().getColumn(j).setPreferredWidth(columnWidths[j]); }
			scrollContent.add(tableRefs[i].getTableHeader());
			scrollContent.add(tableRefs[i]);
		}
		javax.swing.JScrollPane scrollPane = new javax.swing.JScrollPane(scrollContent);
		scrollPane.setViewportBorder(javax.swing.BorderFactory.createEmptyBorder());
		scrollPane.setBorder(javax.swing.BorderFactory.createEmptyBorder());
		return scrollPane;
	}
	
	protected static void tryUpdateProgress(javax.swing.ProgressMonitor pm, int progress, int max, String note) //updating ignored if int is <0 or any of the args is null
	{
		try
		{
			if (pm==null) { return; }
			if (progress >= 0) pm.setProgress(progress);
			if (max >= 0) { pm.setMaximum(max); }
			if (note!=null) { pm.setNote(note); }
		}
		catch (Exception e) {}
	}
	
	protected static boolean tryGetProgressCanceled(javax.swing.ProgressMonitor pm) //updating ignored if int is <0 or any of the args is null
	{
		try
		{
			if (pm!=null) { return pm.isCanceled(); }
		}
		catch (Exception e) {}
		return false;
	}
	
	protected static void tryCancelSplashScreen()
	{
		try { java.awt.SplashScreen.getSplashScreen().close(); } catch(Exception e) {}
	}
	
}
