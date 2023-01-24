import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.ImageWindow;
import ij.gui.Roi;
import ij.io.FileInfo;
import ij.io.OpenDialog;
import ij.io.Opener;
import ij.measure.Calibration;

import java.awt.Button;
import java.awt.Checkbox;
import java.awt.Choice;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Label;
import java.awt.Rectangle;
import java.awt.TextField;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.TextEvent;
import java.awt.event.TextListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import javax.swing.Box;
import javax.swing.JLabel;
import javax.swing.ToolTipManager;

import ij.plugin.PlugIn;
import ij.plugin.frame.RoiManager;
import ij.process.ImageStatistics;
import ij.plugin.ImageCalculator;

/**
 * General Cell Counter
 * @author Yan Chastagnier
 */
public class General_Cell_Counter implements PlugIn {
	public void run(String arg0) {
		ToolTipManager.sharedInstance().setDismissDelay(30000);
		GCCProcess.getInstance().getFrame();
	}
	
	public static void main(String[] args) {
		// set the plugins.dir property to make the plugin appear in the Plugins menu
		Class<?> clazz = General_Cell_Counter.class;
		String url = clazz.getResource("/" + clazz.getName().replace('.', '/') + ".class").toString();
		String pluginsDir = url.substring("file:".length(), url.length() - clazz.getName().length() - ".class".length());
		System.setProperty("plugins.dir", pluginsDir);
		
		// create the ImageJ application context with all available services
		//final ImageJ ij = new ImageJ();
		new ImageJ();
		
		// run the plugin
		IJ.runPlugIn(clazz.getName(), "");
		//ij.quit();
	}
}

class GCCProcess implements ActionListener, ItemListener, TextListener, WindowListener, MouseListener {
	private static GCCProcess instance = null;
	private Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
	private double screenWidth = screenSize.getWidth();
	private double screenHeight = screenSize.getHeight();
	private double framePosX = ij.Prefs.get("GCC.framePosX", 0);
	private double framePosY = ij.Prefs.get("GCC.framePosY", 0);
	private double oriPosX = ij.Prefs.get("GCC.oriPosX", 0);
	private double oriPosY = ij.Prefs.get("GCC.oriPosY", 100);
	private double resPosX = ij.Prefs.get("GCC.resPosX", 500);
	private double resPosY = ij.Prefs.get("GCC.resPosY", 100);
	private double[] xCell = new double[0];
	private double[] yCell = new double[0];
	private double[] areaCell = new double[0];
	private int[] nucleusInCell = new int[0];
	private String[] nucleusInCellPos = new String[0];
	private boolean[] dupCell = new boolean[0];
	private Calibration cal;
	public static final String autoThCmd = getCommand("Auto_Threshold");
	
	private final String[] autoThresholdMethods = {"Default", "Huang", "Intermodes", "IsoData", "IJ_IsoData", "Li", "MaxEntropy", "Mean", "MinError",
													"Minimum", "Moments", "Otsu", "Percentile", "RenyiEntropy", "Shanbhag", "Triangle", "Yen"};
	private final String[] autoLocalThresholdMethods = {"Bernsen", "Contrast", "Mean", "Median", "MidGrey", "Niblack", "Otsu", "Phansalkar", "Sauvola"};

	private final String[] thresholdMethodList = {"Auto Threshold", "Auto Local Threshold", "Chastagnier Threshold"};
	private final String[] thresholdMethodLink = {"https://imagej.net/plugins/auto-threshold", "https://imagej.net/plugins/auto-local-threshold",
			"https://www.frontiersin.org/articles/10.3389/fncom.2017.00118/full"};
	// when adding new threshold method to the list, add case in itemStateChanged method to save/load parameter values
	// and add the method in the threshold method
	
	private int methodSelected = (int)ij.Prefs.get("GCC.methodSelected", 0);
	private int autoThMethodSelected = (int)ij.Prefs.get("GCC.autoThMethodSelected", 0);
	private int autoLocalThMethodSelected = (int)ij.Prefs.get("GCC.autoLocalThMethodSelected", 0);

	private ImagePlus ori, res;
	private ImageWindow oriWin, resWin;
	private String oriROIsPath, oriCellsPath;
	private Frame frame = null;
	private Button newImage = new Button("New image");
	private Button selectImage = new Button("Select current image");
	private Button showRegions = new Button("Display regions");
	private Button saveRegions = new Button("Save current regions");
	private Button saveResults = new Button("Save results");
	private JLabel methodLabel = new JLabel("                   Threshold Method ", JLabel.RIGHT);
	private Label autoThMethodLabel = new Label("Auto Threshold Method ", Label.RIGHT);
	private Label autoLocalThMethodLabel = new Label("Auto Local Threshold Method ", Label.RIGHT);
	private Choice method = new Choice();
	private Choice autoThMethod = new Choice();
	private Choice autoLocalThMethod = new Choice();
	private String methodParmStr = "";
	
	// Cell detection fields
	private final Label cellSizeLabel = new Label("Cell area range ", Label.RIGHT);
	private TextField cellSizeTxt = new TextField(ij.Prefs.get("GCC.cellSize", "1-100"));
	private final Label cellCircularityLabel = new Label("Cell circularity range ", Label.RIGHT);
	private TextField cellCircularityTxt = new TextField(ij.Prefs.get("GCC.cellCircularity", "0.00-1.00"));
	private final Label minDistanceLabel = new Label("Minimal distance ", Label.RIGHT);
	private TextField minDistanceTxt = new TextField(ij.Prefs.get("GCC.minDistance", "10"));

	// Auto Th fields
	private final Label gaussianSigmaAutoLabel = new Label("Gaussian high pass \u03C3 ", Label.RIGHT);
	private TextField gaussianSigmaAutoTxt = new TextField(String.valueOf(ij.Prefs.get("GCC.gaussianSigmaAuto", 10)));
	private final Label medianRadiusAutoLabel = new Label("Median filter radius (px) ", Label.RIGHT);
	private TextField medianRadiusAutoTxt = new TextField(String.valueOf(ij.Prefs.get("GCC.medianRadiusAuto", 3)));
	private Checkbox ignoreBlack = new Checkbox("ignore black", ij.Prefs.get("GCC.ignoreBlack", true));
	private Checkbox ignoreWhite = new Checkbox("ignore white", ij.Prefs.get("GCC.ignoreWhite", false));
	
