package nu.marginalia.converting.processor.logic.dom;

import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeFilter;

import java.util.HashMap;
import java.util.Map;

/** Prune the DOM and remove noisy branches with a lot of tags and not a lot of text.
 * This removes a lot of noise and keeps segments that are more or less just plain text.
 * <p>
 * Used with JSoup's Document.filter() method
 */
public class DomPruningFilter implements NodeFilter {

    private final double pruneThreshold;

    private final Map<Node, NodeData> data = new HashMap<>();
    private final NodeData dummy = new NodeData(Integer.MAX_VALUE, 1, 0);

    public DomPruningFilter(double pruneThreshold) {
        this.pruneThreshold = pruneThreshold;
    }

    @Override
    public FilterResult head(Node node, int depth) {
        return FilterResult.CONTINUE;
    }

    @Override
    public FilterResult tail(Node node, int depth) {
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

        if (dataForNode.depth <= 1)
            return FilterResult.CONTINUE;

        if (dataForNode.signalNodeSize == 0)
            return FilterResult.REMOVE;
        if (dataForNode.noiseNodeSize > 0
                && dataForNode.signalRate() < pruneThreshold
                && dataForNode.treeSize > 3)
            return FilterResult.REMOVE;

        return FilterResult.CONTINUE;
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

class NodeData {
    int signalNodeSize;
    int noiseNodeSize;
    int treeSize = 1;
    int depth;

    NodeData(int depth, int signalNodeSize, int noiseNodeSize) {
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