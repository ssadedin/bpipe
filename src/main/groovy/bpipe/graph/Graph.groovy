/*
* Copyright (c) 2012 MCRI, authors
*
* Permission is hereby granted, free of charge, to any person
* obtaining a copy of this software and associated documentation
* files (the "Software"), to deal in the Software without
* restriction, including without limitation the rights to use,
* copy, modify, merge, publish, distribute, sublicense, and/or sell
* copies of the Software, and to permit persons to whom the
* Software is furnished to do so, subject to the following
* conditions:
*
* The above copyright notice and this permission notice shall be
* included in all copies or substantial portions of the Software.
*
* THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
* EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
* OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
* NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
* HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
* WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
* FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
* THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/
package bpipe.graph

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import groovy.util.logging.Log;

import com.mxgraph.layout.hierarchical.mxHierarchicalLayout;
import com.mxgraph.swing.mxGraphComponent as MxGraphComponent;
import com.mxgraph.layout.hierarchical.mxHierarchicalLayout as MxHierarchicalLayout;
import com.mxgraph.view.mxGraph as MxGraph;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.SwingConstants;

import org.apache.batik.dom.svg.SVGDOMImplementation;
import org.apache.batik.svggen.CachedImageHandlerBase64Encoder;
import org.apache.batik.svggen.GenericImageHandler;
import org.apache.batik.svggen.SVGGeneratorContext;
import org.apache.batik.svggen.SVGGraphics2D
import org.apache.batik.svggen.SVGGraphics2DIOException;

@Log
class Graph extends JFrame {

	Node stages

	private List<String> stageNames

	// Default width for pipe line stage boxes -
	// should be figured out dynamically, but for now
	// just assume something reasonable
	int defaultStageWidth = 180

	int defaultStageBoxWidth = 80

	// This should really be figured out dynamically by scanning the
	// actual stage names and using font metrics
	int stageBoxHeight = 30

	JComponent graphComponent

	Graph(Node stages) {
		super("Pipeline");

		this.stageNames = stages.children().collect { it.name() }

		// TODO: Font metrics!!!!
		this.defaultStageBoxWidth = stageNames.max { it.size() }.size() * 9 + 10
		this.defaultStageWidth = Math.max(defaultStageWidth, defaultStageBoxWidth+20)
		this.stages = stages
		this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	}

    /**
     * Some simple inline test code - should move to unit test
     */
	static main(args) {
		//        def stageNames = ["align", "index_bam", "sort","dedupe","call_variants"]
		//        Node parent = stageNames.inject(new Node(null, "Pipeline")) { p, name -> p.append(new Node(null, name, [])); p }

		Node parent = new Node(null, "Parent")
		Node align = new Node(null, "align")
		parent.append(align)

		def dedupe = new Node(null, "dedupe")

		align.append(dedupe)
		def index = new Node(null, "index")

		align.append(index)
		def filter = new Node(null, "filter")

		Node report = new Node(null, "report")
		align.append(filter)

		for(Node n in [dedupe,index,filter]) {
			n.append(report)
		}

		//		parent.append(report)

		Graph g = new Graph(parent)
		// g.display()
		g.render("out.png")
	}

	void display() {
        
		this.createFlowDiagram(stages)
        
		// For the editor we actually make the window a little bigger so there is
		// more margine, otherwise the diagram is flush with the bottom of the window
		setSize((stages.children().size()+1) * defaultStageWidth + 40, 280);
		this.setVisible(true);
	}

	/**
	 * Render to the specified file name
	 * @param fileName
	 */
	void renderPNG(String fileName) {
        
		this.createFlowDiagram(stages)
        
		log.info "Size = ${this.width} x ${this.height}"
        
//		int childCount = (stages.children().size()+1)
//        
//        int newWidth = childCount * defaultStageWidth + 40
//        
//		setSize(newWidth, this.height);
        
		layoutComponent(this.graphComponent);

		BufferedImage img = new BufferedImage(this.width,this.height,BufferedImage.TYPE_INT_RGB);
		Graphics g = img.getGraphics();
		g.setColor(Color.WHITE)
		g.fillRect(0, 0, this.width, this.height)
		this.graphComponent.paint(g)
		ImageIO.write(img, "png", new File(fileName))
	}

	static void layoutComponent(Component component) {
		synchronized (component.getTreeLock()) {
			component.doLayout();
			if (component instanceof Container) {
				for (Component child : ((Container)component).getComponents()) {
					layoutComponent(child);
				}
			}
		}
	}

	/**
	 * Create the flow diagram from the specified stages
	 */
	void createFlowDiagram(Node stages) {
		MxGraph graph = new MxGraph();
		Object parent = graph.getDefaultParent();
		graph.getModel().beginUpdate();

		try {
			addGraphNodes(graph, stages)
		}
		finally {
			graph.getModel().endUpdate();
		}

		graphComponent = new MxGraphComponent(graph);
		getContentPane().add(graphComponent);
		MxHierarchicalLayout layout = new MxHierarchicalLayout(graph);
        layout.setDisableEdgeStyle(true)
        
        boolean vertical = bpipe.Config.userConfig.diagramOrientation == "vertical"
        if(vertical)
    		layout.setOrientation(SwingConstants.NORTH);
          else
    		layout.setOrientation(SwingConstants.WEST);
            
		layout.execute(graph.getDefaultParent());
        
		// Find the "highest" vertex
		def highestCell = vertices.values().max {  it.geometry.y + it.geometry.height }
        double maxY = highestCell.geometry.y + highestCell.geometry.height
        
		def rightmostCell =vertices.values().max {  it.geometry.x + it.geometry.width } 
        double maxX = rightmostCell.geometry.x + highestCell.geometry.width
        
		log.info "Outer vertices at $maxX,$maxY"
        
		setSize((int)maxX + 40, (int)maxY+ 40);
		this.graphComponent.setSize(this.width, this.height)
	}
    
	void renderSVG(String outputFileName) {
        
        this.createFlowDiagram(stages)
        
        println "Drawing SVG graph ..."
        String svgNS = SVGDOMImplementation.SVG_NAMESPACE_URI;
        def domImpl = SVGDOMImplementation.getDOMImplementation();
        def document = domImpl.createDocument(svgNS, "svg", null);
         
        // Create an instance of the SVG Generator
        SVGGeneratorContext ctx = SVGGeneratorContext.createDefault(document);
     
        // Reuse our embedded base64-encoded image data.
        GenericImageHandler ihandler = new CachedImageHandlerBase64Encoder();
        ctx.setGenericImageHandler(ihandler);
         
        //Create SVG graphics2d Generator similar to the Graphich2d
        SVGGraphics2D svgGenerator = new SVGGraphics2D(ctx, false);
     
        //First draw Graph to the SVGGrapgics2D object using graphcomponent objects draw method
        graphComponent.getGraphControl().drawGraph(svgGenerator, true);
     
        //Once every thing is drawn on graphics find root element and update this by adding additional values for the required fields.
        def root = svgGenerator.getRoot();
        root.setAttributeNS(null, "width", graphComponent.getGraphControl().getPreferredSize().width + "");
        root.setAttributeNS(null, "height", graphComponent.getGraphControl().getPreferredSize().height + "");
        root.setAttributeNS(null, "viewBox", "0 0 " + graphComponent.getGraphControl().getPreferredSize().width + " " + graphComponent.getGraphControl().getPreferredSize().height);
     
        // Print to the SVG Graphics2D object
        boolean useCSS = true; // we want to use CSS style attributes
        Writer out = new FileWriter(new File(outputFileName));
        try {
          svgGenerator.stream(root, out, useCSS, false);
        }
        catch (Exception e) {
           e.printStackTrace();
        }
	}

	def vertices = [:]

	def addGraphNodes(MxGraph graph, Node stages, def previousVertex=[], int xOffset = 0) {

		def v
		if(vertices.containsKey(stages)) {
			v = vertices[stages]
		}
		else {
			xOffset+=defaultStageWidth
			v = graph.insertVertex(graph.getDefaultParent(), null, stages.name(), xOffset, 20, defaultStageBoxWidth, stageBoxHeight);
			vertices[stages] = v
		}

		for(prev in previousVertex) {
			graph.insertEdge(graph.getDefaultParent(), null, "", prev, v);
		}

		if(stages.children()) {
			previousVertex = []
			for(Node c in stages.children()) {
				previousVertex << addGraphNodes(graph, c, [v], xOffset)
			}
		}
		else
			previousVertex = [v]

		return previousVertex
	}
}