	// Auto Local Th fields
	private final Label gaussianSigmaAutoLocLabel = new Label("Gaussian high pass \u03C3 ", Label.RIGHT);
	private TextField gaussianSigmaAutoLocTxt = new TextField(String.valueOf(ij.Prefs.get("GCC.gaussianSigmaAutoLoc", 10)));
	private final Label medianRadiusAutoLocLabel = new Label("Median filter radius (px) ", Label.RIGHT);
	private TextField medianRadiusAutoLocTxt = new TextField(String.valueOf(ij.Prefs.get("GCC.medianRadiusAutoLoc", 3)));
	private final Label localThRadiusLabel = new Label("Local Threshold radius (px) ", Label.RIGHT);
	private TextField localThRadiusTxt = new TextField(String.valueOf(ij.Prefs.get("GCC.localThRadius", 10)));
	// 0 parm : 1 (Contrast), 6 (Otsu)
	// 1 parm : 0 (Bernsen), 2 (Mean), 3 (Median), 4 (MidGrey)
	// 2 parm : 5 (Niblack), 7 (Phansalkar), 8 (Sauvola)
	private final Label localBernsenParm1Label = new Label("Contrast Threshold ", Label.RIGHT);
	private TextField localBernsenParm1Txt = new TextField(String.valueOf(ij.Prefs.get("GCC.localBernsenParm1", 0))); // Bernsen parm 1
	// Contrast 0 parm
	private final Label localMeanParm1Label = new Label("Offset ", Label.RIGHT);
	private TextField localMeanParm1Txt = new TextField(String.valueOf(ij.Prefs.get("GCC.localMeanParm1", 0))); // Mean parm 1
	private final Label localMedianParm1Label = new Label("Offset ", Label.RIGHT);
	private TextField localMedianParm1Txt = new TextField(String.valueOf(ij.Prefs.get("GCC.localMedianParm1", 0))); // Median parm 1
	private final Label localMidGreyParm1Label = new Label("Offset ", Label.RIGHT);
	private TextField localMidGreyParm1Txt = new TextField(String.valueOf(ij.Prefs.get("GCC.localMidGreyParm1", 0))); // MidGrey parm 1
	private final Label localNiblackParm1Label = new Label("k value ", Label.RIGHT);
	private TextField localNiblackParm1Txt = new TextField(String.valueOf(ij.Prefs.get("GCC.localNiblackParm1", 0))); // Niblack parm 1/2
	private final Label localNiblackParm2Label = new Label("Offset ", Label.RIGHT);
	private TextField localNiblackParm2Txt = new TextField(String.valueOf(ij.Prefs.get("GCC.localNiblackParm2", 0))); // Niblack parm 2/2
	// Otsu 0 parm
	private final Label localPhansalkarParm1Label = new Label("k value ", Label.RIGHT);
	private TextField localPhansalkarParm1Txt = new TextField(String.valueOf(ij.Prefs.get("GCC.localPhansalkarParm1", 0))); // Phansalkar parm 1/2
	private final Label localPhansalkarParm2Label = new Label("r value ", Label.RIGHT);
	private TextField localPhansalkarParm2Txt = new TextField(String.valueOf(ij.Prefs.get("GCC.localPhansalkarParm2", 0))); // Phansalkar parm 2/2
	private final Label localSauvolaParm1Label = new Label("k value ", Label.RIGHT);
	private TextField localSauvolaParm1Txt = new TextField(String.valueOf(ij.Prefs.get("GCC.localSauvolaParm1", 0))); // Sauvola parm 1/2
	private final Label localSauvolaParm2Label = new Label("r value ", Label.RIGHT);
	private TextField localSauvolaParm2Txt = new TextField(String.valueOf(ij.Prefs.get("GCC.localSauvolaParm2", 0))); // Sauvola parm 2/2
	
	// Chastagnier Threshold fields
	private final Label gaussianSigmaChast1Label = new Label("Gaussian high pass 1 \u03C3 ", Label.RIGHT);
	private TextField gaussianSigmaChast1Txt = new TextField(String.valueOf(ij.Prefs.get("GCC.gaussianSigmaChast1", 5)));
	private final Label gaussianSigmaChast2Label = new Label("Gaussian high pass 2 \u03C3 ", Label.RIGHT);
	private TextField gaussianSigmaChast2Txt = new TextField(String.valueOf(ij.Prefs.get("GCC.gaussianSigmaChast2", 15)));
	
	// Common fields
	private Checkbox channelCorrection = new Checkbox("Adjust to other channel", false);
	private final Label channelCorrectionLabel = new Label("Other channel number ", Label.RIGHT);
	private TextField channelCorrectionTxt = new TextField(String.valueOf(ij.Prefs.get("GCC.channelCorrection", "1")));
	private boolean doChanCorr = false;
	private Checkbox whiteBackground = new Checkbox("White background", ij.Prefs.get("GCC.whiteBackground", false));
	private Checkbox preview = new Checkbox("Preview", false);
	private TextField logTxt = new TextField("");
	
	private OpenDialog od;
	private ImageCalculator ic = new ImageCalculator();
	private ImageStatistics istat;
	
	protected GCCProcess() {}
	
	public static GCCProcess getInstance() {
		if (instance == null) {
			instance = new GCCProcess();
		}
		return instance;
	}
	
