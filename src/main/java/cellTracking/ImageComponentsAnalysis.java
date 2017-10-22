package cellTracking;

import java.util.ArrayList;
import java.util.Vector;

import ij.ImagePlus;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.gui.Wand;
import ij.plugin.frame.RoiManager;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import inra.ijpb.binary.BinaryImages;
import inra.ijpb.morphology.Strel;
import inra.ijpb.morphology.Morphology.Operation;

public class ImageComponentsAnalysis {
	private ImageProcessor imageComponents;	//image with components
	private ImageProcessor imageIntensity; //image for calculating average intensity
	private int w,h;
	
	private int nComponents;	//number of components
	private ArrayList<ComponentProperties> properties;	//component properties
	
	/* initialize class from binary image ip with components and intensity image "intensityImage" */
	public ImageComponentsAnalysis(ImageProcessor ip, ImageProcessor intensityImage) {
		w = ip.getWidth();
		h = ip.getHeight();
		//imageComponents = ip.duplicate();
		imageComponents = BinaryImages.componentsLabeling(ip, 4, 16);
		//imageComponents = ImageFunctions.operationMorph(imageComponents, Operation.CLOSING, Strel.Shape.DISK, 1);
		//ImagePlus t = new ImagePlus("after closing", imageComponents.duplicate());
		//t.show();
		imageIntensity = intensityImage;
		nComponents = (int)imageComponents.getMax() - (int)imageComponents.getMin() + 1; // components are labelled from 0 to the number of components
		properties = new ArrayList<ComponentProperties>(nComponents);
		for (int i=0; i<nComponents; i++)
			properties.add(new ComponentProperties());
		fillBasicProperties();
		fillCircularity();
	}
	
	// getters by index
	public int getComponentArea(int index) {
		return properties.get(index).area;
	}
	
	public float getComponentPerimeter(int index) {
		return properties.get(index).perimeter;
	}
	
	public float getComponentCircularity(int index) {
		return properties.get(index).circularity;
	}
	
	public int getComponentIntensity(int index) {
		return properties.get(index).intensity;
	}
	
	public int getComponentX0(int index) {
		return properties.get(index).xmin;
	}
	
	public int getComponentY0(int index) {
		return properties.get(index).ymin;
	}
	
	public float getComponentMassCenterX(int index) {
		return properties.get(index).massCenterX;
	}
	
	public float getComponentMassCenterY(int index) {
		return properties.get(index).massCenterY;
	}
	
	public float getComponentAvrgIntensityByIntensity(int intensity) {
		int index = findComponentIndexByIntensity(intensity);
		return properties.get(index).avrgIntensity;
	}
	
	public int getComponentsCount() {
		return nComponents;
	}
	
	public void testFunc(ImageComponentsAnalysis cl) {
		
	}
	
	/* calculates bounding box corners, perimeter, area, average intensity, mass center for components and fills the "properties" array */
	public void fillBasicProperties() {
		// presetting values to find containing rectangle
		for (int i=0; i < properties.size(); i++) {
			properties.get(i).xmin = w - 1;
			properties.get(i).xmax = 0;
			properties.get(i).ymin = h - 1;
			properties.get(i).ymax = 0;
			properties.get(i).intensity = -1;
			properties.get(i).area = 0;
			properties.get(i).avrgIntensity = 0;
			properties.get(i).circularity = 0;
			properties.get(i).perimeter = 0;
			properties.get(i).massCenterX = 0; 
			properties.get(i).massCenterY = 0; 
		}
		
		int v; // component intensity in the image
		int newIndex = 0; //new component index
		int index; 		//current index
		int pix4c, pixDc;
		for (int y=0; y < h; y++) {
			for (int x=0; x < w; x++) {
				v = imageComponents.get(x, y);
				// components can be indexed in whatever range (but no negatives), dont rely on v=0 to be background. And make background a component
				//if (v > 0) { 
				index = findComponentIndexByIntensity(v);
				if (index == -1)
					index = ++newIndex;

				properties.get(index).intensity = v;	
				properties.get(index).area++;
				properties.get(index).avrgIntensity += imageIntensity.getf(x, y);
				properties.get(index).massCenterX += x;
				properties.get(index).massCenterY += y;

				if (isBorderPixel4C(imageComponents, x, y)) { //calculate perimeter
					pix4c = numberOfNeighbours4C(imageComponents, x, y);
					pixDc = numberOfNeighboursDiagC(imageComponents, x, y);
					properties.get(index).perimeter += (float) ((pix4c*1.0f + pixDc * Math.sqrt(2)) / (pix4c + pixDc));
				}
				//if (hasDiagonalBorderNeighbours(image, x, y)) properties.get(v).perimeter += Math.sqrt(2);
				//else properties.get(v).perimeter++;

				if (properties.get(index).xmin > x) properties.get(index).xmin = x;
				if (properties.get(index).xmax < x) properties.get(index).xmax = x;
				if (properties.get(index).ymin > y) properties.get(index).ymin = y;
				if (properties.get(index).ymax < y) properties.get(index).ymax = y;

				//}
			}
		}
		
		for (int i=0; i < properties.size(); i++) {	// calculate average intensity, mass center (area is number of pixels)
			properties.get(i).avrgIntensity /= properties.get(i).area;
			properties.get(i).massCenterX /= properties.get(i).area;
			properties.get(i).massCenterY /= properties.get(i).area;
		}
	}
	
