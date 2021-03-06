package properties;

import graph.CellTrackingGraph;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.gui.Wand;
import ij.process.ImageProcessor;

/* class describing a detection */
public class Detection {
	private int _displayIntensity;
	private int _slice;
	private int _parentIndex;
	private int _labelIndex; 
	private boolean _isMitosis;
	private Roi _roi;
	
	Detection(int displayIntensity, int slice, boolean isMitosis) {
		this._displayIntensity = displayIntensity;
		this._slice = slice;
		this._isMitosis = isMitosis;
		_roi = null;
	}
	
	void fillRoi(ImageProcessor detectionsImage, int intensity, int startX, int startY, int slice) {
		Wand w = new Wand(detectionsImage);
		w.autoOutline(startX, startY, intensity, intensity, Wand.FOUR_CONNECTED);
		if (w.npoints > 0) { // we have a roi from the wand...
			_roi = new PolygonRoi(w.xpoints, w.ypoints, w.npoints, Roi.TRACED_ROI);
			_roi.setPosition(slice);
		}
	}
	
	Roi roi() {
		return _roi;
	}
	
	int displayIntensity() {
		return _displayIntensity;
	}
	
	boolean isMitosis() {
		return _isMitosis;
	}
	
	void setRoi(Roi roi) {
		roi.copyAttributes(_roi);
		_roi = roi;
	}

}