	public void getFrame() {
		if (frame == null) {
			if (method.getItemCount() == 0) {
				// filling Choices with their values
				for (int i = 0; i < thresholdMethodList.length; i++) {
					method.add(thresholdMethodList[i]);
				}
				method.select(methodSelected);
				method.addItemListener(this);
				methodLabel.addMouseListener(this);
				methodLabel.setForeground(Color.BLUE.darker());
				methodLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
				for (int i = 0; i < autoThresholdMethods.length; i++) {
					autoThMethod.add(autoThresholdMethods[i]);
				}
				autoThMethod.select(autoThMethodSelected);
				autoThMethod.addItemListener(this);
				for (int i = 0; i < autoLocalThresholdMethods.length; i++) {
					autoLocalThMethod.add(autoLocalThresholdMethods[i]);
				}
				autoLocalThMethod.select(autoLocalThMethodSelected);
				autoLocalThMethod.addItemListener(this);
				newImage.addActionListener(this);
				selectImage.addActionListener(this);
				showRegions.addActionListener(this);
				saveRegions.addActionListener(this);
				
				gaussianSigmaAutoTxt.addActionListener(this);
				gaussianSigmaAutoTxt.addTextListener(this);
				gaussianSigmaAutoLocTxt.addActionListener(this);
				gaussianSigmaAutoLocTxt.addTextListener(this);
				gaussianSigmaChast1Txt.addActionListener(this);
				gaussianSigmaChast1Txt.addTextListener(this);
				gaussianSigmaChast2Txt.addActionListener(this);
				gaussianSigmaChast2Txt.addTextListener(this);
				medianRadiusAutoTxt.addActionListener(this);
				medianRadiusAutoTxt.addTextListener(this);
				medianRadiusAutoLocTxt.addActionListener(this);
				medianRadiusAutoLocTxt.addTextListener(this);
				localThRadiusTxt.addActionListener(this);
				localThRadiusTxt.addTextListener(this);
				
				localBernsenParm1Txt.addActionListener(this);
				localBernsenParm1Txt.addTextListener(this);
				localMeanParm1Txt.addActionListener(this);
				localMeanParm1Txt.addTextListener(this);
				localMedianParm1Txt.addActionListener(this);
				localMedianParm1Txt.addTextListener(this);
				localMidGreyParm1Txt.addActionListener(this);
				localMidGreyParm1Txt.addTextListener(this);
				localNiblackParm1Txt.addActionListener(this);
				localNiblackParm1Txt.addTextListener(this);
				localNiblackParm2Txt.addActionListener(this);
				localNiblackParm2Txt.addTextListener(this);
				localPhansalkarParm1Txt.addActionListener(this);
				localPhansalkarParm1Txt.addTextListener(this);
				localPhansalkarParm2Txt.addActionListener(this);
				localPhansalkarParm2Txt.addTextListener(this);
				localSauvolaParm1Txt.addActionListener(this);
				localSauvolaParm1Txt.addTextListener(this);
				localSauvolaParm2Txt.addActionListener(this);
				localSauvolaParm2Txt.addTextListener(this);
				
				cellSizeTxt.addActionListener(this);
				cellSizeTxt.addTextListener(this);
				cellCircularityTxt.addActionListener(this);
				cellCircularityTxt.addTextListener(this);
				minDistanceTxt.addActionListener(this);
				minDistanceTxt.addTextListener(this);
				
				channelCorrection.addItemListener(this);
				channelCorrectionTxt.addActionListener(this);
				channelCorrectionTxt.addTextListener(this);
				
				whiteBackground.addItemListener(this);
				preview.addItemListener(this);
				saveResults.addActionListener(this);
			}
			
			frame = new Frame("General Cell Counter v1.0.5");
			frame.setLayout(new GridBagLayout());
			
			addThingContainer(frame, Box.createVerticalStrut(6),	0, 0,	1, 101,	0, 0,	6, 0);
			addThingContainer(frame, Box.createVerticalStrut(6),	3, 0,	1, 101,	0, 0,	6, 0);
			addThingContainer(frame, Box.createHorizontalStrut(6),	1, 0,	2, 1,	0, 0,	0, 6);
			addThingContainer(frame, newImage,						1, 1,	1, 1,	1, 1,	0, 0);
			addThingContainer(frame, selectImage,					2, 1,	1, 1,	1, 1,	0, 0);
			addThingContainer(frame, showRegions,					1, 2,	1, 1,	1, 1,	0, 0);
			addThingContainer(frame, saveRegions,					2, 2,	1, 1,	1, 1,	0, 0);
			addThingContainer(frame, methodLabel,					1, 3,	1, 1,	1, 1,	0, 0);
			addThingContainer(frame, method,						2, 3,	1, 1,	1, 1,	0, 0);
			addThingContainer(frame, Box.createHorizontalStrut(3),	1, 4,	2, 1,	0, 0,	0, 3);
			
			updateElementsFrame(methodSelected, true);
			
			addThingContainer(frame, Box.createHorizontalStrut(6),	1, 85,	2, 1,	0, 0,	0, 6);
			addThingContainer(frame, cellSizeLabel,					1, 86,	1, 1,	1, 1,	0, 0);
			addThingContainer(frame, cellSizeTxt,					2, 86,	1, 1,	1, 1,	0, 0);
			addThingContainer(frame, cellCircularityLabel,			1, 87,	1, 1,	1, 1,	0, 0);
			addThingContainer(frame, cellCircularityTxt,			2, 87,	1, 1,	1, 1,	0, 0);
			addThingContainer(frame, minDistanceLabel,				1, 88,	1, 1,	1, 1,	0, 0);
			addThingContainer(frame, minDistanceTxt,				2, 88,	1, 1,	1, 1,	0, 0);
			
			//addThingContainer(frame, channelCorrection,				2, 92,	2, 1,	1, 1,	0, 0);
			// updated at image opening or selection, see method toggleChannelAdjustment()
			// Label and TextField on line 93
			
			addThingContainer(frame, whiteBackground,				2, 97,	2, 1,	1, 1,	0, 0);
			addThingContainer(frame, preview,						2, 98,	2, 1,	1, 1,	0, 0);
			addThingContainer(frame, saveResults,					1, 99,	2, 1,	1, 1,	0, 0);
			addThingContainer(frame, logTxt,						1, 100,	2, 1,	1, 1,	0, 0);
			addThingContainer(frame, Box.createHorizontalStrut(6),	1, 101,	2, 1,	0, 0,	0, 6);
			
			frame.setLocation((int)framePosX, (int)framePosY+21);
	        frame.pack();
			//frame.setSize((int)frameSizeX, (int)frameSizeY);
	        frame.setVisible(true);
	        frame.addWindowListener(this);
		} else {
			frame.toFront();
		}
	}
	
	private void updateElementsFrame(int methodID, boolean add) {
		switch(methodID) {
		case 0:
			updateElementFrame(autoThMethodLabel,				1, 10,	1, 1,	1, 1,	0, 0, add);
			updateElementFrame(autoThMethod,					2, 10,	1, 1,	1, 1,	0, 0, add);
			updateElementFrame(gaussianSigmaAutoLabel,			1, 13,	1, 1,	1, 1,	0, 0, add);
			updateElementFrame(gaussianSigmaAutoTxt,			2, 13,	1, 1,	1, 1,	0, 0, add);
			updateElementFrame(medianRadiusAutoLabel,			1, 14,	1, 1,	1, 1,	0, 0, add);
			updateElementFrame(medianRadiusAutoTxt,				2, 14,	1, 1,	1, 1,	0, 0, add);
			updateElementFrame(ignoreBlack,						2, 16,	1, 1,	1, 1,	0, 0, add);
			updateElementFrame(ignoreWhite,						2, 17,	1, 1,	1, 1,	0, 0, add);
			break;
		case 1:
			updateElementFrame(autoLocalThMethodLabel,			1, 10,	1, 1,	1, 1,	0, 0, add);
			updateElementFrame(autoLocalThMethod,				2, 10,	1, 1,	1, 1,	0, 0, add);
			updateElementFrame(gaussianSigmaAutoLocLabel,		1, 13,	1, 1,	1, 1,	0, 0, add);
			updateElementFrame(gaussianSigmaAutoLocTxt,			2, 13,	1, 1,	1, 1,	0, 0, add);
			updateElementFrame(medianRadiusAutoLocLabel,		1, 14,	1, 1,	1, 1,	0, 0, add);
			updateElementFrame(medianRadiusAutoLocTxt,			2, 14,	1, 1,	1, 1,	0, 0, add);
			updateElementFrame(localThRadiusLabel,				1, 15,	1, 1,	1, 1,	0, 0, add);
			updateElementFrame(localThRadiusTxt,				2, 15,	1, 1,	1, 1,	0, 0, add);
			switch (autoLocalThMethodSelected) {
			case 0:
				updateElementFrame(localBernsenParm1Label,		1, 24,	1, 1,	1, 1,	0, 0, add);
				updateElementFrame(localBernsenParm1Txt,		2, 24,	1, 1,	1, 1,	0, 0, add);
				break;
			case 2:
				updateElementFrame(localMeanParm1Label,			1, 24,	1, 1,	1, 1,	0, 0, add);
				updateElementFrame(localMeanParm1Txt,			2, 24,	1, 1,	1, 1,	0, 0, add);
				break;
			case 3:
				updateElementFrame(localMedianParm1Label,		1, 24,	1, 1,	1, 1,	0, 0, add);
				updateElementFrame(localMedianParm1Txt,			2, 24,	1, 1,	1, 1,	0, 0, add);
				break;
			case 4:
				updateElementFrame(localMidGreyParm1Label,		1, 24,	1, 1,	1, 1,	0, 0, add);
				updateElementFrame(localMidGreyParm1Txt,		2, 24,	1, 1,	1, 1,	0, 0, add);
				break;
			case 5:
				updateElementFrame(localNiblackParm1Label,		1, 24,	1, 1,	1, 1,	0, 0, add);
				updateElementFrame(localNiblackParm1Txt,		2, 24,	1, 1,	1, 1,	0, 0, add);
				updateElementFrame(localNiblackParm2Label,		1, 25,	1, 1,	1, 1,	0, 0, add);
				updateElementFrame(localNiblackParm2Txt,		2, 25,	1, 1,	1, 1,	0, 0, add);
				break;
			case 7:
				updateElementFrame(localPhansalkarParm1Label,	1, 24,	1, 1,	1, 1,	0, 0, add);
				updateElementFrame(localPhansalkarParm1Txt,		2, 24,	1, 1,	1, 1,	0, 0, add);
				updateElementFrame(localPhansalkarParm2Label,	1, 25,	1, 1,	1, 1,	0, 0, add);
				updateElementFrame(localPhansalkarParm2Txt,		2, 25,	1, 1,	1, 1,	0, 0, add);
				break;
			case 8:
				updateElementFrame(localSauvolaParm1Label,		1, 24,	1, 1,	1, 1,	0, 0, add);
				updateElementFrame(localSauvolaParm1Txt,		2, 24,	1, 1,	1, 1,	0, 0, add);
				updateElementFrame(localSauvolaParm2Label,		1, 25,	1, 1,	1, 1,	0, 0, add);
				updateElementFrame(localSauvolaParm2Txt,		2, 25,	1, 1,	1, 1,	0, 0, add);
				break;
			}
			break;
		case 2:
			updateElementFrame(gaussianSigmaChast1Label,		1, 13,	1, 1,	1, 1,	0, 0, add);
			updateElementFrame(gaussianSigmaChast1Txt,			2, 13,	1, 1,	1, 1,	0, 0, add);
			updateElementFrame(gaussianSigmaChast2Label,		1, 14,	1, 1,	1, 1,	0, 0, add);
			updateElementFrame(gaussianSigmaChast2Txt,			2, 14,	1, 1,	1, 1,	0, 0, add);
			break;
		}
	}
	
