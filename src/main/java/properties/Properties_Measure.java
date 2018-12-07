package properties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import org.apache.tools.ant.DirectoryScanner;

import cellTracking.ImageFunctions;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.GenericDialog;
import ij.io.DirectoryChooser;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import inra.ijpb.morphology.Strel;
import inra.ijpb.math.ImageCalculator;
import inra.ijpb.morphology.Morphology.Operation;

public class Properties_Measure implements PlugIn {
	
	//final ImageJ ij = new ImageJ();
	
	public static void main(String[] args) {

		Class<?> clazz = Properties_Measure.class;
		String url = clazz.getResource("/" + clazz.getName().replace('.', '/') + ".class").toString();
		String pluginsDir = url.substring("file:".length(),
				url.length() - clazz.getName().length() - ".class".length());
		System.setProperty("plugins.dir", pluginsDir);
		new ImageJ();

		IJ.runPlugIn(clazz.getName(), "Properties");
	}

	@Override
	public void run(String arg) {
		DirectoryChooser dirChoose = new DirectoryChooser("Select a folder with data");
		String dir;
		
		boolean selectDir = true;
		if (selectDir)
			dir = dirChoose.getDirectory();
		else
			dir = "C:\\Tokyo\\Data\\Properties Measure";

		try {
			DirectoryScanner scanner = new DirectoryScanner();
			scanner.setBasedir(dir);
			scanner.setCaseSensitive(false);

			scanner.setIncludes(new String[] { "*c1.tif" });
			scanner.scan();
			String[] files_ch1 = scanner.getIncludedFiles();
			if (files_ch1.length == 0)
				throw new Exception("No channel 1 tif image was found");

			scanner.setIncludes(new String[] { "*c2.tif" });
			scanner.scan();
			String[] files_ch2 = scanner.getIncludedFiles();
			if (files_ch2.length == 0)
				throw new Exception("No channel 2 tif image was found");

			scanner.setIncludes(new String[] { "*results.tif" });
			scanner.scan();
			String[] files_restif = scanner.getIncludedFiles();
			if (files_restif.length == 0)
				throw new Exception("No tracking result in CTC image format was found");

			scanner.setIncludes(new String[] { "*results.txt" });
			scanner.scan();
			String[] files_restxt = scanner.getIncludedFiles();
			if (files_restxt.length == 0)
				throw new Exception("No tracking result in CTC text format was found");

			IJ.log("Files found");
			System.out.println(files_ch1[0]);
			System.out.println(files_ch2[0]);
			System.out.println(files_restif[0]);
			System.out.println(files_restxt[0]);

			String ch1_name = files_ch1[0];
			String ch2_name = files_ch2[0];
			String restif_name = files_restif[0];
			String restxt_name = files_restxt[0];
			
			String[] split = ch2_name.split("c2");
			String name = split[0];
			System.out.println(name);

			ImagePlus imp_ch1 = new ImagePlus(dir + '\\' + ch1_name);
			ImagePlus imp_ch2 = new ImagePlus(dir + '\\' + ch2_name);
			ImagePlus imp_res = new ImagePlus(dir + '\\' + restif_name);

			/* Dialog to get background values */
			GenericDialog gd = new GenericDialog("Enter background values");
			gd.addNumericField("Channel 1 (405ex) background", 100, 0);
			gd.addNumericField("Channel 2 (480ex) background", 140, 0);
			gd.showDialog();
			double background0 = gd.getNextNumber();
			double background1 = gd.getNextNumber();			

			/* Subtract background values from channels */
			subtrackBackground(imp_ch1, (int) background0);
			subtrackBackground(imp_ch2, (int) background1);
//			imp_ch1.show();
//			imp_ch2.show();

			ImagePlus ratioImage = ratioImage(imp_ch1, imp_ch2);
//			ratioImage.show();

//			imp_res.show();

			// filling roi from ctc result image
			StackDetection stackDetection = new StackDetection();
			stackDetection.fillStack(imp_res);
			// stackDetection.makeRingRoi(0, 9, 3);
			stackDetection.changeDetectionsToRing(3);
//			stackDetection.show();

			System.out.println("Stack filled");
			IJ.log("Stack with ROIs filled");

			// fill tracks mapping
			TrackCTCMap tracksMap = new TrackCTCMap(dir + '\\' + restxt_name, stackDetection);
			System.out.println("Tracks map filled");
			IJ.log("Track information filled");

			FormatSaver format = new FormatSaver();
			IJ.log("Calculating sttistics...");
			format.calculate(tracksMap, stackDetection, imp_ch1, imp_ch2, ratioImage, dir, name);
			IJ.log("Done!");

		} catch (Exception e) {
			System.err.println(e.getMessage());
			e.printStackTrace();
		}
	}

	private void subtrackBackground(ImagePlus imp, int bg) {
		ImageStack stack = imp.getStack();
		for (int i = 0; i < stack.size(); ++i) {
			ImageProcessor ip = stack.getProcessor(i + 1);
			for (int x = 0; x < ip.getPixelCount(); ++x) {
				int v = ip.get(x) - bg;
				if (v < 0)
					v = 0;
				ip.set(x, v);
			}
		}
	}

	// ratio is channel1 / channel2 (405ex/480ex)
	private ImagePlus ratioImage(ImagePlus ch1, ImagePlus ch2) {
		ImageStack stack = ch1.getStack().convertToFloat();
		ImageStack stack0 = ch1.getStack(), stack1 = ch2.getStack();

		for (int i = 0; i < stack.getSize(); ++i) {
			ImageProcessor ip1 = stack1.getProcessor(i + 1);
			ImageProcessor ip = stack.getProcessor(i + 1);
			for (int px = 0; px < ip.getPixelCount(); ++px) {
				ip.setf(px, ip.getf(px) / ip1.getf(px));
			}
		}
		ImagePlus result = new ImagePlus("Ratio", stack);
		return result;
	}

	public ImagePlus makeRingDetections(ImagePlus ctcDetections, int dilationRadius) {
		ImageStack stack1 = ctcDetections.getStack().duplicate();
		for (int i = 0; i < stack1.size(); ++i) {
			ImageProcessor ip1 = stack1.getProcessor(i + 1);
			ImageProcessor ip2 = ctcDetections.getStack().getProcessor(i + 1);
			ip1 = ImageFunctions.operationMorph(ip1, Operation.DILATION, Strel.Shape.DISK, dilationRadius);
			ImageProcessor sub = ImageCalculator.combineImages(ip1, ip2, ImageCalculator.Operation.MINUS);
			ShortProcessor black = new ShortProcessor(ip1.getWidth(), ip1.getHeight());
			ImageProcessor ringed = ImageCalculator.combineImages(sub, black, ImageCalculator.Operation.MAX);
			stack1.setProcessor(ringed, i + 1);
		}
		ImagePlus result = new ImagePlus("", stack1);
		return result;
	}
}