	public ImageProcessor getImageComponents() {
		return imageComponents;
	}
	
	/* Combine components (properties considered), whose markers appear in the dilated (radius d) mask of previously detected component.
	 * "components" is the image after water segmentation on markers */
	public void mergeComponentsByMarkers(ImageProcessor markers, ImageComponentsAnalysis prevComponents, int d) {
		if (prevComponents == null) 
			return;
		ImageProcessor mask;
		int index, intens;
		ArrayList<Integer> list = new ArrayList<Integer>(3);
		for (int i=0; i<prevComponents.getComponentsCount(); i++) {
			if (prevComponents.getComponentArea(i) < 2000) {	//dont do this for big background regions
				System.out.println("component " +i +", area " + prevComponents.getComponentArea(i));
				mask = prevComponents.getDilatedComponentImage(i, d);
				list = getComponentListByMask(markers, mask, imageComponents, prevComponents.getComponentX0(i), prevComponents.getComponentY0(i));
				// first, remove big components from the list
				for (int k=0; k<list.size(); k++) {
					index = list.get(k);
					System.out.print(index + " ");
					if (this.properties.get(index).area > 1000) 
						list.remove(k);
				}
				
				// !!! problem !!! The same marker can be found in several masks. Remove it if it is the only marker in some component (~)
				// It's better to assign intensity labels to markers in one component and just assign it
				
				//here change all components intensity to that of the first marker. Properties list should also change
				if (list.size() > 0) {
					//intens = properties.get(list.get(0)).intensity;
					intens = prevComponents.getComponentIntensity(i);
					for (int k=1; k<list.size(); k++) {
						changeComponentIntensityByIndex(list.get(k), intens);
						fillBasicProperties();
						fillCircularity();
					}
				}
			}
		}
		System.out.println("___");
	}
	
	/* return list of component indexes, which markers appear in the mask located from x0,y0 */
	private ArrayList<Integer> getComponentListByMask(ImageProcessor markers, ImageProcessor mask, ImageProcessor components, int x0, int y0) {
		ArrayList<Integer> result = new ArrayList<Integer>(3);
		int wb = mask.getWidth(), hb = mask.getHeight();
		int v;	
		//mb later add something that prevents getting the same markerinto the list for different masks...not here tho
		for (int y=y0; y<y0+hb; y++)
			for (int x=x0; x<x0+wb; x++) {
				if (x>0 && x<markers.getWidth() && y>0 && y<markers.getHeight() &&  markers.get(x, y) != 0 && mask.get(x-x0, y-y0) > 0) {
					v = findComponentIndexByIntensity(components.get(x, y));
					result.add(v);
				}
			}
		return result;
	}
	
	/* filter components by area and circularity */
	private void filterComponents(int minArea, int maxArea, float minCirc, float maxCirc, float minAvrgIntensity, float maxAvrgIntensity) {
		ArrayList<Integer> list = new ArrayList<Integer>(20); // what components to filter
		int area;
		float circ, avrgInt;
		
		//fill list with indexes of component to be removed
		for (int i=0; i<properties.size(); i++) {
			area = properties.get(i).area;
			circ = properties.get(i).circularity;
			avrgInt = properties.get(i).avrgIntensity;
			//System.out.println(area + " : " + circ + " : " + avrgInt);
			if (area < minArea || area > maxArea) {
				list.add(i);
				continue;
			}
			if (circ < minCirc || circ > maxCirc) {
				list.add(i);
				continue;
			}
			if (avrgInt < minAvrgIntensity || avrgInt > maxAvrgIntensity) {
				list.add(i);
				continue;
			}
		}
		
		// delete components
		for (int i=list.size() - 1; i>=0 ; i--) {
			removeComponent(imageComponents, properties.get(list.get(i)).intensity);
		}
	}
	