	private void updateElementFrame(
		Component b, 
		int gridx, int gridy, 
		int gridwidth, int gridheight, 
		int weightx, int weighty, 
		int ipadx, int ipady,
		boolean add
	){
		if (add) {
			addThingContainer(frame, b, gridx, gridy, gridwidth, gridheight, weightx, weighty, ipadx, ipady);
		} else {
			frame.remove(b);
		}
	}
	
	private void addThingContainer(
		Container f, Component b, 
		int gridx, int gridy, 
		int gridwidth, int gridheight, 
		int weightx, int weighty, 
		int ipadx,int ipady
	){
		GridBagConstraints c = new GridBagConstraints();
		c.fill = GridBagConstraints.BOTH;
		c.gridx = gridx;
		c.gridy = gridy;
		c.gridwidth = gridwidth;
		c.gridheight = gridheight;
		c.weightx = weightx;
		c.weighty = weighty;
		c.ipadx = ipadx;
		c.ipady = ipady;
		f.add(b, c);
	}
	
	public void actionPerformed(ActionEvent e) {
		Object b = e.getSource();
		if (b == newImage) {
			closeOriRes();
			od = new OpenDialog("Select image to open.");
			String newPath = od.getPath();
			if (newPath != null) {
				String fileFormat = Opener.getFileFormat(newPath);
				boolean openClassic = !fileFormat.matches("unknown") && !fileFormat.matches("txt");
				if (openClassic) {
					ori = IJ.openImage(newPath);
					ImageWindow.setNextLocation((int)oriPosX, (int)oriPosY);
				} else {
					ImageWindow.setNextLocation((int)oriPosX, (int)oriPosY);
					IJ.run("Bio-Formats Importer", "open=["+newPath+
							"] color_mode=Default rois_import=[ROI manager] view=Hyperstack stack_order=XYCZT use_virtual_stack");
					ori = WindowManager.getCurrentImage();
				}
				if (ori == null) {
					logTxt.setText("Failed to open image.");
					return;
				}
				
				toggleChannelAdjustment();
				displayParmsUnits();
				
				if (openClassic) {
					ori.show();
				}
				oriWin = ori.getWindow();
				oriROIsPath = getPathExt(ori, "ROIs", "zip");
				openImageROIs(true);
			} else {
				logTxt.setText("Open image canceled");
				return;
			}
			if (preview.getState()) {
				preview.setState(false);
			}
		} else if (b == selectImage) {
			ImagePlus newImage = WindowManager.getCurrentImage();
			if (newImage == null) {
				logTxt.setText("No image to select");
				return;
			}
			if (newImage == ori) {
				logTxt.setText("Image already selected");
				return;
			}
			FileInfo fi = newImage.getOriginalFileInfo();
			if (fi == null) {
				logTxt.setText("Image must correspond to an opened file");
				return;
			}
			closeOriRes();
			ori = newImage;
			if (ori == null) {
				logTxt.setText("No image to select");
				return;
			}
			oriWin = ori.getWindow();
			toggleChannelAdjustment();
			displayParmsUnits();
			oriROIsPath = getPathExt(ori, "ROIs", "zip");
			openImageROIs(true);
			if (preview.getState()) {
				preview.setState(false);
			}
			logTxt.setText("Image selected.");
		} else if (b == showRegions) {
			showRegions();
		} else if (b == saveRegions) {
			saveRegions();
		} else if (b == saveResults) {
			saveResults();
		} else if (b instanceof TextField) {
			if (preview.getState()) {
				process();
			}
		}
	}
	
	private void toggleChannelAdjustment() {
		updateElementFrame(channelCorrection,	2, 92,	2, 1,	1, 1,	0, 0, ori.getNChannels() > 1);
		frame.pack();
	}

	private void displayParmsUnits() {
		cal = ori.getCalibration();
		if (cal.getUnit().matches(" ") || cal.getUnit().matches("pixel")) {
			cal.setUnit("px");
		} else if (cal.getUnit().matches("micron")) {
			cal.setUnit("\u03BCm");
		}
		cellSizeLabel.setText("Cell area range ("+cal.getUnit()+"\u00B2) ");
		gaussianSigmaAutoLabel.setText("Gaussian high pass \u03C3 ("+cal.getUnit()+") ");
		gaussianSigmaAutoLocLabel.setText("Gaussian high pass \u03C3 ("+cal.getUnit()+") ");
		gaussianSigmaChast1Label.setText("Gaussian high pass 1 \u03C3 ("+cal.getUnit()+") ");
		gaussianSigmaChast2Label.setText("Gaussian high pass 2 \u03C3 ("+cal.getUnit()+") ");
		minDistanceLabel.setText("Minimal distance ("+cal.getUnit()+") ");
		logTxt.setText("Pixel size: "+cal.pixelWidth+"x"+cal.pixelHeight+" "+cal.getUnit()+"\u00B2");
		
	}

	private boolean openImageROIs(boolean resetAndShow) {
		if (resetAndShow) {
			getRM().reset();
		}
		File f = new File(oriROIsPath);
		if (f.exists()) {
			getRM().runCommand("Open", oriROIsPath);
			if (resetAndShow) {
				getRM().runCommand(ori, "Show All with Labels");
			}
			return true;
		} else {
			return false;
		}
	}
	
	private void showRegions() { // clear ROI Manager and bring ori image to front
		if (ori == null) {
			logTxt.setText("Display regions requires an image.");
			return;
		}
		if (oriWin != null) {
			if (oriWin.isVisible()) {
				WindowManager.toFront(oriWin);
			} else {
				oriWin = null;
				ori = null;
				logTxt.setText("Display regions requires an image.");
				return;
			}
		} else {
			ori.show();
			oriWin = ori.getWindow();
		}
		if (preview.getState()) {
			preview.setState(false);
			if (res != null) {
				res.changes = false;
				res.close();
			}
		}
		if (openImageROIs(true)) {
			logTxt.setText("ROIs opened");
		} else {
			logTxt.setText("No ROIs file to open for current image.");
		}
	}
	
