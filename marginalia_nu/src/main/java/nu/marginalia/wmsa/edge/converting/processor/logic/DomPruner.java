package nu.marginalia.wmsa.edge.converting.processor.logic;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeVisitor;

import java.util.HashMap;
import java.util.Map;

public class DomPruner {

    public void prune(Document document, double pruneThreshold) {
        PruningVisitor pruningVisitor = new PruningVisitor();
        document.traverse(pruningVisitor);

        pruningVisitor.data.forEach((node, data) -> {
            if (data.depth <= 1) {
                return;
            }
            if (data.signalNodeSize == 0) node.remove();
            else if (data.noiseNodeSize > 0
                    && data.signalRate() < pruneThreshold
                    && data.treeSize > 3) {
                node.remove();
            }
        });
    }



    private static class PruningVisitor implements NodeVisitor {

        private final Map<Node, NodeData> data = new HashMap<>();
        private final NodeData dummy = new NodeData(Integer.MAX_VALUE, 1, 0);

        @Override
        public void head(Node node, int depth) {}

        @Override
        public void tail(Node node, int depth) {
            final NodeData dataForNode;

            if (node instanceof TextNode tn) {
                dataForNode = new NodeData(depth, tn.text().length(), 0);
            }
            else if (isSignal(node)) {
                dataForNode = new NodeData(depth,  0,0);
                for (var childNode : node.childNodes()) {
                    dataForNode.add(data.getOrDefault(childNode, dummy));
                }
            }
            else {
                dataForNode = new NodeData(depth,  0,0);
                for (var childNode : node.childNodes()) {
                    dataForNode.addAsNoise(data.getOrDefault(childNode, dummy));
                }
            }



            data.put(node, dataForNode);
        }

        public boolean isSignal(Node node) {

            if (node instanceof Element e) {
                if ("a".equalsIgnoreCase(e.tagName()))
                    return false;
                if ("nav".equalsIgnoreCase(e.tagName()))
                    return false;
                if ("footer".equalsIgnoreCase(e.tagName()))
                    return false;
                if ("header".equalsIgnoreCase(e.tagName()))
                    return false;
            }

            return true;
        }
    }

    private static class NodeData {
        int signalNodeSize;
        int noiseNodeSize;
        int treeSize = 1;
        int depth;

        private NodeData(int depth, int signalNodeSize, int noiseNodeSize) {
            this.depth = depth;
            this.signalNodeSize = signalNodeSize;
            this.noiseNodeSize = noiseNodeSize;
        }

        public void add(NodeData other) {
            signalNodeSize += other.signalNodeSize;
            noiseNodeSize += other.noiseNodeSize;
            treeSize += other.treeSize;
        }

        public void addAsNoise(NodeData other) {
            noiseNodeSize += other.noiseNodeSize + other.signalNodeSize;
            treeSize += other.treeSize;
        }


        public double signalRate() {
            return signalNodeSize / (double)(signalNodeSize + noiseNodeSize);
        }
    }
}
