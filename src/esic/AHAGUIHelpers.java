package esic;

import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import javax.swing.BorderFactory;

public class AHAGUIHelpers
{
	public static void applyTheme(Font uiFont) //Apply theme for JCheckbox, JComboBox, JLabel,  JSrollPane, JTabbedPane, JTable
	{ // Need to figure out what key something is named? The stuff below will search and then exit for keys containing the string
		//	java.util.List<String> t=new java.util.ArrayList<String>(2048);
		//	for (Object key : javax.swing.UIManager.getLookAndFeelDefaults().keySet()) { t.add(key.toString()); }
		//	java.util.Collections.sort(t);
		//	for (String key : t ) { if (key.toLowerCase().contains("background")) { System.out.println(key); } }

		java.awt.Color backgroundColor=java.awt.Color.BLACK, foregroundColor=java.awt.Color.GREEN, accentColor=java.awt.Color.DARK_GRAY.darker().darker(), lightAccent=java.awt.Color.DARK_GRAY;//, dbugcolor=java.awt.Color.ORANGE;
		javax.swing.UIManager.put("Button.background", accentColor.brighter().brighter());
		javax.swing.UIManager.put("Button.darkShadow", backgroundColor);
		javax.swing.UIManager.put("Button.focus", accentColor.brighter().brighter()); //remove selection reticle
		javax.swing.UIManager.put("Button.font",uiFont);
		javax.swing.UIManager.put("Button.foreground", foregroundColor);
		javax.swing.UIManager.put("Button.select", java.awt.Color.GRAY.darker());
		javax.swing.border.Border b=new javax.swing.plaf.basic.BasicBorders.RolloverButtonBorder( lightAccent.brighter().brighter(),  lightAccent.brighter().brighter(), lightAccent.brighter().brighter(), lightAccent.brighter().brighter());
		javax.swing.UIManager.put("Button.border", new javax.swing.border.CompoundBorder(BorderFactory.createLineBorder(java.awt.Color.GRAY),b ));
		javax.swing.UIManager.put("CheckBox.foreground", foregroundColor);
		javax.swing.UIManager.put("CheckBox.background", backgroundColor);
		javax.swing.UIManager.put("CheckBox.focus", backgroundColor);
		javax.swing.UIManager.put("CheckBox.font", uiFont);
		javax.swing.UIManager.put("CheckBox.gradient", java.util.Arrays.asList( new Object[] {Float.valueOf(0f),Float.valueOf(0f), java.awt.Color.LIGHT_GRAY, java.awt.Color.LIGHT_GRAY, java.awt.Color.GRAY.brighter() }));
		//javax.swing.UIManager.put("CheckBox.icon", new AHACheckBoxIcon(13,13));
		javax.swing.UIManager.put("ComboBox.background", accentColor.brighter().brighter());
		javax.swing.UIManager.put("ComboBox.font", uiFont); 
		javax.swing.UIManager.put("ComboBox.foreground", foregroundColor);
		javax.swing.UIManager.put("ComboBox.selectionForeground", foregroundColor);
		javax.swing.UIManager.put("ComboBox.selectionBackground", accentColor.brighter().brighter()); 
		javax.swing.UIManager.put("ComboBox.border", BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(java.awt.Color.GRAY),BorderFactory.createLineBorder(accentColor.brighter().brighter(),2)));
		javax.swing.UIManager.put("ComboBoxUI", javax.swing.plaf.basic.BasicComboBoxUI.class.getName());
		javax.swing.UIManager.put("Label.foreground", foregroundColor);
		javax.swing.UIManager.put("Label.background", backgroundColor);
		javax.swing.UIManager.put("Label.font", uiFont);
		javax.swing.UIManager.put("Frame.foreground", foregroundColor);
		javax.swing.UIManager.put("Frame.background", backgroundColor);
		javax.swing.UIManager.put("Panel.foreground", foregroundColor);
		javax.swing.UIManager.put("Panel.background", backgroundColor);
		javax.swing.UIManager.put("ScrollBar.track", backgroundColor);
		javax.swing.UIManager.put("ScrollBar.thumbDarkShadow", backgroundColor);
		javax.swing.UIManager.put("ScrollBar.thumb", accentColor.brighter().brighter().brighter());
		javax.swing.UIManager.put("ScrollBar.thumbHighlight", accentColor.brighter().brighter().brighter());
		javax.swing.UIManager.put("ScrollBar.thumbShadow", accentColor.brighter().brighter().brighter());
		javax.swing.UIManager.put("ScrollBarUI", javax.swing.plaf.basic.BasicScrollBarUI.class.getName() );
		javax.swing.UIManager.put("ScrollBar.width", 8);
		javax.swing.UIManager.put("ScrollPane.foreground", foregroundColor);
		javax.swing.UIManager.put("ScrollPane.background", backgroundColor);
		javax.swing.UIManager.put("TabbedPane.foreground", foregroundColor);
		javax.swing.UIManager.put("TabbedPane.background", backgroundColor);
		javax.swing.UIManager.put("TabbedPane.light", backgroundColor);
		javax.swing.UIManager.put("TabbedPane.borderHightlightColor", backgroundColor);
		javax.swing.UIManager.put("TabbedPane.selected", accentColor.brighter().brighter().brighter());
		javax.swing.UIManager.put("TabbedPane.focus",accentColor.brighter().brighter().brighter());
		javax.swing.UIManager.put("TabbedPane.selectHighlight",accentColor.brighter().brighter().brighter());
		javax.swing.UIManager.put("TabbedPane.darkShadow", accentColor.brighter().brighter().brighter()); //removes difficult to see blue glow around inactive tab edge
		javax.swing.UIManager.put("TabbedPane.contentBorderInsets", new java.awt.Insets(0,0,0,0));
		javax.swing.UIManager.put("TabbedPane.tabsOverlapBorder", true); 
		javax.swing.UIManager.put("TableUI", javax.swing.plaf.basic.BasicTableUI.class.getName() );
		javax.swing.UIManager.put("Table.foreground", foregroundColor);
		javax.swing.UIManager.put("Table.background", backgroundColor);
		javax.swing.UIManager.put("Table.focusCellForeground", foregroundColor);
		javax.swing.UIManager.put("Table.focusCellBackground", backgroundColor);
		javax.swing.UIManager.put("Table.dropCellBackground", backgroundColor);
		javax.swing.UIManager.put("Table.gridColor", accentColor);
		javax.swing.UIManager.put("Table.font", uiFont); //
		javax.swing.UIManager.put("Table.selectionBackground", foregroundColor.darker());
		javax.swing.UIManager.put("Table.focusCellHighlightBorder", foregroundColor.darker());
		javax.swing.UIManager.put("Table.selectionForeground", backgroundColor);
		javax.swing.UIManager.put("Table.sortIconColor", foregroundColor); 
		javax.swing.UIManager.put("Table.ascendingSortIcon",new AHASortIcon(true));
		javax.swing.UIManager.put("Table.descendingSortIcon",new AHASortIcon(false));
		javax.swing.UIManager.put("TableHeaderUI", javax.swing.plaf.basic.BasicTableHeaderUI.class.getName() );
		javax.swing.UIManager.put("TableHeader.foreground", foregroundColor);
		javax.swing.UIManager.put("TableHeader.background", accentColor);
		javax.swing.UIManager.put("TableHeader.cellBorder", new javax.swing.border.BevelBorder(javax.swing.border.BevelBorder.RAISED));
		javax.swing.UIManager.put("TableHeader.font", uiFont); 
		javax.swing.UIManager.put("ToolTip.foreground", java.awt.Color.BLACK);
		javax.swing.UIManager.put("ToolTip.border", java.awt.Color.WHITE);
		javax.swing.UIManager.put("ToolTip.background", java.awt.Color.WHITE);
		javax.swing.UIManager.put("ToolTip.font", uiFont);
		javax.swing.UIManager.put("Viewport.foreground", foregroundColor);
		javax.swing.UIManager.put("Viewport.background", accentColor);
	}

	public static class AHASortIcon implements javax.swing.Icon
	{ //TODO maybe clean this up a little so it can be variably sized...but on the other hand, it looks good exactly with these numbers.
		boolean thingToDraw;
		public AHASortIcon(boolean icon) { thingToDraw=icon; }
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
			java.awt.Graphics2D g2 = (java.awt.Graphics2D)g;
			System.out.printf("paintIcon called x=%d y=%d\n",x,y);
			g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(java.awt.Color.GREEN);
			if (thingToDraw==true) { g2.fillPolygon(new int[]{x,x+6,x+3}, new int[]{y+5,y+5,y}, 3); }
			else { g2.fillPolygon(new int[]{x,x+6,x+3}, new int[]{y+1,y+1,y+6}, 3); }
		}
		public int getIconWidth() { return 6; }
		public int getIconHeight() { return 6; }
	}
	
	public static class AHACheckBoxIcon implements javax.swing.Icon
	{ //TODO maybe clean this up a little so it can be variably sized...but on the other hand, it looks good exactly with these numbers.
		private int width, height;
		public java.awt.Color foreground=java.awt.Color.GREEN, background=java.awt.Color.DARK_GRAY, highlight=java.awt.Color.GRAY;
		public java.awt.Font font=new java.awt.Font(java.awt.Font.MONOSPACED,java.awt.Font.BOLD,12);
		public java.awt.Stroke highlightStroke=new java.awt.BasicStroke(2);
		public AHACheckBoxIcon(int w, int h) { width=w; height=h; }
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
			java.awt.Graphics2D g2 = (java.awt.Graphics2D)g;
			boolean selected=false, rollover=false;
			try 
			{ 
				javax.swing.AbstractButton aButton=(javax.swing.AbstractButton)c;
				javax.swing.ButtonModel bModel=aButton.getModel(); 
				selected=bModel.isSelected();
				rollover=bModel.isRollover();
			} catch (Exception e) {}
			g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(background);
			g2.setFont(font);
			g2.fillRect(x, y, width, height); 
			if (selected) { g2.setColor(foreground); g2.drawString("√", x+3, y+height-1); }
			if (rollover)
			{ 
				g2.setColor(highlight);
				g2.setStroke(highlightStroke);
				g2.drawPolygon(new int[]{x,x+width,x+width,x}, new int[]{y,y,y+height,y+height}, 4);
			}
		}
		public int getIconWidth() { return width; }
		public int getIconHeight() { return height; }
	}

}