	private void saveRegions() {
		if (oriROIsPath != null) {
			if (getRM().getCount() > 0) {
				getRM().runCommand("Save", oriROIsPath);
				logTxt.setText("ROIs saved.");
			} else {
				logTxt.setText("No ROI to save.");
			}
		} else {
			logTxt.setText("No image to save regions for.");
		}
	}
	
	private void saveResults() {
		if (resWin != null && resWin.isVisible()) {
			res.changes = false;
			res.close();
		}
		if (!process()) return;
		preview.setState(true);
		String suffix = "";
		String fileNameSC = ori.getOriginalFileInfo().fileName;
		if (ori.getNChannels() > 1) {
			fileNameSC = fileNameSC+" c"+ori.getChannel();
			suffix = suffix +"_c"+ori.getChannel();
		}
		if (ori.getNSlices() > 1) {
			fileNameSC = fileNameSC+" s"+ori.getSlice();
			suffix = suffix +"_s"+ori.getSlice();
		}
		if (ori.getNFrames() > 1) {
			fileNameSC = fileNameSC+" f"+ori.getFrame();
			suffix = suffix +"_f"+ori.getFrame();
		}
		if (doChanCorr) {
			fileNameSC = fileNameSC+"_c"+channelCorrectionTxt.getText();
			suffix = suffix+"_c"+channelCorrectionTxt.getText();
		}
		fileNameSC = fileNameSC+";";
		oriCellsPath = getPathExt(ori, "Cells"+suffix, "zip");
		int nCells = getRM().getCount();
		if (nCells > 0) {
			getRM().runCommand("Save", oriCellsPath);
		}
		String flatPath = getPathExt(ori, suffix, "png");
		ImagePlus flat1 = res.flatten();
		if (!openImageROIs(true)) {									// open regions selected by user
			IJ.run(ori, "Select All", "");							// or full image if none saved
			getRM().addRoi(ori.getRoi());
		}
		for (int i = 0; i < getRM().getCount(); i++) {
			getRM().select(i);
			getRM().runCommand("Set Color", "green");			// set regions selected as green
		}
		getRM().runCommand(flat1, "Show All with Labels");
		ImagePlus flat2 = flat1.flatten();							// flatten them
		IJ.save(flat2, flatPath);
		flat1.close();
		flat2.close();
		getRM().reset();
		if (nCells > 0) {
			getRM().runCommand("Open", oriCellsPath);
		}
		if (!openImageROIs(false)) {
			IJ.run(ori, "Select All", "");
			getRM().addRoi(ori.getRoi());
		}
		getRM().runCommand(res, "Show All without Labels");
		int nROIs = getRM().getCount()-nCells;
		double[] areas = new double[nROIs];
		int[] nCellsIn = new int[nROIs];
		for (int iRoi = nCells; iRoi < nCells+nROIs; iRoi++) {
			getRM().select(iRoi);
			istat = res.getStatistics(ImageStatistics.AREA);
			areas[iRoi-nCells] = istat.area;
			Roi roi = getRM().getRoi(iRoi);
			int nInside = 0;
			for (int iCell = 0; iCell < nCells; iCell++) {
				if (!dupCell[iCell] && roi.contains((int)xCell[iCell], (int)yCell[iCell])) {
					nInside++;
				}
			}
			nCellsIn[iRoi-nCells] = nInside;
		}
		
		String resFile = ori.getOriginalFileInfo().directory+"GeneralCellCount"+File.separator+"GeneralCellCount.csv";
		String results;
		String[] previousResults = new String[0];
		File f = new File(resFile);
		int index = 1;
		boolean exist = f.exists();
		if (exist) {
			previousResults = removeLinesStartingWith(IJ.openAsString(resFile), fileNameSC).split("\n");
			results = previousResults[0]+ "\n";
			while (index < previousResults.length && previousResults[index].compareToIgnoreCase(fileNameSC) < 0) { 
				results = results + previousResults[index] + "\n";
				index++;
			}
		} else {
			results = "File name [position];ROI ID;Count;Area(unit2);Area(px2);Size Range;Circularity;MinDistance;Method;;;;;\n";
		}
		for (int iRoi = 0; iRoi < nROIs; iRoi++) {
			results = results + fileNameSC +(iRoi+1)+";"+nCellsIn[iRoi]+
					";"+areas[iRoi]+";"+(int)(areas[iRoi]/(cal.pixelWidth*cal.pixelHeight))+methodParmStr+"\n";
		}
		if (exist) {
			for (;index < previousResults.length; index++) {
				results = results + previousResults[index] + "\n";
			}
		}
		
		IJ.saveString(results, resFile);
		logTxt.setText("Results saved.");
	}
	
	private String removeLinesStartingWith(String str, String start) {
		String[] strArr = str.split("\n");
		String res = "";
		for (int i = 0; i < strArr.length; i++) {
			if (!strArr[i].startsWith(start)) {
				res = res + strArr[i]+"\n";
			}
		}
		return res;
	}
	
	public void itemStateChanged(ItemEvent e) {
		Object b = e.getSource();
		if (b == method) {
			updateElementsFrame(methodSelected, false);
			methodSelected = method.getSelectedIndex();
			updateElementsFrame(methodSelected, true);
			frame.pack();
		} else if (b == autoThMethod) {
			autoThMethodSelected = autoThMethod.getSelectedIndex();
		} else if (b == autoLocalThMethod) {
			updateElementsFrame(methodSelected, false);
			autoLocalThMethodSelected = autoLocalThMethod.getSelectedIndex();
			updateElementsFrame(methodSelected, true);
			frame.pack();
		} else if (b == channelCorrection) {
			boolean display = channelCorrection.getState();
			updateElementFrame(channelCorrectionLabel, 	1, 93,	1, 1,	1, 1,	0, 0, display);
			updateElementFrame(channelCorrectionTxt, 	2, 93,	1, 1,	1, 1,	0, 0, display);
			frame.pack();
		} else if (b == preview) {
		} else if (b == whiteBackground) {
		}
		if (preview.getState()) {
			process();
		} else {
			if (resWin != null && resWin.isVisible()) {
				resPosX = resWin.getLocation().getX();
				resPosY = resWin.getLocation().getY();
				ij.Prefs.set("GCC.resPosX", resPosX);
				ij.Prefs.set("GCC.resPosY", resPosY);
				res.changes = false;
				resWin.close();
			}
		}
	}
	