	public ImageProcessor getFilteredComponentsIp(int minArea, int maxArea, float minCirc, float maxCirc, float minAvrgIntensity, float maxAvrgIntensity) {
		filterComponents(minArea, maxArea, minCirc, maxCirc, minAvrgIntensity, maxAvrgIntensity);
		return imageComponents;
	}
	
	/* return box-image containing the component[nComp], dilated by disk with radius "d" */
	public ImageProcessor getDilatedComponentImage(int nComp, int d) {
		int x0 = properties.get(nComp).xmin;
		int x1 = properties.get(nComp).xmax;
		int y0 = properties.get(nComp).ymin;
		int y1 = properties.get(nComp).ymax;
		ImageProcessor result = new FloatProcessor(x1-x0+1 + 2*d, y1-y0+1 + 2*d);
		
		// copy component into the new image
		float v;
		final int compInt = properties.get(nComp).intensity;
		for (int x=d; x < result.getWidth() - d; x++) 
			for (int y=d; y< result.getHeight() - d; y++) {
				v = imageComponents.getf(x0+x-d, y0+y-d);
				if (v == compInt)
					result.setf(x, y, 1);
			}
		result = ImageFunctions.operationMorph(result, Operation.DILATION, Strel.Shape.DISK, d);
		return result;
	}
	
	/* change markers image, so that all markers inside one mask are merged (into the geometrical center), for one component
	 * (x0,y0) is the top-left point of the box, where mask should be in markers image */
	public static void mergeMarkersByComponentMask(ImageProcessor markers, ImageProcessor mask, int x0, int y0) {
		int wb = mask.getWidth(), hb = mask.getHeight();
		int count = 0;
		float newx = 0, newy = 0;		
		for (int y=y0; y<y0+hb; y++)
			for (int x=x0; x<x0+wb; x++) {
				if (x>0 && x<markers.getWidth() && y>0 && y<markers.getHeight() &&  markers.get(x, y) != 0 && mask.get(x-x0, y-y0) > 0) {
					newx+=x;
					newy+=y;
					count++;
					markers.setf(x,y,0);
				}
			}
		markers.setf((int)(newx/count), (int)(newy/count), 255);
	}
	
	/* combines components in ip that belong to the same component in the compImage. 
	 * compImage nd ip must be images after the BinaryImages.componentsLabelling operation (i.e. not float, components are labelled from 0) */
	public static ImageProcessor combineComponentsInMask(ImageProcessor ip, ImageProcessor compImage) {
		if (compImage == null) //for stack processing the first image
			return ip;
		ImageProcessor result = ip.duplicate();
		int[] table = new int[(int) ip.getMax() + 1]; //table for component labels
		for (int i=0; i<table.length; i++) 
			table[i] = -1;
		int v1,v2;
		// first iteration through the image - fill table of pairs (initial component number, new component number)
		for (int i=0; i<ip.getPixelCount(); i++) {
			v1 = ip.get(i); 
			v2 = compImage.get(i);
			if (table[v1] == -1) table[v1] = v2; //set filling value for not initialized component labels
			else if (table[v1] != v2) //if component was already filled but with another value, then discard it by setting value to zero
				table[v1] = 0; 
		}
		
		//the second iteration - change values of components according to the table
		for (int i=0; i<ip.getPixelCount(); i++) {
			v1 = ip.get(i);
			v2 = table[v1];
			result.set(i, v2);
		}
		return result;
	}
	
	/* combines components in ip that belong to the same component in the compImage. Different from previous one in a way, that 
	 * compImage is erosed image and components are labelled if at least some of them is in the  mask (every pixel).
	 * compImage nd ip must be images after the BinaryImages.componentsLabelling operation (i.e. not float, components are labelled from 0) */
	public static ImageProcessor combineComponentsInMaskFromInside(ImageProcessor ip, ImageProcessor compImage) {
		if (compImage == null) //for stack processing the first image
			return ip;
		ImageProcessor result = ip.duplicate();
		int[] table = new int[(int) ip.getMax() + 1]; //table for component labels
		for (int i=0; i<table.length; i++) 
			table[i] = -1;
		int v1,v2;
		// first iteration through the image - fill table of pairs (initial component number, new component number)
		for (int i=0; i<ip.getPixelCount(); i++) {
			v1 = ip.get(i);
			v2 = compImage.get(i);
			if (v2 != 0) table[v1] = v2; 
		}
		
		//the second iteration - change values of components according to the table
		for (int i=0; i<ip.getPixelCount(); i++) {
			v1 = ip.get(i);
			v2 = table[v1];
			result.set(i, v2);
		}
		return result;
	}
	
