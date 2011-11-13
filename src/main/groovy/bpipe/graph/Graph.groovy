package bpipe.graph

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Robot;
import java.awt.image.BufferedImage;

import com.mxgraph.layout.hierarchical.mxHierarchicalLayout;
import com.mxgraph.swing.mxGraphComponent as MxGraphComponent;
import com.mxgraph.layout.hierarchical.mxHierarchicalLayout as MxHierarchicalLayout;
import com.mxgraph.view.mxGraph as MxGraph;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.SwingConstants;

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
		this.createFlowDiagram(stages)
		this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	}

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
		g.display()
		// g.render("out.png")
	}

	void display() {
		// For the editor we actually make the window a little bigger so there is
		// more margine, otherwise the diagram is flush with the bottom of the window
		setSize((stages.children().size()+1) * defaultStageWidth + 40, 280);
		this.setVisible(true);
	}

	/**
	 * Render to the specified file name
	 * @param fileName
	 */
	void render(String fileName) {

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
		setSize((stages.children.size()+1) * defaultStageWidth + 40, stageBoxHeight + 40);
		this.graphComponent.setSize(this.width, this.height)

		MxHierarchicalLayout layout = new MxHierarchicalLayout(graph);
		layout.setOrientation(SwingConstants.WEST);
		layout.execute(graph.getDefaultParent());
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