	private Boolean process() {
		try {
			if (ori == null || oriWin == null || !oriWin.isVisible()) {
				logTxt.setText("No image to process");
				return false;
			}
			IJ.run(ori, "Select None", "");
			res = ori.crop();
			if (whiteBackground.getState()) {
				IJ.run(res, "Invert", "");
			}
			methodParmStr = ";"+cellSizeTxt.getText()+";"+cellCircularityTxt.getText()+";"+minDistanceTxt.getText()+";"+method.getItem(methodSelected);
			switch(methodSelected) {
			case 0: // "Auto Threshold"
				double gaussianSigmaAuto = Double.parseDouble(gaussianSigmaAutoTxt.getText());
				double medianRadiusAuto = Double.parseDouble(medianRadiusAutoTxt.getText());
				if (gaussianSigmaAuto > 0) {
					ImagePlus res2 = res.duplicate();
					IJ.run(res, "Gaussian Blur...", "sigma="+gaussianSigmaAuto+" scaled");
					res = ic.run("Subtract create", res2, res);
				}
				if (medianRadiusAuto > 0) {
					IJ.run(res, "Median...", "radius="+medianRadiusAuto);
				}
				if (res.getBitDepth() != 8) {
					IJ.resetMinAndMax(res);
					IJ.run(res, "8-bit", "");
				}
				String ignoreBlackWhite = "";
				if (ignoreBlack.getState()) {ignoreBlackWhite += " ignore_black";}
				if (ignoreWhite.getState()) {ignoreBlackWhite += " ignore_white";}
				IJ.run(res, "Auto Threshold", "method="+autoThMethod.getSelectedItem()+ignoreBlackWhite+" white");
				methodParmStr=methodParmStr+";GaussianSigma:"+gaussianSigmaAuto+";MedianRadius:"+medianRadiusAuto
						+";AutoThMethod:"+autoThMethod.getSelectedItem()+";;;";
				break;
			case 1: // "Auto Local Threshold"
				double gaussianSigmaAutoLoc = Double.parseDouble(gaussianSigmaAutoLocTxt.getText());
				double medianRadiusAutoLoc = Double.parseDouble(medianRadiusAutoLocTxt.getText());
				double localThRadius = Double.parseDouble(localThRadiusTxt.getText());
				if (gaussianSigmaAutoLoc > 0) {
					ImagePlus res2 = res.duplicate();
					IJ.run(res, "Gaussian Blur...", "sigma="+gaussianSigmaAutoLoc+" scaled");
					res = ic.run("Subtract create", res2, res);
				}
				if (medianRadiusAutoLoc > 0) {
					IJ.run(res, "Median...", "radius="+medianRadiusAutoLoc);
				}
				if (res.getBitDepth() != 8) {
					IJ.resetMinAndMax(res);
					IJ.run(res, "8-bit", "");
				}
				double localParm1 = 0, localParm2 = 0;
				methodParmStr=methodParmStr+";GaussianSigma:"+gaussianSigmaAutoLoc+";MedianRadius:"+medianRadiusAutoLoc
						+";LocalThRadius:"+localThRadius+";LocalThMethod:"+autoLocalThMethod.getSelectedItem();
				switch(autoLocalThMethod.getSelectedIndex()) {
				case 0:
					localParm1 = Double.parseDouble(localBernsenParm1Txt.getText());
					methodParmStr=methodParmStr+";ContrastTh:"+localBernsenParm1Txt.getText()+";";
					break;
				case 2:
					localParm1 = Double.parseDouble(localMeanParm1Txt.getText());
					methodParmStr=methodParmStr+";Offset:"+localMeanParm1Txt.getText()+";";
					break;
				case 3:
					localParm1 = Double.parseDouble(localMedianParm1Txt.getText());
					methodParmStr=methodParmStr+";Offset:"+localMedianParm1Txt.getText()+";";
					break;
				case 4:
					localParm1 = Double.parseDouble(localMidGreyParm1Txt.getText());
					methodParmStr=methodParmStr+";Offset:"+localMidGreyParm1Txt.getText()+";";
					break;
				case 5:
					localParm1 = Double.parseDouble(localNiblackParm1Txt.getText());
					localParm2 = Double.parseDouble(localNiblackParm2Txt.getText());
					methodParmStr=methodParmStr+";k value:"+localNiblackParm1Txt.getText()+";Offset:"+localNiblackParm2Txt.getText();
					break;
				case 7:
					localParm1 = Double.parseDouble(localPhansalkarParm1Txt.getText());
					localParm2 = Double.parseDouble(localPhansalkarParm2Txt.getText());
					methodParmStr=methodParmStr+";k value:"+localPhansalkarParm1Txt.getText()+";r value:"+localPhansalkarParm2Txt.getText();
					break;
				case 8:
					localParm1 = Double.parseDouble(localSauvolaParm1Txt.getText());
					localParm2 = Double.parseDouble(localSauvolaParm2Txt.getText());
					methodParmStr=methodParmStr+";k value:"+localSauvolaParm1Txt.getText()+";r value:"+localSauvolaParm2Txt.getText();
					break;
				default:
					methodParmStr=methodParmStr+";;";
					break;
				}
				IJ.run(res, "Auto Local Threshold", "method="+autoLocalThMethod.getSelectedItem()+" radius="+localThRadius+
						" parameter_1="+localParm1+" parameter_2="+localParm2+" white");
				break;
			case 2: // Chastagnier Threshold
				ImagePlus gaussianLow = res.duplicate();
				IJ.run(gaussianLow, "Gaussian Blur...", "sigma="+Double.parseDouble(gaussianSigmaChast1Txt.getText())+" scaled");
				gaussianLow = ic.run("Subtract create stack", res, gaussianLow);
				IJ.run(gaussianLow, GCCProcess.autoThCmd, "method=Li white stack");
				ImagePlus gaussianHigh = res.duplicate();
				IJ.run(gaussianHigh, "Gaussian Blur...", "sigma="+Double.parseDouble(gaussianSigmaChast2Txt.getText())+" scaled");
				gaussianHigh = ic.run("Subtract create stack", res, gaussianHigh);
				IJ.run(gaussianHigh, GCCProcess.autoThCmd, "method=Li white stack");
				ic.run("AND stack", gaussianLow, gaussianHigh);
				IJ.run(res, GCCProcess.autoThCmd, "method=Otsu ignore_black ignore_white white stack");
				ic.run("OR stack", res, gaussianLow);
				methodParmStr=methodParmStr+";GaussianSigma1:"+gaussianSigmaChast1Txt.getText()+";GaussianSigma2:"+gaussianSigmaChast2Txt.getText()+";;;;";
				break;
			default:
				logTxt.setText("Method doesn't exist. Process canceled.");
				return false;
			} // end of switch
			
			String[] cellSizeParts = cellSizeTxt.getText().split("-");
			String cellSizeSearch = cellSizeTxt.getText();
			doChanCorr = channelCorrection.getState();
			String oriChanCorrCellsPath = "";
			double[][] refCoords = new double[0][0];
			if (doChanCorr) {
				String suffixChanCorr = "";
				if (ori.getNChannels() > 1) {
					suffixChanCorr = suffixChanCorr+"_c"+channelCorrectionTxt.getText();
				}
				if (ori.getNSlices() > 1) {
					suffixChanCorr = suffixChanCorr +"_s"+ori.getSlice();
				}
				if (ori.getNFrames() > 1) {
					suffixChanCorr = suffixChanCorr+"_f"+ori.getFrame();
				}
				oriChanCorrCellsPath = getPathExt(ori, "Cells"+suffixChanCorr, "zip");
				File f = new File(oriChanCorrCellsPath);
				if (!f.exists()) {
					logTxt.setText("Incorrect channel number or zip file doesn't exist.");
					return false;
				} else {
					if (WindowManager.getActiveWindow() != oriWin) { // if it is not the active window, the getStatistics that follows is slow and wrong
						WindowManager.setCurrentWindow(oriWin);
					}
					cellSizeSearch = cellSizeParts[0];
					getRM().reset();
					getRM().runCommand("Open", oriChanCorrCellsPath);
					int cellCount = getRM().getCount();
					refCoords = new double[cellCount][7];
					for (int i = 0; i < cellCount; i++) {
						getRM().select(i);
						istat = ori.getStatistics(ImageStatistics.CENTROID+ImageStatistics.RECT+ImageStatistics.AREA);
						refCoords[i][0] = istat.xCentroid/cal.pixelWidth; // convert to uncalibrated data
						refCoords[i][1] = istat.yCentroid/cal.pixelHeight;
						refCoords[i][2] = istat.roiY/cal.pixelHeight;
						refCoords[i][3] = (istat.roiY+istat.roiHeight)/cal.pixelHeight;
						refCoords[i][4] = istat.roiX/cal.pixelWidth;
						refCoords[i][5] = (istat.roiX+istat.roiWidth)/cal.pixelWidth;
						refCoords[i][6] = istat.area;
					}
				}
			}
			getRM().reset();
			IJ.run(res, "Analyze Particles...", "size="+cellSizeSearch+" circularity="+cellCircularityTxt.getText()+" display clear add");
			
			if (resWin == null || !resWin.isVisible()) {
				ImageWindow.setNextLocation((int)resPosX, (int)resPosY);
				res.show();
				resWin = res.getWindow();
			} else {
				if (WindowManager.getActiveWindow() != resWin) { // if it is not the active window, the getStatistics that follows is slow and wrong
					WindowManager.setCurrentWindow(resWin);
				}
				double magnification = resWin.getCanvas().getMagnification();
				Rectangle rect = resWin.getCanvas().getSrcRect();
				resWin.setImage(res);
				resWin.getCanvas().setSourceRect(rect);
				resWin.getCanvas().setMagnification(magnification);
			}
			IJ.run("Remove Overlay", "");
			
			int nCells = getRM().getCount();
			xCell = new double[nCells];
			yCell = new double[nCells];
			areaCell = new double[nCells];
			nucleusInCell = new int[nCells];
			nucleusInCellPos = new String[nCells];
			if (doChanCorr) {
				getRM().runCommand("Open", oriChanCorrCellsPath);
			}
			int searchStart = 0;
			int searchIndex = 0;
			int searchStop = refCoords.length;
			for (int iCell = 0; iCell < nCells; iCell++) {
				getRM().select(iCell);
				istat = res.getStatistics(ImageStatistics.CENTROID+ImageStatistics.AREA);
				xCell[iCell] = istat.xCentroid/cal.pixelWidth; // divide by calibration to get pixel values
				yCell[iCell] = istat.yCentroid/cal.pixelHeight;
				areaCell[iCell] = istat.area; // calibrated area
				if (doChanCorr) {
					//Roi r = getRM().getRoi(iCell);
					nucleusInCell[iCell] = 0;
					nucleusInCellPos[iCell] = "";
					istat = res.getStatistics(ImageStatistics.RECT);
					while (searchStart < searchStop && refCoords[searchStart][3] < istat.roiY/cal.pixelHeight) {
						searchStart++;
					}
					searchIndex = searchStart;
					double xStart = istat.roiX/cal.pixelWidth;
					double xStop = (istat.roiX+istat.roiWidth)/cal.pixelWidth;
					double yStop = (istat.roiY+istat.roiHeight)/cal.pixelHeight;
					while (searchIndex < searchStop && refCoords[searchIndex][2] < yStop) {
						if (!(refCoords[searchIndex][5] < xStart || refCoords[searchIndex][4] > xStop)) {
							getRM().setSelectedIndexes(new int[]{iCell, searchIndex+nCells});
							getRM().runCommand(res, "AND");
							if (res.getRoi() != null) {
								istat = res.getStatistics(ImageStatistics.AREA);
								if (istat.area/refCoords[searchIndex][6] >= 0.5) { // if half the nucleus is inside the cell
								//if (r.contains((int)refCoords[searchIndex][0], (int)refCoords[searchIndex][1])) {
									nucleusInCell[iCell]++;
									nucleusInCellPos[iCell]+=Integer.toString((int)refCoords[searchIndex][0])+";"+Integer.toString((int)refCoords[searchIndex][1])+";";
								}
							}
						}
						searchIndex++;
					}
					if (nucleusInCellPos[iCell].endsWith(";")) { // remove the potential extra ;
						nucleusInCellPos[iCell] = nucleusInCellPos[iCell].substring(0, nucleusInCellPos[iCell].length()-1);
					}
				}
			}
			
			if (doChanCorr) {
				for (int i = getRM().getCount()-1; i >= nCells; i--) {
					getRM().select(nCells);
					getRM().runCommand("Delete");
				}
				int nRemoved = 0;
				int nDup = 0;
				double minArea = Double.parseDouble(cellSizeParts[0]);
				double maxArea;
				if (cellSizeParts.length > 1) {
					maxArea = Double.parseDouble(cellSizeParts[1]);
				} else {
					maxArea = Double.MAX_VALUE;
				}
				double meanArea;
				boolean removeROI;
				for (int i = 0; i < nCells; i++) {
					removeROI = false;
					nDup = 0;
					if (nucleusInCell[i] == 0) {
						removeROI = true;
					} else {
						meanArea = areaCell[i]/(double)nucleusInCell[i];
						if (meanArea > maxArea) {
							removeROI = true;
						} else if (meanArea < minArea) {
							nDup = (int)Math.floor(areaCell[i]/minArea)-1;
						} else {
							nDup = nucleusInCell[i]-1;
						}
					}
					if (removeROI) {
						getRM().select(i-nRemoved);
						getRM().runCommand("Delete");
						nRemoved++;
					} else {
						for (int j = 0; j < nDup; j++) {
							getRM().addRoi(getRM().getRoi(i-nRemoved));
							getRM().rename(getRM().getCount()-1, getRM().getName(i-nRemoved));
						}
					}
					//System.out.println(""+i+" "+nRemoved+" "+nDup+" "+nucleusInCell[i]);
				}
				getRM().runCommand("Sort");
			}
			
			// comment faire pour afficher les noyaux qui sont dans des cellules ? -> ne pas le faire
			// comment compter le vrai nombre de cellules qui ne correspond ni au nombre de cellules, ni au nombre de noyaux... ? -> dupliquer les ROIs
			// quand il y a plusieurs noyaux, comme ça ça correspond au nombre de ROIs
			// et si une cellule comporte plusieurs noyaux mais que du coup sa taille passe en dessous du seuil, la virer, la compter comme une ?
			// -> 1 ou floor(area / min_area)
			dupCell = areCellDuplicates(xCell, yCell, areaCell, Double.parseDouble(minDistanceTxt.getText()));
			for (int iCell = 0; iCell < nCells; iCell++) {
				getRM().select(iCell);
				if (dupCell[iCell]) {
					getRM().runCommand("Set Fill Color", "yellow");
				} else {
					getRM().runCommand("Set Fill Color", "red");
				}
			}
			getRM().runCommand(res, "Show All without labels");
			logTxt.setText("Preview displayed. "+getRM().getCount()+" object(s) detected.");
			return true;
		} catch (NumberFormatException ex) {
			logTxt.setText("Parameter is not a number");
			return false;
		}
	}
	
