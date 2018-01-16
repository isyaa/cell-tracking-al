package cellTracking;

import java.util.ArrayList;

import graph.Arc;
import graph.Graph;
import graph.Node;
import ij.process.ImageProcessor;
import ij.ImagePlus;
import ij.ImageStack;
import point.Point;

public class NearestNeighbourTracking {
	private Graph cellGraph;

	private int currSlice;
	private int slicesCount;

	/*
	 * List of components classes, containing image with labels and information
	 * about them. Should be formed during processing and then processed.
	 */
	private ArrayList<ImageComponentsAnalysis> componentsList;

	public Graph getGraph() {
		return cellGraph;
	}

	public NearestNeighbourTracking(int slicesCount) {
		this.slicesCount = slicesCount;
		componentsList = new ArrayList<ImageComponentsAnalysis>(slicesCount);
		cellGraph = new Graph(5, 5, 5);
	}

	public void addComponentsAnalysis(ImageComponentsAnalysis comps) {
		componentsList.add(comps);
	}

	public int getSlicesCount() {
		return slicesCount;
	}

	public ArrayList<ImageComponentsAnalysis> getComponentsList() {
		return componentsList;
	}

	/*
	 * finds nearest components in comp1-comp2 not further than 'radius' pixels. (t1
	 * and t2 refers to time points of comp1 and comp2 respectively) this should
	 * refer to the i -> i+1 step. So t1>t2. t1 and t2 are only required to properly
	 * fill in the graph
	 */
	public void findNearestComponents(ImageComponentsAnalysis comp1, int t1, ImageComponentsAnalysis comp2, int t2,
			double radius) {
		Point m1, m2;
		int closestIndex, backClosestIndex;
		Node v1, v2;
		for (int i = 0; i < comp2.getComponentsCount(); i++) {
			if (comp2.getComponentHasParent(i)) { // only add to components that doesn't have parent
				continue;
			}
			m2 = comp2.getComponentMassCenter(i); //
			// closestIndex = findClosestPointIndex(m2, comp1, radius);
			closestIndex = findBestScoringComponentIndex(comp2, i, comp1, radius);
			if (closestIndex != -1) { // closest component found, add to graph
				// should also check back - if for the found component the closest neighbour is
				// the same, then link them, otherwise skip?
				m1 = comp1.getComponentMassCenter(closestIndex);
				// backClosestIndex = findClosestPointIndex(m1, comp2, radius);
				backClosestIndex = findBestScoringComponentIndex(comp1, closestIndex, comp2, radius);
				if (backClosestIndex != i)
					continue;
				if (comp1.getComponentChildCount(closestIndex) > 0) { // only if it has no children
					continue;
				}
				v1 = new Node(t1, closestIndex);
				v2 = new Node(t2, i);
				cellGraph.addArcFromToAddable(v1, v2);
				comp2.setComponentHasParent(i);
				comp1.incComponentChildCount(closestIndex);
			} else {
				// closest component for current component in comp1 was not found - may be
				// remove it from detection (it may not be correct detection)
			}
		}
	}

	/* this is for back tracking (mitosys should be tracked with this steps) */
	public void findNearestComponentsBackStep(ImageComponentsAnalysis comp2, int t2, ImageComponentsAnalysis comp1,
			int t1, double radius) {
		Point m2;
		int closestIndex;
		Node v1, v2;
		for (int i = 0; i < comp2.getComponentsCount(); i++) {
			if (comp2.getComponentHasParent(i)) {
				continue;
			}
			m2 = comp2.getComponentMassCenter(i); // component without parent
			// closestIndex = findClosestPointIndex(m2, comp1, radius);
			closestIndex = findBestScoringComponentIndex(comp2, i, comp1, radius); // here should be daughter-check, not the
																				// same scoring function
			if (closestIndex != -1) { // closest component found, add to graph
				if (comp1.getComponentChildCount(closestIndex) > 1 || comp2.getComponentChildCount(i) == 0) {
					continue; // if closest "parent" already has 2 children, then skip. Or if child component
								// doesn't have any children, i.e. its a dead track. Or if state isn't mitosys
				}
				if (comp1.getComponentChildCount(closestIndex) == 1
						&& comp1.getComponentState(closestIndex) != State.MITOSIS) {
					System.out.println("component with index " + closestIndex + " discarded by state");
					continue; // if closest parent has 1 child but not mitosys, then don't add
				}
				v1 = new Node(t1, closestIndex);
				v2 = new Node(t2, i);
				System.out.println("Arc made during back tracking: " + v1 + " -- " + v2
						+ " with comp1(closest) child count being " + comp1.getComponentChildCount(closestIndex)
						+ "and comp2 child count being " + comp2.getComponentChildCount(i));
				cellGraph.addArcFromToAddable(v1, v2);
				comp2.setComponentHasParent(i);
				comp1.incComponentChildCount(closestIndex);
			} else {
				// closest component for current component in comp2 was not found
			}
		}
	}