	/* merges components if they have similar avrg intensity. Component with label '0' should be the canny edge detection thingy */
	public void mergeComponents() {
		int upLabel, downLabel, leftLabel, rightLabel;
		int l;
		ImageProcessor originalComponents = imageComponents.duplicate();
		for (int i=0; i<properties.size(); i++) 
			//System.out.println(properties.get(i).intensity);
		// pass through the image and look for boundary pixels (label '0'). Then look in 4C-neighbourhood if components should be merged */		
		for (int y=1; y < h-1; y++) {
			for (int x=1; x < w-1; x++) {
				l = imageComponents.get(x,y);
				if (l == 0) {
					upLabel = originalComponents.get(x,y-1);
					downLabel = originalComponents.get(x,y+1);
					/*System.out.println(upLabel);
					System.out.println(downLabel);
					System.out.println(leftLabel);
					System.out.println(rightLabel);*/
					// if up and down has close entensity and different labels, change the down label to that of top
					if (upLabel != 0 && downLabel != 0 && upLabel != downLabel)
						if (Math.abs(getComponentAvrgIntensityByIntensity(upLabel) - getComponentAvrgIntensityByIntensity(downLabel)) < 5) {
							System.out.println(downLabel);
							System.out.println(upLabel);
							changeComponentIntensity(downLabel, upLabel);							
						}
					// same for left/right
					// !!! intensity might have changed in the previous step
					leftLabel = originalComponents.get(x-1,y);
					rightLabel = originalComponents.get(x+1,y);
					if (leftLabel != 0 && rightLabel != 0 && leftLabel != rightLabel)
						if (Math.abs(getComponentAvrgIntensityByIntensity(leftLabel) - getComponentAvrgIntensityByIntensity(rightLabel)) < 5) {
							System.out.println(leftLabel);
							System.out.println(rightLabel);
							changeComponentIntensity(rightLabel, leftLabel);
						}
				}
			}
		}
		imageComponents = ImageFunctions.operationMorph(imageComponents, Operation.CLOSING, Strel.Shape.DISK, 1); // to remove '0' label lines
	}
	
	/* for merging components; also deletes old intensity component from the list */
	private void changeComponentIntensityByIndex(int compIndex, int newIntensity) {
		if (getComponentIntensity(compIndex) == newIntensity) //dont do anything if it's the same component
			return;
		int x0,x1,y0,y1;
		x0 = properties.get(compIndex).xmin;
		x1 = properties.get(compIndex).xmax;
		y0 = properties.get(compIndex).ymin;
		y1 = properties.get(compIndex).ymax;
		int intensity = properties.get(compIndex).intensity;
		for (int y=y0; y <= y1; y++) 
			for (int x=x0; x <= x1; x++) 
				if (imageComponents.get(x, y) == intensity)
					imageComponents.set(x, y, newIntensity);

		properties.remove(compIndex);
		nComponents--;
	}
	
	private void changeComponentIntensity(int intensity, int newIntensity) {
		int x0,x1,y0,y1, nComp;
		nComp = findComponentIndexByIntensity(intensity);
		x0 = properties.get(nComp).xmin;
		x1 = properties.get(nComp).xmax;
		y0 = properties.get(nComp).ymin;
		y1 = properties.get(nComp).ymax;

		for (int y=y0; y <= y1; y++) 
			for (int x=x0; x <= x1; x++) 
				if (imageComponents.get(x, y) == intensity)
					imageComponents.set(x, y, newIntensity);

		properties.get(nComp).intensity = newIntensity;
		//here we should recalculate all properties...or not here
	}
	
	/* Adds rois to roi manager with slice label, with each roi corresponding to one component in this.imageComponents. Rois are ordered by intensity */
	public void addRoisToManager(RoiManager manager, ImagePlus img, int slice) {
		//RoiManager res = new RoiManager();
		Roi roi;
		Wand w = new Wand(imageComponents);
		int currIntens = 1;
		int count = 0, index;
		String roiName;

		// the first slice is 1 (not 0)
		img.setSliceWithoutUpdate(slice);
		
		while (count < properties.size()) {
			index = findComponentIndexByIntensity(currIntens);
			if (index == -1) { //component with such intensity not found, increase intensity
				currIntens++;
				continue;
			}
			w.autoOutline(properties.get(index).xmin, properties.get(index).ymin, currIntens, currIntens);
			if (w.npoints>0) { // we have a roi from the wand... 
				roi = new PolygonRoi(w.xpoints, w.ypoints, w.npoints, Roi.TRACED_ROI);
				roiName = String.format("%04d", slice);
				roiName += "-"+currIntens;
				roi.setName(roiName);
				
				//manager.add(img, roi, slice);
				//img.setRoi(roi);
				manager.addRoi(roi);
				//manager.add(img, roi, slice);
				//res.add(imp, roi, n);
			}
			currIntens++;
			count++; //component added
		}
		img.setSliceWithoutUpdate(slice);
	}
	
