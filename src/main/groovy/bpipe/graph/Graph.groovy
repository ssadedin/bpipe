package bpipe.graph

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Robot;
import java.awt.image.BufferedImage;

import com.mxgraph.swing.mxGraphComponent as MxGraphComponent;
import com.mxgraph.view.mxGraph as MxGraph;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JFrame;

class Graph extends JFrame {
    
    Collection stages
    
    
    // Default width for pipe line stage boxes -
    // should be figured out dynamically, but for now
    // just assume something reasonable
    int defaultStageWidth = 180
    
    int defaultStageBoxWidth = 80
    
    JComponent graphComponent

    Graph(Collection stages) {
        super("Pipeline");
        // TODO: Font metrics!!!!
        this.defaultStageBoxWidth = stages.max { it.size() }.size() * 9 + 10
        this.defaultStageWidth = Math.max(defaultStageWidth, defaultStageBoxWidth+20)
        this.stages = stages
        this.createFlowDiagram(stages)
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    }
    
    static main(args) {
        Graph g = new Graph(["align", "index_bam", "sort","dedupe","call_variants"])
        g.display()
        // g.render("out.png")
    }
    
    void display() {
        // For the editor we actually make the window a little bigger so there is
        // more margine, otherwise the diagram is flush with the bottom of the window
        setSize((stages.size()+1) * defaultStageWidth + 40, 280);
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
    void createFlowDiagram(Collection<String> stages) {
        MxGraph graph = new MxGraph();
        Object parent = graph.getDefaultParent();
        graph.getModel().beginUpdate();
        
        // This should really be figured out dynamically by scanning the
        // actual stage names and using font metrics
        int stageBoxHeight = 30
        try {
            int xOffset = 0
            def previousVertex = null
            for(def s in stages) {
                xOffset+=defaultStageWidth
	            def v = graph.insertVertex(parent, null, s, xOffset, 20, defaultStageBoxWidth, stageBoxHeight);
                if(previousVertex)
		            graph.insertEdge(parent, null, "", previousVertex, v);
                previousVertex = v
            }
        }
        finally {
            graph.getModel().endUpdate();
        }

        graphComponent = new MxGraphComponent(graph);
        getContentPane().add(graphComponent);
        setSize((stages.size()+1) * defaultStageWidth + 40, stageBoxHeight + 40);
        this.graphComponent.setSize(this.width, this.height)
    }

}