	/*
	 * components list should be filled tracking algorithm: first, find nearest
	 * neighbours going from 0 to T time slice then, back tracking: find nearest
	 * component of i in i-1 that wasn't tracked
	 */
	public void trackComponents(double radius, double radiusBackTracking, int n_lookThroughSlices) {
		// 0 -> T tracking

		for (int j = 0; j < n_lookThroughSlices; j++) {
			for (int i = 1; i < componentsList.size(); ++i) {
				if (i + j < componentsList.size())
					findNearestComponents(componentsList.get(i - 1), i - 1, componentsList.get(i + j), i + j,
							radius + j * 5);
			}
		}

		// back tracking T -> 0
		for (int j = 0; j < n_lookThroughSlices; j++) {
			for (int i = componentsList.size() - 1; i > 0; --i) {
				if (i - j > 0) {
					findNearestComponentsBackStep(componentsList.get(i), i, componentsList.get(i - 1 - j), i - 1 - j,
							radiusBackTracking + j * 5);
				}
			}
		}
	}

	/*
	 * returns index of the component in comp, which mass center is the closest to p
	 */
	private int findClosestPointIndex(Point p, ImageComponentsAnalysis comp, double radius) {
		int min_i = -1;
		double min_dist = Double.MAX_VALUE;
		double dist;
		for (int i = 0; i < comp.getComponentsCount(); i++) {
			dist = Point.dist(p, comp.getComponentMassCenter(i));
			if (dist < radius && dist < min_dist) {
				min_dist = dist;
				min_i = i;
			}
		}
		return min_i;
	}

	/*
	 * Similar to 'findClosestPoint', returns the component index in comp2, which
	 * has the best score for component i1 in comp1
	 */
	private int findBestScoringComponentIndex(ImageComponentsAnalysis comp1, int i1, ImageComponentsAnalysis comp2,
			double maxRadius) {
		int min_i = -1;
		double min_score = Double.MAX_VALUE;
		double score;
		for (int i = 0; i < comp2.getComponentsCount(); i++) {
			score = penalFunctionNN(comp1, i1, comp2, i, maxRadius);
			if (score < min_score) {
				min_score = score;
				min_i = i;
			}
		}
		return min_i;
	}

	/*
	 * calculates the penalty function between component with index=i1 in comp1 and
	 * i2 in comp. (So the less it is, the better, the more the chance they should
	 * be connected)
	 */
	private double penalFunctionNN(ImageComponentsAnalysis comp1, int i1, ImageComponentsAnalysis comp2, int i2,
			double maxRadius) {
		Point m1 = comp1.getComponentMassCenter(i1);
		Point m2 = comp2.getComponentMassCenter(i2);
		double dist = Point.dist(m1, m2);
		if (dist > maxRadius)
			return Double.MAX_VALUE;

		int area1, area2;
		float circ1, circ2;
		float intensity1, intensity2;
		double p_circ, p_area, p_int, p_dist;
		area1 = comp1.getComponentArea(i1);
		circ1 = comp1.getComponentCircularity(i1);
		intensity1 = comp1.getComponentAvrgIntensity(i1);
		area2 = comp2.getComponentArea(i2);
		circ2 = comp2.getComponentCircularity(i2);
		intensity2 = comp2.getComponentAvrgIntensity(i2);
		p_area = normVal(area1, area2);
		p_circ = normVal(circ1, circ2);
		p_int = normVal(intensity1, intensity2);
		int minDist_in2 = findClosestPointIndex(m1, comp2, maxRadius);
		int minDist_in1 = findClosestPointIndex(m2, comp1, maxRadius);
		double minDist1 = Double.MAX_VALUE, minDist2 = Double.MAX_VALUE;

		// if closest component was not found closer than maxRadius, then let minDist be
		// huge, so score will be =1
		if (minDist_in2 != -1)
			minDist1 = Point.dist(m1, comp2.getComponentMassCenter(minDist_in2));
		if (minDist_in1 != -1)
			minDist2 = Point.dist(m2, comp1.getComponentMassCenter(minDist_in1));

		if (minDist_in1 == -1 && minDist_in2 == -1)
			p_dist = 1;
		else
			p_dist = normVal(Math.min(minDist1, minDist2), dist);

		// weights for area,circularity, avrg intensity and distance values
		double w_a = 0.2;
		double w_c = 0.2;
		double w_i = 0.4;
		double w_d = 1;
		double penal = w_a * p_area + w_c * p_circ + w_i * p_int + w_d * p_dist;
		return penal;
	}