	public ImageProcessor getAvrgIntensityImage() {
		ImageProcessor result = imageComponents.duplicate();
		int v;
		for (int i=0; i<result.getPixelCount(); i++) {
			v = findComponentIndexByIntensity(result.get(i));
			//System.out.println(result.get(i));
			if (v != -1)
				result.setf(i, properties.get(v).avrgIntensity);
		}
		return result;
	}
	
	/* removes component with given intensity from properties, and deletes it from image (by setting its intenssity to zero) */
	private void removeComponent(ImageProcessor image, int intensity) {
		int x0,x1,y0,y1, nComp;
		nComp = findComponentIndexByIntensity(intensity);
		x0 = properties.get(nComp).xmin;
		x1 = properties.get(nComp).xmax;
		y0 = properties.get(nComp).ymin;
		y1 = properties.get(nComp).ymax;

		for (int y=y0; y <= y1; y++) 
			for (int x=x0; x <= x1; x++) 
				if (image.get(x, y) == intensity)
					image.set(x, y, 0);

		properties.remove(nComp); //remove from the list
		nComponents--;	//decrease number
	}
	
	/* return index of component with given intensity. Returns -1 if not found */
	private int findComponentIndexByIntensity(int intensity) {
		for (int i=0; i<properties.size(); i++)
			if (properties.get(i).intensity == intensity) return i;
		return -1;
	}
	
	/* calculates and fills the circularity property */
	private void fillCircularity() {
		for (int i=0; i<nComponents; i++)
			properties.get(i).calcCircularity();
	}
	
	/* number of neighbour border pixels, 4-connectivity*/
	private int numberOfNeighbours4C(ImageProcessor ip, int x, int y) {
		int result = 0;
		if (isBorderPixel4C(ip, x-1, y)) result++;
		if (isBorderPixel4C(ip, x+1, y)) result++;
		if (isBorderPixel4C(ip, x, y-1)) result++;
		if (isBorderPixel4C(ip, x, y+1)) result++;
		return result;
	}
	
	/* number of neighbour border pixels, diagonal-connectivity */
	private int numberOfNeighboursDiagC(ImageProcessor ip, int x, int y) {
		int result = 0;
		if (isBorderPixel4C(ip, x-1, y-1)) result++;
		if (isBorderPixel4C(ip, x-1, y+1)) result++;
		if (isBorderPixel4C(ip, x+1, y-1)) result++;
		if (isBorderPixel4C(ip, x+1, y+1)) result++;
		return result;
	}
	
	/* returns true if any of the neighbouring pixels (4-connectivity) has other intensity value (not 0) */
	private boolean isBorderPixel4C(ImageProcessor ip, int x, int y) {
		if (x < 0 || x > ip.getWidth() - 1 || y < 0 || y > ip.getHeight() - 1) return false;	// pixels out of the image
		if (x == 0 || y==0 || x == ip.getWidth()-1 || y == ip.getHeight()-1) return true; 		// pixels on the image border
		int v_xy = ip.get(x,y);
		if (x > 0 && ip.get(x - 1, y) != v_xy) return true;
		if (x < ip.getWidth() && ip.get(x+1,y) != v_xy) return true;
		if (y > 0 && ip.get(x, y-1) != v_xy) return true;
		if (y < ip.getHeight() && ip.get(x, y+1) != v_xy) return true;
		return false;
	}
	
	/* return true if (x,y) pixel has diagonally connected border neighbouring 'on' pixels */
	private boolean hasDiagonalBorderNeighbours(ImageProcessor ip, int x, int y) {
		return isBorderPixel4C(ip,x-1,y-1) || isBorderPixel4C(ip,x-1,y+1) || isBorderPixel4C(ip,x+1,y-1) || isBorderPixel4C(ip,x+1,y+1);
	}
	
	@Override
	public String toString() {
		String res = new String();
		for (int i=0; i < properties.size(); i++) res+=properties.get(i).toString() + '\n';
		return res;
	}
}