	private boolean[] areCellDuplicates(double[] xPos, double[] yPos, double[] areaCell, double distance) {
		if (xPos.length != yPos.length) return null;
		boolean[] isDuplicate = new boolean[xPos.length];
		if (distance > 0 && !doChanCorr) { // disabled if distance is zero or channel correction is enabled
			for (int i = 0; i < xPos.length; i++) {
				int j = i+1;
				while (j < xPos.length && yPos[j]-yPos[i] <= distance) { // compare only with ROIs in the distance lines range
					if (Math.abs(xPos[j]-xPos[i]) <= distance) { // compare only with ROIs in the range [pos-distance; pos+distance]
						if (Math.sqrt(Math.pow(xPos[j]-xPos[i], 2)+Math.pow(yPos[j]-yPos[i], 2)) <= distance) {
							if (areaCell[i] > areaCell[j]) {
								isDuplicate[j] = true;
							} else {
								isDuplicate[i] = true;
							}
						}
					}
					j++;
				}
				
			}
		}
		return isDuplicate;
	}
	
	private RoiManager getRM() {
		return getRM(true);
	}
	
	private RoiManager getRM(boolean open) {
		RoiManager rm = RoiManager.getInstance();
		if (!open && rm != null) {
			rm.close();
		}
		if (open && rm == null) {
			rm = RoiManager.getRoiManager();
		}
		return rm;
	}
	
