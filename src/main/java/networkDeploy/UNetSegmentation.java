package networkDeploy;

import java.io.IOException;

import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.nn.modelimport.keras.KerasModelImport;
import org.deeplearning4j.nn.modelimport.keras.exceptions.InvalidKerasConfigurationException;
import org.deeplearning4j.nn.modelimport.keras.exceptions.UnsupportedKerasConfigurationException;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.io.ClassPathResource;

import cellTracking.ImageFunctions;
import cellTracking.ImageProcessorCalculator;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

public class UNetSegmentation {
	ComputationGraph model;
	
	public UNetSegmentation(String modelFile) throws IOException, UnsupportedKerasConfigurationException, InvalidKerasConfigurationException {
		setModel(modelFile);
	}
	
	public void setModel(String modelFile) throws IOException, UnsupportedKerasConfigurationException, InvalidKerasConfigurationException {
		String simpleMlp = new ClassPathResource(modelFile).getFile().getPath();		
		model = KerasModelImport.importKerasModelAndWeights(simpleMlp, false);	
		System.out.println("Model loaded");	
	}
	
	public ImageProcessor binarySegmentation(ImageProcessor ip, float threshold) throws IOException, InvalidKerasConfigurationException, UnsupportedKerasConfigurationException {
		ImageProcessor prediction = predict(ip);
		ImageProcessor binary = ImageFunctions.maskThresholdMoreThan(prediction, threshold, null);
		return binary;
	}

	public FloatProcessor predict(ImageProcessor ip) throws IOException, InvalidKerasConfigurationException, UnsupportedKerasConfigurationException {
		normalizeAndInvert((FloatProcessor) ip);		
		
		INDArray input = imageProcessor2INDArray(ip);
		INDArray[] prediction = model.output(input);
		INDArray singlePrediction = prediction[0];		
		ImageProcessor res = INDArray2ImageProcessor(singlePrediction);
		
		return res.convertToFloatProcessor();
	}

	private INDArray imageProcessor2INDArray(ImageProcessor ip) {
		int w = ip.getWidth();
		int h = ip.getHeight();
		/* Input shape is [minibatchSize, layerInputDepth, inputHeight, inputWidth] */
		INDArray res = Nd4j.zeros(1, 1, h, w);
		for (int y=0; y<h; ++y)
			for (int x=0; x<w; ++x) {
					res.putScalar(0,0,x,y, ip.getf(x, y));
			}
		return res;
	}
	
	private ImageProcessor INDArray2ImageProcessor(INDArray arr) {
		long[] shape = arr.shape();
		int w = (int) shape[3];
		int h = (int) shape[2];
		ImageProcessor ip = new FloatProcessor(w,h);
		for (int y=0; y<h; ++y)
			for (int x=0; x<w; ++x) {
				ip.putPixelValue(x, y, arr.getDouble(0, 0, x, y));
			}
		return ip;
	}
	
	private ImageProcessor patch(ImageProcessor ip, int x0, int y0, int width, int height) {
		ImageProcessor res = new FloatProcessor(width, height);
		for (int y=y0; y<y0+height; ++y)
			for (int x=x0; x<x0+width; ++x) {
				res.putPixelValue(x-x0, y-y0, ip.getf(x,y));
			}		
		return res;
	}
	
	private void normalizeAndInvert(FloatProcessor fp) {
		ImageFunctions.normalize(fp, 0, 1);
		for(int i=0; i<fp.getPixelCount(); ++i) {
			fp.setf(i, 1 - fp.getf(i));
		}
	}
	
	public void main(String[] args) {
		// test 
		String path = "C:\\Tokyo\\Confocal\\181221-q8156901-tiff\\c2\\181221-q8156901hfC2c2.tif";
		ImagePlus confocal_1 = IJ.openImage(path);
		
		ImageStack stack = confocal_1.getStack();
		ImageProcessor ip = stack.getProcessor(5);
		FloatProcessor fp = ip.convertToFloatProcessor();
		
		ImagePlus slice = new ImagePlus("Slice for Testing", fp);
		new ImageJ();
		slice.show();
		
		try {
			ImageProcessor segmented = predict(fp);
			ImagePlus res = new ImagePlus("Segmentation", segmented);
			res.show();
		} catch (IOException | InvalidKerasConfigurationException | UnsupportedKerasConfigurationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println("Finished");
	}
}