	/* gets difference between value in [0,1] */
	private double normVal(double v1, double v2) {
		double v = Math.abs(v1 - v2) / Math.sqrt(v1 * v1 + v2 * v2);
		return v;
	}

	/* draws cellGraph as tracks on ip */
	public void drawTracksIp(ImageProcessor ip) {
		ArrayList<Arc> arcs = cellGraph.getArcs();
		int i0, i1, t0, t1;
		Node n0, n1;
		Point p0, p1;
		int x0, x1, y0, y1;
		for (int k = 0; k < arcs.size(); k++) {
			n0 = arcs.get(k).getFromNode();
			n1 = arcs.get(k).getToNode();
			i0 = n0.get_i();
			i1 = n1.get_i();
			t0 = n0.get_t();
			t1 = n1.get_t();

			p0 = componentsList.get(t0).getComponentMassCenter(i0);
			p1 = componentsList.get(t1).getComponentMassCenter(i1);

			x0 = (int) p0.getX();
			y0 = (int) p0.getY();
			x1 = (int) p1.getX();
			y1 = (int) p1.getY();
			ImageFunctions.drawX(ip, x0, y0);
			ImageFunctions.drawX(ip, x1, y1);
			ImageFunctions.drawLine(ip, x0, y0, x1, y1);
		}
	}

	/* draws arcs on ip */
	public void drawTracksIp(ImageProcessor ip, ArrayList<Arc> arcs) {
		int i0, i1, t0, t1;
		Node n0, n1;
		Point p0, p1;
		int x0, x1, y0, y1;
		for (int k = 0; k < arcs.size(); k++) {
			n0 = arcs.get(k).getFromNode();
			n1 = arcs.get(k).getToNode();
			i0 = n0.get_i();
			i1 = n1.get_i();
			t0 = n0.get_t();
			t1 = n1.get_t();

			p0 = componentsList.get(t0).getComponentMassCenter(i0);
			p1 = componentsList.get(t1).getComponentMassCenter(i1);

			x0 = (int) p0.getX();
			y0 = (int) p0.getY();
			x1 = (int) p1.getX();
			y1 = (int) p1.getY();
			ImageFunctions.drawX(ip, x0, y0);
			ImageFunctions.drawX(ip, x1, y1);
			ImageFunctions.drawLine(ip, x0, y0, x1, y1);
		}
	}

	/* draws tracking result on each slice and return the result */
	ImagePlus drawTracksImagePlus(ImagePlus image) {
		ImagePlus result = image.createImagePlus();

		ImageStack stack = image.getStack();

		System.out.println(stack.getSize());
		System.out.println(image.getNSlices());
		stack.setProcessor(image.getStack().getProcessor(1), 1);

		ImageProcessor ip;
		for (int i = 2; i <= image.getNSlices(); i++) { // slices are from 1 to n_slices
			ip = image.getStack().getProcessor(i).duplicate();
			drawTracksIp(ip, cellGraph.getArcsBeforeTimeSlice(i - 1)); // but time is from 0 to nslices-1
			stack.setProcessor(ip, i);
		}

		result.setStack(stack);
		return result;
	}
}