	public void textValueChanged(TextEvent e) {
		//Object b = e.getSource();
		logTxt.setText("Parameter changed");
	}
	
	public void windowOpened(WindowEvent e) {}
	public void windowClosing(WindowEvent e) {
		ij.Prefs.set("GCC.methodSelected", methodSelected);
		ij.Prefs.set("GCC.autoThMethodSelected", autoThMethodSelected);
		ij.Prefs.set("GCC.gaussianSigmaAuto", gaussianSigmaAutoTxt.getText());
		ij.Prefs.set("GCC.gaussianSigmaAutoLoc", gaussianSigmaAutoLocTxt.getText());
		ij.Prefs.set("GCC.gaussianSigmaChast1", gaussianSigmaChast1Txt.getText());
		ij.Prefs.set("GCC.gaussianSigmaChast2", gaussianSigmaChast2Txt.getText());
		ij.Prefs.set("GCC.medianRadiusAuto", medianRadiusAutoTxt.getText());
		ij.Prefs.set("GCC.medianRadiusAutoLoc", medianRadiusAutoLocTxt.getText());
		ij.Prefs.set("GCC.autoLocalThMethodSelected", autoLocalThMethodSelected);
		ij.Prefs.set("GCC.localThRadius", localThRadiusTxt.getText());
		ij.Prefs.set("GCC.localBernsenParm1", localBernsenParm1Txt.getText());
		ij.Prefs.set("GCC.localMeanParm1", localMeanParm1Txt.getText());
		ij.Prefs.set("GCC.localMedianParm1", localMedianParm1Txt.getText());
		ij.Prefs.set("GCC.localMidGreyParm1", localMidGreyParm1Txt.getText());
		ij.Prefs.set("GCC.localNiblackParm1", localNiblackParm1Txt.getText());
		ij.Prefs.set("GCC.localNiblackParm2", localNiblackParm2Txt.getText());
		ij.Prefs.set("GCC.localPhansalkarParm1", localPhansalkarParm1Txt.getText());
		ij.Prefs.set("GCC.localPhansalkarParm2", localPhansalkarParm2Txt.getText());
		ij.Prefs.set("GCC.localSauvolaParm1", localSauvolaParm1Txt.getText());
		ij.Prefs.set("GCC.localSauvolaParm2", localSauvolaParm2Txt.getText());
		ij.Prefs.set("GCC.cellSize", cellSizeTxt.getText());
		ij.Prefs.set("GCC.cellCircularity", cellCircularityTxt.getText());
		ij.Prefs.set("GCC.minDistance", minDistanceTxt.getText());
		ij.Prefs.set("GCC.channelCorrection", channelCorrectionTxt.getText());
		closeOriRes();
		getRM(false);
		Window w = WindowManager.getWindow("Results");
		if (w != null) {
			w.dispose();
		}
		framePosX = frame.getLocation().getX();
		framePosY = frame.getLocation().getY();
		ij.Prefs.set("GCC.framePosX", framePosX);
		ij.Prefs.set("GCC.framePosY", framePosY);
		frame.dispose();
		frame = null;
	}
	public void windowClosed(WindowEvent e) {}
	public void windowIconified(WindowEvent e) {}
	public void windowDeiconified(WindowEvent e) {}
	public void windowActivated(WindowEvent e) {}
	public void windowDeactivated(WindowEvent e) {}
	public void mouseClicked(MouseEvent e) {
		try {
			if (e.getSource() == methodLabel) {
				Desktop.getDesktop().browse(new URI(thresholdMethodLink[methodSelected]));
			}
		} catch (IOException e1) {
			e1.printStackTrace(); // Auto-generated catch block
		} catch (URISyntaxException e1) {
			e1.printStackTrace(); // Auto-generated catch block
		}
	}
	public void mousePressed(MouseEvent e) {}
	public void mouseReleased(MouseEvent e) {}
	public void mouseEntered(MouseEvent e) {}
	public void mouseExited(MouseEvent e) {}
	
	private String getPathExt(ImagePlus imp, String str, String ext) {
		String folder = imp.getOriginalFileInfo().directory+"GeneralCellCount"+File.separator;
		File f = new File(folder);
		if (!f.exists()) {
			f.mkdir();
		}
		String fileName = imp.getOriginalFileInfo().fileName;
		String ending;
		if (str.matches("")) {
			ending = "."+ext;
		} else if (str.startsWith("_")) {
			ending = str+"."+ext; 
		} else {
			ending = "_"+str+"."+ext;
		}
		int index = fileName.lastIndexOf('.');
		if (index > 0) {
			fileName = fileName.substring(0, index);
		}
		return folder+fileName+ending;
	}
	
	private void closeOriRes() {
		if (ori != null) {
			if (oriWin != null) {
				oriPosX = oriWin.getLocation().getX();
				oriPosY = oriWin.getLocation().getY();
				ij.Prefs.set("GCC.oriPosX", oriPosX);
				ij.Prefs.set("GCC.oriPosY", oriPosY);
			}
			ori.changes = false;
			ori.close();
			ori = null;
		}
		if (res != null) {
			if (resWin != null) {
				resPosX = resWin.getLocation().getX();
				resPosY = resWin.getLocation().getY();
				ij.Prefs.set("GCC.resPosX", resPosX);
				ij.Prefs.set("GCC.resPosY", resPosY);
			}
			res.changes = false;
			res.close();
			res = null;
		}
	}
	
	public static String replaceLast(String string, String substring, String replacement) {
		int index = string.lastIndexOf(substring);
		if (index == -1) {
			return "";
		} else {
			return string.substring(0, index) + replacement + string.substring(index+substring.length());
		}
	}
	
	public void showAtLoc(ImagePlus imp, double normalizedX, double normalizedY) {
		ImageWindow.setNextLocation((int)(normalizedX*screenWidth), (int)(normalizedY*screenHeight));
		imp.show();
	}
	
	private static String getCommand(String cmdVal) {
		String res = IJ.runMacro("str = getArgument(); List.setCommands; List.toArrays(keys, values);"+
		"for (i = 0; i < values.length; i++) {if (endsWith(values[i], str)) {return keys[i];}}", cmdVal);
		if (res == null) {
			res = cmdVal+" plugin not found.\nInstall the plugin and restart the application to use it.";
		}
		return res;
	}
}


